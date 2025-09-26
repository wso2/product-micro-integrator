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

import org.apache.axiom.om.OMElement;
import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.apache.commons.lang.StringUtils;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.common.DBConstants.AuthorizationProviderConfig;
import org.wso2.micro.integrator.dataservices.common.DBConstants.BoxcarringOps;
import org.wso2.micro.integrator.dataservices.common.DBConstants.DBSFields;
import org.wso2.micro.integrator.dataservices.core.auth.AuthorizationProvider;
import org.wso2.micro.integrator.dataservices.core.auth.UserStoreAuthorizationProvider;
import org.wso2.micro.integrator.dataservices.core.description.config.ConfigFactory;
import org.wso2.micro.integrator.dataservices.core.description.event.EventTriggerFactory;
import org.wso2.micro.integrator.dataservices.core.description.operation.Operation;
import org.wso2.micro.integrator.dataservices.core.description.operation.OperationFactory;
import org.wso2.micro.integrator.dataservices.core.description.query.QueryFactory;
import org.wso2.micro.integrator.dataservices.core.description.query.SQLQuery;
import org.wso2.micro.integrator.dataservices.core.description.resource.Resource;
import org.wso2.micro.integrator.dataservices.core.description.resource.ResourceFactory;
import org.wso2.micro.integrator.dataservices.core.engine.CallableRequest;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.SecurityConstants;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Factory class for creating DataService objects
 * from the data services configuration.
 *
 * @see DataService
 */
public class DataServiceFactory {

    public static DataService createDataService(DeploymentFileData deploymentFileData, OMElement dbsElement,
                                                String dsLocation) throws DataServiceFault {

        String serviceName = dbsElement.getAttributeValue(new QName(DBSFields.NAME));
        if (deploymentFileData.isVersionedDeployment()) {
            serviceName = deploymentFileData.getArtifactIdentifier() + "/" + serviceName;
        }
        return populateDataService(serviceName, dbsElement, dsLocation);
    }

    /**
     * Creates a DataService object with the given information.
     *
     * @see DataService
     */
    @SuppressWarnings("unchecked")
    public static DataService createDataService(OMElement dbsElement,
                                                String dsLocation) throws DataServiceFault {

        String serviceName = dbsElement.getAttributeValue(new QName(DBSFields.NAME));
        return populateDataService(serviceName, dbsElement, dsLocation);
    }

