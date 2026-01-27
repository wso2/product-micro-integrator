/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.micro.integrator.observability.util;

/**
 * Constants required in instrumenting Prometheus metrics.
 */
public class MetricConstants {

    // Constants for Synapse artifacts
    public static final String INBOUND_ENDPOINT = "inbound-endpoint";
    public static final String DATA_SERVICE = "data-service";

    public static final String HTTP_PORT = "http.nio.port";
    public static final String JAVA_VERSION = "java.vm.specification.version";
    public static final String JAVA_HOME = "java.home";
    public static final String UPDATE_LEVEL = "UPDATE_LEVEL";

    //Constants for Synapse properties
    public static final String TRANSPORT_IN_URL = "TransportInURL";
    public static final String SERVICE_PREFIX = "SERVICE_PREFIX";
    public static final String PORT_OFFSET = "portOffset";
    public static final String INTERNAL_HTTP_API_PORT = "internal.http.api.port";

    //Constants for metric names
    public static final String PROXY_REQUEST_COUNT_TOTAL = "wso2_integration_proxy_request_count_total";
    public static final String PROXY_REQUEST_COUNT_ERROR_TOTAL = "wso2_integration_proxy_request_count_error_total";
    public static final String PROXY_LATENCY_SECONDS = "wso2_integration_proxy_latency_seconds";

    public static final String API_REQUEST_COUNT_TOTAL = "wso2_integration_api_request_count_total";
    public static final String API_REQUEST_COUNT_ERROR_TOTAL = "wso2_integration_api_request_count_error_total";
    public static final String API_LATENCY_SECONDS = "wso2_integration_api_latency_seconds";

    public static final String INBOUND_ENDPOINT_REQUEST_COUNT_TOTAL =
            "wso2_integration_inbound_endpoint_request_count_total";
    public static final String INBOUND_ENDPOINT_REQUEST_COUNT_ERROR_TOTAL =
            "wso2_integration_inbound_endpoint_request_count_error_total";
    public static final String INBOUND_ENDPOINT_LATENCY_SECONDS =
            "wso2_integration_inbound_endpoint_latency_seconds";

    public static final String DATA_SERVICE_REQUEST_COUNT_TOTAL =
            "wso2_integration_data_service_request_count_total";
    public static final String DATA_SERVICE_REQUEST_COUNT_ERROR_TOTAL =
            "wso2_integration_data_service_request_count_error_total";
    public static final String DATA_SERVICE_LATENCY_SECONDS =
            "wso2_integration_data_service_latency_seconds";

    public static final String SERVER_UP = "wso2_integration_server_up";
    public static final String SERVICE_UP = "wso2_integration_service_up";
    public static final String SERVER_VERSION = "wso2_integration_server_version";

    public static final String METRIC_HANDLER = "metric_handler";
    public static final String PROXY_LATENCY_BUCKETS = "proxy_latency_buckets";
    public static final String API_LATENCY_BUCKETS = "api_latency_buckets";
    public static final String INBOUND_ENDPOINT_LATENCY_BUCKETS = "inbound_endpoint_latency_buckets";
    public static final String DATA_SERVICE_LATENCY_BUCKETS = "data_service_latency_buckets";

    public static final String PROXY_LATENCY_TIMER = "PROXY_LATENCY_TIMER";
    public static final String API_LATENCY_TIMER = "API_LATENCY_TIMER";
    public static final String INBOUND_ENDPOINT_LATENCY_TIMER = "INBOUND_ENDPOINT_LATENCY_TIMER";
    public static final String DATA_SERVICE_LATENCY_TIMER = "DATA_SERVICE_LATENCY_TIMER";

    public static final String SERVER = "Server";
    public static final String SERVICE = "Service";
    public static final String VERSION = "Version";

    //Constants for Prometheus Metrics types
    public static final String COUNTER = "Counter";
    public static final String HISTOGRAM = "Histogram";
    public static final String GAUGE = "Gauge";

    public static final String SERVICE_NAME = "service_name";
    public static final String SERVICE_TYPE = "service_type";
    public static final String INVOCATION_URL = "invocation_url";
    public static final String JAVA_VERSION_LABEL = "java_version";
    public static final String JAVA_HOME_LABEL = "java_home";
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String VERSION_LABEL = "version";
    public static final String UPDATE_LEVEL_LABEL = "update_level";

    public static final String MESSAGE_DIRECTION_IN = "in";

    public static final String MESSAGE_DIRECTION_OUT = "out";
    public static final String ENABLE_LEGACY_PROMETHEUS = "usePrometheusLegacyApi";

}
