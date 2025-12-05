/**
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

package org.wso2.carbon.inbound.endpoint.protocol.rabbitmq;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.axis2.transport.rabbitmq.RabbitMQUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Encapsulate a RabbitMQ AMQP Connection factory definition within an inbound configuration
 * <p/>
 * Connection Factory definitions, allows service level parameters to be defined,
 * and re-used by each service that binds to it
 */
public class RabbitMQConnectionFactory {

    private static final Log log = LogFactory.getLog(RabbitMQConnectionFactory.class);

    private ConnectionFactory connectionFactory;
    private String name;
    private Map<String, String> parameters = new HashMap<>();
    private int retryInterval;
    private int retryCount;
    private Address[] addresses;
    private static final String PKIX = "PKIX";
    private static final String JCE_PROVIDER = "security.jce.provider";

    /**
     * Digest a AMQP CF definition from the configuration and construct
     */
    public RabbitMQConnectionFactory(Properties properties) throws RabbitMQException {
        this.name = properties.getProperty(RabbitMQConstants.RABBITMQ_CON_FAC);
        properties.stringPropertyNames().forEach(param -> parameters.put(param, properties.getProperty(param)));
        initConnectionFactory(parameters);
    }

    /**
     * get connection factory name
     *
     * @return connection factory name
     */
    public String getName() {
        return name;
    }

    /**
     * Set connection factory name
     *
     * @param name name to set for the connection Factory
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return parameters.get(RabbitMQConstants.CONTENT_TYPE);
    }

    /**
     * Get all rabbit mq parameters
     *
     * @return a map of parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Initiate rabbitmq connection factory from the connection parameters
     *
     * @param parameters connection parameters
     */
    private void initConnectionFactory(Map<String, String> parameters) throws RabbitMQException {
        String hostnames = StringUtils.defaultIfEmpty(
                parameters.get(RabbitMQConstants.SERVER_HOST_NAME), ConnectionFactory.DEFAULT_HOST);
        String ports = StringUtils.defaultIfEmpty(
                parameters.get(RabbitMQConstants.SERVER_PORT), String.valueOf(ConnectionFactory.DEFAULT_AMQP_PORT));
        String username = StringUtils.defaultIfEmpty(
                parameters.get(RabbitMQConstants.SERVER_USER_NAME), ConnectionFactory.DEFAULT_USER);
        String password = StringUtils.defaultIfEmpty(
                parameters.get(RabbitMQConstants.SERVER_PASSWORD), ConnectionFactory.DEFAULT_PASS);
        String virtualHost = StringUtils.defaultIfEmpty(
                parameters.get(RabbitMQConstants.SERVER_VIRTUAL_HOST), ConnectionFactory.DEFAULT_VHOST);
        int heartbeat = NumberUtils.toInt(
                parameters.get(RabbitMQConstants.HEARTBEAT), ConnectionFactory.DEFAULT_HEARTBEAT);
        int connectionTimeout = NumberUtils.toInt(
                parameters.get(RabbitMQConstants.CONNECTION_TIMEOUT), ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT);
        long networkRecoveryInterval = NumberUtils.toLong(
                parameters.get(RabbitMQConstants.NETWORK_RECOVERY_INTERVAL), ConnectionFactory.DEFAULT_NETWORK_RECOVERY_INTERVAL);
        this.retryInterval = NumberUtils.toInt(
                parameters.get(RabbitMQConstants.RETRY_INTERVAL), RabbitMQConstants.DEFAULT_RETRY_INTERVAL);
        this.retryCount = NumberUtils.toInt(
                parameters.get(RabbitMQConstants.RETRY_COUNT), RabbitMQConstants.DEFAULT_RETRY_COUNT);
        boolean sslEnabled = BooleanUtils.toBooleanDefaultIfNull(
                BooleanUtils.toBoolean(parameters.get(RabbitMQConstants.SSL_ENABLED)), false);

        String[] hostnameArray = hostnames.split(",");
        String[] portArray = ports.split(",");
        if (hostnameArray.length == portArray.length) {
            addresses = new Address[hostnameArray.length];
            for (int i = 0; i < hostnameArray.length; i++) {
                try {
                    addresses[i] = new Address(hostnameArray[i].trim(), Integer.parseInt(portArray[i].trim()));
                } catch (NumberFormatException e) {
                    throw new RabbitMQException("Number format error in port number", e);
                }
            }
        } else {
            throw new RabbitMQException("The number of hostnames must be equal to the number of ports");
        }

        connectionFactory = new ConnectionFactory();
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        connectionFactory.setVirtualHost(virtualHost);
        connectionFactory.setRequestedHeartbeat(heartbeat);
        connectionFactory.setConnectionTimeout(connectionTimeout);
        connectionFactory.setNetworkRecoveryInterval(networkRecoveryInterval);
        connectionFactory.setAutomaticRecoveryEnabled(true);
        connectionFactory.setTopologyRecoveryEnabled(true);
        setSSL(parameters, sslEnabled);
    }

