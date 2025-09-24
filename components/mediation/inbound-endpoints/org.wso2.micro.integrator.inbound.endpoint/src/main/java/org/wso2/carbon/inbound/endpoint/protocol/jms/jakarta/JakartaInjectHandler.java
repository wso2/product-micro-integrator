/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.inbound.endpoint.protocol.jms.jakarta;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.util.UUIDGenerator;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.builder.BuilderUtil;
import org.apache.axis2.builder.SOAPBuilder;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.format.DataSourceMessageBuilder;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.aspects.flow.statistics.collectors.RuntimeStatisticCollector;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.inbound.InboundEndpointConstants;
import org.apache.synapse.mediators.MediatorFaultHandler;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.customlogsetter.CustomLogSetter;
import org.wso2.carbon.inbound.endpoint.inboundfactory.InboundRequestProcessorFactoryImpl;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericConstants;
import org.wso2.carbon.inbound.endpoint.protocol.jms.JMSConstants;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * JMSInjectHandler use to mediate the received JMS message
 */
public class JakartaInjectHandler {

    private static final Log log = LogFactory.getLog(JakartaInjectHandler.class);

    private String injectingSeq;
    private String onErrorSeq;
    private boolean sequential;
    private SynapseEnvironment synapseEnvironment;
    private Properties jmsProperties;
    //Following is used when using reply destination
    private Connection connection;
    private Destination replyDestination;

    public JakartaInjectHandler(String injectingSeq, String onErrorSeq, boolean sequential,
                                SynapseEnvironment synapseEnvironment, Properties jmsProperties) {
        this.injectingSeq = injectingSeq;
        this.onErrorSeq = onErrorSeq;
        this.sequential = sequential;
        this.synapseEnvironment = synapseEnvironment;
        this.jmsProperties = jmsProperties;

    }

    /**
     * Invoke the mediation logic for the passed message
     */
    public boolean invoke(Object object, String name) throws SynapseException {

        Message msg = (Message) object;
        try {
            org.apache.synapse.MessageContext msgCtx = createMessageContext();
            msgCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, name);
            msgCtx.setProperty(SynapseConstants.ARTIFACT_NAME, SynapseConstants.FAIL_SAFE_MODE_INBOUND_ENDPOINT + name);
            msgCtx.setProperty(SynapseConstants.IS_INBOUND, true);
            if (RuntimeStatisticCollector.isStatisticsEnabled()) {
                populateStatisticsMetadata(msgCtx);
            }
            InboundEndpoint inboundEndpoint = msgCtx.getConfiguration().getInboundEndpoint(name);

            // Adding inbound endpoint parameters as synapse properties
            Map<String, String> parametersMap = inboundEndpoint.getParametersMap();
            for (Map.Entry<String, String> entry : parametersMap.entrySet()) {
                msgCtx.setProperty(entry.getKey(), entry.getValue());
            }

            CustomLogSetter.getInstance().setLogAppender(inboundEndpoint.getArtifactContainerName());
            String contentType = null;

            String contentTypeProperty = jmsProperties.getProperty(JMSConstants.CONTENT_TYPE_PROPERTY);
            if (contentTypeProperty != null) {
                contentType = msg.getStringProperty(contentTypeProperty);
            }

            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = jmsProperties.getProperty(JMSConstants.CONTENT_TYPE);
            }

