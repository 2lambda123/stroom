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

package stroom.statistics.server.common.search;

import org.springframework.context.annotation.Scope;
import stroom.dashboard.expression.FieldIndexMap;
import stroom.mapreduce.BlockingPairQueue;
import stroom.mapreduce.PairQueue;
import stroom.mapreduce.UnsafePairQueue;
import stroom.query.CompiledDepths;
import stroom.query.CompiledFields;
import stroom.query.Item;
import stroom.query.ItemMapper;
import stroom.query.ItemPartitioner;
import stroom.query.Payload;
import stroom.query.TableCoprocessorSettings;
import stroom.query.TablePayload;
import stroom.query.shared.CoprocessorSettings;
import stroom.query.shared.TableSettings;
import stroom.security.SecurityContext;
import stroom.statistics.common.StatisticDataPoint;
import stroom.statistics.common.StatisticDataSet;
import stroom.statistics.common.StatisticsFactory;
import stroom.statistics.server.common.AbstractStatistics;
import stroom.statistics.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.date.DateUtil;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@TaskHandlerBean(task = StatStoreSearchTask.class)
@Scope(value = StroomScope.TASK)
public class StatStoreSearchTaskHandler extends AbstractTaskHandler<StatStoreSearchTask, VoidResult> {
    private final TaskMonitor taskMonitor;
    private final StatisticsFactory statisticsFactory;
    private final SecurityContext securityContext;

    @Inject
    StatStoreSearchTaskHandler(final TaskMonitor taskMonitor, final StatisticsFactory statisticsFactory, final SecurityContext securityContext) {
        this.taskMonitor = taskMonitor;
        this.statisticsFactory = statisticsFactory;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final StatStoreSearchTask task) {
        try {
            securityContext.elevatePermissions();

            final StatStoreSearchResultCollector resultCollector = task.getResultCollector();

            if (!task.isTerminated()) {
                taskMonitor.info(task.getSearchName() + " - initialising");

                final StatisticStoreEntity entity = task.getEntity();

                // Get the statistic store service class based on the engine of the
                // datasource being searched
                final AbstractStatistics statisticEventStore = (AbstractStatistics) statisticsFactory
                        .instance(entity.getEngineName());
                final StatisticDataSet statisticDataSet = statisticEventStore.searchStatisticsData(task.getSearch(),
                        entity);

                // Produce payloads for each coprocessor.
                Map<Integer, Payload> payloadMap = null;

                final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);
                for (final Entry<Integer, CoprocessorSettings> entry : task.getCoprocessorMap().entrySet()) {
                    final TableSettings tableSettings = ((TableCoprocessorSettings) entry.getValue()).getTableSettings();
                    final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(),
                            tableSettings.showDetail());
                    final CompiledFields compiledFields = new CompiledFields(tableSettings.getFields(),
                            fieldIndexMap, task.getSearch().getParamMap());

                    // Create a queue of string arrays.
                    final PairQueue<String, Item> queue = new BlockingPairQueue<>(taskMonitor);
                    final ItemMapper mapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                            compiledDepths.getMaxGroupDepth());

                    performSearch(entity, mapper, statisticDataSet, fieldIndexMap);

                    // partition and reduce based on table settings.
                    final UnsafePairQueue<String, Item> outputQueue = new UnsafePairQueue<>();

                    // Create a partitioner to perform result reduction if needed.
                    final ItemPartitioner partitioner = new ItemPartitioner(compiledDepths.getDepths(),
                            compiledDepths.getMaxDepth());
                    partitioner.setOutputCollector(outputQueue);

                    // Partition the data prior to forwarding to the target node.
                    partitioner.read(queue);

                    // Perform partitioning.
                    partitioner.partition();

                    final Payload payload = new TablePayload(outputQueue);
                    if (payloadMap == null) {
                        payloadMap = new HashMap<>();
                    }
                    payloadMap.put(entry.getKey(), payload);
                }

                resultCollector.handle(payloadMap);
            }

            // Let the result handler know search has finished.
            resultCollector.getResultHandler().setComplete(true);

            return VoidResult.INSTANCE;

        } finally {
            securityContext.restorePermissions();
        }
    }

    private void performSearch(final StatisticStoreEntity dataSource, final ItemMapper mapper, final StatisticDataSet statisticDataSet,
                               final FieldIndexMap fieldIndexMap) {
        final List<String> tagsForStatistic = dataSource.getFieldNames();

        final int[] indexes = new int[7 + tagsForStatistic.size()];
        int i = 0;

        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_DATE_TIME);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_COUNT);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_VALUE);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_MIN_VALUE);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_MAX_VALUE);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_PRECISION);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_PRECISION_MS);

        for (final String tag : tagsForStatistic) {
            indexes[i++] = fieldIndexMap.get(tag);
        }

        for (final StatisticDataPoint dataPoint : statisticDataSet) {
            final Map<String, String> tagMap = dataPoint.getTagsAsMap();

            final long precisionMs = dataPoint.getPrecisionMs();

            final EventStoreTimeIntervalEnum interval = EventStoreTimeIntervalEnum.fromColumnInterval(precisionMs);
            String precisionText;
            if (interval != null) {
                precisionText = interval.longName();
            } else {
                // could be a precision that doesn't match one of our interval
                // sizes
                precisionText = "-";
            }

            final String[] data = new String[fieldIndexMap.size()];
            i = 0;

            if (indexes[i] != -1) {
                data[indexes[i]] = DateUtil.createNormalDateTimeString(dataPoint.getTimeMs());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = String.valueOf(dataPoint.getCount());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = String.valueOf(dataPoint.getValue());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = String.valueOf(dataPoint.getMinValue());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = String.valueOf(dataPoint.getMaxValue());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = precisionText;
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = Long.toString(precisionMs);
            }
            i++;

            for (final String tag : tagsForStatistic) {
                if (indexes[i] != -1) {
                    data[indexes[i]] = tagMap.get(tag);
                }
                i++;
            }

            mapper.collect(null, data);
        }
    }
}
