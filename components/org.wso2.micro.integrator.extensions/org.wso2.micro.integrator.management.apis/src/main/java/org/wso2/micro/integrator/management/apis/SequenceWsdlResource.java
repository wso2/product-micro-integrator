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
import org.apache.synapse.mediators.base.SequenceMediator;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * ICP resource to generate a minimal WSDL for a given sequence.
 * Endpoint: /icp/artifacts/wsdl?sequence={sequenceName}
 */
public class SequenceWsdlResource extends APIResource {

    private static final Log LOG = LogFactory.getLog(SequenceWsdlResource.class);

    private static final String PARAM_SEQUENCE = "sequence";

    public SequenceWsdlResource(String urlTemplate) {
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

        String sequenceName = Utils.getQueryParameter(messageContext, PARAM_SEQUENCE);
        if (Objects.isNull(sequenceName) || sequenceName.trim().isEmpty()) {
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Missing required parameter: sequence", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        SequenceMediator sequence = messageContext.getConfiguration().getDefinedSequences().get(sequenceName);
        if (Objects.isNull(sequence)) {
            axisMsgCtx.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Sequence not found: " + sequenceName, axisMsgCtx, Constants.NOT_FOUND));
            return true;
        }

        try {
            OMElement wsdl;

            // Try to fetch actual WSDL from service URL derived via internal config
            String wsdlUrl = buildServiceWsdlUrl(axisMsgCtx, sequenceName);
            String wsdlText = fetchUrl(wsdlUrl, 5000);
            if (wsdlText != null && !wsdlText.isEmpty()) {
                wsdl = AXIOMUtil.stringToOM(wsdlText);
            } else {
                wsdl = AXIOMUtil.stringToOM("");
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
            LOG.error("Error generating WSDL for sequence: " + sequenceName, e);
            Utils.setJsonPayLoad(axisMsgCtx,
                    Utils.createJsonError("Error generating WSDL: " + e.getMessage(),
                            axisMsgCtx, Constants.INTERNAL_SERVER_ERROR));
        }

        return true;
    }

    private String buildServiceWsdlUrl(org.apache.axis2.context.MessageContext axisMsgCtx, String sequenceName) {
        String host = deriveHost(axisMsgCtx);
        int port = deriveHttpPort();
        String protocol = "http";
        return protocol + "://" + host + ":" + port + "/services/" + sequenceName + "?wsdl";
    }

    private String deriveHost(org.apache.axis2.context.MessageContext axisMsgCtx) {
        String host = null;
        if (axisMsgCtx != null && axisMsgCtx.getConfigurationContext() != null &&
                axisMsgCtx.getConfigurationContext().getAxisConfiguration() != null &&
                axisMsgCtx.getConfigurationContext().getAxisConfiguration().getParameter("hostname") != null) {
            Object val = axisMsgCtx.getConfigurationContext().getAxisConfiguration().getParameter("hostname").getValue();
            if (val != null) {
                host = val.toString();
            }
        }
        if (host == null) {
            String localIp = System.getProperty("carbon.local.ip");
            if (localIp != null && !localIp.isEmpty()) {
                host = localIp;
            }
        }
        if (host == null) {
            host = "localhost";
        }
        return host;
    }

    private int deriveHttpPort() {
        String portStr = CarbonServerConfigurationService.getInstance().getFirstProperty(CarbonServerConfigurationService.HTTP_PORT);
        int port = 8290;
        try {
            if (portStr != null) {
                port = Integer.parseInt(portStr.trim());
            }
        } catch (Exception ignored) {
        }
        return port;
    }

    private String fetchUrl(String urlStr, int timeoutMs) {
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
                    byte[] buf = in.readAllBytes();
                    return new String(buf, StandardCharsets.UTF_8);
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
