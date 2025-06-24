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
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.core.axis2.Axis2MessageContext;

import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.StreamMessage;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import org.wso2.carbon.inbound.endpoint.protocol.jms.JMSConstants;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.Reference;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * Maintain the common methods used by inbound JMS protocol
 */
public class JakartaUtils {

    private static final Log log = LogFactory.getLog(JakartaUtils.class);

    /**
     * Method to infer the JMS message type.
     *
     * @param msg the message to be inferred
     * @return the type of the JMS message
     */
    public static String inferJMSMessageType(Message msg) {
        if (isTextMessage(msg)) {
            return TextMessage.class.getName();
        } else if (isBytesMessage(msg)) {
            return BytesMessage.class.getName();
        } else if (isObjectMessage(msg)) {
            return ObjectMessage.class.getName();
        } else if (isStreamMessage(msg)) {
            return StreamMessage.class.getName();
        } else if (isMapMessage(msg)) {
            return MapMessage.class.getName();
        } else {
            return null;
        }
    }

    public static void convertXMLtoJMSMap(OMElement element, MapMessage message) throws JMSException {

        Iterator itr = element.getChildElements();
        while (itr.hasNext()) {
            OMElement elem = (OMElement) itr.next();
            message.setString(elem.getLocalName(), elem.getText());
        }
    }

    /**
     * Check if a boolean property is set to the Synapse message context or Axis2 message context
     *
     * @param propertyName Name of the property
     * @param msgContext   Synapse messageContext instance
     * @return True if property is set to true
     */
    public static boolean checkIfBooleanPropertyIsSet(String propertyName, org.apache.synapse.MessageContext msgContext) {
        boolean isPropertySet = false;
        Object booleanProperty = msgContext.getProperty(propertyName);
        if (booleanProperty != null) {
            if ((booleanProperty instanceof Boolean && ((Boolean) booleanProperty)) ||
                    (booleanProperty instanceof String && Boolean.valueOf((String) booleanProperty))) {
                isPropertySet = true;
            }
        } else {
            // Then from axis2 context - This is for make it consistent with JMS Transport config parameters
            booleanProperty =
                    (((Axis2MessageContext) msgContext).getAxis2MessageContext()).getProperty(propertyName);
            if ((booleanProperty instanceof Boolean && ((Boolean) booleanProperty))
                    || (booleanProperty instanceof String && Boolean.valueOf((String) booleanProperty))) {
                isPropertySet = true;
            }
        }
        return isPropertySet;
    }

