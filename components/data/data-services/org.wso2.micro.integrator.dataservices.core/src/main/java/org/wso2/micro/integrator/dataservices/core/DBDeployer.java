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
package org.wso2.micro.integrator.dataservices.core;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.deployment.AbstractDeployer;
import org.apache.axis2.deployment.DeploymentEngine;
import org.apache.axis2.deployment.DeploymentErrorMsgs;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.apache.axis2.deployment.util.Utils;
import org.apache.axis2.description.AxisBinding;
import org.apache.axis2.description.AxisBindingMessage;
import org.apache.axis2.description.AxisBindingOperation;
import org.apache.axis2.description.AxisEndpoint;
import org.apache.axis2.description.AxisMessage;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.description.InOnlyAxisOperation;
import org.apache.axis2.description.InOutAxisOperation;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.description.WSDL2Constants;
import org.apache.axis2.description.java2wsdl.Java2WSDLConstants;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.i18n.Messages;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.axis2.wsdl.WSDLUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.neethi.Policy;
import org.apache.neethi.PolicyEngine;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.config.xml.FactoryUtils;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.mediators.Value;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.apache.ws.commons.schema.utils.NamespaceMap;
import org.jaxen.JaxenException;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.common.DBConstants.DBSFields;
import org.wso2.micro.integrator.dataservices.common.DBConstants.ResultTypes;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery.WithParam;
import org.wso2.micro.integrator.dataservices.core.description.config.Config;
import org.wso2.micro.integrator.dataservices.core.description.operation.Operation;
import org.wso2.micro.integrator.dataservices.core.description.query.Query;
import org.wso2.micro.integrator.dataservices.core.description.resource.Resource;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery;
import org.wso2.micro.integrator.dataservices.core.engine.CallableRequest;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.QueryParam;
import org.wso2.micro.integrator.dataservices.core.jmx.DataServiceInstance;
import org.wso2.micro.integrator.dataservices.core.jmx.DataServiceInstanceMBean;
import org.wso2.micro.integrator.dataservices.core.odata.ODataServiceHandler;
import org.wso2.micro.integrator.dataservices.core.odata.ODataServiceRegistry;
import org.wso2.micro.integrator.ndatasource.common.DataSourceConstants;
import org.wso2.micro.integrator.ndatasource.common.DataSourceException;
import org.wso2.micro.integrator.ndatasource.core.DataSourceManager;
import org.wso2.micro.core.util.CarbonUtils;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.InitialContext;
import javax.transaction.TransactionManager;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the custom Axis2 deployer used in deploying data-services .dbs files.
 */
public class DBDeployer extends AbstractDeployer {

    public static final String HTTP_TRANSPORT = "http";

	private final XMLInputFactory inputFactory = XMLInputFactory.newInstance();

	public static final String HTTPS_TRANSPORT = "https";

	private static final Log log = LogFactory.getLog(DBDeployer.class);

	/**
	 * Data Services directory name to be used in Axis2 service deployment
	 */
	public static final String DEPLOYMENT_FOLDER_NAME = "dataservices";

	/**
	 * Current Axis2 AxisConfiguration
	 */
	private AxisConfiguration axisConfig;

	/**
	 * Current Axis2 ConfigurationContext
	 */
	private ConfigurationContext configCtx;

	/**
	 * Data Services repository directory
	 */
	private String repoDir;

	/**
	 * Data Services file directory (i.e. '.dbs')
	 */
	private String extension;

	/**
	 * used for REST processing
	 */
	private Map<String, AxisOperation> httpLocationTable;
    private Map<Pattern, AxisOperation> httpLocationTableForResource;

    /** cached transaction manager instance */
    private static TransactionManager cachedTransactionManager = null;

	/**
	 * Regex for any vault expression.
	 */
	private static final String secureVaultRegex = "\\{(.*?):vault-lookup\\('(.*?)'\\)\\}";
	private static Pattern vaultLookupPattern = Pattern.compile(secureVaultRegex);

	public ConfigurationContext getConfigContext() {
		return configCtx;
	}

