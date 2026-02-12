/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.icp.apis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.message.processor.MessageProcessor;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;
import org.wso2.micro.integrator.management.apis.Constants;
import org.wso2.micro.integrator.management.apis.Utils;
import org.wso2.micro.integrator.ndatasource.core.CarbonDataSource;
import org.wso2.micro.integrator.ndatasource.core.DataSourceManager;
import org.wso2.micro.integrator.ndatasource.core.DataSourceMetaInfo;
import org.wso2.micro.integrator.ndatasource.core.DataSourceRepository;
import org.wso2.micro.integrator.ndatasource.core.utils.DataSourceUtils;
import org.wso2.micro.integrator.ndatasource.rdbms.RDBMSConfiguration;
import org.wso2.micro.integrator.ndatasource.rdbms.RDBMSDataSourceReader;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ICP resource to fetch key-value parameters of supported artifacts.
 * Supported types: inbound-endpoint, message-processor, data-source
 * Endpoint: /icp/artifacts/parameters?type=<type>&name=<artifactName>
 * Response: { "type": "<type>", "name": "<artifactName>", "parameters": [ {"name": k, "value": v}, ... ] }
 */
public class ICPGetParamsResource extends APIResource {

    private static final Log LOG = LogFactory.getLog(ICPGetParamsResource.class);

    public ICPGetParamsResource(String urlTemplate) {
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
        buildMessage(messageContext);

        org.apache.axis2.context.MessageContext axisMsgCtx =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        if (!messageContext.isDoingGET()) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Only GET method is supported", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        String type = Utils.getQueryParameter(messageContext, Constants.TYPE);
        String name = Utils.getQueryParameter(messageContext, Constants.NAME);
        if (StringUtils.isBlank(name)) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Missing required parameter: name", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }
        if (StringUtils.isBlank(type)) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Missing required parameter: type", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        SynapseConfiguration configuration = messageContext.getConfiguration();
        if (configuration == null) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Synapse configuration is not available", axisMsgCtx,
                            Constants.INTERNAL_SERVER_ERROR));
            return true;
        }

        JSONObject resp = new JSONObject();
        resp.put(Constants.TYPE, type);
        resp.put(Constants.NAME, name);
        JSONArray paramsArray = new JSONArray();

        switch (type.toLowerCase()) {
            case "inbound-endpoint": {
                InboundEndpoint inbound = configuration.getInboundEndpoint(name);
                if (inbound == null) {
                    axisMsgCtx.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
                    Utils.setJsonPayLoad(axisMsgCtx,
                            Utils.createJsonError("Inbound endpoint not found: " + name, axisMsgCtx,
                                    Constants.NOT_FOUND));
                    return true;
                }
                try {
                    Map<String, String> params = inbound.getParametersMap();
                    if (params != null) {
                        for (Map.Entry<String, String> e : params.entrySet()) {
                            JSONObject p = new JSONObject();
                            p.put(Constants.NAME, e.getKey());
                            p.put("value", Objects.toString(e.getValue(), ""));
                            paramsArray.put(p);
                        }
                    }
                } catch (Exception ex) {
                    LOG.warn("Error reading parameters for inbound endpoint: " + name, ex);
                }
                break;
            }
            case "message-processor": {
                MessageProcessor mp = configuration.getMessageProcessors().get(name);
                if (mp == null) {
                    axisMsgCtx.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
                    Utils.setJsonPayLoad(axisMsgCtx,
                            Utils.createJsonError("Message processor not found: " + name, axisMsgCtx,
                                    Constants.NOT_FOUND));
                    return true;
                }
                try {
                    Map<String, Object> params = mp.getParameters();
                    if (params != null) {
                        for (Map.Entry<String, Object> e : params.entrySet()) {
                            JSONObject p = new JSONObject();
                            p.put(Constants.NAME, e.getKey());
                            p.put("value", Objects.toString(e.getValue(), ""));
                            paramsArray.put(p);
                        }
                    }
                } catch (Exception ex) {
                    LOG.warn("Error reading parameters for message processor: " + name, ex);
                }
                break;
            }
            case "data-source": {
                try {
                    DataSourceManager dsManager = DataSourceManager.getInstance();
                    DataSourceRepository repo = dsManager.getDataSourceRepository();
                    CarbonDataSource cds = repo.getDataSource(name);
                    if (cds == null) {
                        axisMsgCtx.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
                        Utils.setJsonPayLoad(axisMsgCtx,
                                Utils.createJsonError("Data source not found: " + name, axisMsgCtx,
                                        Constants.NOT_FOUND));
                        return true;
                    }

                    Object dsObj = cds.getDSObject();
                    if (dsObj instanceof DataSource) {
                        PoolConfiguration pool = ((DataSource) dsObj).getPoolProperties();
                        addParam(paramsArray, "driverClass", pool.getDriverClassName());
                        addParam(paramsArray, "url", DataSourceUtils.maskURLPassword(pool.getUrl()));
                        addParam(paramsArray, "userName", pool.getUsername());
                        addParam(paramsArray, "isDefaultAutoCommit", pool.isDefaultAutoCommit());
                        addParam(paramsArray, "isDefaultReadOnly", pool.isDefaultReadOnly());
                        addParam(paramsArray, "removeAbandoned", pool.isRemoveAbandoned());
                        addParam(paramsArray, "validationQuery", pool.getValidationQuery());
                        addParam(paramsArray, "validationQueryTimeout", pool.getValidationQueryTimeout());
                        addParam(paramsArray, "maxActive", pool.getMaxActive());
                        addParam(paramsArray, "maxIdle", pool.getMaxIdle());
                        addParam(paramsArray, "maxWait", pool.getMaxWait());
                        addParam(paramsArray, "maxAge", pool.getMaxAge());
                    } else {
                        // External datasource definition
                        try {
                            DataSourceMetaInfo meta = cds.getDSMInfo();
                            String dsXml = DataSourceUtils.elementToStringWithMaskedPasswords(
                                    (org.w3c.dom.Element) meta.getDefinition().getDsXMLConfiguration());
                            RDBMSConfiguration cfg = RDBMSDataSourceReader.loadConfig(dsXml);
                            addParam(paramsArray, "dataSourceClassName", cfg.getDataSourceClassName());
                            for (RDBMSConfiguration.DataSourceProperty prop : cfg.getDataSourceProps()) {
                                if (!"password".equals(prop.getName())) {
                                    addParam(paramsArray, prop.getName(), prop.getValue());
                                }
                            }
                        } catch (Exception ex) {
                            LOG.warn("Error reading external datasource parameters: " + name, ex);
                        }
                    }
                } catch (Exception ex) {
                    Utils.setJsonPayLoad(axisMsgCtx,
                            Utils.createJsonError("Error reading data source parameters", axisMsgCtx,
                                    Constants.INTERNAL_SERVER_ERROR));
                    return true;
                }
                break;
            }
            default: {
                Utils.setJsonPayLoad(axisMsgCtx,
                        Utils.createJsonError("Unsupported type: " + type, axisMsgCtx, Constants.BAD_REQUEST));
                return true;
            }
        }

        resp.put("parameters", paramsArray);
        Utils.setJsonPayLoad(axisMsgCtx, resp);
        return true;
    }

    private void addParam(JSONArray arr, String name, Object value) {
        JSONObject p = new JSONObject();
        p.put(Constants.NAME, name);
        p.put("value", Objects.toString(value, ""));
        arr.put(p);
    }
}