    /**
     * Set transport headers from the axis message context, into the JMS message
     *
     * @param msgContext the axis message context
     * @param message    the JMS Message
     * @throws JMSException on exception
     */
    public static void setTransportHeaders(MessageContext msgContext, Message message) throws JMSException {

        Map<?, ?> headerMap = (Map<?, ?>) msgContext.getProperty(MessageContext.TRANSPORT_HEADERS);

        if (headerMap == null) {
            return;
        }

        for (Object headerName : headerMap.keySet()) {

            String name = (String) headerName;

            if (name.startsWith(JMSConstants.JMSX_PREFIX) && !(name.equals(JMSConstants.JMSX_GROUP_ID) || name
                    .equals(JMSConstants.JMSX_GROUP_SEQ))) {
                continue;
            }

            if (JMSConstants.JMS_COORELATION_ID.equals(name)) {
                message.setJMSCorrelationID((String) headerMap.get(JMSConstants.JMS_COORELATION_ID));
            } else if (JMSConstants.JMS_DELIVERY_MODE.equals(name)) {
                Object header = headerMap.get(JMSConstants.JMS_DELIVERY_MODE);
                Integer value = parseHeaderToInt(header);
                if (value != null) {
                    message.setJMSDeliveryMode(value);
                }
            } else if (JMSConstants.JMS_EXPIRATION.equals(name)) {
                Object header = headerMap.get(JMSConstants.JMS_EXPIRATION);
                Long value = parseHeaderToLong(header);
                if (value != null) {
                    message.setJMSExpiration(value);
                }
            } else if (JMSConstants.JMS_MESSAGE_ID.equals(name)) {
                message.setJMSMessageID((String) headerMap.get(JMSConstants.JMS_MESSAGE_ID));
            } else if (JMSConstants.JMS_PRIORITY.equals(name)) {
                Object header = headerMap.get(JMSConstants.JMS_PRIORITY);
                Integer value = parseHeaderToInt(header);
                if (value != null) {
                    message.setJMSPriority(value);
                }
            } else if (JMSConstants.JMS_TIMESTAMP.equals(name)) {
                Object header = headerMap.get(JMSConstants.JMS_TIMESTAMP);
                Long value = parseHeaderToLong(header);
                if (value != null) {
                    message.setJMSTimestamp(value);
                }
            } else if (JMSConstants.JMS_MESSAGE_TYPE.equals(name)) {
                message.setJMSType((String) headerMap.get(JMSConstants.JMS_MESSAGE_TYPE));

            } else {
                Object value = headerMap.get(name);
                if (value instanceof String) {
                    if (name.contains("-")) {
                        if (isHyphenReplaceMode(msgContext)) { // we replace
                            message.setStringProperty(transformHyphenatedString(name), (String) value);
                        } else if (isHyphenDeleteMode(msgContext)) { // we skip
                            continue;
                        } else {
                            message.setStringProperty(name, (String) value);
                        }
                    } else {
                        message.setStringProperty(name, (String) value);
                    }
                } else if (value instanceof Boolean) {
                    message.setBooleanProperty(name, (Boolean) value);
                } else if (value instanceof Integer) {
                    message.setIntProperty(name, (Integer) value);
                } else if (value instanceof Long) {
                    message.setLongProperty(name, (Long) value);
                } else if (value instanceof Double) {
                    message.setDoubleProperty(name, (Double) value);
                } else if (value instanceof Float) {
                    message.setFloatProperty(name, (Float) value);
                }
            }
        }
    }

    private static Long parseHeaderToLong(Object header) {
        if (header instanceof Long) {
            return (Long) header;
        } else if (header instanceof String) {
            try {
                return Long.parseLong((String) header);
            } catch (NumberFormatException nfe) {
                log.warn("Invalid header ignored : " + header, nfe);
            }
        } else {
            log.warn("Invalid header ignored : " + header);
        }
        return null;
    }

    private static Integer parseHeaderToInt(Object header) {
        if (header instanceof Integer) {
            return (Integer) header;
        } else if (header instanceof String) {
            try {
                return Integer.parseInt((String) header);
            } catch (NumberFormatException nfe) {
                log.warn("Invalid header ignored : " + header, nfe);
            }
        } else {
            log.warn("Invalid header ignored : " + header);
        }
        return null;
    }

