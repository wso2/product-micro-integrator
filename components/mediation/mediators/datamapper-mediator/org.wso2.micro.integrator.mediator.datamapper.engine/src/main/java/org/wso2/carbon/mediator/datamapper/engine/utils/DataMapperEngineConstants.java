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
package org.wso2.carbon.mediator.datamapper.engine.utils;

/**
 * This class contains constants used in Data Mapper Engine
 */
public class DataMapperEngineConstants {

    public static final String SCHEMA_ATTRIBUTE_FIELD_PREFIX = "attr_";
    public static final String SCHEMA_ATTRIBUTE_PARENT_ELEMENT_POSTFIX = "ATTR";
    public static final String OBJECT_ELEMENT_TYPE = "object";
    public static final String ARRAY_ELEMENT_TYPE = "array";
    public static final String STRING_ELEMENT_TYPE = "string";
    public static final String BOOLEAN_ELEMENT_TYPE = "boolean";
    public static final String INTEGER_ELEMENT_TYPE = "integer";
    public static final String NUMBER_ELEMENT_TYPE = "number";
    public static final String NULL_ELEMENT_TYPE = "null";
    public static final String ARRAY_ELEMENT_FIRST_NAME = "0";
    public static final String NASHORN_ENGINE_NAME = "nashorn";
    public static final String GRAALJS_ENGINE_NAME = "graal.js";
    public static final String DEFAULT_ENGINE_NAME = "js"; //rhino
    public static final int DEFAULT_DATAMAPPER_ENGINE_POOL_SIZE = 20;
    public static final String ORG_APACHE_SYNAPSE_DATAMAPPER_EXECUTOR_POOL_SIZE =
            "org.apache.synapse.datamapper.executor.pool.size";
    public static final String SCHEMA_NAMESPACE_NAME_SEPARATOR = ":";
    public static final String SCHEMA_XML_ELEMENT_TEXT_VALUE_FIELD = "_ELEMVAL";
    public static final  String DMC_FILE_FUNCTION_PREFIX = "function ";
    public static final  String DMC_FILE_DOLLAR_FUNCTION_PREFIX = "$function ";
    public static final String XSI_NAMESPACE_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String XMLNS = "xmlns";
    public static final String PROPERTIES_KEY = "properties";
    public static final String ATTRIBUTES_KEY = "attributes";
    public static final String TYPE_KEY = "type";
    public static final String ITEMS_KEY = "items";
    public static final String VALUE_KEY = "value";
    public static final String PROPERTIES_OBJECT_NAME = "DM_PROPERTIES";
    public static final String EQUALS_SIGN = "=";
    public static final String JS_STRINGIFY = "JSON.stringify";
    public static final String BRACKET_OPEN = "(";
    public static final String BRACKET_CLOSE = ")";
    public static final String FUNCTION_NAME_CONST_1 = "map_S_";
    public static final String FUNCTION_NAME_CONST_2 = "_S_";
    public static final String INPUT_VARIABLE_IDENTIFIER = "inputVariables";
    public static final String MAP_FUNCTION_NAME = "mapFunction";
    public static final String MAP_FUNCTION = "mapFunction(JSON.parse(" + INPUT_VARIABLE_IDENTIFIER + "))";
    public static final String NAME_SEPERATOR = "_Separat0r_";
    public static final String ENCODE_CHAR_HYPHEN = "_EnC0DeCHaRHyPh3n_";
    public static final String HYPHEN = "-";
    public static final String PREFIX_LIST_SEPERATOR = ",";
    public static final String EMPTY_STRING = "";
    public static final String PARAMETER_FILE_ROOT = "xsl:stylesheet";
    public static final String RUN_TIME_PROPERTIES = "xmlns:runTimeProperties";
    public static final String NOT_XSLT_COMPATIBLE = "xmlns:notXSLTCompatible";
    public static final String PROPERTY_SEPERATOR = ",";
    public static final String FIRST_ELEMENT_OF_THE_INPUT = "xmlns:firstElementOfTheInput";
    public static final String XSLT_COMPATIBLE_DEFAULT = "false";
    public static final String OUTPUT_TYPE = "outputType";
}
