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

package stroom.entity.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityServiceCreateAction;
import stroom.entity.shared.EntityServiceException;
import stroom.logging.EntityEventLog;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceCreateAction.class)
@Scope(value = StroomScope.TASK)
class EntityServiceCreateHandler extends AbstractTaskHandler<EntityServiceCreateAction, DocRef> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final EntityEventLog entityEventLog;

    @Inject
    EntityServiceCreateHandler(final EntityServiceBeanRegistry beanRegistry, final EntityEventLog entityEventLog) {
        this.beanRegistry = beanRegistry;
        this.entityEventLog = entityEventLog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DocRef exec(final EntityServiceCreateAction action) {
        final Object bean = beanRegistry.getEntityService(action.getType());
        if (bean == null) {
            throw new EntityServiceException("No entity service can be found");
        }
        if (!(bean instanceof DocumentEntityService<?>)) {
            throw new EntityServiceException("Bean is not a document entity service");
        }

        final DocumentEntityService<DocumentEntity> entityService = (DocumentEntityService<DocumentEntity>) bean;

        BaseEntity result;

        try {
            // Validate the entity name.
            NameValidationUtil.validate(entityService, action.getName());

            result = entityService.create(action.getFolder(), action.getName(), action.getPermissionInheritance());
            entityEventLog.create(result);
        } catch (final RuntimeException e) {
            entityEventLog.create(action.getType(), action.getName(), e);
            throw e;
        }

        return DocRef.create(result);
    }
}
