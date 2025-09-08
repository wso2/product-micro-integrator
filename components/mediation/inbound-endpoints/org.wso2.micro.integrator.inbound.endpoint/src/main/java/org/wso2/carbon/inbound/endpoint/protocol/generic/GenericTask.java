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
package org.wso2.carbon.inbound.endpoint.protocol.generic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.core.SynapseEnvironment;
import org.wso2.carbon.inbound.endpoint.common.InboundTask;
import org.wso2.micro.integrator.ntask.core.impl.LocalTaskActionListener;

import java.util.Properties;

public class GenericTask extends InboundTask implements LocalTaskActionListener {
    private static final Log logger = LogFactory.getLog(GenericTask.class.getName());

    private final GenericPollingConsumer pollingConsumer;

    public GenericTask(GenericPollingConsumer pollingConsumer, long interval) {
        logger.debug("Generic Task initalize.");
        this.interval = interval;
        this.pollingConsumer = pollingConsumer;
    }

    protected void taskExecute() {
        logger.debug("Generic Task executing.");
        pollingConsumer.poll();
    }

    @Override
    public Properties getInboundProperties() {
        return pollingConsumer.getInboundProperties();
    }

    public void init(SynapseEnvironment synapseEnvironment) {
        logger.debug("Initializing Task.");
    }

    public void destroy() {
        logger.debug("Destroying Task. ");
    }

    /**
     * Method to notify when a local task is removed, it can be due to pause or delete.
     * Destroys the Generic task upon removal of the local task.
     *
     * @param taskName the name of the task that was deleted
     */
    @Override
    public void notifyLocalTaskRemoval(String taskName) {
        logger.info("Close connections of the Generic task upon deletion of task: " + taskName);
        pollingConsumer.destroy();
    }

    /**
     * Method to notify when a local task is paused.
     * Close connections of the Generic task upon pause.
     *
     * @param taskName the name of the task that was paused
     */
    @Override
    public void notifyLocalTaskPause(String taskName) {
        logger.info("Close connections of the Generic task upon pause of task: " + taskName);
        pollingConsumer.destroy();
    }

    @Override
    public void notifyLocalTaskResume(String taskName) {
        try {
            pollingConsumer.resume();
        } catch (NoSuchMethodError e) {
            logger.warn("resume() method not available in this version of PollingConsumer. Update to the latest " +
                    "server version immediately Task: " + taskName);
        }
    }
    public GenericPollingConsumer getPollingConsumer() {
        return pollingConsumer;
    }
}
