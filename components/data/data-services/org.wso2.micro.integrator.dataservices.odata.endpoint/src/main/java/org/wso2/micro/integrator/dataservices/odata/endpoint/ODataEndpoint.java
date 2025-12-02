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

package org.wso2.micro.integrator.dataservices.odata.endpoint;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.wso2.micro.integrator.core.Constants;
import org.wso2.micro.integrator.dataservices.core.odata.ODataServiceFault;
import org.wso2.micro.integrator.dataservices.core.odata.ODataServiceHandler;
import org.wso2.micro.integrator.dataservices.core.odata.ODataServiceRegistry;
import org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingCollector;

import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DATA_QUERY_EXECUTION_INDEX;

public class ODataEndpoint {
    private static final Log log = LogFactory.getLog(ODataEndpoint.class);
    private static final String SUPER_TENANT_DOMAIN_NAME = "carbon.super";
    private static final String ODATA_SERVICE = "odata/";
    private static final String URL_SEPARATOR = "/";
    private static final Character URL_SEPARATOR_CHAR = '/';

    /**
     * This method will find the particular OdataHandler from the ODataServiceRegistry and process the request.
     *
     * @param request  HTTPServlet Request
     * @param response HTTPServlet Response
     * @see ODataServiceRegistry
     */
    public static void process(ODataServletRequest request, ODataServletResponse response) {
        String tenantDomain = SUPER_TENANT_DOMAIN_NAME;
        if (log.isDebugEnabled()) {
            log.debug("OData Request received to DSS: Request body - " + request.toString() + ", ThreadID - " +
                      Thread.currentThread().getId());
        }
        try {
            String[] serviceParams = getServiceDetails(request.getRequestURI());
            /*
                Security Comment :
                Here we get the serviceRootPath from the request uri, and then we check whether there is a particular service handler for the service request.
                ODataServiceRegistry stores the all the service handlers. There is a key to a handler, which is the combination of service name and configID,
                These two parameters (Service Name, Config ID) are coming from the requests, therefore we call getServiceDetails to retrieve these information from the request.
                and then we check whether there is a handler for this requests, if there is a handler we process the request. otherwise we don't process the request.
             */
            String serviceRootPath = URL_SEPARATOR + serviceParams[0] + URL_SEPARATOR + serviceParams[1];
            String serviceKey = serviceParams[0] + serviceParams[1];
            ODataServiceRegistry registry = ODataServiceRegistry.getInstance();
            ODataServiceHandler handler = registry.getServiceHandler(serviceKey, tenantDomain);
            if (handler != null) {
                if (log.isDebugEnabled()) {
                    log.debug(serviceRootPath + " Service invoked.");
                }
                process(request, response, serviceRootPath, handler);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Couldn't find the ODataService Handler for " + serviceRootPath + " Service.");
                }
                response.setStatus(Constants.NOT_IMPLEMENTED);
            }
        } catch (ODataServiceFault e) {
            response.setStatus(Constants.BAD_REQUEST);
            if (log.isDebugEnabled()) {
                log.debug("Bad Request invoked. :" + e.getMessage());
            }
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("OData Response send from DSS: Response body - " + response.toString() + ", ThreadID - " +
                          Thread.currentThread().getId());
            }
        }
    }

    /**
     * This method will process the servlet request and builds the response.
     *
     * @param request             OData Servlet request.
     * @param response            OData Servlet response.
     * @param serviceRootPath     Path of the request URL.
     * @param oDataServiceHandler Service handler to process the Servlet request and the response.
     */
    private static void process(ODataServletRequest request, ODataServletResponse response, String serviceRootPath,
                                ODataServiceHandler oDataServiceHandler) {
        if (oDataServiceHandler == null) {
            if (log.isDebugEnabled()) {
                log.debug("Couldn't find the ODataService Handler for " + serviceRootPath + " Service.");
            }
            response.setStatus(Constants.NOT_IMPLEMENTED);
        } else {
            Thread streamThread = new Thread(() -> {
                try {
                    DataServicesTracingCollector.reportOdataQueryExecutionEvent(request.getAxis2MessageContext());
                    oDataServiceHandler.process(request, response, serviceRootPath);
                    DataServicesTracingCollector.closeOdataQueryExecutionEvent(request.getAxis2MessageContext());
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to process the servlet request. " + e);
                    }
                    DataServicesTracingCollector.closeFlowForcefully(request.getAxis2MessageContext(),
                            DATA_QUERY_EXECUTION_INDEX, e);
                    throw new SynapseException("Error occurred while processing the request " + request + ".", e);
                } finally {
                    response.flushOutputStream();
                }
            });
            streamThread.start();
        }
    }

    /**
     * This method retrieve the service name and config id from the request uri.
     *
     * @param uri          Request uri
     * @return String Array String[0] ServiceName, String[1] ConfigID
     */
    public static String[] getServiceDetails(String uri) throws ODataServiceFault {
        String odataServiceName;
        String odataServiceUri;
        String configID;
        int index = uri.indexOf(ODATA_SERVICE);
        if (-1 != index) {
            int serviceStart = index + ODATA_SERVICE.length();
            if (uri.length() > serviceStart + 1) {
                odataServiceUri = uri.substring(serviceStart);
                if (-1 != odataServiceUri.indexOf(URL_SEPARATOR_CHAR)) {
                    String[] params = odataServiceUri.split(URL_SEPARATOR);
                    odataServiceName = params[0];
                    configID = params[1];
                    return new String[] { odataServiceName, configID };
                }
            }
        }
        throw new ODataServiceFault("Bad OData request.");
    }
}