    /**
     * Return the JMS destination with the given destination name looked up from the context
     *
     * @param context         the Context to lookup
     * @param destinationName name of the destination to be looked up
     * @param destinationType type of the destination to be looked up
     * @return the JMS destination, or null if it does not exist
     */
    public static Destination lookupDestination(Context context, String destinationName, String destinationType)
            throws NamingException {
        if (destinationName == null) {
            return null;
        }

        try {
            return JakartaUtils.lookup(context, Destination.class, destinationName);
        } catch (NameNotFoundException e) {
            try {
                Properties initialContextProperties = new Properties();
                if (context.getEnvironment() != null) {
                    if (context.getEnvironment().get(JMSConstants.NAMING_FACTORY_INITIAL) != null) {
                        initialContextProperties.put(JMSConstants.NAMING_FACTORY_INITIAL,
                                                     context.getEnvironment().get(JMSConstants.NAMING_FACTORY_INITIAL));
                    }
                    if (context.getEnvironment().get(JMSConstants.CONNECTION_STRING) != null) {
                        initialContextProperties.put(JMSConstants.CONNECTION_STRING,
                                                     context.getEnvironment().get(JMSConstants.CONNECTION_STRING));
                    }
                    if (context.getEnvironment().get(JMSConstants.PROVIDER_URL) != null) {
                        initialContextProperties.put(JMSConstants.PROVIDER_URL,
                                                     context.getEnvironment().get(JMSConstants.PROVIDER_URL));
                    }
                }
                if (JMSConstants.DESTINATION_TYPE_TOPIC.equalsIgnoreCase(destinationType)) {
                    initialContextProperties.put(JMSConstants.TOPIC_PREFIX + destinationName, destinationName);
                } else if (JMSConstants.DESTINATION_TYPE_QUEUE.equalsIgnoreCase(destinationType)
                        || JMSConstants.DESTINATION_TYPE_GENERIC.equalsIgnoreCase(destinationType)) {
                    initialContextProperties.put(JMSConstants.QUEUE_PREFIX + destinationName, destinationName);
                }
                InitialContext initialContext = new InitialContext(initialContextProperties);
                try {
                    return JakartaUtils.lookup(initialContext, Destination.class, destinationName);
                } catch (NamingException e1) {
                    return JakartaUtils.lookup(context, Destination.class,
                                           (JMSConstants.DESTINATION_TYPE_TOPIC.equalsIgnoreCase(destinationType) ?
                                                   "dynamicTopics/" :
                                                   "dynamicQueues/") + destinationName);
                }

            } catch (NamingException x) {
                log.warn("Cannot locate destination : " + destinationName);
                throw x;
            }
        } catch (NamingException e) {
            log.warn("Cannot locate destination : " + destinationName, e);
            throw e;
        }
    }

    /**
     * Return the JMS destination with the given destination name looked up from the context
     *
     * @param context         the Context to lookup
     * @param destinationName name of the destination to be looked up
     * @param destinationType type of the destination to be looked up
     * @return the JMS destination, or null if it does not exist
     */
    public static jakarta.jms.Destination lookupJakartaDestination(Context context, String destinationName, String destinationType)
            throws NamingException {
        if (destinationName == null) {
            return null;
        }

        try {
            return JakartaUtils.lookup(context, jakarta.jms.Destination.class, destinationName);
        } catch (NameNotFoundException e) {
            try {
                Properties initialContextProperties = new Properties();
                if (context.getEnvironment() != null) {
                    if (context.getEnvironment().get(JMSConstants.NAMING_FACTORY_INITIAL) != null) {
                        initialContextProperties.put(JMSConstants.NAMING_FACTORY_INITIAL,
                                context.getEnvironment().get(JMSConstants.NAMING_FACTORY_INITIAL));
                    }
                    if (context.getEnvironment().get(JMSConstants.CONNECTION_STRING) != null) {
                        initialContextProperties.put(JMSConstants.CONNECTION_STRING,
                                context.getEnvironment().get(JMSConstants.CONNECTION_STRING));
                    }
                    if (context.getEnvironment().get(JMSConstants.PROVIDER_URL) != null) {
                        initialContextProperties.put(JMSConstants.PROVIDER_URL,
                                context.getEnvironment().get(JMSConstants.PROVIDER_URL));
                    }
                }
                if (JMSConstants.DESTINATION_TYPE_TOPIC.equalsIgnoreCase(destinationType)) {
                    initialContextProperties.put(JMSConstants.TOPIC_PREFIX + destinationName, destinationName);
                } else if (JMSConstants.DESTINATION_TYPE_QUEUE.equalsIgnoreCase(destinationType)
                        || JMSConstants.DESTINATION_TYPE_GENERIC.equalsIgnoreCase(destinationType)) {
                    initialContextProperties.put(JMSConstants.QUEUE_PREFIX + destinationName, destinationName);
                }
                InitialContext initialContext = new InitialContext(initialContextProperties);
                try {
                    return JakartaUtils.lookup(initialContext, jakarta.jms.Destination.class, destinationName);
                } catch (NamingException e1) {
                    return JakartaUtils.lookup(context, jakarta.jms.Destination.class,
                            (JMSConstants.DESTINATION_TYPE_TOPIC.equalsIgnoreCase(destinationType) ?
                                    "dynamicTopics/" :
                                    "dynamicQueues/") + destinationName);
                }

            } catch (NamingException x) {
                log.warn("Cannot locate destination : " + destinationName);
                throw x;
            }
        } catch (NamingException e) {
            log.warn("Cannot locate destination : " + destinationName, e);
            throw e;
        }
    }

