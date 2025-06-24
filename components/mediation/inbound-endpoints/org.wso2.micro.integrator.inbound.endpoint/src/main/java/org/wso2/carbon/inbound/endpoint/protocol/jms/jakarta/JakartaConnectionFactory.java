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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.inbound.endpoint.protocol.jms.JMSConstants;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueConnection;
import jakarta.jms.QueueConnectionFactory;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import jakarta.jms.TopicConnection;
import jakarta.jms.TopicConnectionFactory;

import javax.jms.QueueSession;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.util.Properties;

/**
 * use of factory server down and up jms spec transport.jms.MessageSelector
 * isDurable
 */

public class JakartaConnectionFactory implements ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory {
    private static final Log logger = LogFactory.getLog(JakartaConnectionFactory.class.getName());

    protected Context ctx;
    protected ConnectionFactory connectionFactory;
    protected String connectionFactoryString;

    protected JMSConstants.JMSDestinationType destinationType;

    private Destination destination;
    protected String destinationName;

    protected boolean transactedSession = false;
    protected int sessionAckMode = 0;
    protected boolean isDurable;
    protected boolean noPubSubLocal;

    protected String clientId;
    protected String subscriptionName;
    protected String messageSelector;
    protected boolean isSharedSubscription;

    public JakartaConnectionFactory(Properties properties) {
        try {
            ctx = new InitialContext(properties);
        } catch (NamingException e) {
            logger.error("NamingException while obtaining initial context. " + e.getMessage(), e);
        }

        String connectionFactoryType = properties.getProperty(JMSConstants.CONNECTION_FACTORY_TYPE);
        if ("topic".equals(connectionFactoryType)) {
            this.destinationType = JMSConstants.JMSDestinationType.TOPIC;
        } else {
            this.destinationType = JMSConstants.JMSDestinationType.QUEUE;
        }

        if ("true".equalsIgnoreCase(properties.getProperty(JMSConstants.PARAM_IS_SHARED_SUBSCRIPTION))) {
            isSharedSubscription = true;
        } else {
            isSharedSubscription = false;
        }

        noPubSubLocal = Boolean.valueOf(properties.getProperty(JMSConstants.PARAM_PUBSUB_NO_LOCAL));

        clientId = properties.getProperty(JMSConstants.PARAM_DURABLE_SUB_CLIENT_ID);
        subscriptionName = properties.getProperty(JMSConstants.PARAM_DURABLE_SUB_NAME);

        if (isSharedSubscription) {
            if (subscriptionName == null) {
                logger.info("Subscription name is not given. Therefor declaring a non-shared subscription");
                isSharedSubscription = false;
            }
        }

        String subDurable = properties.getProperty(JMSConstants.PARAM_SUB_DURABLE);
        if (subDurable != null) {
            isDurable = Boolean.parseBoolean(subDurable);
        }
        String msgSelector = properties.getProperty(JMSConstants.PARAM_MSG_SELECTOR);
        if (msgSelector != null) {
            messageSelector = msgSelector;
        }
        this.connectionFactoryString = properties.getProperty(JMSConstants.CONNECTION_FACTORY_JNDI_NAME);
        if (connectionFactoryString == null || "".equals(connectionFactoryString)) {
            connectionFactoryString = "QueueConnectionFactory";
        }

        this.destinationName = properties.getProperty(JMSConstants.DESTINATION_NAME);
        if (destinationName == null || "".equals(destinationName)) {
            destinationName = "QUEUE_" + System.currentTimeMillis();
        }

        String strTransactedSession = properties.getProperty(JMSConstants.SESSION_TRANSACTED);
        if (strTransactedSession == null || "".equals(strTransactedSession) || !strTransactedSession.equals("true")) {
            transactedSession = false;
        } else if ("true".equals(strTransactedSession)) {
            transactedSession = true;
            logger.warn(
                    "Usage of transport.jms.SessionTransacted property is deprecated. Please use SESSION_TRANSACTED "
                            + "acknowledge mode to create a transacted session");
        }

        String strSessionAck = properties.getProperty(JMSConstants.SESSION_ACK);
        if (null == strSessionAck) {
            sessionAckMode = 1;
        } else if (strSessionAck.equals("AUTO_ACKNOWLEDGE")) {
            sessionAckMode = Session.AUTO_ACKNOWLEDGE;
        } else if (strSessionAck.equals("CLIENT_ACKNOWLEDGE")) {
            sessionAckMode = Session.CLIENT_ACKNOWLEDGE;
        } else if (strSessionAck.equals("DUPS_OK_ACKNOWLEDGE")) {
            sessionAckMode = Session.DUPS_OK_ACKNOWLEDGE;
        } else if (strSessionAck.equals("SESSION_TRANSACTED")) {
            sessionAckMode = Session.SESSION_TRANSACTED;
            transactedSession = true;
        } else {
            sessionAckMode = 1;
        }

        createConnectionFactory();
    }

