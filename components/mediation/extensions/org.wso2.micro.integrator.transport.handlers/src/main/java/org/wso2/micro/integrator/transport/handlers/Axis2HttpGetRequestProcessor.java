/*
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.micro.integrator.transport.handlers;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.axiom.util.blob.OverflowBlob;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpStatus;
import org.apache.synapse.transport.netty.BridgeConstants;
import org.apache.synapse.transport.netty.config.HttpGetRequestProcessor;
import org.apache.synapse.transport.netty.config.NettyConfiguration;
import org.apache.synapse.transport.netty.util.HttpUtils;
import org.apache.synapse.transport.netty.util.RequestResponseUtils;
import org.wso2.micro.core.transports.CarbonHttpRequest;
import org.wso2.micro.core.transports.CarbonHttpResponse;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.transport.handlers.utils.RequestProcessorDispatcherUtil;
import org.wso2.transport.http.netty.contract.exceptions.ServerConnectorException;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.StringTokenizer;

public class Axis2HttpGetRequestProcessor extends AbstractHttpGetRequestProcessor implements HttpGetRequestProcessor {

    private static final Log LOG = LogFactory.getLog(Axis2HttpGetRequestProcessor.class);

    @Override
    public void process(HttpCarbonMessage inboundCarbonMsg, MessageContext messageContext, boolean b) {

        boolean isRequestHandled = false;

        String uri = (String) inboundCarbonMsg.getProperty(BridgeConstants.TO);
        String serviceName = getServiceName(uri);
        String servicePath = getServicePath();

        HttpCarbonMessage outboundCarbonMsg = new HttpCarbonMessage(
                new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));

        // Handle browser request to get favicon while requesting for wsdl
        if (uri.equals(FAVICON_ICO)) {
            outboundCarbonMsg.setHttpStatusCode(HttpStatus.SC_NOT_FOUND);
            submitResponseAndHideExceptions(inboundCarbonMsg, outboundCarbonMsg, new byte[0]);
            isRequestHandled = true;

        } else if (uri.startsWith(servicePath) && (serviceName == null || serviceName.isEmpty())) {
            //check if service listing request is blocked
            if (isServiceListBlocked(uri)) {
                outboundCarbonMsg.setHttpStatusCode(HttpStatus.SC_FORBIDDEN);
                submitResponseAndHideExceptions(inboundCarbonMsg, outboundCarbonMsg, new byte[0]);
            } else {
                byte[] bytes = getServicesHTML(
                        servicePath.endsWith("/") ? "" : servicePath + "/").getBytes();
                outboundCarbonMsg.setHeader(CONTENT_TYPE, TEXT_HTML);
                submitResponseAndHideExceptions(inboundCarbonMsg, outboundCarbonMsg, bytes);
            }
            isRequestHandled = true;

        } else {
            int pos = uri.indexOf('?');
            if (pos != -1) {
                String queryString = uri.substring(pos + 1);
                String requestUri = uri.substring(0, pos);
                String requestUrl = uri;
                if (!requestUri.contains("://")) {

                    InetSocketAddress localAddress =
                            (InetSocketAddress) inboundCarbonMsg.getProperty(
                                    org.wso2.transport.http.netty.contract.Constants.LOCAL_ADDRESS);

                    String hostName = LOCALHOST;
                    CarbonServerConfigurationService serverConfig = CarbonServerConfigurationService.getInstance();
                    if (serverConfig.getFirstProperty(HOST_NAME) != null) {
                        hostName = serverConfig.getFirstProperty(HOST_NAME);
                    }

                    requestUrl = "http://" + hostName + ":" + localAddress.getPort() + requestUri;
                }

                String contextPath = cfgCtx.getServiceContextPath();
                int beginIndex = -1;
                if (requestUri.contains(contextPath)) {
                    beginIndex = requestUri.indexOf(contextPath) + contextPath.length() + 1;
                }

                // This reverseProxyMode was introduce to avoid LB exposing it's own services when invoked through
                // rest call. For a soap call this works well. But for a rest call this does not work as intended.
                // in LB it has to set system property "reverseProxyMode"
                boolean reverseProxyMode = NettyConfiguration.getInstance().isReverseProxyMode();
                AxisService axisService = null;
                if (!reverseProxyMode) {
                    if (!(beginIndex < 0 || beginIndex > requestUri.length())) {
                        serviceName = requestUri.substring(beginIndex);
                        axisService = cfgCtx.getAxisConfiguration().getServiceForActivation(serviceName);
                    }
                }

                for (String item : getRequestProcessors.keySet()) {
                    if (queryString.indexOf(item) == 0 &&
                            (queryString.equals(item) ||
                                    queryString.indexOf("&") == item.length() ||
                                    queryString.indexOf("=") == item.length())) {
                        //check for APIs since no axis2 service found
                        if (axisService == null
                                && !RequestProcessorDispatcherUtil.isDispatchToApiGetProcessor(requestUri, cfgCtx)) {
                            continue;
                        }

                        try {
                            processWithGetProcessor(inboundCarbonMsg, outboundCarbonMsg, requestUri, requestUrl,
                                    queryString, item);
                        } catch (Exception e) {
                            LOG.error("Error processing request", e);
                            String body = "<html><body><h1>" + "Failed to process the request"
                                    + "</h1><p>Error processing request!</p></body></html>";

                            outboundCarbonMsg.setHeader(CONTENT_TYPE, TEXT_HTML);
                            submitResponseAndHideExceptions(inboundCarbonMsg, outboundCarbonMsg, body.getBytes());
                        }
                        isRequestHandled = true;
                        break;
                    }
                }
            }
        }

        if (isRequestHandled) {
            messageContext.setProperty(BridgeConstants.WSDL_REQUEST_HANDLED, true);
        }
    }

    private void processWithGetProcessor(HttpCarbonMessage inboundCarbonMsg,
                                         HttpCarbonMessage outboundCarbonMsg,
                                         String requestUri,
                                         String requestUrl,
                                         String queryString,
                                         String item) throws Exception {

        OverflowBlob temporaryData = new OverflowBlob(256, 4048, "_netty_http", ".dat");
        try {
            CarbonHttpRequest carbonHttpRequest = new CarbonHttpRequest("GET", requestUri, requestUrl);

            String uri = (String) inboundCarbonMsg.getProperty("TO");
            // setting the parameters for nhttp transport
            int pos = uri.indexOf("?");
            if (pos != -1) {
                StringTokenizer st = new StringTokenizer(uri.substring(pos + 1), "&");
                while (st.hasMoreTokens()) {
                    String param = st.nextToken();
                    pos = param.indexOf("=");
                    if (pos != -1) {
                        carbonHttpRequest.setParameter(
                                param.substring(0, pos), param.substring(pos + 1));
                    } else {
                        carbonHttpRequest.setParameter(param, null);
                    }
                }
            }

            carbonHttpRequest.setContextPath(cfgCtx.getServiceContextPath());
            carbonHttpRequest.setQueryString(queryString);
            CarbonHttpResponse carbonHttpResponse = new CarbonHttpResponse(temporaryData.getOutputStream());

            (getRequestProcessors.get(item)).process(carbonHttpRequest, carbonHttpResponse, cfgCtx);

            // adding headers
            Map<String, String> responseHeaderMap = carbonHttpResponse.getHeaders();
            for (Object key : responseHeaderMap.keySet()) {
                Object value = responseHeaderMap.get(key);
                outboundCarbonMsg.setHeader(key.toString(), value.toString());
            }

            // setting status code
            outboundCarbonMsg.setHttpStatusCode(carbonHttpResponse.getStatusCode());

            // setting error codes
            if (carbonHttpResponse.isError()) {
                if (carbonHttpResponse.getStatusMessage() != null) {
                    outboundCarbonMsg.setProperty(org.wso2.transport.http.netty.contract.Constants.HTTP_REASON_PHRASE,
                            carbonHttpResponse.getStatusMessage());
                }
            }

            if (carbonHttpResponse.isRedirect()) {
                outboundCarbonMsg.setHeader("Location", carbonHttpResponse.getRedirect());
                outboundCarbonMsg.setHttpStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
            }

            try {
                inboundCarbonMsg.respond(outboundCarbonMsg);
            } catch (ServerConnectorException e) {
                LOG.error("Error while responding to the client.", e);
            }

            try (OutputStream outputStream =
                         HttpUtils.getHttpMessageDataStreamer(outboundCarbonMsg).getOutputStream()) {
                temporaryData.writeTo(outputStream);
            } catch (Exception e) {
                LOG.error("Error occurred while writing the response body to the client", e);
            }
        } finally {
            temporaryData.release();
        }
    }

    protected boolean isServiceListBlocked(String incomingURI) {

        String isBlocked = NettyConfiguration.getInstance().isServiceListBlocked();

        return (("/services").equals(incomingURI) || ("/services" + "/").equals(incomingURI)) &&
                Boolean.parseBoolean(isBlocked);
    }

    private void submitResponseAndHideExceptions(HttpCarbonMessage inboundCarbonMsg,
                                                 HttpCarbonMessage outboundCarbonMsg, byte[] bytes) {

        try {
            inboundCarbonMsg.respond(outboundCarbonMsg);
        } catch (ServerConnectorException e) {
            LOG.error("Error while submitting the response to the client.", e);
        }

        try (OutputStream outputStream = HttpUtils.getHttpMessageDataStreamer(outboundCarbonMsg).getOutputStream()) {
            outputStream.write(bytes);
        } catch (IOException e) {
            LOG.error("Error occurred while writing the response body to the client", e);
        }
    }
}
