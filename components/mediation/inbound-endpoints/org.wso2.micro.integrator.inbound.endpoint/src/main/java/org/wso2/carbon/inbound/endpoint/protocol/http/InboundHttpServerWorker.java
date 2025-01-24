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
package org.wso2.carbon.inbound.endpoint.protocol.http;

import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.InOutAxisOperation;
import org.apache.axis2.util.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.protocol.HTTP;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.api.ApiConstants;
import org.apache.synapse.api.inbound.InboundApiHandler;
import org.apache.synapse.aspects.flow.statistics.collectors.RuntimeStatisticCollector;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.core.axis2.MessageContextCreatorForAxis2;
import org.apache.synapse.core.axis2.ResponseState;
import org.apache.synapse.core.axis2.SynapseMessageReceiver;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.inbound.InboundEndpointConstants;
import org.apache.synapse.mediators.MediatorFaultHandler;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.rest.RESTRequestHandler;
import org.apache.synapse.transport.customlogsetter.CustomLogSetter;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.ServerWorker;
import org.apache.synapse.transport.passthru.SourceRequest;
import org.apache.synapse.transport.passthru.config.SourceConfiguration;
import org.wso2.carbon.inbound.endpoint.protocol.http.management.HTTPEndpointManager;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Create SynapseMessageContext from HTTP Request and inject it to the sequence in a synchronous manner
 * This is the worker for HTTP inbound related requests.
 */
public class InboundHttpServerWorker extends ServerWorker {

    private static final Log log = LogFactory.getLog(InboundHttpServerWorker.class);

    private SourceRequest request;
    private int port;
    private String tenantDomain;
    private InboundApiHandler inboundApiHandler;
    private RESTRequestHandler restHandler;
    private Pattern dispatchPattern;
    private Matcher patternMatcher;
    private boolean isInternalHttpInboundEndpoint;
    private boolean isInternalHttpsInboundEndpoint;

    public InboundHttpServerWorker(int port, String tenantDomain, SourceRequest sourceRequest,
                                   SourceConfiguration sourceConfiguration, OutputStream outputStream) {
        super(sourceRequest, sourceConfiguration, outputStream);
        this.request = sourceRequest;
        this.port = port;
        this.tenantDomain = tenantDomain;
        inboundApiHandler = new InboundApiHandler();
        restHandler = new RESTRequestHandler();
        isInternalHttpInboundEndpoint = (HTTPEndpointManager.getInstance().getInternalInboundHttpPort() == port);
        isInternalHttpsInboundEndpoint = (HTTPEndpointManager.getInstance().getInternalInboundHttpsPort() == port);
    }

