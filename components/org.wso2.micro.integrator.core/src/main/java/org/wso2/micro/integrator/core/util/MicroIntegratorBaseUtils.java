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

import com.google.gson.Gson;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.util.base64.Base64Utils;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.deployment.DeploymentConstants;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.util.XMLUtils;
import org.apache.commons.httpclient.Header;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.commons.resolvers.ResolverException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.xerces.util.SecurityManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.micro.core.CarbonAxisConfigurator;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.integrator.core.internal.CarbonCoreDataHolder;
import org.wso2.micro.integrator.core.internal.MicroIntegratorBaseConstants;
import org.wso2.micro.integrator.core.resolver.CarbonEntityResolver;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.management.ManagementPermission;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static org.wso2.micro.core.util.CarbonUtils.getSecuredTransformerFactory;

public class MicroIntegratorBaseUtils {

    private static Log log = LogFactory.getLog(MicroIntegratorBaseUtils.class);

    private static final String REPOSITORY = "repository";
    private static final String UPDATES = "updates";
    private static boolean isServerConfigInitialized;
    private static OMElement axis2Config;
    private static final String TRUE = "true";
    private static final int ENTITY_EXPANSION_LIMIT = 0;
    private static CarbonAxisConfigurator carbonAxisConfigurator;
    private static CarbonServerConfigurationService serverConfigurationService;

    public static String getServerXml() {
        String carbonXML = System.getProperty(MicroIntegratorBaseConstants.CARBON_CONFIG_DIR_PATH);
        if (carbonXML == null) {
            return getCarbonConfigDirPath() + File.separator + "carbon.xml";
        }
        return carbonXML + File.separator + "carbon.xml";
    }

