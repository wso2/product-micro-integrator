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
package org.wso2.carbon.mediator.datamapper.engine.core.schemas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.InvalidPayloadException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.ARRAY_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.OBJECT_ELEMENT_TYPE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.*;

/**
 * This class implements {@link Schema} interface using Jackson JSON library to hold JSON schema
 */
public class JacksonJSONSchema implements Schema {

    private static final Log log = LogFactory.getLog(JacksonJSONSchema.class);
    private static final String EMPTY_STRING = "";
    private static final String NAMESPACE_NAME_CONCAT_STRING = ":";
    private Map jsonSchemaMap;

    private static final String PROPERTIES_KEY = "properties";
    private static final String ATTRIBUTES_KEY = "attributes";
    private static final String NAMESPACE_KEY = "namespaces";
    private static final String PREFIX_KEY = "prefix";
    private static final String URL_KEY = "url";
    private static final String TYPE_KEY = "type";
    private static final String TITLE_KEY = "title";
    private static final String ITEMS_KEY = "items";
    private Map<String, String> namespaceMap;
    private Map<String, String> prefixMap;
    private boolean currentArrayIsPrimitive;

    public JacksonJSONSchema(InputStream inputSchema) throws SchemaException {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            jsonSchemaMap = objectMapper.readValue(inputSchema, Map.class);
        } catch (IOException e) {
            throw new SchemaException("Error while reading input stream. " + e.getMessage());
        }
        initNamespaceMap();
    }

    /**
     * populating name-space value map
     */
    private void initNamespaceMap() {
        namespaceMap = new HashMap<>();
        prefixMap = new HashMap<>();
        ArrayList<Map> namespaceElementArray = (ArrayList<Map>) jsonSchemaMap.get(NAMESPACE_KEY);
        if (namespaceElementArray != null) {
            for (Map namespaceObject : namespaceElementArray) {
                String urlValue = (String) namespaceObject.get(URL_KEY);
                if (!namespaceMap.containsKey(urlValue)) {
                    namespaceMap.put(urlValue, (String) namespaceObject.get(PREFIX_KEY));
                } else {
                    //multiple prefixes exists for this namespace URI. hence adding all prefixes that related to same
                    // namespace separating by commas
                    //adding comma separated prefix string is looks UGLY :-/, BUT can't help .... !, trying to do with
                    //minimum impact existing mechanism
                    namespaceMap.put(urlValue, (namespaceMap.get(urlValue) + PREFIX_LIST_SEPERATOR + namespaceObject.get(PREFIX_KEY)));
                }

                if (!prefixMap.containsKey((String) namespaceObject.get(PREFIX_KEY))) {
                    prefixMap.put((String) namespaceObject.get(PREFIX_KEY), urlValue);
                }
            }
        }
    }

    @Override public String getName() throws SchemaException {
        String schemaName = (String) jsonSchemaMap.get(TITLE_KEY);
        if (schemaName != null) {
            return schemaName;
        } else {
            throw new SchemaException("Invalid WSO2 Data Mapper JSON input schema, schema name not found.");
        }
    }

    @Override public String getElementTypeByName(List<SchemaElement> elementStack)
            throws InvalidPayloadException, SchemaException {
        Map<String, Object> schema = jsonSchemaMap;
        String elementType = null;
        boolean elementFound = false;
        for (SchemaElement element : elementStack) {
            elementFound = false;
            String elementName = element.getElementName();
            String elementNamespace = element.getNamespace();
            if (elementName.equals(getName())) {
                schema = (Map<String, Object>) jsonSchemaMap.get(PROPERTIES_KEY);
                elementType = (String) jsonSchemaMap.get(TYPE_KEY);
                if (ARRAY_ELEMENT_TYPE.equals(elementType)) {
                    setCurrentArrayType(schema, elementName);
                }
                elementFound = true;
            } else if (schema.containsKey(elementName)) {
                elementType = (String) ((Map<String, Object>) schema.get(elementName)).get(TYPE_KEY);
                if (ARRAY_ELEMENT_TYPE.equals(elementType)) {
                    setCurrentArrayType(schema, elementName);
                    schema = getSchemaItems((Map<String, Object>) schema.get(elementName));
                    schema = getSchemaProperties(schema);
                } else if (OBJECT_ELEMENT_TYPE.equals(elementType)) {
                    schema = getSchemaProperties((Map<String, Object>) schema.get(elementName));
                } else {
                    schema = getSchemaProperties((Map<String, Object>) schema.get(elementName));
                }
                elementFound = true;
            }
            if (!elementFound) {
                elementType = NULL_ELEMENT_TYPE;
                log.warn("Element name not found : " + elementName);
            }
        }
        return elementType;
    }

    private void setCurrentArrayType(Map<String, Object> schema, String elementName) {
        Map<String, Object> tempSchema = getSchemaItems((Map<String, Object>) schema.get(elementName));
        if (OBJECT_ELEMENT_TYPE.equals(tempSchema.get(TYPE_KEY))) {
            currentArrayIsPrimitive = false;
        } else if (STRING_ELEMENT_TYPE.equals(tempSchema.get(TYPE_KEY)) || BOOLEAN_ELEMENT_TYPE
                .equals(tempSchema.get(TYPE_KEY)) || NUMBER_ELEMENT_TYPE.equals(tempSchema.get(TYPE_KEY))
                || INTEGER_ELEMENT_TYPE.equals(tempSchema.get(TYPE_KEY))) {
            currentArrayIsPrimitive = true;
        }
    }

    public String getElementTypeByName(String elementName) throws SchemaException {
        String elementType = null;
        Map<String, Object> properties = getSchemaProperties(jsonSchemaMap);
        if (getName().equals(elementName)) {
            return (String) jsonSchemaMap.get(TYPE_KEY);
        } else if (properties.containsKey(elementName)) {
            return getSchemaType((Map<String, Object>) properties.get(elementName));
        } else {
            Iterator<Map.Entry<String, Object>> entryIterator = properties.entrySet().iterator();
            while (entryIterator.hasNext()) {
                Map<String, Object> subSchema = (Map<String, Object>) entryIterator.next().getValue();
                String schemaType = getSchemaType(subSchema);
                if (OBJECT_ELEMENT_TYPE.equals(schemaType)) {
                    elementType = getElementTypeByName(elementName, subSchema);
                } else if (ARRAY_ELEMENT_TYPE.equals(schemaType)) {
                    elementType = getElementTypeByName(elementName, getSchemaItems(subSchema));
                }
                if (elementType != null) {
                    return elementType;
                }
            }
            return null;
        }
    }

    @Override public boolean isChildElement(String elementName, String childElementName) {
        Map<String, Object> elementSchema = getElementSchemaByName(elementName, jsonSchemaMap);
        if (elementSchema.containsKey(PROPERTIES_KEY)) {
            if (getSchemaProperties(elementSchema).containsKey(childElementName)) {
                return true;
            }
        } else {
            if (((Map<String, Object>) getSchemaItems(elementSchema).get(PROPERTIES_KEY))
                    .containsKey(childElementName)) {
                return true;
            }
        }
        return false;
    }

    @Override public boolean isChildElement(List<SchemaElement> elementStack, String childElementName)
            throws InvalidPayloadException, SchemaException {
        Map<String, Object> elementSchema = getElementSchemaByName(elementStack, jsonSchemaMap);
        if (elementSchema.containsKey(PROPERTIES_KEY)) {
            if (getSchemaProperties(elementSchema).containsKey(childElementName)) {
                return true;
            }
        } else if (elementSchema.containsKey(ITEMS_KEY)) {
            if (((Map<String, Object>) getSchemaItems(elementSchema).get(PROPERTIES_KEY))
                    .containsKey(childElementName)) {
                return true;
            }
        } else {
            if (elementSchema.containsKey(childElementName)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, String> getNamespaceMap() {
        return namespaceMap;
    }

    public Map<String, String> getPrefixMap() {
        return prefixMap;
    }

    private Map<String, Object> getElementSchemaByName(List<SchemaElement> elementStack, Map<String, Object> schema)
            throws InvalidPayloadException, SchemaException {
        Map<String, Object> tempSchema = schema;
        String elementType = null;
        for (SchemaElement element : elementStack) {
            String elementName = element.getElementName();
            String elementNamespace = element.getNamespace();
            elementName = getNamespaceAddedFieldName(elementNamespace, elementName);
            if (elementName.equals(getName())) {
                tempSchema = (Map<String, Object>) jsonSchemaMap.get(PROPERTIES_KEY);
                elementType = (String) jsonSchemaMap.get(TYPE_KEY);
            } else if (tempSchema.containsKey(elementName)) {
                elementType = (String) ((Map<String, Object>) tempSchema.get(elementName)).get(TYPE_KEY);
                if (ARRAY_ELEMENT_TYPE.equals(elementType)) {
                    tempSchema = getSchemaItems((Map<String, Object>) tempSchema.get(elementName));
                    tempSchema = getSchemaProperties(tempSchema);
                } else if (OBJECT_ELEMENT_TYPE.equals(elementType)) {
                    tempSchema = getSchemaProperties((Map<String, Object>) tempSchema.get(elementName));
                } else {
                    tempSchema = getSchemaProperties((Map<String, Object>) tempSchema.get(elementName));
                }
            }
        }
        return tempSchema;
    }

    private Map<String, Object> getElementSchemaByName(String elementName, Map<String, Object> schema) {
        Map<String, Object> elementType = null;
        Map<String, Object> properties = getSchemaProperties(schema);
        if (properties.containsKey(elementName)) {
            return (Map<String, Object>) properties.get(elementName);
        } else {
            Iterator<Map.Entry<String, Object>> entryIterator = properties.entrySet().iterator();
            while (entryIterator.hasNext()) {
                Map<String, Object> subSchema = (Map<String, Object>) entryIterator.next().getValue();
                String schemaType = getSchemaType(subSchema);
                if (OBJECT_ELEMENT_TYPE.equals(schemaType)) {
                    elementType = getElementSchemaByName(elementName, subSchema);

                } else if (ARRAY_ELEMENT_TYPE.equals(schemaType)) {
                    elementType = getElementSchemaByName(elementName, getSchemaItems(subSchema));
                }
                if (elementType != null) {
                    return elementType;
                }
            }
            return null;
        }
    }

    private String getElementTypeByName(String elementName, Map<String, Object> schema) {
        String elementType = null;
        Map<String, Object> properties = getSchemaProperties(schema);
        if (properties.containsKey(elementName)) {
            return getSchemaType((Map<String, Object>) properties.get(elementName));
        } else {
            Iterator<Map.Entry<String, Object>> entryIterator = properties.entrySet().iterator();
            while (entryIterator.hasNext()) {
                Map<String, Object> subSchema = (Map<String, Object>) entryIterator.next().getValue();
                String schemaType = getSchemaType(subSchema);
                if (OBJECT_ELEMENT_TYPE.equals(schemaType)) {
                    elementType = getElementTypeByName(elementName, subSchema);
                } else if (ARRAY_ELEMENT_TYPE.equals(schemaType)) {
                    elementType = getElementTypeByName(elementName, getSchemaItems(subSchema));
                } else {
                    elementType = getElementTypeByName(elementName, subSchema);
                }
                if (elementType != null) {
                    return elementType;
                }
            }
        }
        return null;
    }

    private Map<String, Object> getSchemaProperties(Map<String, Object> schema) {
        Map<String, Object> nextSchema = new HashMap<>();
        if (schema.containsKey(PROPERTIES_KEY)) {
            nextSchema.putAll((Map<? extends String, Object>) schema.get(PROPERTIES_KEY));
        }
        if (schema.containsKey(ATTRIBUTES_KEY)) {
            nextSchema.putAll((Map<? extends String, Object>) schema.get(ATTRIBUTES_KEY));
        }
        return nextSchema;
    }

    public Map<String, Object> getSchemaItems(Map<String, Object> schema) {
        Map<String, Object> nextSchema = new HashMap<>();
        if (schema.containsKey(ITEMS_KEY)) {
            Object propertyList = schema.get(ITEMS_KEY);
            if (propertyList instanceof Map) {
                nextSchema.putAll((Map<? extends String, Object>) propertyList);
                if (nextSchema.containsKey(ATTRIBUTES_KEY)) {
                    nextSchema.putAll((Map<? extends String, Object>) nextSchema.get(ATTRIBUTES_KEY));
                }
            } else {
                nextSchema.putAll((Map<? extends String, Object>) ((ArrayList) propertyList).get(0));
            }
        } else {
            throw new IllegalArgumentException("Given schema does not contain value under key : " + ITEMS_KEY);
        }
        if (schema.containsKey(ATTRIBUTES_KEY)) {
            nextSchema.putAll((Map<? extends String, Object>) schema.get(ATTRIBUTES_KEY));
        }
        return nextSchema;
    }

    private String getSchemaType(Map<String, Object> schema) {
        if (schema.containsKey(TYPE_KEY)) {
            Object type = schema.get(TYPE_KEY);
            if (type instanceof String) {
                return (String) type;
            } else {
                throw new IllegalArgumentException("Illegal format " + type.getClass() + " value found under key : " +
                        TYPE_KEY);
            }
        } else {
            throw new IllegalArgumentException("Given schema does not contain value under key : " + TYPE_KEY);
        }
    }

    @Override public String getPrefixForNamespace(String url) {
        if (EMPTY_STRING.equals(url)) {
            return EMPTY_STRING;
        } else if (namespaceMap.containsKey(url)) {
            return namespaceMap.get(url);
        } else {
            return null;
        }
    }

   @Override
   public boolean isCurrentArrayIsPrimitive() {
       return currentArrayIsPrimitive;
    }

    private String getNamespaceAddedFieldName(String uri, String localName) throws InvalidPayloadException {
        if (uri != null) {
            String prefix = getPrefixForNamespace(uri);
            if (StringUtils.isNotEmpty(prefix)) {
                return prefix + NAMESPACE_NAME_CONCAT_STRING + localName;
            } else if (prefix != null) {
                return localName;
            } else {
                throw new InvalidPayloadException(uri + " name-space is not defined in the schema with element " +
                        localName);
            }
        }
        return localName;
    }

    @Override
    public Map getSchemaMap() {
        return jsonSchemaMap;
    }
}
