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

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.api.API;
import org.apache.synapse.aspects.flow.statistics.elasticsearch.ElasticMetadata;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingEvent;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingFlow;
import org.apache.synapse.aspects.flow.statistics.util.StatisticsConstants;
import org.apache.synapse.commons.CorrelationConstants;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.transport.netty.BridgeConstants;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.StatisticsPublisher;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticConstants;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticStatisticsPublisher;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.schema.MoesifDataSchema;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.schema.MoesifDataSchemaElement;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.util.MediationDataPublisherConstants;
import org.wso2.micro.integrator.initializer.ServiceBusInitializer;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public class MoesifStatisticsPublisher implements StatisticsPublisher {
    private static MoesifStatisticsPublisher instance = null;
    protected boolean enabled = false;
    private boolean analyticsDisabledForAPI;
    private boolean analyticsDisabledForSequences;
    private boolean analyticsDisabledForProxyServices;
    private boolean analyticsDisabledForEndpoints;
    private boolean analyticsDisabledForInboundEndpoints;
    private String analyticsDataPrefix;
    private final Log log = LogFactory.getLog(ElasticStatisticsPublisher.class);

    protected MoesifStatisticsPublisher() {
        loadConfigurations();
    }

    public static MoesifStatisticsPublisher GetInstance() {
        if (instance == null) {
            instance = new MoesifStatisticsPublisher();
        }
        return instance;
    }
    @Override
    public void process(PublishingFlow publishingFlow, int tenantId) {
        if (!enabled) {
            return;
        }
        publishingFlow.getEvents().forEach(event -> {
            if (event.getElasticMetadata() == null || !event.getElasticMetadata().isValid()) {
                return;
            }
            if (StatisticsConstants.FLOW_STATISTICS_API.equals(event.getComponentType())) {
                publishApiAnalytics(event);
            } else if (StatisticsConstants.FLOW_STATISTICS_SEQUENCE.equals(event.getComponentType())) {
                publishSequenceMediatorAnalytics(event);
            } else if (StatisticsConstants.FLOW_STATISTICS_ENDPOINT.equals(event.getComponentType())) {
                publishEndpointAnalytics(event);
            } else if (StatisticsConstants.FLOW_STATISTICS_INBOUNDENDPOINT.equals(event.getComponentType())) {
                publishInboundEndpointAnalytics(event);
            } else if (StatisticsConstants.FLOW_STATISTICS_PROXYSERVICE.equals(event.getComponentType())) {
                publishProxyServiceAnalytics(event);
            }
        });
    }

    private void loadConfigurations() {
        analyticsDisabledForAPI = !SynapsePropertiesLoader.getBooleanProperty(
                ElasticConstants.SynapseConfigKeys.API_ANALYTICS_ENABLED, true);
        analyticsDisabledForSequences = !SynapsePropertiesLoader.getBooleanProperty(
                ElasticConstants.SynapseConfigKeys.SEQUENCE_ANALYTICS_ENABLED, true);
        analyticsDisabledForProxyServices = !SynapsePropertiesLoader.getBooleanProperty(
                ElasticConstants.SynapseConfigKeys.PROXY_SERVICE_ANALYTICS_ENABLED, true);
        analyticsDisabledForEndpoints = !SynapsePropertiesLoader.getBooleanProperty(
                ElasticConstants.SynapseConfigKeys.ENDPOINT_ANALYTICS_ENABLED, true);
        analyticsDisabledForInboundEndpoints = !SynapsePropertiesLoader.getBooleanProperty(
                ElasticConstants.SynapseConfigKeys.INBOUND_ENDPOINT_ANALYTICS_ENABLED, true);
        analyticsDataPrefix = SynapsePropertiesLoader.getPropertyValue(
                ElasticConstants.SynapseConfigKeys.ELASTICSEARCH_PREFIX, ElasticConstants.ELASTIC_DEFAULT_PREFIX);
        enabled = SynapsePropertiesLoader.getBooleanProperty(
                ElasticConstants.SynapseConfigKeys.ELASTICSEARCH_ENABLED, false);
    }

    private MoesifDataSchemaElement generateAnalyticsMetadataObject(PublishingEvent event, Class<?> entityClass) {
        MoesifDataSchemaElement analyticPayload = new MoesifDataSchemaElement();
        ElasticMetadata metadata = event.getElasticMetadata();
        MoesifDataSchemaElement serverInfo = setupServerInfo();
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.SCHEMA_VERSION,
                ElasticConstants.SynapseConfigKeys.SCHEMA_VERSION);
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.SERVER_INFO, serverInfo);
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.ENTITY_TYPE, entityClass.getSimpleName());
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.ENTITY_CLASS_NAME, entityClass.getName());
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.FAULT_RESPONSE,
                metadata.isFaultResponse());
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.FAILURE, event.getFaultCount() != 0);
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.MESSAGE_ID,
                metadata.getMessageId());
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.CORRELATION_ID,
                metadata.getProperty(CorrelationConstants.CORRELATION_ID));
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.LATENCY, event.getDuration());

        MoesifDataSchemaElement metadataElement = new MoesifDataSchemaElement();
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.METADATA, metadataElement);
        if (metadata.getAnalyticsMetadata() == null) {
            return analyticPayload;
        }

        for (Map.Entry<String, Object> entry : metadata.getAnalyticsMetadata().entrySet()) {
            if (entry.getValue() == null) {
                continue; // Logstash fails at null
            }
            metadataElement.setAttribute(entry.getKey(), entry.getValue());
        }

        return analyticPayload;
    }
    private void publishApiAnalytics(PublishingEvent event) {
        if (analyticsDisabledForAPI) {
            return;
        }
        MoesifDataSchemaElement analyticsPayload = generateAnalyticsMetadataObject(event, API.class);
        ElasticMetadata metadata = event.getElasticMetadata();
        MoesifDataSchemaElement apiDetails = new MoesifDataSchemaElement();
        apiDetails.setAttribute(ElasticConstants.EnvelopDef.API,
                metadata.getProperty(RESTConstants.SYNAPSE_REST_API));
        apiDetails.setAttribute(ElasticConstants.EnvelopDef.SUB_REQUEST_PATH,
                metadata.getProperty(RESTConstants.REST_SUB_REQUEST_PATH));
        apiDetails.setAttribute(ElasticConstants.EnvelopDef.API_CONTEXT,
                metadata.getProperty(RESTConstants.REST_API_CONTEXT));
        apiDetails.setAttribute(ElasticConstants.EnvelopDef.METHOD,
                metadata.getProperty(RESTConstants.REST_METHOD));
        apiDetails.setAttribute(ElasticConstants.EnvelopDef.TRANSPORT,
                metadata.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.API_DETAILS, apiDetails);
        attachHttpProperties(analyticsPayload, metadata);
        JsonObject moesifPayload = generateMoesifPayload(MoesifConstants.API_ACTION_NAME, analyticsPayload, event);
        publishAnalytic(moesifPayload);
    }

    private void publishSequenceMediatorAnalytics(PublishingEvent event) {
        if (analyticsDisabledForSequences) {
            return;
        }
        SequenceMediator sequence = event.getElasticMetadata().getSequence(event.getComponentName());
        if (sequence == null) {
            return;
        }
        MoesifDataSchemaElement analyticsPayload = generateAnalyticsMetadataObject(event, SequenceMediator.class);
        MoesifDataSchemaElement sequenceDetails = new MoesifDataSchemaElement();
        sequenceDetails.setAttribute(ElasticConstants.EnvelopDef.SEQUENCE_NAME, sequence.getName());
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.SEQUENCE_DETAILS, sequenceDetails);
        JsonObject moesifPayload = generateMoesifPayload(MoesifConstants.SEQUENCE_ACTION_NAME, analyticsPayload, event);
        publishAnalytic(moesifPayload);
    }

    private void publishProxyServiceAnalytics(PublishingEvent event) {
        if (analyticsDisabledForProxyServices) {
            return;
        }

        MoesifDataSchemaElement analyticsPayload = generateAnalyticsMetadataObject(event, ProxyService.class);
        ElasticMetadata metadata = event.getElasticMetadata();
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_TRANSPORT,
                metadata.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_IS_DOING_REST,
                metadata.getProperty(SynapseConstants.IS_CLIENT_DOING_REST));
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_IS_DOING_SOAP11,
                metadata.getProperty(SynapseConstants.IS_CLIENT_DOING_SOAP11));

        MoesifDataSchemaElement proxyServiceDetails = new MoesifDataSchemaElement();
        proxyServiceDetails.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_NAME, event.getComponentName());
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_DETAILS, proxyServiceDetails);
        attachHttpProperties(analyticsPayload, metadata);
        JsonObject moesifPayload = generateMoesifPayload(MoesifConstants.PROXY_SERVICE_ACTION_NAME, analyticsPayload, event);
        publishAnalytic(moesifPayload);
    }

    private void publishEndpointAnalytics(PublishingEvent event) {
        if (analyticsDisabledForEndpoints) {
            return;
        }

        String placeholderName = "$name";
        String componentName = event.getComponentName();

        if (SynapseConstants.ANONYMOUS_ENDPOINT.equals(componentName) || placeholderName.equals(componentName)) {
            return;
        }

        MoesifDataSchemaElement analyticsPayload = generateAnalyticsMetadataObject(event, Endpoint.class);
        MoesifDataSchemaElement endpointDetails = new MoesifDataSchemaElement();
        endpointDetails.setAttribute(ElasticConstants.EnvelopDef.ENDPOINT_NAME, componentName);
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.ENDPOINT_DETAILS, endpointDetails);
        JsonObject moesifPayload = generateMoesifPayload(MoesifConstants.ENDPOINT_ACTION_NAME, analyticsPayload, event);
        publishAnalytic(moesifPayload);
    }

    private void publishInboundEndpointAnalytics(PublishingEvent event) {
        if (analyticsDisabledForInboundEndpoints) {
            return;
        }

        MoesifDataSchemaElement analyticsPayload = generateAnalyticsMetadataObject(event, InboundEndpoint.class);

        MoesifDataSchemaElement inboundEndpointDetails = new MoesifDataSchemaElement();
        inboundEndpointDetails.setAttribute(
                ElasticConstants.EnvelopDef.INBOUND_ENDPOINT_NAME, event.getComponentName());

        Object obj = event.getElasticMetadata().getProperty(SynapseConstants.STATISTICS_METADATA);
        if (obj instanceof Map<?, ?>) {
            //noinspection unchecked
            Map<String, Object> statisticsDetails = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : statisticsDetails.entrySet()) {
                inboundEndpointDetails.setAttribute(entry.getKey(), entry.getValue());
            }
        }

        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.INBOUND_ENDPOINT_DETAILS, inboundEndpointDetails);
        attachHttpProperties(analyticsPayload, event.getElasticMetadata());
        JsonObject moesifPayload = generateMoesifPayload(MoesifConstants.INBOUND_ENDPOINT_ACTION_NAME, analyticsPayload, event);
        publishAnalytic(moesifPayload);
    }

    private void attachHttpProperties(MoesifDataSchemaElement payload, ElasticMetadata metadata) {
        payload.setAttribute(ElasticConstants.EnvelopDef.REMOTE_HOST,
                metadata.getProperty(BridgeConstants.REMOTE_HOST));
        payload.setAttribute(ElasticConstants.EnvelopDef.CONTENT_TYPE,
                metadata.getProperty(BridgeConstants.CONTENT_TYPE_HEADER));
        payload.setAttribute(ElasticConstants.EnvelopDef.HTTP_METHOD,
                metadata.getProperty(BridgeConstants.HTTP_METHOD));
    }

    void publishAnalytic(JsonObject metadataPayload) {
        log.info(String.format("%s %s", analyticsDataPrefix, metadataPayload.toString()));
        String moesifReporterUrl = SynapsePropertiesLoader.getPropertyValue(
                MediationDataPublisherConstants.MOESIF_REPORTER_URL,
                MediationDataPublisherConstants.DEFAULT_MOESIF_REPORTER_URL);
        String moesifApplicationId = SynapsePropertiesLoader.getPropertyValue(
                MediationDataPublisherConstants.MOESIF_APPLICATION_ID, null);
        if (!moesifReporterUrl.endsWith("/")) {
            moesifReporterUrl = moesifReporterUrl + "/";
        }
        moesifReporterUrl = moesifReporterUrl + MoesifConstants.MOESIF_ACTIONS_ENDPOINT;
        HttpsURLConnection connection = null;

        try {
            connection = (HttpsURLConnection) new URL(moesifReporterUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.addRequestProperty("X-Moesif-Application-Id", moesifApplicationId);
            connection.setHostnameVerifier(getHostnameVerifier());

            // Convert JsonObject to String
            byte[] jsonBytes = metadataPayload.toString().getBytes(StandardCharsets.UTF_8);

            // Set content length
            connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));

            // Write JSON payload to output stream
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            // Get response code
            int responseCode = connection.getResponseCode();

            // Read response
            String responseBody = readResponse(connection);
            if (responseCode >= 200 && responseCode < 300) {
                // Success
                System.out.println("Successfully sent data to Moesif. Response: " + responseCode);
            } else {
                // Error
                System.err.println("Failed to send data to Moesif. Response code: " + responseCode);
                System.err.println("Response body: " + responseBody);
            }

        } catch (IOException e) {
            System.err.println("Error sending data to Moesif: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(HttpsURLConnection connection) throws IOException {
        BufferedReader reader = null;
        StringBuilder response = new StringBuilder();

        try {
            // Try to read from input stream (for 2xx responses)
            if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            } else {
                // Read from error stream (for 4xx, 5xx responses)
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            }

            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return response.toString();
    }

    private static HostnameVerifier getHostnameVerifier() {
        return (s, sslSession) -> true;
    }

    JsonObject generateMoesifPayload(String actionName, MoesifDataSchemaElement metadataPayload, PublishingEvent event) {
        MoesifDataSchemaElement request = setupRequest(event);
        MoesifDataSchema dataSchemaInst = new MoesifDataSchema(actionName, request, metadataPayload);
        return dataSchemaInst.getJsonObject();

    }

    public String getStartTime(long startTime) {
        if (startTime == 0) {
            return Instant.now().toString();
        }
        return Instant.ofEpochMilli(startTime).toString();
    }

    public MoesifDataSchemaElement setupServerInfo() {
        MoesifDataSchemaElement serverInfo = new MoesifDataSchemaElement();
        String publisherId = SynapsePropertiesLoader.getPropertyValue(
                ElasticConstants.SynapseConfigKeys.IDENTIFIER, null);
        serverInfo.setAttribute(ElasticConstants.ServerMetadataFieldDef.PUBLISHER_ID, publisherId);
        ServerConfigurationInformation config = ServiceBusInitializer.getConfigurationInformation();
        if (config != null) {
            serverInfo.setAttribute(ElasticConstants.ServerMetadataFieldDef.HOST_NAME, config.getHostName());
            serverInfo.setAttribute(ElasticConstants.ServerMetadataFieldDef.SERVER_NAME, config.getServerName());
            serverInfo.setAttribute(ElasticConstants.ServerMetadataFieldDef.IP_ADDRESS, config.getIpAddress());
        }
        return serverInfo;
    }

    public MoesifDataSchemaElement setupRequest(PublishingEvent event) {
        MoesifDataSchemaElement request = new MoesifDataSchemaElement();
        request.setAttribute(MoesifConstants.REQUEST_TIME, getStartTime(event.getStartTime()));
        return request;
    }
}
