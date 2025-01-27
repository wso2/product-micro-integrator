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

import kafka.api.FetchRequest;
import kafka.api.FetchRequestBuilder;
import kafka.api.PartitionOffsetRequestInfo;
import kafka.common.ErrorMapping;
import kafka.common.TopicAndPartition;
import kafka.javaapi.FetchResponse;
import kafka.javaapi.OffsetRequest;
import kafka.javaapi.OffsetResponse;
import kafka.javaapi.PartitionMetadata;
import kafka.javaapi.TopicMetadata;
import kafka.javaapi.TopicMetadataRequest;
import kafka.javaapi.TopicMetadataResponse;
import kafka.javaapi.consumer.SimpleConsumer;
import kafka.message.MessageAndOffset;
import org.apache.synapse.SynapseException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class SimpleKafkaMessageListener extends AbstractKafkaMessageListener {
    private List<String> replicaBrokers;
    private boolean init;
    private String topic;
    private long maxReads = Long.MAX_VALUE;
    private int partition;
    private List<String> seedBrokers;
    private int port;
    private String leadBroker;
    private String clientName;
    private SimpleConsumer consumer;
    private long readOffset;

    public SimpleKafkaMessageListener(Properties kafkaProperties, InjectHandler injectHandler) throws Exception {
        this.kafkaProperties = kafkaProperties;
        this.injectHandler = injectHandler;
        replicaBrokers = new ArrayList<String>();
        validateInputParameters();
    }

    /**
     * Validate the input parameters for low level consumer
     */
    private void validateInputParameters() throws Exception {
        if (kafkaProperties.getProperty(KAFKAConstants.SIMPLE_TOPIC) == null) {
            log.error("simple consumer topic is invalid");
            throw new SynapseException("simple consumer topic is invalid");
        } else {
            this.topic = kafkaProperties.getProperty(KAFKAConstants.SIMPLE_TOPIC);
        }
        if (kafkaProperties.getProperty(KAFKAConstants.SIMPLE_BROKERS) == null) {
            log.error("simple consumer brokers is invalid");
            throw new SynapseException("simple consumer brokers is invalid");
        } else {
            this.seedBrokers = getSeedBrokers(kafkaProperties.getProperty(KAFKAConstants.SIMPLE_BROKERS));
        }
        if (kafkaProperties.getProperty(KAFKAConstants.SIMPLE_PORT) == null) {
            log.error("simple consumer port is invalid");
            throw new SynapseException("simple consumer port is invalid");
        } else {
            try {
                this.port = Integer.parseInt(kafkaProperties.getProperty(KAFKAConstants.SIMPLE_PORT));
            } catch (NumberFormatException nfe) {
                log.error("simple consumer port should be number." + nfe.getMessage(), nfe);
                throw new SynapseException("simple consumer port should be number.", nfe);
            }
        }
        if (kafkaProperties.getProperty(KAFKAConstants.SIMPLE_PARTITION) == null) {
            log.error("simple consumer partition is invalid");
            throw new SynapseException("simple consumer partition is invalid");
        } else {
            try {
                this.partition = Integer.parseInt(kafkaProperties.getProperty(KAFKAConstants.SIMPLE_PARTITION));
            } catch (NumberFormatException nfe) {
                log.error("simple partition should be a number " + nfe.getMessage(), nfe);
                throw new SynapseException("simple partition should be a number", nfe);
            }
        }
        if (kafkaProperties.getProperty(KAFKAConstants.SIMPLE_MAX_MSG_TO_READ) == null) {
            log.error("simple consumer maximum messages to read is invalid");
            throw new SynapseException("simple consumer maximum messages to read is invalid");
        } else {
            try {
                this.maxReads = Long.parseLong(kafkaProperties.getProperty(KAFKAConstants.SIMPLE_MAX_MSG_TO_READ));
            } catch (NumberFormatException nfe) {
                log.error("maximum messages should be a number " + nfe.getMessage(), nfe);
                throw new SynapseException("maximum messages should be a number", nfe);
            }
        }

    }

    /**
     * Get the brokers from the broker list parameter
     *
     * @param brokers
     * @return
     */
    private List<String> getSeedBrokers(String brokers) {
        try {
            return Arrays.asList(brokers.split(","));
        } catch (Exception nfe) {
            log.error("Error to split the brokers from broker list" + nfe.getMessage(), nfe);
            throw new SynapseException("Error to split the brokers from broker list", nfe);
        }
    }

    @Override
    public boolean createKafkaConsumerConnector() throws Exception {
        return run();
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void injectMessageToESB(String name) {

        log.debug("Fetch the messages until maximum message is zero");
        if (maxReads > 0) {
            if (consumer == null) {
                consumer = new SimpleConsumer(leadBroker, port, KAFKAConstants.SO_TIMEOUT, KAFKAConstants.BUFFER_SIZE,
                                              clientName);
            }
            FetchRequest req = new FetchRequestBuilder().clientId(clientName)
                    .addFetch(topic, partition, readOffset, KAFKAConstants.SO_TIMEOUT).build();
            FetchResponse fetchResponse = consumer.fetch(req);

            if (fetchResponse.hasError()) {
                short code = fetchResponse.errorCode(topic, partition);
                log.error("Error fetching data from the Broker:" + leadBroker + " Reason: " + code);
                if (code == ErrorMapping.OffsetOutOfRangeCode()) {
                    readOffset = getLastOffset(consumer, topic, partition, kafka.api.OffsetRequest.LatestTime(),
                                               clientName);
                }
                consumer.close();
                consumer = null;
                try {
                    leadBroker = findNewLeader(leadBroker, topic, partition, port);
                } catch (Exception e) {
                    log.error("Error to find the new leader " + e.getMessage(), e);
                }
            }
            try {
                for (MessageAndOffset messageAndOffset : fetchResponse.messageSet(topic, partition)) {
                    long currentOffset = messageAndOffset.offset();
                    if (currentOffset < readOffset) {
                        log.info("Found an old offset: " + currentOffset + " Expecting: " + readOffset);
                        continue;
                    }
                    readOffset = messageAndOffset.nextOffset();
                    ByteBuffer payload = messageAndOffset.message().payload();

                    byte[] bytes = new byte[payload.limit()];
                    payload.get(bytes);
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("Start : Add to injectHandler to invoke");
                        }
                        KafkaMessageContext kafkaMessageContext = new KafkaMessageContext(consumer.host(), port, topic,
                                bytes);
                        injectHandler.invoke(kafkaMessageContext, name);
                        if (log.isDebugEnabled()) {
                            log.debug("End : Add the injectHandler to invoke");
                        }

                    } catch (Exception e) {
                        log.error("Error while invoking the bytes " + e.getMessage(), e);
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("0 - added to queue!");
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Reduce the maximum message by 1");
                    }
                    maxReads--;
                    if (maxReads < 1) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error while fetching the responses" + e.getMessage(), e);
                log.debug("Error to fetch the responses");
            } finally {
                if (consumer != null)
                    consumer.close();
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (maxReads == Long.MAX_VALUE) {
            return true;
        }
        return maxReads > 0;
    }

    public boolean run() throws Exception {

        if (init) {
            return init;
        }
        // find the meta data about the topic and partition we are interested in
        PartitionMetadata metadata = findLeader(seedBrokers, port, topic, partition);
        if (metadata == null) {
            throw new SynapseException("Can't find metadata for Topic and Partition. Exiting");
        }
        if (metadata.leader() == null) {
            throw new SynapseException("Can't find Leader for Topic and Partition. Exiting");
        }
        this.leadBroker = metadata.leader().host();
        this.clientName = "Client_" + topic + "_" + partition;

        this.consumer = new SimpleConsumer(leadBroker, port, KAFKAConstants.BUFFER_SIZE, KAFKAConstants.SO_TIMEOUT,
                                           clientName);
        this.readOffset = getLastOffset(consumer, topic, partition, kafka.api.OffsetRequest.EarliestTime(), clientName);
        init = true;

        return init;
    }

    public static long getLastOffset(SimpleConsumer consumer, String topic, int partition, long whichTime,
                                     String clientName) {
        TopicAndPartition topicAndPartition = new TopicAndPartition(topic, partition);
        Map<TopicAndPartition, PartitionOffsetRequestInfo> requestInfo = new HashMap<TopicAndPartition, PartitionOffsetRequestInfo>();
        requestInfo.put(topicAndPartition, new PartitionOffsetRequestInfo(whichTime, 1));
        OffsetRequest request = new OffsetRequest(requestInfo, kafka.api.OffsetRequest.CurrentVersion(), clientName);
        OffsetResponse response = consumer.getOffsetsBefore(request);

        if (response.hasError()) {
            log.error("Error fetching data Offset Data the Broker. Reason: " + response.errorCode(topic, partition));
            return 0;
        }
        long[] offsets = response.offsets(topic, partition);
        return offsets[0];
    }

    private String findNewLeader(String oldLeader, String topic, int partition, int port) throws Exception {
        for (int i = 0; i < 3; i++) {
            boolean goToSleep = false;
            PartitionMetadata metadata = findLeader(replicaBrokers, port, topic, partition);
            if (metadata == null) {
                goToSleep = true;
            } else if (metadata.leader() == null) {
                goToSleep = true;
            } else if (oldLeader.equalsIgnoreCase(metadata.leader().host()) && i == 0) {
                goToSleep = true;
            } else {
                return metadata.leader().host();
            }
            if (goToSleep) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                }
            }
        }
        throw new SynapseException("Unable to find new leader after Broker failure. Exiting");
    }

    private PartitionMetadata findLeader(List<String> a_seedBrokers, int port, String topic, int partition)
            throws Exception {
        PartitionMetadata returnMetaData = null;
        loop:
        for (String seed : a_seedBrokers) {
            SimpleConsumer consumer = null;
            try {
                consumer = new SimpleConsumer(seed, port, KAFKAConstants.SO_TIMEOUT, KAFKAConstants.BUFFER_SIZE,
                                              "leaderLookup");
                List<String> topics = Collections.singletonList(topic);
                TopicMetadataRequest req = new TopicMetadataRequest(topics);
                TopicMetadataResponse resp = consumer.send(req);

                List<TopicMetadata> metaData = resp.topicsMetadata();
                for (TopicMetadata item : metaData) {
                    for (PartitionMetadata part : item.partitionsMetadata()) {
                        if (part.partitionId() == partition) {
                            returnMetaData = part;
                            break loop;
                        }
                    }
                }
            } catch (Exception e) {
                throw new SynapseException(
                        "Error communicating with Broker [" + seed + "] to find Leader for [" + topic + ", " + partition
                                + "] Reason: ", e);
            } finally {
                if (consumer != null)
                    consumer.close();
            }
        }
        if (returnMetaData != null) {
            replicaBrokers.clear();
            for (kafka.cluster.Broker replica : returnMetaData.replicas()) {
                replicaBrokers.add(replica.host());
            }
        }
        return returnMetaData;
    }

}
