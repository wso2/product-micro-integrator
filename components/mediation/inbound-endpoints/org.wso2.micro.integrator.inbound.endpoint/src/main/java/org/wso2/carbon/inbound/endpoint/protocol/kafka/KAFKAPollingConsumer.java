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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class KAFKAPollingConsumer {
    private static final Log log = LogFactory.getLog(KAFKAPollingConsumer.class.getName());

    private InjectHandler injectHandler;
    private Properties kafkaProperties;
    private int threadCount;
    private List<String> topics;
    protected AbstractKafkaMessageListener messageListener;
    private long scanInterval;
    private Long lastRanTime;
    private String name;

    /**
     * Initialize the kafka properties and the polling interval
     */
    public KAFKAPollingConsumer(Properties kafkaProperties, long interval, String name) throws Exception {

        this.kafkaProperties = kafkaProperties;
        this.scanInterval = interval;
        this.name = name;
        try {
            if (kafkaProperties.getProperty(KAFKAConstants.THREAD_COUNT) == null || kafkaProperties
                    .getProperty(KAFKAConstants.THREAD_COUNT).equals("")
                    || Integer.parseInt(kafkaProperties.getProperty(KAFKAConstants.THREAD_COUNT)) <= 0) {
                this.threadCount = 1;
            } else {
                this.threadCount = Integer.parseInt(kafkaProperties.getProperty(KAFKAConstants.THREAD_COUNT));
            }
        } catch (NumberFormatException nfe) {
            log.error("Invalid numeric value for thread count." + nfe.getMessage(), nfe);
            throw new SynapseException("Invalid numeric value for thread count.", nfe);
        }
        if (kafkaProperties.getProperty(KAFKAConstants.TOPICS) != null) {
            this.topics = Arrays.asList(kafkaProperties.getProperty(KAFKAConstants.TOPICS).split(","));
        }
    }

    /**
     * Start the listener to listen when new messages come to the esb,the listener can be high level or low level.
     */
    public void startsMessageListener() throws Exception {

        log.debug("Create the Kafka message listener");
        if (messageListener == null) {
            //Start a high level listener
            try {
                if (kafkaProperties.getProperty(KAFKAConstants.CONSUMER_TYPE) == null || kafkaProperties
                        .getProperty(KAFKAConstants.CONSUMER_TYPE).isEmpty() || kafkaProperties
                        .getProperty(KAFKAConstants.CONSUMER_TYPE)
                        .equalsIgnoreCase(AbstractKafkaMessageListener.CONSUMER_TYPE.HIGHLEVEL.getName())) {
                    messageListener = new KAFKAMessageListener(threadCount, topics, kafkaProperties, injectHandler);
                    //Start a low level listener
                } else if (kafkaProperties.getProperty(KAFKAConstants.CONSUMER_TYPE)
                        .equalsIgnoreCase(AbstractKafkaMessageListener.CONSUMER_TYPE.SIMPLE.getName())) {
                    messageListener = new SimpleKafkaMessageListener(kafkaProperties, injectHandler);
                }
            } catch (Exception e) {
                log.error("The consumer type should be high level or simple." + e.getMessage(), e);
                throw new SynapseException("The consumer type should be high level or simple", e);
            }
        }
    }

    public void execute() {
        try {
            log.debug("Executing : KAFKA Inbound EP : ");
            // Check if the cycles are running in correct interval and start
            // scan
            long currentTime = (new Date()).getTime();
            if (lastRanTime == null || ((lastRanTime + (scanInterval)) <= currentTime)) {
                lastRanTime = currentTime;
                poll();
            } else if (log.isDebugEnabled()) {
                log.debug("Skip cycle since concurrent rate is higher than the scan interval : KAFKA Inbound EP ");
            }
            if (log.isDebugEnabled()) {
                log.debug("End : KAFKA Inbound EP : ");
            }
        } catch (Exception e) {
            log.error("Error while retrieving or injecting KAFKA message." + e.getMessage(), e);
        }
    }

    /**
     * Register a handler to implement injection of the retrieved message
     *
     * @param processingHandler
     */
    public void registerHandler(InjectHandler processingHandler) {
        injectHandler = processingHandler;
    }

    /**
     * Create the connection with the zookeeper and inject the messages to the sequence
     */
    public Object poll() {

        log.debug("Run to poll messages and inject to the sequence");
        //Create the connection to the zookeeper and start to consume the message steams
        try {
            if (!messageListener.createKafkaConsumerConnector()) {
                return null;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        //Inject the messages to the sequence
        try {
            if (messageListener.hasMultipleTopicsToConsume()) {
                if (injectHandler != null) {
                    messageListener.consumeMultipleTopics(name);
                } else {
                    return null;
                }
            } else {
                if (injectHandler != null && messageListener.hasNext()) {
                    messageListener.injectMessageToESB(name);
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error while receiving KAFKA message." + e.getMessage(), e);
        }
        return null;
    }

    public Properties getInboundProperties() {
        return kafkaProperties;
    }

}
