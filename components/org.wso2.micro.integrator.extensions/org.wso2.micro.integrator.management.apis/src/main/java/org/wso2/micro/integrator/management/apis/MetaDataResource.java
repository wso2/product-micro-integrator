/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfiguration;
import org.json.JSONObject;
import org.wso2.micro.core.util.CoreServerInitializerHolder;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.micro.integrator.management.apis.security.handler.SecurityUtils;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;

/**
 * This class serves metadata related to the server.
 */
public class MetaDataResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(MetaDataResource.class);

    // HTTP method types supported by the resource
    Set<String> methods;

    public MetaDataResource() {
        methods = new HashSet<>(1);
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_METHOD_PATCH);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext,
                          org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {

        if (messageContext.isDoingGET()) {
            populateMetaData(axis2MessageContext);
            axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
            return true;
        } else {
            String userName = (String) messageContext.getProperty(USERNAME_PROPERTY);
            try {
                if (SecurityUtils.canUserEdit(userName)) {
                    if (!JsonUtil.hasAJsonPayload(axis2MessageContext)) {
                        Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("JSON payload is missing."));
                        return true;
                    }
                    JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
                    if (payload.has(Constants.STATUS)) {
                        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime()
                                .availableProcessors());
                        Runnable runnable = null;
                        switch (payload.get(Constants.STATUS).getAsString()) {
                            case "shutdown":
                                runnable = this::shutdownServer;
                                Utils.setJsonPayLoad(axis2MessageContext,
                                        createJsonResponse("The server will start to shutdown."));
                                break;
                            case "shutdownGracefully":
                                runnable = this::gracefullyShutdownServer;
                                Utils.setJsonPayLoad(axis2MessageContext,
                                        createJsonResponse("The server will start to shutdown gracefully."));
                                break;
                            case "restart":
                                runnable = this::restart;
                                Utils.setJsonPayLoad(axis2MessageContext,
                                        createJsonResponse("The server will start to restart."));
                                break;
                            case "restartGracefully":
                                runnable = this::gracefullyRestartServer;
                                Utils.setJsonPayLoad(axis2MessageContext,
                                        createJsonResponse("The server will start to restart gracefully."));
                                break;
                            default:
                                LOG.error("Invalid server status received.");
                                Utils.setJsonPayLoad(axis2MessageContext,
                                        Utils.createJsonErrorObject("Invalid server status received."));
                        }
                        if (runnable != null) {
                            executorService.execute(runnable);
                        }
                    } else {
                        LOG.error("Invalid payload structure received.");
                        Utils.setJsonPayLoad(axis2MessageContext,
                                Utils.createJsonErrorObject("Invalid payload structure received."));
                    }
                } else {
                    Utils.sendForbiddenFaultResponse(axis2MessageContext);
                }
            } catch (IOException e) {
                LOG.error("Error when parsing JSON payload.", e);
                Utils.setJsonPayLoad(axis2MessageContext,
                                     Utils.createJsonErrorObject("Error when parsing JSON payload."));
            } catch (UserStoreException e) {
                LOG.error("Error occurred while retrieving the user data", e);
                Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("Error occurred while retrieving the user data"));
            }
        }
        return true;
    }

    /**
     * Create json response with message attribute.
     * @param message
     * @return
     */
    private JSONObject createJsonResponse(String message) {
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, message);
        return jsonResponse;
    }

    /**
     * Shutdown server forcefully.
     */
    private void shutdownServer() {
        CoreServerInitializerHolder coreServerInitializerHolder = CoreServerInitializerHolder.getInstance();
        coreServerInitializerHolder.shutdown();
    }

    /**
     * Shutdown server gracefully.
     */
    private void gracefullyShutdownServer() {
        CoreServerInitializerHolder coreServerInitializerHolder = CoreServerInitializerHolder.getInstance();
        coreServerInitializerHolder.shutdownGracefully();
    }

    /**
     * Restart server forcefully.
     */
    private void restart() {
        CoreServerInitializerHolder coreServerInitializerHolder = CoreServerInitializerHolder.getInstance();
        coreServerInitializerHolder.restart();
    }

    /**
     * Restart server gracefully.
     */
    private void gracefullyRestartServer() {
        CoreServerInitializerHolder coreServerInitializerHolder = CoreServerInitializerHolder.getInstance();
        coreServerInitializerHolder.restartGracefully();
    }

    /**
     * Populate metadata to the axis2message context.
     *
     * @param axis2MessageContext
     */
    private void populateMetaData(org.apache.axis2.context.MessageContext axis2MessageContext) {

        JSONObject jsonObject = new JSONObject();
        CarbonServerConfigurationService serverConfigurationService = MicroIntegratorBaseUtils.getServerConfiguration();
        jsonObject.put("carbonHome", System.getProperty("carbon.home"));
        jsonObject.put("javaHome", System.getProperty("java.home"));
        jsonObject.put("javaVersion", System.getProperty("java.version"));
        jsonObject.put("javaVendor", System.getProperty("java.vendor"));
        jsonObject.put("osName", System.getProperty("os.name"));
        jsonObject.put("osVersion", System.getProperty("os.version"));
        jsonObject.put("productName", serverConfigurationService.getFirstProperty("Name"));
        jsonObject.put("productVersion", serverConfigurationService.getFirstProperty("Version"));
        Utils.setJsonPayLoad(axis2MessageContext, jsonObject);
    }
}
