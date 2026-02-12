/**
 *  Copyright (c) 2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.micro.integrator.ndatasource.core;

import java.io.File;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.wso2.micro.integrator.ndatasource.common.DataSourceConstants;
import org.wso2.micro.integrator.ndatasource.common.DataSourceException;
import org.wso2.micro.integrator.ndatasource.common.spi.DataSourceReader;
import org.wso2.micro.integrator.ndatasource.core.utils.DataSourceUtils;
import org.wso2.micro.integrator.ndatasource.core.internal.DataSourceServiceComponent;
import org.wso2.micro.core.Constants;
import org.wso2.micro.core.context.CarbonContextDataHolder;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;

/**
 * This class contains the functionality in managing the data sources.
 */
public class DataSourceManager {

	private static Log log = LogFactory.getLog(DataSourceManager.class);
	
	private static DataSourceManager
            instance = new DataSourceManager();
	
	private Map<Integer, DataSourceRepository> dsRepoMap;

	private Map<String, DataSourceReader> dsReaders;

	private Map<Integer, Boolean> userDSRepoInitMap;

	public DataSourceManager() {
		this.dsReaders = new HashMap<String, DataSourceReader>();
		this.dsRepoMap = new ConcurrentHashMap<Integer, DataSourceRepository>();
		this.userDSRepoInitMap = new ConcurrentHashMap<Integer, Boolean>();
	}

	public static DataSourceManager getInstance() {
		return instance;
	}


    public DataSourceRepository getDataSourceRepository() throws DataSourceException {
		int tenantId = Constants.SUPER_TENANT_ID;
		return this.getDataSourceRepository(tenantId);
	}

	private synchronized DataSourceRepository getDataSourceRepository(int tenantId)
			throws DataSourceException {
		DataSourceRepository dsRepo = this.dsRepoMap.get(tenantId);
		if (dsRepo == null) {
			dsRepo = DataSourceServiceComponent.getNewTenantDataSourceRepository(tenantId);
			this.dsRepoMap.put(tenantId, dsRepo);
		}
		return dsRepo;
	}


    private synchronized void removeDataSourceRepository(int tenantId) {
		this.dsRepoMap.remove(tenantId);
		this.userDSRepoInitMap.remove(tenantId);
		if (log.isDebugEnabled()) {
			log.debug("Data source repository removed for tenant: " + tenantId);
		}
	}

	/**
	 * Initializes user data sources of a specific tenant.
	 * @param tenantId The tenant id of the tenant to be initialized
	 * @throws DataSourceException
	 */
	public void initTenant(int tenantId) throws DataSourceException {
		if (!this.isDSRepoUserDSInitialized(tenantId)) {
		    this.getDataSourceRepository(tenantId).initRepository();
		    this.userDSRepoInitMap.put(tenantId, true);
		}
	}

	private synchronized boolean isDSRepoUserDSInitialized(int tenantId) {
		Boolean result = this.userDSRepoInitMap.get(tenantId);
		return result != null ? result : false;
	}

	/**
	 * Unloads the user data sources from a specific tenant.
	 * @param tenantId The tenant id of the tenant to be unloaded
	 * @throws DataSourceException
	 */
	public void unloadTenant(int tenantId) throws DataSourceException {
		this.getDataSourceRepository(tenantId).unregisterAllUserDataSources();
		this.removeDataSourceRepository(tenantId);
	}

	public List<String> getDataSourceTypes() throws DataSourceException {
		if (this.dsReaders == null) {
			throw new DataSourceException("The data source readers are not initialized yet");
		}
		return new ArrayList<String>(this.dsReaders.keySet());
	}

	public DataSourceReader getDataSourceReader(String dsType) throws DataSourceException {
		if (this.dsReaders == null) {
			throw new DataSourceException("The data source readers are not initialized yet");
		}
		return this.dsReaders.get(dsType);
	}

	private void addDataSourceProviders(List<String> providers) throws DataSourceException {
		if (providers == null) {
			return;
		}
		DataSourceReader tmpReader;
		for (String provider : providers) {
			try {
				tmpReader = (DataSourceReader) Class.forName(provider).newInstance();
				this.dsReaders.put(tmpReader.getType(), tmpReader);
			} catch (Exception e) {
				throw new DataSourceException("Error in loading data source provider: " +
			            e.getMessage(), e);
			}
		}
	}

