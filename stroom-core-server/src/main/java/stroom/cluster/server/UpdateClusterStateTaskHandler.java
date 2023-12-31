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

package stroom.cluster.server;

import org.springframework.context.annotation.Scope;
import stroom.node.server.NodeCache;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TaskHandlerBean(task = UpdateClusterStateTask.class)
@Scope(StroomScope.TASK)
class UpdateClusterStateTaskHandler extends AbstractTaskHandler<UpdateClusterStateTask, VoidResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(UpdateClusterStateTaskHandler.class);

    private final NodeService nodeService;
    private final NodeCache nodeCache;
    private final ClusterCallServiceRemote clusterCallServiceRemote;
    private final TaskManager taskManager;

    @Inject
    UpdateClusterStateTaskHandler(final NodeService nodeService, final NodeCache nodeCache, final ClusterCallServiceRemote clusterCallServiceRemote, final TaskManager taskManager) {
        this.nodeService = nodeService;
        this.nodeCache = nodeCache;
        this.clusterCallServiceRemote = clusterCallServiceRemote;
        this.taskManager = taskManager;
    }

    @Override
    public VoidResult exec(final UpdateClusterStateTask task) {
        // We sometimes want to wait a bit before we try and establish the
        // cluster state. This is often the case during startup when multiple
        // nodes start to ping each other which triggers an update but we want
        // to give other nodes some time to start also.
        if (task.getDelay() > 0) {
            int remaining = task.getDelay();
            while (!task.isTerminated() && remaining > 0) {
                ThreadUtil.sleep(100);
                remaining -= 100;
            }
        }

        updateState(task);

        return VoidResult.INSTANCE;
    }

    private void updateState(final UpdateClusterStateTask task) {
        final ClusterState clusterState = task.getClusterState();

        Node thisNode = nodeCache.getDefaultNode();

        // Get nodes and ensure uniqueness.
        final Set<Node> uniqueNodes = new HashSet<>();
        final List<Node> nodes = nodeService.find(new FindNodeCriteria());
        if (nodes != null) {
            uniqueNodes.addAll(nodes);
        }
        clusterState.setAllNodes(uniqueNodes);

        // Get a list of enabled nodes and ensure we have the latest version of
        // the local node, i.e. not cached.
        final Set<Node> enabledNodes = new HashSet<>();
        for (final Node node : clusterState.getAllNodes()) {
            if (node.isEnabled()) {
                enabledNodes.add(node);
            }

            // Make sure we have the most up to date copy of this node.
            if (node.equalsEntity(thisNode)) {
                thisNode = node;
            }
        }
        clusterState.setEnabledNodes(enabledNodes);

        // Create a set of active nodes, i.e. nodes we can contact at this
        // time.
        if (task.isTestActiveNodes()) {
            updateActiveNodes(task, thisNode, enabledNodes);

            // Determine which node should be the master.
            int maxPriority = -1;
            Node masterNode = null;
            for (final Node node : enabledNodes) {
                if (node.getPriority() > maxPriority) {
                    maxPriority = node.getPriority();
                    masterNode = node;
                }
            }

            clusterState.setMasterNode(masterNode);
        }

        clusterState.setUpdateTime(System.currentTimeMillis());
    }

    private void updateActiveNodes(final UpdateClusterStateTask updateClusterStateTask, final Node thisNode, final Set<Node> enabledNodes) {
        final ClusterState clusterState = updateClusterStateTask.getClusterState();

        // Only retain active nodes that are currently enabled.
        retainEnabledActiveNodes(clusterState, enabledNodes);

        // Now m
        for (final Node node : enabledNodes) {
            if (node.equals(thisNode)) {
                addEnabledActiveNode(clusterState, node);
            } else {
                final GenericServerTask genericServerTask = GenericServerTask.create(updateClusterStateTask,
                        "Get Active Nodes", "Getting active nodes");
                genericServerTask.setRunnable(() -> {
                    try {
                        // We call the API like this rather than using the
                        // Cluster API as the Cluster API will trigger a
                        // discover call (cyclic dependency).
                        // clusterCallServiceRemote will call getThisNode but
                        // that's OK as we have worked it out above.
                        clusterCallServiceRemote.call(thisNode, node, ClusterNodeManager.BEAN_NAME,
                                ClusterNodeManager.PING_METHOD, new Class<?>[]{Node.class},
                                new Object[]{thisNode});
                        addEnabledActiveNode(clusterState, node);

                    } catch (final Throwable ex) {
                        LOGGER.warn("discover() - unable to contact %s - %s", node.getName(), ex.getMessage());
                        removeEnabledActiveNode(clusterState, node);
                    }
                });
                taskManager.execAsync(genericServerTask);
            }
        }
    }

    private synchronized void retainEnabledActiveNodes(final ClusterState clusterState, final Set<Node> enabledNodes) {
        final Set<Node> enabledActiveNodes = new HashSet<>();
        for (final Node node : enabledNodes) {
            if (clusterState.getEnabledActiveNodes().contains(node)) {
                enabledActiveNodes.add(node);
            }
        }
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }

    private synchronized void addEnabledActiveNode(final ClusterState clusterState, final Node node) {
        final Set<Node> enabledActiveNodes = new HashSet<>();
        enabledActiveNodes.addAll(clusterState.getEnabledActiveNodes());
        enabledActiveNodes.add(node);
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }

    private synchronized void removeEnabledActiveNode(final ClusterState clusterState, final Node node) {
        final Set<Node> enabledActiveNodes = new HashSet<>();
        enabledActiveNodes.addAll(clusterState.getEnabledActiveNodes());
        enabledActiveNodes.remove(node);
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }
}
