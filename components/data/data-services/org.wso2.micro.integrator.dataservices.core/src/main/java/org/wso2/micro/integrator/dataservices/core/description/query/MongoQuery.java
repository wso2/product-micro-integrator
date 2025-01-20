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
package org.wso2.micro.integrator.dataservices.core.description.query;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import java.util.stream.StreamSupport;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.datasource.DataColumn;
import org.wso2.micro.integrator.dataservices.core.datasource.DataRow;
import org.wso2.micro.integrator.dataservices.core.datasource.FixedDataRow;
import org.wso2.micro.integrator.dataservices.core.datasource.QueryResult;
import org.wso2.micro.integrator.dataservices.core.description.config.MongoConfig;
import org.wso2.micro.integrator.dataservices.core.description.event.EventTrigger;
import org.wso2.micro.integrator.dataservices.core.engine.DataEntry;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.InternalParam;
import org.wso2.micro.integrator.dataservices.core.engine.InternalParamCollection;
import org.wso2.micro.integrator.dataservices.core.engine.OutputElement;
import org.wso2.micro.integrator.dataservices.core.engine.OutputElementGroup;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;
import org.wso2.micro.integrator.dataservices.core.engine.QueryParam;
import org.wso2.micro.integrator.dataservices.core.engine.Result;
import org.wso2.micro.integrator.dataservices.core.engine.StaticOutputElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamWriter;

/**
 * This class represents the MongoDB data services query implementation.
 */
public class MongoQuery extends Query {

    private final MongoConfig config;

    private final String expression;

    public MongoQuery(DataService dataService, String queryId, String configId, String expression,
                      List<QueryParam> queryParams, Result result, EventTrigger inputEventTrigger,
                      EventTrigger outputEventTrigger, Map<String, String> advancedProperties, String inputNamespace)
            throws DataServiceFault {
        super(dataService, queryId, queryParams, result, configId, inputEventTrigger, outputEventTrigger,
              advancedProperties, inputNamespace);
        try {
            this.expression = expression;
            this.config = (MongoConfig) this.getDataService().getConfig(this.getConfigId());
        } catch (ClassCastException e) {
            throw new DataServiceFault(e, "Configuration is not a Mongo config:" + this.getConfigId());
        }
    }

    @Override
    public Object runPreQuery(InternalParamCollection params, int queryLevel) throws DataServiceFault {
        try {
            return new MongoQueryResult(this.getExpression(), new ArrayList<>(params.getParams()));
        } catch (Exception e) {
            throw new DataServiceFault(e, "Error in MongoQuery.runQuery: " + e.getMessage());
        }
    }

    @Override
    public void runPostQuery(Object result, XMLStreamWriter xmlWriter, InternalParamCollection params, int queryLevel)
            throws DataServiceFault {
        QueryResult queryResult = (QueryResult) result;
        DataEntry dataEntry;
        DataRow currentRow;
        String tmpVal;
        while (queryResult != null && queryResult.hasNext()) {
            currentRow = queryResult.next();
            tmpVal = currentRow.getValueAt(DBConstants.MongoDB.RESULT_COLUMN_NAME);
            // if tmpVal is not a json then the query is a count query therefore we add count as the column name.

            List<OutputElement> elements = this.getResult().getDefaultElementGroup().getAllElements();
            List<String> totalParamList = new ArrayList<>();
            for (OutputElement element : elements) {
                addInnerElements(element, totalParamList);
            }
            dataEntry = wrapMongoRow(tmpVal, totalParamList);
            this.writeResultEntry(xmlWriter, dataEntry, params, queryLevel);
        }
    }

    private void addInnerElements(OutputElement outputElement, List<String> paramList) {
        if (outputElement instanceof StaticOutputElement) {
            paramList.add(outputElement.getParam());
        } else if (outputElement instanceof OutputElementGroup) {
            List<OutputElement> outputElements = ((OutputElementGroup) outputElement).getAllElements();
            for (OutputElement element : outputElements) {
                addInnerElements(element, paramList);
            }
        }
    }

