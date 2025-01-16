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

import org.apache.axis2.clustering.ClusteringAgent;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.wso2.micro.core.util.ConfigurationContextService;
import org.wso2.micro.integrator.ndatasource.common.DataSourceConstants.DataSourceStatusModes;
import org.wso2.micro.integrator.ndatasource.common.DataSourceException;
import org.wso2.micro.integrator.ndatasource.common.spi.DataSourceReader;
import org.wso2.micro.integrator.ndatasource.core.internal.DataSourceServiceComponent;
import org.wso2.micro.integrator.ndatasource.core.utils.DataSourceUtils;

import java.util.HashMap;
import java.util.Map;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * This class represents the repository which is used to hold the data sources.
 */
public class DataSourceRepository {
	
	private static Log log = LogFactory.getLog(DataSourceRepository.class);
	
	private int tenantId;
	
	private Map<String, CarbonDataSource> dataSources;

	private Marshaller dsmMarshaller;

	private Unmarshaller dsmUnmarshaller;

	public DataSourceRepository(int tenantId) throws DataSourceException {
		this.tenantId = tenantId;
		this.dataSources = new HashMap<String, CarbonDataSource>();
		try {
		    JAXBContext ctx = JAXBContext.newInstance(DataSourceMetaInfo.class);
		    this.dsmMarshaller = ctx.createMarshaller();
		    this.dsmUnmarshaller = ctx.createUnmarshaller();
		} catch (JAXBException e) {
			throw new DataSourceException(
					"Error creating data source meta info marshaller/unmarshaller: "
			            + e.getMessage(), e);
		}
	}

	public int getTenantId() {
		return tenantId;
	}

	/**
	 * Initializes the data sources repository. This method must be
	 * called after the registry service is available to be used.
	 * @throws DataSourceException
	 */
	public void initRepository() throws DataSourceException {
		this.refreshAllUserDataSources();
	}

//	private synchronized Registry getRegistry() throws DataSourceException {
////		if (this.registry == null) {
////		    this.registry = DataSourceUtils.getConfRegistryForTenant(this.getTenantId());
////		    if (log.isDebugEnabled()) {
////		        log.debug("[datasources] Retrieving the governance registry for tenant: " +
////		                this.getTenantId());
////		    }
////		}
////		return registry;
////	}

	private String resourceNameFromPath(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}

	/**
	 * Reloads all the data sources from the repository.
	 * @throws DataSourceException
	 */
	public void refreshAllUserDataSources() throws DataSourceException {
		this.updateAllUserDataSource(false);
	}

	/**
	 * Unregisters all the data sources from the repository.
	 * @throws DataSourceException
	 */
	public void unregisterAllUserDataSources() throws DataSourceException {
		this.updateAllUserDataSource(true);
	}

	private void updateAllUserDataSource(boolean unregister) throws DataSourceException {
		try {
//			if (this.getRegistry().resourceExists(
//					DataSourceConstants.DATASOURCES_REPOSITORY_BASE_PATH)) {
//				Collection dsCollection = (Collection) this.getRegistry().get(
//						DataSourceConstants.DATASOURCES_REPOSITORY_BASE_PATH);
//				String[] dsmPaths = dsCollection.getChildren();
//				for (String dsmPath : dsmPaths) {
//					try {
//					    this.updateDataSource(this.resourceNameFromPath(dsmPath), unregister);
//					} catch (DataSourceException e) {
//						log.error("Error in updating data source [remove:" + unregister +
//								"] at path '" + dsmPath + "': " + e.getMessage(), e);
//					}
//				}
//			}
		} catch (Exception e) {
			throw new DataSourceException(
					"Error in getting all data sources from repository: " + e.getMessage(), e);
		}
	}

