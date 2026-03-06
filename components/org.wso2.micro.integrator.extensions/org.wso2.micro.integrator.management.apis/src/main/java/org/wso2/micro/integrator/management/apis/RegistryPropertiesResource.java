/**
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 */

package org.wso2.micro.integrator.management.apis;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfiguration;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.management.apis.security.handler.SecurityUtils;
import org.wso2.micro.integrator.registry.MicroIntegratorRegistry;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.ANONYMOUS_USER;
import static org.wso2.micro.integrator.management.apis.Constants.AUDIT_LOG_ACTION_CREATED;
import static org.wso2.micro.integrator.management.apis.Constants.AUDIT_LOG_ACTION_DELETED;
import static org.wso2.micro.integrator.management.apis.Constants.AUDIT_LOG_ACTION_UPDATED;
import static org.wso2.micro.integrator.management.apis.Constants.AUDIT_LOG_TYPE_REGISTRY_RESOURCE_PROPERTIES;
import static org.wso2.micro.integrator.management.apis.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.management.apis.Constants.DEFAULT_MEDIA_TYPE;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_DELETE;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_GET;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_POST;
import static org.wso2.micro.integrator.management.apis.Constants.INTERNAL_SERVER_ERROR;
import static org.wso2.micro.integrator.management.apis.Constants.LIST;
import static org.wso2.micro.integrator.management.apis.Constants.NAME;
import static org.wso2.micro.integrator.management.apis.Constants.REGISTRY_PATH;
import static org.wso2.micro.integrator.management.apis.Constants.REGISTRY_PROPERTY_NAME;
import static org.wso2.micro.integrator.management.apis.Constants.REGISTRY_RESOURCE_NAME;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;
import static org.wso2.micro.integrator.management.apis.Constants.VALUE_KEY;
import static org.wso2.micro.integrator.management.apis.Utils.getRegistryPathPrefix;
import static org.wso2.micro.integrator.management.apis.Utils.getResourceName;
import static org.wso2.micro.integrator.management.apis.Utils.isRegistryExist;
import static org.wso2.micro.integrator.management.apis.Utils.isRegistryResourcePropertyExist;
import static org.wso2.micro.integrator.management.apis.Utils.validatePath;

/**
 * This class provides mechanisms to monitor registry properties.
 */
