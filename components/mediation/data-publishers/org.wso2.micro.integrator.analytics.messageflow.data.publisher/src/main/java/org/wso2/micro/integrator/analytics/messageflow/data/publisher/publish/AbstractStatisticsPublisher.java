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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.aspects.flow.statistics.elasticsearch.ElasticMetadata;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingEvent;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingFlow;
import org.apache.synapse.aspects.flow.statistics.util.StatisticsConstants;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.transport.netty.BridgeConstants;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticConstants;

public abstract class AbstractStatisticsPublisher implements StatisticsPublisher {
    protected boolean analyticsDisabledForAPI;
    protected boolean analyticsDisabledForSequences;
    protected boolean analyticsDisabledForProxyServices;
    protected boolean analyticsDisabledForEndpoints;
    protected boolean analyticsDisabledForInboundEndpoints;
    protected boolean enabled = false;
    protected final Log log;

    protected AbstractStatisticsPublisher() {
        this.log = LogFactory.getLog(this.getClass());
        loadConfigurations();
        loadPublisherSpecificConfigurations();
        log.info("Initializing " + this.getClass().getSimpleName() + " statistics publisher");
    }

    @Override
    public final void process(PublishingFlow publishingFlow, int tenantId) {
        if (!enabled) {
            if (log.isDebugEnabled()) {
                log.debug("Publisher " + this.getClass().getSimpleName() + " is not enabled.");
            }
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

    protected void loadConfigurations() {
        if (log.isDebugEnabled()) {
            log.debug("Loading analytics configurations");
        }
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
        enabled = SynapsePropertiesLoader.getBooleanProperty(
                ElasticConstants.SynapseConfigKeys.ANALYTICS_ENABLED, false);
        if (log.isDebugEnabled()) {
            log.debug("Analytics enabled: " + enabled + ", API: " + !analyticsDisabledForAPI +
                    ", Sequences: " + !analyticsDisabledForSequences + ", Proxy: " + !analyticsDisabledForProxyServices +
                    ", Endpoints: " + !analyticsDisabledForEndpoints + ", Inbound: " + !analyticsDisabledForInboundEndpoints);
        }
    }

    protected void attachHttpProperties(PublisherDataSchemaElement payload, ElasticMetadata metadata) {
        payload.setAttribute(ElasticConstants.EnvelopDef.REMOTE_HOST,
                metadata.getProperty(BridgeConstants.REMOTE_HOST));
        payload.setAttribute(ElasticConstants.EnvelopDef.CONTENT_TYPE,
                metadata.getProperty(BridgeConstants.CONTENT_TYPE_HEADER));
        payload.setAttribute(ElasticConstants.EnvelopDef.HTTP_METHOD,
                metadata.getProperty(BridgeConstants.HTTP_METHOD));
    }

    protected abstract void loadPublisherSpecificConfigurations();
    protected abstract void publishApiAnalytics(PublishingEvent event);
    protected abstract void publishSequenceMediatorAnalytics(PublishingEvent event);
    protected abstract void publishEndpointAnalytics(PublishingEvent event);
    protected abstract void publishInboundEndpointAnalytics(PublishingEvent event);
    protected abstract void publishProxyServiceAnalytics(PublishingEvent event);
}
