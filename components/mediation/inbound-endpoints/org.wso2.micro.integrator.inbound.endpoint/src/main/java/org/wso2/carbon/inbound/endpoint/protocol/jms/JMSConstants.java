/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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
package org.wso2.carbon.inbound.endpoint.protocol.jms;

import javax.xml.namespace.QName;

/**
 * Common constants used by inbound JMS protocol
 */
public class JMSConstants {

    public static enum JMSDestinationType {
        QUEUE, TOPIC
    }

    ;

    // Fix with EI-1622
    // No. of retries for JMS client polling that occurs before suspending polling.
    public static final String JMS_CLIENT_POLLING_RETRIES_BEFORE_SUSPENSION = "transport.jms.RetriesBeforeSuspension";
    // Time in milliseconds for the polling suspension period.
    public static final String JMS_CLIENT_POLLING_SUSPENSION_PERIOD = "transport.jms.PollingSuspensionPeriod";
    // Default time in milliseconds for the polling suspension period.
    public static final int DEFAULT_JMS_CLIENT_POLLING_SUSPENSION_PERIOD = 60000;
    // This property need to be enabled if the connection need to be reset after polling suspension.
    public static final String JMS_CLIENT_CONNECTION_RESET_AFTER_POLLING_SUSPENSION = "transport.jms.ResetConnectionOnPollingSuspension";

    public static final String TOPIC_PREFIX = "topic.";
    public static final String QUEUE_PREFIX = "queue.";

    public static String JAVA_INITIAL_NAMING_FACTORY = "java.naming.factory.initial";
    public static String JAVA_NAMING_PROVIDER_URL = "java.naming.provider.url";

    public static String CONNECTION_FACTORY_JNDI_NAME = "transport.jms.ConnectionFactoryJNDIName";
    public static String CONNECTION_FACTORY_TYPE = "transport.jms.ConnectionFactoryType";
    public static String DESTINATION_NAME = "transport.jms.Destination";
    public static String DESTINATION_TYPE = "transport.jms.DestinationType";
    public static final String DESTINATION_TYPE_QUEUE = "queue";
    public static final String DESTINATION_TYPE_TOPIC = "topic";
    public static String SESSION_TRANSACTED = "transport.jms.SessionTransacted";
    public static String SESSION_ACK = "transport.jms.SessionAcknowledgement";
    public static String RECEIVER_TIMEOUT = "transport.jms.ReceiveTimeout";
    public static String CONTENT_TYPE = "transport.jms.ContentType";
    public static String CONTENT_TYPE_PROPERTY = "transport.jms.ContentTypeProperty";
    /**
     * Namespace for JMS map payload representation
     */
    public static final String JMS_MAP_NS = "http://axis.apache.org/axis2/java/transports/jms/map-payload";
    /**
     * Root element name of JMS Map message payload representation
     */
    public static final String JMS_MAP_ELEMENT_NAME = "JMSMap";
    public static final String SET_ROLLBACK_ONLY = "SET_ROLLBACK_ONLY";

    //Constant to represent jmsSession.recover()
    public static final String SET_RECOVER = "SET_RECOVER";

    public static final QName JMS_MAP_QNAME = new QName(JMS_MAP_NS, JMS_MAP_ELEMENT_NAME, "");
    /**
     * Constant that holds the name of the environment property
     * for specifying configuration information for the service provider
     * to use. The value of the property should contain a URL string
     * (e.g. "ldap://somehost:389").
     * This property may be specified in the environment,
     * an applet parameter, a system property, or a resource file.
     * If it is not specified in any of these sources,
     * the default configuration is determined by the service provider.
     *
     * <p> The value of this constant is "java.naming.provider.url".
     */
    public static final String PROVIDER_URL = "java.naming.provider.url";
    public static final String DESTINATION_TYPE_GENERIC = "generic";
    /**
     * Naming factory initial
     */
    public static final String NAMING_FACTORY_INITIAL = "java.naming.factory.initial";
    /**
     * Default Connection Factory
     */
    public static final String CONNECTION_STRING = "connectionfactory.QueueConnectionFactory";

    public static final String PARAM_CACHE_LEVEL = "transport.jms.CacheLevel";
    /**
     * A MessageContext property or client Option indicating the JMS message type
     */
    public static final String JMS_MESSAGE_TYPE = "JMS_MESSAGE_TYPE";
    /**
     * A MessageContext property or client Option indicating the JMS correlation id
     */
    public static final String JMS_COORELATION_ID = "JMS_COORELATION_ID";
    /**
     * The message type indicating a MapMessage. See {@link JMS_MESSAGE_TYPE}
     */
    public static final String JMS_MAP_MESSAGE = "JMS_MAP_MESSAGE";
    /**
     * The Service level Parameter name indicating the [default] response destination of a service
     */
    public static final String PARAM_REPLY_DESTINATION = "transport.jms.ReplyDestination";

    /**
     * The message type indicating a BytesMessage. See {@link JMS_MESSAGE_TYPE}
     */
    public static final String JMS_BYTE_MESSAGE = "JMS_BYTE_MESSAGE";
    /**
     * The message type indicating a TextMessage. See {@link JMS_MESSAGE_TYPE}
     */
    public static final String JMS_TEXT_MESSAGE = "JMS_TEXT_MESSAGE";

