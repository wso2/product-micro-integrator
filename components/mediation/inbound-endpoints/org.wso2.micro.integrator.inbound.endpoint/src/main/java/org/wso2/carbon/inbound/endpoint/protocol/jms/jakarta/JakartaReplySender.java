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

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMOutputFormat;
import org.apache.axiom.om.OMText;
import org.apache.axis2.AxisFault;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.axis2.transport.base.BaseConstants;
import org.apache.axis2.transport.base.BaseUtils;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.util.MessageProcessorSelector;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.inbound.InboundResponseSender;
import org.wso2.carbon.inbound.endpoint.protocol.jms.JMSConstants;

import javax.activation.DataHandler;
import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.UnsupportedCharsetException;

public class JakartaReplySender implements InboundResponseSender {

    private static final Log log = LogFactory.getLog(JakartaReplySender.class.getName());

    private Destination replyTo;
    private CachedJakartaConnectionFactory cachedJMSConnectionFactory;
    private String strUserName;
    private String strPassword;

    public JakartaReplySender(Destination replyTo, CachedJakartaConnectionFactory cachedJMSConnectionFactory,
                              String strUserName, String strPassword) {
        this.replyTo = replyTo;
        this.strUserName = strUserName;
        this.strPassword = strPassword;
        this.cachedJMSConnectionFactory = cachedJMSConnectionFactory;
    }

    /**
     * Send the reply back to the response queue/topic
     */
    public void sendBack(MessageContext synCtx) {
        log.debug("Begin sending reply to the destination queue.");
        MessageProducer producer = null;
        Session session = null;
        try {
            Connection connection = cachedJMSConnectionFactory.getConnection(strUserName, strPassword);
            session = cachedJMSConnectionFactory.getSession(connection);
            producer = cachedJMSConnectionFactory.createProducer(session, replyTo, true);
            Message message = createJMSMessage(synCtx, session, null);
            producer.send(message);
        } catch (JMSException e) {
            log.error("Error sending JMS response", e);
        } catch (Exception e) {
            log.error("Error sending JMS response", e);
        } finally {
            try {
                producer.close();
            } catch (Exception e) {
                log.debug("ERROR: Unable to close the producer");
            }
            try {
                session.close();
            } catch (Exception e) {
                log.debug("ERROR: Unable to close the session");
            }
        }
    }

