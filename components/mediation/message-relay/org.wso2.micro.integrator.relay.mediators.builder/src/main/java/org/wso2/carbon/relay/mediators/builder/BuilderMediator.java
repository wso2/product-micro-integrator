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
package org.wso2.carbon.relay.mediators.builder;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.axis2.transport.TransportUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.wso2.carbon.relay.MessageBuilder;
import org.wso2.carbon.relay.RelayConstants;
import org.wso2.carbon.relay.StreamingOnRequestDataSource;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;


public class BuilderMediator extends AbstractMediator {

    private MessageBuilder messageBuilder = new MessageBuilder();

    private Builder specifiedBuilder;
    private MessageFormatter specifiedFormatter;

    public boolean mediate(MessageContext msgCtx) {
        SynapseLog synLog = getLog(msgCtx);

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Build mediator : start");
        }

        SOAPEnvelope envelope = msgCtx.getEnvelope();

        org.apache.axis2.context.MessageContext messageContext =
                ((Axis2MessageContext) msgCtx).getAxis2MessageContext();

        OMElement contentEle = envelope.getBody().getFirstChildWithName(
                RelayConstants.BINARY_CONTENT_QNAME);

        if (contentEle != null) {

            OMNode node = contentEle.getFirstOMChild();

            if (node != null && (node instanceof OMText)) {
                OMText binaryDataNode = (OMText) node;
                DataHandler dh = (DataHandler) binaryDataNode.getDataHandler();

                if (dh == null) {
                    if (synLog.isTraceOrDebugEnabled()) {
                        synLog.auditWarn("Message has the Binary content element. " +
                                        "But doesn't have binary content embedded within it");
                    }
                    return true;
                }

                DataSource dataSource = dh.getDataSource();

                //Ask the data source to stream, if it has not already cached the request
                if (dataSource instanceof StreamingOnRequestDataSource) {
                    ((StreamingOnRequestDataSource) dataSource).setLastUse(true);
                }

                InputStream in = null;
                try {
                    in = dh.getInputStream();
                } catch (IOException e) {
                    handleException("Error retrieving InputStream from data handler", e, msgCtx);
                }

                String contentType = (String) messageContext.getProperty(
                        Constants.Configuration.CONTENT_TYPE);

                OMElement element = null;
                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug("Trying to build a message with content type :" +
                            contentType);
                }
                try {
                    if (specifiedBuilder != null) {
                        // if a builder class is specified, it gets the preference
                        element = specifiedBuilder.processDocument(in, contentType, messageContext);
                    } else {
                        // otherwise, use the builder associated with this contentType
                        element = messageBuilder.getDocument(contentType, messageContext, in);
                    }
                } catch (Exception e) {
                    synLog.auditWarn("Error building message with content type :" + contentType);
                }

                if (element != null) {
                    try {
                        messageContext.setEnvelope(TransportUtils.createSOAPEnvelope(element));
                        if (specifiedFormatter != null) {
                            messageContext.setProperty(MessageBuilder.FORCED_RELAY_FORMATTER,
                                    specifiedFormatter);
                        } else {
                            // set the formatter map to the message context
                            messageContext.setProperty(MessageBuilder.RELAY_FORMATTERS_MAP,
                                    messageBuilder.getFormatters());
                        }
                    } catch (AxisFault axisFault) {
                        handleException("Failed to set the built SOAP " +
                                "Envelope to the message context", axisFault, msgCtx);
                    }
                } else {
                    synLog.auditWarn("Error occurred while trying to build the message, " +
                            "trying to send the message through");
                }

                //now we have undone thing done  by Relay
                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug("Build mediator : end");
                }
            } else {
                if (synLog.isTraceOrDebugEnabled()) {
                    //if is not wrapped binary content, there is nothing to be done
                    synLog.traceOrDebug("not wrapped binary content, there is nothing to be done");
                }
            }
        } else if (envelope.getBody().getFirstElement() == null) {
            // set the formatter map to the message context
            messageContext.setProperty(MessageBuilder.RELAY_FORMATTERS_MAP,
                    messageBuilder.getFormatters());
        }
        return true;
    }

    public Builder getSpecifiedBuilder() {
        return specifiedBuilder;
    }

    public void setSpecifiedBuilder(Builder specifiedBuilder) {
        this.specifiedBuilder = specifiedBuilder;
    }

    public MessageFormatter getSpecifiedFormatter() {
        return specifiedFormatter;
    }

    public void setSpecifiedFormatter(MessageFormatter specifiedFormatter) {
        this.specifiedFormatter = specifiedFormatter;
    }

    public void addFormatter(String contentType, MessageFormatter formatter) {
        messageBuilder.addFormatter(contentType, formatter);
    }

    public void addBuilder(String contentType, Builder builder) {
        messageBuilder.addBuilder(contentType, builder);
    }

    public Map<String, Builder> getMessageBuilders() {
        return messageBuilder.getBuilders();
    }

    public Map<String, MessageFormatter> getMessageFormatters() {
        return messageBuilder.getFormatters();
    }
}
