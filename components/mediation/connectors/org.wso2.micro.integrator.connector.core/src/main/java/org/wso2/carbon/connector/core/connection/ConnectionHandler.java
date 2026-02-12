/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.connector.core.connection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.wso2.carbon.connector.core.ConnectException;
import org.wso2.carbon.connector.core.pool.Configuration;
import org.wso2.carbon.connector.core.pool.ConnectionFactory;
import org.wso2.carbon.connector.core.pool.ConnectionPool;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.core.util.Constants;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Handles the connections
 */
public class ConnectionHandler implements LocalEntryUndeployCallBack {

    private static final Log log = LogFactory.getLog(ConnectionHandler.class);
    private static final ConnectionHandler handler;
    // Stores connections/connection pools against connection code name
    // defined as <connector_name>:<connection_name>
    private final Map<String, Object> connectionMap;
    private final Map<String, String> connectionLocalEntryMap;
    private final Map<String, ConnectionFactory> connectionFactoryMap;
    private final Map<String, Configuration> configurationMap;
    private final ConcurrentHashMap<String, LocalEntryUndeployObserver> observerMap = new ConcurrentHashMap();
    private SynapseConfiguration synapseConfiguration = null;

    private ReentrantLock lock = new ReentrantLock();
    private ReentrantLock poolLock = new ReentrantLock();

    static {
        handler = new ConnectionHandler();
    }

    private ConnectionHandler() {
        this.connectionMap = new ConcurrentHashMap<>();
        this.connectionLocalEntryMap = new ConcurrentHashMap<>();
        this.connectionFactoryMap = new ConcurrentHashMap<>();
        this.configurationMap = new ConcurrentHashMap<>();
    }

    /**
     * Gets the Connection Handler instance
     *
     * @return ConnectionHandler instance
     */
    public static ConnectionHandler getConnectionHandler() {

        return handler;
    }

    /**
     * Initialize local entry connection mapping
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @param messageContext Message Context
     */
    public void initializeLocalEntryConnectionMapping(String connector, String connectionName,
                                                      MessageContext messageContext) {
        String localEntryName = (String) ConnectorUtils.
                lookupTemplateParamater(messageContext, Constants.INIT_CONFIG_KEY);
        String uniqueConnectionName = getCode(connector, connectionName);
        if (localEntryName != null && !connectionLocalEntryMap.containsKey(uniqueConnectionName)) {
            connectionLocalEntryMap.put(uniqueConnectionName, localEntryName);
            if (!observerMap.containsKey(localEntryName)) {
                LocalEntryUndeployObserver localEntryUndeployObserver = new LocalEntryUndeployObserver(localEntryName);
                localEntryUndeployObserver.setCallback(this); // Set the callback reference
                SynapseConfiguration synapseConfig = messageContext.getEnvironment().getSynapseConfiguration();
                observerMap.put(localEntryName, localEntryUndeployObserver);
                synapseConfig.registerObserver(localEntryUndeployObserver);
                this.synapseConfiguration = synapseConfig;
            }
        }
    }

    @Override
    public void onLocalEntryUndeploy(String localEntryKey) {
        if (localEntryKey != null && connectionLocalEntryMap.containsValue(localEntryKey)) {
            removeLocalEntryConnections(localEntryKey);
            connectionLocalEntryMap.values().removeIf(value -> value.equals(localEntryKey));
            LocalEntryUndeployObserver localEntryUndeployObserver = this.observerMap.remove(localEntryKey);
            if (synapseConfiguration != null) {
                this.synapseConfiguration.unregisterObserver(localEntryUndeployObserver);
            }
        }
    }

