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

package stroom.statistics.server.common.engines;

import stroom.entity.shared.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.node.server.StroomPropertyService;
import stroom.query.shared.DataSource;
import stroom.statistics.common.CommonStatisticConstants;
import stroom.statistics.common.FindStatisticsEntityCriteria;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.logging.StroomLogger;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ProvidesExplorerData
@Component
public class StatisticsDataSourceExplorerDataProvider
        extends AbstractExplorerDataProvider<StatisticStoreEntity, FindStatisticsEntityCriteria> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StatisticsDataSourceExplorerDataProvider.class);

    private static final Set<String> tags = new HashSet<>();
    static {
        tags.add(DataSource.DATA_SOURCE);
    }

    private final StatisticStoreEntityService statisticsDataSourceService;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    StatisticsDataSourceExplorerDataProvider(@Named("cachedFolderService") final FolderService cachedFolderService, final StatisticStoreEntityService statisticsDataSourceService,
                                             final StroomPropertyService stroomPropertyService) {
        super(cachedFolderService);
        LOGGER.debug("Initialising: %s", this.getClass().getCanonicalName());
        this.statisticsDataSourceService = statisticsDataSourceService;
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        final String enabledEngines = stroomPropertyService
                .getProperty(CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME);

        List<String> enabledEnginesList = new ArrayList<>();
        if (enabledEngines != null && enabledEngines.length() > 0) {
            final String[] engines = enabledEngines.split(",");
            Arrays.stream(engines).forEach(enabledEnginesList::add);
        }

        // newly created, but not yet enabled datasources will be in the DB with
        // an engine of Not Set so need to include
        // them
        enabledEnginesList.add(StatisticStoreEntity.NOT_SET);

        final FindStatisticsEntityCriteria criteria = FindStatisticsEntityCriteria
                .instanceByEngineNames(enabledEnginesList);

        addItems(statisticsDataSourceService, treeModel, criteria);
    }

    @Override
    protected EntityData createEntityData(final StatisticStoreEntity entity) {
        final EntityData entityData = super.createEntityData(entity);
        entityData.setTags(tags);
        return entityData;
    }

    @Override
    public String getType() {
        return StatisticStoreEntity.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return StatisticStoreEntity.ENTITY_TYPE_FOR_DISPLAY;
    }

    @Override
    public int getPriority() {
        return 11;
    }
}
