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

package org.wso2.carbon.inbound.endpoint.protocol.rabbitmq;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.wso2.carbon.inbound.endpoint.common.InboundOneTimeTriggerRequestProcessor;
import org.wso2.carbon.inbound.endpoint.protocol.PollingConstants;

import java.util.Objects;
import java.util.Properties;

/**
 * The listener implementation to initialize the RabbitMQ consumer once with inbound parameters.
 */
public class RabbitMQListener extends InboundOneTimeTriggerRequestProcessor {

    private static final String ENDPOINT_POSTFIX = "RABBITMQ" + COMMON_ENDPOINT_POSTFIX;
    private static final Log log = LogFactory.getLog(RabbitMQListener.class);
    private String injectingSeq;
    private String onErrorSeq;
    private Properties rabbitmqProperties;
    private boolean sequential;
    private RabbitMQConnectionFactory rabbitMQConnectionFactory;
    private RabbitMQConsumer rabbitMQConsumer;
    private RabbitMQInjectHandler injectHandler;

    public RabbitMQListener(InboundProcessorParams params) {
        this.name = params.getName();
        this.injectingSeq = params.getInjectingSeq();
        this.onErrorSeq = params.getOnErrorSeq();
        this.startInPausedMode = params.startInPausedMode();
        this.synapseEnvironment = params.getSynapseEnvironment();
        this.rabbitmqProperties = params.getProperties();

        this.sequential = BooleanUtils.toBooleanDefaultIfNull(BooleanUtils.toBooleanObject(
                rabbitmqProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL)), true);

        this.coordination = BooleanUtils.toBooleanDefaultIfNull(BooleanUtils.toBooleanObject(
                rabbitmqProperties.getProperty(PollingConstants.INBOUND_COORDINATION)), true);

        try {
            rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitmqProperties);
        } catch (RabbitMQException e) {
            throw new SynapseException("Error occurred while initializing the connection factory.", e);
        }

        injectHandler = new RabbitMQInjectHandler(injectingSeq, onErrorSeq, sequential, synapseEnvironment);
    }

    @Override
    public void destroy() {
        destroy(true);
    }

    @Override
    public void destroy(boolean removeTask) {
        if (Objects.nonNull(rabbitMQConsumer)) {
            rabbitMQConsumer.close();
        }
        super.destroy(removeTask);
    }

    @Override
    public void init() {
        /*
         * The activate/deactivate functionality for the RabbitMQ Inbound Endpoint is not currently implemented.
         *
         * Therefore, the following check has been added to immediately return if the "suspend"
         * attribute is set to true in the inbound endpoint configuration.
         *
         * Note: This implementation is temporary and should be revisited and improved once
         * the activate/deactivate capability for RabbitMQ listener is implemented.
         */
        if (startInPausedMode) {
            log.info("Inbound endpoint [" + name + "] is currently suspended.");
            return;
        }
        log.info("RABBITMQ inbound endpoint " + name + " initializing ...");
        rabbitMQConsumer = new RabbitMQConsumer(rabbitMQConnectionFactory, rabbitmqProperties, injectHandler);
        rabbitMQConsumer.setInboundName(name);
        start();
    }

    private void start() {
        RabbitMQTask rabbitMQTask = new RabbitMQTask(rabbitMQConsumer);
        start(rabbitMQTask, ENDPOINT_POSTFIX);
    }

}
