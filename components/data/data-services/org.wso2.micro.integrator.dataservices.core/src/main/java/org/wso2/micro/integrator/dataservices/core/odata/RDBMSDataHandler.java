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

package org.wso2.micro.integrator.dataservices.core.odata;

import org.apache.axis2.databinding.utils.ConverterUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.olingo.server.api.uri.queryoption.OrderByItem;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.core.odata.DataColumn.ODataDataType;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.engine.DataEntry;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class implements RDBMS datasource related operations for ODataDataHandler.
 *
 * @see ODataDataHandler
 */
public class RDBMSDataHandler implements ODataDataHandler {
    private static final Log log = LogFactory.getLog(RDBMSDataHandler.class);
    /**
     * Table metadata.
     */
    private Map<String, Map<String, Integer>> rdbmsDataTypes;

    private Map<String, Map<String, DataColumn>> tableMetaData;

    /**
     * Primary Keys of the Tables (Map<Table Name, List>).
     */
    private Map<String, List<String>> primaryKeys;

    /**
     * Config ID.
     */
    private final String configID;

    /**
     * RDBMS datasource.
     */
    private final DataSource dataSource;

    /**
     * List of Tables in the Database.
     */
    private List<String> tableList;

    public static final String TABLE_NAME = "TABLE_NAME";
    public static final String TABLE = "TABLE";
    public static final String VIEW = "VIEW";
    public static final String ORACLE_SERVER = "oracle";
    public static final String MSSQL_SERVER = "microsoft sql server";

    /**
     * Preferred chunk size.
     */
    private final int chunkSize;

    /**
     * Database connection for streaming.
     */
    private Connection streamConnection;

    /**
     * Result Set for streaming.
     */
    private ResultSet streamResultSet;

    /**
     * Prepared statement for streaming.
     */
    private PreparedStatement preparedStatement;

    /**
     * To indicate initialization phase of streaming.
     */
    private boolean initializeStream;

    private ThreadLocal<Connection> transactionalConnection = new ThreadLocal<Connection>() {
        protected synchronized Connection initialValue() {
            return null;
        }
    };

    private boolean defaultAutoCommit;
    private int defaultTransactionalIsolation;

    /**
     * Navigation properties map <Target Table Name, Map<Source Table Name, List<String>).
     */
    private Map<String, NavigationTable> navigationProperties;

    public RDBMSDataHandler(DataSource dataSource, String configId) throws ODataServiceFault {
        this.dataSource = dataSource;
        this.tableList = generateTableList();
        this.configID = configId;
        this.rdbmsDataTypes = new HashMap<>(this.tableList.size());
        initializeMetaData();
        this.initializeStream = false;
        this.chunkSize = ODataAdapter.getChunkSize();
    }

    @Override
    public Map<String, NavigationTable> getNavigationProperties() {
        return this.navigationProperties;
    }

    @Override
    public void openTransaction() throws ODataServiceFault {
        try {
            if (getTransactionalConnection() == null) {
                Connection connection = this.dataSource.getConnection();
                this.defaultAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                this.defaultTransactionalIsolation = connection.getTransactionIsolation();
                try {
                    connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                } catch (SQLException e) {
                    // Some Databases are not supported REPEATABLE_READ Isolation level.
                    connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                }
                transactionalConnection.set(connection);
            }
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Connection Error occurred. :" + e.getMessage());
        }
    }