    /**
     * Create a JMS Message from the given MessageContext and using the given
     * session
     *
     * @param synCtx          the MessageContext
     * @param session             the JMS session
     * @param contentTypeProperty the message property to be used to store the content type
     * @return a JMS message from the context and session
     * @throws JMSException on exception
     * @throws AxisFault    on exception
     */
    private Message createJMSMessage(MessageContext synCtx, Session session, String contentTypeProperty)
            throws JMSException {

        Message message = null;
        org.apache.axis2.context.MessageContext msgContext = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        String msgType = getProperty(msgContext, JMSConstants.JMS_MESSAGE_TYPE);
        String jmsPayloadType = guessMessageType(msgContext);
        if (jmsPayloadType == null) {
            OMOutputFormat format = BaseUtils.getOMOutputFormat(msgContext);
            MessageFormatter messageFormatter = null;
            try {
                messageFormatter = MessageProcessorSelector.getMessageFormatter(msgContext);
            } catch (AxisFault axisFault) {
                throw new JMSException("Unable to get the message formatter to use");
            }

            String contentType = messageFormatter.getContentType(msgContext, format, msgContext.getSoapAction());

            boolean useBytesMessage = msgType != null && JMSConstants.JMS_BYTE_MESSAGE.equals(msgType)
                    || contentType.indexOf(HTTPConstants.HEADER_ACCEPT_MULTIPART_RELATED) > -1;

            OutputStream out;
            StringWriter sw;
            if (useBytesMessage) {
                BytesMessage bytesMsg = session.createBytesMessage();
                sw = null;
                out = new org.wso2.carbon.inbound.endpoint.protocol.jms.jakarta.BytesMessageOutputStream(bytesMsg);
                message = bytesMsg;
            } else {
                sw = new StringWriter();
                try {
                    out = new WriterOutputStream(sw, format.getCharSetEncoding());
                } catch (UnsupportedCharsetException ex) {
                    log.error("Unsupported encoding " + format.getCharSetEncoding(), ex);
                    throw new JMSException("Unsupported encoding " + format.getCharSetEncoding());
                }
            }

            try {
                messageFormatter.writeTo(msgContext, format, out, true);
                out.close();
            } catch (IOException e) {
                log.error("IO Error while creating BytesMessage", e);
                throw new JMSException("IO Error while creating BytesMessage");
            }
            if (!useBytesMessage) {
                TextMessage txtMsg = session.createTextMessage();
                txtMsg.setText(sw.toString());
                message = txtMsg;
            }
            if (contentTypeProperty != null) {
                message.setStringProperty(contentTypeProperty, contentType);
            }

        } else if (JMSConstants.JMS_BYTE_MESSAGE.equals(jmsPayloadType)) {
            message = session.createBytesMessage();
            BytesMessage bytesMsg = (BytesMessage) message;
            OMElement wrapper = msgContext.getEnvelope().getBody()
                    .getFirstChildWithName(BaseConstants.DEFAULT_BINARY_WRAPPER);
            OMNode omNode = wrapper.getFirstOMChild();
            if (omNode != null && omNode instanceof OMText) {
                Object dh = ((OMText) omNode).getDataHandler();
                if (dh != null && dh instanceof DataHandler) {
                    try {
                        ((DataHandler) dh).writeTo(new org.wso2.carbon.inbound.endpoint.protocol.jms.jakarta.
                                BytesMessageOutputStream(bytesMsg));
                    } catch (IOException e) {
                        log.error(
                                "Error serializing binary content of element : " + BaseConstants.DEFAULT_BINARY_WRAPPER,
                                e);
                        throw new JMSException("Error serializing binary content of element : "
                                                       + BaseConstants.DEFAULT_BINARY_WRAPPER);
                    }
                }
            }

        } else if (JMSConstants.JMS_TEXT_MESSAGE.equals(jmsPayloadType)) {
            message = session.createTextMessage();
            TextMessage txtMsg = (TextMessage) message;
            txtMsg.setText(msgContext.getEnvelope().getBody().getFirstChildWithName(BaseConstants.DEFAULT_TEXT_WRAPPER)
                                   .getText());
        } else if (JMSConstants.JMS_MAP_MESSAGE.equalsIgnoreCase(jmsPayloadType)) {
            message = session.createMapMessage();
            JakartaUtils.convertXMLtoJMSMap(
                    msgContext.getEnvelope().getBody().getFirstChildWithName(JMSConstants.JMS_MAP_QNAME),
                    (MapMessage) message);
        }

        // set the JMS correlation ID if specified
        String correlationId = (String) synCtx.getProperty(JMSConstants.JMS_COORELATION_ID);
        if (correlationId != null) {
            message.setJMSCorrelationID(correlationId);
        }

        if (msgContext.isServerSide()) {
            // set SOAP Action as a property on the JMS message
            setProperty(message, msgContext, BaseConstants.SOAPACTION);
        } else {
            String action = msgContext.getOptions().getAction();
            if (action != null) {
                message.setStringProperty(BaseConstants.SOAPACTION, action);
            }
        }

        JakartaUtils.setTransportHeaders(msgContext, message);
        return message;
    }

    private void setProperty(Message message, org.apache.axis2.context.MessageContext msgCtx, String key) {

        String value = getProperty(msgCtx, key);
        if (value != null) {
            try {
                message.setStringProperty(key, value);
            } catch (JMSException e) {
                log.warn("Couldn't set message property : " + key + " = " + value, e);
            }
        }
    }

    private String getProperty(org.apache.axis2.context.MessageContext mc, String key) {
        return (String) mc.getProperty(key);
    }

    /**
     * check the first element of the SOAP body, do we have content wrapped
     * using the
     * default wrapper elements for binary
     * (BaseConstants.DEFAULT_BINARY_WRAPPER) or
     * text (BaseConstants.DEFAULT_TEXT_WRAPPER) ? If so, do not create SOAP
     * messages
     * for JMS but just get the payload in its native format
     * Guess the message type to use for JMS looking at the message contexts'
     * envelope
     *
     * @param msgContext the message context
     * @return JMSConstants.JMS_BYTE_MESSAGE or JMSConstants.JMS_TEXT_MESSAGE or
     * null
     */
    private String guessMessageType(org.apache.axis2.context.MessageContext msgContext) {
        OMElement firstChild = msgContext.getEnvelope().getBody().getFirstElement();
        if (firstChild != null) {
            if (BaseConstants.DEFAULT_BINARY_WRAPPER.equals(firstChild.getQName())) {
                return JMSConstants.JMS_BYTE_MESSAGE;
            } else if (BaseConstants.DEFAULT_TEXT_WRAPPER.equals(firstChild.getQName())) {
                return JMSConstants.JMS_TEXT_MESSAGE;
            } else if (JMSConstants.JMS_MAP_QNAME.equals(firstChild.getQName())) {
                return JMSConstants.JMS_MAP_MESSAGE;
            }
        }
        return null;
    }
}
