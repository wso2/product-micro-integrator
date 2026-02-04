/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.transport.handlers.requestprocessors.swagger.format;

import org.apache.axis2.AxisFault;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.api.API;
import org.apache.synapse.rest.RESTConstants;
import org.wso2.carbon.mediation.commons.rest.api.swagger.ServerConfig;
import org.wso2.carbon.mediation.commons.rest.api.swagger.SwaggerConstants;
import org.wso2.micro.core.util.NetworkUtils;
import org.wso2.micro.integrator.transport.handlers.DataHolder;

import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;

public class MIServerConfig implements ServerConfig {

    private static final Log log = LogFactory.getLog(MIServerConfig.class);
    private AxisConfiguration axisConfiguration;

    public MIServerConfig() {
        this.axisConfiguration = DataHolder.getInstance().getAxisConfigurationContext().getAxisConfiguration();
    }

    /**
     * Function to retrieve host for the swagger definition
     * Host is extracted from following sources
     *  1. Host configured in API definition
     *  2. WSDLEPRPrefix configured in axis2.xml under http or https transport listeners
     *  3. "hostname" parameter in axis2.xml (combined with http port configured for transport listener)
     *  4. Server (machine) host (combined with http port configured for transport listener)
     *
     * @param api api that required to retrieve host
     * @return return host
     */
    @Override
    public String getHost(API api) throws AxisFault {

        if (api.getHost() != null) {
            return api.getHost();
        } else {
            return getHostNameFromTransport(api.getProtocol() == RESTConstants.PROTOCOL_HTTP_ONLY ? "http" : "https");
        }
    }

    @Override
    public String getHost(String trasport) throws AxisFault {
        return getHostNameFromTransport(trasport);
    }

    private String getHostNameFromTransport(String transport) throws AxisFault {

        TransportInDescription transportIn = axisConfiguration.getTransportIn(transport);

        if (transportIn != null) {
            // Give priority to WSDLEPRPrefix
            if (transportIn.getParameter(SwaggerConstants.WSDL_EPR_PREFIX) != null) {
                String wsdlPrefixParam = (String) transportIn.getParameter(SwaggerConstants.WSDL_EPR_PREFIX).getValue();
                if (!wsdlPrefixParam.isEmpty()) {
                    //WSDLEPRPrefix available
                    try {
                        URI hostUri = new URI(wsdlPrefixParam);
                        //Resolve port
                        try {
                            String protocol = transportIn.getName();

                            if (("https".equals(protocol) && hostUri.getPort() == 443) ||
                                    ("http".equals(protocol) && hostUri.getPort() == 80)) {
                                return hostUri.getHost();
                            }
                        } catch (NumberFormatException e) {
                            throw new AxisFault("Error occurred while parsing the port", e);
                        }

                        return hostUri.getHost() + ":" + hostUri.getPort();
                    } catch (URISyntaxException e) {
                        log.error("WSDLEPRPrefix is not a valid URI", e);
                    }
                } else {
                    log.error("\"WSDLEPRPrefix\" is empty. Please provide relevant URI or comment out parameter");
                }
            }

            String portStr = (String) transportIn.getParameter("port").getValue();
            String hostname = "localhost";

            //Resolve hostname
            if (axisConfiguration.getParameter("hostname") != null) {
                hostname = (String) axisConfiguration.getParameter("hostname").getValue();
            } else {
                try {
                    hostname = NetworkUtils.getLocalHostname();
                } catch (SocketException e) {
                    log.warn("SocketException occurred when trying to obtain IP address of local machine");
                }
            }
            return hostname + ':' + portStr;
        }

        throw new AxisFault("http/https transport listeners are required in axis2.xml");
    }
}