    public ConnectionFactory getConnectionFactory() {
        if (this.connectionFactory != null) {
            return this.connectionFactory;
        }

        return createConnectionFactory();
    }

    private ConnectionFactory createConnectionFactory() {
        if (this.connectionFactory != null) {
            return this.connectionFactory;
        }

        if (ctx == null) {
            return null;
        }

        try {
            if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                this.connectionFactory = (QueueConnectionFactory) ctx.lookup(this.connectionFactoryString);
            } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                this.connectionFactory = (TopicConnectionFactory) ctx.lookup(this.connectionFactoryString);
            }
        } catch (NamingException e) {
            logger.error(
                    "Naming exception while obtaining connection factory for '" + this.connectionFactoryString + "'",
                    e);
        }

        return this.connectionFactory;
    }

    public Connection getConnection() {
        return createConnection();
    }

    public Connection createConnection() {
        if (connectionFactory == null) {
            logger.error("Connection cannot be establish to the broker. Please check the broker libs provided.");
            return null;
        }
        Connection connection = null;
        try {

            if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                connection = ((QueueConnectionFactory) (this.connectionFactory)).createQueueConnection();
            } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                connection = ((TopicConnectionFactory) (this.connectionFactory)).createTopicConnection();
            }
            if (isDurable) {
                connection.setClientID(clientId);
            }
            return connection;

        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while creating connection through factory '" + this.connectionFactoryString + "' "
                            + e.getMessage(), e);
            // Need to close the connection in the case if durable subscriptions
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ex) {
                }
            }
        }

        return null;
    }

    public Connection createConnection(String userName, String password) {
        if (connectionFactory == null) {
            logger.error("Connection cannot be establish to the broker. Please check the broker libs provided.");
            return null;
        }
        Connection connection = null;
        try {
            if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                connection = ((QueueConnectionFactory) (this.connectionFactory))
                        .createQueueConnection(userName, password);
            } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                connection = ((TopicConnectionFactory) (this.connectionFactory))
                        .createTopicConnection(userName, password);
            }
            if (isDurable) {
                connection.setClientID(clientId);
            }
            return connection;
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while creating connection through factory '" + this.connectionFactoryString + "' "
                            + e.getMessage(), e);
            // Need to close the connection in the case if durable subscriptions
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ex) {
                }
            }
        }

        return null;
    }

    public QueueConnection createQueueConnection() throws JMSException {
        try {
            return ((QueueConnectionFactory) (this.connectionFactory)).createQueueConnection();
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while creating queue connection through factory '" + this.connectionFactoryString
                            + "' " + e.getMessage(), e);
        }
        return null;
    }

    public QueueConnection createQueueConnection(String userName, String password) throws JMSException {
        try {
            return ((QueueConnectionFactory) (this.connectionFactory)).createQueueConnection(userName, password);
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while creating queue connection through factory '" + this.connectionFactoryString
                            + "' " + e.getMessage(), e);
        }

        return null;
    }

    public TopicConnection createTopicConnection() throws JMSException {
        try {
            return ((TopicConnectionFactory) (this.connectionFactory)).createTopicConnection();
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while creating topic connection through factory '" + this.connectionFactoryString
                            + "' " + e.getMessage(), e);
        }

        return null;
    }

    public TopicConnection createTopicConnection(String userName, String password) throws JMSException {
        try {
            return ((TopicConnectionFactory) (this.connectionFactory)).createTopicConnection(userName, password);
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while creating topic connection through factory '" + this.connectionFactoryString
                            + "' " + e.getMessage(), e);
        }

        return null;
    }

    public Destination getDestination(Session session) {
        if (this.destination != null) {
            return this.destination;
        }

        return createDestination(session);
    }

    public MessageConsumer createMessageConsumer(Session session, Destination destination) {
        try {
            if (isSharedSubscription) {
                if (isDurable) {
                    return session.createSharedDurableConsumer((Topic) destination, subscriptionName, messageSelector);
                } else {
                    return session.createSharedConsumer((Topic) destination, subscriptionName, messageSelector);
                }
            } else {
                if (isDurable) {
                    return session.createDurableSubscriber((Topic) destination, subscriptionName, messageSelector,
                            noPubSubLocal);
                } else {
                    return session.createConsumer(destination, messageSelector);
                }
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while creating consumer. " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * This is a JMS spec independent method to create a MessageProducer. Please be cautious when
     * making any changes
     *
     * @param session     JMS session
     * @param destination the Destination
     * @param isQueue     is the Destination a queue?
     * @return a MessageProducer to send messages to the given Destination
     * @throws JMSException on errors, to be handled and logged by the caller
     */
    public MessageProducer createProducer(Session session, Destination destination, Boolean isQueue)
            throws JMSException {

        return session.createProducer(destination);
    }

    private Destination createDestination(Session session) {
        this.destination = createDestination(session, this.destinationName);
        return this.destination;
    }

    public Destination createDestination(Session session, String destinationName) {
        Destination destination = null;
        try {
            if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                destination = JakartaUtils.lookupJakartaDestination(ctx, destinationName, JMSConstants.DESTINATION_TYPE_QUEUE);
            } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                destination = JakartaUtils.lookupJakartaDestination(ctx, destinationName, JMSConstants.DESTINATION_TYPE_TOPIC);
            }
        } catch (NameNotFoundException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Could not find destination '" + destinationName + "' on connection factory for '"
                                     + this.connectionFactoryString + "'. " + e.getMessage());
                logger.debug("Creating destination '" + destinationName + "' on connection factory for '"
                                     + this.connectionFactoryString + ".");
            }
            try {
                if (this.destinationType.equals(JMSConstants.JMSDestinationType.QUEUE)) {
                    destination = (Queue) session.createQueue(destinationName);
                } else if (this.destinationType.equals(JMSConstants.JMSDestinationType.TOPIC)) {
                    destination = (Topic) session.createTopic(destinationName);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Created '" + destinationName + "' on connection factory for '"
                                         + this.connectionFactoryString + "'.");
                }
            } catch (JMSException e1) {
                logger.error("Could not find nor create '" + destinationName + "' on connection factory for '"
                                     + this.connectionFactoryString + "'. " + e1.getMessage(), e1);
            }

        } catch (NamingException e) {
            logger.error(
                    "Naming exception while obtaining connection factory for '" + this.connectionFactoryString + "' "
                            + e.getMessage(), e);
        }

        return destination;
    }

    public Session getSession(Connection connection) {
        return createSession(connection);
    }

    protected Session createSession(Connection connection) {
        try {
            return connection.createSession(transactedSession, sessionAckMode);
        } catch (JMSException e) {
            logger.error("JMS Exception while obtaining session for factory '" + this.connectionFactoryString + "' " + e
                    .getMessage(), e);
        }

        return null;
    }

    public void start(Connection connection) {
        try {
            connection.start();
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while starting connection for factory '" + this.connectionFactoryString + "' " + e
                            .getMessage(), e);
        }
    }

    public void stop(Connection connection) {
        try {
            connection.stop();
        } catch (JMSException e) {
            logger.error(
                    "JMS Exception while stopping connection for factory '" + this.connectionFactoryString + "' " + e
                            .getMessage(), e);
        }
    }

    public boolean closeConnection(Connection connection) {
        try {
            connection.close();
            return true;
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the connection.");
        }

        return false;
    }

    public Context getContext() {
        return this.ctx;
    }

    public JMSConstants.JMSDestinationType getDestinationType() {
        return this.destinationType;
    }

    public String getConnectionFactoryString() {
        return connectionFactoryString;
    }

    public boolean isTransactedSession() {
        return transactedSession;
    }

    public int getSessionAckMode() {
        return sessionAckMode;
    }

    public jakarta.jms.JMSContext createContext() {
        return connectionFactory.createContext();
    }

    public jakarta.jms.JMSContext createContext(int sessionMode) {
        return connectionFactory.createContext(sessionMode);
    }

    public jakarta.jms.JMSContext createContext(String userName, String password) {
        return connectionFactory.createContext(userName, password);
    }

    public jakarta.jms.JMSContext createContext(String userName, String password, int sessionMode) {
        return connectionFactory.createContext(userName, password, sessionMode);
    }

}
