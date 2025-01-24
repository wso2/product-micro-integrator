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

import kafka.consumer.Blacklist;
import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.ConsumerTimeoutException;
import kafka.consumer.KafkaStream;
import kafka.consumer.Whitelist;
import kafka.message.MessageAndMetadata;
import org.I0Itec.zkclient.exception.ZkTimeoutException;
import org.apache.synapse.SynapseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class KAFKAMessageListener extends AbstractKafkaMessageListener {

    public KAFKAMessageListener(int threadCount, List<String> topics, Properties kafkaProperties,
                                InjectHandler injectHandler) throws Exception {
        this.threadCount = threadCount;
        this.topics = topics;
        this.kafkaProperties = kafkaProperties;
        this.injectHandler = injectHandler;
    }

    /**
     * Create the connection with the zookeeper to consume the messages
     */
    public boolean createKafkaConsumerConnector() throws Exception {

        log.debug("Create the connection and start to consume the streams");
        boolean isCreated;
        try {
            if (consumerConnector == null) {
                log.info("Creating Kafka Consumer Connector...");

                //set default consumer timeout to 3000ms if it is not set by the user
                if (!kafkaProperties.containsKey(KAFKAConstants.CONSUMER_TIMEOUT)) {
                    kafkaProperties.put(KAFKAConstants.CONSUMER_TIMEOUT, "3000");
                }
                consumerConnector = Consumer.createJavaConsumerConnector(new ConsumerConfig(kafkaProperties));
                log.info("Kafka Consumer Connector is created");
                start();
            }
            isCreated = true;
        } catch (ZkTimeoutException toe) {
            log.error(" Error in Creating Kafka Consumer Connector | ZkTimeout" + toe.getMessage());
            throw new SynapseException(" Error in Creating Kafka Consumer Connector| ZkTimeout");

        } catch (Exception e) {
            log.error(" Error in Creating Kafka Consumer Connector." + e.getMessage(), e);
            throw new SynapseException(" Error in Creating Kafka Consumer Connector ", e);
        }
        return isCreated;
    }

    /**
     * Starts topics consuming the messages,the message can be consumed by topic or topic filter which are white list and black list.
     */
    public void start() throws Exception {

        log.debug("Start to consume the streams");
        try {
            log.info("Starting KAFKA consumer...");
            Map<String, Integer> topicCount = new HashMap<String, Integer>();

            if (topics != null && topics.size() > 0) {
                // Define threadCount thread/s for topic
                for (String topic : topics) {
                    topicCount.put(topic, threadCount);
                }
                Map<String, List<KafkaStream<byte[], byte[]>>> consumerStreams = consumerConnector
                        .createMessageStreams(topicCount);

                consumerIte = new ArrayList<ConsumerIterator<byte[], byte[]>>();
                for (String topic : topics) {
                    List<KafkaStream<byte[], byte[]>> streams = consumerStreams.get(topic);
                    startConsumers(streams);

                }
            } else if (kafkaProperties.getProperty(KAFKAConstants.TOPIC_FILTER) != null) {
                // Define #threadCount thread/s for topic filter
                List<KafkaStream<byte[], byte[]>> consumerStreams;
                boolean isFromWhiteList = (kafkaProperties.getProperty(KAFKAConstants.FILTER_FROM_WHITE_LIST) == null
                        || kafkaProperties.getProperty(KAFKAConstants.FILTER_FROM_WHITE_LIST).isEmpty()) ?
                        Boolean.TRUE :
                        Boolean.parseBoolean(kafkaProperties.getProperty(KAFKAConstants.FILTER_FROM_WHITE_LIST));
                if (isFromWhiteList) {
                    consumerStreams = consumerConnector.createMessageStreamsByFilter(
                            new Whitelist(kafkaProperties.getProperty(KAFKAConstants.TOPIC_FILTER)), threadCount);
                } else {
                    consumerStreams = consumerConnector.createMessageStreamsByFilter(
                            new Blacklist(kafkaProperties.getProperty(KAFKAConstants.TOPIC_FILTER)), threadCount);
                }

                startConsumers(consumerStreams);
            }

        } catch (Exception e) {
            log.error("Error while Starting KAFKA consumer." + e.getMessage(), e);
            throw new SynapseException("Error while Starting KAFKA consumer.", e);
        }
    }

    /**
     * Use one stream from kafka stream iterator
     *
     * @param streams
     */
    protected void startConsumers(List<KafkaStream<byte[], byte[]>> streams) {
        if (streams.size() >= 1) {
            consumerIte.add(streams.get(0).iterator());
        }
    }

    @Override
    public void injectMessageToESB(String name) {
        if (consumerIte.size() == 1) {
            injectMessageToESB(name, consumerIte.get(0));
        } else {
            log.debug("There are multiple topics to consume from not a single topic");
        }
    }

    public void injectMessageToESB(String sequenceName, ConsumerIterator<byte[], byte[]> consumerIterator) {
        MessageAndMetadata<byte[], byte[]> messageAndMetadata = consumerIterator.next();
        KafkaMessageContext kafkaMessageContext = new KafkaMessageContext(
                kafkaProperties.getProperty(KAFKAConstants.ZOOKEEPER_CONNECT),
                messageAndMetadata.topic(), messageAndMetadata.message());
        injectHandler.invoke(kafkaMessageContext, sequenceName);
    }

    @Override
    public boolean hasNext() {
        if (consumerIte.size() == 1) {
            return hasNext(consumerIte.get(0));
        } else {
            log.debug("There are multiple topics to consume from not a single topic,");
        }
        return false;
    }

    public boolean hasNext(ConsumerIterator<byte[], byte[]> consumerIterator) {
        try {
            return consumerIterator.hasNext();
        } catch (ConsumerTimeoutException e) {
            //exception ignored
            if (log.isDebugEnabled()) {
                log.debug("Topic has no new messages to consume.");
            }
            return false;
        } catch (Exception e) {
            //Message Listening thread is interrupted during server shutdown. This happen during ESB shutdown is
            // triggered
            if (log.isDebugEnabled()) {
                log.debug("Kafka listener is interrupted by server shutdown.", e);
            }
            return false;
        }
    }

    @Override
    public boolean hasMultipleTopicsToConsume() {
        if (consumerIte.size() > 1) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void consumeMultipleTopics(String name) {
        for (ConsumerIterator<byte[], byte[]> consumerIterator : consumerIte) {
            if (hasNext(consumerIterator)) {
                injectMessageToESB(name, consumerIterator);
            }
        }
    }
}