    /**
     * Set secure socket layer configuration if enabled
     *
     * @param parameters connection parameters
     * @param sslEnabled ssl enabled
     */
    private void setSSL(Map<String, String> parameters, boolean sslEnabled) {
        try {
            if (sslEnabled) {
                String keyStoreLocation = parameters.get(RabbitMQConstants.SSL_KEYSTORE_LOCATION);
                String keyStoreType = parameters.get(RabbitMQConstants.SSL_KEYSTORE_TYPE);
                String keyStorePassword = parameters.get(RabbitMQConstants.SSL_KEYSTORE_PASSWORD);
                String trustStoreLocation = parameters.get(RabbitMQConstants.SSL_TRUSTSTORE_LOCATION);
                String trustStoreType = parameters.get(RabbitMQConstants.SSL_TRUSTSTORE_TYPE);
                String trustStorePassword = parameters.get(RabbitMQConstants.SSL_TRUSTSTORE_PASSWORD);
                String sslVersion = parameters.get(RabbitMQConstants.SSL_VERSION);

                if (StringUtils.isEmpty(keyStoreLocation) || StringUtils.isEmpty(keyStoreType) ||
                    StringUtils.isEmpty(keyStorePassword) || StringUtils.isEmpty(trustStoreLocation) ||
                    StringUtils.isEmpty(trustStoreType) || StringUtils.isEmpty(trustStorePassword)) {
                    log.info("Trustore and keystore information is not provided");
                    if (StringUtils.isNotEmpty(sslVersion)) {
                        connectionFactory.useSslProtocol(sslVersion);
                    } else {
                        log.info("Proceeding with default SSL configuration");
                        connectionFactory.useSslProtocol();
                    }
                } else {
                    char[] keyPassphrase = keyStorePassword.toCharArray();
                    KeyStore ks = KeyStore.getInstance(keyStoreType);
                    ks.load(new FileInputStream(keyStoreLocation), keyPassphrase);

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(getKeyManagerType());
                    kmf.init(ks, keyPassphrase);

                    char[] trustPassphrase = trustStorePassword.toCharArray();
                    KeyStore tks = KeyStore.getInstance(trustStoreType);
                    tks.load(new FileInputStream(trustStoreLocation), trustPassphrase);

                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(getTrustManagerType());
                    tmf.init(tks);

                    SSLContext c = SSLContext.getInstance(sslVersion);
                    c.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                    connectionFactory.useSslProtocol(c);
                }
            }
        } catch (Exception e) {
            log.warn("Format error in SSL enabled value. Proceeding without enabling SSL", e);
        }
    }

    /**
     * Create a RabbitMQ connection
     *
     * @return a {@link Connection} object
     */
    public Connection createConnection() throws RabbitMQException {
        Connection connection = null;
        try {
            connection = RabbitMQUtils.createConnection(connectionFactory, addresses);
            if (connection == null) {
                throw new RabbitMQException("[" + name + "] Could not connect to RabbitMQ Broker.");
            }
            log.info("[" + name + "] Successfully connected to RabbitMQ Broker");
        } catch (IOException | RabbitMQException e) {
            log.error("[" + name + "] Error creating connection to RabbitMQ Broker. " +
                      "Reattempting to connect.", e);
            connection = retry();
            if (connection == null) {
                throw new RabbitMQException("[" + name + "] Could not connect to RabbitMQ Broker. " +
                                            "Error while creating connection", e);
            }
        }
        return connection;
    }

    /**
     * Retry when could not connect to the broker
     *
     * @return the {@link Connection} object after retry completion
     */
    private Connection retry() {
        Connection connection = null;
        int retryC = 0;
        while ((connection == null) && ((retryCount == -1) || (retryC < retryCount))) {
            retryC++;
            log.info("[" + name + "] Attempting to create connection to RabbitMQ Broker" +
                     " in " + retryInterval + "ms. Retry attempts: " + retryC);
            try {
                Thread.sleep(retryInterval);
                connection = RabbitMQUtils.createConnection(connectionFactory, addresses);
                log.info("[" + name + "] Successfully connected to RabbitMQ Broker");
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            } catch (IOException e1) {
                log.error("[" + name + "] Error while trying to reconnect to RabbitMQ Broker", e1);
            }
        }
        return connection;
    }

    private static String getKeyManagerType() {
        String provider = System.getProperty(JCE_PROVIDER);
        if (org.apache.commons.lang.StringUtils.isNotEmpty(provider)) {
            return PKIX;
        } else {
            return KeyManagerFactory.getDefaultAlgorithm();
        }
    }

    private static String getTrustManagerType() {
        String provider = System.getProperty(JCE_PROVIDER);
        if (org.apache.commons.lang.StringUtils.isNotEmpty(provider)) {
            return PKIX;
        } else {
            return TrustManagerFactory.getDefaultAlgorithm();
        }
    }
}