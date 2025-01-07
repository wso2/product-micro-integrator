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

package org.wso2.carbon.inbound.endpoint.protocol.mqtt;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.wso2.carbon.inbound.endpoint.common.InboundOneTimeTriggerRequestProcessor;
import org.wso2.carbon.inbound.endpoint.protocol.PollingConstants;

import java.util.Properties;
import javax.net.ssl.SSLSocketFactory;

import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_ID;

/**
 * This is the listener which directly interacts with the external MQTT server. Every MQTT
 * inbound listener is bound to Server Port, Server Host, Client ID.
 */
public class MqttListener extends InboundOneTimeTriggerRequestProcessor {

    private static final String ENDPOINT_POSTFIX = "MQTT" + COMMON_ENDPOINT_POSTFIX;
    private static final Log log = LogFactory.getLog(MqttListener.class);

    private String injectingSeq;
    private String onErrorSeq;

    private Properties mqttProperties;
    private String contentType;
    private boolean sequential;

    private MqttConnectionFactory confac;
    private MqttAsyncClient mqttAsyncClient;
    private MqttAsyncCallback mqttAsyncCallback;
    private MqttConnectOptions connectOptions;
    private MqttConnectionConsumer connectionConsumer;
    private MqttInjectHandler injectHandler;

    protected String userName;
    protected String password;

    protected boolean cleanSession;

    private InboundProcessorParams params;

    private SSLSocketFactory socketFactory;