	/**
	 * Reloads only a specific given data source.
	 * @param dsName The name of the data source to be loaded.
	 * @throws DataSourceException
	 */
	public void refreshUserDataSource(String dsName) throws DataSourceException {
		if (log.isDebugEnabled()) {
			log.debug("Refreshing data source: " + dsName);
		}
//		this.updateDataSource(dsName, false);
	}

//	private synchronized void updateDataSource(String dsName,
//			boolean unregister) throws DataSourceException {
//		String dsmPath = DataSourceConstants.DATASOURCES_REPOSITORY_BASE_PATH + "/" + dsName;
//		try {
//			DataSourceMetaInfo dsmInfo = this.getDataSourceMetaInfoFromRegistryPath(dsmPath);
//			CarbonDataSource currentCDS = this.getDataSource(dsName);
//			DataSourceMetaInfo currentDsmInfo = null;
//			if (currentCDS != null) {
//				currentDsmInfo = currentCDS.getDSMInfo();
//			}
//			if (unregister) {
//				this.unregisterDataSource(dsName);
//			} else {
//				if (DataSourceUtils.nullAllowEquals(dsmInfo, currentDsmInfo)) {
//					if (log.isDebugEnabled()) {
//						log.debug("No update change for data source: " + dsName);
//					}
//					return;
//				}
//				if (dsmInfo != null) {
//					this.registerDataSource(dsmInfo);
//				} else {
//					this.unregisterDataSource(dsName);
//				}
//			}
//		} catch (Exception e) {
//			throw new DataSourceException("Error in updating data source '" + dsName +
//					"' from registry [remove:" + unregister +"]: " + e.getMessage(), e);
//		}
//	}

	private Object createDataSourceObject(DataSourceMetaInfo dsmInfo, boolean isUseDataSourceFactory)
			throws DataSourceException {
		DataSourceReader dsReader = DataSourceManager.getInstance().getDataSourceReader(
				dsmInfo.getDefinition().getType());
		if (dsReader == null) {
			throw new DataSourceException("A data source reader cannot be found for the type '" +
		            dsmInfo.getDefinition().getType() + "'");
		}
		/* sets the current data source's (tenantId + ":" + name) as a thread local value
		 * so it can be read by data source readers */
		DataSourceUtils.setCurrentDataSourceId(this.getTenantId() + ":" + dsmInfo.getName());
		return dsReader.createDataSource(DataSourceUtils.elementToString(
					(Element) dsmInfo.getDefinition().getDsXMLConfiguration()), isUseDataSourceFactory);
	}

	private Context lookupJNDISubContext(Context context, String jndiName)
			throws DataSourceException {
		try {
			Object obj = context.lookup(jndiName);
			if (!(obj instanceof Context)) {
				throw new DataSourceException("Non JNDI context already exists at '" +
			            context + "/" + jndiName);
			}
			return (Context) obj;
		} catch (NamingException e) {
			return null;
		}
	}

	private void checkAndCreateJNDISubContexts(Context context, String jndiName)
			throws DataSourceException {
		String[] tokens = jndiName.split("/");
		Context tmpCtx;
		String token;
		for (int i = 0; i < tokens.length - 1; i++) {
			token = tokens[i];
			tmpCtx = (Context) this.lookupJNDISubContext(context, token);
			if (tmpCtx == null) {
				try {
				    tmpCtx = context.createSubcontext(token);
				} catch (NamingException e) {
					throw new DataSourceException(
							"Error in creating JNDI subcontext '" + context +
							"/" + token + ": " + e.getMessage(), e);
				}
			}
			context = tmpCtx;
		}
	}

	private void registerJNDI(DataSourceMetaInfo dsmInfo, Object dsObject)
			throws DataSourceException {
		try {
//			PrivilegedCarbonContext.startTenantFlow();
//			PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(this.getTenantId());
		    JNDIConfig jndiConfig = dsmInfo.getJndiConfig();
		    if (jndiConfig == null) {
			    return;
		    }
		    InitialContext context;
		    try {
		        context = new InitialContext(jndiConfig.extractHashtableEnv());
		    } catch (NamingException e) {
			    throw new DataSourceException("Error creating JNDI initial context: " +
		                e.getMessage(), e);
		    }
		    this.checkAndCreateJNDISubContexts(context, jndiConfig.getName());

		    try {
			    context.rebind(jndiConfig.getName(), dsObject);
			} catch (NamingException e) {
		    	throw new DataSourceException("Error in binding to JNDI with name '" +
		                jndiConfig.getName() + "' - " + e.getMessage(), e);
			}
		} finally {
//			PrivilegedCarbonContext.endTenantFlow();
		}
	}

