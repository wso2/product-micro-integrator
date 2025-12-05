/*
 * Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.management.apis;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.api.API;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.SequenceMediatorSerializer;
import org.apache.synapse.config.xml.endpoints.EndpointSerializer;
import org.apache.synapse.config.xml.rest.APISerializer;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.task.TaskDescription;
import org.apache.synapse.task.TaskDescriptionSerializer;
import org.apache.synapse.Startup;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.config.xml.ProxyServiceSerializer;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.synapse.config.xml.inbound.InboundEndpointSerializer;
import org.apache.synapse.message.processor.MessageProcessor;
import org.apache.synapse.message.store.MessageStore;
import org.apache.synapse.config.xml.MessageProcessorSerializer;
import org.apache.synapse.config.xml.MessageStoreSerializer;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Resource handler for retrieving synapse configuration of artifacts.
 * Supports endpoint: /icp/artifacts?type={artifactType}&name={artifactName}
 */
public class ICPArtifactResource extends APIResource {

    private static final Log LOG = LogFactory.getLog(ICPArtifactResource.class);

    private static final String PARAM_TYPE = "type";
    private static final String PARAM_NAME = "name";

    // Artifact types
    private static final String TYPE_API = "api";
    private static final String TYPE_PROXY = "proxy-service";
    private static final String TYPE_ENDPOINT = "endpoint";
    private static final String TYPE_SEQUENCE = "sequence";
    private static final String TYPE_TASK = "task";
    private static final String TYPE_INBOUND_ENDPOINT = "inbound-endpoint";
    private static final String TYPE_MESSAGE_PROCESSOR = "message-processor";
    private static final String TYPE_MESSAGE_STORE = "message-store";

    public ICPArtifactResource(String urlTemplate) {
        super(urlTemplate);
    }

