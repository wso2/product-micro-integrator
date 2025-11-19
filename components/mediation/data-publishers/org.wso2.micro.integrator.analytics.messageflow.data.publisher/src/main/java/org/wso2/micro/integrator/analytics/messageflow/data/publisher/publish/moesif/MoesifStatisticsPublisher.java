/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
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

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif;

import com.google.gson.JsonObject;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.api.API;
import org.apache.synapse.aspects.flow.statistics.elasticsearch.ElasticMetadata;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingEvent;
import org.apache.synapse.commons.CorrelationConstants;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.rest.RESTConstants;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.AbstractStatisticsPublisher;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticConstants;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.schema.MoesifDataSchema;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.schema.MoesifDataSchemaElement;
import org.wso2.micro.integrator.initializer.ServiceBusInitializer;

import java.time.Instant;
import java.util.Map;

public class MoesifStatisticsPublisher extends AbstractStatisticsPublisher {
    private static MoesifStatisticsPublisher instance = null;
    private String analyticsDataPrefix;

    protected MoesifStatisticsPublisher() {
        super();
    }

    public static MoesifStatisticsPublisher GetInstance() {
        if (instance == null) {
            instance = new MoesifStatisticsPublisher();
        }
        return instance;
    }

    @Override
    protected void loadPublisherSpecificConfigurations() {
        analyticsDataPrefix = SynapsePropertiesLoader.getPropertyValue(
                ElasticConstants.SynapseConfigKeys.ANALYTICS_PREFIX, MoesifConstants.MOESIF_DEFAULT_PREFIX);
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
                continue;
            }
            metadataElement.setAttribute(entry.getKey(), entry.getValue());
        }
        return analyticPayload;
    }

    @Override
    protected void publishApiAnalytics(PublishingEvent event) {
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

    @Override
    protected void publishSequenceMediatorAnalytics(PublishingEvent event) {
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

    @Override
    protected void publishProxyServiceAnalytics(PublishingEvent event) {
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

    @Override
    protected void publishEndpointAnalytics(PublishingEvent event) {
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

    @Override
    protected void publishInboundEndpointAnalytics(PublishingEvent event) {
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

    protected void publishAnalytic(JsonObject metadataPayload) {
        log.info(String.format("%s %s", analyticsDataPrefix, metadataPayload.toString()));
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
