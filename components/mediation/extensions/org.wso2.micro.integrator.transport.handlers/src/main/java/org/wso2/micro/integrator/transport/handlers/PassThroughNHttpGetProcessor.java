/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.transport.handlers;

import org.apache.axiom.util.blob.OverflowBlob;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.protocol.HTTP;
import org.apache.synapse.transport.passthru.HttpGetRequestProcessor;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.ProtocolState;
import org.apache.synapse.transport.passthru.SourceContext;
import org.apache.synapse.transport.passthru.SourceHandler;
import org.apache.synapse.transport.passthru.config.PassThroughConfiguration;
import org.wso2.micro.core.transports.CarbonHttpRequest;
import org.wso2.micro.core.transports.CarbonHttpResponse;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.transport.handlers.utils.RequestProcessorDispatcherUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Get Processor implementation for PassThrough Transport.
 */
public class PassThroughNHttpGetProcessor extends AbstractHttpGetRequestProcessor implements HttpGetRequestProcessor {

    private SourceHandler sourceHandler;

    private static final Log log = LogFactory.getLog(PassThroughNHttpGetProcessor.class);

    private void processWithGetProcessor(HttpRequest request,
                                         HttpResponse response,
                                         String requestUri,
                                         String requestUrl,
                                         String queryString,
                                         String item,
                                         OutputStream outputStream,
                                         NHttpServerConnection conn) throws Exception {
        OverflowBlob temporaryData = new OverflowBlob(256, 4048, "_nhttp", ".dat");
        try {
            CarbonHttpRequest carbonHttpRequest = new CarbonHttpRequest(
                    "GET", requestUri, requestUrl);

            String uri = request.getRequestLine().getUri();
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

            CarbonHttpResponse carbonHttpResponse = new CarbonHttpResponse(
                    temporaryData.getOutputStream());

            (getRequestProcessors.get(item)).process(carbonHttpRequest, carbonHttpResponse, cfgCtx);
            
             // adding headers
            Map<String, String> responseHeaderMap = carbonHttpResponse.getHeaders();
            for (Object key : responseHeaderMap.keySet()) {
                Object value = responseHeaderMap.get(key);
                response.addHeader(key.toString(), value.toString());
            }

            // setting status code
            response.setStatusCode(carbonHttpResponse.getStatusCode());

            // setting error codes
            if (carbonHttpResponse.isError()) {
                if (carbonHttpResponse.getStatusMessage() != null) {
                    response.setStatusLine(response.getProtocolVersion(),
                            carbonHttpResponse.getStatusCode(),
                            carbonHttpResponse.getStatusMessage());
                } else {
                    response.setStatusLine(response.getProtocolVersion(),
                            carbonHttpResponse.getStatusCode());
                }
            }

            if (carbonHttpResponse.isRedirect()) {
                response.addHeader("Location", carbonHttpResponse.getRedirect());
                response.setStatusLine(response.getProtocolVersion(), 302);
            }

            SourceContext.updateState(conn, ProtocolState.WSDL_RESPONSE_DONE);
           
            
            try{
            temporaryData.writeTo(outputStream);
            }catch (Exception e) {
				e.printStackTrace();
			}

            try {
                outputStream.flush();
                outputStream.close();
            } catch (Exception ignored) {}
        } finally {
            temporaryData.release();
            sourceHandler.commitResponseHideExceptions(conn, response);
            
        }
    }

    public void init(ConfigurationContext configurationContext, SourceHandler sourceHandler)
            throws AxisFault {
        
        super.init(configurationContext);
        this.sourceHandler =  sourceHandler;
    }
    


