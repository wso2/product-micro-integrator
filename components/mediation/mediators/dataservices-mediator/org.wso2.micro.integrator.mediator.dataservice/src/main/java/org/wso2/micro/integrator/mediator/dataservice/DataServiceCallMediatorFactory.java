/*
 *
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  you may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.micro.integrator.mediator.dataservice;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.Mediator;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.config.xml.FactoryUtils;
import org.apache.synapse.config.xml.SynapseJsonPathFactory;
import org.apache.synapse.config.xml.SynapsePath;
import org.apache.synapse.config.xml.SynapseXPathFactory;
import org.apache.synapse.config.xml.ValueFactory;
import org.apache.synapse.config.xml.XMLConfigConstants;
import org.apache.synapse.mediators.Value;
import org.apache.synapse.util.xpath.SynapseJsonPath;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;
import org.wso2.micro.integrator.mediator.dataservice.DataServiceCallMediatorConstants.OperationsType;
import org.wso2.micro.integrator.mediator.dataservice.DataServiceCallMediator.Operations;
import org.wso2.micro.integrator.mediator.dataservice.DataServiceCallMediator.Param;


import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Factory for {@link DataServiceCallMediator} instances.
 * <p>
 * Configuration syntax:
 * <pre>
 * &lt;dataServicesCall serviceName = ""&gt;
 *      &lt;source  type="inline"|"body"/&gt;
 *      &lt;operations type="single"/&gt;
 *          &lt;operation name=""&gt;
 *                  &lt;param name="string"&gt; &lt;param/&gt;
 *          &lt;/operation&gt;
 *      &lt;/operations&gt;
 *      &lt;target  type="body"|"property" name="string"/&gt;
 * &lt;/dataServicesCall&gt;
 * </pre>
 */

public class DataServiceCallMediatorFactory extends AbstractMediatorFactory {

    private static final Log log = LogFactory.getLog(DataServiceCallMediatorFactory.class);

    private static final QName DSSCALL_Q =
            new QName(SynapseConstants.SYNAPSE_NAMESPACE, DataServiceCallMediatorConstants.DATA_SERVICE_CALL);
    private static final QName SOURCE_Q = new QName(
            XMLConfigConstants.SYNAPSE_NAMESPACE, DataServiceCallMediatorConstants.SOURCE);
    private static final QName TARGET_Q = new QName(
            XMLConfigConstants.SYNAPSE_NAMESPACE, DataServiceCallMediatorConstants.TARGET);
    private static final QName OPERATIONS_Q = new QName(
            XMLConfigConstants.SYNAPSE_NAMESPACE, DataServiceCallMediatorConstants.OPERATIONS);
    private static final QName NAME_Q = new QName(DataServiceCallMediatorConstants.NAME);
    private static final QName TYPE_Q = new QName(DataServiceCallMediatorConstants.TYPE);


    public Mediator createSpecificMediator(OMElement elem, Properties properties) {

        DataServiceCallMediator mediator = new DataServiceCallMediator();
        processAuditStatus(mediator, elem);
        String dsName = FactoryUtils.getFullyQualifiedName(properties,
                elem.getAttributeValue(new QName(DataServiceCallMediatorConstants.SERVICE_NAME)), FactoryUtils.TYPE_DATA_SERVICE);
        if (dsName == null) {
            handleException("The 'serviceName' attribute in 'dataServicesCall' element  is missing " +
                    "in the configuration.");
        }

        mediator.setDsName(dsName);

        mediator = configureSourceElement(mediator, elem);

        mediator = configureTargetElement(mediator, elem);

        return mediator;
    }

    private List extractOperations(OMElement operationsElemet, DataServiceCallMediator mediator) {
        List operationsList = new ArrayList();
        Iterator operationIterator = operationsElemet.getChildElements();
        while (operationIterator.hasNext()) {
            OMElement operationEle = (OMElement) operationIterator.next();
            if (DataServiceCallMediatorConstants.OPERATION.equals(operationEle.getLocalName())) {
                OMAttribute nameAtr = operationEle.getAttribute(new QName(DataServiceCallMediatorConstants.NAME));
                if (nameAtr != null) {
                    String operationName = nameAtr.getAttributeValue();
                    List<Param> paramList = new ArrayList<>();
                    if (operationEle.getFirstElement() != null && DataServiceCallMediatorConstants.PARAM.
                            equals(operationEle.getFirstElement().getLocalName())) {
                        if (mediator.getSourceType().equalsIgnoreCase(DataServiceCallMediatorConstants.INLINE_SOURCE)) {
                            paramList = extractParams(operationEle, mediator);
                        } else {
                            handleException("Inline parameters can not be configured when source type is 'body'");
                        }
                    }
                    operationsList.add(mediator.new Operation(operationName, paramList));
                }
            } else if (DataServiceCallMediatorConstants.OPERATIONS.equals(operationEle.getLocalName())) {
                String operationType = operationEle.getAttributeValue(new QName(DataServiceCallMediatorConstants.TYPE));
                operationsList.add(mediator.new Operations(operationType, extractOperations(operationEle, mediator)));
            } else {
                handleException("The 'operation' element is missing in the configuration.");
            }
        }
        return operationsList;
    }

