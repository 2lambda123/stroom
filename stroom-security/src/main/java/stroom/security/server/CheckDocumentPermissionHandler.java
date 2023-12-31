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
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.security.shared.CheckDocumentPermissionAction;
import stroom.security.shared.DocumentPermissionNames;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedBoolean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = CheckDocumentPermissionAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
public class CheckDocumentPermissionHandler
        extends AbstractTaskHandler<CheckDocumentPermissionAction, SharedBoolean> {
    private final SecurityContext securityContext;

    @Inject
    public CheckDocumentPermissionHandler(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Override
    public SharedBoolean exec(final CheckDocumentPermissionAction action) {
        return SharedBoolean.wrap(securityContext.hasDocumentPermission(action.getDocumentType(), action.getDocumentId(), action.getPermission()));
    }
}