	private void unregisterJNDI(DataSourceMetaInfo dsmInfo) {
		try {
//			PrivilegedCarbonContext.startTenantFlow();
//			PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(this.getTenantId());
			JNDIConfig jndiConfig = dsmInfo.getJndiConfig();
		    if (jndiConfig == null) {
			    return;
		    }
			try {
				InitialContext context = new InitialContext(jndiConfig.extractHashtableEnv());
				context.unbind(jndiConfig.getName());
		    } catch (NamingException e) {
			    log.error("Error in unregistering JNDI name: " +
		                jndiConfig.getName() + " - " + e.getMessage(), e);
		    }
		} finally {
//			PrivilegedCarbonContext.endTenantFlow();
		}
	}

//	private void removePersistedDataSource(String dsName) throws DataSourceException {
//		try {
//			this.getRegistry().beginTransaction();
//			String path = DataSourceConstants.DATASOURCES_REPOSITORY_BASE_PATH + "/" + dsName;
//			if (this.getRegistry().resourceExists(path)) {
//		        this.getRegistry().delete(path);
//			}
//		    this.getRegistry().commitTransaction();
//		} catch (Exception e) {
//			try {
//				this.getRegistry().rollbackTransaction();
//			} catch (RegistryException e1) {
//				log.error("Error in rollback transaction in removing data source:" +
//			            e1.getMessage(), e1);
//			}
//			throw new DataSourceException("Error in removing data source: " + dsName +
//					" - " + e.getMessage(), e);
//		}
//	}

//	private void persistDataSource(DataSourceMetaInfo dsmInfo) throws DataSourceException {
//		try {
//			Element element = DataSourceUtils.
//					convertDataSourceMetaInfoToElement(dsmInfo, this.getDSMMarshaller());
//			DataSourceUtils.secureSaveElement(element);
//
//			Resource resource = this.getRegistry().newResource();
//			resource.setContentStream(DataSourceUtils.elementToInputStream(element));
//			this.getRegistry().put(DataSourceConstants.DATASOURCES_REPOSITORY_BASE_PATH + "/" +
//			        dsmInfo.getName(), resource);
//		} catch (Exception e) {
//			throw new DataSourceException("Error in persisting data source: " +
//		            dsmInfo.getName() + " - " + e.getMessage(), e);
//		}
//	}

	private void unregisterDataSource(String dsName) {
		CarbonDataSource cds = this.getDataSource(dsName);
		if (cds == null) {
			return;
		}
		if (log.isDebugEnabled()) {
			log.debug("Unregistering data source: " + dsName);
		}
		this.unregisterJNDI(cds.getDSMInfo());
		this.dataSources.remove(dsName);
	}

	private synchronized void registerDataSource(DataSourceMetaInfo dsmInfo) throws DataSourceException {
		/* if a data source is already registered with the given name, unregister it first */
		CarbonDataSource currentCDS = this.getDataSource(dsmInfo.getName());
		if (currentCDS != null) {
			/* if the data source is a system data source, throw exception */
			if (dsmInfo.isSystem()) {
				throw new DataSourceException("System datasource " + dsmInfo.getName() + "can not be updated.");
			}
			this.unregisterDataSource(currentCDS.getDSMInfo().getName());
		}
		if (log.isDebugEnabled()) {
			log.debug("Registering data source: " + dsmInfo.getName());
		}
		Object dsObject = null;
		boolean isDataSourceFactoryReference = false;
		DataSourceStatus dsStatus;
		try {
		    JNDIConfig jndiConfig = dsmInfo.getJndiConfig();
		    if (jndiConfig != null) {
			isDataSourceFactoryReference = jndiConfig.isUseDataSourceFactory();
		    }
		    dsObject = this.createDataSourceObject(dsmInfo, isDataSourceFactoryReference);
		    this.registerJNDI(dsmInfo, dsObject);
		    dsStatus = new DataSourceStatus(DataSourceStatusModes.ACTIVE, null);
		} catch (Exception e) {
			String msg = e.getMessage();
			log.error(msg, e);
			dsStatus = new DataSourceStatus(DataSourceStatusModes.ERROR, msg);
		}
		/* Creating DataSource object , if dsObject is a Reference */
		if (isDataSourceFactoryReference) {
			dsObject = this.createDataSourceObject(dsmInfo, false);
		}
		CarbonDataSource cds = new CarbonDataSource(dsmInfo, dsStatus, dsObject);
		this.dataSources.put(cds.getDSMInfo().getName(), cds);
	}