    @Override
    public void commitTransaction() throws ODataServiceFault {
        Connection connection = getTransactionalConnection();
        try {
            connection.commit();
            connection.setTransactionIsolation(defaultTransactionalIsolation);
            connection.setAutoCommit(defaultAutoCommit);
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Connection Error occurred while committing. :" + e.getMessage());
        } finally {
        /* close the connection */
            try {
                connection.close();
                transactionalConnection.set(null);
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    private Connection getTransactionalConnection() {
        return transactionalConnection.get();
    }

    @Override
    public void rollbackTransaction() throws ODataServiceFault {
        Connection connection = getTransactionalConnection();
        try {
            connection.rollback();
            connection.setTransactionIsolation(defaultTransactionalIsolation);
            connection.setAutoCommit(defaultAutoCommit);
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Connection Error occurred while rollback. :" + e.getMessage());
        } finally {
		/* close the connection */
            try {
                connection.close();
                transactionalConnection.set(null);
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    @Override
    public void updateReference(String rootTable, ODataEntry rootTableKeys, String navigationTable,
                                ODataEntry navigationTableKeys) throws ODataServiceFault {
		/* To add a reference first we need to find the foreign key values of the tables,
		and therefore we need to identify which table has been exported */
        // Identifying the exported table and change the imported tables' column value
        NavigationTable navigation = navigationProperties.get(rootTable);
        boolean rootTableExportedColumns = false;
        if (navigation != null && navigation.getTables().contains(navigationTable)) {
            // that means rootTable is the exportedTable -confirmed
            rootTableExportedColumns = true;
        }
        String exportedTable;
        String importedTable;
        ODataEntry exportedTableKeys;
        ODataEntry importedTableKeys;
        List<NavigationKeys> keys;
        if (rootTableExportedColumns) {
            exportedTable = rootTable;
            importedTable = navigationTable;
            exportedTableKeys = rootTableKeys;
            importedTableKeys = navigationTableKeys;
        } else {
            exportedTable = navigationTable;
            importedTable = rootTable;
            exportedTableKeys = navigationTableKeys;
            importedTableKeys = rootTableKeys;
        }
        keys = navigationProperties.get(exportedTable).getNavigationKeys(importedTable);
        ODataEntry exportedKeyValues = getForeignKeysValues(exportedTable, exportedTableKeys, keys);
        modifyReferences(keys, importedTable, exportedTable, exportedKeyValues, importedTableKeys);
    }

    @Override
    public void deleteReference(String rootTable, ODataEntry rootTableKeys, String navigationTable,
                                ODataEntry navigationTableKeys) throws ODataServiceFault {
		/* To add a reference first we need to find the foreign key values of the tables,
		and therefore we need to identify which table has been exported */
        // Identifying the exported table and change the imported tables' column value
        NavigationTable navigation = navigationProperties.get(rootTable);
        boolean rootTableExportedColumns = false;
        if (navigation != null && navigation.getTables().contains(navigationTable)) {
            // that means rootTable is the exportedTable -confirmed
            rootTableExportedColumns = true;
        }
        String exportedTable;
        String importedTable;
        ODataEntry importedTableKeys;
        List<NavigationKeys> keys;
        if (rootTableExportedColumns) {
            exportedTable = rootTable;
            importedTable = navigationTable;
            importedTableKeys = navigationTableKeys;
        } else {
            exportedTable = navigationTable;
            importedTable = rootTable;
            importedTableKeys = rootTableKeys;
        }
        keys = navigationProperties.get(exportedTable).getNavigationKeys(importedTable);
        ODataEntry nullReferenceValues = new ODataEntry();
        for (NavigationKeys key : keys) {
            nullReferenceValues.addValue(key.getForeignKey(), null);
        }
        modifyReferences(keys, importedTable, exportedTable, nullReferenceValues, importedTableKeys);
    }

    private void modifyReferences(List<NavigationKeys> keys, String importedTable, String exportedTable,
                                  ODataEntry modifyValues, ODataEntry primaryKeys) throws ODataServiceFault {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = initializeConnection();
            String query = createAddReferenceSQL(importedTable, keys);
            statement = connection.prepareStatement(query);
            int index = 1;
            for (String column : modifyValues.getNames()) {
                String value = modifyValues.getValue(column);
                bindValuesToPreparedStatement(this.rdbmsDataTypes.get(exportedTable).get(column), value, index,
                                              statement);
                index++;
            }
            for (String column : primaryKeys.getNames()) {
                String value = primaryKeys.getValue(column);
                bindValuesToPreparedStatement(this.rdbmsDataTypes.get(importedTable).get(column), value, index,
                                              statement);
                index++;
            }
            statement.execute();
            commitExecution(connection);
        } catch (SQLException | ParseException e) {
            log.warn("modify value count - " + modifyValues.getNames().size() + ", primary keys size - " +
                     primaryKeys.getNames().size() + ", Error - " + e.getMessage(), e); //todo remove this later
            throw new ODataServiceFault(e, "Error occurred while updating foreign key values. :" + e.getMessage());
        } finally {
            releaseResources(null, statement);
            releaseConnection(connection);
        }
    }

    private ODataEntry getForeignKeysValues(String tableName, ODataEntry keys, List<NavigationKeys> columns)
            throws ODataServiceFault {
        ResultSet resultSet = null;
        PreparedStatement statement = null;
        Connection connection = null;
        try {
            connection = initializeConnection();
            String query = createSelectReferenceKeyFromExportedTable(tableName, keys, columns);
            statement = connection.prepareStatement(query);
            int index = 1;
            for (String column : keys.getNames()) {
                String value = keys.getValue(column);
                bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index, statement);
                index++;
            }
            resultSet = statement.executeQuery();
            ODataEntry values = new ODataEntry();
            String value;
            for (NavigationKeys column : columns) {
                String columnName = column.getPrimaryKey();
                while (resultSet.next()) {
                    value = getValueFromResultSet(this.rdbmsDataTypes.get(tableName).get(columnName), columnName,
                                                  resultSet);
                    values.addValue(columnName, value);
                }
            }
            return values;
        } catch (SQLException | ParseException e) {
            throw new ODataServiceFault(e, "Error occurred while retrieving foreign key values. :" + e.getMessage());
        } finally {
            releaseResources(resultSet, statement);
            releaseConnection(connection);
        }
    }

    private String createSelectReferenceKeyFromExportedTable(String tableName, ODataEntry keys,
                                                             List<NavigationKeys> columns) {
        StringBuilder sql = new StringBuilder();
        boolean propertyMatch = false;
        sql.append("SELECT ");
        for (NavigationKeys column : columns) {
            if (propertyMatch) {
                sql.append(" , ");
            }
            sql.append(column.getPrimaryKey());
            propertyMatch = true;
        }
        sql.append(" FROM ").append(tableName).append(" WHERE ");
        propertyMatch = false;
        for (String column : this.rdbmsDataTypes.get(tableName).keySet()) {
            if (keys.getValue(column) != null) {
                if (propertyMatch) {
                    sql.append(" AND ");
                }
                sql.append(column).append(" = ").append(" ? ");
                propertyMatch = true;
            }
        }
        return sql.toString();
    }

    private String createAddReferenceSQL(String tableName, List<NavigationKeys> keys) {
        List<String> pKeys = primaryKeys.get(tableName);
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(tableName).append(" SET ");
        boolean propertyMatch = false;
        for (NavigationKeys column : keys) {
            if (propertyMatch) {
                sql.append(",");
            }
            sql.append(column.getForeignKey()).append(" = ").append(" ? ");
            propertyMatch = true;
        }
        sql.append(" WHERE ");
        // Handling keys
        propertyMatch = false;
        for (String key : pKeys) {
            if (propertyMatch) {
                sql.append(" AND ");
            }
            sql.append(key).append(" = ").append(" ? ");
            propertyMatch = true;
        }
        return sql.toString();
    }

    @Override
    public List<ODataEntry> readTable(String tableName) throws ODataServiceFault {
        ResultSet resultSet = null;
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = initializeConnection();
            String query = "select * from " + tableName;
            statement = connection.prepareStatement(query);
            resultSet = statement.executeQuery();
            return createDataEntryCollectionFromRS(tableName, resultSet);
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :" +
                                           e.getMessage());
        } finally {
            releaseResources(resultSet, statement);
            releaseConnection(connection);
        }
    }

    public void initStreaming() {
        this.initializeStream = true;
    }

    public int getEntityCountWithKeys(String tableName, ODataEntry keys) throws ODataServiceFault {
        ResultSet resultSet = null;
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = initializeConnection();
            String query = createCountSqlWithKeys(tableName, keys);
            statement = connection.prepareStatement(query);
            int index = 1;
            for (String column : keys.getNames()) {
                if (this.rdbmsDataTypes.get(tableName).keySet().contains(column)) {
                    String value = keys.getValue(column);
                    bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                  statement);
                    index++;
                }
            }
            resultSet = statement.executeQuery();
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException | ParseException e) {
            throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :"
                    + e.getMessage());
        } finally {
            releaseResources(resultSet, statement);
            releaseConnection(connection);
        }
    }

    public int getEntityCount(String tableName) throws ODataServiceFault {
        ResultSet resultSet = null;
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = initializeConnection();
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT COUNT(*) FROM ").append(tableName);
            statement = connection.prepareStatement(sql.toString());
            resultSet = statement.executeQuery();
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :"
                    + e.getMessage());
        } finally {
            releaseResources(resultSet, statement);
            releaseConnection(connection);
        }
    }

    public List<ODataEntry> streamTable(String tableName) throws ODataServiceFault {
        try {
            if (this.initializeStream) {
                this.initializeStream = false;
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT * FROM ").append(tableName);
                initializeStreamConnection(sql.toString());
                this.streamResultSet = this.preparedStatement.executeQuery();
            }
            return readStreamResultSet(tableName);
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :"
                    + e.getMessage());
        } finally {
            try {
                if (this.streamResultSet.isLast() || this.streamResultSet.isAfterLast()) {
                    this.closeStreamConnection();
                }
            } catch (SQLException e) {
                this.closeStreamConnection();
                throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :"
                        + e.getMessage());
            }
        }
    }

    /**
     * This method reads the stream result set to generate a list of OData entries.
     * Maximum size of the list is bounded by the chunk size.
     *
     * @param tableName Name of the table
     * @throws SQLException
     */
    private List<ODataEntry> readStreamResultSet(String tableName) throws SQLException {
        List<ODataEntry> entryList = new ArrayList<>();
        int processedEntryCount = 0;
        while (this.streamResultSet.next()) {
            ODataEntry entry = createDataEntryFromRS(tableName);
            entryList.add(entry);
            processedEntryCount++;
            if (processedEntryCount >= this.chunkSize) {
                break;
            }
        }
        return entryList;
    }

    /**
     * This method closes the stream connection.
     *
     * @throws ODataServiceFault
     */
    private void closeStreamConnection() throws ODataServiceFault {
        try {
            if (this.streamResultSet != null) {
                this.streamResultSet.close();
            }
            if (this.preparedStatement != null) {
                this.preparedStatement.close();
            }
            if (this.streamConnection != null) {
                this.streamConnection.close();
            }
        } catch (SQLException e) {
            throw new ODataServiceFault("Error occurred while trying to close the database connection");
        }
    }

    public List<ODataEntry> streamTableWithOrder(String tableName, OrderByOption orderByOption)
            throws ODataServiceFault {
        try {
            if (this.initializeStream) {
                this.initializeStream = false;
                this.streamConnection = initializeConnection();
                String query = "SELECT * FROM " + tableName + " " + getSortStatement(orderByOption);
                this.preparedStatement = this.streamConnection.prepareStatement(query,
                                                                                ResultSet.TYPE_SCROLL_INSENSITIVE,
                                                                                ResultSet.CONCUR_READ_ONLY);
                this.preparedStatement.setFetchSize(this.chunkSize);
                this.streamResultSet = this.preparedStatement.executeQuery();
            }
            return readStreamResultSet(tableName);
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :"
                    + e.getMessage());
        } finally {
            try {
                if (this.streamResultSet.isLast() || this.streamResultSet.isAfterLast()) {
                    this.closeStreamConnection();
                }
            } catch (SQLException e) {
                this.closeStreamConnection();
                throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :"
                        + e.getMessage());
            }
        }
    }

    /**
     * This method creates an OData entry for a given table.
     *
     * @param tableName
     * @return
     * @throws SQLException
     */
    private ODataEntry createDataEntryFromRS(String tableName) throws SQLException {
        ODataEntry entry = new ODataEntry();
        for (String column : this.rdbmsDataTypes.get(tableName).keySet()) {
            int columnType = this.rdbmsDataTypes.get(tableName).get(column);
            String paramValue = getValueFromResultSet(columnType, column, this.streamResultSet);
            entry.addValue(column, paramValue);
        }
        entry.addValue("ETag", ODataUtils.generateETag(this.configID, tableName, entry));
        return entry;
    }

    /**
     * This method creates the sort statement for the query.
     *
     * @param orderByOption List of keys to consider when sorting
     * @return Sort statement
     */
    private String getSortStatement(OrderByOption orderByOption) throws SQLException {
        String orderBy = "ORDER BY ";
        for (int i = 0; i < orderByOption.getOrders().size(); i++) {
            if (i != 0) {
                orderBy += ", ";
            }
            final OrderByItem item = orderByOption.getOrders().get(i);
            String expression = String.valueOf(item.getExpression()).replaceAll("\\[", "(").replaceAll("\\]", ")")
                    .replaceAll("[\\{\\}]", "");
            if (this.streamConnection.getMetaData().getDatabaseProductName().toLowerCase().contains(MSSQL_SERVER)) {
                expression = expression.replace("length", "len");
            }
            orderBy += expression;
            orderBy += item.isDescending() ? " DESC" : " ASC";
        }
        return orderBy;
    }

    @Override
    public List<String> getTableList() {
        return this.tableList;
    }

    @Override
    public Map<String, List<String>> getPrimaryKeys() {
        return this.primaryKeys;
    }

    private String convertToTimeString(Time sqlTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(sqlTime.getTime());
        return new org.apache.axis2.databinding.types.Time(cal).toString();
    }

    private String convertToTimestampString(Timestamp sqlTimestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(sqlTimestamp.getTime());
        return ConverterUtil.convertToString(cal);
    }

    @Override
    public ODataEntry insertEntityToTable(String tableName, ODataEntry entry) throws ODataServiceFault {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = initializeConnection();
            String query = createInsertSQL(tableName, entry);
            boolean isAvailableAutoIncrementColumns = isAvailableAutoIncrementColumns(tableName);
            if(isAvailableAutoIncrementColumns) {
                statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            } else {
                statement = connection.prepareStatement(query);
            }
            int index = 1;
            for (String column : entry.getNames()) {
                if (this.rdbmsDataTypes.get(tableName).keySet().contains(column)) {
                    String value = entry.getValue(column);
                    bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                  statement);
                    index++;
                }
            }
            ODataEntry createdEntry = new ODataEntry();
            if (isAvailableAutoIncrementColumns(tableName)) {
                statement.executeUpdate();
                ResultSet resultSet = statement.getGeneratedKeys();
                String paramValue;
                int i = 1;
                while (resultSet.next()) {
                    for (DataColumn column : this.tableMetaData.get(tableName).values()) {
                        if (column.isAutoIncrement()) {
                            String resultSetColumnName = resultSet.getMetaData().getColumnName(i);
                            String columnName = column.getColumnName();
                            int columnType = this.rdbmsDataTypes.get(tableName).get(columnName);
                            paramValue = getValueFromResultSet(columnType, resultSetColumnName, resultSet);
                            createdEntry.addValue(columnName, paramValue);
                            // Need to add this column to generate the E-tag
                            entry.addValue(columnName, paramValue);
                        }
                    }
                    i++;
                }
            } else {
                statement.execute();
            }
            commitExecution(connection);
            createdEntry.addValue(ODataConstants.E_TAG, ODataUtils.generateETag(this.configID, tableName, entry));
            return createdEntry;
        } catch (SQLException | ParseException e) {
            throw new ODataServiceFault(e, "Error occurred while writing entities to " + tableName + " table. :" +
                                           e.getMessage());
        } finally {
            releaseResources(null, statement);
            releaseConnection(connection);
        }
    }

    private boolean isAvailableAutoIncrementColumns(String table) {
        for (DataColumn column : this.tableMetaData.get(table).values()) {
            if (column.isAutoIncrement()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ODataEntry> readTableWithKeys(String tableName, ODataEntry keys) throws ODataServiceFault {
        ResultSet resultSet = null;
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = initializeConnection();
            String query = createReadSqlWithKeys(tableName, keys);
            statement = connection.prepareStatement(query);
            int index = 1;
            for (String column : keys.getNames()) {
                if (this.rdbmsDataTypes.get(tableName).keySet().contains(column)) {
                    String value = keys.getValue(column);
                    bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                  statement);
                    index++;
                }
            }
            resultSet = statement.executeQuery();
            return createDataEntryCollectionFromRS(tableName, resultSet);
        } catch (SQLException | ParseException e) {
            throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :" +
                                           e.getMessage());
        } finally {
            releaseResources(resultSet, statement);
            releaseConnection(connection);
        }
    }

    public List<ODataEntry> streamTableWithKeys(String tableName, ODataEntry keys) throws ODataServiceFault {
        try {
            if (this.initializeStream) {
                this.initializeStream = false;
                String query = createReadSqlWithKeys(tableName, keys);
                initializeStreamConnection(query);
                int index = 1;
                for (String column : keys.getNames()) {
                    if (this.rdbmsDataTypes.get(tableName).keySet().contains(column)) {
                        String value = keys.getValue(column);
                        bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                      this.preparedStatement);
                        index++;
                    }
                }
                this.streamResultSet = this.preparedStatement.executeQuery();
            }
            return readStreamResultSet(tableName);
        } catch (SQLException | ParseException e) {
            throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :"
                    + e.getMessage());
        } finally {
            try {
                if (this.streamResultSet.isLast() || this.streamResultSet.isAfterLast()) {
                    this.closeStreamConnection();
                }
            } catch (SQLException e) {
                this.closeStreamConnection();
                throw new ODataServiceFault(e, "Error occurred while reading entities from " + tableName + " table. :"
                        + e.getMessage());
            }
        }
    }

    private void initializeStreamConnection(String query) throws SQLException {
        this.streamConnection = initializeConnection();
        this.preparedStatement = this.streamConnection.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE,
                                                                        ResultSet.CONCUR_READ_ONLY);
        this.preparedStatement.setFetchSize(this.chunkSize);
    }

    /**
     * This method bind values to prepared statement.
     *
     * @param type            data Type
     * @param value           String value
     * @param ordinalPosition Ordinal Position
     * @param sqlStatement    Statement
     * @throws SQLException
     * @throws ParseException
     * @throws ODataServiceFault
     */
    private void bindValuesToPreparedStatement(int type, String value, int ordinalPosition,
                                               PreparedStatement sqlStatement)
            throws SQLException, ParseException, ODataServiceFault {
        byte[] data;
        try {
            switch (type) {
                case Types.INTEGER:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setInt(ordinalPosition, ConverterUtil.convertToInt(value));
                    }
                    break;
                case Types.TINYINT:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setByte(ordinalPosition, ConverterUtil.convertToByte(value));
                    }
                    break;
                case Types.SMALLINT:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setShort(ordinalPosition, ConverterUtil.convertToShort(value));
                    }
                    break;
                case Types.DOUBLE:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setDouble(ordinalPosition, ConverterUtil.convertToDouble(value));
                    }
                    break;
                case Types.VARCHAR:
                /* fall through */
                case Types.CHAR:
				/* fall through */
                case Types.LONGVARCHAR:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setString(ordinalPosition, value);
                    }
                    break;
                case Types.CLOB:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setClob(ordinalPosition, new BufferedReader(new StringReader(value)),
                                             value.length());
                    }
                    break;
                case Types.BOOLEAN:
				/* fall through */
                case Types.BIT:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setBoolean(ordinalPosition, ConverterUtil.convertToBoolean(value));
                    }
                    break;
                case Types.BLOB:
				/* fall through */
                case Types.LONGVARBINARY:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        data = this.getBytesFromBase64String(value);
                        sqlStatement.setBlob(ordinalPosition, new ByteArrayInputStream(data), data.length);
                    }
                    break;
                case Types.BINARY:
				/* fall through */
                case Types.VARBINARY:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        data = this.getBytesFromBase64String(value);
                        sqlStatement.setBinaryStream(ordinalPosition, new ByteArrayInputStream(data), data.length);
                    }
                    break;
                case Types.DATE:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setDate(ordinalPosition, DBUtils.getDate(value));
                    }
                    break;
                case Types.DECIMAL:
				/* fall through */
                case Types.NUMERIC:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setBigDecimal(ordinalPosition, ConverterUtil.convertToBigDecimal(value));
                    }
                    break;
                case Types.FLOAT:
				/* fall through */
                case Types.REAL:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setFloat(ordinalPosition, ConverterUtil.convertToFloat(value));
                    }
                    break;
                case Types.TIME:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setTime(ordinalPosition, DBUtils.getTime(value));
                    }
                    break;
                case Types.LONGNVARCHAR:
				/* fall through */
                case Types.NCHAR:
				/* fall through */
                case Types.NVARCHAR:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setNString(ordinalPosition, value);
                    }
                    break;
                case Types.NCLOB:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setNClob(ordinalPosition, new BufferedReader(new StringReader(value)),
                                              value.length());
                    }
                    break;
                case Types.BIGINT:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setLong(ordinalPosition, ConverterUtil.convertToLong(value));
                    }
                    break;
                case Types.TIMESTAMP:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setTimestamp(ordinalPosition, DBUtils.getTimestamp(value));
                    }
                    break;
                default:
                    if (value == null) {
                        sqlStatement.setNull(ordinalPosition, type);
                    } else {
                        sqlStatement.setString(ordinalPosition, value);
                    }
                    break;
            }
        } catch (DataServiceFault e) {
            throw new ODataServiceFault(e, "Error occurred while binding values. :" + e.getMessage());
        }
    }

    private byte[] getBytesFromBase64String(String base64Str) throws SQLException {
        try {
            return Base64.decodeBase64(base64Str.getBytes(DBConstants.DEFAULT_CHAR_SET_TYPE));
        } catch (Exception e) {
            throw new SQLException(e.getMessage());
        }
    }

    @Override
    public boolean updateEntityInTable(String tableName, ODataEntry newProperties) throws ODataServiceFault {
        List<String> pKeys = this.primaryKeys.get(tableName);
        Connection connection = null;
        PreparedStatement statement = null;
        String value;
        try {
            connection = initializeConnection();
            String query = createUpdateEntitySQL(tableName, newProperties);
            statement = connection.prepareStatement(query);
            int index = 1;
            for (String column : newProperties.getNames()) {
                if (!pKeys.contains(column)) {
                    value = newProperties.getValue(column);
                    bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                  statement);
                    index++;
                }
            }
            for (String column : newProperties.getNames()) {
                if (!pKeys.isEmpty()) {
                    if (pKeys.contains(column)) {
                        value = newProperties.getValue(column);
                        bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                      statement);
                        index++;
                    }
                } else {
                    throw new ODataServiceFault("Error occurred while updating the entity to " + tableName +
                                                " table. couldn't find keys in the table.");
                }
            }
            statement.execute();
            commitExecution(connection);
            return true;
        } catch (SQLException | ParseException e) {
            throw new ODataServiceFault(e, "Error occurred while updating the entity to " + tableName + " table. :" +
                                           e.getMessage());
        } finally {
            releaseResources(null, statement);
            releaseConnection(connection);
        }
    }

    public boolean updateEntityInTableTransactional(String tableName, ODataEntry oldProperties,
                                                    ODataEntry newProperties) throws ODataServiceFault {
        List<String> pKeys = this.primaryKeys.get(tableName);
        PreparedStatement statement = null;
        Connection connection = null;
        String value;
        try {
            connection = initializeConnection();
            String query = createUpdateEntitySQL(tableName, newProperties);
            statement = connection.prepareStatement(query);
            int index = 1;
            for (String column : newProperties.getNames()) {
                if (!pKeys.contains(column)) {
                    value = newProperties.getValue(column);
                    bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                  statement);
                    index++;
                }
            }
            for (String column : oldProperties.getNames()) {
                if (!pKeys.isEmpty()) {
                    if (pKeys.contains(column)) {
                        value = oldProperties.getValue(column);
                        bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                      statement);
                        index++;
                    }
                } else {
                    throw new ODataServiceFault("Error occurred while updating the entity to " + tableName +
                                                " table. couldn't find keys in the table.");
                }
            }
            statement.execute();
            commitExecution(connection);
            return true;
        } catch (SQLException | ParseException e) {
            throw new ODataServiceFault(e, "Error occurred while updating the entity to " + tableName + " table. :" +
                                           e.getMessage());
        } finally {
            releaseResources(null, statement);
            releaseConnection(connection);
        }
    }

    @Override
    public boolean deleteEntityInTable(String tableName, ODataEntry entry) throws ODataServiceFault {
        List<String> pKeys = this.primaryKeys.get(tableName);
        Connection connection = null;
        PreparedStatement statement = null;
        String value;
        try {
            connection = initializeConnection();
            String query = createDeleteSQL(tableName);
            statement = connection.prepareStatement(query);
            int index = 1;
            for (String column : pKeys) {
                if (this.rdbmsDataTypes.get(tableName).keySet().contains(column)) {
                    value = entry.getValue(column);
                    bindValuesToPreparedStatement(this.rdbmsDataTypes.get(tableName).get(column), value, index,
                                                  statement);
                    index++;
                }
            }
            statement.execute();
            int rowCount = statement.getUpdateCount();
            commitExecution(connection);
            return rowCount > 0;
        } catch (SQLException | ParseException e) {
            throw new ODataServiceFault(e, "Error occurred while deleting the entity from " + tableName + " table. :" +
                                           e.getMessage());
        } finally {
            releaseResources(null, statement);
            releaseConnection(connection);
        }
    }

    private void addDataType(String tableName, String columnName, int dataType) {
        Map<String, Integer> tableMap = this.rdbmsDataTypes.get(tableName);
        if (tableMap == null) {
            tableMap = new HashMap<>();
            this.rdbmsDataTypes.put(tableName, tableMap);
        }
        tableMap.put(columnName, dataType);
    }

    /**
     * This method wraps result set data in to DataEntry and creates a list of DataEntry.
     *
     * @param tableName Name of the table
     * @param resultSet Result set
     * @return List of DataEntry
     * @throws ODataServiceFault
     * @see DataEntry
     */
    private List<ODataEntry> createDataEntryCollectionFromRS(String tableName, ResultSet resultSet)
            throws ODataServiceFault {
        List<ODataEntry> entitySet = new ArrayList<>();
        try {
            String paramValue;
            while (resultSet.next()) {
                ODataEntry entry = new ODataEntry();
                //Creating a unique string to represent the
                for (String column : this.rdbmsDataTypes.get(tableName).keySet()) {
                    int columnType = this.rdbmsDataTypes.get(tableName).get(column);
                    paramValue = getValueFromResultSet(columnType, column, resultSet);
                    entry.addValue(column, paramValue);
                }
                //Set Etag to the entity
                entry.addValue("ETag", ODataUtils.generateETag(this.configID, tableName, entry));
                entitySet.add(entry);
            }
            return entitySet;
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error in writing the entities to table. :" + e.getMessage());
        }
    }

    private String getValueFromResultSet(int columnType, String column, ResultSet resultSet) throws SQLException {
        String paramValue;
        switch (columnType) {
            case Types.INTEGER:
                /* fall through */
            case Types.TINYINT:
                /* fall through */
            case Types.SMALLINT:
                paramValue = ConverterUtil.convertToString(resultSet.getInt(column));
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
            case Types.DOUBLE:
                paramValue = ConverterUtil.convertToString(resultSet.getDouble(column));
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
            case Types.VARCHAR:
                /* fall through */
            case Types.CHAR:
                /* fall through */
            case Types.CLOB:
                /* fall through */
            case Types.LONGVARCHAR:
                paramValue = resultSet.getString(column);
                break;
            case Types.BOOLEAN:
                /* fall through */
            case Types.BIT:
                paramValue = ConverterUtil.convertToString(resultSet.getBoolean(column));
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
            case Types.BLOB:
                Blob sqlBlob = resultSet.getBlob(column);
                if (sqlBlob != null) {
                    paramValue = this.getBase64StringFromInputStream(sqlBlob.getBinaryStream());
                } else {
                    paramValue = null;
                }
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
            case Types.BINARY:
                /* fall through */
            case Types.LONGVARBINARY:
                /* fall through */
            case Types.VARBINARY:
                InputStream binInStream = resultSet.getBinaryStream(column);
                if (binInStream != null) {
                    paramValue = this.getBase64StringFromInputStream(binInStream);
                } else {
                    paramValue = null;
                }
                break;
            case Types.DATE:
                Date sqlDate = resultSet.getDate(column);
                if (sqlDate != null) {
                    paramValue = ConverterUtil.convertToString(sqlDate);
                } else {
                    paramValue = null;
                }
                break;
            case Types.DECIMAL:
                /* fall through */
            case Types.NUMERIC:
                BigDecimal bigDecimal = resultSet.getBigDecimal(column);
                if (bigDecimal != null) {
                    paramValue = ConverterUtil.convertToString(bigDecimal);
                } else {
                    paramValue = null;
                }
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
            case Types.FLOAT:
                paramValue = ConverterUtil.convertToString(resultSet.getFloat(column));
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
            case Types.TIME:
                Time sqlTime = resultSet.getTime(column);
                if (sqlTime != null) {
                    paramValue = this.convertToTimeString(sqlTime);
                } else {
                    paramValue = null;
                }
                break;
            case Types.LONGNVARCHAR:
                /* fall through */
            case Types.NCHAR:
                /* fall through */
            case Types.NCLOB:
                /* fall through */
            case Types.NVARCHAR:
                paramValue = resultSet.getNString(column);
                break;
            case Types.BIGINT:
                paramValue = ConverterUtil.convertToString(resultSet.getLong(column));
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
            case Types.TIMESTAMP:
                Timestamp sqlTimestamp = resultSet.getTimestamp(column);
                if (sqlTimestamp != null) {
                    paramValue = this.convertToTimestampString(sqlTimestamp);
                } else {
                    paramValue = null;
                }
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
            /* handle all other types as strings */
            default:
                paramValue = resultSet.getString(column);
                paramValue = resultSet.wasNull() ? null : paramValue;
                break;
        }
        return paramValue;
    }

    private void releaseResources(ResultSet resultSet, Statement statement) {
	    /* close the result set */
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (Exception ignore) {
                // ignore
            }
        }
		/* close the statement */
        if (statement != null) {
            try {
                statement.close();
            } catch (Exception ignore) {
                // ignore
            }
        }

    }

    private String getBase64StringFromInputStream(InputStream in) throws SQLException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        String strData;
        try {
            byte[] buff = new byte[512];
            int i;
            while ((i = in.read(buff)) > 0) {
                byteOut.write(buff, 0, i);
            }
            in.close();
            byte[] base64Data = Base64.encodeBase64(byteOut.toByteArray());
            if (base64Data != null) {
                strData = new String(base64Data, DBConstants.DEFAULT_CHAR_SET_TYPE);
            } else {
                strData = null;
            }
            return strData;
        } catch (Exception e) {
            throw new SQLException(e.getMessage());
        }
    }

    /**
     * This method reads table column meta data.
     *
     * @param tableName Name of the table
     * @return table MetaData
     * @throws ODataServiceFault
     */
    private Map<String, DataColumn> readTableColumnMetaData(String tableName, DatabaseMetaData meta)
            throws ODataServiceFault {
        ResultSet resultSet = null;
        Map<String, DataColumn> columnMap = new HashMap<>();
        try {
            if (meta.getDatabaseProductName().toLowerCase().contains(ORACLE_SERVER)) {
                resultSet = meta.getColumns(null, meta.getUserName(), tableName, null);
                if (meta.getConnection().getSchema() != null) {
                    if (!meta.getConnection().getSchema().equals(meta.getUserName())) {
                        resultSet = meta.getColumns(null, meta.getConnection().getSchema(), tableName,
                                null);
                    }
                }
            } else {
                resultSet = meta.getColumns(null, null, tableName, null);
            }
            int i = 1;
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME");
                int columnType = resultSet.getInt("DATA_TYPE");
                String columnTypeName = resultSet.getString("TYPE_NAME");
                int size = resultSet.getInt("COLUMN_SIZE");
                boolean nullable = resultSet.getBoolean("NULLABLE");
                String columnDefaultVal = resultSet.getString("COLUMN_DEF");
                String autoIncrement = resultSet.getString("IS_AUTOINCREMENT").toLowerCase();
                boolean isAutoIncrement = false;
                if (autoIncrement.contains("yes") || autoIncrement.contains("true")) {
                    isAutoIncrement = true;
                }
                DataColumn column = new DataColumn(columnName, getODataDataType(columnType), i, nullable, size,
                                                   isAutoIncrement);
                if (null != columnDefaultVal) {
                    column.setDefaultValue(columnDefaultVal);
                }
                if (Types.DOUBLE == columnType || Types.FLOAT == columnType || Types.DECIMAL == columnType ||
                    Types.NUMERIC == columnType || Types.REAL == columnType) {
                    int scale = resultSet.getInt("DECIMAL_DIGITS");
                    if (meta.getDatabaseProductName().toLowerCase().contains(ORACLE_SERVER) && size == 0 &&
                            "NUMBER".equals(columnTypeName)) {
                        /* for NUMBER type columns if the precision and scale are not defined, the precision and the scale
                        should be 38 and 0 respectively. Therefore, setting precision as 38 and
                        scale as 5 (to preserve the backward compatibility) to default values as below.*/
                        size = 38;
                        if (scale == -127) {
                            scale = 5;
                        }
                        // Column max length also should be overwrite to new size as below
                        column.setMaxLength(size);
                    }
                    column.setPrecision(size);
                    if (scale == 0) {
                        //setting default scale as 5
                        scale = 5;
                        column.setScale(scale);
                    } else {
                        column.setScale(scale);
                    }
                }
                columnMap.put(columnName, column);
                addDataType(tableName, columnName, columnType);
                i++;
            }
            return columnMap;
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error in reading table meta data in " + tableName + " table. :" +
                                           e.getMessage());
        } finally {
            releaseResources(resultSet, null);
        }
    }

    /**
     * This method initializes metadata.
     *
     * @throws ODataServiceFault
     */
    private void initializeMetaData() throws ODataServiceFault {
        this.tableMetaData = new HashMap<>();
        this.primaryKeys = new HashMap<>();
        this.navigationProperties = new HashMap<>();
        Connection connection = null;
        try {
            connection = initializeConnection();
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String tableName : this.tableList) {
                this.tableMetaData.put(tableName, readTableColumnMetaData(tableName, metadata));
                this.navigationProperties.put(tableName, readForeignKeys(tableName, metadata, catalog));
                this.primaryKeys.put(tableName, readTablePrimaryKeys(tableName, metadata, catalog));
            }
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error in reading tables from the database. :" + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * This method creates a list of tables available in the DB.
     *
     * @return Table List of the DB
     * @throws ODataServiceFault
     */
    private List<String> generateTableList() throws ODataServiceFault {
        List<String> tableList = new ArrayList<>();
        Connection connection = null;
        ResultSet rs = null;
        try {
            connection = initializeConnection();
            DatabaseMetaData meta = connection.getMetaData();
            if (meta.getDatabaseProductName().toLowerCase().contains(ORACLE_SERVER)) {
                rs = meta.getTables(null, meta.getUserName(), null, new String[] { TABLE, VIEW });
            } else if (meta.getDatabaseProductName().toLowerCase().contains(MSSQL_SERVER)) {
                rs = meta.getTables(null, connection.getSchema(), null, new String[] { TABLE, VIEW });
            } else {
                rs = meta.getTables(null, null, null, new String[] { TABLE, VIEW });
            }
            while (rs.next()) {
                String tableName = rs.getString(TABLE_NAME);
                tableList.add(tableName);
            }

                if (meta.getDatabaseProductName().toLowerCase().contains(ORACLE_SERVER) && connection.getSchema() != null
                        && !connection.getSchema().equals(meta.getUserName())) {
                    rs = meta.getTables(null, connection.getSchema(), null,
                            new String[]{TABLE, VIEW});
                    while (rs.next()) {
                        String tableName = rs.getString(TABLE_NAME);
                        tableList.add(tableName);
                    }
                }
            return tableList;
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error in reading tables from the database. :" + e.getMessage());
        } finally {
            releaseResources(rs, null);
            releaseConnection(connection);
        }
    }

    /**
     * This method reads primary keys of the table.
     *
     * @param tableName Name of the table
     * @return primary key list
     * @throws ODataServiceFault
     */
    private List<String> readTablePrimaryKeys(String tableName, DatabaseMetaData metaData, String catalog)
            throws ODataServiceFault {
        ResultSet resultSet = null;
        List<String> keys = new ArrayList<>();
        try {
            if (metaData.getDatabaseProductName().toLowerCase().contains(ORACLE_SERVER)) {
                resultSet = metaData.getPrimaryKeys(catalog, metaData.getUserName(), tableName);
            } else {
                resultSet = metaData.getPrimaryKeys(catalog, null, tableName);
            }
            while (resultSet.next()) {
                String primaryKey = resultSet.getString("COLUMN_NAME");
                keys.add(primaryKey);
            }
            return keys;
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error in reading table primary keys in " + tableName + " table. :" +
                                           e.getMessage());
        } finally {
            releaseResources(resultSet, null);
        }
    }

    /**
     * This method reads foreign keys of the table.
     *
     * @param tableName Name of the table
     * @throws ODataServiceFault
     */
    private NavigationTable readForeignKeys(String tableName, DatabaseMetaData metaData, String catalog)
            throws ODataServiceFault {
        ResultSet resultSet = null;
        try {
            resultSet = metaData.getExportedKeys(catalog, null, tableName);
            NavigationTable navigationLinks = new NavigationTable();
            while (resultSet.next()) {
                // foreignKeyTableName means the table name of the table which used columns as foreign keys in that table.
                String primaryKeyColumnName = resultSet.getString("PKCOLUMN_NAME");
                String foreignKeyTableName = resultSet.getString("FKTABLE_NAME");
                String foreignKeyColumnName = resultSet.getString("FKCOLUMN_NAME");
                List<NavigationKeys> columnList = navigationLinks.getNavigationKeys(foreignKeyTableName);
                if (columnList == null) {
                    columnList = new ArrayList<>();
                    navigationLinks.addNavigationKeys(foreignKeyTableName, columnList);
                }
                columnList.add(new NavigationKeys(primaryKeyColumnName, foreignKeyColumnName));
            }
            return navigationLinks;
        } catch (SQLException e) {
            throw new ODataServiceFault(e, "Error in reading " + tableName + " table meta data. :" + e.getMessage());
        } finally {
            releaseResources(resultSet, null);
        }
    }

    @Override
    public Map<String, Map<String, DataColumn>> getTableMetadata() {
        return this.tableMetaData;
    }

    /**
     * This method creates a SQL query to update data.
     *
     * @param tableName  Name of the table
     * @param properties Properties
     * @return sql Query
     */
    private String createUpdateEntitySQL(String tableName, ODataEntry properties) {
        List<String> pKeys = primaryKeys.get(tableName);
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(tableName).append(" SET ");
        boolean propertyMatch = false;
        for (String column : properties.getNames()) {
            if (!pKeys.contains(column)) {
                if (propertyMatch) {
                    sql.append(",");
                }
                sql.append(column).append(" = ").append(" ? ");
                propertyMatch = true;
            }
        }
        sql.append(" WHERE ");
        // Handling keys
        propertyMatch = false;
        for (String key : pKeys) {
            if (propertyMatch) {
                sql.append(" AND ");
            }
            sql.append(key).append(" = ").append(" ? ");
            propertyMatch = true;
        }
        return sql.toString();
    }

    /**
     * This method creates a SQL query to insert data in table.
     *
     * @param tableName Name of the table
     * @return sqlQuery
     */
    private String createInsertSQL(String tableName, ODataEntry entry) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        boolean propertyMatch = false;
        for (String column : entry.getNames()) {
            if (this.rdbmsDataTypes.get(tableName).keySet().contains(column)) {
                if (propertyMatch) {
                    sql.append(",");
                }
                sql.append(column);
                propertyMatch = true;
            }
        }
        sql.append(" ) VALUES ( ");
        propertyMatch = false;
        for (String column : entry.getNames()) {
            if (this.rdbmsDataTypes.get(tableName).keySet().contains(column)) {
                if (propertyMatch) {
                    sql.append(",");
                }
                sql.append("?");
                propertyMatch = true;
            }
        }
        sql.append(" ) ");
        return sql.toString();
    }

    /**
     * This method creates SQL query to read data with keys.
     *
     * @param tableName Name of the table
     * @param keys      Keys
     * @return sql Query
     */
    private String createReadSqlWithKeys(String tableName, ODataEntry keys) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName).append(" WHERE ");
        boolean propertyMatch = false;
        for (String column : this.rdbmsDataTypes.get(tableName).keySet()) {
            if (keys.getNames().contains(column)) {
                if (propertyMatch) {
                    sql.append(" AND ");
                }
                sql.append(column).append(" = ").append(" ? ");
                propertyMatch = true;
            }
        }
        return sql.toString();
    }

    /**
     * This method creates an SQL query to count the number of rows in a table
     * after applying the where clause.
     *
     * @param tableName Name of the table
     * @param keys      Keys
     * @return sql Query
     */
    private String createCountSqlWithKeys(String tableName, ODataEntry keys) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(tableName).append(" WHERE ");
        boolean propertyMatch = false;
        for (String column : this.rdbmsDataTypes.get(tableName).keySet()) {
            if (keys.getNames().contains(column)) {
                if (propertyMatch) {
                    sql.append(" AND ");
                }
                sql.append(column).append(" = ").append(" ? ");
                propertyMatch = true;
            }
        }
        return sql.toString();
    }

    /**
     * This method creates SQL query to delete data.
     *
     * @param tableName Name of the table
     * @return sql Query
     */
    private String createDeleteSQL(String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ").append(tableName).append(" WHERE ");
        List<String> pKeys = primaryKeys.get(tableName);
        boolean propertyMatch = false;
        for (String key : pKeys) {
            if (propertyMatch) {
                sql.append(" AND ");
            }
            sql.append(key).append(" = ").append(" ? ");
            propertyMatch = true;
        }
        return sql.toString();
    }

    private ODataDataType getODataDataType(int columnType) {
        ODataDataType dataType;
        switch (columnType) {
            case Types.INTEGER:
                dataType = ODataDataType.INT32;
                break;
            case Types.TINYINT:
				/* fall through */
            case Types.SMALLINT:
                dataType = ODataDataType.INT16;
                break;
            case Types.DOUBLE:
                dataType = ODataDataType.DOUBLE;
                break;
            case Types.VARCHAR:
				/* fall through */
            case Types.CHAR:
				/* fall through */
            case Types.LONGVARCHAR:
				/* fall through */
            case Types.CLOB:
				/* fall through */
            case Types.LONGNVARCHAR:
				/* fall through */
            case Types.NCHAR:
				/* fall through */
            case Types.NVARCHAR:
				/* fall through */
            case Types.NCLOB:
				/* fall through */
            case Types.SQLXML:
                dataType = ODataDataType.STRING;
                break;
            case Types.BOOLEAN:
				/* fall through */
            case Types.BIT:
                dataType = ODataDataType.BOOLEAN;
                break;
            case Types.BLOB:
				/* fall through */
            case Types.BINARY:
				/* fall through */
            case Types.LONGVARBINARY:
				/* fall through */
            case Types.VARBINARY:
                dataType = ODataDataType.BINARY;
                break;
            case Types.DATE:
                dataType = ODataDataType.DATE;
                break;
            case Types.DECIMAL:
				/* fall through */
            case Types.NUMERIC:
                dataType = ODataDataType.DECIMAL;
                break;
            case Types.FLOAT:
				/* fall through */
            case Types.REAL:
                dataType = ODataDataType.SINGLE;
                break;
            case Types.TIME:
                dataType = ODataDataType.TIMEOFDAY;
                break;
            case Types.BIGINT:
                dataType = ODataDataType.INT64;
                break;
            case Types.TIMESTAMP:
                dataType = ODataDataType.DATE_TIMEOFFSET;
                break;
            default:
                dataType = ODataDataType.STRING;
                break;
        }
        return dataType;
    }

    private Connection initializeConnection() throws SQLException {
        if (getTransactionalConnection() == null) {
            return this.dataSource.getConnection();
        }
        return getTransactionalConnection();
    }

    private void commitExecution(Connection connection) throws SQLException {
        if (getTransactionalConnection() == null) {
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }

    private void releaseConnection(Connection connection) {
        if (getTransactionalConnection() == null) {
			/* close the connection */
            try {
                connection.close();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }
}
