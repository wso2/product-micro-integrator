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

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.olingo.server.api.uri.queryoption.OrderByItem;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.wso2.micro.integrator.dataservices.core.engine.DataEntry;

/**
 * This class implements MongoDB datasource related operations for ODataDataHandler.
 */
public class MongoDataHandler implements ODataDataHandler {

    /**
     * configuration ID is the ID given for the data service, at the time
     * when the particular service is created.
     */

    private final String configId;

    /**
     * DocumentId/ObjectId s of the Collections
     */
    private final Map<String, List<String>> primaryKeys;

    /**
     * List of Collections in the Database.
     */
    private final List<String> tableList;

    /**
     * Metadata of the Collections
     */
    private final Map<String, Map<String, DataColumn>> tableMetaData;
    private static final String ETAG = "ETag";
    private static final String DOCUMENT_ID = "_id";
    private final MongoDatabase mongoDatabase;

    /**
     * Preferred chunk size.
     */
    private final int chunkSize;

    /**
     * Number of entities to be skipped during the current read iteration.
     */
    private int skipEntityCount;

    public MongoDataHandler(String configId, MongoDatabase mongoDatabase) {
        this.configId = configId;
        this.mongoDatabase = mongoDatabase;
        this.tableList = generateTableList();
        this.tableMetaData = generateTableMetaData();
        this.primaryKeys = generatePrimaryKeys();
        this.chunkSize = ODataAdapter.getChunkSize();
    }

    /**
     * This method returns database collection metadata.
     * Returns a map with collection name as the key, and the values containing
     * maps with column name as the map key, and the values of the column name
     * map will be a DataColumn object, which represents the column.
     *
     * @return Database Metadata
     * @see DataColumn
     */
    @Override
    public Map<String, Map<String, DataColumn>> getTableMetadata() {
        return this.tableMetaData;
    }

    private Map<String, Map<String, DataColumn>> generateTableMetaData() {
        int ordinalPosition = 1;
        Map<String, Map<String, DataColumn>> metaData = new HashMap<>();

        for (String tableName : tableList) {
            MongoCollection<Document> collection = mongoDatabase.getCollection(tableName);

            try (MongoCursor<Document> cursor = collection.find().iterator()) {
                Map<String, DataColumn> column = new HashMap<>();
                while (cursor.hasNext()) {
                    Document documentData = cursor.next();
                    String tempValue = documentData.toJson();
                    Iterator<?> keys = new JSONObject(tempValue).keys();
                    while (keys.hasNext()) {
                        String columnName = (String) keys.next();
                        DataColumn dataColumn = new DataColumn(columnName, DataColumn.ODataDataType.STRING,
                                ordinalPosition, true, 100, columnName.equals(DOCUMENT_ID));
                        column.put(columnName, dataColumn);
                        ordinalPosition++;
                    }
                }
                metaData.put(tableName, column);
            }
        }
        return metaData;
    }

    /**
     * This method creates a list of collections available in the DB.
     *
     * @returns the collection list of the DB
     */
    @Override
    public List<String> getTableList() {
        return this.tableList;
    }

    private List<String> generateTableList() {
        return new ArrayList<>(mongoDatabase.listCollectionNames().into(new ArrayList<>()));
    }

    /**
     * This method returns the primary keys of all the collections in the database.
     * Return a map with table name as the key, and the values contains a list of column
     * names which act as primary keys in each collection.
     *
     * @return Primary Key Map
     */
    @Override
    public Map<String, List<String>> getPrimaryKeys() {
        return this.primaryKeys;
    }

    private Map<String, List<String>> generatePrimaryKeys() {
        Map<String, List<String>> primaryKeyList = new HashMap<>();
        List<String> tableNames = this.tableList;
        List<String> primaryKey = new ArrayList<>();
        primaryKey.add(DOCUMENT_ID);
        for (String tname : tableNames) {
            primaryKeyList.put(tname, primaryKey);
        }
        return primaryKeyList;
    }