            if (contentType == null) {
                String jmsType = msg.getJMSType();

                if (jmsType != null && !(jmsType.trim().isEmpty())) {
                    contentType = jmsType;
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Processed JMS Message of Content-type : " + contentType);
            }
            MessageContext axis2MsgCtx = ((Axis2MessageContext) msgCtx)
                    .getAxis2MessageContext();

            String hyphenSupport = JMSConstants.DEFAULT_HYPHEN_SUPPORT;
            if (inboundEndpoint.getParameter(JMSConstants.PARAM_JMS_HYPHEN_MODE) != null) {
                hyphenSupport = inboundEndpoint.getParameter(JMSConstants.PARAM_JMS_HYPHEN_MODE);
            }
            axis2MsgCtx.setProperty(JMSConstants.PARAM_JMS_HYPHEN_MODE, hyphenSupport);

            //setting transport headers
            Map<String, Object> transportHeaders = JakartaUtils.getTransportHeaders(msg, axis2MsgCtx);
            transportHeaders.put(JMSConstants.JMS_TIMESTAMP, msg.getJMSTimestamp());
            transportHeaders.put(JMSConstants.JMS_PRIORITY, msg.getJMSPriority());
            transportHeaders.put(JMSConstants.JMS_EXPIRATION, msg.getJMSExpiration());
            transportHeaders.put(JMSConstants.JMS_DELIVERY_MODE, msg.getJMSDeliveryMode());
            transportHeaders.put(JMSConstants.JMS_REDELIVERED, msg.getJMSRedelivered());
            if (msg.getJMSMessageID() != null) {
                transportHeaders.put(JMSConstants.JMS_MESSAGE_ID, msg.getJMSMessageID());
            }
            if (msg.getJMSType() != null) {
                transportHeaders.put(JMSConstants.JMS_MESSAGE_TYPE, msg.getJMSType());
            }
            if (msg.getJMSCorrelationID() != null) {
                transportHeaders.put(JMSConstants.JMS_COORELATION_ID, msg.getJMSCorrelationID());
            }
            axis2MsgCtx.setProperty(MessageContext.TRANSPORT_HEADERS, transportHeaders);

            // set the JMS Message ID as the Message ID of the MessageContext
            try {
                if (msg.getJMSMessageID() != null) {
                    msgCtx.setMessageID(msg.getJMSMessageID());
                }
                String jmsCorrelationID = msg.getJMSCorrelationID();
                if (jmsCorrelationID != null && !jmsCorrelationID.isEmpty()) {
                    msgCtx.setProperty(JMSConstants.JMS_COORELATION_ID, jmsCorrelationID);
                } else {
                    msgCtx.setProperty(JMSConstants.JMS_COORELATION_ID, msg.getJMSMessageID());
                }
            } catch (JMSException ignore) {
                log.warn("Error getting the COORELATION ID from the message.");
            }

            // Handle dual channel
            Destination replyTo = msg.getJMSReplyTo();
            if (replyTo != null) {
                // Create the cachedJMSConnectionFactory with the existing
                // connection
                CachedJakartaConnectionFactory cachedJMSConnectionFactory = new CachedJakartaConnectionFactory(jmsProperties,
                                                                                                       connection);
                String strUserName = jmsProperties.getProperty(JMSConstants.PARAM_JMS_USERNAME);
                String strPassword = jmsProperties.getProperty(JMSConstants.PARAM_JMS_PASSWORD);
                JakartaReplySender jmsReplySender = new JakartaReplySender(replyTo, cachedJMSConnectionFactory, strUserName,
                                                                   strPassword);
                msgCtx.setProperty(InboundEndpointConstants.INBOUND_ENDPOINT_RESPONSE_WORKER, jmsReplySender);
            } else if (replyDestination != null) {
                // Create the cachedJMSConnectionFactory with the existing
                // connection
                CachedJakartaConnectionFactory cachedJMSConnectionFactory = new CachedJakartaConnectionFactory(jmsProperties,
                                                                                                       connection);
                String strUserName = jmsProperties.getProperty(JMSConstants.PARAM_JMS_USERNAME);
                String strPassword = jmsProperties.getProperty(JMSConstants.PARAM_JMS_PASSWORD);
                JakartaReplySender jmsReplySender = new JakartaReplySender(replyDestination, cachedJMSConnectionFactory,
                                                                   strUserName, strPassword);
                msgCtx.setProperty(InboundEndpointConstants.INBOUND_ENDPOINT_RESPONSE_WORKER, jmsReplySender);
            }

            // Determine the message builder to use
            Builder builder;
            if (contentType == null) {
                log.debug("No content type specified. Using SOAP builder.");
                builder = new SOAPBuilder();
            } else {
                int index = contentType.indexOf(';');
                String type = index > 0 ? contentType.substring(0, index) : contentType;
                builder = BuilderUtil.getBuilderFromSelector(type, axis2MsgCtx);
                if (builder == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("No message builder found for type '" + type + "'. Falling back to SOAP.");
                    }
                    builder = new SOAPBuilder();
                }
            }
            OMElement documentElement = null;
            // set the message payload to the message context
            try {
                if (msg instanceof TextMessage) {
                    String message = ((TextMessage) msg).getText();
                    InputStream in = new AutoCloseInputStream(new ByteArrayInputStream(message.getBytes()));
                    documentElement = builder.processDocument(in, contentType, axis2MsgCtx);
                } else if (msg instanceof BytesMessage) {
                    if (builder instanceof DataSourceMessageBuilder) {
                        documentElement = ((DataSourceMessageBuilder) builder)
                                .processDocument(new BytesMessageDataSource((BytesMessage) msg), contentType,
                                                 axis2MsgCtx);
                    } else {
                        documentElement = builder
                                .processDocument(new BytesMessageInputStream((BytesMessage) msg), contentType,
                                                 axis2MsgCtx);
                    }
                } else if (msg instanceof MapMessage) {
                    documentElement = convertJMSMapToXML((MapMessage) msg);
                }
            } catch (Exception ex) {
                // Handle message building error
                log.error("Error while building the message", ex);
                msgCtx.setProperty(SynapseConstants.ERROR_CODE, GenericConstants.INBOUND_BUILD_ERROR);
                msgCtx.setProperty(SynapseConstants.ERROR_MESSAGE, ex.getMessage());
                SequenceMediator faultSequence = getFaultSequence(msgCtx, inboundEndpoint);
                faultSequence.mediate(msgCtx);

                if (isRollback(msgCtx) || isToRecover(msgCtx)) {
                    return false;
                }
                return true;
            }

            // Setting JMSXDeliveryCount header on the message context
            try {
                int deliveryCount = msg.getIntProperty("JMSXDeliveryCount");
                msgCtx.setProperty(JMSConstants.DELIVERY_COUNT, deliveryCount);
            } catch (NumberFormatException nfe) {
                if (log.isDebugEnabled()) {
                    log.debug("JMSXDeliveryCount is not set in the received message");
                }
            }

