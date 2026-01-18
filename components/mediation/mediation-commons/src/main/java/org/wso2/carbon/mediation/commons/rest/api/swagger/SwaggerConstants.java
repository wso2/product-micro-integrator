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

import java.util.regex.Pattern;

/**
 * Constants used in Swagger definition generation.
 */
public class SwaggerConstants {

    /**
     * Content type "application/json" string for JSON messages
     */
    public static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * Content type "application/xml" string for JSON messages
     */
    public static final String CONTENT_TYPE_XML = "application/xml";

    /**
     * Content type "application/x-yaml" string for YAML messages
     */
    public static final String CONTENT_TYPE_YAML = "application/x-yaml";

    /**
     * Default encoding for response messages generated from swagger generation
     */
    public static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Success response code for swagger responses
     */
    public static final int HTTP_OK = 200;

    /**
     * Swagger element "swagger" in http://swagger.io/specification/
     */
    static final String SWAGGER = "swagger";

    /**
     * Swagger element "host" in http://swagger.io/specification/
     */
    static final String HOST = "host";

    /**
     * Swagger element "basePath" in http://swagger.io/specification/
     */
    static final String BASE_PATH = "basePath";

    static final String SERVERS = "servers";

    static final String VARIABLES = "variables";

    static final String DEFAULT_CONTEXT = "/default";

    static final String TEMPLATE_REGEX = "\\{(.*?)\\}";

    static final String  URL = "url";
    /**
     * Swagger element "info" in http://swagger.io/specification/
     */
    static final String INFO = "info";

    /**
     * Swagger element "description" in http://swagger.io/specification/
     */
    static final String DESCRIPTION = "description";

    /**
     * Swagger element "version" in http://swagger.io/specification/
     */
    static final String VERSION = "version";

    /**
     * Swagger element "title" in http://swagger.io/specification/
     */
    static final String TITLE = "title";

    /**
     * Swagger element "paths" in http://swagger.io/specification/
     */
    static final String PATHS = "paths";

    /**
     * Swagger element "consumes" in http://swagger.io/specification/
     */
    public static String CONSUMES = "consumes";

    /**
     * Swagger element "produces" in http://swagger.io/specification/
     */
    public static String PRODUCES = "produces";

    /**
     * Swagger element "parameters" in http://swagger.io/specification/
     */
    static final String PARAMETERS = "parameters";

    /**
     * Swagger element "responses" in http://swagger.io/specification/
     */
    static final String RESPONSES = "responses";

    /**
     * Swagger element "schemes" in http://swagger.io/specification/
     */
    static final String SCHEMES = "schemes";

    /**
     * Swagger element "description" of parameters in http://swagger.io/specification/
     */
    static final String PARAMETER_DESCRIPTION = "description";

    /**
     * Swagger element "in" of parameters in http://swagger.io/specification/
     */
    static final String PARAMETER_IN = "in";

    /**
     * Swagger element "name" of parameters in http://swagger.io/specification/
     */
    static final String PARAMETER_NAME = "name";

    /**
     * Swagger element "required" of parameters in http://swagger.io/specification/
     */
    static final String PARAMETER_REQUIRED = "required";

    /**
     * Swagger element "type" of parameters in http://swagger.io/specification/
     */
    static final String PARAMETER_TYPE = "type";

    /**
     * Default version of swagger definition
     */
    static final String SWAGGER_VERSION = "2.0";

    /**
     * String to be used in default "responses" elements
     */
    static final String DEFAULT_VALUE = "default";

    /**
     * Default swagger definition version
     */
    static final String DEFAULT_API_VERSION = "1.0.0";

    /**
     * Default value for API description prefix. API name will appended to this.
     */
    static final String API_DESC_PREFIX = "API Definition of ";

    /**
     * Default value for "response" element since it is not provided by API definition
     */
    static final String DEFAULT_RESPONSE = "Default Response";

    /**
     * Default value for parameter type since it is not provided by API configuration
     */
    static final String PARAMETER_TYPE_STRING = "string";

    /**
     * Parameter type "path"
     */
    static final String PARAMETER_IN_PATH = "path";

    /**
     * Parameter type "query"
     */
    static final String PARAMETER_IN_QUERY = "query";

    /**
     * Parameter type "body"
     */
    static final String PARAMETER_IN_BODY = "body";

    /**
     * Swagger element "schema" of body parameter in http://swagger.io/specification/
     */
    static final String PARAMETER_BODY_SCHEMA = "schema";

    /**
     * Swagger element "properties" of body schema in http://swagger.io/specification/
     */
    static final String PARAMETER_PROPERTIES = "properties";

    /**
     * Protocols supported by API - both HTTP and HTTPS
     */
    static final int PROTOCOL_HTTP_AND_HTTPS = 0;

    /**
     * Protocols supported by API - HTTP
     */
    static final int PROTOCOL_HTTP_ONLY = 1;

    /**
     * Protocols supported by API - HTTPS
     */
    static final int PROTOCOL_HTTPS_ONLY = 2;

    /**
     * Protocol name for HTTP
     */
    static final String PROTOCOL_HTTP = "http";

    /**
     * Protocol name for HTTPs
     */
    static final String PROTOCOL_HTTPS = "https";

    /**
     * Pattern to identify path parameters
     */
    static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{(.*?)\\}");

    /**
     * Path separator character
     */
    static final String PATH_SEPARATOR = "/";

    /**
     * Path to save the swagger document
     */
    public static String DEFAULT_SWAGGER_REGISTRY_PATH = "integrator/applicationdata/";

    public static final String SWAGGER_JSON_FILE_PATH = "/swagger.json";

    /**
     * WSDL Prefix defined in transport listener in axis2.xml
     */
    public static String WSDL_EPR_PREFIX = "WSDLEPRPrefix";

    /**
     * Swagger operations
     */
    public static final String OPERATION_HTTP_GET = "get";
    public static final String OPERATION_HTTP_POST = "post";
    public static final String OPERATION_HTTP_PUT = "put";
    public static final String OPERATION_HTTP_PATCH = "patch";
    public static final String OPERATION_HTTP_DELETE = "delete";
    public static final String OPERATION_HTTP_OPTIONS = "options";
    public static final String OPERATION_HTTP_HEAD = "head";

    /**
     * Default consume mime types
     */
    public static String[] DEFAULT_CONSUMES = {CONTENT_TYPE_JSON, CONTENT_TYPE_XML};

    /**
     * Default produce mime types
     */
    public static String[] DEFAULT_PRODUCES = {CONTENT_TYPE_JSON, CONTENT_TYPE_XML};

    /**
     * Default host for Swagger API
     */
    public static String DEFAULT_HOST = "localhost";

    /**
     * Default port for Swagger API
     */
    public static int DEFAULT_PORT = 8290;


}
