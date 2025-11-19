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

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.schema;

import com.google.gson.JsonObject;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.MoesifConstants;

import java.util.UUID;

public class MoesifDataSchema {
    private final String actionName;
    private final String transactionId;
    private final MoesifDataSchemaElement request;
    private final MoesifDataSchemaElement metadata;

    public MoesifDataSchema(String actionName, MoesifDataSchemaElement request, MoesifDataSchemaElement metadata) {
        this.actionName = actionName;
        this.transactionId = generateTransactionId();
        this.request = request;
        this.metadata = metadata;
    }

    public JsonObject getJsonObject() {
        JsonObject exportingAnalytic = new JsonObject();
        exportingAnalytic.addProperty(MoesifConstants.ACTION_NAME, actionName);
        exportingAnalytic.addProperty(MoesifConstants.TRANSACTION_ID, transactionId);
        exportingAnalytic.add(MoesifConstants.REQUEST, request.toJsonObject());
        exportingAnalytic.add(MoesifConstants.METADATA, metadata.toJsonObject());

        return exportingAnalytic;
    }

    private static String generateTransactionId() {
        return UUID.randomUUID().toString();
    }
}