    @Override
    public Set<String> getMethods() {
        Set<String> methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext) {
        buildMessage(messageContext);
        
        org.apache.axis2.context.MessageContext axisMsgCtx =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        if (!messageContext.isDoingGET()) {
            Utils.setJsonPayLoad(axisMsgCtx, 
                Utils.createJsonError("Only GET method is supported", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        String artifactType = Utils.getQueryParameter(messageContext, PARAM_TYPE);
        String artifactName = Utils.getQueryParameter(messageContext, PARAM_NAME);

        if (Objects.isNull(artifactType) || artifactType.trim().isEmpty()) {
            Utils.setJsonPayLoad(axisMsgCtx, 
                Utils.createJsonError("Missing required parameter: type", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        if (Objects.isNull(artifactName) || artifactName.trim().isEmpty()) {
            Utils.setJsonPayLoad(axisMsgCtx, 
                Utils.createJsonError("Missing required parameter: name", axisMsgCtx, Constants.BAD_REQUEST));
            return true;
        }

        try {
            JSONObject response = getArtifactConfiguration(messageContext, artifactType.toLowerCase(), artifactName, axisMsgCtx);
            
            if (Objects.nonNull(response)) {
                Utils.setJsonPayLoad(axisMsgCtx, response);
            } else {
                axisMsgCtx.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
                Utils.setJsonPayLoad(axisMsgCtx, 
                    Utils.createJsonError("Artifact not found: " + artifactType + " - " + artifactName, 
                        axisMsgCtx, Constants.NOT_FOUND));
            }
        } catch (Exception e) {
            LOG.error("Error retrieving artifact configuration", e);
            Utils.setJsonPayLoad(axisMsgCtx, 
                Utils.createJsonError("Error retrieving artifact configuration: " + e.getMessage(), 
                    axisMsgCtx, Constants.INTERNAL_SERVER_ERROR));
        }

        return true;
    }

    /**
     * Retrieves the synapse configuration for the specified artifact.
     *
     * @param messageContext the message context
     * @param artifactType   the type of artifact (api, proxy-service, endpoint, sequence, etc.)
     * @param artifactName   the name of the artifact
     * @param axisMsgCtx     the axis2 message context
     * @return JSONObject containing the artifact configuration, or null if not found
     */
    private JSONObject getArtifactConfiguration(MessageContext messageContext, String artifactType, 
                                                String artifactName, org.apache.axis2.context.MessageContext axisMsgCtx) {
        SynapseConfiguration synapseConfig = messageContext.getConfiguration();
        JSONObject response = new JSONObject();
        
        response.put(Constants.NAME, artifactName);
        response.put(Constants.TYPE, artifactType);

        OMElement configuration = null;

        switch (artifactType) {
            case TYPE_API:
                configuration = getAPIConfiguration(synapseConfig, artifactName);
                break;
            case TYPE_PROXY:
                configuration = getProxyServiceConfiguration(synapseConfig, artifactName);
                break;
            case TYPE_ENDPOINT:
                configuration = getEndpointConfiguration(synapseConfig, artifactName);
                break;
            case TYPE_SEQUENCE:
                configuration = getSequenceConfiguration(synapseConfig, artifactName);
                break;
            case TYPE_TASK:
                configuration = getTaskConfiguration(synapseConfig, artifactName, axisMsgCtx);
                break;
            case TYPE_INBOUND_ENDPOINT:
                configuration = getInboundEndpointConfiguration(synapseConfig, artifactName);
                break;
            case TYPE_MESSAGE_PROCESSOR:
                configuration = getMessageProcessorConfiguration(synapseConfig, artifactName);
                break;
            case TYPE_MESSAGE_STORE:
                configuration = getMessageStoreConfiguration(synapseConfig, artifactName);
                break;
            default:
                LOG.warn("Unsupported artifact type: " + artifactType);
                return null;
        }

        if (Objects.nonNull(configuration)) {
            response.put(Constants.SYNAPSE_CONFIGURATION, configuration.toString());
            return response;
        }

        return null;
    }

    private OMElement getAPIConfiguration(SynapseConfiguration synapseConfig, String apiName) {
        API api = synapseConfig.getAPI(apiName);
        if (Objects.nonNull(api)) {
            return APISerializer.serializeAPI(api);
        }
        return null;
    }

    private OMElement getProxyServiceConfiguration(SynapseConfiguration synapseConfig, String proxyName) {
        ProxyService proxyService = synapseConfig.getProxyService(proxyName);
        if (Objects.nonNull(proxyService)) {
            return ProxyServiceSerializer.serializeProxy(null, proxyService);
        }
        return null;
    }

    private OMElement getEndpointConfiguration(SynapseConfiguration synapseConfig, String endpointName) {
        Endpoint endpoint = synapseConfig.getEndpoint(endpointName);
        if (Objects.nonNull(endpoint)) {
            return EndpointSerializer.getElementFromEndpoint(endpoint);
        }
        return null;
    }

    private OMElement getSequenceConfiguration(SynapseConfiguration synapseConfig, String sequenceName) {
        SequenceMediator sequence = synapseConfig.getDefinedSequences().get(sequenceName);
        if (Objects.nonNull(sequence)) {
            return new SequenceMediatorSerializer().serializeSpecificMediator(sequence);
        }
        return null;
    }

    private OMElement getTaskConfiguration(SynapseConfiguration synapseConfig, String taskName,
                                           org.apache.axis2.context.MessageContext axisMsgCtx) {
        Startup startup = synapseConfig.getStartup(taskName);
        if (Objects.nonNull(startup)) {
            // Get the task description from the task manager
            AxisConfiguration axisConfig = axisMsgCtx.getConfigurationContext().getAxisConfiguration();
            SynapseEnvironment synapseEnvironment = 
                (SynapseEnvironment) axisConfig.getParameter(SynapseConstants.SYNAPSE_ENV).getValue();
            
            if (Objects.nonNull(synapseEnvironment) && 
                Objects.nonNull(synapseEnvironment.getTaskManager())) {
                TaskDescription task = synapseEnvironment.getTaskManager()
                    .getTaskDescriptionRepository().getTaskDescription(taskName);
                if (Objects.nonNull(task)) {
                    return TaskDescriptionSerializer.serializeTaskDescription(null, task);
                }
            }
        }
        return null;
    }

    private OMElement getInboundEndpointConfiguration(SynapseConfiguration synapseConfig, String inboundName) {
        InboundEndpoint inboundEndpoint = synapseConfig.getInboundEndpoint(inboundName);
        if (Objects.nonNull(inboundEndpoint)) {
            return InboundEndpointSerializer.serializeInboundEndpoint(inboundEndpoint);
        }
        return null;
    }

    private OMElement getMessageProcessorConfiguration(SynapseConfiguration synapseConfig, 
                                                       String messageProcessorName) {
        MessageProcessor messageProcessor = synapseConfig.getMessageProcessors().get(messageProcessorName);
        if (Objects.nonNull(messageProcessor)) {
            return MessageProcessorSerializer.serializeMessageProcessor(null, messageProcessor);
        }
        return null;
    }

    private OMElement getMessageStoreConfiguration(SynapseConfiguration synapseConfig, String messageStoreName) {
        MessageStore messageStore = synapseConfig.getMessageStore(messageStoreName);
        if (Objects.nonNull(messageStore)) {
            return MessageStoreSerializer.serializeMessageStore(null, messageStore, true);
        }
        return null;
    }
}