    private static <T> T lookup(Context context, Class<T> clazz, String name) throws NamingException {

        Object object = context.lookup(name);
        try {
            return clazz.cast(object);
        } catch (ClassCastException ex) {
            // Instead of a ClassCastException, throw an exception with some
            // more information.
            if (object instanceof Reference) {
                Reference ref = (Reference) object;
                handleException("JNDI failed to de-reference Reference with name " + name + "; is the factory " + ref
                        .getFactoryClassName() + " in your classpath?");
                return null;
            } else {
                handleException(
                        "JNDI lookup of name " + name + " returned a " + object.getClass().getName() + " while a "
                                + clazz + " was expected");
                return null;
            }
        }
    }

    protected static void handleException(String s) throws NamingException {
        log.error(s);
        throw new NamingException(s);
    }

    /**
     * Extract transport level headers from JMS message into a Map
     *
     * @param message    JMS message
     * @param msgContext axis2 message context
     * @return a Map of the transport headers
     */
    public static Map<String, Object> getTransportHeaders(Message message, MessageContext msgContext) {
        // create a Map to hold transport headers
        Map<String, Object> map = new HashMap<String, Object>();

        // correlation ID
        try {
            if (message.getJMSCorrelationID() != null) {
                map.put(JMSConstants.JMS_COORELATION_ID, message.getJMSCorrelationID());
            }
        } catch (JMSException ignore) {
        }

        // set the delivery mode as persistent or not
        try {
            map.put(JMSConstants.JMS_DELIVERY_MODE, Integer.toString(message.getJMSDeliveryMode()));
        } catch (JMSException ignore) {
        }

        // destination name
        try {
            if (message.getJMSDestination() != null) {
                Destination dest = message.getJMSDestination();
                map.put(JMSConstants.JMS_DESTINATION,
                        dest instanceof Queue ?
                                ((Queue) dest).getQueueName() : ((Topic) dest).getTopicName());
            }
        } catch (JMSException ignore) {
        }

        // expiration
        try {
            map.put(JMSConstants.JMS_EXPIRATION, Long.toString(message.getJMSExpiration()));
        } catch (JMSException ignore) {
        }

        // if a JMS message ID is found
        try {
            if (message.getJMSMessageID() != null) {
                map.put(JMSConstants.JMS_MESSAGE_ID, message.getJMSMessageID());
            }
        } catch (JMSException ignore) {
        }

        // priority
        try {
            map.put(JMSConstants.JMS_PRIORITY, Long.toString(message.getJMSPriority()));
        } catch (JMSException ignore) {
        }

        // redelivered
        try {
            map.put(JMSConstants.JMS_REDELIVERED, Boolean.toString(message.getJMSRedelivered()));
        } catch (JMSException ignore) {
        }

        // replyto destination name
        try {
            if (message.getJMSReplyTo() != null) {
                Destination dest = message.getJMSReplyTo();
                map.put(JMSConstants.JMS_REPLY_TO,
                        dest instanceof Queue ?
                                ((Queue) dest).getQueueName() : ((Topic) dest).getTopicName());
            }
        } catch (JMSException ignore) {
        }

        // priority
        try {
            map.put(JMSConstants.JMS_TIMESTAMP, Long.toString(message.getJMSTimestamp()));
        } catch (JMSException ignore) {
        }

        // message type
        try {
            if (message.getJMSType() != null) {
                map.put(JMSConstants.JMS_TYPE, message.getJMSType());
            }
        } catch (JMSException ignore) {
        }

        try {
            Enumeration<?> propertyNamesEnm = message.getPropertyNames();

            while (propertyNamesEnm.hasMoreElements()) {
                String headerName = (String) propertyNamesEnm.nextElement();
                Object headerValue = message.getObjectProperty(headerName);

                if (headerValue instanceof String) {
                    if (isHyphenReplaceMode(msgContext)) {
                        map.put(inverseTransformHyphenatedString(headerName), message.getStringProperty(headerName));
                    } else {
                        map.put(headerName, message.getStringProperty(headerName));
                    }
                } else if (headerValue instanceof Integer) {
                    map.put(headerName, message.getIntProperty(headerName));
                } else if (headerValue instanceof Boolean) {
                    map.put(headerName, message.getBooleanProperty(headerName));
                } else if (headerValue instanceof Long) {
                    map.put(headerName, message.getLongProperty(headerName));
                } else if (headerValue instanceof Double) {
                    map.put(headerName, message.getDoubleProperty(headerName));
                } else if (headerValue instanceof Float) {
                    map.put(headerName, message.getFloatProperty(headerName));
                } else {
                    map.put(headerName, headerValue);
                }
            }

        } catch (JMSException e) {
            log.error("Error while reading the Transport Headers from JMS Message", e);
        }

        return map;
    }

