/*
 * Copyright (c) (2017-2022), WSO2 Inc. (http://www.wso2.com).
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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.schema;

import com.google.gson.JsonObject;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticConstants;
import org.wso2.micro.integrator.initializer.ServiceBusInitializer;

public class ElasticDataSchema {
    private static String hostname;
    private static String serverName;
    private static String ipAddress;
    private static String publisherId;
    private final String timestamp;
    private ElasticDataSchemaElement payload;

    public ElasticDataSchema(ElasticDataSchemaElement payload) {
        this.timestamp = payload.getStartTime();
        this.setPayload(payload);
    }

    public static void setPublisherId(String publisherId) {
        ElasticDataSchema.publisherId = publisherId;
    }

    public static void init() {
        publisherId = SynapsePropertiesLoader.getPropertyValue(
                ElasticConstants.SynapseConfigKeys.IDENTIFIER, hostname);
        ServerConfigurationInformation config = ServiceBusInitializer.getConfigurationInformation();
        if (config != null) {
            setupServerMetadata(config);
        }
    }

    public static void setupServerMetadata(ServerConfigurationInformation config) {
        hostname = config.getHostName();
        serverName = config.getServerName();
        ipAddress = config.getIpAddress();
    }

    public void setPayload(ElasticDataSchemaElement payload) {
        this.payload = payload;
    }

    public JsonObject getJsonObject() {
        JsonObject exportingAnalytic = new JsonObject();
        JsonObject serverMetadata = new JsonObject();
        serverMetadata.addProperty(ElasticConstants.ServerMetadataFieldDef.HOST_NAME, hostname);
        serverMetadata.addProperty(ElasticConstants.ServerMetadataFieldDef.SERVER_NAME, serverName);
        serverMetadata.addProperty(ElasticConstants.ServerMetadataFieldDef.IP_ADDRESS, ipAddress);
        serverMetadata.addProperty(ElasticConstants.ServerMetadataFieldDef.PUBLISHER_ID, publisherId);

        exportingAnalytic.add(ElasticConstants.EnvelopDef.SERVER_INFO, serverMetadata);
        exportingAnalytic.addProperty(ElasticConstants.EnvelopDef.TIMESTAMP, timestamp);
        exportingAnalytic.addProperty(ElasticConstants.EnvelopDef.SCHEMA_VERSION,
                ElasticConstants.SynapseConfigKeys.SCHEMA_VERSION);
        exportingAnalytic.add(ElasticConstants.EnvelopDef.PAYLOAD, payload.toJsonObject());

        return exportingAnalytic;
    }

    public String getJsonString() {
        return getJsonObject().toString();
    }
}