public class RegistryPropertiesResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(Utils.class);
    Set<String> methods;

    public RegistryPropertiesResource() {

        methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_POST);
        methods.add(Constants.HTTP_DELETE);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
            SynapseConfiguration synapseConfiguration) {

        String httpMethod = axis2MessageContext.getProperty(Constants.HTTP_METHOD_PROPERTY).toString();
        String registryPath = Utils.getQueryParameter(messageContext, REGISTRY_PATH);
        String validatedPath = validatePath(registryPath, axis2MessageContext, messageContext);

        if (StringUtils.isEmpty(validatedPath)) {
            axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
            return true;
        }

        switch (httpMethod) {
        case HTTP_GET:
            handleGet(messageContext, axis2MessageContext, registryPath, validatedPath);
            break;
        case HTTP_POST:
            try {
                axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
                String username = Utils.getStringPropertyFromMessageContext(messageContext, USERNAME_PROPERTY);
                if (username != null && SecurityUtils.isAdmin(messageContext, username)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Admin user '" + username + "' authorized to POST registry properties at: "
                                + registryPath);
                    }
                    handlePost(messageContext, axis2MessageContext, registryPath, validatedPath);
                } else {
                    Utils.sendForbiddenFaultResponse(axis2MessageContext);
                }
            } catch (UserStoreException e) {
                JSONObject jsonBody = Utils.createJsonError("Error occurs while retrieving the user data",
                        axis2MessageContext, INTERNAL_SERVER_ERROR);
                Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
            }
            break;
        case HTTP_DELETE:
            try {
                axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
                String username = Utils.getStringPropertyFromMessageContext(messageContext, USERNAME_PROPERTY);
                if (username != null && SecurityUtils.isAdmin(messageContext, username)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Admin user '" + username + "' authorized to DELETE registry properties at: "
                                + registryPath);
                    }
                    handleDelete(messageContext, axis2MessageContext, registryPath, validatedPath);
                } else {
                    Utils.sendForbiddenFaultResponse(axis2MessageContext);
                }
            } catch (UserStoreException e) {
                JSONObject jsonBody = Utils.createJsonError("Error occurs while retrieving the user data",
                        axis2MessageContext, INTERNAL_SERVER_ERROR);
                Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
            }
            break;
        default:
            JSONObject jsonBody = Utils.createJsonError(
                    "Unsupported HTTP method, " + httpMethod + ". Only GET, POST and DELETE methods are supported",
                    axis2MessageContext, BAD_REQUEST);
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
            break;
        }
        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
        return true;
    }

    /**
     * This method handles GET.
     *
     * @param messageContext      Synapse message context
     * @param axis2MessageContext AXIS2 message context
     */
    private void handleGet(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
            String registryPath, String validatedPath) {

        String propertyName = Utils.getQueryParameter(messageContext, NAME);
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        if (!isRegistryExist(validatedPath, messageContext)) {
            JSONObject jsonBody = Utils.createJsonError("Can not find the registry: " + registryPath,
                    axis2MessageContext, BAD_REQUEST);
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else if (Objects.nonNull(propertyName)) {
            populateRegistryProperty(axis2MessageContext, microIntegratorRegistry, validatedPath, propertyName);
        } else {
            populateRegistryProperties(axis2MessageContext, microIntegratorRegistry, validatedPath);
        }
    }

    /**
     * This method is used to fetch all the properties of a specified registry file.
     *
     * @param axis2MessageContext     AXIS2 message context
     * @param microIntegratorRegistry Micro integrator registry
     * @param path                    Registry path
     */
    private void populateRegistryProperties(org.apache.axis2.context.MessageContext axis2MessageContext,
            MicroIntegratorRegistry microIntegratorRegistry, String path) {

        JSONObject jsonBody;
        String registryPath = getRegistryPathPrefix(path);
        if (Objects.isNull(registryPath)) {
            jsonBody = Utils.createJsonError("Invalid registry path: " + path, axis2MessageContext, BAD_REQUEST);
        } else {
            Properties propertiesList = microIntegratorRegistry.getResourceProperties(registryPath);
            if (propertiesList != null) {
                jsonBody = Utils.createJSONList(propertiesList.size());
                for (Object property : propertiesList.keySet()) {
                    Object value = propertiesList.get(property);
                    JSONObject propertyObject = new JSONObject();
                    propertyObject.put(NAME, property);
                    propertyObject.put(VALUE_KEY, value);
                    jsonBody.getJSONArray(Constants.LIST).put(propertyObject);
                }
            } else {
                jsonBody = new JSONObject();
                jsonBody.put(LIST, "Error while fetching properties");
            }
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * This method is used to fetch all the properties of a specified registry file.
     *
     * @param axis2MessageContext     AXIS2 message context
     * @param microIntegratorRegistry Micro integrator registry
     * @param path                    Registry path
     */
    private void populateRegistryProperty(org.apache.axis2.context.MessageContext axis2MessageContext,
            MicroIntegratorRegistry microIntegratorRegistry, String path, String propertyName) {

        JSONObject jsonBody = new JSONObject();
        String registryPath = getRegistryPathPrefix(path);
        if (Objects.isNull(registryPath)) {
            jsonBody = Utils.createJsonError("Invalid registry path: " + path, axis2MessageContext, BAD_REQUEST);
        } else {
            Properties propertiesList = microIntegratorRegistry.getResourceProperties(registryPath);
            if (propertiesList != null) {
                String propertyValue = propertiesList.getProperty(propertyName);
                if (propertiesList.containsKey(propertyName)) {
                    jsonBody.put(propertyName, propertyValue);
                } else {
                    jsonBody = Utils.createJsonError("Property named " + propertyName + " does not exist",
                            axis2MessageContext, BAD_REQUEST);
                }
            } else {
                jsonBody = Utils.createJsonError("Error while fetching properties", axis2MessageContext,
                        INTERNAL_SERVER_ERROR);
            }
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * This method handles POST.
     *
     * @param messageContext      Synapse message context
     * @param axis2MessageContext AXIS2 message context
     */
    private void handlePost(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath, String validatedPath) {

        JSONObject jsonBody;
        String pathWithPrefix = getRegistryPathPrefix(validatedPath);
        if (Objects.nonNull(pathWithPrefix)) {
            if (isRegistryExist(validatedPath, messageContext) &&
                    isRegistryResourcePropertyExist(validatedPath, messageContext)) {
                jsonBody = postRegistryProperties(messageContext, axis2MessageContext, pathWithPrefix);
            } else if (isRegistryExist(validatedPath, messageContext)) {
                jsonBody = postNewRegistryProperties(messageContext, axis2MessageContext, pathWithPrefix);
            } else {
                jsonBody = postEmptyRegistryProperties(messageContext, axis2MessageContext, pathWithPrefix);
            }
        } else {
            jsonBody = Utils.createJsonError("Invalid registry path: " + registryPath, axis2MessageContext,
                    BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * This method handles adding and modifying registry properties to an existing property file.
     *
     * @param axis2MessageContext   AXIS2 message context
     * @param registryPath          Registry path
     * @return                      A JSON object with the final status
     */
    private JSONObject postRegistryProperties(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath) {

        String propertiesPayload = getPayload(axis2MessageContext);
        JSONObject jsonBody = new JSONObject();
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        Properties oldProperties = microIntegratorRegistry.getResourceProperties(registryPath);
        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);

        if (Objects.nonNull(propertiesPayload)) {
            Properties properties = getProperties(propertiesPayload);
            if (properties != null) {
                if (oldProperties != null) {
                    Properties newProperties = getNewProperties(oldProperties, properties);
                    try {
                        microIntegratorRegistry.updateProperties(registryPath, newProperties);
                        jsonBody.put("message", "Successfully added the registry property");
                        info.put(LIST, newProperties);
                        AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE_PROPERTIES,
                                AUDIT_LOG_ACTION_UPDATED, info);
                    } catch (URISyntaxException e) {
                        jsonBody = Utils.createJsonError("Error while writing properties", axis2MessageContext,
                                INTERNAL_SERVER_ERROR);
                    }
                } else {
                    jsonBody = Utils.createJsonError("Error while fetching properties from: " + registryPath,
                            axis2MessageContext, INTERNAL_SERVER_ERROR);
                }
            } else {
                jsonBody = Utils.createJsonError("Invalid payload for properties", axis2MessageContext, BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Error while fetching properties from payload", axis2MessageContext,
                    BAD_REQUEST);
        }
        return jsonBody;
    }

    /**
     * This method handles adding new property file to an existing registry.
     *
     * @param axis2MessageContext   AXIS2 message context
     * @param registryPath          Registry path
     * @return                      A JSON object with the final status
     */
    private JSONObject postNewRegistryProperties(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath) {

        String propertiesPayload = getPayload(axis2MessageContext);
        JSONObject jsonBody = new JSONObject();
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);

        if (Objects.nonNull(propertiesPayload)) {
            Properties properties = getProperties(propertiesPayload);
            if (properties != null) {
                try {
                    microIntegratorRegistry.updateProperties(registryPath, properties);
                    jsonBody.put("message", "Successfully added the registry property");
                    info.put(LIST, properties);
                    AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE_PROPERTIES,
                            AUDIT_LOG_ACTION_UPDATED, info);
                } catch (URISyntaxException e) {
                    jsonBody = Utils.createJsonError("Error while writing properties", axis2MessageContext,
                            INTERNAL_SERVER_ERROR);
                }
            } else {
                jsonBody = Utils.createJsonError("Invalid payload for properties", axis2MessageContext, BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Error while fetching properties from payload", axis2MessageContext,
                    BAD_REQUEST);
        }
        return jsonBody;
    }

    /**
     * This method handles adding new property file with an empty content file.
     *
     * @param axis2MessageContext   AXIS2 message context
     * @param registryPath          Registry path
     * @return                      A JSON object with the final status
     */
    private JSONObject postEmptyRegistryProperties(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath) {

        String propertiesPayload = getPayload(axis2MessageContext);
        JSONObject jsonBody = new JSONObject();
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);

        if (Objects.nonNull(propertiesPayload)) {
            Properties properties = getProperties(propertiesPayload);
            if (properties != null) {
                microIntegratorRegistry.addNewNonEmptyResource(registryPath, false, DEFAULT_MEDIA_TYPE, "", properties);
                jsonBody.put("message", "Successfully added the registry property");
                info.put(LIST, properties);
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE_PROPERTIES,
                        AUDIT_LOG_ACTION_CREATED, info);
            } else {
                jsonBody = Utils.createJsonError("Invalid payload for properties", axis2MessageContext, BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Error while fetching properties from payload", axis2MessageContext,
                    BAD_REQUEST);
        }
        return jsonBody;
    }

    /**
     * This method returns a string containing properties.
     *
     * @param axis2MessageContext   AXIS2 message context
     * @return                      A string containing file content
     */
    public static String getPayload(org.apache.axis2.context.MessageContext axis2MessageContext) {

        try {
            InputStream jsonStream = JsonUtil.getJsonPayload(axis2MessageContext);
            return IOUtils.toString(jsonStream);
        } catch (Exception e) {
            LOG.error("Error while fetching properties from payload", e);
            return null;
        }
    }

    /**
     * This method fetches properties to add/modify.
     *
     * @param content   Content of the properties file
     * @return          Fetched properties
     */
    private Properties getProperties(String content) {
        Properties properties = new Properties();
        try {
            JSONArray jsonArray = new JSONArray(content);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                properties.put(jsonObject.get(NAME), jsonObject.get(VALUE_KEY));
            }
        } catch (JSONException e) {
            LOG.error("Error while fetching properties", e);
            return null;
        }
        return properties;
    }

    /**
     * This method adds and modifies properties.
     *
     * @param oldProperties Existing properties
     * @param properties    Properties to be added
     * @return              New properties
     */
    private Properties getNewProperties(Properties oldProperties, Properties properties) {

        for (Object property : properties.keySet()) {
            Object value = properties.get(property);
            oldProperties.put(property, value);
        }
        return oldProperties;
    }

    /**
     * This method handles DELETE.
     *
     * @param messageContext      Synapse message context
     * @param axis2MessageContext AXIS2 message context
     */
    private void handleDelete(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath, String validatedPath) {

        String propertyName = Utils.getQueryParameter(messageContext, NAME);
        JSONObject jsonBody;
        if (Objects.nonNull(propertyName)) {
            String pathWithPrefix = getRegistryPathPrefix(validatedPath);
            if (Objects.nonNull(pathWithPrefix)) {
                if (isRegistryResourcePropertyExist(validatedPath, messageContext)) {
                    jsonBody = deleteRegistryProperty(messageContext, axis2MessageContext, pathWithPrefix,
                            propertyName);
                } else {
                    jsonBody = Utils.createJsonError("Cannot find a property file in the path: " + registryPath,
                            axis2MessageContext, BAD_REQUEST);
                }
            } else {
                jsonBody = Utils.createJsonError("Invalid registry path: " + registryPath, axis2MessageContext,
                        BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Property name not found in the request", axis2MessageContext,
                    BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * Deletes the registry property.
     *
     * @param registryPath  Registry path
     * @return              JSON object indicating the final status
     */
    private JSONObject deleteRegistryProperty(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath, String propertyName) {
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        Properties properties = microIntegratorRegistry.getResourceProperties(registryPath);
        JSONObject jsonBody = new JSONObject();
        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);

        if (properties != null) {
            if (properties.containsKey(propertyName)) {
                properties.remove(propertyName);
                try {
                    microIntegratorRegistry.updateProperties(registryPath, properties);
                    jsonBody.put("message", "Successfully deleted the registry property");
                    info.put(REGISTRY_PROPERTY_NAME, propertyName);
                    AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE_PROPERTIES,
                            AUDIT_LOG_ACTION_DELETED, info);
                } catch (URISyntaxException e) {
                    jsonBody = Utils.createJsonError("Error while writing updated properties", axis2MessageContext,
                            INTERNAL_SERVER_ERROR);
                }
            } else {
                jsonBody = Utils.createJsonError("Property named " + propertyName + " does not exist",
                        axis2MessageContext, BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Error while fetching properties from: " + registryPath,
                    axis2MessageContext, INTERNAL_SERVER_ERROR);
        }
        return jsonBody;
    }
}
