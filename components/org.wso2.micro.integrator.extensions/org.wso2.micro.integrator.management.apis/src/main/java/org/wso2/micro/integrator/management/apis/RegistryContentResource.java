/**
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 */

package org.wso2.micro.integrator.management.apis;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMText;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.transport.passthru.util.RelayConstants;
import org.apache.synapse.transport.passthru.util.StreamingOnRequestDataSource;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.management.apis.security.handler.SecurityUtils;
import org.wso2.micro.integrator.registry.MicroIntegratorRegistry;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;

import static org.wso2.micro.integrator.management.apis.Constants.ANONYMOUS_USER;
import static org.wso2.micro.integrator.management.apis.Constants.AUDIT_LOG_ACTION_CREATED;
import static org.wso2.micro.integrator.management.apis.Constants.AUDIT_LOG_ACTION_DELETED;
import static org.wso2.micro.integrator.management.apis.Constants.AUDIT_LOG_ACTION_UPDATED;
import static org.wso2.micro.integrator.management.apis.Constants.AUDIT_LOG_TYPE_REGISTRY_RESOURCE;
import static org.wso2.micro.integrator.management.apis.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.management.apis.Constants.CONTENT_TYPE;
import static org.wso2.micro.integrator.management.apis.Constants.CONTENT_TYPE_MULTIPART_FORM_DATA;
import static org.wso2.micro.integrator.management.apis.Constants.DEFAULT_MEDIA_TYPE;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_DELETE;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_GET;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_POST;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_PUT;
import static org.wso2.micro.integrator.management.apis.Constants.INTERNAL_SERVER_ERROR;
import static org.wso2.micro.integrator.management.apis.Constants.MAXIMUM_RETRY_COUNT;
import static org.wso2.micro.integrator.management.apis.Constants.MEDIA_TYPE_KEY;
import static org.wso2.micro.integrator.management.apis.Constants.NO_ENTITY_BODY;
import static org.wso2.micro.integrator.management.apis.Constants.REGISTRY_PATH;
import static org.wso2.micro.integrator.management.apis.Constants.REGISTRY_RESOURCE_NAME;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;
import static org.wso2.micro.integrator.management.apis.Utils.formatPath;
import static org.wso2.micro.integrator.management.apis.Utils.getPayload;
import static org.wso2.micro.integrator.management.apis.Utils.getPayloadFromMultipart;
import static org.wso2.micro.integrator.management.apis.Utils.getRegistryPathPrefix;
import static org.wso2.micro.integrator.management.apis.Utils.getResourceName;
import static org.wso2.micro.integrator.management.apis.Utils.isRegistryExist;
import static org.wso2.micro.integrator.management.apis.Utils.validatePath;

/**
 * This class provides mechanisms to monitor content of the registry content.
 */
