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

package stroom.util.concurrent;

import stroom.util.logging.StroomLogger;
import stroom.util.thread.ThreadUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstraction of a ExecutorService that also tracks submitted and completed job
 * counts.
 */
public class SimpleExecutor {
    private static StroomLogger LOGGER = StroomLogger.getLogger(SimpleExecutor.class);

    private static final int THREAD_SLEEP_MS = 100;
    private static final int LOGGING_DEBUG_MS = 1000;

    private final ExecutorService executorService;

    private final int threadCount;

    /**
     * Number of jobs submitted
     */
    private final AtomicInteger executorSubmitCount = new AtomicInteger(0);

    /**
     * Number of jobs completed
     */
    private final AtomicInteger executorCompleteCount = new AtomicInteger(0);

    public SimpleExecutor(int threadCount) {
        this.threadCount = threadCount;
        this.executorService = Executors.newFixedThreadPool(threadCount);
    }

    /**
     * Submit a job
     */
    public void execute(final Runnable runnable) {
        try {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        runnable.run();
                    } catch (final Throwable th) {
                        LOGGER.error("run() - Uncaught exception from execution", th);
                    } finally {
                        executorCompleteCount.incrementAndGet();
                    }
                }
            });

            // If we get a rejected execution then we don't want to increment
            // the submit count.
            executorSubmitCount.incrementAndGet();

        } catch (final RejectedExecutionException e) {
            // don't care about this
            LOGGER.error("run() - ignoring RejectedExecutionException");
        }
    }

    /**
     * Wait for a submitted jobs to complete (without shutting down).
     */
    public void waitForComplete() {
        long lastTime = System.currentTimeMillis();
        while (!executorService.isTerminated() && executorCompleteCount.get() < executorSubmitCount.get()) {
            defaultShortSleep();

            if (LOGGER.isDebugEnabled()) {
                long time = System.currentTimeMillis();
                if (time - lastTime > LOGGING_DEBUG_MS) {
                    LOGGER.debug("waitForComplete() - " + toString());
                }
                lastTime = time;
            }
        }
    }

    /**
     * Wait for the thread pool to stop.
     */
    public void waitForTerminated() {
        long lastTime = System.currentTimeMillis();
        while (!executorService.isTerminated()) {
            defaultShortSleep();

            if (LOGGER.isDebugEnabled()) {
                long time = System.currentTimeMillis();

                if (time - lastTime > LOGGING_DEBUG_MS) {
                    LOGGER.debug("waitForComplete() - " + toString());
                }
                lastTime = time;
            }
        }
    }

    /**
     * Stop and wait for shutdown.
     *
     * @param now
     *            don't wait for pending jobs to start
     */
    public void stop(boolean now) {
        if (now) {
            executorService.shutdownNow();
        } else {
            executorService.shutdown();
        }

        waitForTerminated();
    }

    /**
     * @return finished due to stop
     */
    public boolean isStopped() {
        return executorService.isTerminated();
    }

    public int getExecutorCompleteCount() {
        return executorCompleteCount.get();
    }

    public int getExecutorSubmitCount() {
        return executorSubmitCount.get();
    }

    @Override
    public String toString() {
        return "SimpleExecutor(" + threadCount + ") progress=" + executorCompleteCount + "/" + executorSubmitCount;
    }

    public static final void defaultShortSleep() {
        ThreadUtil.sleep(THREAD_SLEEP_MS);
    }
}
