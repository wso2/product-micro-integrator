/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
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

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish;

public class PublisherConstants {
    public static class SynapseConfigKeys {
        /**
         * Schema version of the analytic.
         */
        public static final int SCHEMA_VERSION = 1;

        /**
         * Unique identifier for the publisher that can be used to filter analytics if multiple micro integrators are
         * publishing data to the same Elasticsearch server.
         */
        public static final String IDENTIFIER = "analytics.id";

        /**
         * Name of the Synapse configuration used to determine if analytics for APIs are enabled or disabled.
         */
        public static final String API_ANALYTICS_ENABLED = "analytics.api_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if analytics for ProxyServices are enabled or disabled.
         */
        public static final String PROXY_SERVICE_ANALYTICS_ENABLED = "analytics.proxy_service_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if analytics for Sequences are enabled or disabled.
         */
        public static final String SEQUENCE_ANALYTICS_ENABLED = "analytics.sequence_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if analytics for Endpoints are enabled or disabled.
         */
        public static final String ENDPOINT_ANALYTICS_ENABLED = "analytics.endpoint_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if analytics for Inbound Endpoints are enabled or disabled.
         */
        public static final String INBOUND_ENDPOINT_ANALYTICS_ENABLED = "analytics.inbound_endpoint_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine the prefix analytics are published with.
         * The purpose of this prefix is to distinguish log lines which hold analytics data from others.
         */
        public static final String ANALYTICS_PREFIX = "analytics.prefix";

        /**
         * Name of the Synapse configuration used to determine if the analytics service is enabled.
         */
        public static final String ANALYTICS_ENABLED = "analytics.enabled";
    }
}
