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
package org.wso2.micro.integrator.core.services;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.util.SecurityManager;
import org.w3c.dom.Element;
import org.wso2.micro.integrator.core.internal.MicroIntegratorBaseConstants;
import org.wso2.micro.integrator.core.internal.MicroIntegratorConfigurationException;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.carbon.securevault.SecretManagerInitializer;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;

import static org.wso2.micro.core.util.CarbonUtils.resolveSystemProperty;

/**
 * This class stores the configuration of the Carbon Server.
 */
@SuppressWarnings("unused")
public class CarbonServerConfigurationService {

	/**
	 * Classes which are allowed to directly call secured methods in this class
	 * <p/>
	 * Note that we have to use the String form of the class name and not, for
	 * example, RegistryResolver.class.getName() since this may unnecessarily
	 * cause NoClassDefFoundErrors
	 */
	private static final List<String> ALLOWED_CLASSES = Arrays
			.asList(CarbonServerConfigurationService.class.getName(),
                    "org.wso2.carbon.utils.CarbonUtils",
                    "org.wso2.carbon.registry.core.utils.RegistryUtils",
                    "org.wso2.carbon.utils.logging.TenantAwarePatternLayout$TenantAwarePatternParser$ServiceNamePatternConverter");

	/**
	 * Constant to be used for properties storing the axis2 repository location.
	 */
	public static final String AXIS2_CONFIG_REPO_LOCATION = "Axis2Config.RepositoryLocation";

	/**
	 * Constant to be used for properties storing the http port of the servlet
	 * transport.
	 */
	public static final String HTTP_PORT = "HTTP.Port";

	/**
	 * Constant used to define the WSO2 server version.
	 */
	public static final String SERVER_VERSION = "Version";

	private static final int ENTITY_EXPANSION_LIMIT = 0;

	/**
	 * Constant to be used for properties storing the port of the command
	 * listener.
	 */
	public static final String COMMAND_LISTENER_PORT = "CommandListener.Port";

	private static Log log = LogFactory.getLog(CarbonServerConfigurationService.class);

	private Map<String, List<String>> configuration = new HashMap<>();
	private boolean isInitialized;
	private boolean isLoadedConfigurationPreserved = false;
	private String documentXML;
	private SecretResolver secretResolver;
	private OMElement carbonXmlElement;

	/**
	 * Stores the singleton server configuration instance.
	 */
	private static CarbonServerConfigurationService instance = new CarbonServerConfigurationService();

	/**
	 * Method to retrieve an instance of the server configuration.
	 *
	 * @return instance of the server configuration
	 */
	public static CarbonServerConfigurationService getInstance() {
		// Need permissions in order to instantiate ServerConfiguration
		MicroIntegratorBaseUtils.checkSecurity();
		return instance;
	}

	// Private constructor preventing creation of duplicate instances.
	private CarbonServerConfigurationService() {
	}

	/**
	 * This initializes the server configuration. This method should only be
	 * called once, for successive calls, it will be checked.
	 * 
	 * @param xmlInputStream
	 *            the server configuration file stream.
	 * 
	 * @throws org.wso2.carbon.base.ServerConfigurationException
	 *             if the operation failed.
	 */
	public synchronized void init(InputStream xmlInputStream)
            throws MicroIntegratorConfigurationException {
		if (isInitialized) {
			return;
		}

		if (!isLoadedConfigurationPreserved) {
			configuration.clear();
		}

		try {
			carbonXmlElement = new StAXOMBuilder(xmlInputStream)
					.getDocumentElement();
			SecretManagerInitializer secretManagerInitializer = new SecretManagerInitializer();
			secretManagerInitializer.init();
			secretResolver = SecretResolverFactory.create(carbonXmlElement, true);
			Stack<String> nameStack = new Stack<String>();
			readChildElements(carbonXmlElement, nameStack);
			isInitialized = true;
			isLoadedConfigurationPreserved = false;
			documentXML = carbonXmlElement.toStringWithConsume();
		} catch (XMLStreamException e) {
			log.fatal("Problem in parsing the configuration file ", e);
			throw new MicroIntegratorConfigurationException(e);
		} catch (Exception e) {
			log.fatal("Problem in parsing the carbon.xml to DOM ", e);
			throw new MicroIntegratorConfigurationException(e);
		}
	}