    /**
     * Creates a new connection pool and stores the connection
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @param factory        Connection Factory that defines how to create connections
     * @param configuration  Configurations for the connection pool
     * @param messageContext Message Context
     */
    public void createConnection(String connector, String connectionName, ConnectionFactory factory,
                                 Configuration configuration, MessageContext messageContext) {
        initializeLocalEntryConnectionMapping(connector, connectionName, messageContext);
        configurationMap.putIfAbsent(getCode(connector, connectionName), configuration);
        connectionFactoryMap.putIfAbsent(getCode(connector, connectionName), factory);
        String key = getCode(connector, connectionName);
        ConnectionPool pool = (ConnectionPool) connectionMap.get(key);

        // Double-checked locking for thread safety
        if (pool == null) {
            poolLock.lock();
            try {
                pool = (ConnectionPool) connectionMap.get(key);  // Second check (inside lock)
                if (pool == null) {
                    log.info("Creating connection pool for " + connectionName);
                    pool = new ConnectionPool(factory, configuration);
                    connectionMap.putIfAbsent(key, pool);
                }
            } finally {
                poolLock.unlock();  // Always release lock
            }
        }
    }

    /**
     * Stores a new single connection
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @param connection     Connection to be stored
     * @param messageContext Message Context
     */
    public void createConnection(String connector, String connectionName, Connection connection
            , MessageContext messageContext) {
        initializeLocalEntryConnectionMapping(connector, connectionName, messageContext);
        connectionMap.putIfAbsent(getCode(connector, connectionName), connection);
    }

    /**
     * Creates a new connection pool and stores the connection
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @param factory        Connection Factory that defines how to create connections
     * @param configuration  Configurations for the connection pool
     */
    public void createConnection(String connector, String connectionName, ConnectionFactory factory,
                                 Configuration configuration) {
        configurationMap.putIfAbsent(getCode(connector, connectionName), configuration);
        connectionFactoryMap.putIfAbsent(getCode(connector, connectionName), factory);
        String key = getCode(connector, connectionName);
        ConnectionPool pool = (ConnectionPool) connectionMap.get(key);

        // Double-checked locking for thread safety
        if (pool == null) {
            poolLock.lock();
            try {
                pool = (ConnectionPool) connectionMap.get(key);  // Second check (inside lock)
                if (pool == null) {
                    log.info("Creating connection pool for " + connectionName);
                    pool = new ConnectionPool(factory, configuration);
                    connectionMap.putIfAbsent(key, pool);
                }
            } finally {
                poolLock.unlock();  // Always release lock
            }
        }
    }

    /**
     * Stores a new single connection
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @param connection     Connection to be stored
     */
    public void createConnection(String connector, String connectionName, Connection connection) {
        connectionMap.putIfAbsent(getCode(connector, connectionName), connection);
    }

    /**
     * Retrieve connection by connector name and connection name
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @return the connection
     * @throws ConnectException if failed to get connection
     */
    public Connection getConnection(String connector, String connectionName) throws ConnectException {

        Connection connection = null;
        String connectorCode = getCode(connector, connectionName);
        Object connectionObj = connectionMap.get(connectorCode);
        if (connectionObj != null) {
            if (connectionObj instanceof ConnectionPool) {
                if (((ConnectionPool) connectionObj).isAgedTimeoutEnabled()) {
                    closeAgedConnectionPoolGracefully(connectorCode);
                    if (!connectionMap.containsKey(connectorCode)) {
                        ConnectionPool pool = new ConnectionPool(connectionFactoryMap.get(connectorCode),
                                configurationMap.get(connectorCode));
                        connectionMap.putIfAbsent(connectorCode, pool);
                    }
                }
                connection = (Connection) ((ConnectionPool) connectionMap.get(connectorCode)).borrowObject();
            } else if (connectionObj instanceof Connection) {
                connection = (Connection) connectionObj;
            }
        } else {
            throw new ConnectException(format("Error occurred during retrieving connection. " +
                    "Connection %s for %s connector does not exist.", connectionName, connector));
        }
        return connection;
    }

