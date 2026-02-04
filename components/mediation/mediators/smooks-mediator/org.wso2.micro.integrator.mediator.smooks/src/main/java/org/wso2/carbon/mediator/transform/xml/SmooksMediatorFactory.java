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
package org.wso2.carbon.mediator.transform.xml;

import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.Mediator;
import org.apache.synapse.SynapseConstants;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMAttribute;
import org.apache.synapse.config.xml.SynapseXPathFactory;
import org.apache.synapse.config.xml.ValueFactory;
import org.apache.synapse.config.xml.XMLConfigConstants;
import org.apache.synapse.mediators.Value;
import org.jaxen.JaxenException;
import org.wso2.carbon.mediator.transform.Input;
import org.wso2.carbon.mediator.transform.Output;
import org.wso2.carbon.mediator.transform.SmooksMediator;

import javax.xml.namespace.QName;
import java.util.Properties;

public class SmooksMediatorFactory extends AbstractMediatorFactory {
    public static final QName SMOOKS_Q = new QName(
            SynapseConstants.SYNAPSE_NAMESPACE, "smooks");

    public static final QName CONFIG_KEY = new QName("config-key");
    /** JPA Persistence Unit Name */
    public static final QName PERSISTENCE_UNIT = new QName("persistence-unit");

    public Mediator createSpecificMediator(OMElement omElement, Properties properties) {
        SmooksMediator smooks = new SmooksMediator();

        OMAttribute configFileAttr = omElement.getAttribute(CONFIG_KEY);

        if (configFileAttr != null) {
            // ValueFactory for creating dynamic or static Value
            ValueFactory keyFactory = new ValueFactory();
            // create dynamic or static key based on OMElement
            Value generatedKey = keyFactory.createValue("config-key", omElement);

            smooks.setConfigKey(generatedKey);
        } else {
            handleException("Invalid smooks mediator. Configuration key is required.");
        }
        
        OMAttribute persistenceUnitAttr = omElement.getAttribute(PERSISTENCE_UNIT);
        if (persistenceUnitAttr != null) {
        	smooks.setPersistenceUnitAttr(persistenceUnitAttr.getAttributeValue());
        }

        OMElement inputElement = omElement.getFirstChildWithName(
                new QName(SynapseConstants.SYNAPSE_NAMESPACE, "input"));
        if (inputElement != null) {
            smooks.setInput(createInput(inputElement));
        } else {
            smooks.setInput(new Input());
        }

        OMElement outputElement = omElement.getFirstChildWithName(
                new QName(SynapseConstants.SYNAPSE_NAMESPACE, "output"));
        if (inputElement != null) {
            smooks.setOutput(createOutput(outputElement));
        } else {
            smooks.setOutput(new Output());
        }
        addAllCommentChildrenToList(omElement, smooks.getCommentsList());
        return smooks;
    }

    private Input createInput(OMElement input) {
        Input in = new Input();

        OMAttribute typeAttr = input.getAttribute(new QName("type"));
        if (typeAttr == null) {
            handleException("type attribute is required for the input element");
        }

        assert typeAttr != null;

        String typeValue = typeAttr.getAttributeValue();
        if (typeValue.equals("text")) {
            in.setType(SmooksMediator.TYPES.TEXT);
        } else if (typeValue.equals("xml")) {
            in.setType(SmooksMediator.TYPES.XML);
        } else {
            handleException("Unexpected type specified as the input: " + typeValue);
        }

        if (input.getAttribute(new QName("expression")) != null) {
            try {
                in.setExpression(SynapseXPathFactory.getSynapseXPath(input, new QName("expression")));
            } catch (JaxenException e) {
                handleException("Error creating the XPath expression", e);
            }
        }

        return in;
    }

    private Output createOutput(OMElement output) {
        Output in = new Output();

        OMAttribute typeAttr = output.getAttribute(new QName("type"));
        if (typeAttr == null) {
            handleException("type attribute is required for the input element");
        }

        assert typeAttr != null;

        String typeValue = typeAttr.getAttributeValue();
        if (typeValue.equals("text")) {
            in.setType(SmooksMediator.TYPES.TEXT);
        } else if (typeValue.equals("xml")) {
            in.setType(SmooksMediator.TYPES.XML);
        } else if (typeValue.equals("java")){
        	 in.setType(SmooksMediator.TYPES.JAVA);
        } else {
            handleException("Unexpected type specified as the input: " + typeValue);
        }

        if (output.getAttribute(new QName("expression")) != null) {
            try {
                in.setExpression(SynapseXPathFactory.getSynapseXPath(output, new QName("expression")));
            } catch (JaxenException e) {
                handleException("Error creating the XPath expression", e);
            }
        }

        OMAttribute actionAttr = output.getAttribute(new QName("action"));
        if (actionAttr != null && actionAttr.getAttributeValue() != null) {
            in.setAction(actionAttr.getAttributeValue());
        }

        OMAttribute propertyAttr = output.getAttribute(new QName("property"));
        if (propertyAttr != null && propertyAttr.getAttributeValue() != null) {
            in.setProperty(propertyAttr.getAttributeValue());
        }

        return in;
    }

    public QName getTagQName() {
        return SMOOKS_Q;
    }
}