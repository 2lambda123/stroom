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

package stroom.index.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.AutoMarshal;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.StroomEntityManager;
import stroom.importexport.server.ImportExportHelper;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.HashSet;

@Profile(StroomSpringProfiles.PROD)
@Component("indexService")
@Transactional
@AutoMarshal
public class IndexServiceImpl extends DocumentEntityServiceImpl<Index, FindIndexCriteria> implements IndexService {
    @Inject
    IndexServiceImpl(final StroomEntityManager entityManager,
                     final ImportExportHelper importExportHelper,
                     final SecurityContext securityContext) {
        super(entityManager, importExportHelper, securityContext);
    }

    @Override
    public Class<Index> getEntityClass() {
        return Index.class;
    }

    @Override
    public FindIndexCriteria createCriteria() {
        return new FindIndexCriteria();
    }

    @Override
    protected QueryAppender<Index, FindIndexCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new IndexQueryAppender(entityManager);
    }

    @Override
    protected Index createCopy(final Index document, String copyUuid) {
        final Index index = new Index();
        index.setUuid(copyUuid);
        index.setName(document.getName());
        index.setDescription(document.getDescription());
        index.setIndexFieldsObject(document.getIndexFieldsObject());
        index.setIndexFields(document.getIndexFields());
        index.setMaxDocsPerShard(document.getMaxDocsPerShard());
        index.setShardsPerPartition(document.getShardsPerPartition());
        index.setPartitionBy(document.getPartitionBy());
        index.setPartitionSize(document.getPartitionSize());

        if (document.getVolumes() != null) {
            index.setVolumes(new HashSet<>(document.getVolumes()));
        }

        index.setRetentionDayAge(document.getRetentionDayAge());
        index.setPPartitionBy(document.getPPartitionBy());
        return index;
    }

    private static class IndexQueryAppender extends QueryAppender<Index, FindIndexCriteria> {
        private final IndexMarshaller marshaller;

        IndexQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            marshaller = new IndexMarshaller();
        }

        @Override
        protected void preSave(final Index entity) {
            super.preSave(entity);
            marshaller.marshal(entity);
        }

        @Override
        protected void postLoad(final Index entity) {
            marshaller.unmarshal(entity);
            super.postLoad(entity);
        }
    }
}