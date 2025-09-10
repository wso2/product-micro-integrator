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

package org.wso2.carbon.inbound.endpoint.protocol.securewebsocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.wso2.carbon.inbound.endpoint.protocol.websocket.InboundWebsocketConstants;
import org.wso2.carbon.inbound.endpoint.protocol.websocket.InboundWebsocketListener;
import org.wso2.carbon.inbound.endpoint.protocol.websocket.management.WebsocketEndpointManager;
import org.wso2.carbon.inbound.endpoint.persistence.PersistenceUtils;

public class InboundSecureWebsocketListener extends InboundWebsocketListener {

    private static final Log log = LogFactory.getLog(InboundSecureWebsocketListener.class);

    private int port;
    private String name;
    private InboundProcessorParams processorParams;

    public InboundSecureWebsocketListener(InboundProcessorParams params) {
        super(params);
        processorParams = params;
        String portParam = params.getProperties()
                .getProperty(InboundWebsocketConstants.INBOUND_ENDPOINT_PARAMETER_WEBSOCKET_PORT);
        try {
            port = Integer.parseInt(portParam);
        } catch (NumberFormatException e) {
            handleException("Validation failed for the port parameter " + portParam, e);
        }
        name = params.getName();
        this.startInPausedMode = params.startInPausedMode();
    }

    @Override
    public void init() {
        log.info("WebSocket inbound endpoint [" + name + "] is initializing"
                + (startInPausedMode ? " but will remain in suspended mode..." : "..."));

        if (!startInPausedMode) {
            int offsetPort = port + PersistenceUtils.getPortOffset(processorParams.getProperties());
            WebsocketEndpointManager.getInstance().startSSLEndpoint(offsetPort, name, processorParams);
        }
    }

    @Override
    public boolean activate() {
        boolean isSuccessfullyActivated = false;
        try {
            int offsetPort = port + PersistenceUtils.getPortOffset(processorParams.getProperties());
            isSuccessfullyActivated = WebsocketEndpointManager.getInstance()
                    .startSSLEndpoint(offsetPort, name, processorParams);
        } catch (SynapseException e) {
            log.error("Error while activating WebSocket inbound endpoint [" + name + "] on port " + port, e);
        }

        if (isSuccessfullyActivated) {
            log.info("WebSocket inbound endpoint [" + name + "] is activated successfully on port " + port);
        } else {
            log.warn("WebSocket inbound endpoint [" + name + "] activation failed on port " + port);
        }
        return isSuccessfullyActivated;
    }
}
