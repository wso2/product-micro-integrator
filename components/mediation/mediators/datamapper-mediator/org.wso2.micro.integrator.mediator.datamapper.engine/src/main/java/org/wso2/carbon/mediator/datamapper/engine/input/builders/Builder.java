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

import java.io.IOException;

/**
 * Interface for implement the methods to create generic data holding model of data mapper engine
 */
public interface Builder {

    /**
     * Method for writing start marker of a Array value.
     * <p/>
     * <p/>
     * Array values can be written in any context where values
     * are allowed: meaning everywhere except for when
     * a field name is expected.
     */
    void writeStartArray() throws IOException;

    /**
     * Method for writing closing marker of a Array.
     * <p/>
     * <p/>
     * Marker can be written if the innermost structured type
     * is Array.
     */
    void writeEndArray() throws IOException;

    /**
     * Method for writing starting marker of a Object value.
     * <p/>
     * <p/>
     * Object values can be written in any context where values
     * are allowed: meaning everywhere except for when
     * a field name is expected.
     */
    void writeStartObject() throws IOException;

    /**
     * Method for writing closing marker of a Object value.
     * <p/>
     * <p/>
     * Marker can be written if the innermost structured type
     * is Object, and the last written event was either a
     * complete value, or START-OBJECT marker
     */
    void writeEndObject() throws IOException;

    /**
     * Method for writing a field name.
     * <p/>
     * <p/>
     * Field names can only be written in Object context , when field name is expected
     */
    void writeFieldName(String name) throws IOException;

    /**
     * Method for outputting a String value. Depending on context
     * this means either array element, (object) field value or
     * a stand alone String; but in all cases, String will be
     * surrounded in double quotes, and contents will be properly
     * escaped.
     */
    void writeString(String text) throws IOException;

    /**
     * Method that will output given chunk of binary data as base64
     * encoded, as a complete String value (surrounded by double quotes).
     * This method defaults
     * <p/>
     * Alternatively if linefeeds are not included,
     * resulting String value may violate the requirement of base64
     * RFC which mandates line-length of 76 characters and use of
     * linefeeds. However, all Parser implementations
     * are required to accept such "long line base64"; as do
     * typical production-level base64 decoders.
     */
    void writeBinary(byte[] data, int offset, int len) throws IOException;

    /**
     * Method for outputting given value as number.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     *
     * @param number Number value to write
     */
    void writeNumber(int number) throws IOException;

    /**
     * Method for outputting indicate JSON numeric value.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     *
     * @param number Number value to write
     */
    void writeNumber(double number) throws IOException;

    /**
     * Method for outputting literal boolean value (one of
     * Strings 'true' and 'false').
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     */
    void writeBoolean(boolean state) throws IOException;

    /*
    /**********************************************************
    /*Convenience field write methods
    /**********************************************************
     */

    /**
     * Convenience method for outputting a field entry ("member")
     * that has a String value. Equivalent to:
     * <pre>
     *  writeFieldName(fieldName);
     *  writeString(value);
     * </pre>
     */
    void writeStringField(String fieldName, String value) throws IOException;

    /**
     * Convenience method for outputting a field entry ("member")
     * that has a String value. Equivalent to:
     * <pre>
     *  writeFieldName(fieldName);
     *  writeString(value);
     * </pre>
     */
    void writeField(String fieldName, Object value, String fieldType) throws IOException;

    /**
     * Convenience method for outputting a field entry ("member")
     * that has a boolean value. Equivalent to:
     * <pre>
     *  writeFieldName(fieldName);
     *  writeBoolean(value);
     * </pre>
     */
    void writeBooleanField(String fieldName, boolean value) throws IOException;

    /**
     * Convenience method for outputting a field entry ("member")
     * that has the specified numeric value. Equivalent to:
     * <pre>
     *  writeFieldName(fieldName);
     *  writeNumber(value);
     * </pre>
     */
    void writeNumberField(String fieldName, int value) throws IOException;

    /**
     * Convenience method for outputting a field entry ("member")
     * that has the specified numeric value. Equivalent to:
     * <pre>
     *  writeFieldName(fieldName);
     *  writeNumber(value);
     * </pre>
     */
    void writeNumberField(String fieldName, double value) throws IOException;

    /**
     * Convenience method for outputting a field entry ("member")
     * that contains specified data in base64-encoded form.
     * Equivalent to:
     * <pre>
     *  writeFieldName(fieldName);
     *  writeBinary(value);
     * </pre>
     */
    void writeBinaryField(String fieldName, byte[] data) throws IOException;

    /**
     * Convenience method for outputting a field entry ("member")
     * (that will contain a JSON Array value), and the START_ARRAY marker.
     * Equivalent to:
     * <pre>
     *  writeFieldName(fieldName);
     *  writeStartArray();
     * </pre>
     * <p/>
     * Note: caller still has to take care to close the array
     * (by calling {#link #writeEndArray}) after writing all values
     * of the value Array.
     */
    void writeArrayFieldStart(String fieldName) throws IOException;

    /**
     * Convenience method for outputting a field entry ("member")
     * (that will contain a JSON Object value), and the START_OBJECT marker.
     * Equivalent to:
     * <pre>
     *  writeFieldName(fieldName);
     *  writeStartObject();
     * </pre>
     * <p/>
     * Note: caller still has to take care to close the Object
     * (by calling {#link #writeEndObject}) after writing all
     * entries of the value Object.
     */
    void writeObjectFieldStart(String fieldName) throws IOException;

    /**
     * Method called to close builder, so that no more content
     * can be written.
     */
    void close() throws IOException;

    /**
     * Methid called to get the final content after closing the builder
     *
     * @return built content
     * @throws IOException
     */
    String getContent() throws IOException;

    /**
     * Convenience method for outputting a primitive
     * that has a primitive value.
     */
    void writePrimitive(Object value, String fieldType) throws IOException;

}
