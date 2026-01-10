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

package org.wso2.micro.integrator.core.util;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.XMLUtils;
import org.apache.axis2.AxisFault;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportInDescription;
import org.wso2.micro.integrator.core.internal.CarbonServerConfigurationService;
import org.wso2.micro.integrator.core.internal.ResolverException;

import javax.xml.stream.XMLStreamException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Iterator;
import javax.xml.namespace.QName;

public class MicroIntegratorBaseUtils {

    private static OMElement axis2Config;
    private static org.wso2.micro.integrator.core.internal.CarbonAxisConfigurator carbonAxisConfigurator;

    // --------------------------
    // getPropertyFromAxisConf
    // --------------------------
    private static String getPropertyFromAxisConf(String parameter) throws IOException, XMLStreamException {
        try (InputStream file = new FileInputStream(Paths.get(getCarbonConfigDirPath(), "axis2", "axis2.xml").toString())) {
            log.debug("Reading property '" + parameter + "' from axis2.xml");
            if (axis2Config == null) {
                OMElement element = (OMElement) XMLUtils.toOM(file);
                element.build();
                axis2Config = element;
            }
            Iterator parameters = axis2Config.getChildrenWithName(new QName("parameter"));
            while (parameters.hasNext()) {
                OMElement parameterElement = (OMElement) parameters.next();
                if (parameter.equals(parameterElement.getAttribute(new QName("name")).getAttributeValue())) {
                    return parameterElement.getText();
                }
            }
            return null;
        } catch (IOException | XMLStreamException e) {
            throw e;
        }
    }

    // --------------------------
    // getPortFromServerConfig
    // --------------------------
    public static int getPortFromServerConfig(String property) {
        CarbonServerConfigurationService serverConfig = getServerConfiguration();
        String portValue = null;
        int portNumber = -1;

        // The following condition deals with ports specified to be read from carbon.xml
        if (property.contains("${") && property.contains("}")) {
            String template = property.substring(property.indexOf("${") + 2, property.indexOf("}"));
            portValue = serverConfig.getFirstProperty(template);
            if (portValue != null) {
                try {
                    portNumber = Integer.parseInt(portValue);
                } catch (NumberFormatException e) {
                    log.error("Invalid port number in server config: " + portValue, e);
                }
            }
        } else {
            // Direct numeric value
            try {
                portNumber = Integer.parseInt(property);
            } catch (NumberFormatException e) {
                log.error("Invalid port number: " + property, e);
            }
        }

        // setting up port offset properties as system global property
        String portOffsetStr = System.getProperty(org.wso2.micro.core.Constants.SERVER_PORT_OFFSET,
                serverConfig.getFirstProperty("Ports.Offset"));
        int portOffset = 0;
        if (portOffsetStr != null) {
            try {
                portOffset = Integer.parseInt(portOffsetStr);
            } catch (NumberFormatException e) {
                log.error("Invalid port offset: " + portOffsetStr, e);
            }
        }
        System.setProperty(org.wso2.micro.core.Constants.SERVER_PORT_OFFSET, String.valueOf(portOffset));

        return portNumber + portOffset;
    }

    // --------------------------
    // HTTP/HTTPS helper
    // --------------------------
    private static int getTransportListenerPort(String transportType) throws ResolverException {
        try {
            int portOffset = 0;
            String offsetProp = System.getProperty(org.wso2.micro.core.Constants.SERVER_PORT_OFFSET);
            if (offsetProp != null) {
                portOffset = Integer.parseInt(offsetProp);
            }

            AxisConfiguration axisConfig = carbonAxisConfigurator.getAxisConfiguration();
            TransportInDescription transport = axisConfig.getTransportsIn().get(transportType);
            if (transport == null) {
                throw new ResolverException(transportType + " transport is not configured");
            }

            Parameter portParam = transport.getParameter(org.wso2.micro.core.Constants.TRANSPORT_PORT);
            if (portParam == null || portParam.getValue() == null) {
                throw new ResolverException(transportType + " transport port parameter is not configured");
            }

            return Integer.parseInt(portParam.getValue().toString()) + portOffset;
        } catch (AxisFault | NumberFormatException e) {
            throw new ResolverException("Error getting " + transportType + " listener port", e);
        }
    }

    public static int getServerHTTPListenerPort() throws ResolverException {
        return getTransportListenerPort(org.wso2.micro.core.Constants.HTTP_TRANSPORT);
    }

    public static int getServerHTTPSListenerPort() throws ResolverException {
        return getTransportListenerPort(org.wso2.micro.core.Constants.HTTPS_TRANSPORT);
    }

    // --------------------------
    // This is to set the carbonAxisConfigurator instance.
    // --------------------------
    public static void setCarbonAxisConfigurator(org.wso2.micro.integrator.core.internal.CarbonAxisConfigurator carbonAxisConfig) {
        carbonAxisConfigurator = carbonAxisConfig;
    }

    // --------------------------
    // Utility methods
    // --------------------------
    private static CarbonServerConfigurationService getServerConfiguration() {
        // implement fetching the CarbonServerConfigurationService instance
        return null;
    }

    private static String getCarbonConfigDirPath() {
        // implement fetching carbon config dir path
        return null;
    }

    private static class log {
        public static void debug(String msg) {
            System.out.println("[DEBUG] " + msg);
        }
        public static void error(String msg, Exception e) {
            System.err.println("[ERROR] " + msg);
            e.printStackTrace();
        }
    }
}