    /**
     * This method convert Mongo json output to DataEntry object to support out mappings.
     *
     * @param jsonString Json String
     * @return DataEntry
     * @throws DataServiceFault
     */
    private DataEntry wrapMongoRow(String jsonString, List<String> keyList) throws DataServiceFault {
        DataEntry dataEntry = new DataEntry();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            for (String key : keyList) {
                dataEntry.addValue(key, new ParamValue(getElementValueFromJson(jsonString, jsonObject, key)));
            }
        } catch (JSONException e) {
            // Normally there can't be any JSON Exception because Mongo send a proper json text for find queries.
            // but it send String for count queries.
            try {
                if (Integer.parseInt(jsonString) >= 0) {
                    dataEntry.addValue(DBConstants.MongoDB.RESULT_COLUMN_NAME.toLowerCase(), new ParamValue(jsonString));
                }
            } catch (NumberFormatException e1) {
                // Or for exists operation. For exists operation it produces only true false value instead of json.
                try {
                    if(jsonString.trim().equalsIgnoreCase("true") ||
                            jsonString.trim().equalsIgnoreCase("false")) {
                        dataEntry.addValue(DBConstants.MongoDB.RESULT_COLUMN_NAME.toLowerCase(), new ParamValue(jsonString));
                    } else {
                        throw new DataServiceFault("Error occurred when retrieving data. :" + e.getMessage());
                    }
                } catch (Exception ex) {
                    throw new DataServiceFault("Error occurred when retrieving data. :" + e.getMessage());
                }
            }
        }
        return dataEntry;
    }

    private String getElementValueFromJson(String jsonString, JSONObject object, String jsonPath) throws JSONException {
        String value = null;
        JSONObject tempObject = object;
        JSONArray tempArray;
        String[] tokens = jsonPath.split("\\.");
        if (tokens[0].equals(DBConstants.MongoDB.RESULT_COLUMN_NAME.toLowerCase())) {
            if (tokens.length == 1) {
                value = jsonString;
            } else {
                for (int i = 1; i < tokens.length; i++) {
                    if (i == tokens.length - 1) {
                        if (tokens[i].contains("[")) {
                            Object[] arrayObjects = getArrayElementKeys(tokens[i]);
                            tempArray = tempObject.getJSONArray(arrayObjects[0].toString());
                            value = tempArray.getString((Integer) arrayObjects[1]);
                        } else {
                            value = tempObject.getString(tokens[i]);
                        }
                    } else {
                        if (tokens[i].contains("[")) {
                            Object[] arrayObjects = getArrayElementKeys(tokens[i]);
                            tempArray = tempObject.getJSONArray(arrayObjects[0].toString());
                            tempObject = tempArray.getJSONObject((Integer) arrayObjects[1]);
                        } else {
                            tempObject = tempObject.getJSONObject(tokens[i]);
                        }
                    }
                }
            }
            return value;
        } else {
            return null;
        }
    }

    private Object[] getArrayElementKeys(String element) {
        int keyIndex;
        String arrayName;
        arrayName = element.substring(0, element.indexOf('['));
        keyIndex = Integer.parseInt(element.substring(element.indexOf('[') + 1, element.indexOf(']')));
        return new Object[] { arrayName, keyIndex };
    }

    public MongoConfig getConfig() {
        return config;
    }

    public String getExpression() {
        return expression;
    }

    private Object[] decodeQuery(String query) throws DataServiceFault {
        int i1 = query.lastIndexOf('.');
        if (i1 == -1) {
            throw new DataServiceFault("The MongoDB Collection not specified in the query '" + query + "'");
        }
        String collection = query.substring(0, i1).trim();
        int i2 = query.indexOf('(', i1);
        if (i2 == -1 || i2 - i1 <= 1) {
            throw new DataServiceFault("Invalid MongoDB operation in the query '" + query + "'");
        }
        String operation = query.substring(i1 + 1, i2).trim();
        int i3 = query.lastIndexOf(')');
        if (i3 == -1) {
            throw new DataServiceFault("Invalid MongoDB operation in the query '" + query + "'");
        }
        String opQuery = null;
        if (i3 - i2 > 1) {
            opQuery = query.substring(i2 + 1, i3).trim();
        }
        DBConstants.MongoDB.MongoOperation mongoOp = this.convertToMongoOp(operation);
        if (mongoOp == DBConstants.MongoDB.MongoOperation.UPDATE) {
            List<Object> result = new ArrayList<>();
            result.add(collection);
            result.add(mongoOp);
            result.addAll(parseInsertQuery(opQuery));
            return result.toArray();
        } else {
            return new Object[] { collection, mongoOp, this.checkAndCleanOpQuery(opQuery) };
        }
    }

    private String checkAndCleanOpQuery(String opQuery) throws DataServiceFault {
        if (opQuery == null) {
            return null;
        }
        int a = 0, b = 0;
        if (opQuery.startsWith("'") || opQuery.startsWith("\"")) {
            a = 1;
        }
        if (opQuery.endsWith("'") || opQuery.endsWith("\"")) {
            b = 1;
        }
        return opQuery.substring(a, opQuery.length() - b);
    }

    private List<Object> parseInsertQuery(String opQuery) throws DataServiceFault {
        List<Object> tokens = new ArrayList<>();
        int bracketCount = 0;
        StringBuilder buff = new StringBuilder(100);
        for (char ch : opQuery.toCharArray()) {
            if (ch == ',' && bracketCount == 0) {
                tokens.add(this.checkAndCleanOpQuery(buff.toString().trim()));
                buff.delete(0, buff.length());
            } else {
                buff.append(ch);
                if (ch == '{') {
                    bracketCount++;
                } else if (ch == '}') {
                    bracketCount--;
                }
            }
        }
        String lastToken = buff.toString().trim();
        if (lastToken.length() > 0) {
            tokens.add(this.checkAndCleanOpQuery(lastToken));
        }
        return tokens;
    }

    private DBConstants.MongoDB.MongoOperation convertToMongoOp(String operation) throws DataServiceFault {
        if (DBConstants.MongoDB.MongoOperationLabels.COUNT.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.COUNT;
        } else if (DBConstants.MongoDB.MongoOperationLabels.DROP.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.DROP;
        } else if (DBConstants.MongoDB.MongoOperationLabels.FIND.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.FIND;
        } else if (DBConstants.MongoDB.MongoOperationLabels.FIND_ONE.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.FIND_ONE;
        } else if (DBConstants.MongoDB.MongoOperationLabels.INSERT.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.INSERT;
        } else if (DBConstants.MongoDB.MongoOperationLabels.REMOVE.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.REMOVE;
        } else if (DBConstants.MongoDB.MongoOperationLabels.UPDATE.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.UPDATE;
        } else if (DBConstants.MongoDB.MongoOperationLabels.EXISTS.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.EXISTS;
        }  else if (DBConstants.MongoDB.MongoOperationLabels.CREATE.equals(operation)) {
            return DBConstants.MongoDB.MongoOperation.CREATE;
        } else {
            throw new DataServiceFault("Unknown MongoDB operation '" + operation + "'");
        }
    }

    private MongoDatabase getMongoDatabase() {
        return this.config.getMongoDatabase();
    }

    public class MongoQueryResult implements QueryResult {

        private Iterator<?> dataIterator;

        public MongoQueryResult(String query, List<InternalParam> params) throws DataServiceFault {
            Object[] request = decodeQuery(query);
            MongoCollection<Document> collection = getMongoDatabase().getCollection((String) request[0]);
            String opQuery = (String) request[2];
            Object[] mongoParams = DBUtils.convertInputParamValues(params);
            switch ((DBConstants.MongoDB.MongoOperation) request[1]) {
                case COUNT:
                    this.dataIterator = this.doCount(collection, opQuery, mongoParams);
                    break;
                case FIND:
                    this.dataIterator = this.doFind(collection, opQuery, mongoParams);
                    break;
                case FIND_ONE:
                    this.dataIterator = this.doFindOne(collection, opQuery, mongoParams);
                    break;
                case DROP:
                    this.doDrop(collection);
                    break;
                case INSERT:
                    this.doInsert(collection, opQuery, mongoParams);
                    break;
                case REMOVE:
                    this.doRemove(collection, opQuery, mongoParams);
                    break;
                case EXISTS:
                    this.dataIterator = this.isExist(collection);
                    break;
                case CREATE:
                    this.createCollection(collection);
                    break;
                case UPDATE:
                    if (request.length < 4) {
                        throw new DataServiceFault("An MongoDB update statement must contain a modifier");
                    }
                    String modifier = (String) request[3];
                    boolean upsert = false;
                    if (request.length > 4) {
                        upsert = Boolean.parseBoolean((String) request[4]);
                    }
                    boolean multi = false;
                    if (request.length > 5) {
                        multi = Boolean.parseBoolean((String) request[5]);
                    }
                    this.doUpdate(collection, opQuery, mongoParams, modifier, upsert, multi);
                    break;
            }
        }

        private Iterator<Long> doCount(MongoCollection collection, String opQuery, Object[] parameters) {
            long count;
            if (opQuery != null) {
                if (parameters.length > 0) {
                    opQuery = replacePlaceholders(opQuery, parameters);
                }
                count = collection.countDocuments(Document.parse(opQuery));
            } else {
                count = collection.countDocuments();
            }
            List<Long> countResult = new ArrayList<>();
            countResult.add(count);
            return countResult.iterator();
        }

        private Iterator<String> doFind(MongoCollection collection, String opQuery, Object[] parameters) {
            if (opQuery != null) {
                if (parameters.length > 0) {
                    opQuery = replacePlaceholders(opQuery, parameters);
                }
            } else {
                opQuery = "{}";
            }
            FindIterable<Document> dbResult = collection.find(Document.parse(opQuery));
            return StreamSupport.stream(dbResult.spliterator(), false)
                    .map(Document::toJson)
                    .iterator();
        }

        private Iterator<String> isExist(MongoCollection collection) {
            List<String> collectionNames = getMongoDatabase().listCollectionNames().into(new ArrayList<>());
            // Check if the collection name exists in the list
            Boolean isExists = collectionNames.contains(collection.getNamespace().getCollectionName());
            List<String> result = new ArrayList<>();
            result.add(isExists.toString());
            return result.iterator();
        }

        private void createCollection(MongoCollection collection) {
            getMongoDatabase().createCollection(String.valueOf(collection.getNamespace()));
        }

        private Iterator<String> doFindOne(MongoCollection collection, String opQuery, Object[] parameters) {
            if (opQuery != null) {
                if (parameters.length > 0) {
                    opQuery = replacePlaceholders(opQuery, parameters);
                }
            } else {
                opQuery = "{}";
            }
            Document document = (Document) collection.find(Document.parse(opQuery)).first();
            String value = document.toJson();
            List<String> result = new ArrayList<>();
            result.add(value);
            return result.iterator();
        }

        private void doInsert(MongoCollection collection, String opQuery, Object[] parameters) throws DataServiceFault {
            if (opQuery != null) {
                if (parameters.length > 0) {
                    opQuery = replacePlaceholders(opQuery, parameters);
                }
                collection.insertOne(Document.parse(opQuery));
            } else {
                throw new DataServiceFault("Mongo insert statements must contain a query");
            }
        }

        public String replacePlaceholders(String query, Object[] parameters) {
            List<Object> remainingParameters = new ArrayList<>();
            StringBuilder updatedQuery = new StringBuilder();
            int paramIndex = 0;
            String[] parts = query.split("#");
            for (int i = 0; i < parts.length; i++) {
                updatedQuery.append(parts[i]);
                if (i < parts.length - 1) {
                    if (paramIndex < parameters.length) {
                        Object param = parameters[paramIndex];
                        if (param instanceof String) {
                            updatedQuery.append("\"").append(param).append("\"");
                        } else {
                            updatedQuery.append(param);
                        }
                        paramIndex++;
                    } else {
                        updatedQuery.append("#");
                    }
                }
            }

            for (int i = paramIndex; i < parameters.length; i++) {
                remainingParameters.add(parameters[i]);
            }
            Object[] updatedParameters = remainingParameters.toArray(new Object[0]);
            System.arraycopy(updatedParameters, 0, parameters, 0, updatedParameters.length);
            return updatedQuery.toString();
        }

        private void doRemove(MongoCollection collection, String opQuery, Object[] parameters) throws DataServiceFault {
            if (opQuery != null) {
                if (parameters.length > 0) {
                    opQuery = replacePlaceholders(opQuery, parameters);
                }
                collection.deleteMany(Document.parse(opQuery));
            } else {
                throw new DataServiceFault("Mongo remove statements must contain a query");
            }
        }

        private void doUpdate(MongoCollection collection, String opQuery, Object[] parameters, String modifier,
                              boolean upsert, boolean multi) throws DataServiceFault {
            UpdateOptions options = null;
            if (opQuery != null) {
                if (parameters.length > 0) {
                    opQuery = replacePlaceholders(opQuery, parameters);
                    modifier = replacePlaceholders(modifier, parameters);
                    options = new UpdateOptions().upsert(upsert);
                }
                if (multi) {
                    collection.updateMany(Document.parse(opQuery), Document.parse(modifier), options);
                } else {
                    collection.updateOne(Document.parse(opQuery), Document.parse(modifier), options);
                }
            } else {
                throw new DataServiceFault("Mongo update statements must contain a query");
            }
        }

        private void doDrop(MongoCollection collection) {
            collection.drop();
        }

        @Override
        public List<DataColumn> getDataColumns() throws DataServiceFault {
            List<DataColumn> result = new ArrayList<>();
            result.add(new DataColumn(DBConstants.MongoDB.RESULT_COLUMN_NAME));
            return result;
        }

        @Override
        public boolean hasNext() throws DataServiceFault {
            return this.dataIterator != null && this.dataIterator.hasNext();
        }

        @Override
        public DataRow next() throws DataServiceFault {
            if (this.dataIterator == null) {
                throw new DataServiceFault("No Mongo data result available");
            } else {
                Object data = this.dataIterator.next();
                Map<String, String> values = new HashMap<>();
                values.put(DBConstants.MongoDB.RESULT_COLUMN_NAME, data.toString());
                return new FixedDataRow(values);
            }
        }
    }

}
