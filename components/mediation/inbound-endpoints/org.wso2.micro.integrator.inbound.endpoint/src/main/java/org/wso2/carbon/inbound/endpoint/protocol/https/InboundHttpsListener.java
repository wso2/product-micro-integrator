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

package org.wso2.carbon.inbound.endpoint.protocol.https;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.transport.passthru.core.ssl.SSLConfiguration;
import org.wso2.carbon.inbound.endpoint.persistence.PersistenceUtils;
import org.wso2.carbon.inbound.endpoint.protocol.http.InboundHttpConstants;
import org.wso2.carbon.inbound.endpoint.protocol.http.InboundHttpListener;
import org.wso2.carbon.inbound.endpoint.protocol.http.management.HTTPEndpointManager;

public class InboundHttpsListener extends InboundHttpListener {

    private static final Log log = LogFactory.getLog(InboundHttpListener.class);

    private SSLConfiguration sslConfiguration;
    private int port;
    private String name;
    private InboundProcessorParams processorParams;

    public InboundHttpsListener(InboundProcessorParams params) {
        super(params);
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
        String keyStoreParam = params.getProperties().getProperty(InboundHttpConstants.KEY_STORE);
        String trustStoreParam = params.getProperties().getProperty(InboundHttpConstants.TRUST_STORE);
        String clientAuthParam = params.getProperties().getProperty(InboundHttpConstants.SSL_VERIFY_CLIENT);
        String sslProtocol = params.getProperties().getProperty(InboundHttpConstants.SSL_PROTOCOL);
        String httpsProtocols = params.getProperties().getProperty(InboundHttpConstants.HTTPS_PROTOCOL);
        String certificateRevocation = params.getProperties().getProperty(InboundHttpConstants.CLIENT_REVOCATION);
        String preferredCiphers = params.getProperties().getProperty(InboundHttpConstants.PREFERRED_CIPHERS);
        sslConfiguration = new SSLConfiguration(keyStoreParam, trustStoreParam, clientAuthParam, httpsProtocols,
                                                certificateRevocation, sslProtocol, preferredCiphers);

    }

    @Override
    public void init() {
        log.info("HTTPS inbound endpoint [" + name + "] is initializing"
                + (this.startInPausedMode ? " but will remain in suspended mode..." : "..."));

        if (!startInPausedMode) {
            if (isPortUsedByAnotherApplication(port)) {
                log.warn("Port " + port + "used by inbound endpoint " + name + " is already used by another application "
                        + "hence undeploying inbound endpoint");
                throw new SynapseException("Port " + port + " used by inbound endpoint " + name + " is already used by "
                        + "another application.");
            } else {
                HTTPEndpointManager.getInstance().startSSLEndpoint(port, name, sslConfiguration, processorParams);
            }
        }
    }

    @Override
    public boolean activate() {
        boolean isSuccessfullyActivated = false;
        try {
            if (isPortUsedByAnotherApplication(port)) {
                log.warn("Port [" + port + "] used by inbound endpoint [" + name + "] is already used by another application "
                        + "hence activation of inbound endpoint failed");
                throw new SynapseException("Port [" + port + "] used by inbound endpoint [" + name + "] is already used by "
                        + "another application.");
            } else {
                isSuccessfullyActivated = HTTPEndpointManager.getInstance()
                        .startSSLEndpoint(port, name, sslConfiguration, processorParams);
            }

            if (isSuccessfullyActivated) {
                log.info("HTTPS inbound endpoint [" + name + "] is activated successfully on port " + port);
            } else {
                log.error("HTTPS inbound endpoint [" + name + "] activation failed on port " + port);
            }
        } catch (SynapseException e) {
            log.error("Error while activating HTTPS inbound endpoint [" + name + "] on port " + port, e);
        }

        return isSuccessfullyActivated;
    }

}
