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

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.schema.ElasticDataSchema;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.schema.ElasticDataSchemaElement;

import java.util.Map;

public class ElasticStatisticsPublisher implements StatisticsPublisher {
    private static ElasticStatisticsPublisher instance = null;
    private final Log log = LogFactory.getLog(ElasticStatisticsPublisher.class);
    protected boolean enabled = false;
    private boolean analyticsDisabledForAPI;
    private boolean analyticsDisabledForSequences;
    private boolean analyticsDisabledForProxyServices;
    private boolean analyticsDisabledForEndpoints;
    private boolean analyticsDisabledForInboundEndpoints;
    private String analyticsDataPrefix;

    protected ElasticStatisticsPublisher() {
        ElasticDataSchema.init();
        loadConfigurations();
    }

    public static ElasticStatisticsPublisher GetInstance() {
        if (instance == null) {
            instance = new ElasticStatisticsPublisher();
        }
        return instance;
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

    void publishAnalytic(ElasticDataSchemaElement payload) {
        ElasticDataSchema dataSchemaInst = new ElasticDataSchema(payload);
        log.info(String.format("%s %s", analyticsDataPrefix, dataSchemaInst.getJsonString()));
    }

    private void publishApiAnalytics(PublishingEvent event) {
        if (analyticsDisabledForAPI) {
            return;
        }

        ElasticDataSchemaElement analyticPayload = generateAnalyticsObject(event, API.class);

        ElasticMetadata metadata = event.getElasticMetadata();
        ElasticDataSchemaElement apiDetails = new ElasticDataSchemaElement();
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
        analyticPayload.setAttribute(ElasticConstants.EnvelopDef.API_DETAILS, apiDetails);
        attachHttpProperties(analyticPayload, metadata);

        publishAnalytic(analyticPayload);
    }

    private void publishSequenceMediatorAnalytics(PublishingEvent event) {
        if (analyticsDisabledForSequences) {
            return;
        }

        SequenceMediator sequence = event.getElasticMetadata().getSequence(event.getComponentName());

        if (sequence == null) {
            return;
        }

        ElasticDataSchemaElement analyticsPayload = generateAnalyticsObject(event, SequenceMediator.class);
        ElasticDataSchemaElement sequenceDetails = new ElasticDataSchemaElement();
        sequenceDetails.setAttribute(ElasticConstants.EnvelopDef.SEQUENCE_NAME, sequence.getName());
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.SEQUENCE_DETAILS, sequenceDetails);
        publishAnalytic(analyticsPayload);
    }

    private void publishProxyServiceAnalytics(PublishingEvent event) {
        if (analyticsDisabledForProxyServices) {
            return;
        }

        ElasticDataSchemaElement analyticsPayload = generateAnalyticsObject(event, ProxyService.class);
        ElasticMetadata metadata = event.getElasticMetadata();
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_TRANSPORT,
                metadata.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_IS_DOING_REST,
                metadata.getProperty(SynapseConstants.IS_CLIENT_DOING_REST));
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_IS_DOING_SOAP11,
                metadata.getProperty(SynapseConstants.IS_CLIENT_DOING_SOAP11));

        ElasticDataSchemaElement proxyServiceDetails = new ElasticDataSchemaElement();
        proxyServiceDetails.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_NAME, event.getComponentName());
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.PROXY_SERVICE_DETAILS, proxyServiceDetails);
        attachHttpProperties(analyticsPayload, metadata);

        publishAnalytic(analyticsPayload);
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

        ElasticDataSchemaElement analyticsPayload = generateAnalyticsObject(event, Endpoint.class);
        ElasticDataSchemaElement endpointDetails = new ElasticDataSchemaElement();
        endpointDetails.setAttribute(ElasticConstants.EnvelopDef.ENDPOINT_NAME, componentName);
        analyticsPayload.setAttribute(ElasticConstants.EnvelopDef.ENDPOINT_DETAILS, endpointDetails);

        publishAnalytic(analyticsPayload);
    }

    private void publishInboundEndpointAnalytics(PublishingEvent event) {
        if (analyticsDisabledForInboundEndpoints) {
            return;
        }

        ElasticDataSchemaElement analyticsPayload = generateAnalyticsObject(event, InboundEndpoint.class);

        ElasticDataSchemaElement inboundEndpointDetails = new ElasticDataSchemaElement();
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

        analyticsPayload.setAttribute(
                ElasticConstants.EnvelopDef.INBOUND_ENDPOINT_DETAILS, inboundEndpointDetails);
        attachHttpProperties(analyticsPayload, event.getElasticMetadata());

        publishAnalytic(analyticsPayload);
    }

    private ElasticDataSchemaElement generateAnalyticsObject(PublishingEvent event, Class<?> entityClass) {
        ElasticDataSchemaElement analyticPayload = new ElasticDataSchemaElement();
        ElasticMetadata metadata = event.getElasticMetadata();
        analyticPayload.setStartTime(event.getStartTime());
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

        ElasticDataSchemaElement metadataElement = new ElasticDataSchemaElement();
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

    private void attachHttpProperties(ElasticDataSchemaElement payload, ElasticMetadata metadata) {
        payload.setAttribute(ElasticConstants.EnvelopDef.REMOTE_HOST,
                metadata.getProperty(BridgeConstants.REMOTE_HOST));
        payload.setAttribute(ElasticConstants.EnvelopDef.CONTENT_TYPE,
                metadata.getProperty(BridgeConstants.CONTENT_TYPE_HEADER));
        payload.setAttribute(ElasticConstants.EnvelopDef.HTTP_METHOD,
                metadata.getProperty(BridgeConstants.HTTP_METHOD));
    }

}
