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

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class PublisherDataSchemaElement {
    private final Map<String, Object> attributes = new HashMap<>();

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    protected <T> T getAttribute(String key, T defaultValue) {
        if (!attributes.containsKey(key)) {
            return defaultValue;
        }

        try {
            return (T) attributes.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}
