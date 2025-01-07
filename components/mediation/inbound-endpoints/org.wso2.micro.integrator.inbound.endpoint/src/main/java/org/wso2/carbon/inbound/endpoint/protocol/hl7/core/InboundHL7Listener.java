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

package org.wso2.carbon.inbound.endpoint.protocol.hl7.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundRequestProcessor;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.management.HL7EndpointManager;

import java.io.IOException;

public class InboundHL7Listener implements InboundRequestProcessor {

    private static final Log log = LogFactory.getLog(InboundHL7Listener.class);

    private int port;
    private InboundProcessorParams params;
    private boolean startInPausedMode;

    public InboundHL7Listener(InboundProcessorParams params) {
        this.params = params;
        startInPausedMode = params.startInPausedMode();
    }

    @Override
    public void init() {
        /*
         * The activate/deactivate functionality for the HL7 protocol is not currently implemented
         * for Inbound Endpoints.
         *
         * Therefore, the following check has been added to immediately return if the "suspend"
         * attribute is set to true in the inbound endpoint configuration.
         *
         * Note: This implementation is temporary and should be revisited and improved once
         * the activate/deactivate capability for HL7 listener is implemented.
         */
        if (startInPausedMode) {
            log.info("Inbound endpoint [" + params.getName() + "] is currently suspended.");
            return;
        }
        if (!InboundHL7IOReactor.isStarted()) {
            log.info("Starting MLLP Transport Reactor");
            try {
                InboundHL7IOReactor.start();
            } catch (IOException e) {
                log.error("MLLP Reactor startup error: ", e);
                return;
            }
        }

        start();
    }

    public void start() {
        try {
            this.port = Integer.parseInt(params.getProperties().getProperty(MLLPConstants.PARAM_HL7_PORT));
        } catch (NumberFormatException e) {
            log.error("The port specified is of an invalid type: " + this.port + ". Endpoint not started.");
            return;
        }
        HL7EndpointManager.getInstance().startEndpoint(port, params.getName(), params);
    }

    @Override
    public void destroy() {
        HL7EndpointManager.getInstance().closeEndpoint(port);
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

        return !InboundHL7IOReactor.isStarted();
    }
}
