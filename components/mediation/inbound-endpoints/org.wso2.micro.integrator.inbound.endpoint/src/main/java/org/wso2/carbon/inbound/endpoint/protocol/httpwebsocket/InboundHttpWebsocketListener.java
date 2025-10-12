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
package org.wso2.carbon.inbound.endpoint.protocol.httpwebsocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundRequestProcessor;
import org.wso2.carbon.inbound.endpoint.protocol.http.management.HTTPEndpointManager;
import org.wso2.carbon.inbound.endpoint.protocol.httpwebsocket.management.HttpWebsocketEndpointManager;

public class InboundHttpWebsocketListener implements InboundRequestProcessor {

    private static final Log LOGGER = LogFactory.getLog(InboundHttpWebsocketListener.class);

    protected final String name;
    protected int port;
    protected InboundProcessorParams processorParams;
    protected boolean startInPausedMode;

    public InboundHttpWebsocketListener(InboundProcessorParams params) {

        processorParams = params;
        String portParam = params.getProperties()
                .getProperty(InboundHttpWebSocketConstants.INBOUND_ENDPOINT_PARAMETER_HTTP_WS_PORT);
        try {
            port = Integer.parseInt(portParam);
        } catch (NumberFormatException e) {
            handleException("Validation failed for the port parameter " + portParam, e);
        }
        name = params.getName();
        startInPausedMode = params.startInPausedMode();
    }

    @Override
    public void init() {

        LOGGER.info("HTTP WebSocket inbound endpoint [" + name + "] is initializing"
                + (startInPausedMode ? " but will remain in suspended mode..." : "..."));

        if (!startInPausedMode) {
            HttpWebsocketEndpointManager.getInstance().startEndpoint(port, name, processorParams);
        }
    }

    @Override
    public void destroy() {

        HttpWebsocketEndpointManager.getInstance().closeEndpoint(port);
    }

    @Override
    public void pause() {
        // need to implement
    }

    @Override
    public boolean activate() {
        boolean isSuccessfullyActivated = false;
        try {
            isSuccessfullyActivated = HttpWebsocketEndpointManager.getInstance()
                    .startEndpoint(port, name, processorParams);

        } catch (SynapseException e) {
            LOGGER.error("Error while activating HTTP WebSocket inbound endpoint [" + name + "] on port " + port, e);
        }

        if (isSuccessfullyActivated) {
            LOGGER.info("HTTP WebSocket inbound endpoint [" + name + "] is activated successfully on port " + port);
        } else {
            LOGGER.warn("HTTP WebSocket inbound endpoint [" + name + "] activation failed on port " + port);
        }

        return isSuccessfullyActivated;
    }

    @Override
    public boolean deactivate() {
        boolean isSuccessfullyDeactivated = false;
        HttpWebsocketEndpointManager manager = HttpWebsocketEndpointManager.getInstance();
        manager.closeEndpoint(port);

        if (!manager.isEndpointRunning(name, port)) {
            LOGGER.info("HTTP/HTTPs WebSocket inbound endpoint [" + name + "] is deactivated successfully.");
            isSuccessfullyDeactivated = true;
        } else {
            LOGGER.warn("HTTP/HTTPS WebSocket inbound endpoint [" + name + "] deactivation failed on port " + port);
        }
        return isSuccessfullyDeactivated;
    }

    @Override
    public boolean isDeactivated() {

        return !HttpWebsocketEndpointManager.getInstance().isEndpointRunning(name, port);
    }

    protected void handleException(String msg, Exception e) {

        LOGGER.error(msg, e);
        throw new SynapseException(msg, e);
    }
}
