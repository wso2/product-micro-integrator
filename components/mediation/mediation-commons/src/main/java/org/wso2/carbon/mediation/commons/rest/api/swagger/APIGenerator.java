/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
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

package org.wso2.carbon.mediation.commons.rest.api.swagger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.swagger.util.Json;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SequenceType;
import org.apache.synapse.api.API;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.dispatch.URITemplateHelper;
import org.apache.synapse.api.dispatch.URLMappingHelper;
import org.apache.synapse.api.version.ContextVersionStrategy;
import org.apache.synapse.api.version.DefaultStrategy;
import org.apache.synapse.api.version.URLBasedVersionStrategy;
import org.apache.synapse.api.version.VersionStrategy;
import org.apache.synapse.config.xml.rest.APIFactory;
import org.apache.synapse.config.xml.rest.APISerializer;
import org.apache.synapse.config.xml.rest.VersionStrategyFactory;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.mediators.builtin.CommentMediator;
import org.apache.synapse.mediators.builtin.LoopBackMediator;
import org.apache.synapse.mediators.builtin.PropertyMediator;
import org.apache.synapse.mediators.builtin.RespondMediator;
import org.apache.synapse.mediators.transform.PayloadFactoryMediator;
import org.apache.synapse.mediators.transform.pfutils.RegexTemplateProcessor;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamException;

/**
 * This class will generate synapse Rest API configuration skeleton from swagger definition
 */
public class APIGenerator {

    private JsonObject swaggerJson;
    private static Log log = LogFactory.getLog(APIGenerator.class);

    public APIGenerator(JsonObject swaggerJson) {
        this.swaggerJson = swaggerJson;
    }

    /**
     * Generate API from provided swagger definition
     *
     * @return Generated API
     * @throws APIGenException
     */
    public API generateSynapseAPI() throws APIGenException {
        String apiContext;
        if (swaggerJson.get(SwaggerConstants.SERVERS) == null ||
                swaggerJson.get(SwaggerConstants.SERVERS).getAsJsonArray().size() == 0) {
            apiContext = SwaggerConstants.DEFAULT_CONTEXT;
        } else {
            JsonObject firstServer = swaggerJson.getAsJsonArray(SwaggerConstants.SERVERS).get(0).getAsJsonObject();
            // get the first path in the servers section
            String serversString = firstServer.get(SwaggerConstants.URL).getAsString();
            if (serversString.contains("{") && serversString.contains("}")) {
                // url is templated, need to resolve
                if (firstServer.has(SwaggerConstants.VARIABLES)) {
                    JsonObject variables = firstServer.get(SwaggerConstants.VARIABLES).getAsJsonObject();
                    serversString = replaceTemplates(serversString,variables);
                } else {
                    throw new APIGenException("Server url is templated, but variables cannot be found");
                }
            }
            try {
                URL url = new URL(serversString);
                apiContext = url.getPath();
            } catch (MalformedURLException e) {
                // url can be relative the place where the swagger is hosted.
                apiContext = serversString;
            }
            if (apiContext.isEmpty() || "/".equals(apiContext)) {
                apiContext = SwaggerConstants.DEFAULT_CONTEXT;
            }
            //cleanup context : remove ending '/'
            if (apiContext.lastIndexOf('/') == (apiContext.length() - 1)) {
                apiContext = apiContext.substring(0, apiContext.length() - 1);
            }
            // add leading / if not exists
            if (!apiContext.startsWith("/")) {
                apiContext = "/" + apiContext;
            }
        }

        if (swaggerJson.get(SwaggerConstants.INFO) == null) {
            throw new APIGenException("The \"info\" section of the swagger definition is mandatory for API generation");
        }
        JsonObject swaggerInfo = swaggerJson.getAsJsonObject(SwaggerConstants.INFO);
        if (swaggerInfo.get(SwaggerConstants.TITLE) == null ||
                swaggerInfo.get(SwaggerConstants.TITLE).getAsString().isEmpty()) {
            throw new APIGenException("The title of the swagger definition is mandatory for API generation");
        }

        String apiName = swaggerInfo.get(SwaggerConstants.TITLE).getAsString();

        // Extract version information
        String versionType = VersionStrategyFactory.TYPE_NULL;
        String version = "";
        JsonElement swaggerVersionElement = swaggerInfo.get(SwaggerConstants.VERSION);
        if (swaggerVersionElement != null && swaggerVersionElement.isJsonPrimitive() &&
                swaggerVersionElement.getAsJsonPrimitive().isString()) {
            version = swaggerVersionElement.getAsString();
            if (apiContext.endsWith(version)) {
                // If the base path ends with the version, then it will be considered as version-type=url
                versionType = VersionStrategyFactory.TYPE_URL;
                //cleanup api context path : remove version from base path
                apiContext = apiContext.substring(0, apiContext.length() - version.length() - 1);
            } else {
                // otherwise context based version strategy
                versionType = VersionStrategyFactory.TYPE_CONTEXT;
            }
        }

        // Create API
        API genAPI = new API(apiName, apiContext);

        //Add version strategy
        if (versionType.equals(VersionStrategyFactory.TYPE_URL)) {
            // If the base path ends with the version, then it will be considered as version-type=url
            genAPI.setVersionStrategy(new URLBasedVersionStrategy(genAPI, version, ""));
        } else if (versionType.equals(VersionStrategyFactory.TYPE_CONTEXT)) {
            // otherwise context based version strategy
            genAPI.setVersionStrategy(new ContextVersionStrategy(genAPI, version, ""));
        } else {
            genAPI.setVersionStrategy(new DefaultStrategy(genAPI));
        }

        if (swaggerJson.get(SwaggerConstants.PATHS) != null) {
            JsonObject pathsObj = swaggerJson.getAsJsonObject(SwaggerConstants.PATHS);
            for (Map.Entry<String, JsonElement> pathEntry : pathsObj.entrySet()) {
                if (pathEntry.getValue() instanceof JsonObject) {
                    createResource(pathEntry.getKey(), pathEntry.getValue().getAsJsonObject(), genAPI, null);
                }
            }
        }

        OMElement apiElement = APISerializer.serializeAPI(genAPI);
        if (log.isDebugEnabled()) {
            log.info("API generation completed : " + genAPI.getName() + " API: " + apiElement.toString());
        }
        return genAPI;
    }

