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

package stroom.security.server;

import org.springframework.context.annotation.Scope;
import stroom.security.shared.ResetPasswordAction;
import stroom.security.shared.UserRef;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = ResetPasswordAction.class)
@Scope(value = StroomScope.TASK)
public class ResetPasswordHandler extends AbstractTaskHandler<ResetPasswordAction, UserRef> {
    private final AuthenticationService authenticationService;

    @Inject
    ResetPasswordHandler(final AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public UserRef exec(final ResetPasswordAction task) {
        return authenticationService.resetPassword(task.getUser(), task.getPassword());
    }
}
