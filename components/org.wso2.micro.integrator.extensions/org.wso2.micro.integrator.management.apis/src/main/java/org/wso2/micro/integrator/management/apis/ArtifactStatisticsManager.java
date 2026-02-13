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

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.api.API;
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

    private static final Log log = LogFactory.getLog(ArtifactStatisticsManager.class);

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
                                                          org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Changing statistics state for proxy service");
        }
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String name = payload.get(NAME).getAsString();
        ProxyService proxyService = messageContext.getConfiguration().getProxyService(name);
        return ArtifactOperationHelper.handleAspectOperation(
                proxyService, name, "Specified proxy ('" + name + "') not found", PROXY_SERVICE_NAME,
                performedBy, Constants.AUDIT_LOG_TYPE_PROXY_SERVICE_STATISTICS, Constants.PROXY_SERVICES,
                ProxyService::getAspectConfiguration, axis2MessageContext, Utils::handleStatistics);
    }

    /**
     * Changes the statistics state of an endpoint.
     * Kept explicit because a second null-check on {@code getDefinition()} is required.
     */
    public static JSONObject changeEndpointStatistics(String performedBy, MessageContext messageContext,
                                                      org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String name = payload.get(NAME).getAsString();
        Endpoint endpoint = messageContext.getConfiguration().getEndpoint(name);

        if (endpoint == null) {
            log.warn("Endpoint not found: " + name);
            return Utils.createJsonError("Specified endpoint ('" + name + "') not found",
                    axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (!(endpoint instanceof AbstractEndpoint)) {
            return Utils.createJsonError("Statistics is not supported for this endpoint",
                    axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (((AbstractEndpoint) endpoint).getDefinition() == null) {
            log.warn("Statistics not supported for endpoint: " + name);
            return Utils.createJsonError("Statistics is not supported for this endpoint",
                    axis2MessageContext, Constants.BAD_REQUEST);
        }

        JSONObject info = new JSONObject();
        info.put(ENDPOINT_NAME, name);
        return Utils.handleStatistics(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT_STATISTICS,
                info, ((AbstractEndpoint) endpoint).getDefinition().getAspectConfiguration(),
                name, axis2MessageContext, payload);
    }

    /**
     * Changes the statistics state of an inbound endpoint.
     */
    public static JSONObject changeInboundEndpointStatistics(String performedBy, MessageContext messageContext,
                                                             org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String name = payload.get(NAME).getAsString();
        InboundEndpoint inboundEndpoint = messageContext.getConfiguration().getInboundEndpoint(name);
        return ArtifactOperationHelper.handleAspectOperation(
                inboundEndpoint, name, "Specified inbound endpoint ('" + name + "') not found",
                INBOUND_ENDPOINT_NAME,
                performedBy, Constants.AUDIT_LOG_TYPE_INBOUND_ENDPOINT_STATISTICS, Constants.INBOUND_ENDPOINTS,
                InboundEndpoint::getAspectConfiguration, axis2MessageContext, Utils::handleStatistics);
    }

    /**
     * Changes the statistics state of an API.
     */
    public static JSONObject changeApiStatistics(String performedBy, MessageContext messageContext,
                                                 org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String name = payload.get(NAME).getAsString();
        API api = messageContext.getConfiguration().getAPI(name);
        return ArtifactOperationHelper.handleAspectOperation(
                api, name, "Specified API ('" + name + "') not found", API_NAME,
                performedBy, Constants.AUDIT_LOG_TYPE_API_STATISTICS, Constants.APIS,
                API::getAspectConfiguration, axis2MessageContext, Utils::handleStatistics);
    }

    /**
     * Changes the statistics state of a sequence.
     */
    public static JSONObject changeSequenceStatistics(String performedBy, MessageContext messageContext,
                                                      org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String name = payload.get(NAME).getAsString();
        SequenceMediator sequence = messageContext.getConfiguration().getDefinedSequences().get(name);
        return ArtifactOperationHelper.handleAspectOperation(
                sequence, name, "Specified sequence ('" + name + "') not found", SEQUENCE_NAME,
                performedBy, Constants.AUDIT_LOG_TYPE_SEQUENCE_STATISTICS, Constants.SEQUENCES,
                SequenceMediator::getAspectConfiguration, axis2MessageContext, Utils::handleStatistics);
    }

    /**
     * Changes the statistics state of a template.
     * Kept explicit because it reads an additional {@code type} field and only supports
     * sequence templates.
     */
    public static JSONObject changeTemplateStatistics(String performedBy, MessageContext messageContext,
                                                      org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (!payload.has(TYPE) || payload.get(TYPE).isJsonNull()) {
            return Utils.createJsonError("Missing required field: type", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String name = payload.get(NAME).getAsString();
        String type = payload.get(TYPE).getAsString();

        JSONObject info = new JSONObject();
        info.put(TEMPLATE_NAME, name);
        info.put(TEMPLATE_TYPE, type);

        TemplateMediator sequenceTemplate = messageContext.getConfiguration().getSequenceTemplate(name);
        if (sequenceTemplate == null) {
            return Utils.createJsonError(
                    "Specified sequence template ('" + name + "') not found. "
                            + "Statistics is only supported for sequence templates.",
                    axis2MessageContext, Constants.BAD_REQUEST);
        }
        return Utils.handleStatistics(performedBy, Constants.AUDIT_LOG_TYPE_SEQUENCE_TEMPLATE_STATISTICS,
                info, sequenceTemplate.getAspectConfiguration(), name, axis2MessageContext, payload);
    }
}