    /**
     * Resolve templated URLs. Ex: https://{customerId}.saas-app.com:{port}/v2/gggg
     * @param input     Input template URL.
     * @param variables OpenAPI variables definition.
     * @return Resolved URL.
     * @throws APIGenException Error occurred while replacing the template values.
     */
    private String replaceTemplates(String input, JsonObject variables) throws APIGenException {
        Matcher m = Pattern.compile(SwaggerConstants.TEMPLATE_REGEX).matcher(input);
        while (m.find()) {
            String temp = m.group(1);
            if (variables.has(temp) && variables.get(temp).getAsJsonObject().has(SwaggerConstants.DEFAULT_VALUE)) {
                String realValue =
                        variables.get(temp).getAsJsonObject().get(SwaggerConstants.DEFAULT_VALUE).getAsString();
                input = input.replace("{" + temp + "}", realValue);
            } else {
                throw new APIGenException("Variables cannot be found to replace the value " + "{" + temp + "}");
            }
        }
        return input;
    }

    /**
     * Generate API from provided swagger definition referring to the old API.
     *
     * @param existingAPI old API
     * @return Generated API
     * @throws APIGenException
     */
    public API generateSynapseAPI(API existingAPI) throws APIGenException {
        String apiContext;
        if (swaggerJson.get(SwaggerConstants.SERVERS) == null ||
                swaggerJson.get(SwaggerConstants.SERVERS).getAsJsonArray().size() == 0) {
            apiContext = SwaggerConstants.DEFAULT_CONTEXT;
        } else {
            // get the first path in the servers section
            String serversString =
                    swaggerJson.getAsJsonArray(SwaggerConstants.SERVERS).get(0).getAsJsonObject()
                            .get(SwaggerConstants.URL).getAsString();
            try {
                URL url = new URL(serversString);
                apiContext = url.getPath();
            } catch (MalformedURLException e) {
                // url can be relative the place where the swagger is hosted.
                apiContext = serversString;
            }
        }
        //cleanup context : remove ending '/'
        if (apiContext.lastIndexOf('/') == (apiContext.length() - 1)) {
            apiContext = apiContext.substring(0, apiContext.length() - 1);
        }
        // add leading / if not exists
        if (!apiContext.startsWith("/")) {
            apiContext = "/" + apiContext;
        }

        if (swaggerJson.get(SwaggerConstants.INFO) == null) {
            throw new APIGenException("The \"info\" section of the swagger definition is mandatory for API generation");
        }
        JsonObject swaggerInfo = swaggerJson.getAsJsonObject(SwaggerConstants.INFO);
        if (swaggerInfo.get(SwaggerConstants.TITLE) == null ||
                swaggerInfo.get(SwaggerConstants.TITLE).getAsString().isEmpty()) {
            throw new APIGenException("The title of the swagger definition is mandatory for API generation");
        }

        String apiName = swaggerInfo.get(SwaggerConstants.TITLE).getAsString();

        // Extract version information
        String versionType = VersionStrategyFactory.TYPE_NULL;
        String version = "";
        JsonElement swaggerVersionElement = swaggerInfo.get(SwaggerConstants.VERSION);
        if (swaggerVersionElement != null && swaggerVersionElement.isJsonPrimitive() &&
                swaggerVersionElement.getAsJsonPrimitive().isString()) {
            version = swaggerVersionElement.getAsString();
            if (apiContext.endsWith(version)) {
                // If the base path ends with the version, then it will be considered as version-type=url
                versionType = VersionStrategyFactory.TYPE_URL;
                //cleanup api context path : remove version from base path
                apiContext = apiContext.substring(0, apiContext.length() - version.length() - 1);
            } else {
                // otherwise context based version strategy
                versionType = VersionStrategyFactory.TYPE_CONTEXT;
            }
        }

        // Create API
        API genAPI = new API(apiName, apiContext);

        //Add version strategy
        if (versionType.equals(VersionStrategyFactory.TYPE_URL)) {
            // If the base path ends with the version, then it will be considered as version-type=url
            genAPI.setVersionStrategy(new URLBasedVersionStrategy(genAPI, version, ""));
        } else if (versionType.equals(VersionStrategyFactory.TYPE_CONTEXT)) {
            // otherwise context based version strategy
            genAPI.setVersionStrategy(new ContextVersionStrategy(genAPI, version, ""));
        } else {
            genAPI.setVersionStrategy(new DefaultStrategy(genAPI));
        }

        if (swaggerJson.get(SwaggerConstants.PATHS) != null) {
            JsonObject pathsObj = swaggerJson.getAsJsonObject(SwaggerConstants.PATHS);
            for (Map.Entry<String, JsonElement> pathEntry : pathsObj.entrySet()) {
                if (pathEntry.getValue() instanceof JsonObject) {
                    createResource(pathEntry.getKey(), pathEntry.getValue().getAsJsonObject(), genAPI, existingAPI);
                }
            }
        }

        OMElement apiElement = APISerializer.serializeAPI(genAPI);
        if (log.isDebugEnabled()) {
            log.info("API generation completed : " + genAPI.getName() + " API: " + apiElement.toString());
        }
        return genAPI;
    }

