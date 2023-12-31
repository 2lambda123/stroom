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

package stroom.index.server;

import event.logging.BaseAdvancedQueryItem;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.FieldMap;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.PermissionException;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexShardService;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.node.shared.VolumeService;
import stroom.security.Insecure;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

@Component
@Profile(StroomSpringProfiles.PROD)
@Insecure
@Transactional
public class IndexShardServiceImpl
        extends SystemEntityServiceImpl<IndexShard, FindIndexShardCriteria> implements IndexShardService {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(IndexShardServiceImpl.class);

    private static final String VOLUME_ERROR = "One or more volumes must been assigned to an index for a shard to be created";

    private final VolumeService volumeService;
    private final SecurityContext securityContext;

    @Inject
    IndexShardServiceImpl(final StroomEntityManager entityManager, final VolumeService volumeService, final SecurityContext securityContext) {
        super(entityManager);
        this.volumeService = volumeService;
        this.securityContext = securityContext;
    }

    @Override
    public IndexShard createIndexShard(final IndexShardKey indexShardKey, final Node ownerNode) {
        final Index index = indexShardKey.getIndex();
        if (index.getVolumes() == null || index.getVolumes().size() == 0) {
            LOGGER.debug(VOLUME_ERROR);
            throw new IndexException(VOLUME_ERROR);
        }

        final Set<Volume> volumes = volumeService.getIndexVolumeSet(ownerNode, index.getVolumes());

        // The first set should be a set of cache volumes unless no caches have
        // been defined or they are full.
        Volume volume = null;

        if (volumes != null && volumes.size() > 0) {
            volume = volumes.iterator().next();
        }
        if (volume == null) {
            throw new IndexException("No shard can be created as no volumes are available for index: " + index.getName()
                    + " (" + index.getId() + ")");
        }

        final IndexShard indexShard = new IndexShard();
        indexShard.setIndex(index);
        indexShard.setNode(ownerNode);
        indexShard.setPartition(indexShardKey.getPartition());
        indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
        indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
        indexShard.setVolume(volume);
        indexShard.setIndexVersion(LuceneVersionUtil.getCurrentVersion());

        return save(indexShard);
    }

    @Override
    public Class<IndexShard> getEntityClass() {
        return IndexShard.class;
    }

    @Override
    public FindIndexShardCriteria createCriteria() {
        return new FindIndexShardCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindIndexShardCriteria criteria) {
        CriteriaLoggingUtil.appendRangeTerm(items, "documentCountRange", criteria.getDocumentCountRange());
        CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "volumeIdSet", criteria.getVolumeIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "indexIdSet", criteria.getIndexShardSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "indexShardStatusSet", criteria.getIndexShardStatusSet());
        CriteriaLoggingUtil.appendStringTerm(items, "partition", criteria.getPartition().getString());

        super.appendCriteria(items, criteria);
    }

    @Secured(IndexShard.MANAGE_INDEX_SHARDS_PERMISSION)
    @Override
    public Boolean delete(final IndexShard entity) throws RuntimeException {
        final Index index = entity.getIndex();
        if (!securityContext.hasDocumentPermission(index.getType(), index.getUuid(), DocumentPermissionNames.DELETE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to delete index shard");
        }

        return super.delete(entity);
    }

    @Override
    protected QueryAppender<IndexShard, FindIndexShardCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new IndexShardQueryAppender(entityManager);
    }

    @Override
    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindIndexShardCriteria.FIELD_PARTITION, IndexShard.PARTITION, "partition");
    }

    private static class IndexShardQueryAppender extends QueryAppender<IndexShard, FindIndexShardCriteria> {
        public IndexShardQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null) {
                if (fetchSet.contains(Node.ENTITY_TYPE)) {
                    sql.append(" INNER JOIN FETCH " + alias + ".node");
                }
                if (fetchSet.contains(Volume.ENTITY_TYPE)) {
                    sql.append(" INNER JOIN FETCH " + alias + ".volume");
                }
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias,
                                           final FindIndexShardCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            sql.appendEntityIdSetQuery(alias + ".index", criteria.getIndexIdSet());
            sql.appendEntityIdSetQuery(alias, criteria.getIndexShardSet());
            sql.appendEntityIdSetQuery(alias + ".node", criteria.getNodeIdSet());
            sql.appendEntityIdSetQuery(alias + ".volume", criteria.getVolumeIdSet());
            sql.appendPrimitiveValueSetQuery(alias + ".pstatus", criteria.getIndexShardStatusSet());
            sql.appendRangeQuery(alias + ".documentCount", criteria.getDocumentCountRange());
            sql.appendValueQuery(alias + ".partition", criteria.getPartition());
        }
    }
}
