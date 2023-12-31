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

package stroom.jobsystem.server;

import org.springframework.context.annotation.Scope;
import stroom.jobsystem.server.JobNodeTrackerCache.Trackers;
import stroom.jobsystem.shared.JobNode;
import stroom.jobsystem.shared.JobNodeInfo;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.scheduler.Scheduler;
import stroom.util.shared.SharedMap;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Collection;

@TaskHandlerBean(task = JobNodeInfoClusterTask.class)
@Scope(value = StroomScope.TASK)
public class JobNodeInfoClusterHandler
        extends AbstractTaskHandler<JobNodeInfoClusterTask, SharedMap<JobNode, JobNodeInfo>> {
    private final JobNodeTrackerCache jobNodeTrackerCache;

    @Inject
    JobNodeInfoClusterHandler(final JobNodeTrackerCache jobNodeTrackerCache) {
        this.jobNodeTrackerCache = jobNodeTrackerCache;
    }

    @Override
    public stroom.util.shared.SharedMap<JobNode, JobNodeInfo> exec(final JobNodeInfoClusterTask task) {
        final SharedMap<JobNode, JobNodeInfo> result = new SharedMap<>();
        final Trackers trackers = jobNodeTrackerCache.getTrackers();
        if (trackers != null) {
            final Collection<JobNodeTracker> trackerList = trackers.getTrackerList();
            if (trackerList != null) {
                for (final JobNodeTracker tracker : trackerList) {
                    final JobNode jobNode = tracker.getJobNode();
                    final int currentTaskCount = tracker.getCurrentTaskCount();

                    Long scheduleReferenceTime = null;
                    final Scheduler scheduler = trackers.getScheduler(jobNode);
                    if (scheduler != null) {
                        scheduleReferenceTime = scheduler.getScheduleReferenceTime();
                    }

                    final JobNodeInfo info = new JobNodeInfo(currentTaskCount, scheduleReferenceTime,
                            tracker.getLastExecutedTime());
                    result.put(jobNode, info);
                }
            }
        }

        return result;
    }
}