            // Inject the message to the sequence.
            msgCtx.setEnvelope(TransportUtils.createSOAPEnvelope(documentElement));
            if (injectingSeq == null || injectingSeq.equals("")) {
                log.error("Sequence name not specified. Sequence : " + injectingSeq);
                return false;
            }
            SequenceMediator seq = (SequenceMediator) synapseEnvironment.getSynapseConfiguration()
                    .getSequence(injectingSeq);
            if (seq != null) {
                if (log.isDebugEnabled()) {
                    log.debug("injecting message to sequence : " + injectingSeq);
                }
                if (!seq.isInitialized()) {
                    seq.init(synapseEnvironment);
                }
                SequenceMediator faultSequence = getFaultSequence(msgCtx, inboundEndpoint);
                MediatorFaultHandler mediatorFaultHandler = new MediatorFaultHandler(faultSequence);
                msgCtx.pushFaultHandler(mediatorFaultHandler);

                if (!synapseEnvironment.injectInbound(msgCtx, seq, sequential)) {
                    return false;
                }
            } else {
                log.error("Sequence: " + injectingSeq + " not found");
                return false;
            }

            if (isRollback(msgCtx) || isToRecover(msgCtx)) {
                return false;
            }
        } catch (SynapseException se) {
            throw se;
        } catch (Exception e) {
            log.error("Error while processing the JMS Message", e);
            throw new SynapseException("Error while processing the JMS Message", e);
        }
        return true;
    }

    private void populateStatisticsMetadata(org.apache.synapse.MessageContext synCtx) {
        Map<String, Object> statisticsDetails = new HashMap<String, Object>();
        statisticsDetails.put(InboundEndpointConstants.INBOUND_ENDPOINT_PROTOCOL,
                InboundRequestProcessorFactoryImpl.Protocols.jms.toString());
        synCtx.setProperty(SynapseConstants.STATISTICS_METADATA, statisticsDetails);
    }

    /**
     * Evaluate if JMS session need to be rollback judging
     * from properties set to message context
     *
     * @param msgCtx MessageContext to evaluate
     * @return true if JMS session need to be recovered
     */
    private boolean isRollback(org.apache.synapse.MessageContext msgCtx) {
        return JakartaUtils.checkIfBooleanPropertyIsSet(JMSConstants.SET_ROLLBACK_ONLY, msgCtx);
    }

    /**
     * Evaluate if JMS session need to be recovered judging
     * from properties set to message context
     *
     * @param msgCtx MessageContext to evaluate
     * @return true if JMS session need to be recovered
     */
    private boolean isToRecover(org.apache.synapse.MessageContext msgCtx) {
        return JakartaUtils.checkIfBooleanPropertyIsSet(JMSConstants.SET_RECOVER, msgCtx);
    }

    /**
     * @param message JMSMap message
     * @return XML representation of JMS Map message
     */
    public static OMElement convertJMSMapToXML(MapMessage message) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMNamespace jmsMapNS = OMAbstractFactory.getOMFactory().createOMNamespace(JMSConstants.JMS_MAP_NS, "");
        OMElement jmsMap = fac.createOMElement(JMSConstants.JMS_MAP_ELEMENT_NAME, jmsMapNS);
        try {
            Enumeration names = message.getMapNames();
            while (names.hasMoreElements()) {
                String nextName = names.nextElement().toString();
                String nextVal = message.getString(nextName);
                OMElement next = fac.createOMElement(nextName.replace(" ", ""), jmsMapNS);
                next.setText(nextVal);
                jmsMap.addChild(next);
            }
        } catch (JMSException e) {
            log.error("Error while processing the JMS Map Message. " + e.getMessage());
        }
        return jmsMap;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public void setReplyDestination(Destination replyDestination) {
        this.replyDestination = replyDestination;
    }

    /**
     * Create the initial message context for the file
     */
    private org.apache.synapse.MessageContext createMessageContext() {
        org.apache.synapse.MessageContext msgCtx = synapseEnvironment.createMessageContext();
        //Need to set this to build the message
        msgCtx.setProperty(SynapseConstants.INBOUND_JMS_PROTOCOL, true);
        MessageContext axis2MsgCtx = ((Axis2MessageContext) msgCtx)
                .getAxis2MessageContext();
        axis2MsgCtx.setServerSide(true);
        axis2MsgCtx.setMessageID(UUIDGenerator.getUUID());
        msgCtx.setProperty(MessageContext.CLIENT_API_NON_BLOCKING, true);
        return msgCtx;
    }

    private SequenceMediator getFaultSequence(org.apache.synapse.MessageContext synCtx, InboundEndpoint endpoint) {
        SequenceMediator faultSequence = null;
        if (endpoint.getOnErrorSeq() != null) {
            faultSequence = (SequenceMediator) synCtx.getSequence(endpoint.getOnErrorSeq());
        }

        if (faultSequence == null) {
            faultSequence = (SequenceMediator) synCtx.getFaultSequence();
        }

        return faultSequence;
    }

}