    public void run() {
        if (request != null) {
            try {
                //get already created axis2 context from ServerWorker
                MessageContext axis2MsgContext = getRequestContext();

                //create Synapse Message Context
                org.apache.synapse.MessageContext synCtx = createSynapseMessageContext(request, axis2MsgContext);
                updateAxis2MessageContextForSynapse(synCtx);

                setInboundProperties(synCtx);
                // setting ResponseState for http inbound endpoint request
                synCtx.setProperty(SynapseConstants.RESPONSE_STATE, new ResponseState());
                String method = request.getRequest() != null ? request.getRequest().
                        getRequestLine().getMethod().toUpperCase() : "";
                processHttpRequestUri(axis2MsgContext, method);

                if (isInternalHttpInboundEndpoint) {
                    doPreInjectTasks(axis2MsgContext, (Axis2MessageContext) synCtx, method);
                    boolean result = HTTPEndpointManager.getInstance().getInternalHttpApiDispatcher().dispatch(synCtx);
                    respond(synCtx, result);
                    return;
                }

                if (isInternalHttpsInboundEndpoint) {
                    doPreInjectTasks(axis2MsgContext, (Axis2MessageContext) synCtx, method);
                    boolean result = HTTPEndpointManager.getInstance().getInternalHttpsApiDispatcher().dispatch(synCtx);
                    respond(synCtx, result);
                    return;
                }

                String endpointName = HTTPEndpointManager.getInstance().getEndpointName(port, tenantDomain);
                if (endpointName == null) {
                    handleException("Endpoint not found for port : " + port + "" + " tenant domain : " + tenantDomain);
                }
                InboundEndpoint endpoint = synCtx.getConfiguration().getInboundEndpoint(endpointName);

                if (endpoint == null) {
                    log.error("Cannot find deployed inbound endpoint " + endpointName + "for process request");
                    return;
                }

                CustomLogSetter.getInstance().setLogAppender(endpoint.getArtifactContainerName());

                doPreInjectTasks(axis2MsgContext, (Axis2MessageContext) synCtx, method);

                synCtx.setProperty(ApiConstants.API_CALLER, endpoint.getName());
                boolean isProcessed = inboundApiHandler.process(synCtx);

                if (!isProcessed) {
                    dispatchPattern = HTTPEndpointManager.getInstance().getPattern(tenantDomain, port);

                    boolean continueDispatch = true;
                    if (dispatchPattern != null) {
                        patternMatcher = dispatchPattern.matcher(request.getUri());
                        if (!patternMatcher.matches()) {
                            if (log.isDebugEnabled()) {
                                log.debug("Requested URI does not match given dispatch regular expression.");
                            }
                            continueDispatch = false;
                        }
                    }
                    if (continueDispatch && dispatchPattern != null) {
                        boolean processedByAPI = false;

                        // Trying to dispatch to an API

                        // Remove the API_CALLER property from the Synapse Context
                        Set properties = synCtx.getPropertyKeySet();
                        if (properties != null) {
                            properties.remove(ApiConstants.API_CALLER);
                        }
                        processedByAPI = restHandler.process(synCtx);
                        if (log.isDebugEnabled()) {
                            log.debug("Dispatch to API state : enabled, Message is "
                                    + (!processedByAPI ? "NOT" : "") + "processed by an API");
                        }

                        if (!processedByAPI) {
                            //check the validity of message routing to axis2 path
                            boolean isAxis2Path = isAllowedAxis2Path(synCtx);

                            if (isAxis2Path) {
                                //create axis2 message context again to avoid settings updated above
                                axis2MsgContext = createMessageContext(null, request);

                                processHttpRequestUri(axis2MsgContext, method);

                                //set inbound properties for axis2 context
                                setInboundProperties(axis2MsgContext);

                                if (!isRESTRequest(axis2MsgContext, method)) {
                                    if (request.isEntityEnclosing()) {
                                        processEntityEnclosingRequest(axis2MsgContext, isAxis2Path);
                                    } else {
                                        processNonEntityEnclosingRESTHandler(null, axis2MsgContext, isAxis2Path);
                                    }
                                } else {
                                    String contentTypeHeader = request.getHeaders().get(HTTP.CONTENT_TYPE);
                                    SOAPEnvelope soapEnvelope = handleRESTUrlPost(contentTypeHeader);
                                    processNonEntityEnclosingRESTHandler(soapEnvelope, axis2MsgContext, true);
                                }
                            } else {
                                //this case can only happen regex exists and it DOES match
                                //BUT there is no api or proxy found message to be injected
                                //should be routed to the main sequence instead inbound defined sequence
                                injectToMainSequence(synCtx, endpoint);
                            }
                        }
                    } else if (continueDispatch && dispatchPattern == null) {
                        // else if for clarity compiler will optimize
                        injectToSequence(synCtx, endpoint);
                    } else {
                        //this case can only happen regex exists and it DOES NOT match
                        //should be routed to the main sequence instead inbound defined sequence
                        injectToMainSequence(synCtx, endpoint);
                    }
                }
                SynapseMessageReceiver.doPostInjectUpdates(synCtx);
                // send ack for client if needed
                sendAck(axis2MsgContext);
            } catch (Exception e) {
                log.error("Exception occurred when running " + InboundHttpServerWorker.class.getName(), e);
            }
        } else {
            log.error("InboundSourceRequest cannot be null");
        }
    }

    private void doPreInjectTasks(MessageContext axis2MsgContext, Axis2MessageContext synCtx, String method) {

        if (!isRESTRequest(axis2MsgContext, method)) {
            if (request.isEntityEnclosing()) {
                processEntityEnclosingRequest(axis2MsgContext, false);
            } else {
                processNonEntityEnclosingRESTHandler(null, axis2MsgContext, false);
            }
        } else {
            AxisOperation axisOperation = synCtx.getAxis2MessageContext().getAxisOperation();
            synCtx.getAxis2MessageContext().setAxisOperation(null);
            String contentTypeHeader = request.getHeaders().get(HTTP.CONTENT_TYPE);
            SOAPEnvelope soapEnvelope = handleRESTUrlPost(contentTypeHeader);
            processNonEntityEnclosingRESTHandler(soapEnvelope, axis2MsgContext, false);
            synCtx.getAxis2MessageContext().setAxisOperation(axisOperation);

        }
    }

