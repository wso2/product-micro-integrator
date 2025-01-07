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
        /*
         * The activate/deactivate functionality for the WSS Inbound Endpoint is not currently implemented.
         *
         * Therefore, the following check has been added to immediately return if the "suspend"
         * attribute is set to true in the inbound endpoint configuration.
         *
         * Note: This implementation is temporary and should be revisited and improved once
         * the activate/deactivate capability for WSS listener is implemented.
         */
        if (startInPausedMode) {
            log.info("Inbound endpoint [" + name + "] is currently suspended.");
            return;
        }
        int offsetPort = port + PersistenceUtils.getPortOffset(processorParams.getProperties());
        WebsocketEndpointManager.getInstance().startSSLEndpoint(offsetPort, name, processorParams);
    }

}
