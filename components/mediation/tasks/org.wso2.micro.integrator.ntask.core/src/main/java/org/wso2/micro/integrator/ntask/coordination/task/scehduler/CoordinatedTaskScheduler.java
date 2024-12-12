/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.ntask.coordination.task.scehduler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.commons.util.MiscellaneousUtil;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.message.processor.MessageProcessor;
import org.apache.synapse.task.TaskDescription;
import org.apache.synapse.task.TaskDescriptionRepository;
import org.wso2.micro.integrator.coordination.ClusterCoordinator;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.micro.integrator.ntask.common.TaskException;
import org.wso2.micro.integrator.ntask.coordination.TaskCoordinationException;
import org.wso2.micro.integrator.ntask.coordination.task.ClusterCommunicator;
import org.wso2.micro.integrator.ntask.coordination.task.CoordinatedTask;
import org.wso2.micro.integrator.ntask.coordination.task.resolver.TaskLocationResolver;
import org.wso2.micro.integrator.ntask.coordination.task.store.TaskStore;
import org.wso2.micro.integrator.ntask.coordination.task.store.cleaner.TaskStoreCleaner;
import org.wso2.micro.integrator.ntask.core.TaskUtils;
import org.wso2.micro.integrator.ntask.core.impl.standalone.ScheduledTaskManager;
import org.wso2.micro.integrator.ntask.core.internal.DataHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scheduler class, which runs periodically to retrieve all the scheduled tasks assigned to the node and schedule
 * them locally. Also if the running node is leader, it cleans up the task store and resolve the un assigned nodes.
 */
public class CoordinatedTaskScheduler implements Runnable {

    private static final Log LOG = LogFactory.getLog(CoordinatedTaskScheduler.class);

    private DataHolder dataHolder = DataHolder.getInstance();
    private TaskLocationResolver taskLocationResolver;
    private ClusterCoordinator clusterCoordinator = dataHolder.getClusterCoordinator();
    private TaskStore taskStore;
    private TaskStoreCleaner taskStoreCleaner;
    private int resolvingFrequency;
    private int resolveCount = 0;
    private ClusterCommunicator clusterCommunicator;
    private ScheduledTaskManager taskManager;
    private String localNodeId;

    public CoordinatedTaskScheduler(ScheduledTaskManager taskManager, TaskStore taskStore,
                                    TaskLocationResolver taskLocationResolver, ClusterCommunicator connector) {
        this(taskManager, taskStore, taskLocationResolver, connector, null, 1);
    }

    public CoordinatedTaskScheduler(ScheduledTaskManager taskManager, TaskStore taskStore,
                                    TaskLocationResolver taskLocationResolver, ClusterCommunicator connector,
                                    TaskStoreCleaner cleaner, int frequency) {
        this.taskManager = taskManager;
        this.taskStore = taskStore;
        this.taskLocationResolver = taskLocationResolver;
        this.clusterCommunicator = connector;
        this.taskStoreCleaner = cleaner;
        this.resolvingFrequency = frequency;
        this.localNodeId = clusterCoordinator.getThisNodeId();
    }

    @Override
    public void run() {

        try {
            pauseDeactivatedTasks();
            scheduleAssignedTasks(CoordinatedTask.States.ACTIVATED);
            checkInterrupted();
            if (clusterCoordinator.isLeader()) {
                // cleaning will run for each n times resolving frequency . ( n = 0,1,2 ... ).
                if (resolveCount % resolvingFrequency == 0) {
                    LOG.debug("This node is leader hence cleaning task store.");
                    taskStoreCleaner.clean();
                    resolveCount = 0;
                }
                LOG.debug("This node is leader hence resolving unassigned tasks.");
                addFailedTasks();
                resolveCount++;
                resolveUnassignedNotCompletedTasksAndUpdateStore();
            } else {
                LOG.debug("This node is not leader. Hence not cleaning task store or resolving un assigned tasks.");
            }
            // schedule all tasks assigned to this node and in state none
            scheduleAssignedTasks(CoordinatedTask.States.NONE);
            checkInterrupted();
        } catch (Throwable throwable) { // catching throwable to prohibit permanent stopping of the executor service.
            LOG.fatal("Unexpected error occurred while trying to schedule tasks.", throwable);
        }
    }

