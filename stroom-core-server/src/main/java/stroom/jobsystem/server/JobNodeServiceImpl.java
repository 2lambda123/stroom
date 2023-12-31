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

import event.logging.BaseAdvancedQueryItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.jobsystem.shared.FindJobCriteria;
import stroom.jobsystem.shared.FindJobNodeCriteria;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.jobsystem.shared.JobNode.JobType;
import stroom.jobsystem.shared.JobNodeService;
import stroom.jobsystem.shared.JobService;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.security.Secured;
import stroom.util.logging.StroomLogger;
import stroom.util.scheduler.SimpleCron;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomBeanMethod;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Transactional
@Secured(Job.MANAGE_JOBS_PERMISSION)
@Component
public class JobNodeServiceImpl extends SystemEntityServiceImpl<JobNode, FindJobNodeCriteria> implements JobNodeService {
    public static final String DELETE_ORPHAN_JOBS_MYSQL = "DELETE JB FROM " + Job.TABLE_NAME + " JB LEFT OUTER JOIN "
            + JobNode.TABLE_NAME + " JB_ND ON (JB." + Job.ID + " = JB_ND." + Job.FOREIGN_KEY + ") WHERE JB_ND."
            + JobNode.ID + " IS NULL;";
    public static final String DELETE_ORPHAN_JOBS_HSQLDB = "DELETE FROM " + Job.TABLE_NAME + " WHERE " + Job.ID
            + " IN (" + "SELECT " + Job.ID + " FROM " + Job.TABLE_NAME + " JB LEFT OUTER JOIN " + JobNode.TABLE_NAME
            + " JB_ND ON (JB." + Job.ID + " = JB_ND." + Job.FOREIGN_KEY + ") WHERE JB_ND." + JobNode.ID + " IS NULL);";
    private static final StroomLogger LOGGER = StroomLogger.getLogger(JobNodeServiceImpl.class);
    private static final String LOCK_NAME = "JobNodeService";

    private final StroomEntityManager entityManager;
    private final ClusterLockService clusterLockService;
    private final NodeCache nodeCache;
    private final JobService jobService;
    private final StroomBeanStore stroomBeanStore;
    private final StroomDatabaseInfo stroomDatabaseInfo;


    @Inject
    JobNodeServiceImpl(final StroomEntityManager entityManager, final ClusterLockService clusterLockService,
                       final NodeCache nodeCache, final JobService jobService, final StroomBeanStore stroomBeanStore,
                       final StroomDatabaseInfo stroomDatabaseInfo) {
        super(entityManager);
        this.entityManager = entityManager;
        this.clusterLockService = clusterLockService;
        this.nodeCache = nodeCache;
        this.jobService = jobService;
        this.stroomBeanStore = stroomBeanStore;
        this.stroomDatabaseInfo = stroomDatabaseInfo;
    }

    @Override
//    @Secured(permission = DocumentPermissionNames.UPDATE)
    public JobNode save(final JobNode entity) throws RuntimeException {
        // We always want to update a job instance even if we have a stale
        // version.
        if (entity.isPersistent()) {
            final JobNode tmp = load(entity);
            entity.setVersion(tmp.getVersion());
        }

        // Stop Job Nodes being saved with invalid crons.
        if (JobType.CRON.equals(entity.getJobType())) {
            if (entity.getSchedule() != null) {
                // This will throw a runtime exception if the expression is
                // invalid.
                SimpleCron.compile(entity.getSchedule());
            }
        }
        if (JobType.FREQUENCY.equals(entity.getJobType())) {
            if (entity.getSchedule() != null) {
                // This will throw a runtime exception if the expression is
                // invalid.
                ModelStringUtil.parseDurationString(entity.getSchedule());
            }
        }

        return super.save(entity);
    }

