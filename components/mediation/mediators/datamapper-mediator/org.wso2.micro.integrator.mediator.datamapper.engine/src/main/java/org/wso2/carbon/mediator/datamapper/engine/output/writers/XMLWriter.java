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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.WriterException;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.Schema;
import org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.ENCODE_CHAR_HYPHEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.HYPHEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants
        .SCHEMA_XML_ELEMENT_TEXT_VALUE_FIELD;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.SCHEMA_ATTRIBUTE_FIELD_PREFIX;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants
        .SCHEMA_ATTRIBUTE_PARENT_ELEMENT_POSTFIX;

/**
 * This class implements {@link Writer} interface and xml writer for data mapper engine using StAX
 */
public class XMLWriter implements Writer {

    private static final Log log = LogFactory.getLog(XMLWriter.class);
    private StringWriter stringWriter;
    private XMLStreamWriter xmlStreamWriter;
    private Schema outputSchema;
    private Stack<String> arrayElementStack;
    private String latestElementName;
    private Map<String, String> namespaceMap;
    private Map<String, String> nsPrefixToUriMap;
    private static final String NAMESPACE_SEPARATOR = "_";
    private static final String XSI_TYPE_IDENTIFIER = "_xsi_type_";


    public XMLWriter(Schema outputSchema) throws SchemaException, WriterException {
        this.outputSchema = outputSchema;
        this.arrayElementStack = new Stack<>();
        this.stringWriter = new StringWriter();
        init(outputSchema);
    }

    private void init(Schema outputSchema) throws SchemaException, WriterException {
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        try {
            xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(stringWriter);
            //creating root element of the xml message
            namespaceMap = outputSchema.getNamespaceMap();
            nsPrefixToUriMap = outputSchema.getPrefixMap();
            writeStartElement(outputSchema.getName(), xmlStreamWriter);
            Iterator<Map.Entry<String, String>> namespaceEntryIterator = nsPrefixToUriMap.entrySet().iterator();
            while (namespaceEntryIterator.hasNext()) {
                Map.Entry<String, String> entry = namespaceEntryIterator.next();
                xmlStreamWriter.writeNamespace(entry.getKey(), entry.getValue());
            }
        } catch (XMLStreamException e) {
            throw new WriterException("Error while creating xml output factory. " + e.getMessage());
        }
    }

    @Override public void writeStartObject(String name) throws WriterException {
        try {
            name = decodeSpecialChars(name);
            if (name.endsWith(SCHEMA_ATTRIBUTE_PARENT_ELEMENT_POSTFIX)) {
                latestElementName = name.substring(0, name.lastIndexOf(SCHEMA_ATTRIBUTE_PARENT_ELEMENT_POSTFIX));
                writeStartElement(latestElementName, xmlStreamWriter);
            } else {
                writeStartElement(name, xmlStreamWriter);
                latestElementName = name;
            }
        } catch (XMLStreamException e) {
            throw new WriterException(e.getMessage());
        }
    }

    @Override public void writeField(String name, Object fieldValue) throws WriterException {
        try {
            name = decodeSpecialChars(name);
            //with in a element attributes must come first before any of other field values
            if (fieldValue != null) {
                String value = getFieldValueAsString(fieldValue);
                if (name.contains(SCHEMA_ATTRIBUTE_FIELD_PREFIX)) {
                    String attributeNameWithNamespace = name.replaceFirst(SCHEMA_ATTRIBUTE_FIELD_PREFIX, "");
                    if (attributeNameWithNamespace.contains("_")) {
                        String[] attributeNameArray = attributeNameWithNamespace.split("_");
                        if (nsPrefixToUriMap.containsKey(attributeNameArray[0])) {
                            xmlStreamWriter.writeAttribute(attributeNameArray[0],
                                    nsPrefixToUriMap.get(attributeNameArray[0]),
                                    attributeNameArray[attributeNameArray.length - 1], value);
                        } else {
                            xmlStreamWriter.writeAttribute(attributeNameWithNamespace, value);
                        }
                    } else {
                        xmlStreamWriter.writeAttribute(attributeNameWithNamespace, value);
                    }
                } else if (name.equals(SCHEMA_XML_ELEMENT_TEXT_VALUE_FIELD)){
                    xmlStreamWriter.writeCharacters(value);
                } else {
                    writeStartElement(name, xmlStreamWriter);
                    xmlStreamWriter.writeCharacters(value);
                    xmlStreamWriter.writeEndElement();
                }
            }
        } catch (XMLStreamException e) {
            throw new WriterException(e.getMessage());
        }
    }

    private String getFieldValueAsString(Object fieldValue) {
        if (fieldValue instanceof String) {
            return (String) fieldValue;
        } else if (fieldValue instanceof Integer) {
            return Integer.toString((Integer) fieldValue);
        } else if (fieldValue instanceof Double) {
            return Double.toString((Double) fieldValue);
        } else if (fieldValue instanceof Boolean) {
            return Boolean.toString((Boolean) fieldValue);
        }
        throw new IllegalArgumentException("Unsupported value type found" + fieldValue.toString());
    }

    @Override public void writeEndObject(String objectName) throws WriterException {
        try {
            xmlStreamWriter.writeEndElement();
        } catch (XMLStreamException e) {
            throw new WriterException(e.getMessage());
        }
    }

    @Override public String terminateMessageBuilding() throws WriterException {
        try {
            xmlStreamWriter.writeEndElement();
            xmlStreamWriter.flush();
            xmlStreamWriter.close();
            return stringWriter.getBuffer().toString();
        } catch (XMLStreamException e) {
            throw new WriterException(e.getMessage());
        }
    }

    @Override public void writeStartArray() {
        arrayElementStack.push(latestElementName);
    }

    @Override public void writeEndArray() {
        arrayElementStack.pop();
    }

    @Override public void writeStartAnonymousObject() throws WriterException {
        try {
            writeStartElement(arrayElementStack.peek(), xmlStreamWriter);
        } catch (XMLStreamException e) {
            throw new WriterException(e.getMessage());
        }
    }

    @Override public void writePrimitive(Object value) throws WriterException {
        try {
            xmlStreamWriter.writeCharacters(getFieldValueAsString(value));
        } catch (XMLStreamException e) {
            throw new WriterException(e.getMessage());
        }
    }

    private void writeStartElement(String name, XMLStreamWriter xMLStreamWriter) throws XMLStreamException {
        name = decodeSpecialChars(name);
        String prefix = name.split(NAMESPACE_SEPARATOR)[0];
        if (nsPrefixToUriMap.containsKey(prefix)) {
            if (name.contains(DataMapperEngineConstants.NAME_SEPERATOR)) {
                name = name.split(DataMapperEngineConstants.NAME_SEPERATOR)[0];
            }
            name = name.replaceFirst(prefix + NAMESPACE_SEPARATOR, "");
            xMLStreamWriter.writeStartElement(prefix, name, nsPrefixToUriMap.get(prefix));
        } else if (name.contains(DataMapperEngineConstants.NAME_SEPERATOR)) {
            name = name.split(DataMapperEngineConstants.NAME_SEPERATOR)[0];
            xMLStreamWriter.writeStartElement(name);
        } else {
            xMLStreamWriter.writeStartElement(name);
        }
    }

    /**
     * Function to decode special characters used data mapper configuration
     * @param str
     * @return
     */
    private String decodeSpecialChars(String str) {
        return str.replace(ENCODE_CHAR_HYPHEN, HYPHEN);
    }

}