    /**
     * Function to generate updated API extracting the implementation from an existing API
     *
     * @param existingAPI the existing API to extract resource implementations
     * @return generated API
     */
    public API generateUpdatedSynapseAPI(API existingAPI) throws APIGenException {
        if (existingAPI == null) {
            throw new APIGenException("Provided existing API is null");
        }
        API genAPI = generateSynapseAPI(existingAPI);
        // clone existing API
        API clonedAPI;
        try {
            clonedAPI = APIFactory.createAPI(AXIOMUtil.stringToOM(APISerializer.serializeAPI(existingAPI).toString()));
        } catch (XMLStreamException e) {
            throw new APIGenException("Error occurred while cloning the existing API", e);
        }
        // handle if the existing API use default version strategy (when generating default version strategy won't
        // get selected)
        if (existingAPI.getVersionStrategy() instanceof DefaultStrategy) {
            genAPI.setVersionStrategy(new DefaultStrategy(genAPI));
        }
        // Copy the Swagger resource property if specified in the existing API
        if (StringUtils.isNotBlank(existingAPI.getSwaggerResourcePath())) {
            genAPI.setSwaggerResourcePath(existingAPI.getSwaggerResourcePath());
        }
        updateImplChanges(genAPI, clonedAPI);
        return genAPI;
    }

