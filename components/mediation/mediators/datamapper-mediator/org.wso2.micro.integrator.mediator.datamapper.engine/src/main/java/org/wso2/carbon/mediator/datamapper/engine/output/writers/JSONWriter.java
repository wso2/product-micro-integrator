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
package org.wso2.carbon.mediator.datamapper.engine.output.writers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.InvalidPayloadException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.WriterException;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.Schema;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.SchemaElement;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.*;

/**
 * This class implements {@link Writer} interface and json writer for data mapper engine using
 * Jackson
 */
public class JSONWriter implements Writer {

    private static final Log log = LogFactory.getLog(JSONWriter.class);
    private Schema outputSchema;
    private JsonGenerator jsonGenerator;
    private StringWriter writer;
    private List<SchemaElement> schemaElementList;

    public JSONWriter(Schema outputSchema) throws SchemaException, WriterException {
        this.outputSchema = outputSchema;
        this.writer = new StringWriter();
        this.schemaElementList = new ArrayList<>();
        this.schemaElementList.add(new SchemaElement(outputSchema.getName()));
        try {
            JsonFactory jsonFactory = new JsonFactory();
            this.jsonGenerator = jsonFactory.createGenerator(writer);
            writeStartAnonymousObject();
        } catch (IOException e) {
            throw new WriterException("Error while creating json generator. " + e.getMessage());
        }
    }

    @Override public void writeStartObject(String name) throws WriterException {
        try {
            String schemaName = name;
            if (name.endsWith(SCHEMA_ATTRIBUTE_PARENT_ELEMENT_POSTFIX)) {
                schemaName = name.substring(0, name.lastIndexOf(SCHEMA_ATTRIBUTE_PARENT_ELEMENT_POSTFIX));
            }
            schemaElementList.add(new SchemaElement(schemaName));
            String type = null;
            try {
                type = outputSchema.getElementTypeByName(schemaElementList);
            } catch (InvalidPayloadException | SchemaException e) {
                throw new WriterException(e.getMessage());
            }
            if (OBJECT_ELEMENT_TYPE.equals(type)) {
                jsonGenerator.writeObjectFieldStart(name);
            } else if (STRING_ELEMENT_TYPE.equals(type)) {
                jsonGenerator.writeObjectFieldStart(name);
            } else {
                jsonGenerator.writeArrayFieldStart(name);
                //start an object only if it is an object array
                if (!outputSchema.isCurrentArrayIsPrimitive()) {
                    jsonGenerator.writeStartObject();
                }
            }
        } catch (IOException e) {
            throw new WriterException("Error while creating starting object. " + e.getMessage());
        }
    }

    @Override public void writeField(String name, Object value) throws WriterException {
        try {
            if (value instanceof String) {
                jsonGenerator.writeStringField(name, (String) value);
            } else if (value instanceof Integer) {
                jsonGenerator.writeNumberField(name, (Integer) value);
            } else if (value instanceof Double) {
                jsonGenerator.writeNumberField(name, (Double) value);
            } else if (value instanceof Boolean) {
                jsonGenerator.writeBooleanField(name, (Boolean) value);
            }
        } catch (IOException e) {
            throw new WriterException("Error while creating writing field. " + e.getMessage());
        }
    }

    @Override public void writeEndObject(String objectName) throws WriterException {
        try {
            if ((!ARRAY_ELEMENT_TYPE.equals(outputSchema.getElementTypeByName(schemaElementList)) && !schemaElementList
                    .isEmpty()) && schemaElementList.get(schemaElementList.size() - 1).getElementName()
                    .equals(objectName)) {
                schemaElementList.remove(schemaElementList.size() - 1);
                jsonGenerator.writeEndObject();
            } else if (ARRAY_ELEMENT_TYPE.equals(outputSchema.getElementTypeByName(schemaElementList))) {
                if (!outputSchema.isCurrentArrayIsPrimitive()) {
                    jsonGenerator.writeEndObject();
                }
            }
        } catch (IOException | SchemaException | InvalidPayloadException e) {
            throw new WriterException("Error while creating ending object. " + e.getMessage());
        }
    }

    @Override public String terminateMessageBuilding() throws WriterException {
        String inputJSVariable = null;
        try {
            writeEndObject(null);
            jsonGenerator.close();
            inputJSVariable = writer.toString();
            writer.close();
            return inputJSVariable;
        } catch (IOException e) {
            throw new WriterException("Error while creating terminating message building. " + e.getMessage());
        }
    }

    @Override public void writeStartArray() {
    }

    @Override public void writeEndArray() throws WriterException {
        try {
            schemaElementList.remove(schemaElementList.size() - 1);
            jsonGenerator.writeEndArray();
        } catch (IOException e) {
            try {
                jsonGenerator.writeEndObject();
                jsonGenerator.writeEndArray();
            } catch (IOException e1) {
                throw new WriterException(e.getMessage());
            }
        }
    }

    @Override public void writeStartAnonymousObject() throws WriterException {
        try {
            jsonGenerator.writeStartObject();
        } catch (IOException e) {
            throw new WriterException("Error while creating anonymous object. " + e.getMessage());
        }
    }

    @Override public void writePrimitive(Object value) throws WriterException {

        try {
            if (value instanceof String) {
                jsonGenerator.writeString((String) value);
            } else if (value instanceof Boolean) {
                jsonGenerator.writeBoolean((Boolean) value);
            } else if (value instanceof Long) {
                jsonGenerator.writeNumber((Long) value);
            } else if (value instanceof Double) {
                jsonGenerator.writeNumber((Double) value);
            }
        } catch (IOException e) {
            throw new WriterException("Error while writing primitive. " + e.getMessage());
        }

    }
}
