/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.mediation.commons.rest.api.swagger;

import org.apache.axis2.AxisFault;
import org.apache.synapse.api.API;

/**
 * This interface should be implemented
 */
public interface ServerConfig {

    /**
     * Function to retrieve server host name for host entry in swagger definition (API).
     *
     * @return server host name
     * @throws AxisFault error occurred while retrieving the host name.
     */
    String getHost(API api) throws AxisFault;

    /**
     * Function to retrieve server host name by giving a transport.
     *
     * @param transport name of the transport. ex: HTTP/ HTTPS
     * @return server host name
     * @throws AxisFault error occurred while retrieving the host name.
     */
    String getHost(String transport) throws AxisFault;
}