    public static final String PARAM_JMS_USERNAME = "transport.jms.UserName";
    /**
     * The password to use when obtaining a JMS Connection
     */
    public static final String PARAM_JMS_PASSWORD = "transport.jms.Password";
    /**
     * The parameter indicating the JMS API specification to be used - if this
     * is "1.1" the JMS 1.1 API would be used, else the JMS 1.0.2B
     */
    public static final String PARAM_JMS_SPEC_VER = "transport.jms.JMSSpecVersion";
    /**
     * Maximum number of shared JMS Connections when sending messages out
     */
    public static final String MAX_JMS_CONNECTIONS = "transport.jms.MaxJMSConnections";

    public static final String MAX_JMS_SESSIONS = "transport.jms.MaxJMSSessions";

    public static final String PARAM_SUB_DURABLE = "transport.jms.SubscriptionDurable";
    /**
     * The name for the durable subscription See {@link PARAM_SUB_DURABLE}
     */
    public static final String PARAM_DURABLE_SUB_NAME = "transport.jms.DurableSubscriberName";
    public static final String PARAM_DURABLE_SUB_CLIENT_ID = "transport.jms.DurableSubscriberClientID";

    /**
     * A message selector to be used when messages are sought for this service
     */
    public static final String PARAM_MSG_SELECTOR = "transport.jms.MessageSelector";
    /**
     * Should a pub-sub connection receive messages published by itself?
     */
    public static final String PARAM_PUBSUB_NO_LOCAL = "transport.jms.PubSubNoLocal";
    /**
     * Do not cache any JMS resources between tasks (when sending) or JMS CF's
     * (when sending)
     */
    public static final int CACHE_NONE = 0;
    /**
     * Cache only the JMS connection between tasks (when receiving), or JMS CF's
     * (when sending)
     */
    public static final int CACHE_CONNECTION = 1;
    /**
     * Cache only the JMS connection and Session between tasks (receiving), or
     * JMS CF's (sending)
     */
    public static final int CACHE_SESSION = 2;
    /**
     * Cache the JMS connection, Session and Consumer between tasks when
     * receiving
     */
    public static final int CACHE_CONSUMER = 3;
    /**
     * Cache the JMS connection, Session and Producer within a
     * JMSConnectionFactory when sending
     */
    public static final int CACHE_PRODUCER = 4;
    /**
     * automatic choice of an appropriate caching level (depending on the
     * transaction strategy)
     */
    public static final int CACHE_AUTO = 5;
    /**
     * The prefix that denotes JMSX properties
     */
    public static final String JMSX_PREFIX = "JMSX";
    /**
     * The JMSXGroupID property
     */
    public static final String JMSX_GROUP_ID = "JMSXGroupID";
    /**
     * A MessageContext property or client Option indicating the JMS destination to use on a Send
     */
    public static final String JMS_DESTINATION = "JMS_DESTINATION";
    /**
     * A MessageContext property or client Option indicating the JMS replyTo Destination
     */
    public static final String JMS_REPLY_TO = "JMS_REPLY_TO";
    /**
     * A MessageContext property indicating the JMS type String returned by {@link javax.jms.Message.getJMSType()}
     */
    public static final String JMS_TYPE = "JMS_TYPE";
    /**
     * A MessageContext property or client Option indicating the JMS delivery mode as an Integer or String
     * Value 1 - javax.jms.DeliveryMode.NON_PERSISTENT
     * Value 2 - javax.jms.DeliveryMode.PERSISTENT
     */
    public static final String JMS_DELIVERY_MODE = "JMS_DELIVERY_MODE";
    /**
     * The JMSXGroupSeq property
     */
    public static final String JMSX_GROUP_SEQ = "JMSXGroupSeq";
    /**
     * A MessageContext property or client Option indicating the JMS message expiration - a Long value
     * specified as a String
     */
    public static final String JMS_EXPIRATION = "JMS_EXPIRATION";
    /**
     * A MessageContext property or client Option indicating the JMS message id
     */
    public static final String JMS_MESSAGE_ID = "JMS_MESSAGE_ID";
    /**
     * A MessageContext property or client Option indicating the JMS priority
     */
    public static final String JMS_PRIORITY = "JMS_PRIORITY";
    /**
     * A MessageContext property or client Option indicating the JMS timestamp (Long specified as String)
     */
    public static final String JMS_TIMESTAMP = "JMS_TIMESTAMP";
    /**
     * A MessageContext property or client Option indicating the JMS message redelivered (boolean specified as String)
     */
    public static final String JMS_REDELIVERED = "JMS_REDELIVERED";

    /**
     * Does the JMS broker support hyphen in JMS message property names.
     */
    public static final String PARAM_JMS_HYPHEN_MODE = "transport.jms.MessagePropertyHyphens";

    public static final String HYPHEN_MODE_NONE = "none";

    public static final String HYPHEN_MODE_REPLACE = "replace";

    public static final String HYPHEN_MODE_DELETE = "delete";

    public static final String HYPHEN_REPLACEMENT_STR = "_DASHED_";

    public static final String DEFAULT_HYPHEN_SUPPORT = HYPHEN_MODE_NONE;

    public static final String JMS_RETRY_DURATION = "transport.jms.retry.duration";

    /**
     * JMS 2.0 Parameters
     */
    public static final String PARAM_IS_SHARED_SUBSCRIPTION = "transport.jms.SharedSubscription";

    public static final String DELIVERY_COUNT = "jms.message.delivery.count";

    public static final String JMS_SPEC_VERSION_1_0 = "1.0";

    public static final String JMS_SPEC_VERSION_1_1 = "1.1";

    public static final String JMS_SPEC_VERSION_2_0 = "2.0";

    public static final String JMS_SPEC_VERSION_3_1 = "3.1";

}
