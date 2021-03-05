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

package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.AssignTasksRequest;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import event.logging.Query;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@AutoLogged
class ProcessorTaskResourceImpl implements ProcessorTaskResource {
    private final Provider<ProcessorTaskService> processorTaskServiceProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<ProcessorTaskManager> processorTaskManagerProvider;

    @Inject
    ProcessorTaskResourceImpl(final Provider<ProcessorTaskService> processorTaskServiceProvider,
                              final Provider<DocumentEventLog> documentEventLogProvider,
                              final Provider<NodeService> nodeServiceProvider,
                              final Provider<NodeInfo> nodeInfoProvider,
                              final Provider<WebTargetFactory> webTargetFactoryProvider,
                              final Provider<ProcessorTaskManager> processorTaskManagerProvider) {
        this.processorTaskServiceProvider = processorTaskServiceProvider;
        this.documentEventLogProvider = documentEventLogProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.processorTaskManagerProvider = processorTaskManagerProvider;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public ResultPage<ProcessorTask> find(final ExpressionCriteria criteria) {
        ResultPage<ProcessorTask> result;

        final Query.Builder<Void> queryBuilder = Query.builder();
        StroomEventLoggingUtil.appendExpression(queryBuilder, criteria.getExpression());
        final Query query = queryBuilder.build();

        try {
            result = processorTaskServiceProvider.get().find(criteria);
            documentEventLogProvider.get().search(
                    criteria.getClass().getSimpleName(),
                    query,
                    ProcessorTask.class.getSimpleName(),
                    result.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().search(
                    criteria.getClass().getSimpleName(),
                    query,
                    ProcessorTask.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }

        return result;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        ResultPage<ProcessorTaskSummary> result;

        final Query.Builder<Void> queryBuilder = Query.builder();
        StroomEventLoggingUtil.appendExpression(queryBuilder, criteria.getExpression());
        final Query query = queryBuilder.build();

        try {
            result = processorTaskServiceProvider.get().findSummary(criteria);
            documentEventLogProvider.get().search(
                    criteria.getClass().getSimpleName(),
                    query,
                    ProcessorTaskSummary.class.getSimpleName(),
                    result.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().search(
                    criteria.getClass().getSimpleName(),
                    query,
                    ProcessorTaskSummary.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }

        return result;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ProcessorTaskList assignTasks(final String nodeName, final AssignTasksRequest request) {
        // If this is the node that was contacted then just return the latency we have incurred within this method.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            return processorTaskManagerProvider.get().assignTasks(request.getNodeName(), request.getCount());

        } else {
            final String url =
                    NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    ProcessorTaskResource.BASE_PATH,
                    ProcessorTaskResource.ASSIGN_TASKS_PATH_PART,
                    nodeName);

            try {
                final Response response = webTargetFactoryProvider.get()
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                return response.readEntity(ProcessorTaskList.class);
            } catch (Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public Boolean abandonTasks(final String nodeName, final ProcessorTaskList request) {
        // If this is the node that was contacted then just return the latency we have incurred within this method.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            return processorTaskManagerProvider.get().abandonTasks(request);

        } else {
            final String url =
                    NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    ProcessorTaskResource.BASE_PATH,
                    ProcessorTaskResource.ABANDON_TASKS_PATH_PART,
                    nodeName);

            try {
                final Response response = webTargetFactoryProvider.get()
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .put(Entity.json(request));
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                return response.readEntity(Boolean.class);
            } catch (Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }
}
