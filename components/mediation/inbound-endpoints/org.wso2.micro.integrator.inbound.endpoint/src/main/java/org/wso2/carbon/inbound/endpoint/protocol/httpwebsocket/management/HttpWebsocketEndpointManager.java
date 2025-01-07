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
package org.wso2.carbon.inbound.endpoint.protocol.httpwebsocket.management;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.transport.netty.BridgeConstants;
import org.apache.synapse.transport.netty.api.HttpWebSocketInboundEndpointHandler;
import org.apache.synapse.transport.netty.api.config.HttpWebSocketInboundEndpointConfiguration;
import org.apache.synapse.transport.netty.api.config.SSLConfiguration;
import org.wso2.carbon.inbound.endpoint.common.AbstractInboundEndpointManager;
import org.wso2.carbon.inbound.endpoint.inboundfactory.InboundRequestProcessorFactoryImpl;
import org.wso2.carbon.inbound.endpoint.osgi.service.ServiceReferenceHolder;
import org.wso2.carbon.inbound.endpoint.protocol.httpwebsocket.InboundHttpWebSocketConstants;

import java.io.IOException;
import java.net.ServerSocket;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;

public class HttpWebsocketEndpointManager extends AbstractInboundEndpointManager {

    private static final Log LOGGER = LogFactory.getLog(HttpWebsocketEndpointManager.class);

    private static final HttpWebsocketEndpointManager instance = new HttpWebsocketEndpointManager();

    public static HttpWebsocketEndpointManager getInstance() {

        return instance;
    }

    @Override
    public boolean startEndpoint(int port, String name, InboundProcessorParams inboundParameters) {

        if (handleExistingEndpointOnSamePort(port, name)) {
            return true;
        }
        dataStore.registerListeningEndpoint(port, SUPER_TENANT_DOMAIN_NAME,
                InboundRequestProcessorFactoryImpl.Protocols.httpws.toString(), name, inboundParameters);
        boolean start = startListener(port, name, inboundParameters);

        if (!start) {
            dataStore.unregisterListeningEndpoint(port, SUPER_TENANT_DOMAIN_NAME);
            return false;
        }
        return true;
    }

    public boolean startSSLEndpoint(int port, String name, InboundProcessorParams inboundParameters) {

        if (handleExistingEndpointOnSamePort(port, name)) {
            return true;
        }
        dataStore.registerListeningEndpoint(port, SUPER_TENANT_DOMAIN_NAME,
                InboundRequestProcessorFactoryImpl.Protocols.httpswss.toString(), name, inboundParameters);
        boolean start = startSSLListener(port, name, inboundParameters);

        if (!start) {
            dataStore.unregisterListeningEndpoint(port, SUPER_TENANT_DOMAIN_NAME);
            return false;
        }
        return true;
    }

    @Override
    public boolean startListener(int port, String name, InboundProcessorParams inboundParameters) {

        if (isPortOccupied(port)) {
            LOGGER.error("A service is already listening on port " + port + ". Please select a different port for "
                    + "this endpoint.");
            return false;
        }

        ConfigurationContext configurationContext = ServiceReferenceHolder.getInstance().
                getConfigurationContextService().getServerConfigContext();

        HttpWebSocketInboundEndpointConfiguration configuration = getHttpWebSocketInboundEndpointConfiguration(port,
                name, inboundParameters, false);

        return HttpWebSocketInboundEndpointHandler.startListener(configurationContext, configuration);
    }

    public boolean startSSLListener(int port, String name, InboundProcessorParams inboundParameters) {

        if (isPortOccupied(port)) {
            LOGGER.error("A service is already listening on port " + port
                    + ". Please select a different port for this endpoint.");
            return false;
        }

        ConfigurationContext configurationContext = ServiceReferenceHolder.getInstance().
                getConfigurationContextService().getServerConfigContext();

        HttpWebSocketInboundEndpointConfiguration configuration = getHttpWebSocketInboundEndpointConfiguration(port,
                name, inboundParameters, true);

        return HttpWebSocketInboundEndpointHandler.startSSLListener(configurationContext, configuration);
    }

    @Override
    public void closeEndpoint(int port) {

        dataStore.unregisterListeningEndpoint(port, SUPER_TENANT_DOMAIN_NAME);
        HttpWebSocketInboundEndpointHandler.closeEndpoint(port);
    }

