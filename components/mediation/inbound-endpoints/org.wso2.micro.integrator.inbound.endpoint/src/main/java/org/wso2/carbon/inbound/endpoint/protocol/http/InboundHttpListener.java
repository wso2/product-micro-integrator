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

package org.wso2.carbon.inbound.endpoint.protocol.http;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundRequestProcessor;
import org.apache.synapse.transport.passthru.api.PassThroughInboundEndpointHandler;
import org.wso2.carbon.inbound.endpoint.persistence.PersistenceUtils;
import org.wso2.carbon.inbound.endpoint.protocol.http.management.HTTPEndpointManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;

/**
 * Listener class for HttpInboundEndpoint which is trigger by inbound core and
 * responsible for start ListeningEndpoint related to given port
 */
public class InboundHttpListener implements InboundRequestProcessor {

    private static final Log log = LogFactory.getLog(InboundHttpListener.class);

    private String name;
    private int port;
    private InboundProcessorParams processorParams;
    protected boolean startInPausedMode;

    public InboundHttpListener(InboundProcessorParams params) {
        processorParams = params;
        boolean enableInboundPortOffset = SynapsePropertiesLoader.
                getBooleanProperty(InboundHttpConstants.ENABLE_PORT_OFFSET_FOR_INBOUND_ENDPOINT, false);
        String portParam = params.getProperties()
                .getProperty(InboundHttpConstants.INBOUND_ENDPOINT_PARAMETER_HTTP_PORT);
        try {
            port = enableInboundPortOffset ? Integer.parseInt(portParam) +
                    PersistenceUtils.getPortOffset() : Integer.parseInt(portParam);
        } catch (NumberFormatException e) {
            handleException("Please provide port number as integer  instead of  port  " + portParam, e);
        }
        name = params.getName();
        startInPausedMode = params.startInPausedMode();
    }

    @Override
    public void init() {
        log.info("HTTP inbound endpoint [" + name + "] is initializing"
                + (startInPausedMode ? " but will remain in suspended mode..." : "..."));

        if (!startInPausedMode) {
            if (isPortUsedByAnotherApplication(port)) {
                log.warn("Port " + port + " used by inbound endpoint " + name + " is already used by another application "
                        + "hence undeploying inbound endpoint");
                throw new SynapseException("Port " + port + " used by inbound endpoint " + name + " is already used by "
                        + "another application.");
            } else {
                HTTPEndpointManager.getInstance().startEndpoint(port, name, processorParams);
            }
        }
    }

    @Override
    public void destroy() {
        HTTPEndpointManager.getInstance().closeEndpoint(port);
    }

    @Override
    public boolean activate() {
        boolean isSuccessfullyActivated = false;
        try {
            isSuccessfullyActivated = HTTPEndpointManager.getInstance()
                    .startEndpoint(port, name, processorParams);
            if (isSuccessfullyActivated) {
                log.info("HTTP inbound endpoint [" + name + "] is activated successfully on port " + port);
            } else {
                log.warn("HTTP inbound endpoint [" + name + "] activation failed on port " + port);
            }
        } catch (SynapseException e) {
            log.error("Error while activating HTTP inbound endpoint [" + name + "] on port " + port, e);
        }

        return isSuccessfullyActivated;
    }

    @Override
    public boolean deactivate() {
        boolean isSuccessfullyDeactivated = false;
        HTTPEndpointManager manager = HTTPEndpointManager.getInstance();
        manager.closeEndpoint(port);

        if (!manager.isEndpointRunning(name, port)) {
            log.info("HTTP/HTTPS inbound endpoint [" + name + "] is deactivated successfully.");
            isSuccessfullyDeactivated = true;
        } else {
            log.warn("HTTP/HTTPS inbound endpoint [" + name + "] deactivation failed on port " + port);
        }
        return isSuccessfullyDeactivated;
    }

    @Override
    public boolean isDeactivated() {

        return !HTTPEndpointManager.getInstance().isEndpointRunning(name, port);
    }

    protected void handleException(String msg, Exception e) {
        log.error(msg, e);
        throw new SynapseException(msg, e);
    }

    protected boolean isPortUsedByAnotherApplication(int port) {
        if (PassThroughInboundEndpointHandler.isEndpointRunning(port)) {
            return false;
        } else {
            try {
                ServerSocket srv = new ServerSocket(port);
                srv.close();
                srv = null;
                return false;
            } catch (IOException e) {
                return true;
            }
        }
    }

    protected void destoryInbound() {
        if (processorParams.getSynapseEnvironment() != null) {
            Collection<InboundEndpoint> inboundEndpoints = processorParams.getSynapseEnvironment().
                    getSynapseConfiguration().getInboundEndpoints();
            {
                for (InboundEndpoint inboundEndpoint : inboundEndpoints) {
                    if (inboundEndpoint.getName().equals(name)) {
                        processorParams.getSynapseEnvironment().
                                getSynapseConfiguration().removeInboundEndpoint(name);
                        break;
                    }
                }
            }
        }
    }
}
