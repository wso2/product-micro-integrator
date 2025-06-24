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
import jakarta.jms.Session;
import java.util.Properties;

public class CachedJakartaConnectionFactory extends JakartaConnectionFactory {
    private static final Log logger = LogFactory.getLog(CachedJakartaConnectionFactory.class);

    private int cacheLevel = 0;

    private Connection cachedConnection = null;
    private Session cachedSession = null;
    private MessageConsumer cachedMessageConsumer = null;

    public CachedJakartaConnectionFactory(Properties properties) {
        super(properties);
        setValues(properties);
    }

    public CachedJakartaConnectionFactory(Properties properties, Connection cachedConnection) {
        super(properties);
        this.cachedConnection = cachedConnection;
        setValues(properties);
    }

    private void setValues(Properties properties) {
        String cacheLevel = properties.getProperty(JMSConstants.PARAM_CACHE_LEVEL);
        if (null != cacheLevel && !"".equals(cacheLevel)) {
            this.cacheLevel = Integer.parseInt(cacheLevel);
        } else {
            this.cacheLevel = JMSConstants.CACHE_NONE;
        }
    }

    @Override
    public ConnectionFactory getConnectionFactory() {
        return super.getConnectionFactory();
    }

    public Connection getConnection(String userName, String password) {
        Connection connection;
        if (cachedConnection == null) {
            connection = createConnection(userName, password);
        } else {
            connection = cachedConnection;
        }
        if (connection == null) {
            return null;
        }
        try {
            connection.start();
        } catch (JMSException e) {
            logger.error("JMS Exception while starting connection for factory '" + this.connectionFactoryString + "' ",
                         e);
            resetCache();
            return null;
        }
        return connection;
    }

    @Override
    public Connection createConnection(String userName, String password) {
        Connection connection;
        if (userName == null || password == null) {
            connection = super.createConnection();
        } else {
            connection = super.createConnection(userName, password);
        }
        if (this.cacheLevel >= JMSConstants.CACHE_CONNECTION) {
            cachedConnection = connection;
        }
        return connection;
    }

    @Override
    public Session getSession(Connection connection) {
        if (cachedSession == null) {
            return createSession(connection);
        } else {
            return cachedSession;
        }
    }

    @Override
    protected Session createSession(Connection connection) {
        Session session = super.createSession(connection);
        if (this.cacheLevel >= JMSConstants.CACHE_SESSION) {
            cachedSession = session;
        }
        return session;
    }

    public MessageConsumer getMessageConsumer(Session session, Destination destination) {
        MessageConsumer messageConsumer = null;
        if (cachedMessageConsumer == null) {
            messageConsumer = createMessageConsumer(session, destination);
        } else {
            messageConsumer = cachedMessageConsumer;
        }
        return messageConsumer;
    }

    public MessageConsumer createMessageConsumer(Session session, Destination destination) {
        MessageConsumer messageConsumer = super.createMessageConsumer(session, destination);
        if (this.cacheLevel >= JMSConstants.CACHE_CONSUMER) {
            cachedMessageConsumer = messageConsumer;
        }
        return messageConsumer;
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
        return super.createProducer(session, destination, isQueue);
    }

    /**
     * Recover JMS session
     *
     * @param session    JMS session to issue recover() on
     * @param forcefully True if recover needs to be done without conditions. Otherwise, recover
     *                   will be done based on cache level
     * @throws JMSException Upon error recovering the session
     */
    public void recoverSession(Session session, boolean forcefully) throws JMSException {
        if (this.cacheLevel >= JMSConstants.CACHE_SESSION || forcefully) {
            if (logger.isDebugEnabled()) {
                logger.debug("Recovered JMS session");
            }
            session.recover();
        }
    }

    public boolean closeConnection() {
        try {
            if (cachedConnection != null) {
                cachedConnection.close();
                cachedConnection = null;
            }
            return true;
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the connection.", e);
        }
        return false;
    }

    public boolean closeConnection(Connection connection) {
        return closeConnection(connection, false);
    }

    public boolean closeConsumer(MessageConsumer messageConsumer) {
        return closeConsumer(messageConsumer, false);
    }

    public boolean closeSession(Session session) {
        return closeSession(session, false);
    }

    public boolean closeConnection(Connection connection, boolean forcefully) {
        try {
            if (this.cacheLevel < JMSConstants.CACHE_CONNECTION || forcefully) {
                connection.close();
                cachedConnection = null;
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the connection.", e);
        }
        return false;
    }

    public boolean closeConsumer(MessageConsumer messageConsumer, boolean forcefully) {
        try {
            if (this.cacheLevel < JMSConstants.CACHE_CONSUMER || forcefully) {
                messageConsumer.close();
                cachedMessageConsumer = null;
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the consumer.", e);
        }
        return false;
    }

    public boolean closeSession(Session session, boolean forcefully) {
        try {
            if (this.cacheLevel < JMSConstants.CACHE_SESSION || forcefully) {
                session.close();
                cachedSession = null;
            }
        } catch (JMSException e) {
            logger.error("JMS Exception while closing the consumer.", e);
        }
        return false;
    }

    private void resetCache() {
        if (cachedConnection != null) {
            try {
                cachedConnection.close();
            } catch (JMSException e) {
            }
            cachedConnection = null;
        }
        if (cachedSession != null) {
            try {
                cachedSession.close();
            } catch (JMSException e) {
            }
            cachedSession = null;
        }
        if (cachedMessageConsumer != null) {
            try {
                cachedMessageConsumer.close();
            } catch (JMSException e) {
            }
            cachedMessageConsumer = null;
        }
    }

    public JMSConstants.JMSDestinationType getDestinationType() {
        return this.destinationType;
    }
}
