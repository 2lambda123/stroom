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

import org.springframework.context.annotation.Scope;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;

@TaskHandlerBean(task = LifecycleTask.class)
@Scope(value = StroomScope.TASK)
public class LifecycleTaskHandler extends AbstractTaskHandler<LifecycleTask, VoidResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(LifecycleTaskHandler.class);

    @Override
    public VoidResult exec(final LifecycleTask task) {
        try {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            LOGGER.debug("exec() - >>> %s", task.getTaskName());
            task.getExecutable().exec(task);
            LOGGER.debug("exec() - <<< %s took %s", task.getTaskName(), logExecutionTime);
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return new VoidResult();
    }
}