	/**
	 * This initializes the server configuration. This method should only be
	 * called once, for successive calls, it will be checked.
	 *
	 * @param configurationXMLLocation the location of the server configuration file (carbon.xml).
	 * @throws MicroIntegratorConfigurationException if the operation failed.
	 */
	public synchronized void init(String configurationXMLLocation)
			throws MicroIntegratorConfigurationException {
		if (isInitialized) {
			return;
		}
		if (configurationXMLLocation == null) {
			String configPath = System.getProperty(MicroIntegratorBaseConstants.CARBON_CONFIG_DIR_PATH);
			if (configPath == null) {
				configurationXMLLocation = Paths.get("conf", "carbon.xml").toString();
			} else {
				String relativeConfDirPath = Paths.get(System.getProperty(MicroIntegratorBaseConstants.CARBON_HOME)).relativize(
                        Paths.get(configPath)).toString();
				configurationXMLLocation = Paths.get(relativeConfDirPath, "carbon.xml").toString();
			}
		}

		InputStream xmlInputStream = null;
		try {
			try {
				// URL will parse the location according to respective RFC's and
				// open a connection.
				URL urlXMLLocation = new URL(configurationXMLLocation);
				xmlInputStream = urlXMLLocation.openStream();
			} catch (MalformedURLException e) {
				File f = new File(configurationXMLLocation);
				try {
					xmlInputStream = new FileInputStream(f);
				} catch (FileNotFoundException e1) {
					// As a last resort test in the classpath
					ClassLoader cl = CarbonServerConfigurationService.class.getClassLoader();
					xmlInputStream = cl
							.getResourceAsStream(configurationXMLLocation);
					if (xmlInputStream == null) {
						String msg = "Configuration File cannot be loaded from "
                                     + configurationXMLLocation;
						log.fatal(msg, e1);
						throw new MicroIntegratorConfigurationException(msg, e1);

					}
				}
			} catch (IOException e) {
				log.fatal("Configuration File cannot be loaded from "
						+ configurationXMLLocation, e);
				throw new MicroIntegratorConfigurationException(e);
			}
			init(xmlInputStream);
		} finally {
			if (xmlInputStream != null) {
				try {
					xmlInputStream.close();
				} catch (IOException e) {
					log.warn("Cannot close input stream", e);
				}
			}
		}
	}

	/**
	 * Method to forcibly initialize the server configuration. If there is any
	 * configuration loaded, it will not be preserved.
	 * 
	 * @param xmlInputStream
	 *            the server configuration file stream.
	 * 
	 * @throws MicroIntegratorConfigurationException
	 *             if the operation failed.
	 */
	public synchronized void forceInit(InputStream xmlInputStream)
			throws MicroIntegratorConfigurationException {
		isInitialized = false;
		init(xmlInputStream);
	}

	/**
	 * Method to forcibly initialize the server configuration. If there is any
	 * configuration loaded, it will not be preserved.
	 * 
	 * @param configurationXMLLocation
	 *            the location of the server configuration file (carbon.xml).
	 * 
	 * @throws MicroIntegratorConfigurationException
	 *             if the operation failed.
	 */
	public synchronized void forceInit(String configurationXMLLocation)
			throws MicroIntegratorConfigurationException {
		isInitialized = false;
		init(configurationXMLLocation);
	}

	/**
	 * Method to forcibly initialize the server configuration.
	 * 
	 * @param configurationXMLLocation
	 *            the location of the server configuration file (carbon.xml).
	 * @param isLoadedConfigurationPreserved
	 *            whether the currently loaded configuration is preserved.
	 * 
	 * @throws MicroIntegratorConfigurationException
	 *             if the operation failed.
	 */
	public synchronized void forceInit(String configurationXMLLocation,
                                       boolean isLoadedConfigurationPreserved)
			throws MicroIntegratorConfigurationException {
		isInitialized = false;
		this.isLoadedConfigurationPreserved = isLoadedConfigurationPreserved;
		init(configurationXMLLocation);
	}

	private void readChildElements(OMElement serverConfig, Stack<String> nameStack) {

		for (Iterator childElements = serverConfig.getChildElements(); childElements.hasNext(); ) {
			OMElement element = (OMElement) childElements.next();
			nameStack.push(element.getLocalName());
			if (elementHasText(element)) {
				String key = getKey(nameStack);
				String value;
				String resolvedValue = MiscellaneousUtil.resolve(element, secretResolver);

				if (resolvedValue != null && !resolvedValue.isEmpty()) {
					value = resolvedValue;
				} else {
					value = element.getText();
				}
				value = resolveSystemProperty(value);
				addToConfiguration(key, value);
			}
			readChildElements(element, nameStack);
			nameStack.pop();
		}
	}

	private void addToConfiguration(String key, String value) {
		List<String> list = configuration.get(key);
		if (list == null) {
			list = new ArrayList<String>();
			list.add(value);
			configuration.put(key, list);
		} else {
			if (!list.contains(value)) {
				list.add(value);
			}
		}
	}

	private void overrideConfiguration(String key, String value) {
		List<String> list = new ArrayList<String>();
		list.add(value);
		configuration.put(key, list);
	}

	/**
	 * Method to a given key, value pair into the configuration.
	 * 
	 * @param key
	 *            the key to add
	 * @param value
	 *            the value
	 */
	public void setConfigurationProperty(String key, String value) {
		addToConfiguration(key, value);

		StringTokenizer tokenizer = new StringTokenizer(key, ".");
		OMElement ele = getDocumentElementInternal();
		String token = "";
		while (tokenizer.hasMoreTokens()) {
			token = tokenizer.nextToken();
			if (ele != null) {
				ele = ele.getFirstChildWithName(new QName("", token, ""));
			} else {
				break;
			}
		}
		if (ele != null) {
			ele.getFirstOMChild().detach();
			ele.setText(token);
		}
	}