    /**
     * This method reads the data for a given collection.
     * Returns a list of DataEntry objects.
     *
     * @param tableName Name of the table
     * @return EntityCollection
     * @see DataEntry
     */
    @Override
    public List<ODataEntry> readTable(String tableName) {
        List<ODataEntry> entryList = new ArrayList<>();
        try (MongoCursor<Document> cursor = mongoDatabase.getCollection(tableName).find().iterator()) {
            while (cursor.hasNext()) {
                Document document = cursor.next();
                String documentJson = document.toJson();
                JSONObject jsonObject = new JSONObject(documentJson);
                Iterator<String> keys = jsonObject.keys();
                ODataEntry dataEntry = createDataEntryFromResult(document, keys);
                // Set ETag to the entity
                dataEntry.addValue("ETag", ODataUtils.generateETag(configId, tableName, dataEntry));
                entryList.add(dataEntry);
            }
        }

        return entryList;
    }

    public List<ODataEntry> streamTable(String tableName) {
        try (MongoCursor<Document> cursor = mongoDatabase.getCollection(tableName).find()
                .skip(skipEntityCount)
                .limit(chunkSize)
                .iterator()) {
            return readStreamResultSet(tableName, cursor);
        }
    }

    public List<ODataEntry> streamTableWithKeys(String tableName, ODataEntry keys) throws ODataServiceFault {
        throw new ODataServiceFault("MongoDB datasources doesn't support navigation.");
    }

    public void initStreaming() {
        this.skipEntityCount = 0;
    }

    public List<ODataEntry> streamTableWithOrder(String tableName, OrderByOption orderByOption) {
        MongoCollection<Document> readResult = mongoDatabase.getCollection(tableName);
        List<BasicDBObject> stages = getSortStage(orderByOption);

        BasicDBObject skip = new BasicDBObject();
        skip.put("$skip", this.skipEntityCount);
        stages.add(skip);
        BasicDBObject limit = new BasicDBObject();
        limit.put("$limit", this.chunkSize);
        stages.add(limit);
        MongoCursor<Document> iterator = readResult.aggregate(stages).iterator();
        return readStreamResultSet(tableName, iterator);
    }

    /**
     * This method reads the stream result set to generate a list of OData entries.
     *
     * @param tableName Name of the table
     * @param iterator  Iterator of the results set
     * @return
     */
    private List<ODataEntry> readStreamResultSet(String tableName, MongoCursor<Document> iterator) {
        List<ODataEntry> entryList = new ArrayList<>();
        while (iterator.hasNext()) {
            Document documentData = iterator.next();
            String tempValue = documentData.toJson();
            Iterator<String> keys = new JSONObject(tempValue).keys();
            ODataEntry dataEntry = createDataEntryFromResult(documentData, keys);
            //Set Etag to the entity
            dataEntry.addValue(ETAG, ODataUtils.generateETag(this.configId, tableName, dataEntry));
            entryList.add(dataEntry);
        }
        this.skipEntityCount += this.chunkSize;
        return entryList;
    }

    /**
     * This method arranges the sort stage of the aggregator.
     *
     * @param orderByOption List of keys to consider when sorting
     * @return List of DBObjects
     * @see BasicDBObject
     */
    private List<BasicDBObject> getSortStage(OrderByOption orderByOption) {
        List<BasicDBObject> stages = new ArrayList<>();
        BasicDBObject sortList = new BasicDBObject();
        BasicDBObject fieldList = new BasicDBObject();

        for (int i = 0; i < orderByOption.getOrders().size(); i++) {
            final OrderByItem item = orderByOption.getOrders().get(i);
            String expr = item.getExpression().toString().replaceAll("[\\[\\]]", "").replaceAll("[\\{\\}]", "");
            String[] exprArr = expr.split(" ");
            int order = item.isDescending() ? -1 : 1;
            if (exprArr.length == 1) {
                sortList.put(exprArr[0], order);
            } else if (exprArr.length == 2) {
                BasicDBObject length = new BasicDBObject();
                length.put("$strLenCP", "$" + exprArr[1]);
                fieldList.put(exprArr[1] + "Len", length);
                sortList.put(exprArr[1] + "Len", order);
            }
        }
        BasicDBObject addFields = new BasicDBObject();
        addFields.put("$addFields", fieldList);
        BasicDBObject sort = new BasicDBObject();
        sort.put("$sort", sortList);
        stages.add(addFields);
        stages.add(sort);
        return stages;
    }

