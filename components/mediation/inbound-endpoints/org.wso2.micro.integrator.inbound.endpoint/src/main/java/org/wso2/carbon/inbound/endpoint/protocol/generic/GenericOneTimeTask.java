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
import org.wso2.carbon.inbound.endpoint.common.OneTimeTriggerInboundTask;
import org.wso2.micro.integrator.ntask.core.impl.LocalTaskActionListener;

public class GenericOneTimeTask extends OneTimeTriggerInboundTask implements LocalTaskActionListener {

    private static final Log logger = LogFactory.getLog(GenericOneTimeTask.class.getName());
    private GenericEventBasedConsumer eventBasedConsumer;

    public GenericOneTimeTask(GenericEventBasedConsumer waitingConsumer) {
        logger.debug("Generic One time Task initalize.");
        this.eventBasedConsumer = waitingConsumer;
    }

    protected void taskExecute() {
        logger.debug("One time task executing.");
        eventBasedConsumer.listen();
    }

    public void init(SynapseEnvironment synapseEnvironment) {
        logger.debug("Initializing Task.");
    }

    public void destroy() {
        logger.debug("Destroying Task. ");
    }

    public GenericEventBasedConsumer getEventBasedConsumer() {
        return eventBasedConsumer;
    }

    /**
     * Method to notify when a local task is removed, it can be due to pause or delete.
     * Destroys the Generic task upon removal of the local task.
     *
     * @param taskName the name of the task that was deleted
     */
    @Override
    public void notifyLocalTaskRemoval(String taskName) {
        logger.info("Removing Generic One Time task: " + taskName);
        eventBasedConsumer.destroy();
    }

    /**
     * Method to notify when a local task is paused.
     * Close connections of the Generic task upon pause.
     *
     * @param taskName the name of the task that was paused
     */

    @Override
    public void notifyLocalTaskPause(String taskName) {
        logger.info("Pausing Generic One Time task: " + taskName);
        try {
            eventBasedConsumer.pause();
        } catch (AbstractMethodError e) {
            throw new UnsupportedOperationException("Unsupported operation 'pause()' for Inbound Endpoint: " + getName() +
                    "If using a WSO2-released inbound, please upgrade to the latest version. " +
                    "If this is a custom inbound, implement the 'resume' logic accordingly.");
        }
    }

    @Override
    public void notifyLocalTaskResume(String taskName) {
        logger.info("Resuming Generic One Time task: " + taskName);
        try {
            eventBasedConsumer.resume();
        } catch (AbstractMethodError e) {
            throw new UnsupportedOperationException("Unsupported operation 'resume()' for Inbound Endpoint: " + getName() +
                    "If using a WSO2-released inbound, please upgrade to the latest version. " +
                    "If this is a custom inbound, implement the 'resume' logic accordingly.");
        }
    }

}
