/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.relay.module;

import org.wso2.carbon.relay.MessageBuilder;
import org.apache.axis2.AxisFault;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.axis2.builder.Builder;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class RelayConfiguration {
    private List<String> services = new ArrayList<String>();

    private Map<String, String> builders = new HashMap<String, String>();
    private Map<String, String> formatters = new HashMap<String, String>();

    private boolean includeHiddenServices = true;
    private MessageBuilder messageBuilder = new MessageBuilder();

    public List<String> getServices() {
        return services;
    }

    public boolean isIncludeHiddenServices() {
        return includeHiddenServices;
    }

    public void setIncludeHiddenServices(boolean includeHiddenServices) {
        this.includeHiddenServices = includeHiddenServices;
    }

    public void addService(String service) {
        services.add(service);
    }

    public Map<String, String> getBuilders() {
        return builders;
    }

    public void addBuilder(String contentType, String className) {
        builders.put(contentType, className);
    }

    public void addFormatter(String contentType, String className) {
        formatters.put(contentType, className);
    }

    public Map<String, String> getFormatters() {
        return formatters;
    }

    public MessageBuilder getMessageBuilder() {
        return messageBuilder;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

    public void init() throws AxisFault {
        for (String key : builders.keySet()) {
            Builder builder = MessageBuilder.createBuilder(builders.get(key));
            messageBuilder.addBuilder(key, builder);

            if (formatters.containsKey(key)) {
                MessageFormatter formatter = MessageBuilder.createFormatter(formatters.get(key));

                messageBuilder.addFormatter(key, formatter);
            }
        }
    }
}
