/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.transport.handlers.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisResource;
import org.apache.axis2.description.AxisResourceMap;
import org.apache.axis2.description.AxisResourceParameter;
import org.apache.axis2.description.AxisResources;
import org.apache.axis2.description.AxisService;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.api.API;
import org.apache.synapse.api.version.DefaultStrategy;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.registry.Registry;
import org.wso2.carbon.mediation.commons.rest.api.swagger.OpenAPIProcessor;
import org.wso2.carbon.mediation.commons.rest.api.swagger.SwaggerConstants;
import org.wso2.micro.core.Constants;
import org.wso2.micro.integrator.transport.handlers.requestprocessors.swagger.format.MIServerConfig;

import javax.xml.namespace.QName;

import static org.wso2.micro.application.deployer.AppDeployerUtils.createRegistryPath;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.micro.core.Constants.SUPER_TENANT_DOMAIN_NAME;

/**
 * Util class with methods to generate swagger definition and fetch them from the registry.
 */
public final class SwaggerUtils {

    private static Log logger = LogFactory.getLog(SwaggerUtils.class.getName());

    /**
     * Fetch the swagger from registry if available or create one from scratch.
     *
     * @param requestURI           request URI.
     * @param configurationContext Configuration context with details.
     * @param isJSON               result format JSON or YAML.
     * @return Swagger definition as string
     * @throws AxisFault Error occurred while fetching the host details.
     */
    public static String getDataServiceSwagger(String requestURI, ConfigurationContext configurationContext,
                                               boolean isJSON) throws AxisFault, SwaggerException {
        String dataServiceName = requestURI.substring(requestURI.lastIndexOf("/") + 1);
        AxisService dataService = configurationContext.getAxisConfiguration().getService(dataServiceName);
        if (dataService != null) {
            MIServerConfig serverConfig = new MIServerConfig();
            Object dataServiceObject =
                    dataService.getParameter(SwaggerProcessorConstants.DATA_SERVICE_OBJECT).getValue();
            if (dataService.getParameter(SwaggerProcessorConstants.SWAGGER_RESOURCE_PATH) != null) {
                String swaggerLocation =
                        (String) dataService.getParameter(SwaggerProcessorConstants.SWAGGER_RESOURCE_PATH).getValue();
                if (StringUtils.isNotEmpty(swaggerLocation)) {
                    String swaggerFromReg = SwaggerUtils.fetchSwaggerFromRegistry(swaggerLocation);
                    if (StringUtils.isNotEmpty(swaggerFromReg)) {
                        return swaggerFromReg;
                    } else throw new SwaggerException("Could not fetch the swagger definition from registry. Registry" +
                            " path : " + swaggerLocation);
                }
                return null;
            } else {
                List<String> transports = dataService.getExposedTransports();
                if (dataServiceObject instanceof AxisResources) {
                    AxisResourceMap axisResourceMap = ((AxisResources) dataServiceObject).getAxisResourceMap();
                    return SwaggerUtils.createSwaggerFromDefinition(axisResourceMap, dataServiceName, transports,
                            serverConfig, isJSON, dataService);
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Create a swagger definition from data-service resource details.
     *
     * @param axisResourceMap AxisResourceMap containing resource details.
     * @param dataServiceName Name of the data service.
     * @param transports      Transports supported from the data-service.
     * @param serverConfig    Server config details.
     * @param isJSON          result format JSON or YAML.
     * @return Swagger definition as string.
     * @throws AxisFault Error occurred while fetching the host address.
     */
    private static String createSwaggerFromDefinition(AxisResourceMap axisResourceMap, String dataServiceName,
                                                      List<String> transports, MIServerConfig serverConfig,
                                                      boolean isJSON, AxisService dataService) throws AxisFault {

        OpenAPI openAPI = new OpenAPI();

        Info info = new Info();
        info.title(dataServiceName);
        info.setVersion("1.0");
        info.description("API Definition of dataservice : " + dataServiceName);
        openAPI.setInfo(info);

        addServersSection(dataServiceName, transports, serverConfig, openAPI);

        Paths paths = new Paths();

        for (Map.Entry<String, AxisResource> entry : axisResourceMap.getResources().entrySet()) {
            PathItem pathItem = new PathItem();
            for (String method : entry.getValue().getMethods()) {
                Operation operation = new Operation();
                List<AxisResourceParameter> parameterList = entry.getValue().getResourceParameterList(method);
                addPathAndQueryParameters(method, operation, parameterList);
                // Adding a sample request payload for methods except GET and DELETE ( OAS3 onwards )
                addSampleRequestBody(method, operation, parameterList, entry.getKey().
                        endsWith(Constants.BATCH_REQUEST));
                addDefaultResponseAndPathItem(pathItem, method, operation);
            }
            // adding the resource. all the paths should start with "/"
            paths.put(entry.getKey().startsWith("/") ? entry.getKey() : "/" + entry.getKey(), pathItem);
        }
        if (dataService.getOperation(new QName("request_box")) != null) {
            addRestRequestBoxPath(axisResourceMap, paths);
        }
        openAPI.setPaths(paths);
        try {
            if (isJSON) return Json.mapper().writeValueAsString(openAPI);
            return Yaml.mapper().writeValueAsString(openAPI);
        } catch (JsonProcessingException e) {
            logger.error("Error occurred while creating the YAML configuration", e);
            return null;
        }
    }

    private static void addRestRequestBoxPath(AxisResourceMap axisResourceMap, Paths paths) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        HashMap<String, List<AxisResourceParameter>> parameterListWithOperationName = new HashMap<>();
        for (Map.Entry<String, AxisResource> entry : axisResourceMap.getResources().entrySet()) {
            String name = entry.getKey();
            if (!name.endsWith(Constants.BATCH_REQUEST)) {
                for (String method : entry.getValue().getMethods()) {
                    String requestBoxMethodName = Constants.UNDERSCORE.concat(method.toLowerCase()).concat(
                            name.replaceAll(Constants.CURLY_OPEN_BRACKET, Constants.EMPTY).
                                    replaceAll(Constants.CURLY_CLOSE_BRACKET, Constants.EMPTY).
                                    replaceAll(Constants.SLASH, Constants.UNDERSCORE).toLowerCase());
                    parameterListWithOperationName.put(requestBoxMethodName,
                            entry.getValue().getResourceParameterList(method));
                }
            }
        }
        addSampleRequestBoxBody(operation, parameterListWithOperationName);
        addDefaultResponseAndPathItem(pathItem, "POST", operation);
        paths.put(Constants.SLASH.concat(Constants.REQUEST_BOX), pathItem);
    }

    // Add request body schema for methods except GET and DELETE.
    private static void addSampleRequestBody(String method, Operation operation,
                                             List<AxisResourceParameter> parameterList, boolean isBatchRequest) {

        if (!method.equals("GET") && !method.equals("DELETE")) {
            RequestBody requestBody = new RequestBody();
            requestBody.description("Sample Payload");
            requestBody.setRequired(false);

            MediaType mediaType = new MediaType();
            Schema bodySchema = new Schema();
            bodySchema.setType("object");

            Map<String, Schema> inputProperties = new HashMap<>();
            ObjectSchema objectSchema = new ObjectSchema();
            Map<String, Schema> payloadProperties = new HashMap<>();
            updatePayloadPropertiesWithTypeMapping(parameterList, payloadProperties);
            objectSchema.setProperties(payloadProperties);
            bodySchema.setProperties(inputProperties);
            if (isBatchRequest) {
                // Create the outer structure schema
                Schema outerSchema = new ObjectSchema().
                        addProperty(Constants.PAYLOAD, new ArraySchema().items(objectSchema));
                inputProperties.put(Constants.PAYLOAD, outerSchema);
            } else {
                inputProperties.put(Constants.PAYLOAD, objectSchema);
            }
            mediaType.setSchema(bodySchema);
            Content content = new Content();
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            operation.setRequestBody(requestBody);
        }
    }

    private static void addSampleRequestBoxBody(Operation operation,
                                                HashMap<String, List<AxisResourceParameter>> parameterList) {
        RequestBody requestBody = new RequestBody();
        requestBody.description("Sample Payload");
        requestBody.setRequired(false);

        MediaType mediaType = new MediaType();
        Schema bodySchema = new Schema();
        bodySchema.setType("object");

        Map<String, Schema> inputProperties = new HashMap<>();
        Map<String, Schema> properties = new HashMap<>();
        ObjectSchema objectSchema = new ObjectSchema();
        for (Map.Entry<String, List<AxisResourceParameter>> entry :parameterList.entrySet()) {
            Map<String, Schema> payloadProperties = new HashMap<>();
            updatePayloadPropertiesWithTypeMapping(entry.getValue(), payloadProperties);
            ObjectSchema objectSchemaForEveryOperation = new ObjectSchema();
            objectSchemaForEveryOperation.setProperties(payloadProperties);
            properties.put(entry.getKey(), objectSchemaForEveryOperation);
        }
        objectSchema.setProperties(properties);
        inputProperties.put(Constants.REQUEST_BOX, objectSchema);
        bodySchema.setProperties(inputProperties);
        mediaType.setSchema(bodySchema);
        Content content = new Content();
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        operation.setRequestBody(requestBody);
    }

    private static void updatePayloadPropertiesWithTypeMapping(List<AxisResourceParameter> resourceParameters,
                                                        Map<String, Schema>  payloadProperties) {
        for (AxisResourceParameter resourceParameter : resourceParameters) {
            switch (resourceParameter.getParameterDataType()) {
                case SwaggerProcessorConstants.INTEGER:
                    payloadProperties.put(resourceParameter.getParameterName(), new IntegerSchema());
                    break;
                case SwaggerProcessorConstants.NUMBER:
                    payloadProperties.put(resourceParameter.getParameterName(), new NumberSchema());
                    break;
                case SwaggerProcessorConstants.BOOLEAN:
                    payloadProperties.put(resourceParameter.getParameterName(), new BooleanSchema());
                    break;
                default:
                    payloadProperties.put(resourceParameter.getParameterName(), new StringSchema());
            }
        }
    }

    // Add path parameters and query parameters to the operation.
    private static void addPathAndQueryParameters(String method, Operation operation,
                                                  List<AxisResourceParameter> parameterList) {

        if (!parameterList.isEmpty()) {
            for (AxisResourceParameter resourceParameter : parameterList) {
                AxisResourceParameter.ParameterType resourceParameterType =
                        resourceParameter.getParameterType();
                if (resourceParameterType.equals(AxisResourceParameter.ParameterType.URL_PARAMETER)) {
                    PathParameter pathParameter = new PathParameter();
                    pathParameter.setName(resourceParameter.getParameterName());
                    switch (resourceParameter.getParameterDataType()) {
                        case "integer":
                            pathParameter.setSchema(new IntegerSchema());
                            break;
                        case "number":
                            pathParameter.setSchema(new NumberSchema());
                            break;
                        case "boolean":
                            pathParameter.setSchema(new BooleanSchema());
                            break;
                        default:
                            pathParameter.setSchema(new StringSchema());
                            break;
                    }
                    pathParameter.required(true);
                    operation.addParametersItem(pathParameter);
                } else if (resourceParameterType
                        .equals(AxisResourceParameter.ParameterType.QUERY_PARAMETER) && method.equals("GET")) {
                    //  Currently handling query parameter only for GET requests.
                    QueryParameter queryParameter = new QueryParameter();
                    queryParameter.setName(resourceParameter.getParameterName());
                    switch (resourceParameter.getParameterDataType()) {
                        case "integer":
                            queryParameter.setSchema(new IntegerSchema());
                            break;
                        case "number":
                            queryParameter.setSchema(new NumberSchema());
                            break;
                        case "boolean":
                            queryParameter.setSchema(new BooleanSchema());
                            break;
                        default:
                            queryParameter.setSchema(new StringSchema());
                            break;
                    }
                    queryParameter.required(true);
                    operation.addParametersItem(queryParameter);
                }
            }
        }
    }

    // Add the default response ( since we cannot define it ) and pathItems to path map
    private static void addDefaultResponseAndPathItem(PathItem pathItem, String method, Operation operation) {

        ApiResponses apiResponses = new ApiResponses();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setDescription("Default response");
        apiResponses.addApiResponse("default", apiResponse);
        operation.setResponses(apiResponses);

        switch (method) {
            case "GET":
                pathItem.setGet(operation);
                break;
            case "POST":
                pathItem.setPost(operation);
                break;
            case "DELETE":
                pathItem.setDelete(operation);
                break;
            case "PUT":
                pathItem.setPut(operation);
                break;
        }
    }

    // Add servers section to the OpenApi definition.
    private static void addServersSection(String dataServiceName, List<String> transports, MIServerConfig serverConfig,
                                          OpenAPI openAPI) throws AxisFault {

        String scheme;
        String host;
        if (transports.contains("https")) {
            scheme = "https";
            host = serverConfig.getHost("https");
        } else {
            scheme = "http";
            host = serverConfig.getHost("http");
        }
        String basePath = "/" + SwaggerProcessorConstants.SERVICES_PREFIX + "/" + dataServiceName;

        Server server = new Server();
        server.setUrl(scheme + "://" + host + basePath);
        openAPI.setServers(Arrays.asList(server));
    }

    /**
     * Util method to fetch a swagger resource from the registry.
     *
     * @param resourcePath registry path to the resource.
     * @return null if no resource found or the swagger as string.
     */
    public static String fetchSwaggerFromRegistry(String resourcePath) {

        String defString = null;
        SynapseConfiguration synapseConfig =
                SynapseConfigUtils.getSynapseConfiguration(Constants.SUPER_TENANT_DOMAIN_NAME);
        Registry registry = synapseConfig.getRegistry();
        OMNode regContent = registry.lookup(createRegistryPath(resourcePath));

        if (regContent instanceof OMText) {
            defString = ((OMText) regContent).getText();
            byte[] decodedBytes = Base64.getDecoder().decode(defString);
            defString = new String(decodedBytes);
        }
        return defString;
    }

    /**
     * Get the swagger definition for a given API.
     *
     * @param api             API object.
     * @param isJSONRequested response needed in JSON / JAML.
     * @return Swagger as String.
     * @throws AxisFault Error occurred while fetching the host name for API.
     */
    public static String getAPISwagger(API api, boolean isJSONRequested) throws AxisFault {
        org.yaml.snakeyaml.Yaml yamlDefinition = new org.yaml.snakeyaml.Yaml();
        MIServerConfig serverConfig = new MIServerConfig();
        String responseString = retrieveAPISwaggerFromRegistry(api);
        // check the swagger in synapse configuration context if not found in registry.
        if (StringUtils.isEmpty(responseString)) {
            responseString = retrieveSwaggerSynapseConfiguration(api);
        }
        if (StringUtils.isNotEmpty(responseString)) {
            boolean isJson = false;
            try {
                new JsonParser().parse(responseString);
                isJson = true;
            } catch (JsonSyntaxException ex) {
                // neglect the error, content is in YAML format
            }
            if (isJSONRequested && !isJson) {
                final Object loadedYaml = yamlDefinition.load(responseString);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                responseString = gson.toJson(loadedYaml, LinkedHashMap.class);
            } else if (!isJSONRequested && isJson) {
                Gson gson = new Gson();
                Map map = gson.fromJson(responseString, Map.class);
                responseString = yamlDefinition.dumpAsMap(map);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Generating swagger definition for: " + api.getName());
            }
            OpenAPIProcessor openAPIProcessor = new OpenAPIProcessor(api, serverConfig);
            responseString = openAPIProcessor.getOpenAPISpecification(isJSONRequested);
        }
        return responseString;
    }

    /**
     * Function to extract swagger definition from the registry.
     *
     * @param api API object.
     * @return null if registry content unavailable or empty, otherwise relevant content.
     */
    private static String retrieveAPISwaggerFromRegistry(API api) {

        String resourcePath = api.getSwaggerResourcePath();

        if (resourcePath == null) {
            //Create resource path in registry
            StringBuilder resourcePathBuilder = new StringBuilder();
            resourcePathBuilder.append(SwaggerProcessorConstants.CONFIG_REG_PREFIX)
                    .append(SwaggerConstants.DEFAULT_SWAGGER_REGISTRY_PATH).append(api.getAPIName());
            if (!(api.getVersionStrategy() instanceof DefaultStrategy)) {
                resourcePathBuilder.append(":v").append(api.getVersion());
            }
            resourcePathBuilder.append("/swagger.json");
            resourcePath = resourcePathBuilder.toString();

        }

        return SwaggerUtils.fetchSwaggerFromRegistry(resourcePath);
    }

    /**
     * Function to retrieve swagger from the synapse configuration.
     *
     * @param api Api object.
     * @return swagger definition as string or null if not exists.
     */
    private static String retrieveSwaggerSynapseConfiguration(API api) {

        SynapseConfiguration synapseConfiguration =
                SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        return synapseConfiguration.getSwaggerOfTheAPI(api.getName());
    }
}