    private boolean handleExistingEndpointOnSamePort(int port, String name) {

        String epName = dataStore.getListeningEndpointName(port, SUPER_TENANT_DOMAIN_NAME);
        if (epName == null) {
            return false;
        }
        if (epName.equalsIgnoreCase(name)) {
            LOGGER.info(epName + " Endpoint is already started in port : " + port);
            return true;
        } else {
            String msg = "Another endpoint named: " + epName + " is currently using this port: " + port;
            LOGGER.warn(msg);
            throw new SynapseException(msg);
        }
    }

    public boolean isEndpointRunning(String name, int port) {
        String epName = dataStore.getListeningEndpointName(port, SUPER_TENANT_DOMAIN_NAME);
        return epName.equalsIgnoreCase(name);
    }

    /**
     * Checks if the given port is available to use.
     *
     * @param port port
     * @return true if the port is already used by another service
     */
    public static boolean isPortOccupied(int port) {

        try {
            ServerSocket ss = new ServerSocket(port);
            ss.close();
            return false;
        } catch (IOException var2) {
            return true;
        }
    }

    /**
     * Builds the {@code HttpWebSocketInboundEndpointConfiguration}, which wraps all the necessary information
     * to be used in the {@code HttpWebSocketInboundEndpointHandler} to start the transport listener.
     *
     * @param port   port
     * @param name   name of the inbound endpoint
     * @param params parameters defined in the inbound endpoint definition
     * @param isSSL  whether this is secured
     * @return {@code HttpWebSocketInboundEndpointConfiguration}
     */
    public static HttpWebSocketInboundEndpointConfiguration getHttpWebSocketInboundEndpointConfiguration(
            int port, String name, InboundProcessorParams params, boolean isSSL) {

        String hostname = params.getProperties()
                .getProperty(InboundHttpWebSocketConstants.INBOUND_ENDPOINT_PARAMETER_HOSTNAME);
        String protocolVersion =
                params.getProperties().getProperty(InboundHttpWebSocketConstants.INBOUND_ENDPOINT_PARAMETER_HTTP_PROTOCOL_VERSIONS);

        HttpWebSocketInboundEndpointConfiguration configuration = new HttpWebSocketInboundEndpointConfiguration(port,
                hostname, name, protocolVersion, params.getHandlers());
        if (isSSL) {
            configuration.setSslConfiguration(buildSSLConfiguration(params));
        }
        return configuration;
    }

    /**
     * Builds the {@code SSLConfiguration} to wrap all the security related parameters defined in the inbound endpoint.
     *
     * @param params parameters defined in the inbound endpoint definition
     * @return {@code SSLConfiguration}
     */
    public static SSLConfiguration buildSSLConfiguration(InboundProcessorParams params) {

        String keyStoreParam = params.getProperties().getProperty(BridgeConstants.KEY_STORE);
        String trustStoreParam = params.getProperties().getProperty(BridgeConstants.TRUST_STORE);
        String clientAuthParam = params.getProperties().getProperty(BridgeConstants.SSL_VERIFY_CLIENT);
        String sslProtocol = params.getProperties().getProperty(BridgeConstants.SSL_PROTOCOL);
        String httpsProtocols = params.getProperties().getProperty(BridgeConstants.HTTPS_PROTOCOL);
        String certificateRevocation = params.getProperties().getProperty(BridgeConstants.CLIENT_REVOCATION);
        String preferredCiphers = params.getProperties().getProperty(BridgeConstants.PREFERRED_CIPHERS);
        String sessionTimeout = params.getProperties().getProperty(BridgeConstants.SSL_SESSION_TIMEOUT);
        String handshakeTimeout = params.getProperties().getProperty(BridgeConstants.SSL_HANDSHAKE_TIMEOUT);

        return new SSLConfiguration.SSLConfigurationBuilder().keyStore(keyStoreParam).trustStore(trustStoreParam)
                .clientAuthEl(clientAuthParam).httpsProtocolsEl(httpsProtocols)
                .revocationVerifier(certificateRevocation).sslProtocol(sslProtocol)
                .preferredCiphersEl(preferredCiphers).sessionTimeout(sessionTimeout)
                .handshakeTimeout(handshakeTimeout).build();
    }

}
