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
package org.wso2.carbon.mediator.datamapper.config.xml;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.config.xml.FactoryUtils;
import org.apache.synapse.config.xml.ValueFactory;
import org.apache.synapse.config.xml.XMLConfigConstants;
import org.apache.synapse.mediators.Value;
import org.wso2.carbon.mediator.datamapper.DataMapperMediator;

import javax.xml.namespace.QName;
import java.util.Properties;

/**
 * Configuration syntax:
 * <datamapper config="gov:datamapper/mappingConfig.js"
 * inputSchema="gov:datamapper/inputSchema.jsschema"
 * outputSchema="gov:datamapper/outputSchema.jsschema"
 * inputType="XML"
 * outputType="XML" />
 */

public class DataMapperMediatorFactory extends AbstractMediatorFactory {
    /**
     * Holds the QName for the DataMapperMediator xml configuration
     */
    private static final QName TAG_QNAME =
            new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, DataMapperMediatorConstants.DATAMAPPER);

    /**
     * Get the DataMapperMediator configuration tag name
     *
     * @return QName specifying the DataMapperMediator tag name of the xml
     * configuration
     */
    @Override public QName getTagQName() {
        return TAG_QNAME;
    }

    /**
     * Creates the DataMapperMediator by parsing the given xml
     * configuration
     *
     * @param element    OMElement describing the configuration of the
     *                   DataMapperMediator
     * @param properties Properties passed
     * @return DataMapperMediator created from the given configuration
     */
    @Override protected Mediator createSpecificMediator(OMElement element, Properties properties) {

        DataMapperMediator datamapperMediator = new DataMapperMediator();

        OMAttribute configKeyAttribute = element.getAttribute(new QName(DataMapperMediatorConstants.CONFIG));
        OMAttribute inputSchemaKeyAttribute = element.getAttribute(new QName(DataMapperMediatorConstants.INPUT_SCHEMA));
        OMAttribute outputSchemaKeyAttribute =
                element.getAttribute(new QName(DataMapperMediatorConstants.OUTPUT_SCHEMA));
        OMAttribute inputTypeAttribute = element.getAttribute(new QName(DataMapperMediatorConstants.INPUT_TYPE));
        OMAttribute outputTypeAttribute = element.getAttribute(new QName(DataMapperMediatorConstants.OUTPUT_TYPE));
        OMAttribute xsltStyleSheetKeyAttribute = element.getAttribute(new QName
                (DataMapperMediatorConstants.XSLT_STYLE_SHEET));
        OMAttribute targetVariableAttribute = element.getAttribute(new QName(DataMapperMediatorConstants.TARGET_VARIABLE));
        OMAttribute targetAttribute = element.getAttribute(new QName(DataMapperMediatorConstants.TARGET));

        /*
         * ValueFactory for creating dynamic or static Value and provide methods
		 * to create value objects
		 */
        ValueFactory keyFac = new ValueFactory();

        if (configKeyAttribute != null) {
            // Create dynamic or static key based on OMElement
            Value configKeyValue = keyFac.createValue(configKeyAttribute.getLocalName(), element);
            if (configKeyValue.getKeyValue() != null && !configKeyValue.hasExprTypeKey()){
                configKeyValue = new Value(FactoryUtils.prependArtifactIdentifierToFileName(
                        configKeyValue.getKeyValue(), properties));
            }
            // set key as the Value
            datamapperMediator.setMappingConfigurationKey(configKeyValue);
        } else {
            handleException("The attribute config is required for the DataMapper mediator");
        }

        if (inputSchemaKeyAttribute != null) {
            Value inputSchemaKeyValue = keyFac.createValue(inputSchemaKeyAttribute.getLocalName(), element);
            if (inputSchemaKeyValue.getKeyValue() != null && !inputSchemaKeyValue.hasExprTypeKey()){
                inputSchemaKeyValue = new Value(FactoryUtils.prependArtifactIdentifierToFileName(
                        inputSchemaKeyValue.getKeyValue(), properties));
            }
            datamapperMediator.setInputSchemaKey(inputSchemaKeyValue);
        } else {
            handleException("The attribute inputSchema is required for the DataMapper mediator");
        }

        if (outputSchemaKeyAttribute != null) {
            Value outputSchemaKeyValue = keyFac.createValue(outputSchemaKeyAttribute.getLocalName(), element);
            if (outputSchemaKeyValue.getKeyValue() != null && !outputSchemaKeyValue.hasExprTypeKey()){
                outputSchemaKeyValue = new Value(FactoryUtils.prependArtifactIdentifierToFileName(
                        outputSchemaKeyValue.getKeyValue(), properties));
            }
            datamapperMediator.setOutputSchemaKey(outputSchemaKeyValue);
        } else {
            handleException("The outputSchema attribute is required for the DataMapper mediator");
        }

        if (inputTypeAttribute != null) {
            datamapperMediator.setInputType(inputTypeAttribute.getAttributeValue());
        }

        if (outputTypeAttribute != null) {
            datamapperMediator.setOutputType(outputTypeAttribute.getAttributeValue());
        }

        if (xsltStyleSheetKeyAttribute != null) {
            Value xsltStyleSheetKeyValue = keyFac.createValue(xsltStyleSheetKeyAttribute
                    .getLocalName(), element);
            if (xsltStyleSheetKeyValue.getKeyValue() != null && !xsltStyleSheetKeyValue.hasExprTypeKey()){
                xsltStyleSheetKeyValue = new Value(FactoryUtils.prependArtifactIdentifierToFileName(
                        xsltStyleSheetKeyValue.getKeyValue(), properties));
            }
            datamapperMediator.setXsltStyleSheetKey(xsltStyleSheetKeyValue);
        }

        if (targetAttribute != null) {
            datamapperMediator.setTarget(targetAttribute.getAttributeValue());
        }

        if (targetVariableAttribute != null) {
            datamapperMediator.setTargetVariableName(targetVariableAttribute.getAttributeValue());
        }

        processAuditStatus(datamapperMediator, element);
        addAllCommentChildrenToList(element, datamapperMediator.getCommentsList());
        return datamapperMediator;
    }

}
