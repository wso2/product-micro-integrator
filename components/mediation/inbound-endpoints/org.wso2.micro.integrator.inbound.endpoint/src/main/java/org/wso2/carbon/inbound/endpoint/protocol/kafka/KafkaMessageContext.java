/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.inbound.endpoint.protocol.kafka;

public class KafkaMessageContext {

    private String connection;
    private String topic;
    private byte[] msg;

    public KafkaMessageContext(String host, int port, String topic, byte[] msg) {
        this.connection = host + ":" + port;
        this.topic = topic;
        this.msg = msg;
    }

    public KafkaMessageContext(String connection, String topic, byte[] msg) {
        this.connection = connection;
        this.topic = topic;
        this.msg = msg;
    }

    public String getTopic() {

        return topic;
    }

    public void setTopic(String topic) {

        this.topic = topic;
    }

    public byte[] getMsg() {

        return msg;
    }

    public void setMsg(byte[] msg) {

        this.msg = msg;
    }

    public String getConnection() {

        return connection;
    }

    public void setConnection(String connection) {

        this.connection = connection;
    }
}
