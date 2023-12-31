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

import stroom.entity.shared.Entity;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.BaseEntityService;
import stroom.entity.shared.FindService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class MockEntityService<E extends Entity, C extends BaseCriteria>
        implements BaseEntityService<E>, FindService<E, C>, Clearable {
    private static final Set<String> BLANK_SET = Collections.emptySet();
    protected final Map<Object, E> map = new ConcurrentHashMap<>();
    private final AtomicLong currentId = new AtomicLong();
    private String entityType;

    @Override
    public void clear() {
        map.clear();
        currentId.set(0);
    }

    public boolean isMatch(final C criteria, final E entity) {
//        if (entity instanceof NamedEntity && criteria instanceof FindNamedEntityCriteria) {
//            return ((FindNamedEntityCriteria) criteria).getName().isMatch(((NamedEntity) entity).getName());
//        }
        return true;
    }

    @Override
    public BaseResultList<E> find(final C criteria) throws RuntimeException {
        final List<E> list = new ArrayList<>();
        for (final E entity : map.values()) {
            if (criteria == null || isMatch(criteria, entity)) {
                list.add(entity);
            }
        }
        return BaseResultList.createUnboundedList(list);
    }

//    @Override
//    public E create(final E entity) throws RuntimeException {
//        if (entity.isPersistent()) {
//            throw new EntityServiceException("Entity is already persistent");
//        }
//        return save(entity);
//    }

    @Override
    public E load(final E entity) throws RuntimeException {
        return load(entity, BLANK_SET);
    }


    @Override
    public E load(final E entity, final Set<String> fetchSet) throws RuntimeException {
        if (entity == null) {
            return null;
        }
        return map.get(entity.getPrimaryKey());
    }

    @Override
    public E loadById(final long id) throws RuntimeException {
        return loadById(id, BLANK_SET);
    }

    @Override
    public E loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
        return map.get(id);
    }

    @Override
    public E loadByIdInsecure(final long id, final Set<String> fetchSet) throws RuntimeException {
        return map.get(id);
    }

    @Override
    public E save(final E entity) {
        if (!entity.isPersistent()) {
            if (entity instanceof BaseEntity) {
                ((BaseEntity) entity).setId(currentId.incrementAndGet());
            }
        }
        map.put(entity.getPrimaryKey(), entity);
        return entity;
    }

    @Override
    public Boolean delete(final E entity) {
        if (map.remove(entity.getPrimaryKey()) != null) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    @Override
    public C createCriteria() {
        return null;
    }

    public abstract Class<E> getEntityClass();

    public String getEntityType() {
        if (entityType == null) {
            try {
                entityType = getEntityClass().newInstance().getType();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
        return entityType;
    }
}
