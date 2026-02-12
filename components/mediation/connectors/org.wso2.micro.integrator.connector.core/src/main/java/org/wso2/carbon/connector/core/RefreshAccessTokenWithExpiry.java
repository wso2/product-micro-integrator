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

package org.wso2.carbon.connector.core;

import org.apache.commons.lang.StringUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.registry.Registry;

/**
 * This class can be used by connectors to refresh OAuth 2.0 access tokens by setting the following mandatory
 * properties in message context: uri.var.hostName, uri.var.refreshToken. By default this class constructs the refresh
 * url in the format "{uri.var.hostName}/services/oauth2/token?grant_type=refresh_token&client_id=
 * {uri.var.clientId}&client_secret={uri.var.clientSecret}&refresh_token={uri.var.refreshToken}&format=json".
 * Here client_id and client_secret are optional. If you want to use a different url please set the custom url to
 * uri.var.customRefreshUrl in message context prior to using this class mediator.
 *
 * After refresh call this will set the uri.var.accessToken, and uri.var.apiUrl in the message context to be used by
 * subsequent calls.
 */
public class RefreshAccessTokenWithExpiry extends RefreshAccessToken {

    @Override
    public void connect(MessageContext messageContext) throws ConnectException {
        Registry registry = messageContext.getConfiguration().getRegistry();
        boolean isRefreshNeeded = false;
        String accessTokenRegistryPath = (String) messageContext.getProperty(PROPERTY_PREFIX +
                "accessTokenRegistryPath");
        String lastRefreshedTimeString = registry.getResourceProperties(accessTokenRegistryPath).getProperty("timestamp");
        if (StringUtils.isEmpty(lastRefreshedTimeString)) {
            isRefreshNeeded = true;
        } else {
            String intervalTimeString = (String) messageContext.getProperty("uri.var.intervalTime");
            if (StringUtils.isEmpty(intervalTimeString)) {
                intervalTimeString = "300000"; // sets default interval time as 50 min
            }
            long expiryTimeInterval = Long.parseLong(intervalTimeString);
            long lastRefreshedTime = Long.parseLong(lastRefreshedTimeString);
            if (System.currentTimeMillis() - lastRefreshedTime > expiryTimeInterval) {
                isRefreshNeeded = true;
            }
        }

        if (!isRefreshNeeded) {
            isRefreshNeeded = reuseSavedAccessToken(messageContext, registry, accessTokenRegistryPath);
        }

        if (isRefreshNeeded) {
            handleRefresh(messageContext, registry, accessTokenRegistryPath);
        }
    }
}