	/**
	 * Initializes the system data sources, i.e. /repository/conf/datasources/*-datasources.xml.
	 * @throws DataSourceException
	 */
	public void initSystemDataSources() throws DataSourceException {
		try {
			CarbonContextDataHolder dataHolder = new CarbonContextDataHolder();
			dataHolder.testCarbonStatic();
			String dataSourcesDir = MicroIntegratorBaseUtils.getCarbonConfigDirPath() + File.separator +
					DataSourceConstants.DATASOURCES_DIRECTORY_NAME;
			File masterDSFile = new File(dataSourcesDir + File.separator +
					DataSourceConstants.MASTER_DS_FILE_NAME);
			/* initialize the master data sources first */
			if (masterDSFile.exists()) {
			    this.initSystemDataSource(masterDSFile);
			}
			/* then rest of the system data sources */
			File dataSourcesFolder = new File(dataSourcesDir);
			for (File sysDSFile : dataSourcesFolder.listFiles()) {
				if (sysDSFile.getName().endsWith(DataSourceConstants.SYS_DS_FILE_NAME_SUFFIX)
						&& !sysDSFile.getName().equals(DataSourceConstants.MASTER_DS_FILE_NAME)) {
					this.initSystemDataSource(sysDSFile);
				}
			}
		} catch (Exception e) {
			throw new DataSourceException("Error in initializing system data sources: " +
		            e.getMessage(), e);
		}
	}

	private void initSystemDataSource(File sysDSFile) throws DataSourceException {
		try {
			OMElement doc = DataSourceUtils.convertToOMElement(sysDSFile);
			DataSourceUtils.secureResolveOMElement(doc);

			// Parse SystemDataSourcesConfiguration manually
			SystemDataSourcesConfiguration sysDS = parseSystemDataSourcesConfiguration(doc);

		    this.addDataSourceProviders(sysDS.getProviders());
		    DataSourceRepository dsRepo = this.getDataSourceRepository(
				    Constants.SUPER_TENANT_ID);
		    for (DataSourceMetaInfo dsmInfo : sysDS.getDataSources()) {
		    	dsmInfo.setSystem(true);
		    	dsRepo.addDataSource(dsmInfo);
		    }
		} catch (Exception e) {
			throw new DataSourceException("Error in initializing system data sources at '" +
		            sysDSFile.getAbsolutePath() + "' - " + e.getMessage(), e);
		}
	}

	/**
	 * Parse SystemDataSourcesConfiguration from OMElement.
	 *
	 * @param doc - datasources-configuration root element
	 * @return SystemDataSourcesConfiguration object
	 */
	private SystemDataSourcesConfiguration parseSystemDataSourcesConfiguration(OMElement doc) {
		SystemDataSourcesConfiguration config = new SystemDataSourcesConfiguration();

		Iterator<OMElement> elements = doc.getChildElements();
		while (elements.hasNext()) {
			OMElement element = elements.next();
			String localName = element.getLocalName();

			if ("providers".equals(localName)) {
				config.setProviders(parseProviders(element));
			} else if ("datasources".equals(localName)) {
				config.setDataSources(parseDataSources(element));
			}
		}

		return config;
	}

	/**
	 * Parse providers list from OMElement.
	 *
	 * @param providersElement - providers wrapper element
	 * @return List of provider class names
	 */
	private List<String> parseProviders(OMElement providersElement) {
		List<String> providers = new ArrayList<String>();

		Iterator<OMElement> providerElements = providersElement.getChildElements();
		while (providerElements.hasNext()) {
			OMElement providerElement = providerElements.next();
			if ("provider".equals(providerElement.getLocalName())) {
				String providerClassName = providerElement.getText();
				if (providerClassName != null && !providerClassName.trim().isEmpty()) {
					providers.add(providerClassName.trim());
				}
			}
		}

		return providers;
	}

	/**
	 * Parse datasources list from OMElement.
	 *
	 * @param datasourcesElement - datasources wrapper element
	 * @return List of DataSourceMetaInfo objects
	 */
	private List<DataSourceMetaInfo> parseDataSources(OMElement datasourcesElement) {
		List<DataSourceMetaInfo> dataSources = new ArrayList<DataSourceMetaInfo>();

		Iterator<OMElement> datasourceElements = datasourcesElement.getChildElements();
		while (datasourceElements.hasNext()) {
			OMElement datasourceElement = datasourceElements.next();
			if ("datasource".equals(datasourceElement.getLocalName())) {
				DataSourceMetaInfo dsmInfo = parseDataSourceMetaInfo(datasourceElement);
				dataSources.add(dsmInfo);
			}
		}

		return dataSources;
	}

