/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityIterator;
import org.apache.olingo.commons.api.edm.EdmEntitySet;

import java.util.Iterator;
import java.util.List;

/**
 * This class stores OData Entity iterator and parameters required to process entities.
 */
public class StreamingEntityIterator extends EntityIterator {

    /**
     * Entity iterator
     */
    public static Iterator<Entity> iterator;

    /**
     * Number of processed entities.
     */
    public int entityCount;

    /**
     * Number of applicable entries.
     */
    public int odataCount;

    /**
     * Number of entries.
     */
    public int rowsCount;

    private ODataAdapter adapter;

    /**
     * Container of the Entity Type.
     */
    private EdmEntitySet edmEntitySet;

    /**
     * Base URL of the request.
     */
    private String baseURL;

    /**
     * List of entities.
     */
    private List<Entity> entityList;

    /**
     * OData navigation properties.
     */
    private ODataEntry properties;

    /**
     * Name of the table.
     */
    private String tableName;

    /**
     * OData query options.
     */
    private QueryOptions queryOptions;

    public StreamingEntityIterator(ODataAdapter adapter, EdmEntitySet edmEntitySet, String baseURL,
                                   Iterator<Entity> iterator, List<Entity> entityList, QueryOptions queryOptions,
                                   int rowsCount, ODataEntry properties, String tableName) {
        this.adapter = adapter;
        this.edmEntitySet = edmEntitySet;
        this.baseURL = baseURL;
        this.iterator = iterator;
        this.entityList = entityList;
        this.queryOptions = queryOptions;
        this.rowsCount = rowsCount;
        this.tableName = tableName;
        this.properties = properties;
        this.entityCount = 0;

        if (queryOptions != null && queryOptions.getSkipTokenOption() != null) {
            this.setNext(queryOptions.getNextLinkUri());
        }

        this.setCount(0);
    }

    public QueryOptions getQueryOptions() {
        return queryOptions;
    }

    public ODataEntry getProperties() {
        return properties;
    }

    public void setProperties(ODataEntry properties) {
        this.properties = properties;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public ODataAdapter getAdapter() {
        return adapter;
    }

    public EdmEntitySet getEdmEntitySet() {
        return edmEntitySet;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public List<Entity> getEntityList() {
        return entityList;
    }
    public int getOdataCount() {

        return odataCount;
    }

    @Override
    public boolean hasNext() {
        return this.hasNext();
    }

    @Override
    public Entity next() {
        return this.next();
    }

}