	/**
	 * overrides the configuration property instead of adding the value to the
	 * list
	 * 
	 * @param key
	 * @param value
	 */
	public void overrideConfigurationProperty(String key, String value) {
		overrideConfiguration(key, value);

		StringTokenizer tokenizer = new StringTokenizer(key, ".");
		OMElement ele = getDocumentElementInternal();
		String token = "";
		while (tokenizer.hasMoreTokens()) {
			token = tokenizer.nextToken();
			if (ele != null) {
				ele = ele.getFirstChildWithName(new QName("", token, ""));
			} else {
				break;
			}
		}
		if (ele != null) {
			ele.getFirstOMChild().detach();
			ele.setText(token);
		}
	}

	private String getKey(Stack<String> nameStack) {
		StringBuffer key = new StringBuffer();
		for (int i = 0; i < nameStack.size(); i++) {
			String name = nameStack.elementAt(i);
			key.append(name).append(".");
		}
		key.deleteCharAt(key.lastIndexOf("."));

		return key.toString();
	}

	private boolean elementHasText(OMElement element) {
		String text = element.getText();
		return text != null && text.trim().length() != 0;
	}

	/**
	 * There can be multiple objects with the same key. This will return the
	 * first String from them
	 * 
	 * @param key
	 *            the search key
	 * 
	 * @return value corresponding to the given key
	 */
	public String getFirstProperty(String key) {
		List<String> value = configuration.get(key);
		if (value == null) {
			return null;
		}
		return value.get(0);
	}

	/**
	 * There can be multiple object corresponding to the same object.
	 * 
	 * @param key
	 *            the search key
	 * 
	 * @return the properties corresponding to the <code>key</code>
	 */
	public String[] getProperties(String key) {
		List<String> values = configuration.get(key);
		if (values == null) {
			return new String[0];
		}
		return values.toArray(new String[values.size()]);
	}

	/**
	 * Method to retrieve the Configuration as an XML Document.
	 * 
	 * @return DOM element containing server configuration.
	 */
	public Element getDocumentElement() {
		try {
			return toDOM(documentXML);
		} catch (Exception e) {
			log.error("Cannot get ServerConfiguration document element", e);
			return null;
		}
	}

	public OMElement getDocumentOMElement() {
		return carbonXmlElement;
	}

	private OMElement getDocumentElementInternal() {
		try {
			return (new StAXOMBuilder(new ByteArrayInputStream(
					documentXML.getBytes()))).getDocumentElement();
		} catch (XMLStreamException e) {
			log.error("Cannot get ServerConfiguration document element", e);
			return null;
		}
	}

	/**
	 * Converts a given OMElement to a DOM Element.
	 * 
	 * @param element
	 *            the OM element to be converted to DOM.
	 * 
	 * @return Returns Element.
	 * @throws Exception
	 *             if the operation failed.
	 */
	private static Element toDOM(OMElement element) throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		element.serialize(outputStream);
		ByteArrayInputStream inputStream = new ByteArrayInputStream(
				outputStream.toByteArray());

		DocumentBuilderFactory factory = getSecuredDocumentBuilder();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(inputStream)
				.getDocumentElement();
	}

	/**
	 * Converts a given OMElement to a DOM Element.
	 *
	 * @param elementStr the OM element to be converted to DOM.
	 *
	 * @return Returns Element.
	 * @throws Exception
	 *             if the operation failed.
	 */
	private static Element toDOM(String elementStr) throws Exception {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(elementStr.getBytes());
		DocumentBuilderFactory factory = getSecuredDocumentBuilder();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(inputStream).getDocumentElement();
	}

	private static DocumentBuilderFactory getSecuredDocumentBuilder() {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setXIncludeAware(false);
		dbf.setExpandEntityReferences(false);
		try {
			dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
			dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
			dbf.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
		} catch (ParserConfigurationException e) {
			log.error(
					"Failed to load XML Processor Feature " + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE + " or " +
							Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE + " or " + Constants.LOAD_EXTERNAL_DTD_FEATURE);
		}

		SecurityManager securityManager = new SecurityManager();
		securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
		dbf.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY, securityManager);

		return dbf;
	}

	protected boolean isProtectedToken(String key) {
		return secretResolver != null && secretResolver.isInitialized()
				&& secretResolver.isTokenProtected("Carbon." + key);
	}

	protected String getProtectedValue(String key) {
		return secretResolver.resolve("Carbon." + key);
	}

	public String getResolvedValue(String key) {
		return secretResolver.resolve(key);
	}

	/**
	 * Take the WSO2 server version from the carbon.xml file.
	 *
	 * @return WSO2 server version.
	 */
	public String getServerVersion() {
	    return getFirstProperty(SERVER_VERSION);
	}
}