	private void notifyClusterDSChange(String dsName) throws DataSourceException {
		if (log.isDebugEnabled()) {
			log.debug("Notifying cluster DS change: " + dsName);
		}
		ConfigurationContextService configCtxService = DataSourceServiceComponent.
		        getConfigContextService();
		if (configCtxService == null) {
			throw new DataSourceException("ConfigurationContextService not available " +
					"for notifying the cluster");
		}
		ConfigurationContext configCtx = configCtxService.getServerConfigContext();
		ClusteringAgent agent = configCtx.getAxisConfiguration().getClusteringAgent();
		if (log.isDebugEnabled()) {
			log.debug("Clustering Agent: " + agent);
		}
		if (agent != null) {
			DataSourceStatMessage msg = new DataSourceStatMessage();
			msg.setTenantId(this.getTenantId());
			msg.setDsName(dsName);
			try {
				agent.sendMessage(msg, true);
			} catch (ClusteringFault e) {
				throw new DataSourceException("Error in sending out cluster message: " +
						e.getMessage(), e);
			}
		}
	}

//	private DataSourceMetaInfo getDataSourceMetaInfoFromRegistryPath(String path)
//			throws DataSourceException, Exception {
//        InputStream in = null;
//		try {
//		    this.getRegistry().beginTransaction();
//            if (this.getRegistry().resourceExists(path)) {
//			    Resource resource;
//			    try {
//			    	resource = this.getRegistry().get(path);
//			    } catch (ResourceNotFoundException e) {
//			    	/* this step is as a precaution, because sometimes even though the
//			    	 * resource is deleted, "resourceExists" returns true */
//					return null;
//				}
//			    in = resource.getContentStream();
//                Document doc = DataSourceUtils.convertToDocument(in);
//                /* only super tenant will lookup secure vault information for system data sources,
//			     * others are not allowed to */
//                DataSourceUtils.secureResolveDocument(doc, false);
//                this.getRegistry().commitTransaction();
//			    return (DataSourceMetaInfo) this.getDSMUnmarshaller().unmarshal(doc);
//		    } else {
//			    return null;
//		    }
//		} catch (Exception e) {
//			this.getRegistry().rollbackTransaction();
//			throw e;
//		} finally {
//            if (in != null) {
//                in.close();
//            }
//        }
//	}

	private Unmarshaller getDSMUnmarshaller() {
		return dsmUnmarshaller;
	}

	private Marshaller getDSMMarshaller() {
		return dsmMarshaller;
	}

	/**
	 * Gets information about all the data sources.
	 * @return A list of all data sources
	 */
	public java.util.Collection<CarbonDataSource> getAllDataSources() {
		return dataSources.values();
	}

	/**
	 * Gets information about a specific given data source.
	 * @param dsName The name of the data source.
	 * @return The data source information
	 */
	public CarbonDataSource getDataSource(String dsName) {
		return this.dataSources.get(dsName);
	}
	
	/**
	 * Adds a new data source to the repository.
	 * @param dsmInfo The meta information of the data source to be added.
	 */
	public void addDataSource(DataSourceMetaInfo dsmInfo) throws DataSourceException {
		if (log.isDebugEnabled()) {
			log.debug("Adding data source: " + dsmInfo.getName());
		}
//        if (dsmInfo.isPersistable()) {
//		    this.persistDataSource(dsmInfo);
//		}
		this.registerDataSource(dsmInfo);
        if (dsmInfo.isPersistable()) {
		    this.notifyClusterDSChange(dsmInfo.getName());
		}
	}
	
	/**
	 * Unregisters and deletes the data source from the repository.
	 * @param dsName The data source name
	 */
	public void deleteDataSource(String dsName) throws DataSourceException {
		if (log.isDebugEnabled()) {
			log.debug("Deleting data source: " + dsName);
		}
		CarbonDataSource cds = this.getDataSource(dsName);
		if (cds == null) {
			throw new DataSourceException("Data source does not exist: " + dsName);
		}
		if (cds.getDSMInfo().isSystem()) {
			throw new DataSourceException("System data sources cannot be deleted: " + dsName);
		}
        this.unregisterDataSource(dsName);
        if (cds.getDSMInfo().isPersistable()) {
//            this.removePersistedDataSource(dsName);
            this.notifyClusterDSChange(dsName);
        }
	}
	
	/**
	 * Tests Connection of the data source
	 * @param dsmInfo The meta information of the data source to be tested.
	 */
	public boolean testDataSourceConnection(DataSourceMetaInfo dsmInfo) throws DataSourceException {
		if (log.isDebugEnabled()) {
			log.debug("Testing connection of data source: " + dsmInfo.getName());
		}
		DataSourceReader dsReader = DataSourceManager.getInstance().getDataSourceReader(
				dsmInfo.getDefinition().getType());
		try {
			return dsReader.testDataSourceConnection(DataSourceUtils.elementToString((Element)dsmInfo.getDefinition().getDsXMLConfiguration()));
		} catch (DataSourceException e) {
			log.error(e.getMessage(), e);
			throw e;
		}
		
	}

}
