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
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.EntityServiceMoveAction;
import stroom.logging.EntityEventLog;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceMoveAction.class)
@Scope(value = StroomScope.TASK)
class EntityServiceMoveHandler
        extends AbstractTaskHandler<EntityServiceMoveAction<DocumentEntity>, DocumentEntity> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final EntityEventLog entityEventLog;

    @Inject
    EntityServiceMoveHandler(final EntityServiceBeanRegistry beanRegistry, final EntityEventLog entityEventLog) {
        this.beanRegistry = beanRegistry;
        this.entityEventLog = entityEventLog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DocumentEntity exec(final EntityServiceMoveAction<DocumentEntity> action) {
        final Object bean = beanRegistry.getEntityService(action.getEntity().getClass());
        if (bean == null) {
            throw new EntityServiceException("No entity service can be found");
        }
        if (!(bean instanceof DocumentEntityService<?>)) {
            throw new EntityServiceException("Bean is not a document entity service");
        }

        final DocumentEntityService<DocumentEntity> entityService = (DocumentEntityService<DocumentEntity>) bean;

        DocumentEntity result;

        final DocumentEntity entity = action.getEntity();

        try {
            // Validate the entity name.
            NameValidationUtil.validate(entityService, entity);

            result = entityService.move(entity, action.getFolder(), action.getPermissionInheritance());
            entityEventLog.move(entity, result);
        } catch (final RuntimeException e) {
            entityEventLog.move(entity, entity, e);
            throw e;
        }

        return result;
    }
}