    public static String getUpdateLevel() {
        String defaultUpdateLevel = "-";
        String carbonHome = getCarbonHome();
        if (carbonHome != null) {
            String configFilePath = carbonHome + File.separator + UPDATES + File.separator + "config.json";
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                Gson gsonParser = new Gson();
                try {
                    Reader configFileReader = Files.newBufferedReader(Paths.get(configFilePath));
                    Map<?, ?> configMap = gsonParser.fromJson(configFileReader, Map.class);
                    return (String) configMap.get("update-level");
                } catch (Exception e) {
                    return defaultUpdateLevel;
                }
            } else {
                return defaultUpdateLevel;
            }
        }
        return defaultUpdateLevel;
    }

    public static String getCarbonConfigDirPath() {
        String carbonConfigDirPath = System.getProperty(MicroIntegratorBaseConstants.CARBON_CONFIG_DIR_PATH);
        if (carbonConfigDirPath == null) {
            carbonConfigDirPath = System.getenv(MicroIntegratorBaseConstants.CARBON_CONFIG_DIR_PATH_ENV);
            if (carbonConfigDirPath == null) {
                return getCarbonHome() + File.separator + "repository" + File.separator + "conf";
            }
        }
        return carbonConfigDirPath;
    }

    public static String getCarbonHome() {
        String carbonHome = System.getProperty(MicroIntegratorBaseConstants.CARBON_HOME);
        if (carbonHome == null) {
            carbonHome = System.getenv(MicroIntegratorBaseConstants.CARBON_HOME_ENV);
            System.setProperty(MicroIntegratorBaseConstants.CARBON_HOME, carbonHome);
        }
        return carbonHome;
    }

    public static String getUserMgtXMLPath() {
        String carbonHome = getCarbonHome();
        String configPath = null;
        if (carbonHome != null) {
            if (System.getProperty(org.wso2.micro.core.Constants.USER_MGT_XML_PATH) == null) {
                configPath = getCarbonConfigDirPath() + File.separator + "user-mgt.xml";
            } else {
                configPath = System.getProperty(org.wso2.micro.core.Constants.USER_MGT_XML_PATH);
            }
        }
        return configPath;
    }

    public static void checkSecurity() {
        java.lang.SecurityManager secMan = System.getSecurityManager();
        if (secMan != null) {
            secMan.checkPermission(new ManagementPermission("control"));
        }
    }

    public static <T> T[] arrayCopyOf(T[] original) {
        if (original == null) {
            return null;
        }
        Class newType = original.getClass();
        int newLength = original.length;
        T[] copy = (newType == Object[].class) ?
                (T[]) new Object[newLength] :
                (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, 0, copy, 0, newLength);
        return copy;
    }

    public static boolean isChildNode() {
        return TRUE.equals(System.getProperty("instance"));
    }

    public static boolean isURL(String location) {
        try {
            new URL(location);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static String getAxis2Xml() {
        String axis2XML = CarbonServerConfigurationService.getInstance().
                getFirstProperty("Axis2Config.ConfigurationFile");
        if (axis2XML == null) {
            axis2XML = System.getProperty(Constants.AXIS2_CONF);
        }
        return axis2XML;
    }

    public static boolean isMultipleInstanceCase() {
        return System.getProperty("instances.value") != null;
    }

    public static String getComponentsRepo() {
        String componentsRepo = System.getProperty(org.wso2.micro.core.Constants.COMPONENT_REP0);
        if (componentsRepo == null) {
            componentsRepo = System.getenv(MicroIntegratorBaseConstants.COMPONENT_REP0_ENV);
            if (componentsRepo == null) {
                return getCarbonHome() + File.separator + REPOSITORY + File.separator + "components" + File.separator
                        + "plugins";
            }
        }
        return componentsRepo;
    }

    public static String getAxis2ServicesDir(AxisConfiguration axisConfig) {
        String servicesDir = "axis2services";
        String serviceDirPara = (String) axisConfig.getParameterValue(DeploymentConstants.SERVICE_DIR_PATH);
        if (serviceDirPara != null) {
            servicesDir = serviceDirPara;
        }
        return servicesDir;
    }

    public static String getAxis2Repo() {
        String axis2Repo = System.getProperty(org.wso2.micro.core.Constants.AXIS2_REPO);
        if (axis2Repo == null) {
            axis2Repo = System.getenv(MicroIntegratorBaseConstants.AXIS2_REPO_ENV);
        }
        return axis2Repo;
    }

    public static String getCarbonRepository() {
        CarbonServerConfigurationService serverConfig = getServerConfiguration();
        return serverConfig.getFirstProperty("Axis2Config.RepositoryLocation");
    }

    public static CarbonServerConfigurationService getServerConfiguration() {
        CarbonServerConfigurationService serverConfig = CarbonServerConfigurationService.getInstance();
        if (!isServerConfigInitialized) {
            String serverXml = MicroIntegratorBaseUtils.getServerXml();
            File carbonXML = new File(serverXml);
            InputStream inSXml = null;
            try {
                inSXml = new FileInputStream(carbonXML);
                serverConfig.init(inSXml);
                isServerConfigInitialized = true;
            } catch (Exception e) {
                //log.error("Cannot read file " + serverXml, e);
            } finally {
                if (inSXml != null) {
                    try {
                        inSXml.close();
                    } catch (IOException e) {
                        //log.warn("Cannot close file " + serverXml, e);
                    }
                }
            }
        }
        return serverConfig;
    }

    public static boolean isDataService(org.apache.axis2.context.MessageContext messageContext) throws AxisFault {
        AxisService axisService = messageContext.getAxisService();
        if (axisService != null) {
            URL file = axisService.getFileName();
            if (file != null) {
                String filePath = file.getPath();
                return filePath.endsWith(".dbs");
            }
        }
        return false;
    }

    public static String getPassThroughJsonBuilder() throws IOException, XMLStreamException {
        String psJsonBuilder = getPropertyFromAxisConf(org.wso2.micro.integrator.core.Constants.PASSTHRU_JSON_BUILDER);
        if (psJsonBuilder == null) {
            return "org.apache.synapse.commons.json.JsonStreamBuilder";
        } else {
            return psJsonBuilder;
        }
    }

    public static String getPassThroughJsonFormatter() throws IOException, XMLStreamException {
        String psJsonFormatter = getPropertyFromAxisConf(org.wso2.micro.integrator.core.Constants.PASSTHRU_JSON_FORMATTER);
        if (psJsonFormatter == null) {
            return "org.apache.synapse.commons.json.JsonStreamFormatter";
        } else {
            return psJsonFormatter;
        }
    }

    public static String getDSSJsonBuilder() throws IOException, XMLStreamException {
        String dssJsonBuilder = getPropertyFromAxisConf(org.wso2.micro.integrator.core.Constants.DATASERVICE_JSON_BUILDER);
        if (dssJsonBuilder == null) {
            return "org.apache.axis2.json.gson.JsonBuilder";
        } else {
            return dssJsonBuilder;
        }
    }

    public static String getDSSJsonFormatter() throws IOException, XMLStreamException {
        String dssJsonFormatter = getPropertyFromAxisConf(org.wso2.micro.integrator.core.Constants.DATASERVICE_JSON_FORMATTER);
        if (dssJsonFormatter == null) {
            return "org.apache.axis2.json.gson.JsonFormatter";
        } else {
            return dssJsonFormatter;
        }
    }

    private static String getPropertyFromAxisConf(String parameter) throws IOException, XMLStreamException {
        try (InputStream file = new FileInputStream(Paths.get(getCarbonConfigDirPath(), "axis2", "axis2.xml").toString())) {
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

    public static void setBasicAccessSecurityHeaders(String userName, String password, boolean rememberMe,
                                                     ServiceClient serviceClient) {
        String userNamePassword = userName + ":" + password;
        String encodedString = Base64Utils.encode(userNamePassword.getBytes());

        String authorizationHeader = "Basic " + encodedString;

        List<Header> headers = new ArrayList<>();

        Header authHeader = new Header("Authorization", authorizationHeader);
        headers.add(authHeader);

        if (rememberMe) {
            Header rememberMeHeader = new Header("RememberMe", TRUE);
            headers.add(rememberMeHeader);
        }

        serviceClient.getOptions().setProperty(HTTPConstants.HTTP_HEADERS, headers);
    }

    private static DocumentBuilderFactory getSecuredDocumentBuilder() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        try {
            dbf.setFeature(org.apache.xerces.impl.Constants.SAX_FEATURE_PREFIX +
                    org.apache.xerces.impl.Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            dbf.setFeature(org.apache.xerces.impl.Constants.SAX_FEATURE_PREFIX +
                    org.apache.xerces.impl.Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            dbf.setFeature(org.apache.xerces.impl.Constants.XERCES_FEATURE_PREFIX +
                    org.apache.xerces.impl.Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException e) {
        }

        SecurityManager securityManager = new SecurityManager();
        securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
        dbf.setAttribute(org.apache.xerces.impl.Constants.XERCES_PROPERTY_PREFIX +
                org.apache.xerces.impl.Constants.SECURITY_MANAGER_PROPERTY, securityManager);
        return dbf;
    }

    public static InputStream replaceSystemVariablesInXml(InputStream xmlConfiguration) throws CarbonException {
        DocumentBuilderFactory documentBuilderFactory = getSecuredDocumentBuilder();
        DocumentBuilder documentBuilder;
        Document doc;
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver(new CarbonEntityResolver());
            doc = documentBuilder.parse(xmlConfiguration);
        } catch (Exception e) {
            throw new CarbonException("Error in building Document", e);
        }
        NodeList nodeList = null;
        if (doc != null) {
            nodeList = doc.getElementsByTagName("*");
        }
        if (nodeList != null) {
            for (int i = 0; i < nodeList.getLength(); i++) {
                resolveLeafNodeValue(nodeList.item(i));
            }
        }
        return toInputStream(doc);
    }

    public static void resolveLeafNodeValue(Node node) {
        if (node != null) {
            Element element = (Element) node;
            NodeList childNodeList = element.getChildNodes();
            for (int j = 0; j < childNodeList.getLength(); j++) {
                Node chileNode = childNodeList.item(j);
                if (!chileNode.hasChildNodes()) {
                    String nodeValue = resolveSystemProperty(chileNode.getTextContent());
                    childNodeList.item(j).setTextContent(nodeValue);
                } else {
                    resolveLeafNodeValue(chileNode);
                }
            }
        }
    }

    public static InputStream toInputStream(Document doc) throws CarbonException {
        InputStream in;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Source xmlSource = new DOMSource(doc);
            Result result = new StreamResult(outputStream);
            TransformerFactory factory = getSecuredTransformerFactory();
            factory.newTransformer().transform(xmlSource, result);
            in = new ByteArrayInputStream(outputStream.toByteArray());
        } catch (TransformerException e) {
            throw new CarbonException("Error in transforming DOM to InputStream", e);
        }
        return in;
    }

    public static String resolveSystemProperty(String text) {
        int indexOfStartingChars = -1;
        int indexOfClosingBrace;

        while (indexOfStartingChars < text.indexOf("${")
                && (indexOfStartingChars = text.indexOf("${")) != -1
                && (indexOfClosingBrace = text.indexOf('}')) != -1) {
            String sysProp = text.substring(indexOfStartingChars + 2,
                    indexOfClosingBrace);
            String propValue = System.getProperty(sysProp);
            if (propValue != null) {
                text = text.substring(0, indexOfStartingChars) + propValue
                        + text.substring(indexOfClosingBrace + 1);
            }
            if (sysProp.equals("carbon.home") && propValue != null
                    && propValue.equals(".")) {

                text = new File(".").getAbsolutePath() + File.separator + text;
            }
        }
        return text;
    }

    public static int getPortFromServerConfig(String property) {
        CarbonServerConfigurationService serverConfig = getServerConfiguration();
        String portValue = null;
        int portNumber = -1;

        if (property.contains("${") && property.contains("}")) {
            String template = property.substring(property.indexOf("${") + 2, property.indexOf("}"));
            portValue = serverConfig.getFirstProperty(template);
            if (portValue != null) {
                portNumber = Integer.parseInt(portValue);
            }
        }

        String portOffsetStr = System.getProperty("portOffset", serverConfig.getFirstProperty("Ports.Offset"));
        int portOffset = 0;
        if (portOffsetStr != null) {
            portOffset = Integer.parseInt(portOffsetStr);
        }
        System.setProperty("portOffset", String.valueOf(portOffset));

        return portNumber + portOffset;
    }

    public static void setCarbonAxisConfigurator(CarbonAxisConfigurator carbonAxisConfig) {
        carbonAxisConfigurator = carbonAxisConfig;
    }

    public static void setServerConfigurationService(CarbonServerConfigurationService serverConfiguration) {
        serverConfigurationService = serverConfiguration;
    }

    public static CarbonAxisConfigurator getCarbonAxisConfigurator() {
        return carbonAxisConfigurator;
    }

    public static SynapseEnvironment getSynapseEnvironment() {
        Parameter synapseEnvironmentParatemer =
                CarbonCoreDataHolder.getInstance().getAxis2ConfigurationContextService().getServerConfigContext()
                        .getAxisConfiguration().getParameter(SynapseConstants.SYNAPSE_ENV);
        return (SynapseEnvironment) synapseEnvironmentParatemer.getValue();
    }

    public static String getServerHostName() {
        return serverConfigurationService.getFirstProperty("HostName");
    }

    public static int getServerHTTPListenerPort() throws ResolverException {
        try {
            int portOffset = 0;
            String offsetProp = System.getProperty(org.wso2.micro.core.Constants.SERVER_PORT_OFFSET);
            if (offsetProp != null) {
                portOffset = Integer.parseInt(offsetProp);
            }

            Object portValue = carbonAxisConfigurator.getAxisConfiguration()
                    .getTransportsIn()
                    .get(org.wso2.micro.core.Constants.HTTP_TRANSPORT)
                    .getParameter(org.wso2.micro.core.Constants.TRANSPORT_PORT)
                    .getValue();

            return Integer.parseInt(portValue.toString()) + portOffset;
        } catch (AxisFault e) {
            throw new ResolverException("Error in getting server HTTP listener port", e);
        }
    }

    public static int getServerHTTPSListenerPort() throws ResolverException {
        try {
            int portOffset = 0;
            String offsetProp = System.getProperty(org.wso2.micro.core.Constants.SERVER_PORT_OFFSET);
            if (offsetProp != null) {
                portOffset = Integer.parseInt(offsetProp);
            }

            Object portValue = carbonAxisConfigurator.getAxisConfiguration()
                    .getTransportsIn()
                    .get(org.wso2.micro.core.Constants.HTTPS_TRANSPORT)
                    .getParameter(org.wso2.micro.core.Constants.TRANSPORT_PORT)
                    .getValue();

            return Integer.parseInt(portValue.toString()) + portOffset;
        } catch (AxisFault e) {
            throw new ResolverException("Error in getting server HTTPS listener port", e);
        }
    }
}
