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
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.API;
import static org.wso2.micro.integrator.management.apis.Constants.ARTIFACT_TYPE;
import static org.wso2.micro.integrator.management.apis.Constants.ENDPOINT;
import static org.wso2.micro.integrator.management.apis.Constants.INBOUND_ENDPOINT;
import static org.wso2.micro.integrator.management.apis.Constants.NAME;
import static org.wso2.micro.integrator.management.apis.Constants.PROXY_SERVICE;
import static org.wso2.micro.integrator.management.apis.Constants.SEQUENCE;

/**
 * Resource class for handling artifact statistics changes via ICP API.
 * Supports statistics changes for proxy services, endpoints, inbound endpoints,
 * APIs, sequences, and templates.
 */
public class ICPStatisticsResource extends APIResource {

    private static final Log LOG = LogFactory.getLog(ICPStatisticsResource.class);
    private static final String TEMPLATE = "template";

    public ICPStatisticsResource(String urlTemplate) {
        super(urlTemplate);
    }

    @Override
    public Set<String> getMethods() {
        Set<String> methods = new HashSet<>();
        methods.add(Constants.HTTP_POST);
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext) {
        buildMessage(messageContext);
        org.apache.axis2.context.MessageContext axis2MessageContext = ((Axis2MessageContext) messageContext)
                .getAxis2MessageContext();

        try {
            JsonObject payload = Utils.getJsonPayload(axis2MessageContext);

            if (!payload.has(ARTIFACT_TYPE) || !payload.has(NAME)) {
                Utils.setJsonPayLoad(axis2MessageContext,
                        Utils.createJsonError("Missing required fields: type and name are required",
                                axis2MessageContext, Constants.BAD_REQUEST));
                return true;
            }

            String performedBy = Constants.ANONYMOUS_USER;
            if (messageContext.getProperty(Constants.USERNAME_PROPERTY) != null) {
                performedBy = messageContext.getProperty(Constants.USERNAME_PROPERTY).toString();
            }

            String artifactType = payload.get(ARTIFACT_TYPE).getAsString();
            JSONObject response = handleStatisticsChange(performedBy, messageContext, axis2MessageContext,
                    artifactType);

            Utils.setJsonPayLoad(axis2MessageContext, response);

        } catch (IOException e) {
            LOG.error("Error while parsing JSON payload", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Error parsing request payload", axis2MessageContext,
                            Constants.BAD_REQUEST));
        } catch (Exception e) {
            LOG.error("Error while processing statistics change request", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Internal server error", axis2MessageContext,
                            Constants.INTERNAL_SERVER_ERROR));
        }

        return true;
    }

    /**
     * Routes the statistics change request to the appropriate handler based on
     * artifact type.
     */
    private JSONObject handleStatisticsChange(String performedBy, MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext,
            String artifactType) {
        try {
            switch (artifactType.toLowerCase()) {
                case PROXY_SERVICE:
                    return ArtifactStatisticsManager.changeProxyServiceStatistics(performedBy, messageContext,
                            axis2MessageContext);
                case ENDPOINT:
                    return ArtifactStatisticsManager.changeEndpointStatistics(performedBy, messageContext,
                            axis2MessageContext);
                case INBOUND_ENDPOINT:
                    return ArtifactStatisticsManager.changeInboundEndpointStatistics(performedBy, messageContext,
                            axis2MessageContext);
                case API:
                    return ArtifactStatisticsManager.changeApiStatistics(performedBy, messageContext,
                            axis2MessageContext);
                case SEQUENCE:
                    return ArtifactStatisticsManager.changeSequenceStatistics(performedBy, messageContext,
                            axis2MessageContext);
                case TEMPLATE:
                    return ArtifactStatisticsManager.changeTemplateStatistics(performedBy, messageContext,
                            axis2MessageContext);
                default:
                    return Utils.createJsonError("Unsupported artifact type: " + artifactType,
                            axis2MessageContext, Constants.BAD_REQUEST);
            }
        } catch (IOException e) {
            LOG.error("Error while changing statistics for artifact type: " + artifactType, e);
            return Utils.createJsonError("Error processing statistics change request",
                    axis2MessageContext, Constants.INTERNAL_SERVER_ERROR);
        }
    }
}
