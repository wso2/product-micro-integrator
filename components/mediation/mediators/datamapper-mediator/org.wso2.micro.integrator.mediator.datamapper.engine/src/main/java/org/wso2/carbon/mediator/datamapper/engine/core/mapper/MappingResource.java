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
package org.wso2.carbon.mediator.datamapper.engine.core.mapper;

import org.apache.commons.lang.StringUtils;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.JSException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.JacksonJSONSchema;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.Schema;
import org.wso2.carbon.mediator.datamapper.engine.utils.InputOutputDataType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.BRACKET_CLOSE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.BRACKET_OPEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.FUNCTION_NAME_CONST_1;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.FUNCTION_NAME_CONST_2;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.JS_STRINGIFY;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.HYPHEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.ENCODE_CHAR_HYPHEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.MAP_FUNCTION;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.MAP_FUNCTION_NAME;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.OUTPUT_TYPE;

public class MappingResource {

    public static final String NAMESPACE_DELIMETER = ":";
    public static final String NEW_LINE = "\n";
    private Schema inputSchema;
    private Schema outputSchema;
    private String inputRootelement;
    private String outputRootelement;
    private JSFunction function;
    private List<String> propertiesList;

    /**
     * @param inputSchema   respective output json schema as a a stream of bytes
     * @param outputSchema  respective output json schema as a a stream of bytes
     * @param mappingConfig mapping configuration file as a stream of bytes
     * @throws IOException when input errors, If there any parser exception occur while passing
     *                     above schemas method
     *                     will this exception
     */
    public MappingResource(InputStream inputSchema, InputStream outputSchema, InputStream mappingConfig,
            String outputType) throws SchemaException, JSException {
        this.inputSchema = getJSONSchema(inputSchema);
        this.outputSchema = getJSONSchema(outputSchema);
        this.inputRootelement = this.inputSchema.getName();
        this.outputRootelement = this.outputSchema.getName();
        this.propertiesList = new ArrayList<>();
        this.function = createFunction(mappingConfig, outputType);
    }

    private Schema getJSONSchema(InputStream inputSchema) throws SchemaException {
        return new JacksonJSONSchema(inputSchema);
    }

    public Schema getInputSchema() {
        return inputSchema;
    }

    public Schema getOutputSchema() {
        return outputSchema;
    }

    public JSFunction getFunction() {
        return function;
    }

    public void setFunction(JSFunction function) {
        this.function = function;
    }

    /**
     * propertiesList contains a list of WSO2 ESB Properties used in the Data Mapper Mapping configuration.
     * These will be extracted by processing the mapping configuration file and will be included as Strings
     * in the format of : "SCOPE['PROP_NAME']"
     *
     * @return propertiesList
     */
    public List getPropertiesList() {
        return propertiesList;
    }

    /**
     * need to create java script function by passing the configuration file
     * Since this function going to execute every time when message hit the mapping backend
     * so this function save in the resource model
     *
     * @param mappingConfig mapping configuration
     * @return java script function
     */
    private JSFunction createFunction(InputStream mappingConfig, String outputType) throws JSException {
        BufferedReader configReader = new BufferedReader(new InputStreamReader(mappingConfig, StandardCharsets.UTF_8));
        //need to identify the main method of the configuration because that method going to
        // execute in engine
        String[] inputRootElementArray = inputRootelement.split(NAMESPACE_DELIMETER);
        String inputRootElement = inputRootElementArray[inputRootElementArray.length - 1];
        inputRootElement = inputRootElement.replaceAll("[:=,]", "_").replace(HYPHEN, ENCODE_CHAR_HYPHEN);
        String[] outputRootElementArray = outputRootelement.split(NAMESPACE_DELIMETER);
        String outputRootElement = outputRootElementArray[outputRootElementArray.length - 1];
        String jsFunctionBody;

        String propertiesPattern = "(DM_PROPERTIES.)([a-zA-Z_$][a-zA-Z_$0-9]*)\\['([a-zA-Z_$][a-zA-Z-_.$0-9]*)'\\]";
        Pattern pattern = Pattern.compile(propertiesPattern);
        Matcher match;

        String fnName =
                FUNCTION_NAME_CONST_1 + inputRootElement + FUNCTION_NAME_CONST_2 + outputRootElement + BRACKET_OPEN
                        + BRACKET_CLOSE;
        String configLine;
        StringBuilder configScriptBuilder = new StringBuilder();
        try {
            while ((configLine = configReader.readLine()) != null) {
                configScriptBuilder.append(configLine);
                configScriptBuilder.append(NEW_LINE);
            }
        } catch (IOException e) {
            throw new JSException(e.getMessage());
        }

        jsFunctionBody = configScriptBuilder.toString();
        match = pattern.matcher(jsFunctionBody);

        while (match.find()) {
            propertiesList.add(match.group(2) + "['" + match.group(3) + "']");
        }

        if (!jsFunctionBody.contains(fnName) && jsFunctionBody.contains(MAP_FUNCTION_NAME)) {
            fnName = MAP_FUNCTION;
        }
        if (StringUtils.isEmpty(outputType)) {
            if (outputSchema != null && outputSchema.getSchemaMap() != null &&
                    outputSchema.getSchemaMap().containsKey(OUTPUT_TYPE)) {
                outputType = outputSchema.getSchemaMap().get(OUTPUT_TYPE).toString();
            }
        }
        if (InputOutputDataType.JSON.toString().equals(outputType)) {
            fnName = JS_STRINGIFY + BRACKET_OPEN + fnName + BRACKET_CLOSE;
        }

        if (fnName != null) {
            return new JSFunction(fnName, jsFunctionBody);
        } else {
            throw new JSException("Could not find mapping JavaScript function.");
        }
    }

}