    /**
     * Check if the task is interrupted.
     *
     * @throws TaskCoordinationException when something goes wrong connecting to the store
     */
    private void checkInterrupted() throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) {
            try {
                List<String> tasks = taskManager.getLocallyRunningCoordinatedTasks();
                // stop all running coordinated tasks.
                tasks.forEach(task -> {
                    try {
                        taskManager.stopExecutionTemporarily(task);
                    } catch (TaskException e) {
                        LOG.error("Unable to pause the task " + task, e);
                    }
                });
            } finally {
                Thread.currentThread().interrupt();
                throw new InterruptedException("Task was interrupted.");
            }
        }
    }

    /**
     * Pause ( stop execution ) the deactivated tasks.
     *
     * @throws TaskCoordinationException - when something goes wrong while retrieving tasks information from store.
     */
    private void pauseDeactivatedTasks() throws TaskCoordinationException {

        List<String> deactivatedTasks = taskStore.retrieveTaskNames(localNodeId, CoordinatedTask.States.DEACTIVATED);
        if (LOG.isDebugEnabled()) {
            deactivatedTasks.stream().map(
                    task -> "Task [" + task + "] retrieved in [" + CoordinatedTask.States.DEACTIVATED + "] state.")
                    .forEachOrdered(LOG::debug);
        }
        ScheduledTaskManager scheduledTaskManager = taskManager;
        List<String> pausedTasks = new ArrayList<>();
        List<String> deployedTasks = taskManager.getAllCoordinatedTasksDeployed();
        deactivatedTasks.forEach(task -> {
            if (deployedTasks.contains(task)) {
                try {
                    scheduledTaskManager.stopExecution(task);
                    pausedTasks.add(task);
                } catch (TaskException e) {
                    LOG.error("Error stopping the task [" + task + "]", e);
                }
            } else {
                LOG.info("The task [" + task + "] retrieved to be paused is not a deployed task "
                                 + "in this node or an invalid entry, hence ignoring it.");
            }
        });
        notifyOnPause(pausedTasks);
        taskStore.updateTaskState(pausedTasks, CoordinatedTask.States.PAUSED);
    }

    /**
     * Add failed tasks to the store.
     *
     * @throws TaskCoordinationException - When something goes wrong while retrieving all the tasks from store.
     */
    private void addFailedTasks() throws TaskCoordinationException {

        List<ScheduledTaskManager.TaskEntry> failedTasks = taskManager.getAdditionFailedTasks();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Following list of tasks were found in the failed list.");
            failedTasks.forEach(LOG::debug);
        }
        for (ScheduledTaskManager.TaskEntry task : failedTasks) {
            taskStore.addTaskIfNotExist(task.getName(), task.getState());
            taskManager.removeTaskFromAdditionFailedTaskList(task);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully added the failed task [" + task + "]");
            }
        }
    }

    /**
     * Schedules all tasks assigned to this node.
     *
     * @param state - The state of the tasks which need to be scheduled.
     * @throws TaskCoordinationException - When something goes wrong while retrieving all the assigned tasks.
     */
    private void scheduleAssignedTasks(CoordinatedTask.States state) throws TaskCoordinationException
            , InterruptedException {

        LOG.debug("Retrieving tasks assigned to this node and to be scheduled.");
        List<String> tasksOfThisNode = taskStore.retrieveTaskNames(localNodeId, state);
        if (tasksOfThisNode.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No tasks assigned to this node to be scheduled in state " + state);
            }
            return;
        }
        List<String> deployedCoordinatedTasks = taskManager.getAllCoordinatedTasksDeployed();
        List<String> erroredTasks = new ArrayList<>();
        for (String taskName : tasksOfThisNode) {
            if (deployedCoordinatedTasks.contains(taskName)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Submitting retrieved task [" + taskName + "] to the task manager.");
                }
                try {
                    checkInterrupted();
                    taskManager.scheduleCoordinatedTask(taskName);
                } catch (TaskException ex) {
                    if (!TaskException.Code.DATABASE_ERROR.equals(ex.getCode())) {
                        erroredTasks.add(taskName);
                    }
                    LOG.error("Exception occurred while scheduling coordinated task : " + taskName, ex);
                }
            } else {
                LOG.info("The task [" + taskName + "] retrieved to be scheduled is not a deployed task "
                                 + "in this node or an invalid entry, hence ignoring it.");
            }
        }
        taskStore.updateTaskState(erroredTasks, CoordinatedTask.States.NONE);
    }

    /**
     * Resolves the un assigned tasks and update the task store.
     * Synchronized since this will be triggered in leader periodically and upon member addition.
     *
     * @throws TaskCoordinationException when something goes wrong connecting to the store
     */
    public synchronized void resolveUnassignedNotCompletedTasksAndUpdateStore() throws TaskCoordinationException {

        List<String> unAssignedTasks = taskStore.retrieveAllUnAssignedAndIncompleteTasks();
        if (unAssignedTasks.isEmpty()) {
            LOG.debug("No un assigned tasks found.");
            return;
        }
        Map<String, String> tasksToBeUpdated = new HashMap<>();
        unAssignedTasks.forEach(taskName -> {
            String destinedNode = taskLocationResolver.getTaskNodeLocation(clusterCommunicator, taskName);
            if (destinedNode != null) { // can't resolve all of the time
                tasksToBeUpdated.put(taskName, destinedNode);
            }
        });
        taskStore.updateAssignmentAndState(tasksToBeUpdated);
    }

    private void notifyOnPause(List<String> pausedTasks) {
        Set<String> completedProcessors = new HashSet();
        Set<String> completedInboundEndpoints = new HashSet();
        SynapseEnvironment synapseEnvironment = MicroIntegratorBaseUtils.getSynapseEnvironment();
        TaskDescriptionRepository taskRepo = synapseEnvironment.getTaskManager().getTaskDescriptionRepository();
        pausedTasks.forEach(task -> {
            if (MiscellaneousUtil.isTaskOfMessageProcessor(task)) {
                String messageProcessorName = MiscellaneousUtil.getMessageProcessorName(task);
                if (!completedProcessors.contains(messageProcessorName)) {
                    MessageProcessor messageProcessor = synapseEnvironment.getSynapseConfiguration()
                            .getMessageProcessors().get(messageProcessorName);
                    if (messageProcessor != null) {
                        messageProcessor.cleanUpDeactivatedProcessors();
                    }
                    completedProcessors.add(messageProcessorName);
                }
            } else {
                TaskDescription taskDescription = taskRepo.getTaskDescription(task);
                if (taskDescription.getProperty(TaskUtils.TASK_OWNER_PROPERTY) == TaskUtils.TASK_BELONGS_TO_INBOUND_ENDPOINT) {
                    String inboundEndpointName = (String) taskDescription.getProperty(TaskUtils.TASK_OWNER_NAME);

                    if (!completedInboundEndpoints.contains(inboundEndpointName)) {
                        InboundEndpoint inboundEndpoint = synapseEnvironment.getSynapseConfiguration()
                                .getInboundEndpoint(inboundEndpointName);
                        if (inboundEndpoint != null) {
                            inboundEndpoint.updateInboundEndpointState(true);
                        }
                        completedInboundEndpoints.add(inboundEndpointName);
                    }
                }
            }
        });
    }
}