	/**
	 * Deploys a data service with the given deployment data.
	 */
	public void deploy(DeploymentFileData deploymentFileData)
			throws DeploymentException {
//        PrivilegedCarbonContext.getThreadLocalCarbonContext().setApplicationName(deploymentFileData.getName());
        /* If there's already a faulty service corresponding to this particular service,
           remove it */
        if (isFaultyService(deploymentFileData)) {
            this.axisConfig.removeFaultyService(deploymentFileData.getFile().getAbsolutePath());
        }

		String serviceHierarchy = Utils.getServiceHierarchy(
				deploymentFileData.getAbsolutePath(), this.repoDir);
        if (serviceHierarchy == null){
            serviceHierarchy = "";
        }

        /* state variable kept to check if the service was successfully deployed at the end */
		boolean successfullyDeployed = false;
		/* used to store the error message if there is a problem in deploying */
		String errorMessage = null;
		/* Axis2 service to be deployed */
		AxisService service = null;

		try {
			/* In the context of dataservices one service group will only contain one dataservice.
            *  Hence assigning the service group as the service group name */
            String serviceGroupName = serviceHierarchy +
                    this.getServiceNameFromDSContents(deploymentFileData.getFile());

			if (deploymentFileData.isVersionedDeployment()) {
				serviceGroupName = deploymentFileData.getArtifactIdentifier() + "/" + serviceGroupName;
			}

			if (DBUtils.isAvailableDSServiceGroup(axisConfig, serviceGroupName)) {
				throw new DataServiceFault("Data Service name is already exists. Please choose different name for \'" +
				                           this.getServiceNameFromDSContents(deploymentFileData.getFile()) +
				                           "\' data service.");
			}

			/* service active property */
			boolean serviceActive;

            AxisServiceGroup serviceGroup = new AxisServiceGroup();
            serviceGroup.setServiceGroupName(serviceGroupName);
            service = processService(deploymentFileData, serviceGroup, this.configCtx);
            service.setName(serviceHierarchy + service.getName());
            /* save original value */
            serviceActive = service.isActive();

            /* set transports */
            if(service.getExposedTransports().isEmpty()) {
                List<String> transports = new ArrayList<String>();
                transports.add(Constants.TRANSPORT_HTTP);
                transports.add(Constants.TRANSPORT_HTTPS);
                service.setExposedTransports(transports);
            }

			ArrayList<AxisService> services = new ArrayList<AxisService>();
            services.add(service);

            boolean secEnabled = this.handleSecurityProxy(deploymentFileData, service);

            DeploymentEngine.addServiceGroup(serviceGroup, services,
                    deploymentFileData.getFile().toURI().toURL(), deploymentFileData,
                    this.axisConfig);

            //Engage rampart module if security is enabled
            if (secEnabled) {
                service.engageModule(this.configCtx.getAxisConfiguration().getModule(
                        DBConstants.SECURITY_MODULE_NAME), this.configCtx.getAxisConfiguration());
            }

			/* restore original service active value */
			service.setActive(serviceActive);

			if (log.isDebugEnabled()) {
			    log.debug(Messages.getMessage(DeploymentErrorMsgs.DEPLOYING_WS,
			    		deploymentFileData.getName(), deploymentFileData.getAbsolutePath()));
		    }
            super.deploy(deploymentFileData);
			/* finished deploying successfully */
			successfullyDeployed = true;

		} catch (DataServiceFault e) {
			errorMessage = DBUtils.getStacktraceFromException(e);
			log.error(Messages.getMessage(DeploymentErrorMsgs.INVALID_SERVICE, deploymentFileData.getName()), e);
			/* if there is a request to re-schedule in the exception, do it .. */
			if (DBConstants.FaultCodes.CONNECTION_UNAVAILABLE_ERROR.equals(e.getCode())) {
				this.sheduleRedeploy(deploymentFileData, service);
			}
			DataService ds = e.getSourceDataService();
			try {
				/* only if the data service is available, for XML syntax based error in the dbs,
				 * we cannot get the data service */
				if (ds != null) {
				    ds.cleanup();
				}
			} catch (DataServiceFault e2) {
				log.warn("Error in data service cleanup: " + e2.getMessage(), e2);
			}
			throw new DeploymentException(Messages.getMessage(
					DeploymentErrorMsgs.INVALID_SERVICE,
					deploymentFileData.getName()), e);
		} catch (Throwable e) {
			errorMessage = DBUtils.getStacktraceFromException(e);
			log.error(Messages.getMessage(DeploymentErrorMsgs.INVALID_SERVICE, deploymentFileData.getName()), e);
			throw new DeploymentException(Messages.getMessage(
					DeploymentErrorMsgs.INVALID_SERVICE,
					deploymentFileData.getName()), e);
		} finally {
			if (!successfullyDeployed)	{
				String deploymentFilePath = deploymentFileData.getFile().getAbsolutePath();
				/* Register the faulty service */
				this.axisConfig.getFaultyServices().put(deploymentFilePath, errorMessage);
                try {
                	CarbonUtils.registerFaultyService(deploymentFilePath,
                    		DBConstants.DB_SERVICE_TYPE, configCtx);
                } catch (Exception e) {
                    log.error("Cannot register faulty service with Carbon", e);
                }
			}
		}
	}

    /**
     * Checks whether the service that is being deployed is already marked as a faulty service
     *
     * @param deploymentFileData    DeploymentFileData instance corresponding to the service being
     *                              deployed.
     * @return                      Boolean representing the existence of the service as a faulty
     *                              service
     */
    private boolean isFaultyService(DeploymentFileData deploymentFileData) {
        String faultyServiceFilePath = deploymentFileData.getFile().getAbsolutePath();
        AxisService faultyService = CarbonUtils.getFaultyService(faultyServiceFilePath, this.configCtx);
        return faultyService != null;
    }

