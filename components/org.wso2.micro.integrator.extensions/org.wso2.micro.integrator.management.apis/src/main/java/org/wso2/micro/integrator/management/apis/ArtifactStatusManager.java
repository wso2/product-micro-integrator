/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
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

package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import org.apache.axis2.description.Parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.Startup;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.message.processor.MessageProcessor;
import org.apache.synapse.startup.quartz.StartUpController;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;

import java.util.List;

import static org.wso2.micro.integrator.management.apis.Constants.ACTIVE_STATUS;
import static org.wso2.micro.integrator.management.apis.Constants.INACTIVE_STATUS;
import static org.wso2.micro.integrator.management.apis.Constants.NAME;
import static org.wso2.micro.integrator.management.apis.Constants.STATUS;
import static org.wso2.micro.integrator.management.apis.Constants.TRIGGER_STATUS;

/**
 * Utility class for managing artifact status changes across different artifact types.
 * Centralizes status change logic to avoid code duplication.
 */
public class ArtifactStatusManager {

    private static final Log log = LogFactory.getLog(ArtifactStatusManager.class);

    private static final String PROXY_SERVICE_NAME = "proxyServiceName";
    private static final String ENDPOINT_NAME = "endpointName";
    private static final String MESSAGE_PROCESSOR_NAME = "messageProcessorName";
    private static final String INBOUND_ENDPOINT_NAME = "inboundEndpointName";
    private static final String TASK_NAME = "taskName";