    private List<Param> extractParams(OMElement elem, DataServiceCallMediator mediator) {
        List<Param> paramList = new ArrayList<>();
        if (elem != null) {
            Iterator paramsIterator = elem.getChildrenWithName(new QName(DataServiceCallMediatorConstants.PARAM));
            while (paramsIterator.hasNext()) {
                OMElement paramElement = (OMElement) paramsIterator.next();
                OMAttribute nameAtr = paramElement.getAttribute(new QName(DataServiceCallMediatorConstants.NAME));

                if (nameAtr != null) {
                    String paramName = nameAtr.getAttributeValue();
                    String paramValue = paramElement.getAttributeValue(new QName(DataServiceCallMediatorConstants.VALUE));
                    String evaluator = paramElement.getAttributeValue(new QName(DataServiceCallMediatorConstants.
                            EVALUATOR));
                    String paramExpression = paramElement.getAttributeValue(new QName(DataServiceCallMediatorConstants.
                            EXPRESSION));
                    Param param = mediator.new Param(paramName);
                    param.setParamValue(paramValue);
                    param.setEvaluator(evaluator);
                    try {
                        if (paramExpression != null) {
                            if (evaluator != null && evaluator.equals(DataServiceCallMediatorConstants.JSON_TYPE)) {
                                if (paramExpression.startsWith("json-eval(")) {
                                    paramExpression = paramExpression.substring(10, paramExpression.length() - 1);
                                }
                                param.setParamExpression(SynapseJsonPathFactory.getSynapseJsonPath(paramExpression));
                                // we have to explicitly define the path type since we are not going to mark
                                // JSON Path's with prefix "json-eval(".
                                param.getParamExpression().setPathType(SynapsePath.JSON_PATH);
                            } else {
                                SynapseXPath sxp = SynapseXPathFactory.getSynapseXPath(paramElement, ATT_EXPRN);
                                //we need to disable stream Xpath forcefully
                                sxp.setForceDisableStreamXpath(Boolean.TRUE);
                                param.setParamExpression(sxp);
                                param.getParamExpression().setPathType(SynapsePath.X_PATH);
                            }
                        }
                    } catch (JaxenException e) {
                        handleException("Invalid XPath expression for attribute expression : " +
                                paramExpression, e);
                    }
                    paramList.add(param);
                }
            }
        }
        return paramList;
    }

    public QName getTagQName() {
        return DSSCALL_Q;
    }

    /**
     * Validate the given name to identify whether it is static or dynamic key.
     * If the name is in the {} format then it is dynamic key(XPath)
     * Otherwise just a static name
     *
     * @param nameValue string to validate as a name
     * @return isDynamicName representing name type
     */
    private boolean isDynamicName(String nameValue) {
        if (nameValue.length() < 2) {
            return false;
        }

        final char startExpression = '{';
        final char endExpression = '}';

        char firstChar = nameValue.charAt(0);
        char lastChar = nameValue.charAt(nameValue.length() - 1);

        return (startExpression == firstChar && endExpression == lastChar);
    }

    /**
     * Method to create the dynamic name value based on the provided OMElement.
     *
     * @param propertyName  string to validate as a name
     * @param targetElement OMElement
     * @return the key value
     */
    private Value createDynamicNameValue(String propertyName, OMElement targetElement) {
        try {
            String nameExpression = propertyName.substring(1, propertyName.length() - 1);
            if (nameExpression.startsWith("json-eval(")) {
                new SynapseJsonPath(nameExpression.substring(10, nameExpression.length() - 1));
            } else {
                new SynapseXPath(nameExpression);
            }
        } catch (JaxenException e) {
            handleException("Invalid expression for attribute 'name' : " + propertyName, e);
        }
        // ValueFactory for creating dynamic Value
        ValueFactory nameValueFactory = new ValueFactory();
        // create dynamic Value based on OMElement
        return nameValueFactory.createValue(XMLConfigConstants.NAME, targetElement);
    }

