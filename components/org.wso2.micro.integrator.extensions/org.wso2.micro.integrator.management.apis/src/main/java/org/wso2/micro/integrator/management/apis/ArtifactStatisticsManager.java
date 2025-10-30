/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.management.apis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.api.API;
import org.apache.synapse.aspects.AspectConfiguration;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.AbstractEndpoint;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.mediators.template.TemplateMediator;
import org.json.JSONObject;

import static org.wso2.micro.integrator.management.apis.Constants.NAME;
import static org.wso2.micro.integrator.management.apis.Constants.TYPE;

import java.io.IOException;

/**
 * Utility class for managing statistics operations across different artifact types.
 * Centralizes statistics logic to avoid code duplication across individual resource classes.
 */
public class ArtifactStatisticsManager {

    private static final Log LOG = LogFactory.getLog(ArtifactStatisticsManager.class);
    private static final String PROXY_SERVICE_NAME = "proxyServiceName";
    private static final String ENDPOINT_NAME = "endpointName";
    private static final String INBOUND_ENDPOINT_NAME = "inboundEndpointName";
    private static final String API_NAME = "apiName";
    private static final String SEQUENCE_NAME = "sequenceName";
    private static final String TEMPLATE_NAME = "templateName";
    private static final String TEMPLATE_TYPE = "templateType";

    /**
     * Changes the statistics state of a proxy service.
     */
    public static JSONObject changeProxyServiceStatistics(String performedBy, MessageContext messageContext,
                                                          org.apache.axis2.context.MessageContext axis2MessageContext) throws IOException {
        SynapseConfiguration configuration = messageContext.getConfiguration();
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        ProxyService proxyService = configuration.getProxyService(name);

        if (proxyService == null) {
            return Utils.createJsonError("Specified proxy ('" + name + "') not found", 
                    axis2MessageContext, Constants.BAD_REQUEST);
        }

        JSONObject info = new JSONObject();
        info.put(PROXY_SERVICE_NAME, name);

        return Utils.handleStatistics(performedBy, Constants.AUDIT_LOG_TYPE_PROXY_SERVICE_STATISTICS,
                Constants.PROXY_SERVICES, info, proxyService.getAspectConfiguration(),
                name, axis2MessageContext);
    }

    /**
     * Changes the statistics state of an endpoint.
     */
    public static JSONObject changeEndpointStatistics(String performedBy, MessageContext messageContext,
                                                      org.apache.axis2.context.MessageContext axis2MessageContext) throws IOException {
        SynapseConfiguration configuration = messageContext.getConfiguration();
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        Endpoint endpoint = configuration.getEndpoint(name);

        if (endpoint == null) {
            return Utils.createJsonError("Specified endpoint ('" + name + "') not found", 
                    axis2MessageContext, Constants.BAD_REQUEST);
        }

        if (((AbstractEndpoint) endpoint).getDefinition() != null) {
            AspectConfiguration aspectConfiguration = ((AbstractEndpoint) endpoint).getDefinition()
                    .getAspectConfiguration();
            JSONObject info = new JSONObject();
            info.put(ENDPOINT_NAME, name);

            return Utils.handleStatistics(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT_STATISTICS,
                    Constants.ENDPOINTS, info, aspectConfiguration, name, axis2MessageContext);
        } else {
            return Utils.createJsonError("Statistics is not supported for this endpoint", 
                    axis2MessageContext, Constants.BAD_REQUEST);
        }
    }

    /**
     * Changes the statistics state of an inbound endpoint.
     */
    public static JSONObject changeInboundEndpointStatistics(String performedBy, MessageContext messageContext,
                                                             org.apache.axis2.context.MessageContext axis2MessageContext) throws IOException {
        SynapseConfiguration configuration = messageContext.getConfiguration();
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        InboundEndpoint inboundEndpoint = configuration.getInboundEndpoint(name);

        if (inboundEndpoint == null) {
            return Utils.createJsonError("Specified inbound endpoint ('" + name + "') not found",
                    axis2MessageContext, Constants.BAD_REQUEST);
        }

        JSONObject info = new JSONObject();
        info.put(INBOUND_ENDPOINT_NAME, name);

        return Utils.handleStatistics(performedBy, Constants.AUDIT_LOG_TYPE_INBOUND_ENDPOINT_STATISTICS,
                Constants.INBOUND_ENDPOINTS, info, inboundEndpoint.getAspectConfiguration(), 
                name, axis2MessageContext);
    }

    /**
     * Changes the statistics state of an API.
     */
    public static JSONObject changeApiStatistics(String performedBy, MessageContext messageContext,
                                                 org.apache.axis2.context.MessageContext axis2MessageContext) throws IOException {
        SynapseConfiguration configuration = messageContext.getConfiguration();
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        API api = configuration.getAPI(name);

        if (api == null) {
            return Utils.createJsonError("Specified API ('" + name + "') not found", 
                    axis2MessageContext, Constants.BAD_REQUEST);
        }

        JSONObject info = new JSONObject();
        info.put(API_NAME, name);

        return Utils.handleStatistics(performedBy, Constants.AUDIT_LOG_TYPE_API_STATISTICS, 
                Constants.APIS, info, api.getAspectConfiguration(), name, axis2MessageContext);
    }

    /**
     * Changes the statistics state of a sequence.
     */
    public static JSONObject changeSequenceStatistics(String performedBy, MessageContext messageContext,
                                                      org.apache.axis2.context.MessageContext axis2MessageContext) throws IOException {
        SynapseConfiguration configuration = messageContext.getConfiguration();
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        SequenceMediator sequence = configuration.getDefinedSequences().get(name);

        if (sequence == null) {
            return Utils.createJsonError("Specified sequence ('" + name + "') not found", 
                    axis2MessageContext, Constants.BAD_REQUEST);
        }

        JSONObject info = new JSONObject();
        info.put(SEQUENCE_NAME, name);

        return Utils.handleStatistics(performedBy, Constants.AUDIT_LOG_TYPE_SEQUENCE_STATISTICS,
                Constants.SEQUENCES, info, sequence.getAspectConfiguration(), name, axis2MessageContext);
    }

    /**
     * Changes the statistics state of a template.
     */
    public static JSONObject changeTemplateStatistics(String performedBy, MessageContext messageContext,
                                                      org.apache.axis2.context.MessageContext axis2MessageContext) throws IOException {
        SynapseConfiguration configuration = messageContext.getConfiguration();
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        String type = Utils.getJsonPayload(axis2MessageContext).get(TYPE).getAsString();

        if (type == null || type.isEmpty()) {
            return Utils.createJsonError("Template type is required", 
                    axis2MessageContext, Constants.BAD_REQUEST);
        }

        JSONObject info = new JSONObject();
        info.put(TEMPLATE_NAME, name);
        info.put(TEMPLATE_TYPE, type);

        // Only sequence templates support statistics
        TemplateMediator sequenceTemplate = null;
        if (!configuration.getSequenceTemplates().isEmpty()){
            sequenceTemplate = configuration.getSequenceTemplate(name);
            return Utils.handleStatistics(performedBy, Constants.AUDIT_LOG_TYPE_SEQUENCE_TEMPLATE_STATISTICS,
                    Constants.TEMPLATES, info, sequenceTemplate.getAspectConfiguration(),
                    name, axis2MessageContext);
        } else {
            return Utils.createJsonError("Statistics is only supported for sequence templates", 
                    axis2MessageContext, Constants.BAD_REQUEST);
        }
    }
}
