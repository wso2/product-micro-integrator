/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.icp.apis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.Entry;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;
import org.wso2.micro.integrator.management.apis.Constants;
import org.wso2.micro.integrator.management.apis.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * ICP resource to fetch the value of a given Local Entry.
 * Endpoint: /icp/artifacts/local-entry?name=<key>
 */
public class ICPGetLocalEntryValueResource extends APIResource {

    private static final Log LOG = LogFactory.getLog(ICPGetLocalEntryValueResource.class);

    public ICPGetLocalEntryValueResource(String urlTemplate) {
        super(urlTemplate);
    }

    @Override
    public Set<String> getMethods() {
        Set<String> methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing ICP request to fetch local entry value");
        }
        buildMessage(messageContext);

        org.apache.axis2.context.MessageContext axisMsgCtx =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        if (!messageContext.isDoingGET()) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Only GET method is supported", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        String name = Utils.getQueryParameter(messageContext, "name");
        if (StringUtils.isBlank(name)) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Missing required parameter: name", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        SynapseConfiguration synapseConfig = messageContext.getConfiguration();
        if (synapseConfig == null) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Synapse configuration is not available", axisMsgCtx,
                            Constants.INTERNAL_SERVER_ERROR));
            return true;
        }

        Entry entry = synapseConfig.getDefinedEntries().get(name);
        if (entry == null) {
            axisMsgCtx.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Local entry not found: " + name, axisMsgCtx, Constants.NOT_FOUND));
            return true;
        }

        try {
            switch (entry.getType()) {
                case Entry.INLINE_TEXT:
                case Entry.INLINE_XML: {
                    JSONObject resp = new JSONObject();
                    resp.put(Constants.NAME, name);
                    resp.put("value", Objects.toString(entry.getValue(), ""));
                    Utils.setJsonPayLoad(axisMsgCtx, resp);
                    return true;
                }
                case Entry.URL_SRC: {
                    String content = fetchFromUrl(Objects.toString(entry.getValue(), ""));
                    JSONObject resp = new JSONObject();
                    resp.put(Constants.NAME, name);
                    resp.put("value", content);
                    Utils.setJsonPayLoad(axisMsgCtx, resp);
                    return true;
                }
                case Entry.REMOTE_ENTRY: {
                    JSONObject resp = new JSONObject();
                    resp.put(Constants.NAME, name);
                    resp.put("registryKey", Objects.toString(entry.getValue(), ""));
                    Utils.setJsonPayLoad(axisMsgCtx, resp);
                    return true;
                }
                default: {
                    JSONObject resp = new JSONObject();
                    resp.put(Constants.NAME, name);
                    resp.put("value", Objects.toString(entry.getValue(), ""));
                    Utils.setJsonPayLoad(axisMsgCtx, resp);
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to read local entry value for: " + name + ", error: " + e.getMessage());
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Failed to read local entry value", axisMsgCtx,
                            Constants.INTERNAL_SERVER_ERROR));
            return true;
        }
    }

    private String fetchFromUrl(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String errorMessage = "HTTP " + responseCode + " when fetching URL: " + urlStr;
                String errorBody = null;
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    errorBody = readStream(errorStream);
                }
                if (StringUtils.isNotBlank(errorBody)) {
                    throw new IOException(errorMessage + ". Response: " + errorBody);
                }
                throw new IOException(errorMessage + ". No error body returned.");
            }
            return readStream(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private String readStream(InputStream inputStream) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
