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
package org.wso2.carbon.mediator.datamapper.engine.input.readers;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMXMLBuilderFactory;
import org.apache.axiom.om.OMXMLParserWrapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.JSException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.ReaderException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.Schema;
import org.wso2.carbon.mediator.datamapper.engine.input.InputBuilder;
import org.wso2.carbon.mediator.datamapper.engine.input.builders.JSONBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.BOOLEAN_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.INTEGER_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.ITEMS_KEY;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.NUMBER_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.PROPERTIES_KEY;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.STRING_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.TYPE_KEY;

/**
 * This class is responsible for generating the JSON message for the given CSV message
 */
public class CSVInputReader implements InputReader {

    private static final Log log = LogFactory.getLog(CSVInputReader.class);
    /* JSON schema for input message */
    private Map jsonSchema;
    /* JSON schema of the input message */
    private Schema inputSchema;
    /* JSON message builder instance */
    private JSONBuilder jsonBuilder;
    /* Reference of the InputBuilder object to send the built JSON message */
    private InputBuilder messageBuilder;

    /**
     * Constructor
     *
     * @throws IOException
     */
    public CSVInputReader() throws IOException {
        this.jsonBuilder = new JSONBuilder();
    }

    @Override
    public void read(InputStream input, Schema inputSchema, InputBuilder messageBuilder) throws ReaderException {
        this.inputSchema = inputSchema;
        this.jsonSchema = inputSchema.getSchemaMap();
        this.messageBuilder = messageBuilder;
        OMXMLParserWrapper parserWrapper = OMXMLBuilderFactory.createOMBuilder(input);
        OMElement root = parserWrapper.getDocumentElement();
        String csvContent = getCSVContent(root);
        try {
            populateCSVContents(csvContent, jsonSchema);
        } catch (IOException | SchemaException | JSException e) {
            throw new ReaderException("Error while parsing CSV input stream. " + e.getMessage());
        }
    }

    /**
     * Populate CSV content to a JSON message
     *
     * @param csvContent    CSV content extracted from the input message
     * @param jsonSchemaMap respective JSON schema
     * @throws IOException
     * @throws ReaderException
     * @throws SchemaException
     * @throws JSException
     */
    private void populateCSVContents(String csvContent, Map jsonSchemaMap)
            throws IOException, ReaderException, SchemaException, JSException {
        if (csvContent == null) {
            throw new ReaderException(
                    "Request csv data not found. The csv records should contain in a <text></text> tag.");
        }

        Map<String, Object> fieldMap;
        List<String> fieldNamesList;
        CSVReader csvReader = new CSVReader(new StringReader(csvContent));
        List<String[]> allData = csvReader.readAll();

        if (!allData.isEmpty()) {
            /* Retrieve field names from the JSON schema */
            fieldMap = (Map<String, Object>) ((Map<String, Object>) ((ArrayList) jsonSchemaMap.get(ITEMS_KEY)).get(0))
                    .get(PROPERTIES_KEY);
            fieldNamesList = new ArrayList<>(fieldMap.keySet());
            jsonBuilder.writeStartArray();

            for (String[] items : allData) {
                jsonBuilder.writeStartObject();
                for (int i = 0; i < items.length; i++) {
                    writeFieldElement(fieldNamesList.get(i), items[i],
                                      getElementTypeByName(fieldNamesList.get(i), fieldMap));
                }
                jsonBuilder.writeEndObject();
            }
            jsonBuilder.writeEndArray();
        }
        writeTerminateElement();
    }

    /**
     * Extract CSV content from the input message
     *
     * @param element root element
     * @return CSV content as text
     */
    private String getCSVContent(OMElement element) {
        if (element == null) {
            log.warn("No CSV content found in the input file");
        }

        if (element.getLocalName().equals("text")) {
            return element.getText();
        }

        Iterator<OMElement> it = element.getChildElements();
        if (it.hasNext()) {
            getCSVContent(it.next());
        }

        return null;
    }

    private String getElementTypeByName(String elementName, Map elementMap) {
        String elementType = (String) ((Map) elementMap.get(elementName)).get(TYPE_KEY);
        return elementType;
    }

    public Schema getInputSchema() {
        return inputSchema;
    }

    private void writeFieldElement(String fieldName, String valueString, String fieldType)
            throws IOException, JSException, SchemaException, ReaderException {
        switch (fieldType) {
        case STRING_ELEMENT_TYPE:
            jsonBuilder.writeField(fieldName, valueString, fieldType);
            break;
        case BOOLEAN_ELEMENT_TYPE:
            jsonBuilder.writeField(fieldName, Boolean.parseBoolean(valueString), fieldType);
            break;
        case NUMBER_ELEMENT_TYPE:
            jsonBuilder.writeField(fieldName, Double.parseDouble(valueString), fieldType);
            break;
        case INTEGER_ELEMENT_TYPE:
            jsonBuilder.writeField(fieldName, Integer.parseInt(valueString), fieldType);
            break;
        default:
            jsonBuilder.writeField(fieldName, valueString, fieldType);

        }
    }

    private void writeTerminateElement() throws IOException, JSException, SchemaException, ReaderException {
        jsonBuilder.close();
        String jsonBuiltMessage = jsonBuilder.getContent();
        messageBuilder.notifyWithResult(jsonBuiltMessage);
    }
}