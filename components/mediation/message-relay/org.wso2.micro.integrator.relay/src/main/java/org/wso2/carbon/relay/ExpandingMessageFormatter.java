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
package org.wso2.carbon.relay;

import org.apache.axiom.om.*;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.SOAPMessageFormatter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;


public class ExpandingMessageFormatter extends SOAPMessageFormatter {

    private Log log = LogFactory.getLog(ExpandingMessageFormatter.class);

    private static final String MESSAGE_AS_BYTES = "MESSAGE_AS_BYTES";

    @Override
    public byte[] getBytes(MessageContext messageContext, OMOutputFormat format)
            throws AxisFault {
        SOAPEnvelope envelope = messageContext.getEnvelope();
        if (hasASoapMessageEmbeded(envelope)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            findAndWrite2OutputStream(messageContext, out, false);
            return out.toByteArray();
        } else {
            return super.getBytes(messageContext, format);
        }
    }

    @Override
    public String getContentType(MessageContext msgCtxt, OMOutputFormat format,
                                 String soapActionString) {
        String contentType = (String) msgCtxt.getProperty(Constants.Configuration.CONTENT_TYPE);
        if (contentType == null) {
            MessageFormatter formatter = getMessageFormatter(msgCtxt);
            if (formatter != null) {
                contentType = formatter.getContentType(msgCtxt, format,  soapActionString);
            } else {
                String messageType = getMessageFormatterProperty(msgCtxt);
                if (messageType.equals(HTTPConstants.MEDIA_TYPE_X_WWW_FORM)) {
                    contentType = messageType;
                    String encoding = format.getCharSetEncoding();
                    if (encoding != null) {
                        contentType += "; charset=" + encoding;
                    }
                } else {
                    contentType = super.getContentType(msgCtxt, format, soapActionString);
                }
            }
        }
        return contentType;
    }

    @Override
    public void writeTo(MessageContext messageContext, OMOutputFormat format,
                        OutputStream outputStream, boolean preserve) throws AxisFault {

        try {
            byte[] messageAsBytes = (byte[]) messageContext.getOperationContext().
                    getProperty(MESSAGE_AS_BYTES);
            if (messageAsBytes != null) {
                outputStream.write(messageAsBytes);
            } else {
                SOAPEnvelope envelope = messageContext.getEnvelope();
                Object forcedFormatter;
                if (hasASoapMessageEmbeded(envelope)) {
                    findAndWrite2OutputStream(messageContext, outputStream, preserve);
                } else if ((forcedFormatter = messageContext.
                        getProperty(MessageBuilder.FORCED_RELAY_FORMATTER)) != null) {
                    /** If a formatter is forced by the builder mediator or some other means,
                     *  it gets preference */
                    if (forcedFormatter instanceof MessageFormatter) {
                        ((MessageFormatter) forcedFormatter).writeTo(messageContext, format, outputStream, preserve);
                    } else {
                        String msg = "Invalid formatter is forced by " +
                                MessageBuilder.FORCED_RELAY_FORMATTER + " property.";
                        log.error(msg);
                        throw new AxisFault(msg);
                    }
                } else {
                    // try to get the formatters from the map set by the builder mediator or
                    // SkipAdminHandler
                    MessageFormatter formatter = getMessageFormatter(messageContext);
                    if (formatter != null) {
                        formatter.writeTo(messageContext, format, outputStream, preserve);
                    } else {
                        super.writeTo(messageContext, format, outputStream, preserve);
                    }
                }
            }
        } catch (IOException e) {
            throw AxisFault.makeFault(e);
        }
    }

    @Override
    public URL getTargetAddress(MessageContext msgCtxt, OMOutputFormat format, URL targetURL) throws AxisFault {
        if (!msgCtxt.isDoingREST()) {
            return super.getTargetAddress(msgCtxt, format, targetURL);
        } 

        return targetURL;
    }

    public void writeAsREST(MessageContext messageContext, OMOutputFormat format,
                            OutputStream outputStream, boolean preserve) throws AxisFault {
        OMElement element = messageContext.getEnvelope().getBody().getFirstElement();
        try {
            if (element != null) {
                if (preserve) {
                    element.serialize(outputStream, format);
                } else {
                    element.serializeAndConsume(outputStream, format);
                }
            }
            outputStream.flush();
        } catch (XMLStreamException e) {
            String msg = "Error writing Rest message";
            log.error(msg, e);
            throw new AxisFault(msg, e);
        } catch (IOException e) {
            String msg = "Error writing text message to stream";
            log.error(msg);
            throw new AxisFault(msg, e);
        }
    }

    private boolean hasASoapMessageEmbeded(SOAPEnvelope envelope) {
        OMElement contentEle = envelope.getBody().getFirstElement();
        return contentEle != null && contentEle.getQName().equals(RelayConstants.BINARY_CONTENT_QNAME);
    }

    private void findAndWrite2OutputStream(MessageContext messageContext,
                                           OutputStream out,
                                           boolean preserve) throws AxisFault {
        try {
            SOAPEnvelope envelope = messageContext.getEnvelope();
            OMElement contentEle = envelope.getBody().getFirstElement();
            if (contentEle != null) {
                OMNode node = contentEle.getFirstOMChild();
                if (!(node instanceof OMText)) {
                    String msg = "Wrong Input for the Validator, " +
                            "the content of the first child element of the Body " +
                            "should have the zip file";
                    log.error(msg);
                    throw new AxisFault(msg);
                }
                OMText binaryDataNode = (OMText) node;
                DataHandler dh = (DataHandler) binaryDataNode.getDataHandler();

                DataSource dataSource = dh.getDataSource();
                if (dataSource instanceof StreamingOnRequestDataSource) {
                    if (((StreamingOnRequestDataSource) dataSource).isConsumed()) {
                        Object httpMethodObj = messageContext.getProperty(Constants.Configuration.HTTP_METHOD);
                        if ((httpMethodObj instanceof String) &&
                                Constants.Configuration.HTTP_METHOD_POST.equals(httpMethodObj)) {
                            log.warn("Attempting to send an already consumed request [POST/Empty Message Body]");
                        }
                    } else {
                        //Ask the data source to stream, if it has not already cached the request
                        if (!preserve) {
                            ((StreamingOnRequestDataSource) dataSource).setLastUse(true);
                        }
                    }
                }
                dh.writeTo(out);
            }
        } catch (OMException e) {
            log.error(e);
            throw AxisFault.makeFault(e);
        } catch (IOException e) {
            log.error(e);
            throw AxisFault.makeFault(e);
        }
    }

    private static String getMessageFormatterProperty(MessageContext msgContext) {
        String messageFormatterProperty = null;
        Object property = msgContext
                .getProperty(Constants.Configuration.MESSAGE_TYPE);
        if (property != null) {
            messageFormatterProperty = (String) property;
        }
        if (messageFormatterProperty == null) {
            Parameter parameter = msgContext
                    .getParameter(Constants.Configuration.MESSAGE_TYPE);
            if (parameter != null) {
                messageFormatterProperty = (String) parameter.getValue();
            }
        }
        return messageFormatterProperty;
    }

    private static MessageFormatter getMessageFormatter(MessageContext messageContext) {

        Object o = messageContext.getProperty(MessageBuilder.RELAY_FORMATTERS_MAP);
        if (o != null && o instanceof Map) {
            Map map = (Map) o;
            String messageFormatString =
                    getMessageFormatterProperty(messageContext);
            if (messageFormatString != null) {
                return (MessageFormatter)
                        map.get(messageFormatString);
            }
        }

        return null;
    }
}