    private void injectToMainSequence(org.apache.synapse.MessageContext synCtx, InboundEndpoint endpoint) {

        SequenceMediator injectingSequence = (SequenceMediator) synCtx.getMainSequence();

        SequenceMediator faultSequence = getFaultSequence(synCtx, endpoint);

        MediatorFaultHandler mediatorFaultHandler = new MediatorFaultHandler(faultSequence);
        synCtx.pushFaultHandler(mediatorFaultHandler);

        /* handover synapse message context to synapse environment for inject it to given
        sequence in synchronous manner*/
        if (log.isDebugEnabled()) {
            log.debug("injecting message to sequence : " + endpoint.getInjectingSeq());
        }
        synCtx.getEnvironment().injectMessage(synCtx, injectingSequence);
    }

    private void injectToSequence(org.apache.synapse.MessageContext synCtx, InboundEndpoint endpoint) {
        // Get injecting sequence for synapse engine
        SequenceMediator injectingSequence = null;
        if (endpoint.getInjectingSeq() != null) {

            injectingSequence = (SequenceMediator) synCtx.getSequence(endpoint.getInjectingSeq());
        }

        if (injectingSequence == null) {
            injectingSequence = (SequenceMediator) synCtx.getMainSequence();
        }

        SequenceMediator faultSequence = getFaultSequence(synCtx, endpoint);

        MediatorFaultHandler mediatorFaultHandler = new MediatorFaultHandler(faultSequence);
        synCtx.pushFaultHandler(mediatorFaultHandler);

        /* handover synapse message context to synapse environment for inject it to given sequence in
        synchronous manner*/
        if (log.isDebugEnabled()) {
            log.debug("injecting message to sequence : " + endpoint.getInjectingSeq());
        }
        synCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, endpoint.getName());
        synCtx.setProperty(SynapseConstants.ARTIFACT_NAME,
                           SynapseConstants.FAIL_SAFE_MODE_INBOUND_ENDPOINT + endpoint.getName());
        if (RuntimeStatisticCollector.isStatisticsEnabled()) {
            populateStatisticsMetadata(synCtx, endpoint);
        }
        synCtx.getEnvironment().injectMessage(synCtx, injectingSequence);
    }

    private void populateStatisticsMetadata(org.apache.synapse.MessageContext synCtx, InboundEndpoint endpoint) {
        Map<String, Object> statisticsDetails = new HashMap<String, Object>();
        statisticsDetails.put(SynapseConstants.INBOUND_PORT, port);
        statisticsDetails.put(InboundEndpointConstants.INBOUND_ENDPOINT_PROTOCOL, endpoint.getProtocol());
        synCtx.setProperty(SynapseConstants.STATISTICS_METADATA, statisticsDetails);
    }

    private SequenceMediator getFaultSequence(org.apache.synapse.MessageContext synCtx, InboundEndpoint endpoint) {
        SequenceMediator faultSequence = null;
        if (endpoint.getOnErrorSeq() != null) {
            faultSequence = (SequenceMediator) synCtx.getSequence(endpoint.getOnErrorSeq());
        }

        if (faultSequence == null) {
            faultSequence = (SequenceMediator) synCtx.getFaultSequence();
        }

        return faultSequence;
    }

    /**
     * Set Inbound Related Properties for Synapse Message Context
     *
     * @param msgContext Synapse Message Context of incoming request
     */
    private void setInboundProperties(org.apache.synapse.MessageContext msgContext) {
        msgContext.setProperty(SynapseConstants.IS_INBOUND, true);
        msgContext.setProperty(InboundEndpointConstants.INBOUND_ENDPOINT_RESPONSE_WORKER,
                               new InboundHttpResponseSender());
        msgContext.setWSAAction(request.getHeaders().get(InboundHttpConstants.SOAP_ACTION));
    }

    /**
     * Set Inbound Related Properties for Axis2 Message Context
     *
     * @param axis2Context Axis2 Message Context of incoming request
     */
    private void setInboundProperties(MessageContext axis2Context) {
        axis2Context.setProperty(SynapseConstants.IS_INBOUND, true);
    }

    protected void handleException(String msg) {
        log.error(msg);
        throw new SynapseException(msg);
    }

    /**
     * Checks whether the message should be routed to Axis2 path
     *
     * @param synapseMsgContext Synapse Message Context of incoming message
     * @return true if the message should be routed, false otherwise
     */
    private boolean isAllowedAxis2Path(org.apache.synapse.MessageContext synapseMsgContext) {
        boolean isProxy = false;

        String reqUri = request.getUri();
        String servicePath = getSourceConfiguration().getConfigurationContext().getServicePath();

        //Get the operation part from the request URL
        // e.g. '/services/TestProxy/' > TestProxy when service path is '/service/' > result 'TestProxy/'
        String serviceOpPart = Utils.getServiceAndOperationPart(reqUri, servicePath);
        //if proxy, then check whether it is deployed in the environment
        if (serviceOpPart != null) {
            isProxy = isProxyDeployed(synapseMsgContext, serviceOpPart);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Requested Proxy Service '" + serviceOpPart + "' is not deployed");
            }
        }
        return isProxy;
    }

    /**
     * Checks whether the given proxy is deployed in synapse environment
     *
     * @param synapseContext Synapse Message Context of incoming message
     * @param serviceOpPart  String name of the service operation
     * @return true if the proxy is deployed, false otherwise
     */
    private boolean isProxyDeployed(org.apache.synapse.MessageContext synapseContext, String serviceOpPart) {
        boolean isDeployed = false;

        //extract proxy name from serviceOperation, get the first portion split by '/'
        String proxyName = serviceOpPart.split("/")[0];

        //check whether the proxy is deployed in synapse environment
        if (synapseContext.getConfiguration().getProxyService(proxyName) != null) {
            isDeployed = true;
        }
        return isDeployed;
    }

    /**
     * Creates synapse message context from axis2 context
     *
     * @param inboundSourceRequest Source Request of inbound
     * @param axis2Context         Axis2 message context of message
     * @return Synapse Message Context instance
     * @throws AxisFault
     */
    private org.apache.synapse.MessageContext createSynapseMessageContext(SourceRequest inboundSourceRequest,
                                                                          MessageContext axis2Context)
            throws AxisFault {

        return MessageContextCreatorForAxis2.getSynapseMessageContext(axis2Context);
    }

    /**
     * Updates additional properties in Axis2 Message Context from Synapse Message Context
     *
     * @param synCtx Synapse Message Context
     * @return Updated Axis2 Message Context
     * @throws AxisFault
     */
    private org.apache.synapse.MessageContext updateAxis2MessageContextForSynapse(
            org.apache.synapse.MessageContext synCtx) throws AxisFault {

        ServiceContext svcCtx = new ServiceContext();
        OperationContext opCtx = new OperationContext(new InOutAxisOperation(), svcCtx);

        ((Axis2MessageContext) synCtx).getAxis2MessageContext().setServiceContext(svcCtx);
        ((Axis2MessageContext) synCtx).getAxis2MessageContext().setOperationContext(opCtx);

        return synCtx;
    }

    /**
     * Sends the respond back to the client.
     *
     * @param synCtx the MessageContext
     * @param result the result of API Call
     */
    private void respond(org.apache.synapse.MessageContext synCtx, boolean result) {
        synCtx.setTo(null);
        synCtx.setResponse(true);
        Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
        org.apache.axis2.context.MessageContext axis2MessageCtx = axis2smc.getAxis2MessageContext();
        axis2MessageCtx.getOperationContext().setProperty(Constants.RESPONSE_WRITTEN, "SKIP");
        if (!result) {
            if (axis2MessageCtx.getProperty(PassThroughConstants.HTTP_SC) == null) {
                axis2MessageCtx.setProperty(PassThroughConstants.HTTP_SC, "404");
            }
        }
        InboundHttpResponseSender responseSender = (InboundHttpResponseSender) synCtx
                .getProperty(InboundEndpointConstants.INBOUND_ENDPOINT_RESPONSE_WORKER);
        responseSender.sendBack(synCtx);
    }
}
