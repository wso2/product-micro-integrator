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

import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericEventBasedConsumer;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericOneTimeTask;

public abstract class InboundOneTimeTriggerEventBasedProcessor extends InboundOneTimeTriggerRequestProcessor {

    private GenericEventBasedConsumer eventBasedConsumer;

    /**
     * Based on the coordination option schedule the task with NTASK or run as a
     * background thread
     *
     * @param task
     * @param endpointPostfix
     */
    protected void start(GenericOneTimeTask task, String endpointPostfix) {
        eventBasedConsumer = task.getEventBasedConsumer();
        super.start(task, endpointPostfix);
    }

    /**
     * Stop the inbound polling processor This will be called when inbound is
     * undeployed/redeployed or when server stop
     */
    public void destroy() {
        destroy(true);
    }

    /**
     * Stop the inbound polling processor This will be called when inbound is
     * undeployed/redeployed or when server stop
     */
    public void destroy(boolean removeTask) {
        super.destroy(removeTask);
        //Terminate waiting events
        eventBasedConsumer.destroy();
    }
}
