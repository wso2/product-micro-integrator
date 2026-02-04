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
package org.wso2.carbon.relay.mediators.builder.xml;

import org.apache.axis2.builder.Builder;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.Mediator;
import org.apache.synapse.SynapseConstants;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMAttribute;
import org.apache.axis2.AxisFault;
import org.wso2.carbon.relay.mediators.builder.BuilderMediator;
import org.wso2.carbon.relay.MessageBuilder;

import javax.xml.namespace.QName;
import java.util.Iterator;
import java.util.Properties;

public class BuilderMediatorFactory extends AbstractMediatorFactory {
    
    private QName BUILDER_T = new QName(SynapseConstants.SYNAPSE_NAMESPACE, "builder");
    private QName MESSAGE_BUILDER_T = new QName(SynapseConstants.SYNAPSE_NAMESPACE, "messageBuilder");
    private QName CONTENT_TYPE_ATT = new QName("contentType");
    private QName BUILDER_CLASS_ATT = new QName("class");
    private QName FORMATTER_CLASS_ATT = new QName("formatterClass");

    public QName getTagQName() {
        return BUILDER_T;
    }

    protected Mediator createSpecificMediator(OMElement omElement, Properties properties) {
        BuilderMediator builderMediator = new BuilderMediator();

        OMAttribute builderClassAtt = omElement.getAttribute(BUILDER_CLASS_ATT);

        if (builderClassAtt != null) {
            /** This configuration is preferred since it is the new way of using builder mediator */
            OMAttribute formatterClassAtt = omElement.getAttribute(FORMATTER_CLASS_ATT);
            if (formatterClassAtt == null) {
                handleException("formatterClass attribute is required");
                return null;
            }

            try {
                Builder specifiedBuilder =
                        MessageBuilder.createBuilder(builderClassAtt.getAttributeValue());
                MessageFormatter specifiedFormatter =
                        MessageBuilder.createFormatter(formatterClassAtt.getAttributeValue());
                builderMediator.setSpecifiedBuilder(specifiedBuilder);
                builderMediator.setSpecifiedFormatter(specifiedFormatter);
            } catch (AxisFault axisFault) {
                handleException("Invalid builder/formatter class.", axisFault);
                return null;
            }

        } else {
            /** This is to support the old configuration model of builder mediator */
            Iterator it = omElement.getChildrenWithName(MESSAGE_BUILDER_T);
            while (it.hasNext()) {
                OMElement e = (OMElement) it.next();

                OMAttribute contentTypeAtt = e.getAttribute(CONTENT_TYPE_ATT);

                if (contentTypeAtt == null) {
                    handleException("contentType attribute is required");
                    return null;
                }

                OMAttribute builderClasAtt = e.getAttribute(BUILDER_CLASS_ATT);
                if (builderClasAtt == null) {
                    handleException("class attribute is required");
                    return null;
                }

                try {
                    builderMediator.addBuilder(contentTypeAtt.getAttributeValue(),
                            MessageBuilder.createBuilder(builderClasAtt.getAttributeValue()));
                } catch (AxisFault axisFault) {
                    handleException("Error creating message builder: " +
                            builderClasAtt.getAttributeValue(), axisFault);
                }

                OMAttribute formatterClassAtt = e.getAttribute(FORMATTER_CLASS_ATT);
                if (formatterClassAtt != null && formatterClassAtt.getAttributeValue() != null) {
                    try {
                        builderMediator.addFormatter(contentTypeAtt.getAttributeValue(),
                                MessageBuilder.createFormatter(formatterClassAtt.getAttributeValue()));
                    } catch (AxisFault axisFault) {
                        handleException("Error creating message formatter: "
                                + formatterClassAtt.getAttributeValue(), axisFault);
                    }
                }
            }
        }
        addAllCommentChildrenToList(omElement, builderMediator.getCommentsList());
        return builderMediator;
    }
}
