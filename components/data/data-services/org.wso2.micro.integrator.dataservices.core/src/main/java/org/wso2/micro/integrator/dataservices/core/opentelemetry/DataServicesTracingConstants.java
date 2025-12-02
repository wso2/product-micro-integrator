/*
 * Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.dataservices.core.opentelemetry;

public class DataServicesTracingConstants {
    public static final int DEFAULT_PARENT_INDEX = -1;
    public static final int DATA_SERVICE_INDEX = 0;
    public static final int DATA_QUERY_EXECUTION_INDEX = 1;
    public static final int MULTI_REQUEST_BASE_INDEX = 2;
    public static final String MULTI_REQUEST_LAST_INDEX_PROPERTY = "MULTI_REQUEST_LAST_INDEX_PROPERTY";
    public static final String ODATA_SERVICE = "odata/";
    public static final String URL_SEPARATOR = "/";
    public static final Character URL_SEPARATOR_CHAR = '/';
    public static final String TRANSPORT_IN_URL = "TransportInURL";
    public static final String HTTP_METHOD = "http.method";
    public static final String HTTP_URL = "http.url";
    public static final String HTTP_METHOD_OBJECT = "HTTP_METHOD_OBJECT";
    public static final String DB_CONFIG_ID = "db.config.id";
    public static final String DB_QUERY_ID = "db.query.id";
    public static final String DEFAULT_ODATA_SERVICE_NAME = "default_odata_service_name";
}
