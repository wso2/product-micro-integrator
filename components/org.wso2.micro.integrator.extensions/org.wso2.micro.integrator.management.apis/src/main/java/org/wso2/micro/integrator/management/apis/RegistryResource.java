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

import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.micro.integrator.registry.MicroIntegratorRegistry;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.management.apis.Constants.EXPAND_PARAM;
import static org.wso2.micro.integrator.management.apis.Constants.REGISTRY_PATH;
import static org.wso2.micro.integrator.management.apis.Constants.VALUE_TRUE;
import static org.wso2.micro.integrator.management.apis.Utils.formatPath;
import static org.wso2.micro.integrator.management.apis.Utils.validatePath;

/**
 * This class provides mechanisms to monitor registry directory.
 */
public class RegistryResource implements MiApiResource {

    Set<String> methods;

    public RegistryResource() {

        methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
            SynapseConfiguration synapseConfiguration) {

        handleGet(messageContext, axis2MessageContext);
        return true;
    }

    /**
     * This method handles GET.
     *
     * @param messageContext      Synapse message context
     * @param axis2MessageContext AXIS2 message context
     */
    private void handleGet(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext) {

        String registryPath = Utils.getQueryParameter(messageContext, REGISTRY_PATH);
        String expandedEnabled = Utils.getQueryParameter(messageContext, EXPAND_PARAM);
        MicroIntegratorRegistry microIntegratorRegistry = new MicroIntegratorRegistry();
        String validatedPath;
        if (Objects.nonNull(registryPath)) {
            validatedPath = validatePath(registryPath, axis2MessageContext);
            if (Objects.nonNull(validatedPath) && Objects.nonNull(expandedEnabled) && expandedEnabled.equals(
                    VALUE_TRUE)) {
                populateRegistryResourceJSON(axis2MessageContext, microIntegratorRegistry, validatedPath);
            } else if (Objects.nonNull(validatedPath)) {
                populateImmediateChildren(axis2MessageContext, microIntegratorRegistry, validatedPath);
            }
        } else {
            JSONObject jsonBody = Utils.createJsonError("Registry path not found in the request", axis2MessageContext,
                    BAD_REQUEST);
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        }
        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
    }

    /**
     * This method is used to get the <MI-HOME>/registry directory and its content as a JSON.
     *
     * @param axis2MessageContext     AXIS2 message context
     * @param microIntegratorRegistry Micro integrator registry
     */
    private void populateRegistryResourceJSON(org.apache.axis2.context.MessageContext axis2MessageContext,
            MicroIntegratorRegistry microIntegratorRegistry, String path) {

        String carbonHomePath = Utils.getCarbonHome();
        String folderPath = formatPath(carbonHomePath + File.separator + path + File.separator);
        File node = new File(folderPath);
        JSONObject jsonBody;
        if (node.exists() && node.isDirectory()) {
            jsonBody = microIntegratorRegistry.getRegistryResourceJSON(folderPath);
        } else {
            jsonBody = Utils.createJsonError("Invalid registry path", axis2MessageContext, BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * This method is to get the immediate child files, folders of a given directory with their metadata and properties.
     *
     * @param axis2MessageContext     AXIS2 message context
     * @param microIntegratorRegistry Micro integrator registry
     * @param path                    Registry path
     */
    private void populateImmediateChildren(org.apache.axis2.context.MessageContext axis2MessageContext,
            MicroIntegratorRegistry microIntegratorRegistry, String path) {

        String carbonHomePath = formatPath(Utils.getCarbonHome());
        String registryPath = formatPath(carbonHomePath + File.separator + path);
        File node = new File(registryPath);
        JSONObject jsonBody;
        if (node.exists() && node.isDirectory()) {
            JSONArray childrenList = microIntegratorRegistry.getChildrenList(registryPath, carbonHomePath);
            jsonBody = Utils.createJSONList(childrenList.length());
            jsonBody.put(Constants.LIST, childrenList);
        } else {
            jsonBody = Utils.createJsonError("Invalid registry path", axis2MessageContext, BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }
}