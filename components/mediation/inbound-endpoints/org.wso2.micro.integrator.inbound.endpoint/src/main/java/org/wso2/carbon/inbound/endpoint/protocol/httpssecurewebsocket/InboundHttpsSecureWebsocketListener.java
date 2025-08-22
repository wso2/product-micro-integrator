/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.inbound.endpoint.protocol.httpssecurewebsocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.wso2.carbon.inbound.endpoint.protocol.httpwebsocket.InboundHttpWebsocketListener;
import org.wso2.carbon.inbound.endpoint.protocol.httpwebsocket.management.HttpWebsocketEndpointManager;

public class InboundHttpsSecureWebsocketListener extends InboundHttpWebsocketListener {

    private static final Log LOGGER = LogFactory.getLog(InboundHttpsSecureWebsocketListener.class);

    public InboundHttpsSecureWebsocketListener(InboundProcessorParams params) {

        super(params);
    }

    @Override
    public void init() {

        LOGGER.info("HTTPS WebSocket inbound endpoint [" + name + "] is initializing"
                + (this.startInPausedMode ? " but will remain in suspended mode..." : "..."));

        if (!startInPausedMode) {
            HttpWebsocketEndpointManager.getInstance().startSSLEndpoint(port, name, processorParams);
        }
    }

    @Override
    public boolean activate() {
        boolean isSuccessfullyActivated = false;
        try {
            isSuccessfullyActivated = HttpWebsocketEndpointManager.getInstance()
                    .startSSLEndpoint(port, name, processorParams);

        } catch (SynapseException e) {
            LOGGER.error("Error while activating HTTPS WebSocket inbound endpoint [" + name + "] on port " + port, e);
        }

        if (isSuccessfullyActivated) {
            LOGGER.info("HTTPS WebSocket inbound endpoint [" + name + "] is activated successfully on port " + port);
        } else {
            LOGGER.warn("HTTPS WebSocket inbound endpoint [" + name + "] activation failed on port " + port);
        }

        return isSuccessfullyActivated;
    }
}