    private List<JobNode> findAllJobs(final Node node) {
        // See if the job exists in the database.
        final FindJobNodeCriteria criteria = new FindJobNodeCriteria();
        criteria.getFetchSet().add(Job.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.getNodeIdSet().add(node);
        return find(criteria);

    }

    @Override
    @StroomStartup
    public void startup() {
        LOGGER.info("startup()");
        // Lock the cluster so only 1 node at a time can call the
        // following code.
        LOGGER.trace("Locking the cluster");
        clusterLockService.lock(LOCK_NAME);

        final Node node = nodeCache.getDefaultNode();

        final List<JobNode> existingJobList = findAllJobs(node);
        final Map<String, JobNode> existingJobMap = new HashMap<>();
        for (final JobNode jobNode : existingJobList) {
            existingJobMap.put(jobNode.getJob().getName(), jobNode);
        }

        final Set<String> validJobNames = new HashSet<>();

        for (final StroomBeanMethod stroomBeanMethod : stroomBeanStore.getStroomBeanMethod(JobTrackedSchedule.class)) {
            final JobTrackedSchedule jobScheduleDescriptor = stroomBeanMethod.getBeanMethod()
                    .getAnnotation(JobTrackedSchedule.class);
            final StroomSimpleCronSchedule stroomSimpleCronSchedule = stroomBeanMethod.getBeanMethod()
                    .getAnnotation(StroomSimpleCronSchedule.class);
            final StroomFrequencySchedule stroomFrequencySchedule = stroomBeanMethod.getBeanMethod()
                    .getAnnotation(StroomFrequencySchedule.class);

            validJobNames.add(jobScheduleDescriptor.jobName());

            if (stroomFrequencySchedule == null && stroomSimpleCronSchedule == null) {
                LOGGER.error("Invalid annotations on %s", stroomBeanMethod);
                continue;
            }

            // Get the actual job.
            Job job = new Job();
            job.setName(jobScheduleDescriptor.jobName());
            job.setEnabled(jobScheduleDescriptor.enabled());
            job = getOrCreateJob(job);

            final JobNode newJobNode = new JobNode();
            newJobNode.setJob(job);
            newJobNode.setNode(node);
            newJobNode.setEnabled(jobScheduleDescriptor.enabled());
            if (stroomSimpleCronSchedule != null) {
                newJobNode.setJobType(JobType.CRON);
                newJobNode.setSchedule(stroomSimpleCronSchedule.cron());
            } else if (stroomFrequencySchedule != null) {
                newJobNode.setJobType(JobType.FREQUENCY);
                newJobNode.setSchedule(stroomFrequencySchedule.value());
            }

            // Add the job node to the DB if it isn't there already.
            JobNode existingJobNode = existingJobMap.get(jobScheduleDescriptor.jobName());
            if (existingJobNode == null) {
                LOGGER.info("Adding JobNode '%s' for node '%s'", newJobNode.getJob().getName(),
                        newJobNode.getNode().getName());
                save(newJobNode);
                existingJobMap.put(newJobNode.getJob().getName(), newJobNode);

            } else if (!newJobNode.getJobType().equals(existingJobNode.getJobType())) {
                // If the job type has changed then update the job node.
                existingJobNode.setJobType(newJobNode.getJobType());
                existingJobNode.setSchedule(newJobNode.getSchedule());
                existingJobNode = save(existingJobNode);
                existingJobMap.put(jobScheduleDescriptor.jobName(), existingJobNode);
            }
        }

        // Distributed Jobs done a different way
        for (final String beanFactory : stroomBeanStore.getStroomBean(DistributedTaskFactoryBean.class)) {
            final DistributedTaskFactoryBean distributedTaskFactoryBean = stroomBeanStore.findAnnotationOnBean(beanFactory,
                    DistributedTaskFactoryBean.class);
            validJobNames.add(distributedTaskFactoryBean.jobName());

            // Add the job node to the DB if it isn't there already.
            final JobNode existingJobNode = existingJobMap.get(distributedTaskFactoryBean.jobName());
            if (existingJobNode == null) {
                // Get the actual job.
                Job job = new Job();
                job.setName(distributedTaskFactoryBean.jobName());
                job.setEnabled(false);
                job = getOrCreateJob(job);

                final JobNode newJobNode = new JobNode();
                newJobNode.setJob(job);
                newJobNode.setNode(node);
                newJobNode.setEnabled(false);
                newJobNode.setJobType(JobType.DISTRIBUTED);

                LOGGER.info("Adding JobNode '%s' for node '%s'", newJobNode.getJob().getName(),
                        newJobNode.getNode().getName());
                save(newJobNode);
                existingJobMap.put(newJobNode.getJob().getName(), newJobNode);
            }
        }

        existingJobList.stream().filter(jobNode -> !validJobNames.contains(jobNode.getJob().getName()))
                .forEach(jobNode -> {
                    LOGGER.info("Removing old job node %s ", jobNode.getJob().getName());
                    delete(jobNode);
                });

        // Force to delete
        entityManager.flush();

        final SqlBuilder sql = new SqlBuilder();
        if (stroomDatabaseInfo.isMysql()) {
            sql.append(DELETE_ORPHAN_JOBS_MYSQL);
        } else {
            sql.append(DELETE_ORPHAN_JOBS_HSQLDB);
        }

        final Long deleteCount = entityManager.executeNativeUpdate(sql);
        if (deleteCount != null && deleteCount > 0) {
            LOGGER.info("Removed %s orhan jobs", deleteCount);
        }

    }

    private Job getOrCreateJob(final Job job) {
        Job result = null;

        // During unit testing jobs are deleted from the database
        // and need to be added back in but because they are added in
        // previous tests they have an id. To persist them again we need
        // to remove the id.
        job.setId(-1);

        // See if the job exists in the database.
        final FindJobCriteria criteria = new FindJobCriteria();
        criteria.getName().setString(job.getName());

        // Add the job to the DB if it isn't there already.
        final BaseResultList<Job> existingJob = jobService.find(criteria);
        if (existingJob != null && existingJob.size() > 0) {
            result = existingJob.getFirst();

            // Update the job description if we need to.
            if (job.getDescription() != null && !job.getDescription().equals(result.getDescription())) {
                result.setDescription(job.getDescription());
                LOGGER.info("Updating Job     '%s'", job.getName());
                result = jobService.save(result);
            }
        } else {
            LOGGER.info("Adding Job     '%s'", job.getName());
            result = jobService.save(job);
        }

        return result;
    }

    @Override
    public Class<JobNode> getEntityClass() {
        return JobNode.class;
    }

    @Override
    public FindJobNodeCriteria createCriteria() {
        return new FindJobNodeCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindJobNodeCriteria criteria) {
        CriteriaLoggingUtil.appendStringTerm(items, "jobName", criteria.getJobName());
        CriteriaLoggingUtil.appendEntityIdSet(items, "jobIdSet", criteria.getJobIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<JobNode, FindJobNodeCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new JobNodeQueryAppender(entityManager);
    }

    private static class JobNodeQueryAppender extends QueryAppender<JobNode, FindJobNodeCriteria> {
        public JobNodeQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null) {
                if (fetchSet.contains(Node.ENTITY_TYPE)) {
                    sql.append(" INNER JOIN FETCH " + alias + ".node");
                }
                if (fetchSet.contains(Job.ENTITY_TYPE)) {
                    sql.append(" INNER JOIN FETCH " + alias + ".job");
                }
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias, final FindJobNodeCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
            sql.appendEntityIdSetQuery(alias + ".node", criteria.getNodeIdSet());
            sql.appendEntityIdSetQuery(alias + ".job", criteria.getJobIdSet());
            sql.appendValueQuery(alias + ".job.name", criteria.getJobName());
        }
    }
}
