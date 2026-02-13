/*
 * Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.management.apis;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.axis2.engine.AxisConfiguration;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;
import org.wso2.micro.service.mgt.ServiceAdmin;
import org.wso2.micro.service.mgt.ServiceMetaData;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * ICP resource to fetch WSDL for a given data service or proxy service.
 * Endpoints:
 *  - /icp/artifacts/wsdl?service={dataServiceName}
 *  - /icp/artifacts/wsdl?proxy={proxyServiceName}
 */
public class WsdlResource extends APIResource {

    private static final Log LOG = LogFactory.getLog(WsdlResource.class);

    private static final String PARAM_SERVICE = "service";
    private static final String PARAM_PROXY = "proxy";

    private static volatile ServiceAdmin serviceAdmin = null;

    public WsdlResource(String urlTemplate) {
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
            LOG.debug("Processing WSDL retrieval request");
        }
        buildMessage(messageContext);

        org.apache.axis2.context.MessageContext axisMsgCtx = ((Axis2MessageContext) messageContext)
                .getAxis2MessageContext();

        if (serviceAdmin == null) {
            synchronized (WsdlResource.class) {
                if (serviceAdmin == null) {
                    serviceAdmin = Utils.getServiceAdmin(messageContext);
                }
            }
        }

        if (!messageContext.isDoingGET()) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Only GET method is supported", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        String serviceName = Utils.getQueryParameter(messageContext, PARAM_SERVICE);
        String proxyName = Utils.getQueryParameter(messageContext, PARAM_PROXY);
        if ((serviceName == null || serviceName.trim().isEmpty())
                && (proxyName == null || proxyName.trim().isEmpty())) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Missing required parameter: service or proxy", axisMsgCtx,
                            Constants.BAD_REQUEST));
            return true;
        }

        final boolean isProxyRequest = (proxyName != null && !proxyName.trim().isEmpty());
        final String targetName = isProxyRequest ? proxyName : serviceName;

        // Validate that the specified Axis service exists (data service or proxy are Axis services)
        AxisConfiguration axisConfiguration = axisMsgCtx.getConfigurationContext().getAxisConfiguration();
        org.apache.axis2.description.AxisService axisService = null;
        if (axisConfiguration != null) {
            try {
                axisService = axisConfiguration.getServiceForActivation(targetName);
                if (axisService == null) {
                    axisService = axisConfiguration.getService(targetName);
                }
            } catch (Exception ignored) {
            }
        }
        if (Objects.isNull(axisService)) {
            axisMsgCtx.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
            String kind = isProxyRequest ? "Proxy service" : "Data service";
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError(kind + " not found: " + targetName, axisMsgCtx, Constants.NOT_FOUND));
            return true;
        }

        try {
            LOG.info("Fetching WSDL for " + (isProxyRequest ? "proxy service: " : "data service: ") + targetName);
            OMElement wsdl = null;

                ServiceMetaData serviceMetaData = serviceAdmin.getServiceData(targetName);
                if (serviceMetaData == null || serviceMetaData.getWsdlURLs() == null
                    || serviceMetaData.getWsdlURLs().length == 0) {
                String kind = isProxyRequest ? "proxy service" : "data service";
                Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("WSDL URL not available for " + kind + ": " + targetName,
                        axisMsgCtx, Constants.NOT_FOUND));
                return true;
                }
                String wsdlUrl = serviceMetaData.getWsdlURLs()[0];
            String wsdlText = fetchUrl(wsdlUrl, 5000);
            
            if (wsdlText != null && !wsdlText.trim().isEmpty()) {
                try {
                    wsdl = AXIOMUtil.stringToOM(wsdlText);
                } catch (Exception e) {
                    LOG.error("Failed to parse WSDL XML from URL: " + wsdlUrl, e);
                    Utils.setJsonPayLoad(axisMsgCtx,
                            Utils.createJsonError("Internal server error while fetching WSDL",
                                    axisMsgCtx, Constants.INTERNAL_SERVER_ERROR));
                    return true;
                }
            } else {
                // WSDL fetch failed or returned empty response
                String kind = isProxyRequest ? "proxy service" : "data service";
                Utils.setJsonPayLoad(axisMsgCtx,
                        Utils.createJsonError("Unable to fetch WSDL for " + kind + ": " + targetName + 
                                ". Service may not be running or WSDL URL is not accessible.",
                                axisMsgCtx, Constants.INTERNAL_SERVER_ERROR));
                return true;
            }

            // Set XML payload
            if (axisMsgCtx.getEnvelope() == null) {
                axisMsgCtx.setEnvelope(OMAbstractFactory.getSOAP11Factory().createSOAPEnvelope());
                axisMsgCtx.getEnvelope().addChild(OMAbstractFactory.getSOAP11Factory().createSOAPBody());
            }
            axisMsgCtx.getEnvelope().getBody().addChild(wsdl);

            axisMsgCtx.setProperty(Constants.MESSAGE_TYPE, "application/xml");
            axisMsgCtx.setProperty(Constants.CONTENT_TYPE, "application/xml");
            axisMsgCtx.removeProperty(Constants.NO_ENTITY_BODY);

        } catch (Exception e) {
            LOG.error("Error fetching WSDL for " + (isProxyRequest ? "proxy" : "data service") + ": " + targetName, e);
            Utils.setJsonPayLoad(axisMsgCtx,
                Utils.createJsonError("Internal server error while fetching WSDL",
                            axisMsgCtx, Constants.INTERNAL_SERVER_ERROR));
        }

        return true;
    }

    private String fetchUrl(String urlStr, int timeoutMs) {
        final int maxSize = 10 * 1024 * 1024;
        final int bufferSize = 8 * 1024;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[bufferSize];
                    int bytesRead;
                    int totalRead = 0;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    while ((bytesRead = in.read(buffer)) != -1) {
                        totalRead += bytesRead;
                        if (totalRead > maxSize) {
                            LOG.warn("WSDL response exceeded max size limit (" + maxSize + " bytes) for URL: "
                                    + urlStr + ", read: " + totalRead + " bytes");
                            return null;
                        }
                        out.write(buffer, 0, bytesRead);
                    }
                    return out.toString(StandardCharsets.UTF_8.name());
                }
            } else {
                LOG.warn("WSDL fetch returned non-2xx: " + code + " for URL: " + urlStr);
                return null;
            }
        } catch (Exception e) {
            LOG.warn("Failed to fetch WSDL from URL: " + urlStr + ", " + e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
