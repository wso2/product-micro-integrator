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
package org.wso2.carbon.connector.core.util;

/**
 * Contains constant values
 *
 * @deprecated This class is deprecated. Use the new connector core module available at
 * <a href="https://github.com/wso2-extensions/mi-connector-core">https://github.com/wso2-extensions/mi-connector-core</a>
 */
@Deprecated
public class Constants {

    // Connection Pool Parameters
    public static final String MAX_ACTIVE_CONNECTIONS = "maxActiveConnections";
    public static final String MAX_IDLE_CONNECTIONS = "maxIdleConnections";
    public static final String MAX_WAIT_TIME = "maxWaitTime";
    public static final String MAX_EVICTION_TIME = "minEvictionTime";
    public static final String EVICTION_CHECK_INTERVAL = "evictionCheckInterval";
    public static final String EXHAUSTED_ACTION = "exhaustedAction";
    public static final String INIT_CONFIG_KEY = "INIT_CONFIG_KEY";
}
