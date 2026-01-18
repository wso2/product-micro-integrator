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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.api.API;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.dispatch.DispatcherHelper;
import org.apache.synapse.api.dispatch.URLMappingBasedDispatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Generalized object structure for Swagger definitions of APIs. This structure contains set of Maps which compatible
 * with both JSON and YAML formats.
 */
public class GenericApiObjectDefinition {
    private static final Log log = LogFactory.getLog(GenericApiObjectDefinition.class);

    /**
     * Provides structure for the "responses" element in swagger definition.
     *
     * @return Map containing information for responses element
     */
    private static Map<String, Object> getResponsesMap() {
        Map<String, Object> responsesMap = new LinkedHashMap<>();
        Map<String, Object> responseDetailsMap = new LinkedHashMap<>();
        //Use a default response since these information is not available in synapse configuration for APIs
        responseDetailsMap.put(SwaggerConstants.DESCRIPTION, SwaggerConstants.DEFAULT_RESPONSE);
        responsesMap.put(SwaggerConstants.DEFAULT_VALUE, responseDetailsMap);
        if(log.isDebugEnabled()){
            log.debug("Response map created with size " + responsesMap.size());
        }
        return responsesMap;
    }

    /**
     * Provides structure for the "paths" element in swagger definition.
     *
     * @return Map containing information for paths element
     */
    public static Map<String, Object> getPathMap(API api) {
        Map<String, Object> pathsMap = new LinkedHashMap<>();
        for (Resource resource : api.getResources()) {
            DispatcherHelper resourceDispatcherHelper = resource.getDispatcherHelper();
            Map<String, Object> methodMap =
                    (Map<String, Object>) pathsMap.get(getPathFromUrl(getUri(resourceDispatcherHelper)));
            if (methodMap == null) {
                methodMap = new LinkedHashMap<>();
            }
            for (String method : resource.getMethods()) {
                if (method != null) {
                    Map<String, Object> methodInfoMap = new LinkedHashMap<>();
                    methodInfoMap.put(SwaggerConstants.RESPONSES, getResponsesMap());
                    if (resourceDispatcherHelper != null) {
                        Object[] parameters = getResourceParameters(resource, method);
                        if (parameters.length > 0) {
                            methodInfoMap.put(SwaggerConstants.PARAMETERS, parameters);
                        }
                    }
                    methodMap.put(method.toLowerCase(), methodInfoMap);
                }
            }
            pathsMap.put(getPathFromUrl(getUri(resourceDispatcherHelper)), methodMap);
        }
        if(log.isDebugEnabled()){
            log.debug("Paths map created with size " + pathsMap.size());
        }
        return pathsMap;
    }

    private static String getUri(DispatcherHelper resourceDispatcherHelper) {
        return resourceDispatcherHelper == null ? SwaggerConstants.PATH_SEPARATOR
                : resourceDispatcherHelper.getString();
    }

    /**
     * Generate resource parameters for the given resource for given method.
     *
     * @param resource instance of Resource in the API
     * @return Array of parameter objects supported by the API
     */
    private static Object[] getResourceParameters(Resource resource, String method) {
        ArrayList<Map<String, Object>> parameterList = new ArrayList<>();

        String uri = resource.getDispatcherHelper().getString();

        if (resource.getDispatcherHelper() instanceof URLMappingBasedDispatcher) {
            generateParameterList(parameterList, uri, false);
        } else {
            generateParameterList(parameterList, uri, true);
        }
        if(log.isDebugEnabled()){
            log.debug("Parameters processed for the URI + " + uri + " size " + parameterList.size());
        }

        generateBodyParameter(parameterList, resource, method);

        return parameterList.toArray();
    }


    /**
     * Generate URI and Path parameters for the given URI.
     *
     * @param parameterList     List of maps to be populated with parameters
     * @param uriString         URI string to be used to extract parameters
     * @param generateBothTypes Indicates whether to consider both query and uri parameters. True if both to be
     *                          considered.
     */
    private static void generateParameterList(ArrayList<Map<String, Object>> parameterList, String uriString, boolean
            generateBothTypes) {
        if (uriString == null) {
            return;
        }
        if (generateBothTypes) {
            String[] params = getQueryStringFromUrl(uriString).split("&");
            for (String parameter : params) {
                if (parameter != null) {
                    int pos = parameter.indexOf('=');
                    if (pos > 0) {
                        parameterList.add(getParametersMap(parameter.substring(0, pos), SwaggerConstants.PARAMETER_IN_QUERY));
                    }
                }
            }
        }
        Matcher matcher = SwaggerConstants.PATH_PARAMETER_PATTERN.matcher(getPathFromUrl(uriString));
        while (matcher.find()) {
            parameterList.add(getParametersMap(matcher.group(1), SwaggerConstants.PARAMETER_IN_PATH));
        }
    }

