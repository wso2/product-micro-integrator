/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.inbound.endpoint.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundRequestProcessor;
import org.apache.synapse.startup.quartz.StartUpController;
import org.apache.synapse.task.TaskDescription;
import org.apache.synapse.task.TaskManager;
import org.wso2.carbon.inbound.endpoint.persistence.InboundEndpointsDataStore;
import org.wso2.carbon.inbound.endpoint.protocol.jms.JMSTask;
import org.wso2.micro.integrator.mediation.ntask.NTaskTaskManager;
import org.wso2.micro.integrator.ntask.core.TaskUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class provides the common implementation for polling protocol processors
 */
public abstract class InboundRequestProcessorImpl implements InboundRequestProcessor {

    protected SynapseEnvironment synapseEnvironment;
    protected long interval;
    protected String name;
    protected boolean coordination;
    protected boolean startInPausedMode;

    private List<StartUpController> startUpControllersList = new ArrayList<>();
    private HashMap<Thread, InboundRunner> inboundRunnersThreadsMap = new HashMap<>();
    private static final Log log = LogFactory.getLog(InboundRequestProcessorImpl.class);
    private InboundEndpointsDataStore dataStore;

    protected final static String COMMON_ENDPOINT_POSTFIX = "--SYNAPSE_INBOUND_ENDPOINT";

    public InboundRequestProcessorImpl() {
        dataStore = InboundEndpointsDataStore.getInstance();
    }

    /**
     * Based on the coordination option schedule the task with NTASK or run as a
     * background thread
     *
     * @param task
     * @param endpointPostfix
     */
    protected void start(InboundTask task, String endpointPostfix) {
        log.info("Starting the inbound endpoint [" + name + "]" + (startInPausedMode ? " in suspended mode" : "")
                + ", with coordination " + coordination + ". Interval : " + interval + ". Type : " + endpointPostfix);
        if (coordination) {
            try {
                TaskDescription taskDescription = new TaskDescription();
                taskDescription.setName(name + "-" + endpointPostfix);
                taskDescription.setTaskGroup(endpointPostfix);
                if (interval < InboundTask.TASK_THRESHOLD_INTERVAL) {
                    taskDescription.setInterval(InboundTask.TASK_THRESHOLD_INTERVAL);
                } else {
                    taskDescription.setInterval(interval);
                }
                taskDescription.setIntervalInMs(true);
                taskDescription.addResource(TaskDescription.INSTANCE, task);
                taskDescription.addResource(TaskDescription.CLASSNAME, task.getClass().getName());
                taskDescription.setTaskImplClassName(task.getClass().getName());
                taskDescription.addProperty(TaskUtils.TASK_OWNER_PROPERTY, TaskUtils.TASK_BELONGS_TO_INBOUND_ENDPOINT);
                taskDescription.addProperty(TaskUtils.TASK_OWNER_NAME, name);
                taskDescription.addProperty(TaskUtils.START_IN_PAUSED_MODE, String.valueOf(startInPausedMode));
                StartUpController startUpController = new StartUpController();
                startUpController.setTaskDescription(taskDescription);
                startUpController.init(synapseEnvironment);
                startUpControllersList.add(startUpController);
                //register a listener to be notified when the local jms task is deleted
                if (task instanceof JMSTask) {
                    TaskManager taskManagerImpl = synapseEnvironment.getTaskManager().getTaskManagerImpl();
                    if (taskManagerImpl instanceof NTaskTaskManager) {
                        ((NTaskTaskManager) taskManagerImpl)
                                .registerListener((JMSTask) task, taskDescription.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error starting the inbound endpoint " + name + ". Unable to schedule the task. " + e
                        .getLocalizedMessage(), e);
            }
        } else {

            startInboundRunnerThread(task, Constants.SUPER_TENANT_DOMAIN_NAME, false, startInPausedMode);
        }
    }

    /**
     * Starts a new thread to execute the given inbound task by creating a new {@link InboundRunner} instance
     * and running it in a separate thread.
     *
     * @param task The inbound task to be executed by the thread.
     * @param tenantDomain The tenant domain under which the task should be run.
     * @param mgrOverride A flag indicating whether the manager override is enabled.
     * @param startInPausedMode A flag indicating whether the task should start in paused mode.
     */
    private void startInboundRunnerThread(InboundTask task, String tenantDomain, boolean mgrOverride,
                                          boolean startInPausedMode) {
        InboundRunner inboundRunner = new InboundRunner(task, interval, tenantDomain, mgrOverride, startInPausedMode);
        Thread runningThread = new Thread(inboundRunner);
        inboundRunnersThreadsMap.put(runningThread, inboundRunner);
        runningThread.start();
    }

    /**
     * Stop the inbound polling processor This will be called when inbound is
     * undeployed/redeployed or when server stop
     */
    public void destroy() {
        log.info("Inbound endpoint " + name + " stopping.");

        if (!startUpControllersList.isEmpty()) {
            for (StartUpController sc : startUpControllersList) {
                sc.destroy();
            }
            startUpControllersList.clear();
        } else if (!inboundRunnersThreadsMap.isEmpty()) {

            Iterator itr = inboundRunnersThreadsMap.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry entry = (Map.Entry) itr.next();
                Thread thread = (Thread) entry.getKey();
                InboundRunner inboundRunner = (InboundRunner) entry.getValue();

                inboundRunner.terminate();
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    thread.interrupt();
                    log.error("Error while stopping the inbound thread.");
                }
            }
            inboundRunnersThreadsMap.clear();
        }
    }

