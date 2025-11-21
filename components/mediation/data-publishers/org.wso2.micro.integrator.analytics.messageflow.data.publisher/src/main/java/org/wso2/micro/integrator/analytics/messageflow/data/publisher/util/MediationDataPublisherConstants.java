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
package org.wso2.micro.integrator.analytics.messageflow.data.publisher.util;

public class MediationDataPublisherConstants {

    public static final String STREAM_NAME = "org.wso2.esb.analytics.stream.FlowEntry";
    public static final String STREAM_VERSION = "1.0.0";

    public static final String CONFIG_STREAM_NAME = "org.wso2.esb.analytics.stream.ConfigEntry";
    public static final String CONFIG_STREAM_VERSION = "1.0.0";
    public static final String DATABRIDGE_PUBLISHER_TYPE = "databridge";
    public static final String LOG_PUBLISHER_TYPE = "log";
    public static final String MOESIF_PUBLISHER_TYPE = "moesif";
    public static final String ANALYTICS_TYPE = "analytics.publisher";
}
