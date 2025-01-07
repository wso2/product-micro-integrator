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
package org.wso2.micro.integrator.dataservices.core.description.config;

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.core.DBUtils;import org.wso2.micro.integrator.dataservices.core.DataServiceFault;import org.wso2.micro.integrator.dataservices.core.engine.DataService;import org.wso2.micro.integrator.dataservices.core.odata.MongoDataHandler;import org.wso2.micro.integrator.dataservices.core.odata.ODataDataHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * This class represents a MongoDB based data source configuration.
 */

public class MongoConfig extends Config {

    private static final Log log = LogFactory.getLog(
            MongoConfig.class);

    private final MongoClient mongoClient;

    private final MongoClientSettings mongoClientSettings;
    private final MongoDatabase mongoDatabase;

    public MongoConfig(DataService dataService, String configId, Map<String, String> properties, boolean odataEnable)
            throws DataServiceFault {
        super(dataService, configId, DBConstants.DataSourceTypes.MONGODB, properties, odataEnable);
        String serversParam = properties.get(DBConstants.MongoDB.SERVERS);
        if (DBUtils.isEmptyString(serversParam)) {
            throw new DataServiceFault("The data source param '" + DBConstants.MongoDB.SERVERS + "' is required");
        }
        String[] servers = serversParam.split(",");
        String database = properties.get(DBConstants.MongoDB.DATABASE);
        if (DBUtils.isEmptyString(database)) {
            throw new DataServiceFault("The data source param '" + DBConstants.MongoDB.DATABASE + "' is required");
        }
        try {
            String writeConcern = properties.get(DBConstants.MongoDB.WRITE_CONCERN);
            String readPref = properties.get(DBConstants.MongoDB.READ_PREFERENCE);
            List<ServerAddress> serverAddresses = createServerAddresses(servers);
            MongoCredential mongoCredentials = createCredential(properties);
            this.mongoClientSettings = extractMongoOptions(properties, writeConcern, readPref, serverAddresses,
                    mongoCredentials);
            this.mongoClient = createNewMongo(this.mongoClientSettings);
            this.mongoDatabase = this.getMongoClient().getDatabase(database);
        } catch (Exception e) {
            throw new DataServiceFault(e, DBConstants.FaultCodes.CONNECTION_UNAVAILABLE_ERROR, e.getMessage());
        }

    }

    public MongoClient createNewMongo(MongoClientSettings mongoClientSettings) throws DataServiceFault {
        try {
            return MongoClients.create(mongoClientSettings);
        } catch (Exception e) {
            throw new DataServiceFault(e);
        }
    }

    @Override
    public boolean isActive() {
        try {
            // TODO: Need to check the connection availability properly.
            MongoClient mongo = this.createNewMongo(this.mongoClientSettings);
            return mongo != null;
        } catch (Exception e) {
            log.error("Error in checking Mongo config availability", e);
            return false;
        }
    }

    @Override
    public void close() {
         /* nothing to close */
    }

    @Override
    public ODataDataHandler createODataHandler() {
        return new MongoDataHandler(getConfigId(), this.mongoDatabase);
    }

