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

        /*
         * The activate/deactivate functionality for the HTTP-WS protocol is not currently implemented
         * for Inbound Endpoints.
         *
         * Therefore, the following check has been added to immediately return if the "suspend"
         * attribute is set to true in the inbound endpoint configuration.
         *
         * Note: This implementation is temporary and should be revisited and improved once
         * the activate/deactivate capability for HTTP-WS listener is implemented.
         */
        if (startInPausedMode) {
            LOGGER.info("Inbound endpoint [" + name + "] is currently suspended.");
        } else {
            HttpWebsocketEndpointManager.getInstance().startEndpoint(port, name, processorParams);
        }
    }

    @Override
    public void destroy() {

        HttpWebsocketEndpointManager.getInstance().closeEndpoint(port);
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

        return !HttpWebsocketEndpointManager.getInstance().isEndpointRunning(name, port);
    }

    protected void handleException(String msg, Exception e) {

        LOGGER.error(msg, e);
        throw new SynapseException(msg, e);
    }
}
