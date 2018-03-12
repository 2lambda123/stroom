/*
 * Copyright 2017 Crown Copyright
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

package stroom.entity;

import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityServiceFindReferenceAction;
import stroom.entity.shared.ResultList;
import stroom.query.api.v2.DocRef;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceFindReferenceAction.class)
class EntityServiceFindReferenceHandler
        extends AbstractTaskHandler<EntityServiceFindReferenceAction<BaseEntity>, ResultList<DocRef>> {
    private final EntityServiceBeanRegistry beanRegistry;

    @Inject
    EntityServiceFindReferenceHandler(final EntityServiceBeanRegistry beanRegistry) {
        this.beanRegistry = beanRegistry;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ResultList<DocRef> exec(final EntityServiceFindReferenceAction<BaseEntity> action) {
        final Object entityService = beanRegistry.getEntityServiceByType(action.getEntity().getType());
        return (ResultList<DocRef>) beanRegistry.invoke(entityService, "findReference", action.getEntity());
    }
}
