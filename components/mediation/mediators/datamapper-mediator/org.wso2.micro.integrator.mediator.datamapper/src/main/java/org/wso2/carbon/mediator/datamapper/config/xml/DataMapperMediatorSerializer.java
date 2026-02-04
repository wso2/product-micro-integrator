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

import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorSerializer;
import org.apache.synapse.config.xml.ValueSerializer;
import org.wso2.carbon.mediator.datamapper.DataMapperMediator;

/**
 * Serializer for the DataMapperMediator which converts the
 * DataMapperMediator instance to the xml configuration
 * <p/>
 * <datamapper config="gov:datamapper/mappingConfig.dmc"
 * inputSchema="gov:datamapper/inputSchema.avsc" outputSchema="gov:datamapper/outputSchema.avsc"
 * inputType="application/xml" outputType="application/xml" />
 */

public class DataMapperMediatorSerializer extends AbstractMediatorSerializer {

    /**
     * Gets the mediator class name which will be serialized by the serializer
     *
     * @return String representing the full class name of the mediator
     */
    @Override public String getMediatorClassName() {
        return DataMapperMediator.class.getName();
    }

    /**
     * Holds the serialization logic of the DataMapperMediator class to the
     * relevant xml configuration
     *
     * @param mediator An instance of DataMapperMediator to be serialized
     * @return OMElement describing the serialized configuration of the DataMapperMediator
     */
    @Override protected OMElement serializeSpecificMediator(Mediator mediator) {
        if (!(mediator instanceof DataMapperMediator)) {
            handleException("Unsupported mediator passed in for serialization :" + mediator.getType());
        }

        DataMapperMediator dataMapperMediator = (DataMapperMediator) mediator;
        OMElement dataMapperElement = fac.createOMElement(DataMapperMediatorConstants.DATAMAPPER, synNS);

        if (dataMapperMediator.getMappingConfigurationKey() != null) {
            // Serialize Value using ValueSerializer
            ValueSerializer keySerializer = new ValueSerializer();
            keySerializer
                    .serializeValue(dataMapperMediator.getMappingConfigurationKey(), DataMapperMediatorConstants.CONFIG,
                                    dataMapperElement);
        } else {
            handleException("Invalid DataMapper mediator. Configuration registry key is required");
        }

        if (dataMapperMediator.getInputSchemaKey() != null) {
            ValueSerializer keySerializer = new ValueSerializer();
            keySerializer
                    .serializeValue(dataMapperMediator.getInputSchemaKey(), DataMapperMediatorConstants.INPUT_SCHEMA,
                                    dataMapperElement);
        } else {
            handleException("Invalid DataMapper mediator. InputSchema registry key is required");
        }

        if (dataMapperMediator.getOutputSchemaKey() != null) {
            ValueSerializer keySerializer = new ValueSerializer();
            keySerializer
                    .serializeValue(dataMapperMediator.getOutputSchemaKey(), DataMapperMediatorConstants.OUTPUT_SCHEMA,
                                    dataMapperElement);
        } else {
            handleException("Invalid DataMapper mediator. OutputSchema registry key is required");
        }

        if (dataMapperMediator.getXsltStyleSheetKey() != null) {
            ValueSerializer keySerializer = new ValueSerializer();
            keySerializer
                    .serializeValue(dataMapperMediator.getXsltStyleSheetKey(),
                                    DataMapperMediatorConstants.XSLT_STYLE_SHEET,
                                    dataMapperElement);
        }

        String inputType = dataMapperMediator.getInputType();
        if (inputType != null) {
            dataMapperElement.addAttribute(fac.createOMAttribute(DataMapperMediatorConstants.INPUT_TYPE, nullNS,
                                                                 inputType));
        }

        String outputType = dataMapperMediator.getOutputType();
        if (outputType != null) {
            dataMapperElement.addAttribute(fac.createOMAttribute(DataMapperMediatorConstants.OUTPUT_TYPE, nullNS,
                                                                 outputType));
        }

        String targetVariableName = dataMapperMediator.getTargetVariableName();
        if (targetVariableName != null) {
            dataMapperElement.addAttribute(fac.createOMAttribute(DataMapperMediatorConstants.TARGET_VARIABLE, nullNS,
                                                                 targetVariableName));
        }

        String target = dataMapperMediator.getTarget();
        if (target != null) {
            dataMapperElement.addAttribute(fac.createOMAttribute(DataMapperMediatorConstants.TARGET, nullNS, target));
        }

        saveTracingState(dataMapperElement, dataMapperMediator);
        serializeComments(dataMapperElement, dataMapperMediator.getCommentsList());
        return dataMapperElement;
    }

}
