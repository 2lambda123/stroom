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

package stroom.streamtask.server;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.shared.Period;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.StroomPropertyService;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.streamtask.shared.StreamProcessorFilterTracker;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.TaskStatus;
import stroom.util.date.DateUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Scope(value = StroomScope.TASK)
public class StreamTaskDeleteExecutor extends AbstractBatchDeleteExecutor {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StreamTaskDeleteExecutor.class);

    private static final String TASK_NAME = "Stream Task Delete Executor";
    private static final String LOCK_NAME = "StreamTaskDeleteExecutor";
    private static final String STREAM_TASKS_DELETE_AGE_PROPERTY = "stroom.streamTask.deleteAge";
    private static final String STREAM_TASKS_DELETE_BATCH_SIZE_PROPERTY = "stroom.streamTask.deleteBatchSize";
    private static final int DEFAULT_STREAM_TASK_DELETE_BATCH_SIZE = 1000;
    private static final String TEMP_STRM_TASK_ID_TABLE = "TEMP_STRM_TASK_ID";

    private final StreamTaskCreatorImpl streamTaskCreator;
    private final StreamProcessorFilterService streamProcessorFilterService;

    @Inject
    public StreamTaskDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
                                    final ClusterLockService clusterLockService, final StroomPropertyService propertyService,
                                    final TaskMonitor taskMonitor, final StreamTaskCreatorImpl streamTaskCreator,
                                    final StreamProcessorFilterService streamProcessorFilterService) {
        super(batchIdTransactionHelper, clusterLockService, propertyService, taskMonitor, TASK_NAME, LOCK_NAME,
                STREAM_TASKS_DELETE_AGE_PROPERTY, STREAM_TASKS_DELETE_BATCH_SIZE_PROPERTY,
                DEFAULT_STREAM_TASK_DELETE_BATCH_SIZE, TEMP_STRM_TASK_ID_TABLE);
        this.streamTaskCreator = streamTaskCreator;
        this.streamProcessorFilterService = streamProcessorFilterService;
    }

    @StroomFrequencySchedule("1m")
    @JobTrackedSchedule(jobName = "Stream Task Retention", description = "Physically delete stream tasks that have been logically deleted or complete based on age ("
            + STREAM_TASKS_DELETE_AGE_PROPERTY + ")")
    public void exec() {
        final AtomicLong nextDeleteMs = streamTaskCreator.getNextDeleteMs();

        try {
            if (nextDeleteMs.get() == 0) {
                LOGGER.debug("deleteSchedule() - no schedule set .... maybe we aren't in charge of creating tasks");
            } else {
                LOGGER.debug("deleteSchedule() - nextDeleteMs=%s",
                        DateUtil.createNormalDateTimeString(nextDeleteMs.get()));
                // Have we gone past our next delete schedule?
                if (nextDeleteMs.get() < System.currentTimeMillis()) {
                    lockAndDelete();
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    @Override
    public void delete(final long age) {
        super.delete(age);
        deleteOldFilters(age);
    }

    @Override
    protected void deleteCurrentBatch(final long total) {
        // Delete stream tasks.
        deleteWithJoin(StreamTask.TABLE_NAME, StreamTask.ID, "stream tasks", total);
    }

    @Override
    protected SqlBuilder getTempIdSelectSql(final long age, final int batchSize) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT ");
        sql.append(StreamTask.ID);
        sql.append(" FROM ");
        sql.append(StreamTask.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(StreamTask.STATUS);
        sql.append(" IN (");
        sql.append(TaskStatus.COMPLETE.getPrimitiveValue());
        sql.append(", ");
        sql.append(TaskStatus.FAILED.getPrimitiveValue());
        sql.append(") AND (");
        sql.append(StreamTask.CREATE_MS);
        sql.append(" IS NULL OR ");
        sql.append(StreamTask.CREATE_MS);
        sql.append(" < ");
        sql.arg(age);
        sql.append(")");
        sql.append(" ORDER BY ");
        sql.append(StreamTask.ID);
        sql.append(" LIMIT ");
        sql.arg(batchSize);
        return sql;
    }

    private void deleteOldFilters(final long age) {
        try {
            // Get all filters that have not been polled for a while.
            final FindStreamProcessorFilterCriteria criteria = new FindStreamProcessorFilterCriteria();
            criteria.setLastPollPeriod(new Period(null, age));
            final List<StreamProcessorFilter> filters = streamProcessorFilterService.find(criteria);
            for (final StreamProcessorFilter filter : filters) {
                final StreamProcessorFilterTracker tracker = filter.getStreamProcessorFilterTracker();

                if (tracker != null && StreamProcessorFilterTracker.COMPLETE.equals(tracker.getStatus())) {
                    // The tracker thinks that no more tasks will ever be
                    // created for this filter so we can delete it if there are
                    // no remaining tasks for this filter.
                    //
                    // The database constraint will not allow filters to be
                    // deleted that still have associated tasks.
                    try {
                        LOGGER.debug("deleteCompleteOrFailedTasks() - Removing old complete filter %s", filter);
                        streamProcessorFilterService.delete(filter);

                    } catch (final Throwable t) {
                        // The database constraint will not allow filters to be
                        // deleted that still have associated tasks. This is
                        // what we want to happen but output debug here to help
                        // diagnose problems.
                        LOGGER.debug("deleteCompleteOrFailedTasks() - Failed as tasks still remain for this filter - "
                                + t.getMessage(), t);
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }
}