    /**
     * Closes the connection.
     *
     * @param connectorCode String Object
     */
    private void closeAgedConnectionPoolGracefully(String connectorCode) {
        Instant current = Instant.now();
        if (((ConnectionPool)connectionMap.get(connectorCode)).isPoolExpired(current)) {
            lock.lock();
            try {
                if (connectionMap.get(connectorCode) != null && ((ConnectionPool)connectionMap.get(connectorCode)).isPoolExpired(current)) {
                    ((ConnectionPool)connectionMap.get(connectorCode)).close();
                    connectionMap.remove(connectorCode);
                }
            } catch (ConnectException e) {
                log.error("Failed to close connection pool. ", e);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Return borrowed connection
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @param connection     Connection to be returned to the pool
     */
    public void returnConnection(String connector, String connectionName, Connection connection) {

        String connectorCode = this.getCode(connector, connectionName);
        Object connectionObj = this.connectionMap.get(connectorCode);
        if (connectionObj instanceof ConnectionPool) {
            ((ConnectionPool) connectionObj).returnObject(connection);
        }
    }

    /**
     * Shutdown all the connection pools
     * and unregister from the handler.
     */
    public void shutdownConnections() {

        for (Map.Entry<String, Object> connection : connectionMap.entrySet()) {
            closeConnection(connection.getKey(), connection.getValue());
        }
        connectionMap.clear();
    }

    /**
     * Shutdown connection pools for a specified connector
     * and unregister from the handler.
     *
     * @param connector Name of the connector
     */
    public void shutdownConnections(String connector) {

        Iterator<Map.Entry<String, Object>> it = connectionMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> connection = it.next();
            if (connection.getKey().split(":")[0].equals(connector)) {
                closeConnection(connection.getKey(), connection.getValue());
                it.remove();
            }
        }
    }

    /**
     * remove local entry and associate connections from local entry store and connection map
     * @param localEntryName
     */
    public void removeLocalEntryConnections(String localEntryName) {
        Set<String> keysToRemove = connectionLocalEntryMap.entrySet().stream()
                .filter(entry -> localEntryName.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Now close each connection and remove the entry from the connectionMap
        keysToRemove.forEach(key -> {
            Object connection = connectionMap.get(key);
            if (connection != null) {
                closeConnection(connection);
                connectionMap.remove(key);
            }
        });
    }

    /**
     * Check if a connection exists for the connector by the same connection name
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @return true if a connection exists, false otherwise
     */
    public boolean checkIfConnectionExists(String connector, String connectionName) {

        return connectionMap.containsKey(getCode(connector, connectionName));
    }

    /**
     * Closes the connection.
     *
     * @param conName       Name of connection entry
     * @param connectionObj Connection Object
     */
    private void closeConnection(String conName, Object connectionObj) {
        if (connectionObj instanceof ConnectionPool) {
            try {
                ((ConnectionPool) connectionObj).close();
            } catch (ConnectException e) {
                log.error("Failed to close connection pool. ", e);
            }
        } else if (connectionObj instanceof Connection) {
            try {
                ((Connection) connectionObj).close();
            } catch (ConnectException e) {
                log.error("Failed to close connection " + conName, e);
            }
        }
    }

    /**
     * Closes the connection.
     *
     * @param connectionObj Connection Object
     */
    private void closeConnection(Object connectionObj) {
        if (connectionObj instanceof ConnectionPool) {
            try {
                ((ConnectionPool) connectionObj).close();
            } catch (ConnectException e) {
                log.error("Failed to close connection pool. ", e);
            }
        } else if (connectionObj instanceof Connection) {
            try {
                ((Connection) connectionObj).close();
            } catch (ConnectException e) {
                log.error("Failed to close connection ", e);
            }
        }
    }

    /**
     * Retrieves the connection code defined as <connector_name>:<connection_name>
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @return the connector code
     */
    private String getCode(String connector, String connectionName) {

        return format("%s:%s", connector, connectionName);
    }

    /**
     * Retrieves whether the connection pool or not
     *
     * @param connector      Name of the connector
     * @param connectionName Name of the connection
     * @return the connection pool status
     */
    public boolean getStatusOfConnection(String connector, String connectionName) {
        String connectorCode = getCode(connector, connectionName);
        Object connectionObj = connectionMap.get(connectorCode);
        if (connectionObj != null) {
            if (connectionObj instanceof ConnectionPool) {
                return true;
            }
        }
        return false;
    }

}
