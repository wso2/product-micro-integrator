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
package org.wso2.carbon.inbound.endpoint.protocol.kafka;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundTaskProcessor;
import org.apache.synapse.task.TaskStartupObserver;
import org.wso2.carbon.inbound.endpoint.common.InboundRequestProcessorImpl;
import org.wso2.carbon.inbound.endpoint.common.InboundTask;
import org.wso2.carbon.inbound.endpoint.protocol.PollingConstants;

import java.util.Properties;

public class KAFKAProcessor extends InboundRequestProcessorImpl implements TaskStartupObserver, InboundTaskProcessor {
    private static final Log log = LogFactory.getLog(KAFKAProcessor.class.getName());

    private static final String ENDPOINT_POSTFIX = "KAFKA" + COMMON_ENDPOINT_POSTFIX;

    private KAFKAPollingConsumer pollingConsumer;
    private Properties kafkaProperties;
    private String injectingSeq;
    private String onErrorSeq;
    private boolean sequential;

    public KAFKAProcessor(InboundProcessorParams params) {

        this.name = params.getName();
        this.kafkaProperties = params.getProperties();

        String inboundEndpointInterval = kafkaProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_INTERVAL);
        if (inboundEndpointInterval != null) {
            try {
                this.interval = Long.parseLong(inboundEndpointInterval);
            } catch (NumberFormatException nfe) {
                log.error("Invalid numeric value for interval." + nfe.getMessage(), nfe);
                throw new SynapseException("Invalid numeric value for interval.", nfe);
            }
        }
        this.sequential = true;
        String inboundEndpointSequential = kafkaProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL);
        if (inboundEndpointSequential != null) {
            try {
                this.sequential = Boolean.parseBoolean(inboundEndpointSequential);
            } catch (Exception e) {
                log.error("The sequential value should be true or false." + e.getMessage(), e);
                throw new SynapseException("The sequential value should be true or false", e);
            }
        }
        this.coordination = true;
        String inboundCoordination = kafkaProperties.getProperty(PollingConstants.INBOUND_COORDINATION);
        if (inboundCoordination != null) {
            try {
                this.coordination = Boolean.parseBoolean(inboundCoordination);
            } catch (Exception e) {
                log.error("The Coordination value should be true or false." + e.getMessage(), e);
                throw new SynapseException("The Coordination value should be true or false", e);
            }
        }
        this.injectingSeq = params.getInjectingSeq();
        this.onErrorSeq = params.getOnErrorSeq();
        this.synapseEnvironment = params.getSynapseEnvironment();
        this.startInPausedMode = params.startInPausedMode();
    }

    /**
     * This will be called at the time of synapse artifact deployment.
     */
    public void init() {
        /*
         * The activate/deactivate functionality for the Kafka Inbound Endpoint is not currently implemented.
         *
         * Therefore, the following check has been added to immediately return if the "suspend"
         * attribute is set to true in the inbound endpoint configuration.
         *
         * Note: This implementation is temporary and should be revisited and improved once
         * the activate/deactivate capability for Kafka listener is implemented.
         */
        if (startInPausedMode) {
            log.info("Inbound endpoint [" + name + "] is currently suspended.");
            return;
        }
        log.info("Initializing inbound KAFKA listener for destination " + name);
        try {
            pollingConsumer = new KAFKAPollingConsumer(kafkaProperties, interval, name);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return;
        }
        pollingConsumer.registerHandler(new KAFKAInjectHandler(injectingSeq, onErrorSeq, sequential, synapseEnvironment,
                                                               kafkaProperties
                                                                       .getProperty(KAFKAConstants.CONTENT_TYPE)));
        try {
            pollingConsumer.startsMessageListener();
        } catch (Exception e) {
            log.error("Error initializing message listener " + e.getMessage(), e);
            throw new SynapseException("Error initializing message listener", e);
        }
        start();
    }

    /**
     * Register/start the schedule service
     */
    public void start() {
        InboundTask task = new KAFKATask(pollingConsumer, interval);
        start(task, ENDPOINT_POSTFIX);
    }

    public void update() {
        // This will not be called for inbound endpoints
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void destroy() {
        try {
            if (pollingConsumer != null && pollingConsumer.messageListener != null
                    && pollingConsumer.messageListener.consumerConnector != null) {
                pollingConsumer.messageListener.consumerConnector.shutdown();
                log.info("Shutdown the kafka consumer connector");
            }
        } catch (Exception e) {
            log.error("Error while shutdown the consumer connector" + e.getMessage(), e);
        }
        super.destroy();
    }

    @Override
    public boolean activate() {

        return false;
    }

    @Override
    public boolean deactivate() {

        return false;
    }

    /**
     * Remove inbound endpoints.
     *
     * @param removeTask Whether to remove scheduled task from the registry or not.
     */
    @Override
    public void destroy(boolean removeTask) {
        if (removeTask) {
            destroy();
        }
    }
}
