/*
Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.util.SecurityManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.micro.integrator.core.resolver.CarbonEntityResolver;

import static org.wso2.micro.integrator.core.Constants.DYNAMIC_PROPERTY_PLACEHOLDER_PREFIX;
import static org.wso2.micro.integrator.core.Constants.ENV_VAR_PLACEHOLDER_PREFIX;
import static org.wso2.micro.integrator.core.Constants.PLACEHOLDER_SUFFIX;
import static org.wso2.micro.integrator.core.Constants.SYS_PROPERTY_PLACEHOLDER_PREFIX;

public class CarbonUtils {

    private static Log log = LogFactory.getLog(org.wso2.micro.core.util.CarbonUtils.class);

    public CarbonUtils() {
    }

    public static String replaceSystemVariablesInXml(String xmlConfiguration) throws CarbonException {
        InputStream in = replaceSystemVariablesInXml((InputStream)(new ByteArrayInputStream(xmlConfiguration.getBytes())));

        try {
            xmlConfiguration = IOUtils.toString(in);
            return xmlConfiguration;
        } catch (IOException var3) {
            throw new CarbonException("Error in converting InputStream to String");
        }
    }

    public static InputStream replaceSystemVariablesInXml(InputStream xmlConfiguration) throws CarbonException {
        DocumentBuilderFactory documentBuilderFactory = getSecuredDocumentBuilder();

        Document doc;
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver(new CarbonEntityResolver());
            doc = documentBuilder.parse(xmlConfiguration);
        } catch (Exception var6) {
            throw new CarbonException("Error in building Document", var6);
        }

        NodeList nodeList = null;
        if (doc != null) {
            nodeList = doc.getElementsByTagName("*");
        }

        if (nodeList != null) {
            for(int i = 0; i < nodeList.getLength(); ++i) {
                resolveLeafNodeValue(nodeList.item(i));
            }
        }

        return toInputStream(doc);
    }

    public static void resolveLeafNodeValue(Node node) {
        if (node != null) {
            Element element = (Element)node;
            NodeList childNodeList = element.getChildNodes();

            for(int j = 0; j < childNodeList.getLength(); ++j) {
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

    public static String resolveSystemProperty(String text) {
        String sysRefs = StringUtils.substringBetween(text, SYS_PROPERTY_PLACEHOLDER_PREFIX, PLACEHOLDER_SUFFIX);
        String envRefs = StringUtils.substringBetween(text, ENV_VAR_PLACEHOLDER_PREFIX, PLACEHOLDER_SUFFIX);

        // Resolves system property references ($sys{ref}) in an individual string.
        if (sysRefs != null) {
            String property = System.getProperty(sysRefs);
            if (StringUtils.isNotEmpty(property)) {
                text = text.replaceAll(Pattern.quote(SYS_PROPERTY_PLACEHOLDER_PREFIX + sysRefs + PLACEHOLDER_SUFFIX), property);
            } else {
                log.error("System property is not available for " + sysRefs);
            }
            return text;
        }
        // Resolves environment variable references ($env{ref}) in an individual string.
        if (envRefs != null) {
            String resolvedValue = System.getenv(envRefs);
            if (StringUtils.isNotEmpty(resolvedValue)) {
                text = text.replaceAll(Pattern.quote(ENV_VAR_PLACEHOLDER_PREFIX + envRefs + PLACEHOLDER_SUFFIX), resolvedValue);
            } else {
                log.error("Environment variable is not available for " + envRefs);
            }
            return text;
        }
        int indexOfStartingChars = -1;

        int indexOfClosingBrace;
        while(indexOfStartingChars < text.indexOf(DYNAMIC_PROPERTY_PLACEHOLDER_PREFIX)
                && (indexOfStartingChars = text.indexOf(DYNAMIC_PROPERTY_PLACEHOLDER_PREFIX)) != -1
                && (indexOfClosingBrace = text.indexOf(125)) != -1) {
            String sysProp = text.substring(indexOfStartingChars + 2, indexOfClosingBrace);
            String propValue = System.getProperty(sysProp);
            if (propValue == null) {
                propValue = System.getenv(sysProp);
            }
            if (propValue != null) {
                text = text.substring(0, indexOfStartingChars) + propValue + text.substring(indexOfClosingBrace + 1);
            }

            if (sysProp.equals("carbon.home") && propValue != null && propValue.equals(".")) {
                text = (new File(".")).getAbsolutePath() + File.separator + text;
            }
        }

        return text;
    }

    public static InputStream toInputStream(Document doc) throws CarbonException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Source xmlSource = new DOMSource(doc);
            Result result = new StreamResult(outputStream);
            TransformerFactory factory = getSecuredTransformerFactory();
            factory.newTransformer().transform(xmlSource, result);
            InputStream in = new ByteArrayInputStream(outputStream.toByteArray());
            return in;
        } catch (TransformerException var5) {
            throw new CarbonException("Error in transforming DOM to InputStream", var5);
        }
    }

    /**
     * Create a secure process enabled TransformerFactory.
     *
     * @return
     * @throws TransformerConfigurationException
     */
    public static TransformerFactory getSecuredTransformerFactory() throws TransformerConfigurationException {

        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Can't configure the factory to disable external entity resolution
        // factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        // factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return factory;
    }

    private static DocumentBuilderFactory getSecuredDocumentBuilder() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        try {
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
        } catch (ParserConfigurationException var2) {
            log.error("Failed to load XML Processor Feature external-general-entities or external-parameter-entities or nonvalidating/load-external-dtd");
        }

        SecurityManager securityManager = new SecurityManager();
        securityManager.setEntityExpansionLimit(0);
        dbf.setAttribute("http://apache.org/xml/properties/security-manager", securityManager);
        return dbf;
    }

    public static void registerFaultyService(String artifactPath, String serviceType, ConfigurationContext configurationContext) throws
                                                                                                                                 AxisFault {
        String repository = configurationContext.getAxisConfiguration().getRepository().getPath();
        String serviceName = artifactPath;
        if (File.separatorChar == '\\') {
            serviceName = artifactPath.replace('\\', '/');
            repository = repository.replace('\\', '/');
        }

        if (serviceName.endsWith("/")) {
            serviceName = serviceName.substring(0, serviceName.length() - 1);
        }

        if (repository.endsWith("/")) {
            repository = repository.substring(0, repository.length() - 1);
        }

        serviceName = serviceName.substring(repository.length() + 1);
        serviceName = serviceName.substring(serviceName.indexOf(47) + 1);
        int slashIndex = serviceName.lastIndexOf(47);
        int dotIndex = serviceName.lastIndexOf(46);
        if (dotIndex != -1 && dotIndex > slashIndex) {
            serviceName = serviceName.substring(0, dotIndex);
        }

        AxisService service = new AxisService(serviceName);
        if (serviceType != null) {
            Parameter serviceTypeParam = new Parameter("serviceType", serviceType);
            service.addParameter(serviceTypeParam);
        }

        Map<String, AxisService>
                faultyServicesMap = (Map)configurationContext.getPropertyNonReplicable("local_carbon.faulty.services.map");
        if (faultyServicesMap == null) {
            faultyServicesMap = new HashMap();
            configurationContext.setNonReplicableProperty("local_carbon.faulty.services.map", faultyServicesMap);
        }

        ((Map)faultyServicesMap).put(artifactPath, service);
    }

    public static AxisService getFaultyService(String serviceName, ConfigurationContext configurationContext) {
        Map<String, AxisService> faultyServicesMap = (Map)configurationContext.getPropertyNonReplicable("local_carbon.faulty.services.map");
        return faultyServicesMap != null ? (AxisService)faultyServicesMap.get(serviceName) : null;
    }
}