    private static void generateBodyParameter(ArrayList<Map<String, Object>> parameterList, Resource resource,
                                        String method) {
        if (method.equalsIgnoreCase(SwaggerConstants.OPERATION_HTTP_POST) ||
                method.equalsIgnoreCase(SwaggerConstants.OPERATION_HTTP_PUT) ||
                method.equalsIgnoreCase(SwaggerConstants.OPERATION_HTTP_PATCH)) {
            parameterList.add(getParametersMap("payload", SwaggerConstants.PARAMETER_IN_BODY, "Sample Payload", false));
        }
    }

    /**
     * Create map of parameters from give name and type. Default values are used for other fields since those are not
     * provided by the synapse configuration of the API.
     *
     * @param parameterName Name of the parameter
     * @param parameterType Type of the parameter
     * @return Map containing parameter properties
     */
    private static Map<String, Object> getParametersMap(String parameterName, String parameterType) {
        return getParametersMap(parameterName, parameterType, parameterName, true);
    }

    private static Map<String, Object> getParametersMap(String parameterName, String parameterType, String description,
                                                 boolean required) {
        Map<String, Object> parameterMap = new LinkedHashMap<>();
        parameterMap.put(SwaggerConstants.PARAMETER_DESCRIPTION, description);
        parameterMap.put(SwaggerConstants.PARAMETER_IN, parameterType);
        parameterMap.put(SwaggerConstants.PARAMETER_NAME, parameterName);
        parameterMap.put(SwaggerConstants.PARAMETER_REQUIRED, required);

        if (parameterType.equals(SwaggerConstants.PARAMETER_IN_BODY)) {
            /*
             * If the parameter in: body, then it should contain schema describing the body structure. Since synapse
             * configuration does not contain such information DEFAULT schema will be generated for default payload:
             *          {
             *              "payload" : "string"
             *          }
             */
            Map<String, Object> schemaMap = new LinkedHashMap<>();
            Map<String, Object> schemaPropertiesMap = new LinkedHashMap<>();
            Map<String, Object> schemaPropertiesTypeMap = new LinkedHashMap<>();

            schemaPropertiesTypeMap.put("type", "string");
            schemaPropertiesMap.put("payload", schemaPropertiesTypeMap);
            schemaMap.put(SwaggerConstants.PARAMETER_TYPE, "object");
            schemaMap.put(SwaggerConstants.PARAMETER_PROPERTIES, schemaPropertiesMap);

            parameterMap.put(SwaggerConstants.PARAMETER_BODY_SCHEMA, schemaMap);

        } else  {
            /* Type will be "string" for all parameters since synapse configuration does
            not contain such information for APIs. */
            parameterMap.put(SwaggerConstants.PARAMETER_TYPE, SwaggerConstants.PARAMETER_TYPE_STRING);
        }

        return parameterMap;
    }

    /**
     * Get the path portion from the URI.
     *
     * @param uri String URI to be analysed
     * @return String containing the path portion of the URI
     */
    private static String getPathFromUrl(String uri) {
        int pos = uri.indexOf("?");
        if (pos > 0) {
            return uri.substring(0, pos);
        }
        return uri;
    }

    /**
     * Get query parameter portion from the URI.
     *
     * @param uri String URI to be analysed
     * @return String containing the URI parameter portion of the URI
     */
    private static String getQueryStringFromUrl(String uri) {
        int pos = uri.indexOf("?");
        if (pos > 0) {
            return uri.substring(pos + 1);
        }
        return "";
    }

    /**
     * A util method to convert from YAML to JSON.
     *
     * @param yaml YAML input as string.
     * @return converted JSON as string.
     * @throws JsonProcessingException error occurred while parsing the JSON.
     */
    public static String convertYamlToJson(String yaml) throws JsonProcessingException {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        Object obj = yamlReader.readValue(yaml, Object.class);

        ObjectMapper jsonWriter = new ObjectMapper();
        return jsonWriter.writeValueAsString(obj);
    }
}