    /**
     * This method reads the collection data for a given key(i.e. _id).
     * Returns a list of DataEntry object which has been wrapped the entity.
     *
     * @param tableName Name of the table
     * @param keys      Keys to check
     * @return EntityCollection
     * @throws ODataServiceFault
     * @see DataEntry
     */
    @Override
    public List<ODataEntry> readTableWithKeys(String tableName, ODataEntry keys) throws ODataServiceFault {
        List<ODataEntry> entryList = new ArrayList<>();
        ODataEntry dataEntry;
        for (String keyName : keys.getData().keySet()) {
            String keyValue = keys.getValue(keyName);
            String projectionResult = mongoDatabase.getCollection(tableName)
                    .find(new Document("_id", new ObjectId(keyValue))).map(Document::toJson).first();
            if (projectionResult == null) {
                throw new ODataServiceFault(DOCUMENT_ID + keyValue + " does not exist in collection: "
                    + tableName + " .");
            }
            Iterator<String> key = new JSONObject(projectionResult).keys();
            dataEntry = createDataEntryFromResult(Document.parse(projectionResult), key);
            //Set Etag to the entity
            dataEntry.addValue(ETAG, ODataUtils.generateETag(this.configId, tableName, dataEntry));
            entryList.add(dataEntry);
        }
        return entryList;
    }

    /**
     * This method creates an OData DataEntry for a given individual database record.
     * Returns a DataEntry object which has been wrapped in the entity.
     *
     * @param readResult DB result
     * @param keys       Keys set of the DB result
     * @return EntityCollection
     * @see DataEntry
     */
    private ODataEntry createDataEntryFromResult(Document readResult, Iterator<String> keys) {
        ODataEntry dataEntry = new ODataEntry();
        while (keys.hasNext()) {
            String columnName = keys.next();
            Object columnValueObj = readResult.get(columnName);
            String columnValue = columnValueObj != null ? columnValueObj.toString() : null;
            dataEntry.addValue(columnName, columnValue);
        }
        return dataEntry;
    }

    /**
     * This method inserts a given entity to the given collection.
     *
     * @param tableName Name of the table
     * @param entity    Entity
     * @throws ODataServiceFault
     */
    @Override
    public ODataEntry insertEntityToTable(String tableName, ODataEntry entity) {
        ODataEntry createdEntry = new ODataEntry();
        final Document document = new Document();
        for (String columnName : entity.getData().keySet()) {
            String columnValue = entity.getValue(columnName);
            document.put(columnName, columnValue);
            entity.addValue(columnName, columnValue);
        }
        ObjectId objectId = new ObjectId();
        document.put(DOCUMENT_ID, objectId);
        mongoDatabase.getCollection(tableName).insertOne(document);
        String documentIdValue = objectId.toString();
        createdEntry.addValue(DOCUMENT_ID, documentIdValue);
        //Set Etag to the entity
        createdEntry.addValue(ODataConstants.E_TAG, ODataUtils.generateETag(this.configId, tableName, entity));
        return createdEntry;
    }

    /**
     * This method deletes the entity from the collection for a given key.
     *
     * @param tableName Name of the table
     * @param entity    Entity
     * @throws ODataServiceFault
     */
    @Override
    public boolean deleteEntityInTable(String tableName, ODataEntry entity) throws ODataServiceFault {
        String documentId = entity.getValue(DOCUMENT_ID);
        Document filter = new Document(DOCUMENT_ID, new ObjectId(documentId));
        DeleteResult deleteResult = mongoDatabase.getCollection(tableName).deleteOne(filter);
        if (deleteResult.getDeletedCount() == 1) {
            return deleteResult.wasAcknowledged();
        } else {
            throw new ODataServiceFault("Document ID: " + documentId + " does not exist in the collection.");
        }
    }

    /**
     * This method updates the given entity in the given collection.
     *
     * @param tableName     Name of the table
     * @param newProperties New Properties
     * @throws ODataServiceFault
     */
    @Override
    public boolean updateEntityInTable(String tableName, ODataEntry newProperties) throws ODataServiceFault {
        List<String> primaryKeyList = this.primaryKeys.get(tableName);
        String newPropertyObjectKeyValue = newProperties.getValue(DOCUMENT_ID);
        StringBuilder mongoUpdate = new StringBuilder();
        mongoUpdate.append("{$set: {");
        boolean propertyMatch = false;
        for (String column : newProperties.getData().keySet()) {
            if (!primaryKeyList.contains(column)) {
                if (propertyMatch) {
                    mongoUpdate.append("', ");
                }
                String propertyValue = newProperties.getValue(column);
                mongoUpdate.append(column).append(": '").append(propertyValue);
                propertyMatch = true;
            }
        }
        mongoUpdate.append("'}}");
        String query = mongoUpdate.toString();
        Document filter = new Document("_id", new ObjectId(newPropertyObjectKeyValue));
        Document update = Document.parse(query);
        UpdateResult result = mongoDatabase.getCollection(tableName).updateOne(filter, update);
        if (result.getMatchedCount() == 1) {
            return result.wasAcknowledged();
        } else {
            throw new ODataServiceFault("Document ID: " + newPropertyObjectKeyValue
                    + " does not exist in the collection " + tableName + ".");
        }
    }

