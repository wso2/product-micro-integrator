/*
 * Copyright (c) 2022, WSO2 LLC (http://www.wso2.com).
 *
 * WSO2 LLC licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.transport.passthru.config.PassThroughCorrelationConfigDataHolder;
import org.json.JSONObject;
import org.wso2.micro.integrator.management.apis.security.handler.SecurityUtils;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;

/**
 * This resource will handle requests coming to configs/.
 */
public class ConfigsResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(ConfigsResource.class);

    private static final String CONFIG_NAME = "configName";
    private static final String CONFIGS = "configs";
    private static final String CORRELATION = "correlation";
    private static final String ENABLED = "enabled";
    Set<String> methods;

    public ConfigsResource() {
        methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_PUT);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext,
                          org.apache.axis2.context.MessageContext axis2MessageContext, SynapseConfiguration synapseConfiguration) {
        String httpMethod = axis2MessageContext.getProperty(Constants.HTTP_METHOD_PROPERTY) != null ?
                axis2MessageContext.getProperty(Constants.HTTP_METHOD_PROPERTY).toString() :
                "method undefined";
        if (LOG.isDebugEnabled()) {
            LOG.debug("Handling" + httpMethod + "request");
        }
        JSONObject response;
        String userName = (String) messageContext.getProperty(USERNAME_PROPERTY);
        try {
            switch (httpMethod) {
                case Constants.HTTP_GET: {
                    response = handleGet(messageContext);
                    break;
                }
                case Constants.HTTP_PUT: {
                    if (SecurityUtils.canUserEdit(userName)) {
                        response = handlePut(axis2MessageContext);
                    } else {
                        Utils.sendForbiddenFaultResponse(axis2MessageContext);
                        response = Utils.createJsonError("", axis2MessageContext, Constants.FORBIDDEN);
                    }
                    break;
                }
                default: {
                    response = Utils.createJsonError(
                            "Unsupported HTTP method, " + httpMethod + ". Only GET and " + "PUT methods are supported",
                            axis2MessageContext, Constants.BAD_REQUEST);
                    break;
                }
            }
        } catch (ConfigNotFoundException e) {
            response = Utils.createJsonError(" Error : ", e, axis2MessageContext, Constants.BAD_REQUEST);
        } catch (IOException e) {
            LOG.error("Error when parsing JSON payload", e);
            response = Utils.createJsonErrorObject("Error while parsing JSON payload");
        } catch (UserStoreException e) {
            LOG.error("Error occurred while retrieving the user data", e);
            response = Utils.createJsonError("Error occurred while retrieving the user data",
                    axis2MessageContext, Constants.FORBIDDEN);
        }
        Utils.setJsonPayLoad(axis2MessageContext, response);
        return true;
    }

    private JSONObject handlePut(org.apache.axis2.context.MessageContext axis2MessageContext)
            throws ConfigNotFoundException, IOException {
        if (!JsonUtil.hasAJsonPayload(axis2MessageContext)) {
            return Utils.createJsonErrorObject("JSON payload is missing");
        }
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        String configName;
        if (!payload.has(CONFIG_NAME)) {
            throw new ConfigNotFoundException("Missing Required Field: " + CONFIG_NAME);
        }
        configName = payload.get(CONFIG_NAME).getAsString();
        switch (configName) {
            case CORRELATION: {
                JsonObject configs;
                if (!payload.has(CONFIGS)) {
                    throw new ConfigNotFoundException("Missing Required Field: " + CONFIGS);
                }
                configs = payload.get(CONFIGS).getAsJsonObject();
                if (!configs.has(ENABLED)) {
                    throw new ConfigNotFoundException("Missing Required Field: " + ENABLED);
                }
                boolean enabled = Boolean.parseBoolean(configs.get(ENABLED).getAsString());
                PassThroughCorrelationConfigDataHolder.setEnable(enabled);
                JSONObject response = new JSONObject();
                response.put(Constants.MESSAGE, "Successfully Updated Correlation Logs Status");
                return response;
            }
            default: {
                throw new ConfigNotFoundException(configName + " configName not found");
            }
        }
    }

    private JSONObject handleGet(MessageContext messageContext) throws ConfigNotFoundException {
        String configName = Utils.getQueryParameter(messageContext, CONFIG_NAME);
        if (configName == null) {
            throw new ConfigNotFoundException("Missing Required Query Parameter : configName");
        }
        JSONObject response;
        switch (configName) {
            case CORRELATION: {
                JSONObject configs = new JSONObject();
                Boolean correlationEnabled = PassThroughCorrelationConfigDataHolder.isEnable();
                configs.put(ENABLED, correlationEnabled);
                response = new JSONObject();
                response.put(CONFIG_NAME, configName);
                response.put(CONFIGS, configs);
                break;
            }
            default: {
                throw new ConfigNotFoundException(configName + " configName not found");
            }
        }
        return response;
    }
}
