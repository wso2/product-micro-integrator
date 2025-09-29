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

        /*
         * The activate/deactivate functionality for the HTTPS-WSS protocol is not currently implemented
         * for Inbound Endpoints.
         *
         * Therefore, the following check has been added to immediately return if the "suspend"
         * attribute is set to true in the inbound endpoint configuration.
         *
         * Note: This implementation is temporary and should be revisited and improved once
         * the activate/deactivate capability for HTTPS-WSS listeners is implemented.
         */
        if (startInPausedMode) {
            LOGGER.info("Inbound endpoint [" + name + "] is currently suspended.");
        } else {
            HttpWebsocketEndpointManager.getInstance().startSSLEndpoint(port, name, processorParams);
        }
    }
}
