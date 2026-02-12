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
import org.apache.synapse.SynapseLog;
import org.apache.synapse.registry.Registry;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.carbon.connector.core.util.ConnectorUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * This class can be used by connectors to generate OAuth 2.0 access tokens by setting the following mandatory
 * property in message context: uri.var.hostName. By default this class constructs the token generation
 * url in the format "{uri.var.tokenEndpointUrl}?grant_type=client_credentials&client_id=
 * {uri.var.clientId}&client_secret={uri.var.clientSecret}&format=json".
 * Here client_id and client_secret are mandatory. If you want to use a different url please set the custom url to
 * uri.var.authorizationUrl in message context prior to using this class mediator.
 *
 * After token generation call this will set the uri.var.accessToken in the message context to be used by
 * subsequent calls.
 *
 * @deprecated This class is deprecated. Use the new connector core module available at
 * <a href="https://github.com/wso2-extensions/mi-connector-core">https://github.com/wso2-extensions/mi-connector-core</a>
 */
@Deprecated
public class GenerateAccessToken extends AbstractConnector {
    protected static final String PROPERTY_PREFIX = "uri.var.";
    protected static final String ACCEPT_ENCODING = "Accept-Encoding";
    protected static final String CACHE_CONTROL = "Cache-Control";
    protected static final String PRAGMA = "Pragma";

    @Override
    public void connect(MessageContext messageContext) throws ConnectException {
        Registry registry = messageContext.getConfiguration().getRegistry();
        String accessTokenRegistryPath = (String) messageContext.getProperty(PROPERTY_PREFIX +
                "accessTokenRegistryPath");
        if (StringUtils.isEmpty(accessTokenRegistryPath)) {
            throw new ConnectException("Access token registry path not provided for access token storage and reuse.");
        }
        handleTokenGeneration(messageContext, registry, accessTokenRegistryPath);
    }

    protected void handleTokenGeneration(MessageContext messageContext, Registry registry,
                                         String accessTokenRegistryPath)
            throws ConnectException {
        SynapseLog synLog = getLog(messageContext);
        Set propertyKeySet = messageContext.getPropertyKeySet();
        propertyKeySet.remove(ACCEPT_ENCODING);

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : Generate Access Token mediator.");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + messageContext.getEnvelope());
            }
        }

        try {
            String jsonResponse = ConnectorUtils.sendPost(getPostData(messageContext), StandardCharsets.UTF_8,
                    messageContext);
            extractAndSetPropertyAndRegistryResource(messageContext, jsonResponse, registry, accessTokenRegistryPath);
        } catch (IOException e) {
            synLog.error(e);
            throw new ConnectException(e, "Error while executing POST request to generate the access token");
        } catch (JSONException e) {
            synLog.error(e);
            throw new ConnectException(e, "Error while processing the response message");
        } finally {
            propertyKeySet.remove(CACHE_CONTROL);
            propertyKeySet.remove(PRAGMA);
        }
    }

    protected String getPostData(MessageContext messageContext) {
        String customTokenGenerationUrl = (String) messageContext.getProperty(PROPERTY_PREFIX
                + "customTokenGenerationUrl");

        if (StringUtils.isNotEmpty(customTokenGenerationUrl)) {
            return customTokenGenerationUrl;
        } else {
            StringBuilder urlStringBuilder = new StringBuilder();
            urlStringBuilder.append("grant_type=client_credentials");
            String clientId = (String) messageContext.getProperty(PROPERTY_PREFIX + "clientId");
            if (StringUtils.isNotEmpty(clientId)) {
                urlStringBuilder.append("&client_id=").append(clientId);
            }
            String clientSecret = (String) messageContext.getProperty(PROPERTY_PREFIX + "clientSecret");
            if (StringUtils.isNotEmpty(clientSecret)) {
                urlStringBuilder.append("&client_secret=").append(clientSecret);
            }
            String scope = (String) messageContext.getProperty(PROPERTY_PREFIX + "scope");
            if (StringUtils.isNotEmpty(scope)) {
                urlStringBuilder.append("&scope=").append(scope);
            }
            urlStringBuilder.append("&format=json");
            return urlStringBuilder.toString();
        }
    }

    protected void extractAndSetPropertyAndRegistryResource(MessageContext messageContext,
                                                            String jsonResponse,
                                                            Registry registry, String accessTokenRegistryPath)
            throws IOException, ConnectException, JSONException {
        JSONObject jsonObject = new JSONObject(jsonResponse);

        String accessToken = jsonObject.getString("access_token");
        messageContext.setProperty(PROPERTY_PREFIX + "accessToken", accessToken);

        String systemTime = Long.toString(System.currentTimeMillis());

        if(StringUtils.isNotEmpty(accessToken)) {
            registry.newNonEmptyResource(accessTokenRegistryPath, false, "text/plain", systemTime, "timestamp");
            registry.updateResource(accessTokenRegistryPath, accessToken);
        }
    }
}