    /**
     * Method to configure the source element.
     *
     * @param mediator dataServiceCallMediator instance
     * @param element  OMElement
     * @return dataServiceCallMediator with configured source element
     */
    private DataServiceCallMediator configureSourceElement(DataServiceCallMediator mediator, OMElement element) {
        OMElement sourceElement = element.getFirstChildWithName(SOURCE_Q);
        OMElement operationsTypeElement = element.getFirstChildWithName(OPERATIONS_Q);
        String message;
        if (sourceElement != null) {
            OMAttribute typeAtr = sourceElement.getAttribute(TYPE_Q);
            if (typeAtr != null) {
                String sourceType = typeAtr.getAttributeValue();
                mediator.setSourceType(sourceType);
                if (sourceType.equalsIgnoreCase(DataServiceCallMediatorConstants.INLINE_SOURCE)) {
                    return configureInlineSource(mediator, operationsTypeElement);
                } else {
                    if (operationsTypeElement != null) {
                        handleException("The 'source' type is set to body. Inline configurations " +
                                "are only applicable for source type 'inline'.");
                    }
                    mediator.setSourceType(DataServiceCallMediatorConstants.SOURCE_BODY_TYPE);
                    return mediator;
                }
            } else {
                message = "The 'type' attribute in 'source' element is missing. The default value will be set to 'body'.";
            }
        } else {
            message = "The 'source' element is missing in the configuration. The default value will be set to 'body'.";
        }
        return handleFaultySourceConfig(mediator, operationsTypeElement, message);
    }

    /**
     * Method to handle inline source configurations.
     *
     * @param mediator              dataServiceCallMediator instance
     * @param operationsTypeElement OMElement of the operations configuration
     * @return dataServiceCallMediator with inline source configuration
     */
    private DataServiceCallMediator configureInlineSource(DataServiceCallMediator mediator, OMElement operationsTypeElement) {
        if (!operationsTypeElement.getLocalName().equals(DataServiceCallMediatorConstants.OPERATIONS)) {
            handleException("The 'operations' element in 'dataServicesCall' element  is missing in the configuration.");
        }
        String operationType = operationsTypeElement.getAttributeValue(new QName(DataServiceCallMediatorConstants.TYPE));
        if (operationType == null) {
            handleException("The 'type' attribute in 'operations' element  is missing in the configuration.");
        }
        List operationList = extractOperations(operationsTypeElement, mediator);
        if (OperationsType.SINGLE.equals(operationType) && operationList.size() > 1) {
            handleException("The 'single operation' should contain one operation in the configuration.");
        }
        Operations operations = mediator.new Operations(operationType, operationList);
        mediator.setOperations(operations);

        return mediator;
    }

    /**
     * Method to set the target element.
     *
     * @param mediator dataServiceCallMediator instance
     * @param element  OMElement
     * @return dataServiceCallMediator with configured target element
     */
    private DataServiceCallMediator configureTargetElement(DataServiceCallMediator mediator, OMElement element) {
        OMElement targetElement = element.getFirstChildWithName(TARGET_Q);
        if (targetElement != null) {
            OMAttribute typeAtr = targetElement.getAttribute(TYPE_Q);
            if (typeAtr != null) {
                String typeValue = typeAtr.getAttributeValue();
                mediator.setTargetType(typeValue);
                if (typeValue.equals(DataServiceCallMediatorConstants.PROPERTY)) {
                    OMAttribute propertyAtr = targetElement.getAttribute(NAME_Q);
                    if (propertyAtr != null) {
                        if (!propertyAtr.getAttributeValue().isEmpty()) {
                            String propertyName = propertyAtr.getAttributeValue();
                            if (isDynamicName(propertyName)) {
                                Value targetDynamicName = createDynamicNameValue(propertyName, targetElement);
                                mediator.setTargetDynamicName(targetDynamicName);
                            } else {
                                mediator.setTargetPropertyName(propertyName);
                            }
                        } else {
                            handleException("The 'name' attribute in 'target' element is empty. " +
                                    "Please enter a value.");
                        }
                    } else {
                        handleException("The 'name' attribute in 'target' element  is missing " +
                                "in the configuration.");
                    }
                }
            } else {
                String msg = "The 'type' attribute in 'target' element is required for the configuration";
                return handleFaultyTargetConfig(mediator, msg);
            }
        } else {
            String msg = "The 'target' element is missing in the configuration. The default value will be set to 'body'.";
            return handleFaultyTargetConfig(mediator, msg);
        }
        return mediator;
    }

    /**
     * Method to handle incorrect source configurations.
     *
     * @param mediator dataServiceCallMediator instance
     * @param operationsTypeElement operationsTypeElement
     * @param message      the message to be printed
     * @return dataServiceCallMediator with default source type
     */
    private DataServiceCallMediator handleFaultySourceConfig(DataServiceCallMediator mediator, OMElement operationsTypeElement, String message) {
        mediator.setSourceType(DataServiceCallMediatorConstants.SOURCE_BODY_TYPE);
        log.warn(message);
        if (operationsTypeElement != null) {
            handleException("The source type is configured to 'body'. Inline configurations are " +
                    "permitted when source type is 'inline'");
        }
        return mediator;
    }

    /**
     * Method to handle incorrect target configurations.
     *
     * @param mediator dataServiceCallMediator instance
     * @param message      the message to be printed
     * @return dataServiceCallMediator with default target type
     */
    private DataServiceCallMediator handleFaultyTargetConfig(DataServiceCallMediator mediator, String message) {
        mediator.setTargetType(DataServiceCallMediatorConstants.TARGET_BODY_TYPE);
        log.warn(message);
        return mediator;
    }
}
