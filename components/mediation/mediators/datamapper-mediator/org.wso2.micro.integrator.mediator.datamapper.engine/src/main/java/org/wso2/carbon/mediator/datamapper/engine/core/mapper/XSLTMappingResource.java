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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.EMPTY_STRING;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.FIRST_ELEMENT_OF_THE_INPUT;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.NOT_XSLT_COMPATIBLE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.PARAMETER_FILE_ROOT;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.PROPERTY_SEPERATOR;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.RUN_TIME_PROPERTIES;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.XSLT_COMPATIBLE_DEFAULT;

/**
 * This class contains required resources for the xslt transformation
 */
public class XSLTMappingResource {

    private final Map<String, String> runTimeProperties;
    private String name;
    private String content;
    private boolean xsltCompatible;


    public XSLTMappingResource(String content) throws SAXException,
                                                      IOException,
                                                      ParserConfigurationException {
        this.content = content;
        this.runTimeProperties = new HashMap<>();
        Document document = getDocument();
        xsltCompatible = processConfigurationDetails(document);
        if (!xsltCompatible) {
            this.content = null;
        }
    }

    /**
     * Create a input stream from the available content
     *
     * @return Input stream of the xslt stylesheet
     */
    InputStream getInputStream() {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a input source from the available content
     *
     * @return Input source of the xslt stylesheet
     */
    private InputSource getInputSource() {
        return new InputSource(new StringReader(content));
    }

    /**
     * Creating a document to process the xslt stylesheet
     *
     * @return document of the xslt stylesheet
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    private Document getDocument() throws SAXException, IOException,
                                          ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(getInputSource());
    }

    /**
     * Process configuration details included in the xslt stylesheet
     *
     * @param document xslt stylesheet as a document
     * @return return whether the xslt transformation possible or not
     */
    private boolean processConfigurationDetails(Document document) {
        boolean compatible = false;
        Node rootNode = document.getElementsByTagName(PARAMETER_FILE_ROOT).item(0);
        for (int j = 0; j < rootNode.getAttributes().getLength(); j++) {
            Node propertyNode = rootNode.getAttributes().item(j);

            // Process configuration details passed as attributes
            switch (propertyNode.getNodeName()) {
                case RUN_TIME_PROPERTIES:
                    //Runtime properties are passed as "propertyName1,propertyScope1,proertyName2,propertyScope2, .."
                    //Property name followed by its scope, separated by a comma
                    String runTimePropertyString = propertyNode.getNodeValue();
                    if (!EMPTY_STRING.equals(runTimePropertyString)) {
                        //first element of the array is a property name followed by its scope.
                        String[] properties = runTimePropertyString.split(PROPERTY_SEPERATOR);
                        int currentIndex = 0;
                        while (currentIndex < properties.length) {
                            //adding property name and its scope to the runTimeProperties map
                            runTimeProperties.put(properties[currentIndex],
                                                  properties[currentIndex + 1]);
                            currentIndex += 2;
                        }
                    }
                    break;
                case NOT_XSLT_COMPATIBLE:
                    if (propertyNode.getNodeValue().equals(XSLT_COMPATIBLE_DEFAULT)) {
                        compatible = true;
                    } else {
                        return compatible;
                    }
                    break;
                case FIRST_ELEMENT_OF_THE_INPUT:
                    this.name = propertyNode.getNodeValue();
                    break;
            }
        }
        if (this.name == null) {
            return false;
        }
        return compatible;

    }

    /**
     * Return name of the root element of the input xml
     *
     * @return name of the root element of the input xml
     */
    public String getName() {
        return name;
    }

    /**
     * Return run time properties included in the xslt stylesheet
     *
     * @return runtime properties
     */
    public Map<String, String> getRunTimeProperties() {
        return runTimeProperties;
    }

    /**
     * Indicate whether xslt transformation is possible or not
     *
     * @return whether xslt transformation is possible or not
     */
    public boolean isXsltCompatible() {
        return xsltCompatible;
    }
}