    /**
     * This method updates the entity in table when transactional update is necessary.
     *
     * @param tableName     Table Name
     * @param oldProperties Old Properties
     * @param newProperties New Properties
     * @throws ODataServiceFault
     */
    @Override
    public boolean updateEntityInTableTransactional(String tableName, ODataEntry oldProperties,
                                                    ODataEntry newProperties) throws ODataServiceFault {
        String oldPropertyObjectKeyValue = oldProperties.getValue(DOCUMENT_ID);
        StringBuilder updateNewProperties = new StringBuilder();
        updateNewProperties.append("{$set: {");
        boolean propertyMatch = false;
        for (String column : newProperties.getData().keySet()) {
            if (propertyMatch) {
                updateNewProperties.append("', ");
            }
            String propertyValue = newProperties.getValue(column);
            updateNewProperties.append(column).append(": '").append(propertyValue);
            propertyMatch = true;
        }
        updateNewProperties.append("'}}");
        String query = updateNewProperties.toString();
        Document update = Document.parse(query);
        Document filter = new Document("_id", new ObjectId(oldPropertyObjectKeyValue));
        MongoCollection<Document> collection = mongoDatabase.getCollection(tableName);
        UpdateResult result = collection.updateOne(filter, update);
        if (result.getMatchedCount() == 1) {
            return result.wasAcknowledged();
        } else {
            throw new ODataServiceFault("Error occurred while updating the entity in collection: " + tableName);
        }
    }

    @Override
    public Map<String, NavigationTable> getNavigationProperties() {
        return null;
    }

    private ThreadLocal<Boolean> transactionAvailable = new ThreadLocal<Boolean>() {
        protected synchronized Boolean initialValue() {
            return false;
        }
    };

    /**
     * This method opens the transaction.
     */
    @Override
    public void openTransaction() {
        this.transactionAvailable.set(true);
        // doesn't support
    }

    /**
     * This method commits the transaction.
     */
    @Override
    public void commitTransaction() {
        this.transactionAvailable.set(false);
        // doesn't support
    }

    /**
     * This method rollbacks the transaction.
     */
    @Override
    public void rollbackTransaction() {
        this.transactionAvailable.set(false);
        // doesn't support
    }

    /**
     * This method updates the references of the table where the keys were imported.
     *
     * @param rootTableName       Root - Table Name
     * @param rootTableKeys       Root - Entity keys (Primary Keys)
     * @param navigationTable     Navigation - Table Name
     * @param navigationTableKeys Navigation - Entity Name (Primary Keys)
     * @throws ODataServiceFault
     */
    @Override
    public void updateReference(String rootTableName, ODataEntry rootTableKeys, String navigationTable,
                                ODataEntry navigationTableKeys) throws ODataServiceFault {
        throw new ODataServiceFault("MongoDB datasources do not support references.");
    }

    /**
     * This method deletes the references of the table where the keys were imported.
     *
     * @param rootTableName       Root - Table Name
     * @param rootTableKeys       Root - Entity keys (Primary Keys)
     * @param navigationTable     Navigation - Table Name
     * @param navigationTableKeys Navigation - Entity Name (Primary Keys)
     * @throws ODataServiceFault
     */

    @Override
    public void deleteReference(String rootTableName, ODataEntry rootTableKeys, String navigationTable,
                                ODataEntry navigationTableKeys) throws ODataServiceFault {
        throw new ODataServiceFault("MongoDB datasources do not support references.");
    }

    @Override
    public int getEntityCount(String tableName) {
        MongoCollection<Document> readResult = mongoDatabase.getCollection(tableName);
        int rowCount = (int) readResult.countDocuments();
        return rowCount;
    }

    @Override
    public int getEntityCountWithKeys(String tableName, ODataEntry keys) throws ODataServiceFault {
        throw new ODataServiceFault("MongoDB datasources doesn't support navigation.");
    }
}