public class RegistryContentResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(RegistryContentResource.class);
    Set<String> methods;

    public RegistryContentResource() {

        methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_POST);
        methods.add(Constants.HTTP_PUT);
        methods.add(Constants.HTTP_DELETE);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
            SynapseConfiguration synapseConfiguration) {

        String httpMethod = axis2MessageContext.getProperty(Constants.HTTP_METHOD_PROPERTY).toString();
        String registryPath = Utils.getQueryParameter(messageContext, REGISTRY_PATH);
        String validatedPath = validatePath(registryPath, axis2MessageContext, messageContext);

        if (StringUtils.isEmpty(validatedPath)) {
            axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
            return true;
        }

        if (httpMethod.equals(HTTP_GET)) {
            handleGet(messageContext, axis2MessageContext, registryPath, validatedPath);
        } else {
            try {
                axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
                String username = Utils.getStringPropertyFromMessageContext(messageContext, USERNAME_PROPERTY);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Processing registry " + httpMethod + " request for user: " + username);
                }
                if (username != null && SecurityUtils.isAdmin(messageContext, username)) {
                    switch (httpMethod) {
                    case HTTP_POST:
                        handlePost(messageContext, axis2MessageContext, registryPath, validatedPath);
                        break;
                    case HTTP_PUT:
                        handlePut(messageContext, axis2MessageContext, registryPath, validatedPath);
                        break;
                    case HTTP_DELETE:
                        handleDelete(messageContext, axis2MessageContext, registryPath, validatedPath);
                        break;
                    default:
                        JSONObject jsonBody = Utils.createJsonError("Unsupported HTTP method, " + httpMethod
                                        + ". Only GET, POST, PUT and DELETE methods are supported", axis2MessageContext,
                                BAD_REQUEST);
                        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
                        break;
                    }
                } else {
                    Utils.sendForbiddenFaultResponse(axis2MessageContext);
                }
            } catch (UserStoreException e) {
                JSONObject jsonBody = Utils.createJsonError("Error occurs while retrieving the user data",
                        axis2MessageContext, INTERNAL_SERVER_ERROR);
                Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
            }
        }
        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
        return true;
    }

    /**
     * This method handles GET.
     *
     * @param messageContext      Synapse message context
     * @param axis2MessageContext AXIS2 message context
     */
    private void handleGet(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
            String registryPath, String validatedPath) {

        if (!isRegistryExist(validatedPath, messageContext)) {
            JSONObject jsonBody = Utils.createJsonError("Can not find the registry: " + registryPath,
                    axis2MessageContext, BAD_REQUEST);
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            populateRegistryContent(messageContext, axis2MessageContext, validatedPath);
        }
    }

    /**
     * This method is to get the content of the specified registry file.
     *
     * @param synCtx              Synapse message context
     * @param axis2MessageContext AXIS2 message context
     * @param pathParameter       Registry path
     */
    private void populateRegistryContent(MessageContext synCtx,
            org.apache.axis2.context.MessageContext axis2MessageContext, String pathParameter) {

        DataHandler dataHandler = downloadRegistryFiles(pathParameter, synCtx);
        if (dataHandler != null) {
            try {
                InputStream fileInput = dataHandler.getInputStream();
                SOAPFactory factory = OMAbstractFactory.getSOAP12Factory();
                SOAPEnvelope env = factory.getDefaultEnvelope();
                OMNamespace ns = factory.createOMNamespace(RelayConstants.BINARY_CONTENT_QNAME.getNamespaceURI(), "ns");
                OMElement omEle = factory.createOMElement(RelayConstants.BINARY_CONTENT_QNAME.getLocalPart(), ns);
                StreamingOnRequestDataSource ds = new StreamingOnRequestDataSource(fileInput);
                dataHandler = new DataHandler(ds);
                OMText textData = factory.createOMText(dataHandler, true);
                omEle.addChild(textData);
                env.getBody().addChild(omEle);
                synCtx.setEnvelope(env);
                axis2MessageContext.removeProperty(NO_ENTITY_BODY);
                axis2MessageContext.setProperty(Constants.MESSAGE_TYPE, "application/octet-stream");
                axis2MessageContext.setProperty(Constants.CONTENT_TYPE, "application/txt");
            } catch (AxisFault e) {
                LOG.error("Error occurred while creating the response", e);
                JSONObject jsonBody = Utils.createJsonError("Error occurred while creating the response",
                        axis2MessageContext, INTERNAL_SERVER_ERROR);
                Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
            } catch (IOException e) {
                LOG.error("Error occurred while reading the input stream", e);
                JSONObject jsonBody = Utils.createJsonError("Error occurred while reading the input stream",
                        axis2MessageContext, INTERNAL_SERVER_ERROR);
                Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
            }
        } else {
            JSONObject jsonBody = Utils.createJsonError("Error occurred while reading the requested file",
                    axis2MessageContext, INTERNAL_SERVER_ERROR);
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        }
    }

    /**
     * This method fetches content from the registry.
     *
     * @param path  Registry path
     * @return      DataHandler object with registry content
     */
    private DataHandler downloadRegistryFiles(String path, MessageContext messageContext) {

        ByteArrayDataSource bytArrayDS;
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        String regRoot = microIntegratorRegistry.getRegRoot();
        String registryPath = formatPath(regRoot + File.separator + path);
        File file = new File(registryPath);
        if (file.exists() && !file.isDirectory()) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(registryPath))) {
                bytArrayDS = new ByteArrayDataSource(is, "text/xml");
                return new DataHandler(bytArrayDS);
            } catch (FileNotFoundException e) {
                LOG.error("Could not find the requested file : " + path + " in : " + registryPath, e);
                return null;
            } catch (IOException e) {
                LOG.error("Error occurred while reading the file : " + path + " in : " + registryPath, e);
                return null;
            }
        } else {
            LOG.error("Could not find the requested file : " + path + " in : " + registryPath);
            return null;
        }
    }

    /**
     * This method handles POST.
     *
     * @param messageContext      Synapse message context
     * @param axis2MessageContext AXIS2 message context
     */
    private void handlePost(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath, String validatedPath) {

        JSONObject jsonBody;
        String pathWithPrefix = getRegistryPathPrefix(validatedPath);
        if (isRegistryExist(validatedPath, messageContext)) {
            jsonBody = Utils.createJsonError("Registry already exists. Can not POST an existing registry",
                    axis2MessageContext, BAD_REQUEST);
        } else if (Objects.nonNull(pathWithPrefix)) {
            jsonBody = postRegistryArtifact(messageContext, axis2MessageContext, pathWithPrefix);
        } else {
            jsonBody = Utils.createJsonError("Invalid registry path: " + registryPath, axis2MessageContext,
                    BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * This method handles adding registry resources.
     *
     * @param messageContext        Synapse message context
     * @param axis2MessageContext   AXIS2 message context
     * @param registryPath          Registry path
     * @return                      A JSON object with the final status
     */
    private JSONObject postRegistryArtifact(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath) {

        String contentType = axis2MessageContext.getProperty(CONTENT_TYPE).toString();
        String mediaType = Utils.getQueryParameter(messageContext, MEDIA_TYPE_KEY);
        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (Objects.isNull(mediaType)) {
            mediaType = DEFAULT_MEDIA_TYPE;
        }
        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);

        JSONObject jsonBody = new JSONObject();
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        if (Objects.nonNull(contentType) && contentType.contains(CONTENT_TYPE_MULTIPART_FORM_DATA)) {
            byte[] fileContent = getPayloadFromMultipart(messageContext);
            if (Objects.nonNull(fileContent)) {
                microIntegratorRegistry.addMultipartResource(registryPath, mediaType, fileContent);
                jsonBody.put("message", "Successfully added the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_CREATED, info);
            } else {
                jsonBody = Utils.createJsonError("Error while fetching file content from payload", axis2MessageContext,
                        BAD_REQUEST);
            }
        } else if (Objects.nonNull(contentType)) {
            String fileContent = getPayload(axis2MessageContext);
            if (Objects.nonNull(fileContent)) {
                microIntegratorRegistry.newNonEmptyResource(registryPath, false, mediaType, fileContent, "");
                jsonBody.put("message", "Successfully added the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_CREATED, info);
            } else {
                jsonBody = Utils.createJsonError("Error while fetching file content from payload", axis2MessageContext,
                        BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Error while fetching Content-Type from request", axis2MessageContext,
                    BAD_REQUEST);
        }
        return jsonBody;
    }

    /**
     * This method handles PUT.
     *
     * @param messageContext      Synapse message context
     * @param axis2MessageContext AXIS2 message context
     */
    private void handlePut(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
            String registryPath, String validatedPath) {

        JSONObject jsonBody;
        String pathWithPrefix = getRegistryPathPrefix(validatedPath);
        if (!isRegistryExist(validatedPath, messageContext)) {
            jsonBody = Utils.createJsonError("Registry does not exists in the path: " + registryPath,
                    axis2MessageContext, BAD_REQUEST);
        } else if (Objects.nonNull(pathWithPrefix)) {
            jsonBody = putRegistryArtifact(axis2MessageContext, messageContext, pathWithPrefix);
        } else {
            jsonBody = Utils.createJsonError("Invalid registry path: " + registryPath, axis2MessageContext,
                    BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * This method handles modifying registry resources.
     *
     * @param axis2MessageContext   AXIS2 message context
     * @param registryPath          Registry path
     * @return                      A JSON object with the final status
     */
    private JSONObject putRegistryArtifact(org.apache.axis2.context.MessageContext axis2MessageContext,
            MessageContext messageContext, String registryPath) {

        String contentType = axis2MessageContext.getProperty(CONTENT_TYPE).toString();
        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);

        JSONObject jsonBody = new JSONObject();
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        if (Objects.nonNull(contentType) && contentType.contains(CONTENT_TYPE_MULTIPART_FORM_DATA)) {
            byte[] fileContent = getPayloadFromMultipart(messageContext);
            if (Objects.nonNull(fileContent)) {
                microIntegratorRegistry.addMultipartResource(registryPath, null, fileContent);
                jsonBody.put("message", "Successfully modified the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_UPDATED, info);
            } else {
                jsonBody = Utils.createJsonError("Error while fetching file content from payload", axis2MessageContext,
                        BAD_REQUEST);
            }
        } else if (Objects.nonNull(contentType)) {
            String fileContent = getPayload(axis2MessageContext);
            if (Objects.nonNull(fileContent)) {
                microIntegratorRegistry.updateResource(registryPath, fileContent);
                jsonBody.put("message", "Successfully modified the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_UPDATED, info);
            } else {
                jsonBody = Utils.createJsonError("Error while fetching file content from payload", axis2MessageContext,
                        BAD_REQUEST);
            }
        } else {
            jsonBody = Utils.createJsonError("Error while fetching Content-Type from request", axis2MessageContext,
                    BAD_REQUEST);
        }
        return jsonBody;
    }

    /**
     * This method handles DELETE.
     *
     * @param messageContext      Synapse message context
     * @param axis2MessageContext AXIS2 message context
     */
    private void handleDelete(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath, String validatedPath) {

        JSONObject jsonBody;
        String pathWithPrefix = getRegistryPathPrefix(validatedPath);
        if (!isRegistryExist(validatedPath, messageContext)) {
            jsonBody = Utils.createJsonError("Registry does not exists in the path: " + registryPath,
                    axis2MessageContext, BAD_REQUEST);
        } else if (Objects.nonNull(pathWithPrefix)) {
            jsonBody = deleteRegistryArtifact(messageContext, axis2MessageContext, pathWithPrefix, validatedPath);
        } else {
            jsonBody = Utils.createJsonError("Invalid registry path: " + registryPath, axis2MessageContext,
                    BAD_REQUEST);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * Deletes the registry resources and directories.
     *
     * @param registryPath  Registry path
     * @return              JSON object indicating the final status
     */
    private JSONObject deleteRegistryArtifact(MessageContext messageContext,
            org.apache.axis2.context.MessageContext axis2MessageContext, String registryPath, String validatedPath) {

        String performedBy = ANONYMOUS_USER;
        JSONObject info = new JSONObject();

        if (messageContext.getProperty(USERNAME_PROPERTY) !=  null) {
            performedBy = messageContext.getProperty(USERNAME_PROPERTY).toString();
        }
        String name = getResourceName(registryPath);
        info.put(REGISTRY_RESOURCE_NAME, name);
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();

        for (int i = 0; i < MAXIMUM_RETRY_COUNT; i++) {
            microIntegratorRegistry.delete(registryPath);
            if (!isRegistryExist(validatedPath, messageContext)) {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("message", "Successfully deleted the registry resource");
                AuditLogger.logAuditMessage(performedBy, AUDIT_LOG_TYPE_REGISTRY_RESOURCE,
                        AUDIT_LOG_ACTION_DELETED, info);
                return jsonBody;
            }
        }
        return Utils.createJsonError("Seems that the file still exists but unable to delete the resource",
                axis2MessageContext, INTERNAL_SERVER_ERROR);
    }
}