    private String getServiceNameFromDSContents(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file.getAbsoluteFile())) {
            StAXOMBuilder builder = new StAXOMBuilder(fis);
            OMElement serviceEl = builder.getDocumentElement();
            String serviceName = serviceEl.getAttributeValue(new QName(DBSFields.NAME));
            builder.close();
            if (DBUtils.isEmptyString(serviceName)) {
                throw new DataServiceFault("Service group cannot be determined for the data service at '"
                        + file.getAbsolutePath() + "'");
            }
            return serviceName;
        }
    }

	/**
	 * Creates a timer with a one minute delay, for re-deploying a data service.
	 */
	private void sheduleRedeploy(DeploymentFileData deploymentFileData, AxisService service) {
		Runnable faultyServiceRectifier = new FaultyServiceRectifier(service,
				deploymentFileData, configCtx);
		/* Retry in one minute */
		long retryIn = 1000 * 60;
		DBUtils.scheduleTask(faultyServiceRectifier, retryIn);
	}

	/**
	 * Initializes the deployer.
	 */
	public void init(ConfigurationContext configCtx) {
		this.configCtx = configCtx;
		DataHolder.getInstance().setConfigurationContext(configCtx);
		this.axisConfig = this.configCtx.getAxisConfiguration();
		/* init is called after the setDirectory is called so setting the
		 * repoDir and the extension here. */
		configCtx.setProperty(DBConstants.DB_SERVICE_REPO, this.repoDir);
		configCtx.setProperty(DBConstants.DB_SERVICE_EXTENSION, this.extension);
		configCtx.setProperty(DBConstants.DB_SERVICE_DEPLOYER, this);

		/* retrieve tenant id */
		int tid = org.wso2.micro.core.Constants.SUPER_TENANT_ID;
//        try {
//        	tid = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
//        } catch (ExceptionInInitializerError e) {
//        	/* workaround for unit test failures */
//        	tid = org.wso2.micro.core.Constants.SUPER_TENANT_ID;
//        }

//        try {
//        	/* load tenant registry */
//        	DataServicesDSComponent.getTenantRegistryLoader().loadTenantRegistry(tid);
//        } catch (Exception e) {
//        	/*ignore*/
//        }

		/* transaction manager looked up and cached for later use, rather than always doing the JNDI lookup */
        this.doExtractTransactionManager();

		// Avoid initializing DataSourceManager if the server is in NonRegistryMode
		if (!Boolean.parseBoolean(System.getProperty("NonRegistryMode"))) {
			/* data sources component tenant initialized, this is done here as a precaution to
			 * make sure that the tenant's data sources are initialized before the data services
			 * are deployed, this uncertainty comes because we cannot predict the order which
			 * the Axis2ConfigurationContext observers will be called */
			try {
				DataSourceManager.getInstance().initTenant(tid);
			} catch (DataSourceException e) {
				log.error("Error in intializing Carbon data sources for tenant: " +
						tid + " from data services");
			} catch (NoClassDefFoundError e) {
				//workaround for unit test failures
			} catch (NoSuchMethodError e) {
				//workaround for unit test failures
			}
		}
	}

    private void doExtractTransactionManager() {
    	if (cachedTransactionManager != null) {
    		return;
    	}
    	try {
			Object txObj = InitialContext.doLookup(
					DBConstants.STANDARD_USER_TRANSACTION_JNDI_NAME);
			if (txObj instanceof TransactionManager) {
				cachedTransactionManager = (TransactionManager) txObj;
			}
		} catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug("Cannot find transaction manager at: "
						+ DBConstants.STANDARD_USER_TRANSACTION_JNDI_NAME, e);
			}
			/* ignore, move onto next step */
		}
		if (cachedTransactionManager == null) {
			try {
				cachedTransactionManager = InitialContext.doLookup(
						DBConstants.STANDARD_TRANSACTION_MANAGER_JNDI_NAME);
			} catch (Exception e) {
				if (log.isDebugEnabled()) {
					log.debug("Cannot find transaction manager at: " +
				         DBConstants.STANDARD_TRANSACTION_MANAGER_JNDI_NAME, e);
				}
				/* we'll do the lookup later, maybe user provided a custom JNDI name */
			}
		}
    }

    public static TransactionManager getCachedTransactionManager() {
    	return cachedTransactionManager;
    }

	public void setDirectory(String repoDir) {
		this.repoDir = repoDir;
	}

	public String getRepoDir() {
		return repoDir;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public String getExtension() {
	    return extension;
	}

	private DataService getDataServiceByServicePath(String servicePath) throws Exception {
		Parameter tmpParam;
		DataService tmpDS;
		String canonicalServicePath = new File(servicePath).getCanonicalPath();
		for (AxisService axisService : this.axisConfig.getServices().values()) {
			tmpParam = axisService.getParameter(DBConstants.DATA_SERVICE_OBJECT);
			if (tmpParam != null) {
				tmpDS = (DataService) tmpParam.getValue();
				if (new File(tmpDS.getDsLocation()).getCanonicalPath().equals(
						canonicalServicePath)) {
			    	return tmpDS;
			    }
			}
		}
		//throw new DataServiceFault("Data service at '" + servicePath + "' cannot be found");
		return null;
	}

	/**
	 * Undeploys a service.
	 */
	public void undeploy(String servicePath) throws DeploymentException {
		try {
			DataService dataService = this.getDataServiceByServicePath(servicePath);
			if (dataService == null) {
				/* must be a faulty service */
                /* the faulty service should be removed at this point from the axis configuration.
                   otherwise the service would still be shown as a faulty service in the management
                   console UI since it queries and lists out the faulty services from the
                   axisConfiguration itself.
                   */
                this.axisConfig.removeFaultyService(servicePath);
				return;
			}
			String serviceHierarchy = Utils.getServiceHierarchy(servicePath, this.repoDir);
	        if (serviceHierarchy == null){
	            serviceHierarchy = "";
	        }
	        String serviceName = serviceHierarchy + dataService.getName();
            /* In the context of dataservices one service group will only contain one dataservice.
            *  Hence assigning the service group as the service group name */
			AxisServiceGroup serviceGroup = this.axisConfig.getServiceGroup(serviceName);
//			CarbonContext cCtx = CarbonContextrbonContext.getThreadLocalCarbonContext();
			if (serviceGroup == null) { /* must be a faulty service */
				this.axisConfig.removeFaultyService(servicePath);
				for (String configID : dataService.getConfigs().keySet()) {
					if (dataService.getConfig(configID).isODataEnabled()) {
						removeODataHandler(org.wso2.micro.core.Constants.SUPER_TENANT_DOMAIN_NAME, dataService.getName() + configID);
					}
				}
			} else {
				/* cleanup data service */
				for (String configID : dataService.getConfigs().keySet()) {
					if (dataService.getConfig(configID).isODataEnabled()) {
						removeODataHandler(org.wso2.micro.core.Constants.SUPER_TENANT_DOMAIN_NAME, dataService.getName() + configID);
					}
				}
				dataService.cleanup();
				this.axisConfig.removeService(serviceName);
				/* if the service group is now empty, remove it as well */
				if (!serviceGroup.getServices().hasNext()) {
					/* when the service group is removed re-deployment causes problems */
					this.axisConfig.removeServiceGroup(serviceGroup.getServiceGroupName());
				}
			}
            if (log.isDebugEnabled()) {
                log.debug(Messages.getMessage(DeploymentErrorMsgs.SERVICE_REMOVED, serviceName));
            }
            super.undeploy(servicePath);
		} catch (Exception e) {
			String msg = "Error in undeploying service";
			log.error(msg, e);
			throw new DeploymentException(msg, e);
		}
	}

	/**
	 * Configuration files prior to multiple data source support did not have id attribute
	 * for config element. Adding that & saving.
	 */
	@SuppressWarnings("unchecked")
	private void convertConfigToMultipleDSFormat(String configFilePath)
			throws DataServiceFault {
		FileInputStream fis = null;
		boolean changed = false;
		try {
			/*
			    Security Comment :
			    This config file  path is trustworthy, file path cannot be access by the user.
			*/
			fis = new FileInputStream(configFilePath);
			OMElement configElement = (new StAXOMBuilder(fis)).getDocumentElement();
			configElement.build();
			Iterator<OMElement> configElements = configElement.getChildrenWithName(
					new QName(DBSFields.CONFIG));
			int emptyConfigs = 0;
			while (configElements.hasNext()) {
				OMElement config = configElements.next();
				String configId = config.getAttributeValue(new QName(DBSFields.ID));
				if (configId == null || configId.trim().length() == 0) {
					config.addAttribute(DBSFields.ID, DBConstants.DEFAULT_CONFIG_ID, null);
					changed = true;
					emptyConfigs++;
					if (emptyConfigs > 1) {
						throw new DataServiceFault("More than one config elements found in " +
								configFilePath);
					}
				}
			}
			Iterator<OMElement> queryElements =
                    configElement.getChildrenWithName(new QName(DBSFields.QUERY));
			while (queryElements.hasNext()) {
				OMElement query = queryElements.next();
				String useConfig = query.getAttributeValue(new QName(
						DBSFields.USE_CONFIG));
				if (useConfig == null || useConfig.trim().length() == 0) {
					query.addAttribute(DBSFields.USE_CONFIG, DBConstants.DEFAULT_CONFIG_ID, null);
					changed = true;
				}
			}
			if (changed) {
				if (log.isDebugEnabled()) {
					log.debug("Converting " + configFilePath +
							" to support multiple data sources.");
				}
				BufferedWriter out = new BufferedWriter(new FileWriter(configFilePath));
				configElement.serialize(out);
				out.close();
				DBUtils.prettifyXMLFile(configFilePath);
			}
		} catch (Exception e) {
			throw new DataServiceFault(e);
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					log.error("Error in closing data service configuration file", e);
				}
			}
		}
	}

	/**
	 * Creates an AxisOperation with the given data service operation object.
	 * @see Operation
	 * @see AxisOperation
	 */
	private AxisOperation createAxisOperationFromDSOperation(Operation operation,
			AxisBinding soap11Binding, AxisBinding soap12Binding,
			AxisBinding httpBinding) throws AxisFault {
		String opName = operation.getName();
		String requestName = operation.getRequestName();

		int index = opName.indexOf(":");
		if (index > -1) {
			opName = opName.substring(index + 1);
		}
		boolean hasResult = operation.getCallQuery().isHasResult()
				|| operation.isReturnRequestStatus();
		String description = operation.getDescription();
		return createAxisOperation(requestName, opName, HTTPConstants.HTTP_METHOD_POST, hasResult,
				soap11Binding, soap12Binding, httpBinding, description);
	}

	/**
	 * Creates an AxisOperation with the given data service resource object.
	 * @see Operation
	 * @see AxisOperation
	 */
	private AxisOperation createAxisOperationFromDSResource(Resource resource,
                                                            AxisBinding soap11Binding, AxisBinding soap12Binding,
                                                            AxisBinding httpBinding) {
		Resource.ResourceID resourceId = resource.getResourceId();
		String method = resourceId.getMethod();
		String path = resourceId.getPath();
		String requestName = resource.getRequestName();
		String description = resource.getDescription();
		boolean hasResult = resource.getCallQuery().isHasResult()
				|| resource.isReturnRequestStatus();
		return createAxisOperation(requestName, path, method, hasResult, soap11Binding,
				soap12Binding, httpBinding, description);
	}

	/**
	 * Utility method for creating AxisOperation objects.
	 */
	private AxisOperation createAxisOperation(String operationName, String httpLocation,
			String method, boolean hasResult,
			AxisBinding soap11Binding, AxisBinding soap12Binding, AxisBinding httpBinding,
			String description) {
		AxisOperation axisOperation;
		if (hasResult) {
			axisOperation = new InOutAxisOperation(new QName(operationName));
			DBInOutMessageReceiver inoutMsgReceiver = new DBInOutMessageReceiver();
			axisOperation.setMessageReceiver(inoutMsgReceiver);
			axisOperation.setMessageExchangePattern(WSDL2Constants.MEP_URI_IN_OUT);
		} else {
			axisOperation = new InOnlyAxisOperation(new QName(operationName));
			DBInOnlyMessageReceiver inonlyMsgReceiver = new DBInOnlyMessageReceiver();
			axisOperation.setMessageReceiver(inonlyMsgReceiver);
			axisOperation.setMessageExchangePattern(WSDL2Constants.MEP_URI_ROBUST_IN_ONLY);
		}

		axisOperation.setStyle(WSDLConstants.STYLE_DOC);

		String opName = axisOperation.getName().getLocalPart();
		// Create a default SOAP 1.1 Binding operation
		AxisBindingOperation soap11BindingOperation = createDefaultSOAP11BindingOperation(
				axisOperation, httpLocation, "urn:" + opName, soap11Binding);

		// Create a default SOAP 1.2 Binding operation
		AxisBindingOperation soap12BindingOperation = createDefaultSOAP12BindingOperation(
				axisOperation, httpLocation, "urn:" + opName, soap12Binding);

		// Create a default HTTP Binding operation
		AxisBindingOperation httpBindingOperation = createDefaultHTTPBindingOperation(
				axisOperation, httpLocation, method, httpBinding);

        if(httpLocation.startsWith("/")){
            httpLocation = httpLocation.substring(1);
        }

        Pattern httpLocationPattern = WSDLUtil.getConstantFromHTTPLocationForResource(httpLocation, method);
        this.httpLocationTableForResource.put(httpLocationPattern, axisOperation);

		// Create the in and out axis messages for this operation
		AxisMessage inMessage = axisOperation.getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
		if (inMessage != null) {
			inMessage.setName(operationName + Java2WSDLConstants.MESSAGE_SUFFIX);
			createAxisBindingMessage(soap11BindingOperation, inMessage,
                    WSDLConstants.MESSAGE_LABEL_IN_VALUE, false);
			createAxisBindingMessage(soap12BindingOperation, inMessage,
                    WSDLConstants.MESSAGE_LABEL_IN_VALUE, false);
			createAxisBindingMessage(httpBindingOperation, inMessage,
                    WSDLConstants.MESSAGE_LABEL_IN_VALUE, false);
		}

		if (axisOperation instanceof InOutAxisOperation) {
			AxisMessage outMessage =
                    axisOperation.getMessage(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
			if (outMessage != null) {
				outMessage.setName(operationName + Java2WSDLConstants.RESPONSE_MESSAGE);
				createAxisBindingMessage(soap11BindingOperation, outMessage,
                        WSDLConstants.MESSAGE_LABEL_OUT_VALUE, false);
				createAxisBindingMessage(soap12BindingOperation, outMessage,
                        WSDLConstants.MESSAGE_LABEL_OUT_VALUE, false);
				createAxisBindingMessage(httpBindingOperation, outMessage,
                        WSDLConstants.MESSAGE_LABEL_OUT_VALUE, false);
			}
		}
		/* Set the fault message, only if operation returns a result*/
		if (hasResult) {
			AxisMessage faultMessage = new AxisMessage();
			faultMessage.setName(DBConstants.DS_FAULT_ELEMENT);
			faultMessage.setElementQName(new QName(DBConstants.WSO2_DS_NAMESPACE,
	                DBConstants.DS_FAULT_ELEMENT));
			axisOperation.setFaultMessages(faultMessage);
			createAxisBindingMessage(soap11BindingOperation, faultMessage,
	                WSDLConstants.MESSAGE_LABEL_FAULT_VALUE, true);
			createAxisBindingMessage(soap12BindingOperation, faultMessage,
	                WSDLConstants.MESSAGE_LABEL_FAULT_VALUE, true);
			createAxisBindingMessage(httpBindingOperation, faultMessage,
	                WSDLConstants.MESSAGE_LABEL_FAULT_VALUE, true);
		}

		axisOperation.setDocumentation(description);
		return axisOperation;
	}

	/**
	 * Creates a schema from a DataService object, to be used later in WSDL generation.
	 */
	@SuppressWarnings ("unchecked")
	private void createDSSchema(AxisService axisService, DataService dataService)
			throws DataServiceFault {
		NamespaceMap map = new NamespaceMap();
		map.put(Java2WSDLConstants.DEFAULT_SCHEMA_NAMESPACE_PREFIX, Java2WSDLConstants.URI_2001_SCHEMA_XSD);
		axisService.setNamespaceMap(map);
		DataServiceDocLitWrappedSchemaGenerator.populateServiceSchema(axisService);
	}

	/**
	 * Validate the data service to see if the data service is invalid.
	 */
	private void validateDataService(DataService dataService) throws DataServiceFault {
		this.validateRequestCallQuery(dataService);
		this.validateRequestQueryParams(dataService);
		this.validateRequestQueryResults(dataService);
	}

	/**
	 * Check call-queries of callable requests (i.e. operations, resources) to see if the queries
	 * exists.
	 */
	private void validateRequestCallQuery(DataService dataService) throws DataServiceFault {
		for (CallableRequest cr : dataService.getCallableRequests().values()) {
			CallQuery callQuery = cr.getCallQuery();
            if (callQuery.getQuery() == null) {
                DataServiceFault dsf = new DataServiceFault("Invalid DBS",
                        "Call query with id: " + callQuery.getQueryId() +
                        " doesn't exist as referenced by the operation/resource: " +
                        cr.getRequestName());
                dsf.setSourceDataService(dataService);
                throw dsf;
            }
		}
	}

	/**
	 * Check query-params if they exist in the query as mentioned in the 'with-params' in
	 * operation/resource, the computational complexity of this code is not an issue, since this is
	 * deployment time.
	 */
	private void validateRequestQueryParams(DataService dataService) throws DataServiceFault {
		for (CallableRequest cr : dataService.getCallableRequests().values()) {
			CallQuery callQuery = cr.getCallQuery();
            Query query = callQuery.getQuery();
            for (WithParam withParam : callQuery.getWithParams().values()) {
                boolean found = false;
                for (QueryParam queryParam : query.getQueryParams()) {
                    if (withParam.getName().equals(queryParam.getName())) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    /* param found, move onto next 'with-param' */
                    continue;
                } else {
                    /* the param is not found in the query, throw an exception */
                    DataServiceFault dsf = new DataServiceFault("Invalid DBS",
                            "with-param with name: " + withParam.getName()
                                    + " doesn't exist in query with id: " + query.getQueryId()
                                    + " as referenced by the operation/resource: "
                                    + cr.getRequestName());
                    dsf.setSourceDataService(dataService);
                    throw dsf;
                }
            }
		}
	}

	/**
	 * Check if an request's query has a result, and if that result contain an element wrapper.
	 */
	private void validateRequestQueryResults(DataService dataService) throws DataServiceFault {
		for (CallableRequest request : dataService.getCallableRequests().values()) {
			CallQuery callQuery = request.getCallQuery();
			if (callQuery == null) {
				continue;
			}
			Query query = callQuery.getQuery();
			if (query == null) {
				continue;
			}
			if (query.getResult() != null) {
				if (query.getResult().getResultType() != ResultTypes.JSON &&
				        DBUtils.isEmptyString(query.getResult().getElementName())) {
					throw new DataServiceFault("The request '" + request.getRequestName()
							+ "' contains the query with id '" + query.getQueryId()
							+ "' contains an XML result with no element wrapper.");
				}
			}
		}
	}

	/**
	 * Creates AxisService from DBS.
	 */
	private AxisService createDBService(DeploymentFileData deploymentFileData,
			AxisConfiguration axisConfiguration) throws DataServiceFault {
		FileInputStream fis = null;
		try {
			/*
			    Security Comment :
			    This config file  path is trustworthy, file path cannot be access by the user.
			*/
			/* convert to multiple config format */
			String configFilePath = deploymentFileData.getAbsolutePath();

			convertConfigToMultipleDSFormat(configFilePath);

			fis = new FileInputStream(configFilePath);
			inputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
			javax.xml.stream.XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(fis);
			OMElement dbsElement = (new StAXOMBuilder(xmlReader)).getDocumentElement();
			dbsElement.build();

			/* apply secure vault information in resolving the aliases to decrypted values */
            this.secureVaultResolve(dbsElement);

			/* create the data service object from dbs */
			DataService dataService = DataServiceFactory.createDataService(deploymentFileData, dbsElement, configFilePath);

			String serviceName = dataService.getName();

			/*create the odata service */
			for (String configId : dataService.getConfigs().keySet()) {
				Config config = dataService.getConfig(configId);
				if (config.isODataEnabled()) {
					ODataServiceHandler serviceHandler = new ODataServiceHandler(config.createODataHandler(),
					                                                             dataService.getServiceNamespace(),
					                                                             configId);
					registerODataHandler(dataService.getName(), serviceHandler,
					                     org.wso2.micro.core.Constants.SUPER_TENANT_DOMAIN_NAME, configId);
				}
			}

			/* validate the data service */
			this.validateDataService(dataService);

			String interfaceName = serviceName + WSDL2Constants.INTERFACE_PREFIX;

			AxisService axisService = new AxisService(serviceName);
            try {
                axisService.setFileName(new URL("file://" + configFilePath));
            } catch (MalformedURLException e) {
                throw new DataServiceFault(e);
            }

			/* set service target namespace */
			axisService.setTargetNamespace(dataService.getServiceNamespace());

			/* Used by the container to find out what kind of a service this is. */
			axisService.addParameter(new Parameter(DBConstants.AXIS2_SERVICE_TYPE,
					DBConstants.DB_SERVICE_TYPE));

			/* save the data service object in the AxisService */
			axisService.addParameter(DBConstants.DATA_SERVICE_OBJECT, dataService);

			String swaggerResourcePath = dataService.getSwaggerResourcePath();
			if (StringUtils.isNotEmpty(swaggerResourcePath)) {
				axisService.addParameter(DBConstants.SWAGGER_RESOURCE_PATH, swaggerResourcePath);
			}

			/* set service description */
			axisService.setDocumentation(dataService.getDescription());

			/* set transports */
			axisService.setExposedTransports(dataService.getTransports());

			this.httpLocationTable = new TreeMap<String, AxisOperation>(
					new Comparator<String>() {
						public int compare(String o1, String o2) {
							return (-1 * o1.compareTo(o2));
						}
					});
            this.httpLocationTableForResource = new TreeMap<Pattern, AxisOperation>(
                    new Comparator<Pattern>() {
                        public int compare(Pattern o1, Pattern o2) {
                            return (-1 * o1.pattern().compareTo(o2.pattern()));
                        }
                    });

			AxisBinding soap11Binding = createDefaultSOAP11Binding(
					serviceName, interfaceName);
			AxisBinding soap12Binding = createDefaultSOAP12Binding(
					serviceName, interfaceName);
			AxisBinding httpBinding = createDefaultHTTPBinding(serviceName,
					interfaceName);

			/* REST processing - adding DS resources to AxisService */
            Set<Resource.ResourceID> resourceIds = dataService.getResourceIds();
            Set<Resource.ResourceID> sortedResourceIds = new TreeSet<>(resourceIds).descendingSet();
            for (Resource.ResourceID resourceId : sortedResourceIds) {
                Resource resource = dataService.getResource(resourceId);
                AxisOperation axisOperation = createAxisOperationFromDSResource(resource, soap11Binding, soap12Binding,
                        httpBinding);
                axisService.addOperation(axisOperation);
                axisConfig.getPhasesInfo().setOperationPhases(axisOperation);
            }

			/* add operations */
			Iterator<String> opPathItr = dataService.getOperationNames()
					.iterator();
			while (opPathItr.hasNext()) {
				Operation operation = dataService
						.getOperation(opPathItr.next());
				AxisOperation axisOperation = createAxisOperationFromDSOperation(
						operation, soap11Binding, soap12Binding, httpBinding);
				axisService.addOperation(axisOperation);
				axisConfig.getPhasesInfo().setOperationPhases(axisOperation);
			}

			createDefaultEndpoints(axisService, soap11Binding, soap12Binding,
					httpBinding);

			/* create schema */
			createDSSchema(axisService, dataService);

			/* set session scope type for boxcarring */
			if (dataService.isBoxcarringEnabled() && !dataService.isDisableLegacyBoxcarringMode()) {
				axisService.setScope(Constants.SCOPE_TRANSPORT_SESSION);
			}

			/* register JMX MBean */
			this.registerMBean(dataService);

			/* set service status */
			axisService.setActive(!dataService.isServiceInactive());

			return axisService;
		} catch (FileNotFoundException e) {
			throw new DataServiceFault(e, "Error reading service configuration file.");
		} catch (XMLStreamException e) {
			throw new DataServiceFault(e, "Error while parsing the service configuration file.");
		} catch (AxisFault e) {
			throw new DataServiceFault(e);
		} finally {
			try {
				if (fis != null) {
				    fis.close();
				}
			} catch (IOException e) {
				log.error("Error in closing data services configuration file", e);
			}
		}
	}

	/**
	 * Registers an MBean representing the given data service.
	 */
	private void registerMBean(DataService dataService) {
		DataServiceInstanceMBean dsMBean = new DataServiceInstance(dataService);
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		if (server != null) {
			try {
				ObjectName objectName = new ObjectName(DBConstants.DATA_SERVICES_JMX_DOMAIN +
			    		":section=Services,service=" +
			    		dsMBean.getServiceName());
				try {
					server.unregisterMBean(objectName);
				} catch (Exception ignore) {
					/* ignore if it doesn't exist */
				}
			    server.registerMBean(dsMBean, objectName);
			} catch (Exception e) {
				log.error("Error in Registering Data Services MBean", e);
			}
		}
	}

	/**
	 * Creates AxisBindingMessage and populates it.
	 */
	private void createAxisBindingMessage(
			AxisBindingOperation bindingOperation, AxisMessage inMessage, String label, boolean isFault) {
		AxisBindingMessage soap11InBindingMessage = new AxisBindingMessage();
		soap11InBindingMessage.setName(inMessage.getName());
		soap11InBindingMessage.setAxisMessage(inMessage);
		soap11InBindingMessage.setParent(bindingOperation);
		if (isFault) {
			soap11InBindingMessage.setFault(true);
			bindingOperation.addFault(soap11InBindingMessage);
		} else {
			soap11InBindingMessage.setFault(false);
			bindingOperation.addChild(label, soap11InBindingMessage);
		}
	}

	/**
	 * Creates AxisBindingOperation and populates it with HTTP properties
	 */
	private AxisBindingOperation createDefaultHTTPBindingOperation(
			AxisOperation axisOp, String httpLocation, String httpMethod,
			AxisBinding httpBinding) {
		AxisBindingOperation httpBindingOperation = new AxisBindingOperation();
		httpBindingOperation.setAxisOperation(axisOp);
		httpBindingOperation.setName(axisOp.getName());
		httpBindingOperation.setParent(httpBinding);
		httpBindingOperation.setProperty(WSDL2Constants.ATTR_WHTTP_LOCATION, httpLocation);
		httpBindingOperation.setProperty(WSDL2Constants.ATTR_WHTTP_METHOD, httpMethod);
		httpBinding.addChild(httpBindingOperation.getName(), httpBindingOperation);
		return httpBindingOperation;
	}

	/**
	 * Creates AxisBindingOperation and populates it with SOAP 1.2 properties
	 */
	private AxisBindingOperation createDefaultSOAP12BindingOperation(
			AxisOperation axisOp, String httpLocation, String inputAction,
			AxisBinding soap12Binding) {
		AxisBindingOperation soap12BindingOperation = new AxisBindingOperation();
		soap12BindingOperation.setAxisOperation(axisOp);
		soap12BindingOperation.setName(axisOp.getName());
		soap12BindingOperation.setParent(soap12Binding);
		soap12BindingOperation.setProperty(WSDL2Constants.ATTR_WHTTP_LOCATION,
				httpLocation);
		soap12Binding.addChild(soap12BindingOperation.getName(), soap12BindingOperation);
		soap12BindingOperation.setProperty(WSDL2Constants.ATTR_WSOAP_ACTION, inputAction);
		return soap12BindingOperation;
	}

	/**
	 * Creates AxisBindingOperation and populates it with SOAP 1.1 properties
	 */
	private AxisBindingOperation createDefaultSOAP11BindingOperation(
			AxisOperation axisOp, String httpLocation, String inputAction,
			AxisBinding soap11Binding) {
		AxisBindingOperation soap11BindingOperation = new AxisBindingOperation();
		soap11BindingOperation.setAxisOperation(axisOp);
		soap11BindingOperation.setName(axisOp.getName());
		soap11BindingOperation.setParent(soap11Binding);
		soap11BindingOperation.setProperty(WSDL2Constants.ATTR_WHTTP_LOCATION,
				httpLocation);
		soap11Binding.addChild(soap11BindingOperation.getName(), soap11BindingOperation);
		soap11BindingOperation.setProperty(WSDL2Constants.ATTR_WSOAP_ACTION, inputAction);
		return soap11BindingOperation;
	}


	/**
	 * Creates a AxisBinding and populates it with default SOAP 1.1 properties
	 */
	private AxisBinding createDefaultSOAP11Binding(String name, String interfaceName) {
		AxisBinding soap11Binding = new AxisBinding();
		soap11Binding.setName(new QName(name + Java2WSDLConstants.BINDING_NAME_SUFFIX));
		soap11Binding.setType(WSDL2Constants.URI_WSDL2_SOAP);
		soap11Binding.setProperty(WSDL2Constants.ATTR_WSOAP_PROTOCOL, WSDL2Constants.HTTP_PROTOCAL);
		soap11Binding.setProperty(WSDL2Constants.ATTR_WSOAP_VERSION, SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
		soap11Binding.setProperty(WSDL2Constants.INTERFACE_LOCAL_NAME, interfaceName);
		soap11Binding.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE, httpLocationTable);
        soap11Binding.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE_FOR_RESOURCE, httpLocationTableForResource);
		return soap11Binding;
	}

	/**
	 * Creates a AxisBinding and populates it with default HTTP properties
	 */
	private AxisBinding createDefaultHTTPBinding(String name, String interfaceName) {
		AxisBinding httpBinding = new AxisBinding();
		httpBinding.setName(new QName(name + Java2WSDLConstants.HTTP_BINDING));
		httpBinding.setType(WSDL2Constants.URI_WSDL2_HTTP);
		httpBinding.setProperty(WSDL2Constants.INTERFACE_LOCAL_NAME, interfaceName);
		httpBinding.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE, httpLocationTable);
        httpBinding.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE_FOR_RESOURCE, httpLocationTableForResource);
		return httpBinding;
	}

	/**
	 * Creates a AxisBinding and populates it with default SOAP 1.2 properties
	 */
	private AxisBinding createDefaultSOAP12Binding(String name, String interfaceName) {
		AxisBinding soap12Binding = new AxisBinding();
		soap12Binding.setName(new QName(name + Java2WSDLConstants.SOAP12BINDING_NAME_SUFFIX));
		soap12Binding.setType(WSDL2Constants.URI_WSDL2_SOAP);
		soap12Binding.setProperty(WSDL2Constants.ATTR_WSOAP_PROTOCOL, WSDL2Constants.HTTP_PROTOCAL);
		soap12Binding.setProperty(WSDL2Constants.ATTR_WSOAP_VERSION, SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI);
		soap12Binding.setProperty(WSDL2Constants.INTERFACE_LOCAL_NAME, interfaceName);
		soap12Binding.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE, httpLocationTable);
        soap12Binding.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE_FOR_RESOURCE, httpLocationTableForResource);
		return soap12Binding;
	}


	/**
	 * Creates a set of default endpoints for this service
	 */
	private void createDefaultEndpoints(AxisService axisService, AxisBinding soap11Binding,
			AxisBinding soap12Binding, AxisBinding httpBinding) {
		Map<String, TransportInDescription> transportsIn = axisConfig.getTransportsIn();
		Iterator<TransportInDescription> iterator = transportsIn.values().iterator();
		while (iterator.hasNext()) {
			/*
			 * Used to indicate whether a HTTPEndpoint is needed. Http endpoint
			 * is needed only for http and https transports
			 */
			boolean needHttp = false;

			/* The prefix is used to generate endpoint names */
			String prefix = "";
			TransportInDescription transportIn = iterator.next();
			String transportInName = transportIn.getName();
			if (HTTP_TRANSPORT.equalsIgnoreCase(transportInName)) {
				needHttp = true;
			} else if (HTTPS_TRANSPORT.equalsIgnoreCase(transportInName)) {
				needHttp = true;
				prefix = WSDL2Constants.DEFAULT_HTTPS_PREFIX;
			} else if (transportInName != null) {
				prefix = transportInName.toUpperCase();
			}

			/* Creates a default SOAP 1.1 endpoint */
			AxisEndpoint soap11Endpoint = new AxisEndpoint();
			String soap11EndpointName = prefix
					+ WSDL2Constants.DEFAULT_SOAP11_ENDPOINT_NAME;
			soap11Endpoint.setName(soap11EndpointName);
			soap11Endpoint.setBinding(soap11Binding);
			soap11Endpoint.setParent(axisService);
			soap11Endpoint.setTransportInDescription(transportInName);
			soap11Endpoint.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE, httpLocationTable);
            soap11Endpoint.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE_FOR_RESOURCE, httpLocationTableForResource);
			axisService.addEndpoint(soap11EndpointName, soap11Endpoint);

            /* setting soap11 endpoint as the default endpoint */
			axisService.setEndpointName(soap11EndpointName);

			/* Creates a default SOAP 1.2 endpoint */
			AxisEndpoint soap12Endpoint = new AxisEndpoint();
			String soap12EndpointName = prefix
					+ WSDL2Constants.DEFAULT_SOAP12_ENDPOINT_NAME;
			soap12Endpoint.setName(soap12EndpointName);
			soap12Endpoint.setBinding(soap12Binding);
			soap12Endpoint.setParent(axisService);
			soap12Endpoint.setTransportInDescription(transportInName);
			soap12Endpoint.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE, httpLocationTable);
            soap12Endpoint.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE_FOR_RESOURCE, httpLocationTableForResource);
			axisService.addEndpoint(soap12EndpointName, soap12Endpoint);

			/* Creates a HTTP endpoint if its http or https transport is used */
			if (needHttp) {
				AxisEndpoint httpEndpoint = new AxisEndpoint();
				String httpEndpointName = prefix
						+ WSDL2Constants.DEFAULT_HTTP_ENDPOINT_NAME;
				httpEndpoint.setName(httpEndpointName);
				httpEndpoint.setBinding(httpBinding);
				httpEndpoint.setParent(axisService);
				httpEndpoint.setTransportInDescription(transportInName);
				httpEndpoint.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE, httpLocationTable);
                httpEndpoint.setProperty(WSDL2Constants.HTTP_LOCATION_TABLE_FOR_RESOURCE, httpLocationTableForResource);
				axisService.addEndpoint(httpEndpointName, httpEndpoint);
			}
		}
	}

	/**
	 * This method checks if the given data service has a corresponding "services.xml" is available,
	 * if so, the AxisService representing the data service is applied the instructions from its
	 * "services.xml".
	 */
	private AxisService handleTransports(DeploymentFileData file, AxisService axisService) throws DataServiceFault {
		try (FileInputStream fis = new FileInputStream(file.getFile().getAbsoluteFile())) {
			StAXOMBuilder builder = new StAXOMBuilder(fis);
            OMElement documentElement =  builder.getDocumentElement();
            OMAttribute transports = documentElement.getAttribute(new QName(DBSFields.TRANSPORTS));
            if (transports != null) {
                String [] transportArr = transports.getAttributeValue().split(" ");
                axisService.setExposedTransports(Arrays.asList(transportArr));
            }
		} catch (Exception e) {
			throw new DataServiceFault(e, "Error in processing transports info");
		}
		return axisService;
	}

	/**
	 * Creates AxisService with the given deployment information.
	 */
	private AxisService processService(DeploymentFileData currentFile,
			AxisServiceGroup axisServiceGroup, ConfigurationContext configCtx)
			throws DataServiceFault {
		/*
			Security Comment
			CurrentFile contains the actual dbs data location in the server. there isn't any input from the user.
		 */
		AxisService axisService = createDBService(currentFile, configCtx.getAxisConfiguration());
		axisService.setParent(axisServiceGroup);
		axisService.setClassLoader(axisConfig.getServiceClassLoader());
        /* handle services.xml, if exists */
		this.handleTransports(currentFile, axisService);
		return axisService;
	}

    /**
     * Helper method to handle security policies.
     *
     * @param file deployment data file.
     * @param axisService to be modified.
     * @return true if security is enabled, false otherwise.
     * @throws DataServiceFault
     */
    private boolean handleSecurityProxy(DeploymentFileData file, AxisService axisService) throws DataServiceFault {
        try (FileInputStream fis = new FileInputStream(file.getFile().getAbsoluteFile())) {
            boolean secEnabled = false;
            StAXOMBuilder builder = new StAXOMBuilder(fis);
            OMElement documentElement =  builder.getDocumentElement();
            OMElement enableSecElement= documentElement.getFirstChildWithName(new QName(DBSFields.ENABLESEC));
            if (enableSecElement != null) {
                secEnabled = true;
            }
            OMElement policyElement= documentElement.getFirstChildWithName(new QName(DBSFields.POLICY));
            if (policyElement != null) {
                String policyKey = policyElement.getAttributeValue(new QName(DBSFields.POLICY_KEY));
                if (null == policyKey) {
                    throw new DataServiceFault("Policy key element should contain a policy key in "
                            + file.getFile().getName());
                }
                Policy policy = PolicyEngine.getPolicy(DBUtils.getInputStreamFromPath(policyKey));
                axisService.getPolicySubject().attachPolicy(policy);
            }
            return secEnabled;
        }catch (Exception e) {
            throw new DataServiceFault(e, "Error in processing security policy");
        }
    }

    @SuppressWarnings("unchecked")
	private void secureVaultResolve(OMElement dbsElement) {
		SynapseEnvironment synapseEnvironment = null;
    	String secretAliasAttr = dbsElement.getAttributeValue(
			    new QName(DataSourceConstants.SECURE_VAULT_NS, DataSourceConstants.SECRET_ALIAS_ATTR_NAME));
    	if (secretAliasAttr != null) {
    		dbsElement.setText(DBUtils.loadFromSecureVault(secretAliasAttr));
    	}

    	Iterator<OMElement> childEls = (Iterator<OMElement>) dbsElement.getChildElements();
    	while (childEls.hasNext()) {
    		this.secureVaultResolve(childEls.next());
    	}
		// check for existence of the vault-lookup function
		String elementText = dbsElement.getText();
		dbsElement.setText(resolveVaultExpressions(elementText));
	}

	/**
	 * Resolve secure-vault property values
	 *
	 * @param propertyValue value to be resolved
	 * @return a resolved value
	 */
	private String resolveVaultExpressions(String propertyValue) {
		Matcher lookupMatcher = vaultLookupPattern.matcher(propertyValue);
		if (lookupMatcher.matches()) {
			//getting the expression with out curly brackets
			String expressionStr = lookupMatcher.group(0).substring(1, lookupMatcher.group(0).length() - 1);
			try {
				String resolvedValue = null;
				Value expression = new Value(new SynapseXPath(expressionStr));
				Parameter synapseEnv = axisConfig.getParameter(SynapseConstants.SYNAPSE_ENV);
				if (synapseEnv != null) {
					SynapseEnvironment synapseEnvironment = (SynapseEnvironment) synapseEnv.getValue();
					resolvedValue = expression.evaluateValue(synapseEnvironment.createMessageContext());
				}
				if (resolvedValue == null || resolvedValue.isEmpty()) {
					log.warn("Found Empty value for expression : " + expression.getExpression());
				} else {
					return resolvedValue;
				}
			} catch (JaxenException e) {
				log.error("Error while building the expression : " + expressionStr);
			}
		}
		return propertyValue;
	}

	private void removeODataHandler(String tenantDomain, String dataServiceName) throws DataServiceFault {
		ODataServiceRegistry registry = ODataServiceRegistry.getInstance();
		registry.removeODataService(tenantDomain, dataServiceName);
	}

	private void registerODataHandler(String dataServiceName, ODataServiceHandler handler, String tenantDomain,
	                                  String configId) throws DataServiceFault {
		ODataServiceRegistry registry = ODataServiceRegistry.getInstance();
		registry.registerODataService(dataServiceName + configId, handler, tenantDomain);
	}
}