    private static DataService populateDataService(String serviceName, OMElement dbsElement, String dsLocation)
            throws DataServiceFault {

        DataService dataService = null;
        try {
            String serviceNamespace = dbsElement.getAttributeValue(new QName(DBSFields.SERVICE_NAMESPACE));
            if (DBUtils.isEmptyString(serviceNamespace)) {
                serviceNamespace = DBConstants.WSO2_DS_NAMESPACE;
            }
            String defaultNamespace = dbsElement.getAttributeValue(new QName(DBSFields.BASE_URI));
            if (DBUtils.isEmptyString(defaultNamespace)) {
                defaultNamespace = serviceNamespace;
            }

            String serviceGroup = dbsElement.getAttributeValue(new QName(DBSFields.SERVICE_GROUP));
            if (DBUtils.isEmptyString(serviceGroup)) {
                serviceGroup = serviceName;
            }

            /* get the description */
            OMElement descEl = dbsElement.getFirstChildWithName(new QName(DBSFields.DESCRIPTION));
            String description = null;
            if (descEl != null) {
                description = descEl.getText();
            }

            String serviceStatus = dbsElement.getAttributeValue(
                    new QName(DBSFields.SERVICE_STATUS));

            boolean batchRequestsEnabled = false;
            boolean boxcarringEnabled = false;

            String batchRequestsEnabledStr = dbsElement.getAttributeValue(
                    new QName(DBSFields.ENABLE_BATCH_REQUESTS));
            if (batchRequestsEnabledStr != null) {
                batchRequestsEnabled = Boolean.parseBoolean(batchRequestsEnabledStr);
            }

            String boxcarringEnabledStr = dbsElement.getAttributeValue(
                    new QName(DBSFields.ENABLE_BOXCARRING));
            if (boxcarringEnabledStr != null) {
                boxcarringEnabled = Boolean.parseBoolean(boxcarringEnabledStr);
            }

            boolean disableStreaming = false;
            String disableStreamingStr = dbsElement.getAttributeValue(
                    new QName(DBSFields.DISABLE_STREAMING));
            if (disableStreamingStr != null) {
                disableStreaming = Boolean.parseBoolean(disableStreamingStr);
            }

            boolean disableLegacyBoxcarringMode = false;
            String disableLegacyBoxcarringModeStr =
                    dbsElement.getAttributeValue(new QName(DBSFields.DISABLE_LEGACY_BOXCARRING_MODE));
            if (disableLegacyBoxcarringModeStr != null) {
                disableLegacyBoxcarringMode = Boolean.parseBoolean(disableLegacyBoxcarringModeStr);
            }

            /* txManagerName property */
            String userTxJNDIName = dbsElement.getAttributeValue(
                    new QName(DBSFields.TRANSACTION_MANAGER_JNDI_NAME));

            dataService = new DataService(serviceName, description,
                                          defaultNamespace, dsLocation, serviceStatus,
                                          batchRequestsEnabled, boxcarringEnabled,
                                          userTxJNDIName);
            /* setting authorization provider */
            OMElement authorizationProviderElement = dbsElement.getFirstChildWithName(
                    new QName(AuthorizationProviderConfig.ELEMENT_NAME_AUTHORIZATION_PROVIDER));
            AuthorizationProvider authorizationProvider;
            if (authorizationProviderElement != null) {
                authorizationProvider = DBUtils.generateAuthProviderFromXMLOMElement(authorizationProviderElement);
            } else {
                authorizationProvider = new UserStoreAuthorizationProvider();
            }

            dataService.setAuthorizationProvider(authorizationProvider);

            /* set service namespace */
            dataService.setServiceNamespace(serviceNamespace);

            /* set disable streaming */
            dataService.setDisableStreaming(disableStreaming);

            /* set disable legacy boxcarring mode */
            dataService.setDisableLegacyBoxcarringMode(disableLegacyBoxcarringMode);

            /* set transports */
            String transports = dbsElement.getAttributeValue(new QName(DBSFields.TRANSPORTS));
            if (transports != null && !transports.isEmpty()) {
                List<String> transportsList = Arrays.asList(transports.split("\\s"));
                dataService.setTransports(transportsList);
            }

            /* add the password manager */
            Iterator<OMElement> passwordMngrItr = dbsElement.getChildrenWithName(
                    new QName(SecurityConstants.PASSWORD_MANAGER_SIMPLE));
            if (passwordMngrItr.hasNext()) {
                SecretResolver secretResolver = SecretResolverFactory.create(dbsElement, false);
                dataService.setSecretResolver(secretResolver);
            }

            /* add the configs */
            for (Iterator<OMElement> itr = dbsElement.getChildrenWithName(
                    new QName(DBSFields.CONFIG)); itr.hasNext();) {
                dataService.addConfig(ConfigFactory.createConfig(dataService, itr.next()));
            }

            /* add event triggers */
            for (Iterator<OMElement> itr = dbsElement.getChildrenWithName(
                    new QName(DBSFields.EVENT_TRIGGER)); itr.hasNext();) {
                dataService.addEventTrigger(
                        EventTriggerFactory.createEventTrigger(dataService, itr.next()));
            }

            /* add the queries */
            for (Iterator<OMElement> itr = dbsElement
                    .getChildrenWithName(new QName(DBSFields.QUERY)); itr.hasNext();) {
                dataService.addQuery(QueryFactory.createQuery(dataService, itr.next()));
            }

            /* add the operations */
            for (Iterator<OMElement> itr = dbsElement
                    .getChildrenWithName(new QName(DBSFields.OPERATION)); itr.hasNext();) {
                dataService.addOperation(OperationFactory.createOperation(dataService,
                                                                          itr.next()));
            }

            /* add the resources */
            for (Iterator<OMElement> itr = dbsElement.getChildrenWithName(
                    new QName(DBSFields.RESOURCE)); itr.hasNext();) {
                dataService.addResource(ResourceFactory.createResource(dataService,
                                                                       itr.next()));
            }

            String swaggerLocation = dbsElement.getAttributeValue(new QName(DBSFields.SWAGGER_LOCATION));

            if (StringUtils.isNotEmpty(swaggerLocation)) {
                if (dataService.getResourceIds().isEmpty()) {
                    throw new DataServiceFault("Cannot expose a swagger from a data-service which does not have any " +
                            "resources defined");
                }
                dataService.setSwaggerResourcePath(swaggerLocation);
            }

            /* init the data service object */
            dataService.init();

            /* add necessary equivalent batch requests for the above defined operations/resources */
            if (dataService.isBatchRequestsEnabled()) {
                populateBatchOperations(dataService);
                populateBatchResources(dataService);
            }
            /* initialising requestBox operations and resources */
//            dataService.initRequestBox();

            return dataService;
        } catch (DataServiceFault e) {
            /* the exception is caught to fill in the data service deployment exception details */
            e.setSourceDataService(dataService);
            throw e;
        } catch (Exception e) {
            /* if an unexpected exception has occurred */
            DataServiceFault dsf = new DataServiceFault(e);
            dsf.setSourceDataService(dataService);
            throw dsf;
        }
    }
    
