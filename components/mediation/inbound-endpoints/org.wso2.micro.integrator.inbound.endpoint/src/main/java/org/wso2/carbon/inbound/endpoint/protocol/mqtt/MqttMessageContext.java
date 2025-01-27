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
package org.wso2.carbon.inbound.endpoint.protocol.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttMessageContext {

    private MqttMessage mqttMessage;
    private String topic;
    private String host;
    private String port;

    public MqttMessageContext(MqttMessage mqttMessage, String topic, String host, String port) {

        this.mqttMessage = mqttMessage;
        this.topic = topic;
        this.host = host;
        this.port = port;
    }

    public MqttMessage getMqttMessage() {

        return mqttMessage;
    }

    public String getTopic() {

        return topic;
    }

    public String getHost() {

        return host;
    }

    public String getPort() {

        return port;
    }
}
