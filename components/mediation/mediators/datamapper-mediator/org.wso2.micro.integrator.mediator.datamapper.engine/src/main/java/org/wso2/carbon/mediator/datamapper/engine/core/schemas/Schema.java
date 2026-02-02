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

import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.InvalidPayloadException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;

import java.util.List;
import java.util.Map;

/**
 * Interface to represent schema in data mapper engine.
 */
public interface Schema {

    /**
     * Method for get defined name of the schema
     *
     * @return Name of the schema as a String
     */
    String getName() throws SchemaException;

    /**
     * Method to get the element type specified in the schema by giving the element hierarchy
     *
     * @param elementStack
     * @return type of the element
     */
    String getElementTypeByName(List<SchemaElement> elementStack) throws InvalidPayloadException, SchemaException;

    String getElementTypeByName(String elementStack) throws InvalidPayloadException, SchemaException;

    /**
     * Method for check whether schema has a child element inside given element
     *
     * @return
     */
    boolean isChildElement(String elementName, String childElementName);

    boolean isChildElement(List<SchemaElement> elementStack, String childElementName)
            throws InvalidPayloadException, SchemaException;

    String getPrefixForNamespace(String url);

    Map<String, String> getNamespaceMap();

    Map<String, String> getPrefixMap();

    boolean isCurrentArrayIsPrimitive();

    Map getSchemaMap();
}