    private static boolean isHyphenReplaceMode(MessageContext msgContext) {
        if (msgContext == null) {
            return false;
        }

        String hyphenSupport = (String) msgContext.getProperty(JMSConstants.PARAM_JMS_HYPHEN_MODE);
        if (hyphenSupport != null && hyphenSupport.equals(JMSConstants.HYPHEN_MODE_REPLACE)) {
            return true;
        }

        return false;
    }

    /**
     * This method is to fix ESBJAVA-3687 - certain brokers do not support '-' in JMS property name, in such scenarios
     * we will replace the dash with a special character sequence. This support is configurable and is turned off by
     * default.
     *
     * @return modified string name if broker does not support name format
     */
    private static String transformHyphenatedString(String name) {
        return name.replaceAll("-", JMSConstants.HYPHEN_REPLACEMENT_STR);
    }

    private static boolean isHyphenDeleteMode(MessageContext msgContext) {
        if (msgContext == null) {
            return false;
        }

        String hyphenSupport = (String) msgContext.getProperty(JMSConstants.PARAM_JMS_HYPHEN_MODE);
        if (hyphenSupport != null && hyphenSupport.equals(JMSConstants.HYPHEN_MODE_DELETE)) {
            return true;
        }

        return false;
    }

    /**
     * Method to infer if the message is a TextMessage.
     *
     * @param msg message to be inferred
     * @return whether or not the message is a TextMessage
     */
    private static boolean isTextMessage(Message msg) {
        return (msg instanceof TextMessage);
    }

    /**
     * Method to infer if the message is a MapMessage.
     *
     * @param msg message to be inferred
     * @return whether or not the message is a MapMessage
     */
    private static boolean isMapMessage(Message msg) {
        return (msg instanceof MapMessage);
    }

    /**
     * Method to infer if the message is a StreamMessage.
     *
     * @param msg message to be inferred
     * @return whether or not the message is a StreamMessage
     */
    private static boolean isStreamMessage(Message msg) {
        return (msg instanceof StreamMessage);
    }

    /**
     * Method to infer if the message is an ObjectMessage.
     *
     * @param msg message to be inferred
     * @return whether or not the message is an ObjectMessage
     */
    private static boolean isObjectMessage(Message msg) {
        return (msg instanceof ObjectMessage);
    }

    /**
     * Method to infer if the message is a BytesMessage.
     *
     * @param msg message to be inferred
     * @return whether or not the message is a BytesMessage
     */
    private static boolean isBytesMessage(Message msg) {
        return (msg instanceof BytesMessage);
    }

    private static String inverseTransformHyphenatedString(String name) {
        return name.replaceAll(JMSConstants.HYPHEN_REPLACEMENT_STR, "-");
    }
}
