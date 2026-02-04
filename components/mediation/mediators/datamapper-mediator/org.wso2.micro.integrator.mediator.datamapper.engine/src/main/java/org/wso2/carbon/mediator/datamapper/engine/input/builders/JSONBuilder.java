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
package org.wso2.carbon.mediator.datamapper.engine.input.builders;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.StringWriter;

import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.BOOLEAN_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.INTEGER_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.NUMBER_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.STRING_ELEMENT_TYPE;

/**
 * This class implements {@link Builder} interface and JSON builder for data mapper engine using
 * jackson
 */
public class JSONBuilder implements Builder {

    private JsonGenerator jsonGenerator;
    private StringWriter writer;

    public JSONBuilder() throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        writer = new StringWriter();
        jsonGenerator = jsonFactory.createGenerator(writer);
    }

    @Override public void writeStartArray() throws IOException {
        jsonGenerator.writeStartArray();
    }

    @Override public void writeEndArray() throws IOException {
        jsonGenerator.writeEndArray();
    }

    @Override public void writeStartObject() throws IOException {
        jsonGenerator.writeStartObject();
    }

    @Override public void writeEndObject() throws IOException {
        jsonGenerator.writeEndObject();
    }

    @Override public void writeFieldName(String name) throws IOException {
        jsonGenerator.writeFieldName(name);
    }

    @Override public void writeString(String text) throws IOException {
        jsonGenerator.writeString(text);
    }

    @Override public void writeBinary(byte[] data, int offset, int len) throws IOException {
        jsonGenerator.writeBinary(data, offset, len);
    }

    @Override public void writeNumber(int number) throws IOException {
        jsonGenerator.writeNumber(number);
    }

    @Override public void writeNumber(double number) throws IOException {
        jsonGenerator.writeNumber(number);
    }

    @Override public void writeBoolean(boolean state) throws IOException {
        jsonGenerator.writeBoolean(state);
    }

    @Override public void writeStringField(String fieldName, String value) throws IOException {
        writeFieldName(fieldName);
        writeString(value);
    }

    @Override public void writeField(String fieldName, Object value, String fieldType) throws IOException {
        switch (fieldType) {
            case STRING_ELEMENT_TYPE:
                writeStringField(fieldName, (String) value);
                break;
            case BOOLEAN_ELEMENT_TYPE:
                writeBooleanField(fieldName, (Boolean) value);
                break;
            case NUMBER_ELEMENT_TYPE:
                writeNumberField(fieldName, (Double) value);
                break;
            case INTEGER_ELEMENT_TYPE:
                writeNumberField(fieldName, (Long) value);
                break;
            default:
                writeStringField(fieldName, (String) value);
        }
    }

    @Override public void writeBooleanField(String fieldName, boolean value) throws IOException {
        writeFieldName(fieldName);
        writeBoolean(value);
    }

    @Override public void writeNumberField(String fieldName, int value) throws IOException {
        writeFieldName(fieldName);
        writeNumber(value);
    }

    @Override public void writeNumberField(String fieldName, double value) throws IOException {
        writeFieldName(fieldName);
        writeNumber(value);
    }

    @Override public void writeBinaryField(String fieldName, byte[] data) throws IOException {
        writeFieldName(fieldName);
        writeBinary(data, 0, 0);
    }

    @Override public void writeArrayFieldStart(String fieldName) throws IOException {
        writeFieldName(fieldName);
        writeStartArray();
    }

    @Override public void writeObjectFieldStart(String fieldName) throws IOException {
        writeFieldName(fieldName);
        writeStartObject();
    }

    @Override public void close() throws IOException {
        jsonGenerator.close();
    }

    @Override public void writePrimitive(Object value, String fieldType) throws IOException {
        switch (fieldType) {
            case STRING_ELEMENT_TYPE:
                writeString((String) value);
                break;
            case BOOLEAN_ELEMENT_TYPE:
                writeBoolean((Boolean) value);
                break;
            case NUMBER_ELEMENT_TYPE:
                writeNumber((Double) value);
                break;
            case INTEGER_ELEMENT_TYPE:
                writeNumber((Long) value);
                break;
            default:
                writeString((String) value);
        }
    }

    public String getContent() throws IOException {
        String inputJSVariable = writer.toString();
        writer.close();
        return inputJSVariable;
    }

}