    /**
     * Function to create resource from swagger definition.
     *
     * @param path        path of the resource
     * @param resourceObj json representation of resource
     * @param genAPI      generated API
     * @param existingAPI old API
     */
    private void createResource(String path, JsonObject resourceObj, API genAPI, API existingAPI)
            throws APIGenException {
        boolean noneURLStyleAdded = false;
        List<Resource> resources = new ArrayList<>();
        if (existingAPI != null) {
            for (Resource resource : existingAPI.getResources()) {
                String resourceMapping = resource.getDispatcherHelper() != null ?
                        resource.getDispatcherHelper().getString() : "/";
                // Getting all the resources whose path matches
                if (path.equals(resourceMapping)) {
                    resources.add(resource);
                }
            }
        }
        int i = 0;
        // Same number is assigned to all the method in the same resource of the existing API
        HashMap<String, Integer> methodMapping = new HashMap<>();
        HashMap<Integer, Resource> createdResources = new HashMap<>();
        for (Resource resource : resources) {
            for (String method : resource.getMethods()) {
                methodMapping.put(method, i);
            }
            i++;
        }
        for (Map.Entry<String, JsonElement> methodEntry : resourceObj.entrySet()) {
            if (log.isDebugEnabled()) {
                log.info("Generating resource for path : " + path + ", method : " + methodEntry.getKey());
            }

            String methodName = methodEntry.getKey().toUpperCase();
            if (methodMapping.containsKey(methodName)) {
                Resource createdResource = createdResources.get(methodMapping.get(methodName));
                // Check if a resource was created for another method belongs to the same resource.
                if (createdResource != null) {
                    createdResource.addMethod(methodName);
                    continue;
                }
            }

            // Create a new resource for each method.
            Resource resource = new Resource();
            resource.addMethod(methodName);

            // Identify URL Mapping and template and create relevant helper
            Matcher matcher = SwaggerConstants.PATH_PARAMETER_PATTERN.matcher(path);
            ArrayList<String> pathParamList = new ArrayList<>();
            while (matcher.find()) {
                pathParamList.add(matcher.group(1));
            }
            if (pathParamList.isEmpty()) {
                // if the path is '/' then it should have none URL style
                if (!"/".equals(path) || noneURLStyleAdded) {
                    resource.setDispatcherHelper(new URLMappingHelper(path));
                }
                if ("/".equals(path)) {
                    noneURLStyleAdded = true;
                }
            } else {
                resource.setDispatcherHelper(new URITemplateHelper(path));
            }

            resource.setInSequence(APIGenerator.getDefaultInSequence(pathParamList));
            resource.setOutSequence(APIGenerator.getDefaultOutSequence());
            genAPI.addResource(resource);

            if (methodMapping.containsKey(methodName)) {
                createdResources.put(methodMapping.get(methodName), resource);
            }
        }

    }

