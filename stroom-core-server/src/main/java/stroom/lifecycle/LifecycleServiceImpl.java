/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.lifecycle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.StroomEntityManager;
import stroom.jobsystem.server.ScheduledTaskExecutor;
import stroom.pool.SecurityHelper;
import stroom.security.SecurityContext;
import stroom.task.server.StroomThreadGroup;
import stroom.task.server.TaskCallbackAdaptor;
import stroom.task.server.TaskManager;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.VoidResult;
import stroom.util.spring.ContextAwareService;
import stroom.util.spring.StroomBeanLifeCycle;
import stroom.util.spring.StroomBeanMethodExecutable;
import stroom.util.task.ServerTask;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.ThreadScopeRunnable;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class LifecycleServiceImpl implements ContextAwareService {
    protected static final StroomLogger LOGGER = StroomLogger.getLogger(LifecycleServiceImpl.class);
    private static final String STROOM_LIFECYCLE_THREAD_POOL = "Stroom Lifecycle#";

    private static final int ONE_SECOND = 1000;
    private static final long DEFAULT_INTERVAL = 10 * ONE_SECOND;

    private final TaskManager taskManager;
    private final StroomBeanLifeCycle stroomBeanLifeCycle;
    private final StroomEntityManager entityManager;
    private final ScheduledTaskExecutor scheduledTaskExecutor;
    private final SecurityContext securityContext;

    // The scheduled executor that executes executable beans.
    private ScheduledExecutorService scheduledExecutorService;

    private final AtomicInteger startingBeanCount = new AtomicInteger();
    private final AtomicInteger stoppingBeanCount = new AtomicInteger();

    private boolean startingUp = false;
    private boolean running = false;
    private boolean enabled;
    private long executionInterval;

    @Inject
    public LifecycleServiceImpl(final TaskManager taskManager,
                                final StroomBeanLifeCycle stroomBeanLifeCycle,
                                final StroomEntityManager entityManager,
                                final ScheduledTaskExecutor scheduledTaskExecutor,
                                final SecurityContext securityContext,
                                @Value("#{propertyConfigurer.getProperty('stroom.lifecycle.enabled')}") final String enabled,
                                @Value("#{propertyConfigurer.getProperty('stroom.lifecycle.executionInterval')}") final String executionIntervalString) {
        this.taskManager = taskManager;
        this.stroomBeanLifeCycle = stroomBeanLifeCycle;
        this.entityManager = entityManager;
        this.scheduledTaskExecutor = scheduledTaskExecutor;
        this.securityContext = securityContext;
        this.enabled = PropertyUtil.toBoolean(enabled, false);

        Long executionInterval;
        try {
            executionInterval = ModelStringUtil.parseDurationString(executionIntervalString);
            if (executionInterval == null) {
                executionInterval = DEFAULT_INTERVAL;
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("Unable to parse property 'stroom.lifecycle.executionInterval' value '" + executionIntervalString
                    + "', using default of '10s' instead", e);
            executionInterval = DEFAULT_INTERVAL;
        }
        this.executionInterval = executionInterval;
    }

    /**
     * Called when the application context is initialised.
     */
    @Override
    public void init() {
        if (enabled) {
            // Do this async so that we don't delay starting the web app up
            new Thread(() -> {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.info("init() - Starting up in background");
                startup();
                LOGGER.info("init() - Started in %s", logExecutionTime);
            }).start();
        }
    }

    /**
     * Called when the application context is destroyed.
     */
    @Override
    public void destroy() {
        LOGGER.debug("contextDestroyed()");
        if (enabled) {
            shutdown();
        }
    }

    public void startup() {
        LOGGER.info("Starting Stroom Lifecycle service");
        startingUp = true;

        taskManager.startup();

        startNext();
        // Wait for startup to complete.
        while (startingBeanCount.get() > 0) {
            ThreadUtil.sleep(500);
        }

        // Create the runnable object that will perform execution on all
        // scheduled services.
        final Runnable runnable = new ThreadScopeRunnable() {
            private final ReentrantLock lock = new ReentrantLock();

            @Override
            protected void exec() {
                if (lock.tryLock()) {
                    try (SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
                        Thread.currentThread().setName("Stroom Lifecycle - ScheduledExecutor");
                        scheduledTaskExecutor.execute();
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    LOGGER.warn("Still trying to execute tasks");
                }
            }
        };

        // Create the thread pool that we will use to startup, shutdown and
        // execute lifecycle beans asynchronously.
        final CustomThreadFactory threadFactory = new CustomThreadFactory(STROOM_LIFECYCLE_THREAD_POOL,
                StroomThreadGroup.instance(), Thread.MIN_PRIORITY + 1);

        // Create the executor service that will execute scheduled
        // services.
        scheduledExecutorService = Executors.newScheduledThreadPool(1, threadFactory);
        scheduledExecutorService.scheduleWithFixedDelay(runnable, 0, executionInterval, TimeUnit.MILLISECONDS);

        LOGGER.info("Started Stroom Lifecycle service");
        running = true;
        startingUp = false;
    }

    private void startNext() {
        final StroomBeanMethodExecutable executable = stroomBeanLifeCycle.getStartExecutable();
        if (executable != null) {
            startingBeanCount.getAndIncrement();
            taskManager.execAsync(new LifecycleTask(executable), new TaskCallbackAdaptor<VoidResult>() {
                @Override
                public void onSuccess(final VoidResult result) {
                    startNext();
                    startingBeanCount.getAndDecrement();
                }
            });
        }
    }

    public void shutdown() {
        // Wait for startup to finish.
        while (startingUp) {
            LOGGER.info("Waiting for startup to finish before shutting down");
            ThreadUtil.sleep(ONE_SECOND);
        }

        LOGGER.info("Stopping Stroom Lifecycle service");
        if (scheduledExecutorService != null) {
            // Stop the scheduled executor.
            scheduledExecutorService.shutdown();
            try {
                scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES);
            } catch (final InterruptedException e) {
                LOGGER.error(e, e);
            }
        }

        stopNext();
        // Wait for stop to complete.
        while (stoppingBeanCount.get() > 0) {
            ThreadUtil.sleep(500);
        }

        taskManager.shutdown();

        // Finally shutdown the entity manager.
        if (entityManager != null) {
            entityManager.shutdown();
        }

        LOGGER.info("Stopped Stroom Lifecycle service");
        running = false;
    }

    private void stopNext() {
        final StroomBeanMethodExecutable executable = stroomBeanLifeCycle.getStopExecutable();
        if (executable != null) {
            stoppingBeanCount.getAndIncrement();
            taskManager.execAsync(new LifecycleTask(executable), new TaskCallbackAdaptor<VoidResult>() {
                @Override
                public void onSuccess(final VoidResult result) {
                    stopNext();
                    stoppingBeanCount.getAndDecrement();
                }
            });
        }
    }

    public boolean isRunning() {
        return running;
    }
}