    /**
     * Activates the Inbound Endpoint by activating any associated startup controllers
     * or resuming inbound runner threads if no startup controllers are present.
     *
     * <p>This method first checks if there are any startup controllers. If there are, it attempts to activate
     * each controller and sets the success flag accordingly. If no startup controllers are present, it resumes
     * any inbound runner threads that may be running. The method returns a boolean indicating whether
     * the activation was successful.</p>
     *
     * @return {@code true} if at least one associated startup controller was successfully activated or inbound runner
     *         threads were resumed; {@code false} if activation task failed for all the startup controllers or
     *         if no startup controllers or inbound runner threads present.
     */
    @Override
    public boolean activate() {
        log.info("Activating the Inbound Endpoint [" + name + "].");

        boolean isSuccessfullyActivated = false;
        if (!startUpControllersList.isEmpty()) {
            for (StartUpController sc : startUpControllersList) {
                if (sc.activateTask()) {
                    isSuccessfullyActivated = true;
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to activate the consumer: " + sc.getTaskDescription().getName());
                    }
                }
            }
        } else if (!inboundRunnersThreadsMap.isEmpty()) {
            for (Map.Entry<Thread, InboundRunner> threadInboundRunnerEntry : inboundRunnersThreadsMap.entrySet()) {
                InboundRunner inboundRunner = (InboundRunner) ((Map.Entry) threadInboundRunnerEntry).getValue();
                inboundRunner.resume();
            }
            isSuccessfullyActivated = true;
        }
        return isSuccessfullyActivated;
    }

    /**
     * Deactivates the Inbound Endpoint by deactivating any associated startup controllers
     * or pausing inbound runner threads if no startup controllers are present.
     *
     * <p>This method first checks if there are any startup controllers. If there are, it attempts to deactivate
     * each controller and sets the success flag accordingly. If no startup controllers are present, it pauses
     * any inbound runner threads that may be running. The method returns a boolean indicating whether
     * the deactivation was successful.</p>
     *
     * @return {@code true} if all associated startup controllers were successfully deactivated or inbound runner threads
     *         were paused; {@code false} if any deactivation task failed.
     */
    @Override
    public boolean deactivate() {
        log.info("Deactivating the Inbound Endpoint [" + name + "].");

        boolean isSuccessfullyDeactivated = true;
        if (!startUpControllersList.isEmpty()) {
            for (StartUpController sc : startUpControllersList) {
                if (!sc.deactivateTask()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to deactivate the consumer: " + sc.getTaskDescription().getName());
                    }
                    isSuccessfullyDeactivated = false;
                }
            }
        } else if (!inboundRunnersThreadsMap.isEmpty()) {
            for (Map.Entry<Thread, InboundRunner> threadInboundRunnerEntry : inboundRunnersThreadsMap.entrySet()) {
                InboundRunner inboundRunner = (InboundRunner) ((Map.Entry<?, ?>) threadInboundRunnerEntry).getValue();
                inboundRunner.pause();
            }
        }
        return isSuccessfullyDeactivated;
    }

    /**
     * Checks if the Inbound Endpoint is deactivated. This method checks the status of any associated
     * startup controllers or inbound runner threads. The endpoint is considered deactivated if all
     * startup controllers are inactive and all inbound runner threads are paused.
     *
     * @return {@code true} if all startup controllers are inactive and all inbound runner threads are paused;
     *         {@code false} if any startup controller is active or any inbound runner thread is not paused.
     */
    @Override
    public boolean isDeactivated() {
        if (!startUpControllersList.isEmpty()) {
            for (StartUpController sc : startUpControllersList) {
                if (sc.isTaskActive()) {
                    // Inbound Endpoint is considered active if at least one consumer is alive.
                    return false;
                }
            }
        } else if (!inboundRunnersThreadsMap.isEmpty()) {
            for (Map.Entry<Thread, InboundRunner> threadInboundRunnerEntry : inboundRunnersThreadsMap.entrySet()) {
                InboundRunner inboundRunner = (InboundRunner) ((Map.Entry<?, ?>) threadInboundRunnerEntry).getValue();
                if (!inboundRunner.isPaused()) {
                    // Inbound Endpoint is considered active if at least one consumer is alive.
                    return false;
                }
            }
        }
        return true;
    }
}
