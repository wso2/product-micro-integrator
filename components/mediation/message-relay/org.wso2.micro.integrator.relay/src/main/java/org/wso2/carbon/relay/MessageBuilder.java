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

import org.apache.axis2.builder.*;
import org.apache.axis2.json.*;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.AxisFault;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.axis2.transport.http.XFormURLEncodedFormatter;
import org.apache.axis2.transport.http.MultipartFormDataFormatter;
import org.apache.axis2.transport.http.ApplicationXMLFormatter;
import org.apache.axis2.transport.http.SOAPMessageFormatter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;

import javax.xml.stream.XMLStreamException;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.InputStream;

public class MessageBuilder {
    private static Log log = LogFactory.getLog(MessageBuilder.class);

    private Map<String, Builder> builders = new HashMap<String, Builder>();
    private Map<String, MessageFormatter> formatters = new HashMap<String, MessageFormatter>();

    public final static String RELAY_FORMATTERS_MAP = "__REALY_FORMATTERS_MAP";
    public final static String FORCED_RELAY_FORMATTER = "__FORCED_RELAY_FORMATTER";

    public MessageBuilder() {
        // first initilialize with the defualt builders
        builders.put("multipart/related", new MIMEBuilder());
        builders.put("application/soap+xml", new SOAPBuilder());
        builders.put("text/xml", new SOAPBuilder());
        builders.put("application/xop+xml", new MTOMBuilder());
        builders.put("application/xml", new ApplicationXMLBuilder());
        builders.put("application/x-www-form-urlencoded",
                new XFormURLEncodedBuilder());
        builders.put("application/json", new JSONBuilder());

        // initialize the default formatters
        formatters.put("application/x-www-form-urlencoded", new XFormURLEncodedFormatter());
        formatters.put("multipart/form-data", new MultipartFormDataFormatter());
        formatters.put("application/xml", new ApplicationXMLFormatter());
        formatters.put("text/xml", new SOAPMessageFormatter());
        formatters.put("application/soap+xml", new SOAPMessageFormatter());
        formatters.put("application/json", new JSONMessageFormatter());
    }

    public Map<String, Builder> getBuilders() {
        return builders;
    }

    public void addBuilder(String contentType, Builder builder) {
        builders.put(contentType, builder);
    }

    public void addFormatter(String contentType, MessageFormatter messageFormatter) {
        formatters.put(contentType, messageFormatter);
    }

    public Map<String, MessageFormatter> getFormatters() {
        return formatters;
    }

    public OMElement getDocument(String contentType, MessageContext msgCtx, InputStream in) throws
            XMLStreamException, AxisFault {
        OMElement element = null;
        Builder builder;
        if (contentType != null) {
            // try to get a builder from existing builders
            builder = getBuilderForContentType(contentType);
            if (builder != null) {
                try {
                    element = builder.processDocument(in, contentType, msgCtx);
                } catch (AxisFault axisFault) {
                    log.error("Error building message", axisFault);
                    throw axisFault;
                }
            }
        }

        if (element == null) {
            if (msgCtx.isDoingREST()) {
                try {
                    element = BuilderUtil.getPOXBuilder(in, null).getDocumentElement();
                } catch (XMLStreamException e) {
                    log.error("Errpr building message using POX Builder", e);
                    throw e;
                }
            } else {
                // switch to default
                builder = new SOAPBuilder();
                try {
                    element = builder.processDocument(in, contentType, msgCtx);
                } catch (AxisFault axisFault) {
                    log.error("Error building message using SOAP builder");
                    throw axisFault;
                }
            }
        }

        // build the soap headers and body
        if (element instanceof SOAPEnvelope) {
            SOAPEnvelope env = (SOAPEnvelope) element;
            env.hasFault();
        }

        return element;
    }

    private Builder getBuilderForContentType(String contentType) {
        String type;
        int index = contentType.indexOf(';');
        if (index > 0) {
            type = contentType.substring(0, index);
        } else {
            type = contentType;
        }

        Builder builder = builders.get(type);

        if (builder == null) {
            builder = builders.get(type.toLowerCase());
        }

        if (builder == null) {
            Iterator<Map.Entry<String, Builder>> iterator = builders.entrySet().iterator();
            while (iterator.hasNext() && builder == null) {
                Map.Entry<String, Builder> entry = iterator.next();
                String key = entry.getKey();
                if (contentType.matches(key)) {
                    builder = entry.getValue();
                }
            }
        }
        return builder;
    }

    public static Builder createBuilder(String className) throws AxisFault {
        try {
            Class c = Class.forName(className);
            Object o = c.newInstance();
            if (o instanceof Builder) {
                return (Builder) o;
            }
        } catch (ClassNotFoundException e) {
            handleException("Builder class not found :" +
                    className, e);
        } catch (IllegalAccessException e) {
            handleException("Cannot initiate Builder class :" +
                    className, e);
        } catch (InstantiationException e) {
            handleException("Cannot initiate Builder class :" +
                    className, e);
        }
        return null;
    }

    public static MessageFormatter createFormatter(String className) throws AxisFault {
        try {
            Class c = Class.forName(className);
            Object o = c.newInstance();
            if (o instanceof MessageFormatter) {
                return (MessageFormatter) o;
            }
        } catch (ClassNotFoundException e) {
            handleException("MessageFormatter class not found :" +
                    className, e);
        } catch (IllegalAccessException e) {
            handleException("Cannot initiate MessageFormatter class :" +
                    className, e);
        } catch (InstantiationException e) {
            handleException("Cannot initiate MessageFormatter class :" +
                    className, e);
        }
        return null;
    }

    private static void handleException(String message, Exception e) throws AxisFault {
        log.error(message, e);
        throw new AxisFault(message, e);
    }
}