    private MongoClientSettings extractMongoOptions(Map<String, String> properties, String writeConcern,
                                                    String readPref, List<ServerAddress> serverAddresses, MongoCredential mongoCredentials) {
        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder();
        String connectionsPerHost = properties.get(DBConstants.MongoDB.CONNECTIONS_PER_HOST);
        if (!DBUtils.isEmptyString(connectionsPerHost)) {
            settingsBuilder.applyToConnectionPoolSettings(builder -> builder.maxSize(Integer.parseInt(connectionsPerHost)));
        }
        String maxWaitTime = properties.get(DBConstants.MongoDB.MAX_WAIT_TIME);
        if (!DBUtils.isEmptyString(maxWaitTime)) {
            settingsBuilder.applyToConnectionPoolSettings(builder -> builder.maxWaitTime(Integer.parseInt(maxWaitTime),
                    TimeUnit.MILLISECONDS));
        }
        String connectTimeout = properties.get(DBConstants.MongoDB.CONNECT_TIMEOUT);
        if (!DBUtils.isEmptyString(connectTimeout)) {
            settingsBuilder.applyToSocketSettings(builder -> builder.connectTimeout(Integer.parseInt(connectTimeout),
                    TimeUnit.MILLISECONDS));
        }
        String socketTimeout = properties.get(DBConstants.MongoDB.SOCKET_TIMEOUT);
        if (!DBUtils.isEmptyString(socketTimeout)) {
            settingsBuilder.applyToSocketSettings(builder -> builder.readTimeout(Integer.parseInt(socketTimeout),
                    TimeUnit.MILLISECONDS));
        }
        String sslEnabled = (properties.get(DBConstants.MongoDB.SSL_ENABLED));
        if (Boolean.parseBoolean(sslEnabled)) {
            settingsBuilder.applyToSslSettings(builder -> builder.enabled(true));
        }
        if (!DBUtils.isEmptyString(writeConcern)) {
            settingsBuilder.writeConcern(WriteConcern.valueOf(writeConcern.toUpperCase()));
        }
        if (!DBUtils.isEmptyString(readPref)) {
            settingsBuilder.readPreference(ReadPreference.valueOf(readPref));
        }
        settingsBuilder.applyToClusterSettings(builder -> builder.hosts(serverAddresses));
        if (mongoCredentials != null) {
            settingsBuilder.credential(mongoCredentials);
        }
        return settingsBuilder.build();
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    private List<ServerAddress> createServerAddresses(String[] servers) throws Exception {
        List<ServerAddress> result = new ArrayList<>();
        String[] tmpAddr;
        for (String server : servers) {
            tmpAddr = server.split(":");
            if (tmpAddr.length == 2) {
                result.add(new ServerAddress(tmpAddr[0], Integer.parseInt(tmpAddr[1])));
            } else {
                result.add(new ServerAddress(tmpAddr[0]));
            }
        }
        return result;
    }

    private MongoCredential createCredential(Map<String, String> properties) throws DataServiceFault {
        MongoCredential credential = null;
        String authenticationType = properties.get(DBConstants.MongoDB.AUTHENTICATION_TYPE);
        String username = properties.get(DBConstants.MongoDB.USERNAME);
        String password = properties.get(DBConstants.MongoDB.PASSWORD);
        String authSource = properties.get(DBConstants.MongoDB.AUTH_SOURCE);
        if (authSource == null || authSource.isEmpty()) {
            // For MONGODB-CR, SCRAM-SHA-1, and SCRAM-SHA-256, PLAIN the default auth source is the database tyring to connect
            // refer: https://docs.mongodb.com/ruby-driver/master/reference/authentication/
            // since database is mandatory, we will not address the case where DB is not defined.
            authSource = properties.get(DBConstants.MongoDB.DATABASE);
        }
        if (authenticationType != null) {
            switch (authenticationType) {
                case DBConstants.MongoDB.MongoAuthenticationTypes.PLAIN:
                    credential = MongoCredential.createPlainCredential(username, authSource, password.toCharArray());
                    break;
                case DBConstants.MongoDB.MongoAuthenticationTypes.SCRAM_SHA_1:
                    credential = MongoCredential.createScramSha1Credential(username, authSource, password.toCharArray());
                    break;
                case DBConstants.MongoDB.MongoAuthenticationTypes.GSSAPI:
                    credential = MongoCredential.createGSSAPICredential(username);
                    break;
                case DBConstants.MongoDB.MongoAuthenticationTypes.MONGODB_X509:
                    credential = MongoCredential.createMongoX509Credential(username);
                    break;
                default:
                    throw new DataServiceFault("Invalid Authentication type. ");
            }
            return credential;
        } else {
            return null;
        }
    }

    public MongoDatabase getMongoDatabase() {
        return mongoDatabase;
    }

    @Override
    public boolean isResultSetFieldsCaseSensitive() {
        return true;
    }
}