    private void updateImplChanges(API newAPI, API currentAPI) {
        String newVersion = newAPI.getVersion();
        //Migrate version strategy
        VersionStrategy strategy = currentAPI.getVersionStrategy();
        if (strategy instanceof URLBasedVersionStrategy) {
            newAPI.setVersionStrategy(new URLBasedVersionStrategy(newAPI, newVersion,
                    currentAPI.getVersionStrategy().getVersionParam()));
        } else if (strategy instanceof ContextVersionStrategy) {
            newAPI.setVersionStrategy(
                    new ContextVersionStrategy(newAPI, newVersion, currentAPI.getVersionStrategy().getVersionParam()));
        }

        // Map of resources against resource url mapping or template
        HashMap<String, HashMap<String, Resource>> currentResourceList = new HashMap<>();
        // Extract all existing resources and categorize according URL mapping or template
        for (Resource resource : currentAPI.getResources()) {

            String resourceMapping = resource.getDispatcherHelper() != null ?
                    resource.getDispatcherHelper().getString() : "/";
            HashMap<String, Resource> resourceMap;
            if (currentResourceList.get(resourceMapping) != null) {
                resourceMap = currentResourceList.get(resourceMapping);
            } else {
                resourceMap = new HashMap<>();
            }
            for (String method : resource.getMethods()) {
                resourceMap.put(method, resource);
            }
            currentResourceList.put(resourceMapping, resourceMap);
        }

        for (Resource resource : newAPI.getResources()) {

            String resourceMapping = resource.getDispatcherHelper() != null ?
                    resource.getDispatcherHelper().getString() : "/";
            HashMap<String, Resource> existingResources = currentResourceList.get(resourceMapping);

            if (existingResources != null) {
                // TODO handle multiple resources with same URL mapping or template with different methods
                for (String method : resource.getMethods()) {
                    // Check for a resource with matching method
                    if (existingResources.containsKey(method)) {
                        compareAndUpdateResource(existingResources.get(method), resource);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Function to compare and update resource implementation
     *
     * @param source source resource to extract implementation
     * @param target resource to update
     */
    private void compareAndUpdateResource(Resource source, Resource target) {
        if (source.getInSequenceKey() != null) {
            target.setInSequenceKey(source.getInSequenceKey());
        } else if (source.getInSequence() != null) {
            target.setInSequence(source.getInSequence());
        }

        if (source.getOutSequenceKey() != null) {
            target.setOutSequenceKey(source.getOutSequenceKey());
        } else if (source.getOutSequence() != null) {
            target.setOutSequence(source.getOutSequence());
        }

        if (source.getFaultSequenceKey() != null) {
            target.setFaultSequenceKey(source.getFaultSequenceKey());
        } else if (source.getFaultSequence() != null) {
            target.setFaultSequence(source.getFaultSequence());
        }
    }

    /**
     * Function to create default in sequence
     *
     * @return template API in-sequence
     */
    private static SequenceMediator getDefaultInSequence(List<String> pathParams) throws APIGenException {
        SequenceMediator defaultInSeq = new SequenceMediator();
        defaultInSeq.setSequenceType(SequenceType.ANON);

        CommentMediator generatedComment = new CommentMediator();
        generatedComment.setCommentText("This is generated API skeleton.");
        defaultInSeq.addChild(generatedComment);

        if (pathParams != null && pathParams.size() > 0) {
            // Create populate properties reading path parameters
            for (String param : pathParams) {
                try {
                    PropertyMediator propertyMediator = new PropertyMediator();
                    propertyMediator.setExpression(new SynapseXPath("get-property('uri.var." + param + "')"));
                    propertyMediator.setName(param);
                    defaultInSeq.addChild(propertyMediator);
                } catch (JaxenException e) {
                    throw new APIGenException("Error occurred while creating property mediator for extracting path " +
                            "params", e);
                }
            }
        }

        CommentMediator logicGoesHereComment = new CommentMediator();
        logicGoesHereComment.setCommentText("Business Logic Goes Here");
        defaultInSeq.addChild(logicGoesHereComment);

        PayloadFactoryMediator defaultPayload = new PayloadFactoryMediator();
        defaultPayload.setTemplateProcessor(new RegexTemplateProcessor());
        defaultPayload.setType("json");
        defaultPayload.setFormat("{\"Response\" : \"Sample Response\"}");
        defaultInSeq.addChild(defaultPayload);

        defaultInSeq.addChild(new LoopBackMediator());
        return defaultInSeq;
    }

    /**
     * Function to create default out sequence
     *
     * @return template API in-sequence
     */
    private static SequenceMediator getDefaultOutSequence() {
        SequenceMediator defaultOutSeq = new SequenceMediator();
        defaultOutSeq.setSequenceType(SequenceType.ANON);
        defaultOutSeq.addChild(new RespondMediator());
        return defaultOutSeq;
    }
}
