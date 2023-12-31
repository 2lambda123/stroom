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

package stroom.pipeline.server.factory;

import stroom.pipeline.server.errorhandler.*;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskManager;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Severity;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskScopeContext;
import stroom.util.task.TaskScopeContextHolder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
@Scope(StroomScope.TASK)
class ProcessorFactoryImpl implements ProcessorFactory {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ProcessorFactoryImpl.class);

    static class MultiWayProcessor implements Processor {
        private final List<Processor> processors;
        private final TaskManager taskManager;
        private final ErrorReceiver errorReceiver;

        MultiWayProcessor(final List<Processor> processors, final TaskManager taskManager,
                          final ErrorReceiver errorReceiver) {
            this.processors = processors;
            this.taskManager = taskManager;
            this.errorReceiver = errorReceiver;
        }

        @Override
        public void process() {
            // Try and get the parent task context.
            final TaskScopeContext context = TaskScopeContextHolder.getContext();
            final Task<?> parentTask = context.getTask();

            final CountDownLatch countDownLatch = new CountDownLatch(processors.size());
            for (final Processor processor : processors) {
                final TaskCallback<VoidResult> taskCallback = new TaskCallback<VoidResult>() {
                    @Override
                    public void onSuccess(final VoidResult result) {
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        try {
                            if (!(t instanceof ExpectedProcessException)) {
                                if (t instanceof LoggedException) {
                                    // The exception has already been logged so
                                    // ignore it.
                                    if (LOGGER.isTraceEnabled()) {
                                        LOGGER.trace(t.getMessage(), t);
                                    }
                                } else {
                                    outputError(t);
                                }
                            }
                        } finally {
                            countDownLatch.countDown();
                        }
                    }
                };

                final GenericServerTask task = GenericServerTask.create(parentTask,  "Process",
                        null);
                task.setRunnable(processor::process);
                taskManager.execAsync(task, taskCallback);
            }

            try {
                while (!parentTask.isTerminated() && countDownLatch.getCount() > 0) {
                    countDownLatch.await(10, TimeUnit.SECONDS);
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        /**
         * Used to handle any errors that may occur during translation.
         */
        private void outputError(final Throwable t) {
            if (errorReceiver != null && !(t instanceof LoggedException)) {
                try {
                    if (t.getMessage() != null) {
                        errorReceiver.log(Severity.FATAL_ERROR, null, "MultiWayProcessor", t.getMessage(), t);
                    } else {
                        errorReceiver.log(Severity.FATAL_ERROR, null, "MultiWayProcessor", t.toString(), t);
                    }
                } catch (final Throwable e) {
                    // Ignore exception as we generated it.
                }

                if (errorReceiver instanceof ErrorStatistics) {
                    ((ErrorStatistics) errorReceiver).checkRecord(-1);
                }
            } else {
                LOGGER.fatal(t, t);
            }
        }
    }

    private final TaskManager taskManager;
    private final ErrorReceiverProxy errorReceiverProxy;

    @Inject
    public ProcessorFactoryImpl(final TaskManager taskManager, final ErrorReceiverProxy errorReceiverProxy) {
        this.taskManager = taskManager;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public Processor create(final List<Processor> processors) {
        if (processors == null || processors.size() == 0) {
            return null;
        }

        if (processors.size() == 1) {
            return processors.get(0);
        }

        return new MultiWayProcessor(processors, taskManager, errorReceiverProxy);
    }
}
