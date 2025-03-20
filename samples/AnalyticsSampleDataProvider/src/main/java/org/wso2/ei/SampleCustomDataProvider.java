/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
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

package org.wso2.ei;

import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.producer.AnalyticsCustomDataProvider;

import java.util.HashMap;
import java.util.Map;

public class SampleCustomDataProvider implements AnalyticsCustomDataProvider {
    @Override
    public Map<String, Object> getCustomProperties(MessageContext messageContext) {
        Map<String, Object> properties = new HashMap<>();
        Mediator mediator = messageContext.getMainSequence();
        properties.put("mediatorName", mediator.getMediatorName());
        return properties;
    }
}
