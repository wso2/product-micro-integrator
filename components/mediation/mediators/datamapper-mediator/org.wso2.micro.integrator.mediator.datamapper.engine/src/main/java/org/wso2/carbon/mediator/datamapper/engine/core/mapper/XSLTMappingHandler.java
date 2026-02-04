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

import net.sf.saxon.TransformerFactoryImpl;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;

/**
 * This class handle the transformation of the input xml to the output xml.
 * Saxon xslt transformation engine is used for the transformation.
 */
public class XSLTMappingHandler {

    private final Templates templates;

    /**
     * @param xsltMappingResource xslt mapping resource
     * @throws TransformerException errors arise from xslt transformation
     */
    public XSLTMappingHandler(XSLTMappingResource xsltMappingResource) throws TransformerException {
        Source xsltSource = new StreamSource(xsltMappingResource.getInputStream());
        TransformerFactory transFact = new TransformerFactoryImpl();
        templates = transFact.newTemplates(xsltSource);
    }

    /**
     * This method performs xslt transformation from input xml to the output xml
     *
     * @param properties set of runtime properties with values
     * @param inputXML   input stream of the input xml message
     * @return output xml as a string
     * @throws TransformerException errors arise from xslt transformation
     */
    public String doMap(Map<String, Object> properties, InputStream inputXML) throws
                                                                              TransformerException {

        Transformer transformer = templates.newTransformer();
        setParameters(transformer, properties);
        Source xmlSource = new StreamSource(inputXML);
        StringWriter sw = new StringWriter();
        Result result = new javax.xml.transform.stream.StreamResult(sw);
        transformer.transform(xmlSource, result);
        return sw.toString();

    }

    /**
     * Set runtime properties as parameters in the transformer
     *
     * @param transformer Transformer object
     * @param properties Map of properties with values
     */
    private void setParameters(Transformer transformer, Map<String, Object> properties) {
        transformer.clearParameters();
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            transformer.setParameter(property.getKey(), property.getValue());
        }
    }

}