    private static void populateBatchOperations(DataService dataService) {
        List<Operation> tmpOpList = new ArrayList<Operation>();
        Operation operation;
        for (String opName : dataService.getOperationNames()) {
            if (isBoxcarringOps(opName)) {
                /* skip boxcarring operations */
                continue;
            }
            operation = dataService.getOperation(opName);
            if (isBatchCompatible(operation)) {
                /* this is a batch operation and the parent operation is also given */
                Operation batchOp = new Operation(
                        operation.getDataService(),
                        operation.getName() + DBConstants.BATCH_OPERATON_NAME_SUFFIX,
                        "batch operation for '" + operation.getName() + "'",
                        operation.getCallQuery(), true,
                        operation, operation.isDisableStreamingRequest(),
                        operation.isDisableStreamingEffective());
                batchOp.setReturnRequestStatus(operation.isReturnRequestStatus());
                tmpOpList.add(batchOp);
            }
        }
        /* the operations are added outside the loop that iterates the operation list,
         * if we add it inside the loop while iterating, we will get a concurrent modification exception */
        for (Operation tmpOp : tmpOpList) {
            dataService.addOperation(tmpOp);
        }
    }
    
    private static String getBatchResourcePath(String path) {
        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1) + DBConstants.BATCH_OPERATON_NAME_SUFFIX + "/";
        } else {
            return path + DBConstants.BATCH_OPERATON_NAME_SUFFIX;
        }
    }
    
    private static void populateBatchResources(DataService dataService) {
        List<Resource> tmpOpList = new ArrayList<Resource>();
        Resource resource;
        Resource.ResourceID batchResId;
        for (Resource.ResourceID resId : dataService.getResourceIds()) {
            resource = dataService.getResource(resId);
            if (isBatchCompatible(resource)) {
                batchResId = new Resource.ResourceID(getBatchResourcePath(resId.getPath()), resId.getMethod());
                Resource batchRes = new Resource(
                        resource.getDataService(), batchResId,
                        "batch resource for [" + resId.getMethod() + ":" + resId.getPath() + "]",
                        resource.getCallQuery(), true, resource,
                        resource.isDisableStreamingRequest(),
                        resource.isDisableStreamingEffective());
                batchRes.setReturnRequestStatus(resource.isReturnRequestStatus());
                tmpOpList.add(batchRes);
            }
        }
        for (Resource tmpRes : tmpOpList) {
            dataService.addResource(tmpRes);
        }
    }

    /**
     * Checks if the given operation is related to boxcarring.
     */
    private static boolean isBoxcarringOps(String opName) {
        return opName.equals(BoxcarringOps.BEGIN_BOXCAR) ||
                opName.equals(BoxcarringOps.END_BOXCAR) ||
                opName.equals(BoxcarringOps.ABORT_BOXCAR);
    }

    /**
     * Checks if the given data service request is batch request compatible,
     * i.e. does not have a result.
     */
    private static boolean isBatchCompatible(CallableRequest request) {
        if (request.getCallQuery().getWithParams().size() == 0) {
            return false;
        }
        boolean isReturnGeneratedKeys = false;
        if (request.getCallQuery().getQuery() instanceof SQLQuery) {
            isReturnGeneratedKeys = ((SQLQuery) request.getCallQuery().getQuery()).isReturnGeneratedKeys();
        }
        return !request.getCallQuery().getQuery().hasResult() || isReturnGeneratedKeys;
    }

}
