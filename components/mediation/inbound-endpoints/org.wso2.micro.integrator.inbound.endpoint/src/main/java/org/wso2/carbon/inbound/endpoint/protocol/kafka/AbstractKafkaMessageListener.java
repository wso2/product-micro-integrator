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

import kafka.consumer.ConsumerIterator;
import kafka.javaapi.consumer.ConsumerConnector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.Properties;

public abstract class AbstractKafkaMessageListener {
    protected int threadCount;
    protected List<String> topics;
    protected ConsumerConnector consumerConnector;
    protected InjectHandler injectHandler;
    protected Properties kafkaProperties;
    protected List<ConsumerIterator<byte[], byte[]>> consumerIte;
    protected static final Log log = LogFactory.getLog(KAFKAMessageListener.class.getName());

    /**
     * the consumer types are high level and simple,high level is used for kafka high level configuration
     * and simple is used for kafka low level configuration
     */
    public static enum CONSUMER_TYPE {

        HIGHLEVEL("highlevel"), SIMPLE("simple");
        String name;

        private CONSUMER_TYPE(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Create the connection with the zookeeper
     */
    public abstract boolean createKafkaConsumerConnector() throws Exception;

    /**
     * Start to consume the messages from topics
     */
    public abstract void start() throws Exception;

    /**
     * Destroy consuming the messages
     */
    public abstract void destroy();

    /**
     * Poll the messages from the zookeeper and injected to the sequence
     */
    public abstract void injectMessageToESB(String name);

    /**
     * Check ConsumerIterator whether It has next value
     */
    public abstract boolean hasNext();

    /**
     * Used to check whether there are multiple topics to consume from
     */
    public boolean hasMultipleTopicsToConsume() {
        return false;
    }

    /**
     * Consume from multiple topics
     */
    public void consumeMultipleTopics(String sequenceName) {
    }
}