    public void process(HttpRequest request, HttpResponse response,
                        MessageContext messageContext,
                        NHttpServerConnection conn,
                        OutputStream outputStream, boolean b) {

        boolean isRequestHandled = false;


        String uri = request.getRequestLine().getUri();

        String servicePath = getServicePath();
        String serviceName = getServiceName(uri);

        boolean loadBalancer = Boolean.parseBoolean(System.getProperty("wso2.loadbalancer", "false"));
        // Handle browser request to get favicon while requesting for wsdl
        if (uri.equals(FAVICON_ICO)) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            SourceContext.updateState(conn, ProtocolState.WSDL_RESPONSE_DONE);
            try {
                outputStream.flush();
                outputStream.close();
            } catch (Exception ignore) {
            } finally {
                sourceHandler.commitResponseHideExceptions(conn, response);
                isRequestHandled = true ;
            }
        } else if(uri.startsWith(servicePath) &&
                (serviceName == null || serviceName.length() == 0)){
            //check if service listing request is blocked
            if (isServiceListBlocked(uri)) {
                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
            } else {
                generateServicesList(response, conn, outputStream, servicePath);

                messageContext.setProperty("WSDL_GEN_HANDLED", true);
            }
            SourceContext.updateState(conn, ProtocolState.WSDL_RESPONSE_DONE);
            try {
                outputStream.flush();
                outputStream.close();
            } catch (IOException ignore) {
            } finally {
                sourceHandler.commitResponseHideExceptions(conn, response);
            }
            isRequestHandled = true ;
        } else {
            int pos = uri.indexOf('?');
            if (pos != -1) {
                String queryString = uri.substring(pos + 1);
                String requestUri = uri.substring(0, pos);
                String requestUrl = uri;
                if (requestUri.indexOf("://") == -1) {
                    HttpInetConnection inetConn = (HttpInetConnection) conn;

                    String hostName = LOCALHOST;
                    CarbonServerConfigurationService serverConfig = CarbonServerConfigurationService.getInstance();
                    if (serverConfig.getFirstProperty("HostName") != null) {
                        hostName = serverConfig.getFirstProperty("HostName");
                    }

                    requestUrl = "http://" +
                            hostName + ":" + inetConn.getLocalPort() + requestUri;
                }

                String contextPath = cfgCtx.getServiceContextPath();
                int beginIndex = -1;
                if (requestUri.indexOf(contextPath) != -1) {
                    beginIndex = requestUri.indexOf(contextPath) + contextPath.length() + 1;
                }
                
                /**
                * This reverseProxyMode was introduce to avoid LB exposing it's own services when invoked through rest call.
                * For a soap call this works well. But for a rest call this does not work as intended. in LB it has to set system property "reverseProxyMode"
                *
                */
                boolean reverseProxyMode = Boolean.parseBoolean(System.getProperty("reverseProxyMode"));                
                AxisService axisService = null;
                if (!reverseProxyMode) {
                    if (!(beginIndex < 0 || beginIndex > requestUri.length())) {
                	    serviceName = requestUri.substring(beginIndex);
                	    axisService = cfgCtx.getAxisConfiguration().getServiceForActivation(serviceName);
                	}
                }

                if (queryString != null) {
                    for (String item : getRequestProcessors.keySet()) {
                        if (queryString.indexOf(item) == 0 &&
                                (queryString.equals(item) ||
                                        queryString.indexOf("&") == item.length() ||
                                        queryString.indexOf("=") == item.length())) {
                            //check for APIs since no axis2 service found
                            if (axisService == null &&
                                    !RequestProcessorDispatcherUtil.isDispatchToApiGetProcessor(requestUri, cfgCtx)) {
                                continue;
                            }

                            try {
                                processWithGetProcessor(request, response, requestUri,
                                        requestUrl, queryString,
                                        item, outputStream, conn);
                                messageContext.setProperty("WSDL_GEN_HANDLED", true);
                            } catch (Exception e) {
                                handleBrowserException(response, conn, outputStream,
                                        "Error processing request", e);
                            }
                            isRequestHandled = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!isRequestHandled) {
            //processGetAndDelete(request, response, messageContext, conn, outputStream, "GET", b);
        	messageContext.setProperty(PassThroughConstants.REST_GET_DELETE_INVOKE, true);
        }
    }

    /**
     * Generates the services list.
     *
     * @param response    HttpResponse
     * @param conn        NHttpServerConnection
     * @param os          OutputStream
     * @param servicePath service path of the service
     */
    protected void generateServicesList(HttpResponse response,
                                        NHttpServerConnection conn,
                                        OutputStream os, String servicePath) {
        try {
            byte[] bytes = getServicesHTML(
                    servicePath.endsWith("/") ? "" : servicePath + "/").getBytes();
            response.addHeader(CONTENT_TYPE, TEXT_HTML);
            os.write(bytes);

        } catch (IOException e) {
            handleBrowserException(response, conn, os,
                    "Error generating services list", e);
        }
    }

    /**
     * Is the incoming URI is requesting service list and http.block_service_list=true in
     * nhttp.properties
     * @param incomingURI incoming URI
     * @return whether to proceed with incomingURI

     */
    protected boolean isServiceListBlocked(String incomingURI) {
        boolean isBlocked = PassThroughConfiguration.getInstance().isServiceListBlocked();

        return (("/services").equals(incomingURI) || ("/services" + "/").equals(incomingURI)) && isBlocked;
    }
    
    
    
    /**
     * Handles browser exception.
     *
     * @param response HttpResponse
     * @param conn     NHttpServerConnection
     * @param os       OutputStream
     * @param msg      message
     * @param e        Exception
     */
    protected void handleBrowserException(HttpResponse response,
                                          NHttpServerConnection conn, OutputStream os,
                                          String msg, Exception e) {
        if (e == null) {
            log.error(msg);
        } else {
            log.error(msg, e);
        }

        if (!response.containsHeader(HTTP.TRANSFER_ENCODING)) {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.setReasonPhrase(msg);
            //response.addHeader(CONTENT_TYPE, TEXT_HTML);
            //serverHandler.commitResponseHideExceptions(conn, response);
            try {
                os.write(msg.getBytes());
                os.close();
            } catch (IOException ignore) {
            }
        }

        if (conn != null) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }
    }

}
