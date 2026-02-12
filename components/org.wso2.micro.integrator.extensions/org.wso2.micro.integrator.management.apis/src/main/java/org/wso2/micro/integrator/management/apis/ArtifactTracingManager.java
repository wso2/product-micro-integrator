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
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.AbstractEndpoint;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.json.JSONObject;

import static org.wso2.micro.integrator.management.apis.Constants.NAME;

import java.io.IOException;

/**
 * Utility class for managing tracing operations across different artifact types.
 * Centralizes tracing logic to avoid code duplication across individual resource classes.
 */
public class ArtifactTracingManager {

    private static final Log log = LogFactory.getLog(ArtifactTracingManager.class);

    private static final String PROXY_SERVICE_NAME = "proxyServiceName";
    private static final String ENDPOINT_NAME = "endpointName";
    private static final String INBOUND_ENDPOINT_NAME = "inboundEndpointName";
    private static final String API_NAME = "apiName";
    private static final String SEQUENCE_NAME = "sequenceName";

    /**
     * Changes the tracing state of a proxy service.
     */
    public static JSONObject changeProxyServiceTracing(String performedBy, MessageContext messageContext,
                                                       org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.info("Initiating proxy service tracing change request by: " + performedBy);
        }
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        ProxyService proxyService = messageContext.getConfiguration().getProxyService(name);
        return ArtifactOperationHelper.handleAspectOperation(
                proxyService, name, "Specified proxy ('" + name + "') not found", PROXY_SERVICE_NAME,
                performedBy, Constants.AUDIT_LOG_TYPE_PROXY_SERVICE_TRACE, Constants.PROXY_SERVICES,
                ProxyService::getAspectConfiguration, axis2MessageContext, Utils::handleTracing);
    }

    /**
     * Changes the tracing state of an endpoint.
     * Kept explicit because a second null-check on {@code getDefinition()} is required.
     */
    public static JSONObject changeEndpointTracing(String performedBy, MessageContext messageContext,
                                                   org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        Endpoint endpoint = messageContext.getConfiguration().getEndpoint(name);

        if (endpoint == null) {
            log.warn("Endpoint tracing change failed: endpoint '" + name + "' not found");
            return Utils.createJsonError("Specified endpoint ('" + name + "') not found",
                    axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (((AbstractEndpoint) endpoint).getDefinition() == null) {
            return Utils.createJsonError("Tracing is not supported for this endpoint",
                    axis2MessageContext, Constants.BAD_REQUEST);
        }

        JSONObject info = new JSONObject();
        info.put(ENDPOINT_NAME, name);
        return Utils.handleTracing(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT_TRACE,
                Constants.ENDPOINTS, info,
                ((AbstractEndpoint) endpoint).getDefinition().getAspectConfiguration(),
                name, axis2MessageContext);
    }

    /**
     * Changes the tracing state of an inbound endpoint.
     */
    public static JSONObject changeInboundEndpointTracing(String performedBy, MessageContext messageContext,
                                                          org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        InboundEndpoint inboundEndpoint = messageContext.getConfiguration().getInboundEndpoint(name);
        return ArtifactOperationHelper.handleAspectOperation(
                inboundEndpoint, name, "Specified inbound endpoint ('" + name + "') not found",
                INBOUND_ENDPOINT_NAME,
                performedBy, Constants.AUDIT_LOG_TYPE_INBOUND_ENDPOINT_TRACE, Constants.INBOUND_ENDPOINTS,
                InboundEndpoint::getAspectConfiguration, axis2MessageContext, Utils::handleTracing);
    }

    /**
     * Changes the tracing state of an API.
     */
    public static JSONObject changeApiTracing(String performedBy, MessageContext messageContext,
                                              org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        API api = messageContext.getConfiguration().getAPI(name);
        return ArtifactOperationHelper.handleAspectOperation(
                api, name, "Specified API ('" + name + "') not found", API_NAME,
                performedBy, Constants.AUDIT_LOG_TYPE_API_TRACE, Constants.APIS,
                API::getAspectConfiguration, axis2MessageContext, Utils::handleTracing);
    }

    /**
     * Changes the tracing state of a sequence.
     */
    public static JSONObject changeSequenceTracing(String performedBy, MessageContext messageContext,
                                                   org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {
        String name = Utils.getJsonPayload(axis2MessageContext).get(NAME).getAsString();
        SequenceMediator sequence = messageContext.getConfiguration().getDefinedSequences().get(name);
        return ArtifactOperationHelper.handleAspectOperation(
                sequence, name, "Specified sequence ('" + name + "') not found", SEQUENCE_NAME,
                performedBy, Constants.AUDIT_LOG_TYPE_SEQUENCE_TRACE, Constants.SEQUENCES,
                SequenceMediator::getAspectConfiguration, axis2MessageContext, Utils::handleTracing);
    }
}
