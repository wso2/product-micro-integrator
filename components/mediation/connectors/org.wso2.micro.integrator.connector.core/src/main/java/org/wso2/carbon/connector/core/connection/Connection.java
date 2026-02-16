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
package org.wso2.carbon.connector.core.connection;

import org.wso2.carbon.connector.core.ConnectException;

/**
 * Represents a base connection that other
 * connections should implement
 *
 * @deprecated This class is deprecated. Use the new connector core module available at
 * <a href="https://github.com/wso2-extensions/mi-connector-core">https://github.com/wso2-extensions/mi-connector-core</a>
 */
@Deprecated
public interface Connection {

    /**
     * Make the connection
     * @throws ConnectException
     */
     void connect(ConnectionConfig config) throws ConnectException;

    /**
     * Close the connection and cleanup resources
     * @throws ConnectException
     */
    void close() throws ConnectException;

}
