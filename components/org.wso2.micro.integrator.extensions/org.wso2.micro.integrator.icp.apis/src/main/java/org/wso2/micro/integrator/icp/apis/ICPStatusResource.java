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

package org.wso2.micro.integrator.icp.apis;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;
import org.wso2.micro.integrator.management.apis.ArtifactStatusManager;
import org.wso2.micro.integrator.management.apis.Constants;
import org.wso2.micro.integrator.management.apis.Utils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.ARTIFACT_TYPE;
import static org.wso2.micro.integrator.management.apis.Constants.ENDPOINT;
import static org.wso2.micro.integrator.management.apis.Constants.INBOUND_ENDPOINT;
import static org.wso2.micro.integrator.management.apis.Constants.MESSAGE_PROCESSOR;
import static org.wso2.micro.integrator.management.apis.Constants.NAME;
import static org.wso2.micro.integrator.management.apis.Constants.PROXY_SERVICE;
import static org.wso2.micro.integrator.management.apis.Constants.STATUS;
import static org.wso2.micro.integrator.management.apis.Constants.TASK;

/**
 * Resource class for handling artifact status changes via ICP API.
 * Supports status changes for proxy services, endpoints, inbound endpoints, and message processors.
 */
public class ICPStatusResource extends APIResource {

    private static final Log LOG = LogFactory.getLog(ICPStatusResource.class);

    public ICPStatusResource(String urlTemplate) {
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
        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        try {
            JsonObject payload = Utils.getJsonPayload(axis2MessageContext);

            if (!payload.has(ARTIFACT_TYPE) || !payload.has(NAME) || !payload.has(STATUS)) {
                Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Missing required fields: type, name, and status are required",
                        axis2MessageContext, Constants.BAD_REQUEST));
                return true;
            }

            String performedBy = Constants.ANONYMOUS_USER;
            if (messageContext.getProperty(Constants.USERNAME_PROPERTY) != null) {
                performedBy = messageContext.getProperty(Constants.USERNAME_PROPERTY).toString();
            }

            String artifactType = payload.get(ARTIFACT_TYPE).getAsString();
            JSONObject response = handleStatusChange(performedBy, messageContext, axis2MessageContext,
                                                      payload, artifactType);

            Utils.setJsonPayLoad(axis2MessageContext, response);

        } catch (IOException e) {
            LOG.error("Error while parsing JSON payload", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                Utils.createJsonError("Error parsing request payload", axis2MessageContext,
                    Constants.BAD_REQUEST));
        } catch (Exception e) {
            LOG.error("Error while processing status change request", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                Utils.createJsonError("Internal server error", axis2MessageContext,
                    Constants.INTERNAL_SERVER_ERROR));
        }

        return true;
    }

    /**
     * Routes the status change request to the appropriate handler based on artifact type.
     */
    private JSONObject handleStatusChange(String performedBy, MessageContext messageContext,
                                          org.apache.axis2.context.MessageContext axis2MessageContext,
                                          JsonObject payload, String artifactType) {

        SynapseConfiguration synapseConfiguration = messageContext.getConfiguration();

        switch (artifactType.toLowerCase()) {
            case PROXY_SERVICE:
                return ArtifactStatusManager.changeProxyServiceStatus(performedBy, messageContext,
                                                                       axis2MessageContext, payload);
            case ENDPOINT:
                return ArtifactStatusManager.changeEndpointStatus(performedBy, axis2MessageContext,
                                                                   synapseConfiguration, payload);
            case MESSAGE_PROCESSOR:
                return ArtifactStatusManager.changeMessageProcessorStatus(performedBy, messageContext,
                                                                           axis2MessageContext, payload);
            case INBOUND_ENDPOINT:
                return ArtifactStatusManager.changeInboundEndpointStatus(performedBy, messageContext,
                                                                          axis2MessageContext, payload);
            case TASK:
                return ArtifactStatusManager.changeTaskStatus(performedBy, messageContext,
                                                               axis2MessageContext, payload);
            default:
                return Utils.createJsonError("Unsupported artifact type: " + artifactType,
                                            axis2MessageContext, Constants.BAD_REQUEST);
        }
    }
}