    /**
     * constructor for the MQTT inbound endpoint listener     *
     *
     * @param params
     */
    public MqttListener(InboundProcessorParams params) {

        this.name = params.getName();
        this.injectingSeq = params.getInjectingSeq();
        this.onErrorSeq = params.getOnErrorSeq();
        this.synapseEnvironment = params.getSynapseEnvironment();
        this.mqttProperties = params.getProperties();
        this.params = params;
        this.startInPausedMode = params.startInPausedMode();

        //assign default value if sequential mode parameter is not present
        this.sequential = true;
        if (mqttProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL) != null) {
            this.sequential = Boolean
                    .parseBoolean(mqttProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL));
        }

        //assign default value if coordination mode parameter is not present
        this.coordination = true;
        if (mqttProperties.getProperty(PollingConstants.INBOUND_COORDINATION) != null) {
            this.coordination = Boolean.parseBoolean(mqttProperties.getProperty(PollingConstants.INBOUND_COORDINATION));
        }

        this.confac = new MqttConnectionFactory(mqttProperties);
        this.contentType = confac.getContent();

        this.injectHandler = new MqttInjectHandler(injectingSeq, onErrorSeq, sequential, synapseEnvironment,
                                                   contentType);
        this.synapseEnvironment = params.getSynapseEnvironment();
        this.socketFactory = confac.getSSLSocketFactory();

        //mqtt connection options
        if (mqttProperties.getProperty(MqttConstants.MQTT_USERNAME) != null) {
            this.userName = mqttProperties.getProperty(MqttConstants.MQTT_USERNAME);
        }

        if (mqttProperties.getProperty(MqttConstants.MQTT_PASSWORD) != null) {
            this.password = mqttProperties.getProperty(MqttConstants.MQTT_PASSWORD);
        }

        if (mqttProperties.getProperty(MqttConstants.MQTT_SESSION_CLEAN) != null) {
            this.cleanSession = Boolean.parseBoolean(mqttProperties.getProperty(MqttConstants.MQTT_SESSION_CLEAN));
        }
    }

    @Override
    public void destroy() {
        destroy(true);
    }

    @Override
    public void destroy(boolean removeTask) {
        log.info("Mqtt Inbound endpoint: " + name + " Started destroying context.");
        MqttClientManager clientManager = MqttClientManager.getInstance();
        String inboundIdentifier = clientManager
                .buildIdentifier(mqttAsyncClient.getClientId(), confac.getServerHost(), confac.getServerPort());
        //we should ignore the case of manually loading of tenant
        //we maintain a flag for cases where we load the tenant manually
        if (!clientManager.isInboundTenantLoadingFlagSet(inboundIdentifier)) {
            //release the thread from suspension
            //this will release thread suspended thread for completion
            connectionConsumer.shutdown();
            mqttAsyncCallback.shutdown();
            confac.shutdown(mqttAsyncClient.isConnected());
            try {
                if (mqttAsyncClient.isConnected()) {
                    mqttAsyncClient.unsubscribe(confac.getTopic());
                    mqttAsyncClient.disconnect();
                }
                mqttAsyncClient.close();

                String nameIdentifier = clientManager.buildNameIdentifier(name, String.valueOf(SUPER_TENANT_ID));
                //here we unregister it because this is not a case of tenant loading
                MqttClientManager.getInstance().unregisterMqttClient(inboundIdentifier, nameIdentifier);

                log.info("Disconnected from the remote MQTT server.");
            } catch (MqttException e) {
                log.error("Error while disconnecting from the remote server.");
            }
        }
        super.destroy(removeTask);
    }

    @Override
    public void init() {
        /*
         * The activate/deactivate functionality for the MQTT protocol is not currently implemented
         * for Inbound Endpoints.
         *
         * Therefore, the following check has been added to immediately return if the "suspend"
         * attribute is set to true in the inbound endpoint configuration.
         *
         * Note: This implementation is temporary and should be revisited and improved once
         * the activate/deactivate capability for MQTT listener is implemented.
         */
        if (startInPausedMode) {
            log.info("Inbound endpoint [" + name + "] is currently suspended.");
            return;
        }
        log.info("MQTT inbound endpoint " + name + " initializing ...");
        initAsyncClient();
        start();
    }

    public void initAsyncClient() {

        mqttAsyncClient = confac.getMqttAsyncClient(this.name);

        MqttClientManager clientManager = MqttClientManager.getInstance();
        String inboundIdentifier = clientManager
                .buildIdentifier(mqttAsyncClient.getClientId(), confac.getServerHost(), confac.getServerPort());

        if (!clientManager.hasMqttCallback(inboundIdentifier)) {
            //registering callback for the first time
            connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(cleanSession);
            if (userName != null && password != null) {
                connectOptions.setUserName(userName);
                connectOptions.setPassword(password.toCharArray());
            }
            if (socketFactory != null) {
                connectOptions.setSocketFactory(socketFactory);
            }
            mqttAsyncCallback = new MqttAsyncCallback(mqttAsyncClient, injectHandler, confac, connectOptions,
                                                      mqttProperties);
            mqttAsyncCallback.setName(params.getName());
            connectionConsumer = new MqttConnectionConsumer(connectOptions, mqttAsyncClient, confac, mqttProperties,
                                                            name);
            mqttAsyncCallback.setMqttConnectionConsumer(connectionConsumer);
            mqttAsyncClient.setCallback(mqttAsyncCallback);
            //here we register the callback handler
            clientManager.registerMqttCallback(inboundIdentifier, mqttAsyncCallback);
        } else {
            //has previously registered callback we just update the reference
            //in other words has previous un-destroyed callback
            //this is a manually tenant loading case
            //should clear the previously set tenant loading flags for the inbound identifier
            clientManager.unRegisterInboundTenantLoadingFlag(inboundIdentifier);

            mqttAsyncCallback = clientManager.getMqttCallback(inboundIdentifier);

            mqttAsyncCallback.setName(params.getName());
            connectOptions = mqttAsyncCallback.getMqttConnectionOptions();
            connectionConsumer = mqttAsyncCallback.getMqttConnectionConsumer();

            //but we need to update injectHandler due to recreation of synapse environment
            mqttAsyncCallback.updateInjectHandler(injectHandler);

        }
    }

    public void start() {
        MqttTask mqttTask = new MqttTask(connectionConsumer);
        mqttTask.setCallback(mqttAsyncCallback);
        start(mqttTask, ENDPOINT_POSTFIX);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