    /**
     * Changes the state of a proxy service.
     */
    public static JSONObject changeProxyServiceStatus(String performedBy, MessageContext messageContext,
                                                      org.apache.axis2.context.MessageContext axis2MessageContext,
                                                      JsonObject payload) {
        if(log.isDebugEnabled()){
            log.debug("Attempting to change proxy service status. Performed by: " + performedBy);
        }
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (!payload.has(STATUS) || payload.get(STATUS).isJsonNull()) {
            return Utils.createJsonError("Missing required field: status", axis2MessageContext, Constants.BAD_REQUEST);
        }
        SynapseConfiguration synapseConfiguration = messageContext.getConfiguration();
        String name = payload.get(NAME).getAsString();
        String status = payload.get(STATUS).getAsString();
        ProxyService proxyService = synapseConfiguration.getProxyService(name);

        return ArtifactOperationHelper.handleStatusOperation(
                proxyService, "Proxy service could not be found", PROXY_SERVICE_NAME, name,
                axis2MessageContext, (ps, info) -> {
                    List pinnedServers = ps.getPinnedServers();
                    ServerConfigurationInformation serverConfigInformation =
                        getServerConfigInformation(synapseConfiguration);
                    JSONObject jsonResponse = new JSONObject();
                    if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                        if (pinnedServers.isEmpty() ||
                        (serverConfigInformation != null &&
                            pinnedServers.contains(serverConfigInformation.getServerName()))) {
                            ps.start(synapseConfiguration);
                            jsonResponse.put("Message", "Proxy service " + name + " started successfully");
                            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_PROXY_SERVICE,
                                    Constants.AUDIT_LOG_ACTION_ENABLE, info);
                        } else {
                            jsonResponse.put("Message", "Operation skipped: proxy service " + name +
                                    " is pinned to other servers");
                        }
                    } else if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                        if (pinnedServers.isEmpty() ||
                        (serverConfigInformation != null &&
                            pinnedServers.contains(serverConfigInformation.getServerName()))) {
                            ps.stop(synapseConfiguration);
                            jsonResponse.put("Message", "Proxy service " + name + " stopped successfully");
                            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_PROXY_SERVICE,
                                    Constants.AUDIT_LOG_ACTION_DISABLED, info);
                        } else {
                            jsonResponse.put("Message", "Operation skipped: proxy service " + name +
                                    " is pinned to other servers");
                        }
                    } else {
                        return Utils.createJsonError("Provided state is not valid", axis2MessageContext,
                                Constants.BAD_REQUEST);
                    }
                    return jsonResponse;
                });
    }

    /**
     * Changes the status of an endpoint.
     */
    public static JSONObject changeEndpointStatus(String performedBy,
                                                  org.apache.axis2.context.MessageContext axis2MessageContext,
                                                  SynapseConfiguration configuration, JsonObject payload) {
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (!payload.has(STATUS) || payload.get(STATUS).isJsonNull()) {
            return Utils.createJsonError("Missing required field: status", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String endpointName = payload.get(NAME).getAsString();
        String status = payload.get(STATUS).getAsString();
        Endpoint ep = configuration.getEndpoint(endpointName);

        return ArtifactOperationHelper.handleStatusOperation(
                ep, "Endpoint does not exist", ENDPOINT_NAME, endpointName,
                axis2MessageContext, (endpoint, info) -> {
                    JSONObject jsonResponse = new JSONObject();
                    if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                        endpoint.getContext().switchOff();
                        jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, endpointName + " is switched Off");
                        AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT,
                                Constants.AUDIT_LOG_ACTION_DISABLED, info);
                    } else if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                        endpoint.getContext().switchOn();
                        jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, endpointName + " is switched On");
                        AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_ENDPOINT,
                                Constants.AUDIT_LOG_ACTION_ENABLE, info);
                    } else {
                        return Utils.createJsonError("Provided state is not valid", axis2MessageContext,
                                Constants.BAD_REQUEST);
                    }
                    return jsonResponse;
                });
    }

    /**
     * Changes the status of a message processor.
     */
    public static JSONObject changeMessageProcessorStatus(String performedBy, MessageContext messageContext,
                                                          org.apache.axis2.context.MessageContext axis2MessageContext,
                                                          JsonObject payload) {
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (!payload.has(STATUS) || payload.get(STATUS).isJsonNull()) {
            return Utils.createJsonError("Missing required field: status", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String processorName = payload.get(NAME).getAsString();
        String status = payload.get(STATUS).getAsString();
        MessageProcessor messageProcessor =
                messageContext.getConfiguration().getMessageProcessors().get(processorName);

        return ArtifactOperationHelper.handleStatusOperation(
                messageProcessor, "Message processor does not exist", MESSAGE_PROCESSOR_NAME, processorName,
                axis2MessageContext, (mp, info) -> {
                    JSONObject jsonResponse = new JSONObject();
                    if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                        mp.deactivate();
                        jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, processorName + " : is deactivated");
                        AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_MESSAGE_PROCESSOR,
                                Constants.AUDIT_LOG_ACTION_DISABLED, info);
                    } else if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                        mp.activate();
                        jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, processorName + " : is activated");
                        AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_MESSAGE_PROCESSOR,
                                Constants.AUDIT_LOG_ACTION_ENABLE, info);
                    } else {
                        return Utils.createJsonError("Provided state is not valid", axis2MessageContext,
                                Constants.BAD_REQUEST);
                    }
                    return jsonResponse;
                });
    }

    /**
     * Changes the status of an inbound endpoint.
     */
    public static JSONObject changeInboundEndpointStatus(String performedBy, MessageContext messageContext,
                                                         org.apache.axis2.context.MessageContext axis2MessageContext,
                                                         JsonObject payload) {
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (!payload.has(STATUS) || payload.get(STATUS).isJsonNull()) {
            return Utils.createJsonError("Missing required field: status", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String name = payload.get(NAME).getAsString();
        String status = payload.get(STATUS).getAsString();
        InboundEndpoint inboundEndpoint = messageContext.getConfiguration().getInboundEndpoint(name);

        return ArtifactOperationHelper.handleStatusOperation(
                inboundEndpoint, "Inbound Endpoint could not be found", INBOUND_ENDPOINT_NAME, name,
                axis2MessageContext, (ie, info) -> {
                    JSONObject jsonResponse = new JSONObject();
                    if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                        org.apache.synapse.util.DynamicControlOperationResult result = ie.deactivate();
                        if (result.isSuccess()) {
                            jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, name + " : is deactivated");
                            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_INBOUND_ENDPOINT,
                                    Constants.AUDIT_LOG_ACTION_DISABLED, info);
                        } else {
                            return Utils.createJsonError(result.getMessage(), axis2MessageContext,
                                    Constants.INTERNAL_SERVER_ERROR);
                        }
                    } else if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                        org.apache.synapse.util.DynamicControlOperationResult result = ie.activate();
                        if (result.isSuccess()) {
                            jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, name + " : is activated");
                            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_INBOUND_ENDPOINT,
                                    Constants.AUDIT_LOG_ACTION_ENABLE, info);
                        } else {
                            return Utils.createJsonError(result.getMessage(), axis2MessageContext,
                                    Constants.INTERNAL_SERVER_ERROR);
                        }
                    } else {
                        return Utils.createJsonError("Provided state is not valid", axis2MessageContext,
                                Constants.BAD_REQUEST);
                    }
                    return jsonResponse;
                });
    }

    /**
     * Changes the status of a scheduled task.
     */
    public static JSONObject changeTaskStatus(String performedBy, MessageContext messageContext,
                                              org.apache.axis2.context.MessageContext axis2MessageContext,
                                              JsonObject payload) {
        if (!payload.has(NAME) || payload.get(NAME).isJsonNull()) {
            return Utils.createJsonError("Missing required field: name", axis2MessageContext, Constants.BAD_REQUEST);
        }
        if (!payload.has(STATUS) || payload.get(STATUS).isJsonNull()) {
            return Utils.createJsonError("Missing required field: status", axis2MessageContext, Constants.BAD_REQUEST);
        }
        String name = payload.get(NAME).getAsString();
        String status = payload.get(STATUS).getAsString();
        Startup task = messageContext.getConfiguration().getStartup(name);

        return ArtifactOperationHelper.handleStatusOperation(
                task, "Task could not be found", TASK_NAME, name,
                axis2MessageContext, (t, info) -> {
                    if (!(t instanceof StartUpController)) {
                        return Utils.createJsonError("Task could not be found",
                                axis2MessageContext, Constants.NOT_FOUND);
                    }
                    StartUpController controllerTask = (StartUpController) t;
                    JSONObject jsonResponse = new JSONObject();
                    if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                        org.apache.synapse.util.DynamicControlOperationResult result = controllerTask.deactivate();
                        if (result.isSuccess()) {
                            jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, name + " : is deactivated");
                            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_TASK,
                                    Constants.AUDIT_LOG_ACTION_DISABLED, info);
                        } else {
                            return Utils.createJsonError(result.getMessage(), axis2MessageContext,
                                    Constants.INTERNAL_SERVER_ERROR);
                        }
                    } else if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                        org.apache.synapse.util.DynamicControlOperationResult result = controllerTask.activate();
                        if (result.isSuccess()) {
                            jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, name + " : is activated");
                            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_TASK,
                                    Constants.AUDIT_LOG_ACTION_ENABLE, info);
                        } else {
                            return Utils.createJsonError(result.getMessage(), axis2MessageContext,
                                    Constants.INTERNAL_SERVER_ERROR);
                        }
                    } else if (TRIGGER_STATUS.equalsIgnoreCase(status)) {
                        org.apache.synapse.util.DynamicControlOperationResult result = controllerTask.trigger();
                        if (result.isSuccess()) {
                            jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, name + " : is triggered");
                            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_TASK,
                                    Constants.AUDIT_LOG_ACTION_TRIGGERED, info);
                        } else {
                            return Utils.createJsonError(result.getMessage(), axis2MessageContext,
                                    Constants.INTERNAL_SERVER_ERROR);
                        }
                    } else {
                        return Utils.createJsonError("Provided state is not valid", axis2MessageContext,
                                Constants.BAD_REQUEST);
                    }
                    return jsonResponse;
                });
    }

    /**
     * Helper method to get server configuration information.
     */
    private static ServerConfigurationInformation getServerConfigInformation(SynapseConfiguration synapseConfiguration) {
        Parameter parameter = synapseConfiguration.getAxisConfiguration()
                .getParameter(SynapseConstants.SYNAPSE_SERVER_CONFIG_INFO);
        if (parameter == null) {
            return null;
        }

        Object value = parameter.getValue();
        if (value instanceof ServerConfigurationInformation) {
            return (ServerConfigurationInformation) value;
        }
        return null;
    }
}
