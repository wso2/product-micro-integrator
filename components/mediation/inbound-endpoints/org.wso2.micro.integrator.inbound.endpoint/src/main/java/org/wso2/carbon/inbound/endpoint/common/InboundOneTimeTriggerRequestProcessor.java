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
import org.apache.synapse.inbound.InboundTaskProcessor;
import org.apache.synapse.startup.quartz.StartUpController;
import org.apache.synapse.task.TaskDescription;
import org.apache.synapse.task.TaskManager;
import org.wso2.carbon.inbound.endpoint.persistence.InboundEndpointsDataStore;
import org.wso2.carbon.inbound.endpoint.protocol.rabbitmq.RabbitMQTask;
import org.wso2.micro.integrator.mediation.ntask.NTaskTaskManager;

import java.util.Objects;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

/**
 * This class provides the common implementation for one time trigger protocol processors
 * Implemented the support if message injection happens in a separate thread. ( using Callbacks )
 * One such requirement is loading the tenant when message is injected if at that moment tenant
 * is unloaded.
 */
public abstract class InboundOneTimeTriggerRequestProcessor implements InboundRequestProcessor, InboundTaskProcessor {

    protected StartUpController startUpController;
    protected SynapseEnvironment synapseEnvironment;
    protected String name;
    protected boolean coordination;
    protected boolean startInPausedMode;

    private OneTimeTriggerInboundRunner inboundRunner;
    private Thread runningThread;
    private static final Log log = LogFactory.getLog(InboundOneTimeTriggerRequestProcessor.class);
    private InboundEndpointsDataStore dataStore;

    protected final static String COMMON_ENDPOINT_POSTFIX = "--SYNAPSE_INBOUND_ENDPOINT";
    public static final int TASK_THRESHOLD_INTERVAL = 1000;

    public InboundOneTimeTriggerRequestProcessor() {
        dataStore = InboundEndpointsDataStore.getInstance();
    }

    /**
     * Based on the coordination option schedule the task with NTASK or run as a
     * background thread
     *
     * @param task
     * @param endpointPostfix
     */
    protected void start(OneTimeTriggerInboundTask task, String endpointPostfix) {
        log.info("Starting the inbound endpoint " + name + ", with coordination " + coordination + ". Type : "
                         + endpointPostfix);
        if (coordination) {
            try {
                TaskDescription taskDescription = new TaskDescription();
                taskDescription.setName(name + "-" + endpointPostfix);
                taskDescription.setTaskGroup(endpointPostfix);
                taskDescription.setInterval(TASK_THRESHOLD_INTERVAL);
                taskDescription.setIntervalInMs(true);
                taskDescription.addResource(TaskDescription.INSTANCE, task);
                taskDescription.addResource(TaskDescription.CLASSNAME, task.getClass().getName());
                startUpController = new StartUpController();
                startUpController.setTaskDescription(taskDescription);
                startUpController.init(synapseEnvironment);
                // registering a listener to identify task removal or deletions.
                if (task instanceof RabbitMQTask) {
                    TaskManager taskManagerImpl = synapseEnvironment.getTaskManager().getTaskManagerImpl();
                    if (taskManagerImpl instanceof NTaskTaskManager) {
                        ((NTaskTaskManager) taskManagerImpl).registerListener((RabbitMQTask) task,
                                                                              taskDescription.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error starting the inbound endpoint " + name + ". Unable to schedule the task. " + e
                        .getLocalizedMessage(), e);
            }
        } else {

            if (!dataStore.isPollingEndpointRegistered(SUPER_TENANT_DOMAIN_NAME, name)) {
                dataStore.registerPollingEndpoint(SUPER_TENANT_DOMAIN_NAME, name);
            }

            inboundRunner = new OneTimeTriggerInboundRunner(task, SUPER_TENANT_DOMAIN_NAME);
            if (task.getCallback() != null) {
                task.getCallback().setInboundRunnerMode(true);
            }
            runningThread = new Thread(inboundRunner);
            runningThread.start();
        }
    }

    /**
     * Stop the inbound polling processor This will be called when inbound is
     * undeployed/redeployed or when server stop
     */
    public void destroy() {
        destroy(true);
    }

    @Override
    public void destroy(boolean removeTask) {
        log.info("Inbound endpoint " + name + " stopping.");

        dataStore.unregisterPollingEndpoint(SUPER_TENANT_DOMAIN_NAME, name);

        if (startUpController != null) {
            startUpController.destroy(removeTask);
        } else if (runningThread != null) {
            try {
                //this is introduced where the the thread is suspended due to external server is not
                //up and running and waiting connection to be completed.
                //thread join waits until that suspension is removed where inbound endpoint
                //is un deployed that will eventually lead to completion of this thread
                runningThread.join();
            } catch (InterruptedException e) {
                log.error("Error while stopping the inbound thread.");
            }
        }
    }

    @Override
    public boolean activate() {

        return false;
    }

    @Override
    public boolean deactivate() {

        return false;
    }

    @Override
    public boolean isDeactivated() {

        if (Objects.nonNull(startUpController)) {
            return !startUpController.isTaskActive();
        } else if (Objects.nonNull(runningThread)) {
            return !runningThread.isAlive();
        }
        return true;
    }
}