	/**
	 * Parse DataSourceMetaInfo from OMElement.
	 *
	 * @param datasourceElement - datasource element
	 * @return DataSourceMetaInfo object
	 */
	private DataSourceMetaInfo parseDataSourceMetaInfo(OMElement datasourceElement) {
		DataSourceMetaInfo dataSourceMetaInfo = new DataSourceMetaInfo();

		Iterator<OMElement> elements = datasourceElement.getChildElements();
		while (elements.hasNext()) {
			OMElement element = elements.next();
			String localName = element.getLocalName();

			if ("name".equals(localName)) {
				dataSourceMetaInfo.setName(element.getText());
			} else if ("description".equals(localName)) {
				dataSourceMetaInfo.setDescription(element.getText());
			} else if ("jndiConfig".equals(localName)) {
				dataSourceMetaInfo.setJndiConfig(parseJNDIConfig(element));
			} else if ("definition".equals(localName)) {
				dataSourceMetaInfo.setDefinition(parseDataSourceDefinition(element));
			}
		}

		return dataSourceMetaInfo;
	}

	/**
	 * Parse JNDI configuration from OMElement.
	 *
	 * @param jndiElement - jndiConfig element
	 * @return JNDIConfig object
	 */
	private JNDIConfig parseJNDIConfig(OMElement jndiElement) {
		JNDIConfig jndiConfig = new JNDIConfig();

		// Check for useDataSourceFactory attribute
		OMAttribute useDataSourceFactoryAttr = jndiElement.getAttribute(
				new javax.xml.namespace.QName("useDataSourceFactory"));
		if (useDataSourceFactoryAttr != null) {
			jndiConfig.setUseDataSourceFactory(Boolean.parseBoolean(useDataSourceFactoryAttr.getAttributeValue()));
		}

		// Parse child elements
		Iterator<OMElement> elements = jndiElement.getChildElements();
		List<JNDIConfig.EnvEntry> envEntries = new ArrayList<JNDIConfig.EnvEntry>();

		while (elements.hasNext()) {
			OMElement element = elements.next();
			String localName = element.getLocalName();

			if ("name".equals(localName)) {
				jndiConfig.setName(element.getText());
			} else if ("environment".equals(localName)) {
				// Parse environment properties
				Iterator<OMElement> properties = element.getChildElements();
				while (properties.hasNext()) {
					OMElement property = properties.next();
					if ("property".equals(property.getLocalName())) {
						JNDIConfig.EnvEntry envEntry = parseEnvEntry(property);
						envEntries.add(envEntry);
					}
				}
			}
		}

		if (!envEntries.isEmpty()) {
			jndiConfig.setEnvironment(envEntries.toArray(new JNDIConfig.EnvEntry[0]));
		}

		return jndiConfig;
	}

	/**
	 * Parse environment entry from OMElement.
	 *
	 * @param propertyElement - property element
	 * @return EnvEntry object
	 */
	private JNDIConfig.EnvEntry parseEnvEntry(OMElement propertyElement) {
		JNDIConfig.EnvEntry envEntry = new JNDIConfig.EnvEntry();

		// Parse name attribute
		OMAttribute nameAttr = propertyElement.getAttribute(new javax.xml.namespace.QName("name"));
		if (nameAttr != null) {
			envEntry.setName(nameAttr.getAttributeValue());
		}

		// Parse encrypted attribute (default is true)
		OMAttribute encryptedAttr = propertyElement.getAttribute(new javax.xml.namespace.QName("encrypted"));
		if (encryptedAttr != null) {
			envEntry.setEncrypted(Boolean.parseBoolean(encryptedAttr.getAttributeValue()));
		}

		// Parse value
		envEntry.setValue(propertyElement.getText());

		return envEntry;
	}

	/**
	 * Parse DataSource definition from OMElement.
	 *
	 * @param definitionElement - definition element
	 * @return DataSourceDefinition object
	 */
	private DataSourceMetaInfo.DataSourceDefinition parseDataSourceDefinition(OMElement definitionElement) {
		DataSourceMetaInfo.DataSourceDefinition definition = new DataSourceMetaInfo.DataSourceDefinition();

		// Parse type attribute
		OMAttribute typeAttr = definitionElement.getAttribute(new javax.xml.namespace.QName("type"));
		if (typeAttr != null) {
			definition.setType(typeAttr.getAttributeValue());
		}

		// Parse configuration element (the actual datasource configuration XML)
		Iterator<OMElement> elements = definitionElement.getChildElements();
		if (elements.hasNext()) {
			OMElement configElement = elements.next();
			// Convert OMElement to DOM Element by converting to String first
			String xmlString = configElement.toString();
			Element domElement = DataSourceUtils.stringToElement(xmlString);
			definition.setDsXMLConfiguration(domElement);
		}

		return definition;
	}

}
