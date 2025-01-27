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

package org.wso2.carbon.inbound.endpoint.protocol.hl7.core;

import ca.uhn.hl7v2.HL7Exception;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.aspects.flow.statistics.collectors.RuntimeStatisticCollector;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.inbound.InboundEndpointConstants;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundResponseSender;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.customlogsetter.CustomLogSetter;
import org.wso2.carbon.inbound.endpoint.inboundfactory.InboundRequestProcessorFactoryImpl;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.context.MLLPContext;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.util.Axis2HL7Constants;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.util.HL7ExecutorServiceFactory;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.util.HL7MessageUtils;

import java.nio.charset.CharsetDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HL7Processor implements InboundResponseSender {
    private static final Log log = LogFactory.getLog(HL7Processor.class);

    private ScheduledExecutorService executorService = HL7ExecutorServiceFactory.getExecutorService();

    private Map<String, Object> parameters;
    private InboundProcessorParams params;
    private String inSequence;
    private String onErrorSequence;

    private boolean autoAck = true;
    private int timeOut;

    public HL7Processor(Map<String, Object> parameters) {
        this.parameters = parameters;

        params = (InboundProcessorParams) parameters.get(MLLPConstants.INBOUND_PARAMS);
        inSequence = params.getInjectingSeq();
        onErrorSequence = params.getOnErrorSeq();

        if (params.getProperties().getProperty(MLLPConstants.PARAM_HL7_AUTO_ACK).equals("false")) {
            autoAck = false;
        }

        timeOut = HL7MessageUtils.getInt(MLLPConstants.PARAM_HL7_TIMEOUT, params);

    }

    /**
     * HL7 Request Processing logic
     *
     * @param mllpContext
     * @throws Exception - catch any generic exceptions or else I/O Reactor may shutdown.
     */
    public void processRequest(final MLLPContext mllpContext) throws Exception {
        mllpContext.setRequestTime(System.currentTimeMillis());

        // Prepare Synapse Context for message injection
        MessageContext synCtx;
        try {
            synCtx = HL7MessageUtils.createSynapseMessageContext(mllpContext.getHl7Message(), params);
        } catch (HL7Exception e) {
            handleException(mllpContext, e.getMessage());
            return;
        } catch (AxisFault e) {
            handleException(mllpContext, e.getMessage());
            return;
        }

        mllpContext.setMessageId(synCtx.getMessageID());
        synCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, params.getName());
        synCtx.setProperty(SynapseConstants.ARTIFACT_NAME,
                           SynapseConstants.FAIL_SAFE_MODE_INBOUND_ENDPOINT + params.getName());
        synCtx.setProperty(SynapseConstants.IS_INBOUND, true);
        if (RuntimeStatisticCollector.isStatisticsEnabled()) {
            populateStatisticsMetadata(synCtx);
        }
        InboundEndpoint inboundEndpoint = synCtx.getConfiguration().getInboundEndpoint(params.getName());
        CustomLogSetter.getInstance().setLogAppender(inboundEndpoint.getArtifactContainerName());
        synCtx.setProperty(MLLPConstants.HL7_INBOUND_MSG_ID, synCtx.getMessageID());

        // If not AUTO ACK, we need response invocation through this processor
        if (!autoAck) {
            synCtx.setProperty(InboundEndpointConstants.INBOUND_ENDPOINT_RESPONSE_WORKER, this);
            synCtx.setProperty(MLLPConstants.MLLP_CONTEXT, mllpContext);
        }

        addProperties(synCtx, mllpContext);

        SequenceMediator injectSeq = (SequenceMediator) synCtx.getEnvironment().getSynapseConfiguration()
                .getSequence(inSequence);
        if (injectSeq == null) {
            log.error("Could not find inbound sequence '" + inSequence + "'.");
            handleException(mllpContext, "Could not find inbound sequence.");
            return;
        } else if (!injectSeq.isInitialized()) {
            injectSeq.init(synCtx.getEnvironment());
        }
        injectSeq.setErrorHandler(onErrorSequence);

        if (!autoAck && timeOut > 0) {
            executorService
                    .schedule(new TimeoutHandler(mllpContext, synCtx.getMessageID()), timeOut, TimeUnit.MILLISECONDS);
        }

        CallableTask task = new CallableTask(synCtx, injectSeq);

        executorService.submit(task);

    }

    public void processError(final MLLPContext mllpContext, final Exception ex) {
        mllpContext.setRequestTime(System.currentTimeMillis());

        // Prepare Synapse Context for message injection
        MessageContext synCtx;
        try {
            if (mllpContext.getRequestBuffer() != null) {
                synCtx = HL7MessageUtils.
                        createErrorMessageContext(mllpContext.getRequestBuffer().toString(), ex, params);
            } else {
                synCtx = HL7MessageUtils.
                        createErrorMessageContext("The message received is not parseable", ex, params);
            }
        } catch (HL7Exception e) {
            handleException(mllpContext, e.getMessage());
            return;
        } catch (AxisFault e) {
            handleException(mllpContext, e.getMessage());
            return;
        }

        mllpContext.setMessageId(synCtx.getMessageID());
        synCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, params.getName());
        synCtx.setProperty(SynapseConstants.ARTIFACT_NAME,
                           SynapseConstants.FAIL_SAFE_MODE_INBOUND_ENDPOINT + params.getName());
        synCtx.setProperty(SynapseConstants.IS_INBOUND, true);
        if (RuntimeStatisticCollector.isStatisticsEnabled()) {
            populateStatisticsMetadata(synCtx);
        }
        InboundEndpoint inboundEndpoint = synCtx.getConfiguration().getInboundEndpoint(params.getName());
        CustomLogSetter.getInstance().setLogAppender(inboundEndpoint.getArtifactContainerName());
        synCtx.setProperty(MLLPConstants.HL7_INBOUND_MSG_ID, synCtx.getMessageID());

        // If not AUTO ACK, we need response invocation through this processor
        if (!autoAck) {
            synCtx.setProperty(InboundEndpointConstants.INBOUND_ENDPOINT_RESPONSE_WORKER, this);
            synCtx.setProperty(MLLPConstants.MLLP_CONTEXT, mllpContext);
        }

        addProperties(synCtx, mllpContext);

        SequenceMediator injectSeq = (SequenceMediator) synCtx.getEnvironment().getSynapseConfiguration()
                .getSequence(onErrorSequence);
        if (injectSeq == null) {
            log.error("Could not find inbound error sequence '" + onErrorSequence + "'.");
            handleException(mllpContext, "Could not find inbound error sequence.");
            return;
        } else if (!injectSeq.isInitialized()) {
            injectSeq.init(synCtx.getEnvironment());
        }

        if (!autoAck && timeOut > 0) {
            executorService
                    .schedule(new TimeoutHandler(mllpContext, synCtx.getMessageID()), timeOut, TimeUnit.MILLISECONDS);
        }

        CallableTask task = new CallableTask(synCtx, injectSeq);

        executorService.submit(task);
    }

    /**
     * We need to add several context properties that HL7 Axis2 transport sender depends on (also the
     * application/edi-hl7 formatter).
     *
     * @param synCtx
     */
    private void addProperties(MessageContext synCtx, MLLPContext context) {
        org.apache.axis2.context.MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) synCtx)
                .getAxis2MessageContext();

        axis2MsgCtx.setProperty(Axis2HL7Constants.HL7_MESSAGE_OBJECT, context.getHl7Message());

        if (params.getProperties().getProperty(MLLPConstants.PARAM_HL7_BUILD_RAW_MESSAGE) != null) {
            axis2MsgCtx.setProperty(Axis2HL7Constants.HL7_BUILD_RAW_MESSAGE, Boolean.valueOf(
                    params.getProperties().getProperty(MLLPConstants.PARAM_HL7_BUILD_RAW_MESSAGE)));
        }

        if (params.getProperties().getProperty(MLLPConstants.PARAM_HL7_PASS_THROUGH_INVALID_MESSAGES) != null) {
            axis2MsgCtx.setProperty(Axis2HL7Constants.HL7_PASS_THROUGH_INVALID_MESSAGES, Boolean.valueOf(
                    params.getProperties().getProperty(MLLPConstants.PARAM_HL7_PASS_THROUGH_INVALID_MESSAGES)));
        }

        if (parameters.get(MLLPConstants.HL7_CHARSET_DECODER) != null) {
            axis2MsgCtx.setProperty(Axis2HL7Constants.HL7_MESSAGE_CHARSET,
                                    ((CharsetDecoder) parameters.get(MLLPConstants.HL7_CHARSET_DECODER)).charset()
                                            .displayName());
        }

        // Below is expensive, it is in HL7 Axis2 transport but we should not depend on this!
        //axis2MsgCtx.setProperty(Axis2HL7Constants.HL7_RAW_MESSAGE_PROPERTY_NAME, context.getCodec());

    }

    public Map<String, Object> getInboundParameterMap() {
        return parameters;
    }

    private void populateStatisticsMetadata(MessageContext synCtx) {
        Map<String, Object> statisticsDetails = new HashMap<String, Object>();
        statisticsDetails.put(SynapseConstants.INBOUND_PORT,
                params.getProperties().getProperty(MLLPConstants.PARAM_HL7_PORT));
        statisticsDetails.put(InboundEndpointConstants.INBOUND_ENDPOINT_PROTOCOL,
                InboundRequestProcessorFactoryImpl.Protocols.hl7.toString());
        synCtx.setProperty(SynapseConstants.STATISTICS_METADATA, statisticsDetails);
    }

    @Override
    public void sendBack(MessageContext messageContext) {
        MLLPContext mllpContext = (MLLPContext) messageContext.getProperty(MLLPConstants.MLLP_CONTEXT);
        sendBack(messageContext, mllpContext);
    }

    private void sendBack(MessageContext messageContext, MLLPContext mllpContext) {

        if (messageContext.getProperty(MLLPConstants.HL7_INBOUND_MSG_ID) != null && !mllpContext.getMessageId()
                .equals(messageContext.getProperty(MLLPConstants.HL7_INBOUND_MSG_ID))) {
            log.warn("Response ID does not match request ID. Response may have been received after timeout.");
            return;
        }

        try {
            if ((((String) messageContext.getProperty(Axis2HL7Constants.HL7_RESULT_MODE)) != null)
                    && ((String) messageContext.getProperty(Axis2HL7Constants.HL7_RESULT_MODE))
                    .equals(Axis2HL7Constants.HL7_RESULT_MODE_NACK)) {
                String nackMessage = (String) messageContext.getProperty(Axis2HL7Constants.HL7_NACK_MESSAGE);
                mllpContext.setNackMode(true);
                mllpContext.setHl7Message(HL7MessageUtils.createNack(mllpContext.getHl7Message(), nackMessage));
            } else {
                // if HL7_APPLICATION_ACK is set then we are going to send the auto-generated ACK based on
                // the HL7 request, so we do not set the payload contents as context HL7 Message.
                if (messageContext.getProperty(Axis2HL7Constants.HL7_APPLICATION_ACK) != null && messageContext
                        .getProperty(Axis2HL7Constants.HL7_APPLICATION_ACK).equals("true")) {
                    mllpContext.setApplicationAck(true);
                } else {
                    mllpContext.setHl7Message(HL7MessageUtils.payloadToHL7Message(messageContext, params));
                }
            }

            mllpContext.requestOutput();
        } catch (NoSuchElementException e) {
            log.error(
                    "Could not find HL7 response in required XML format. Please ensure XML payload contains response inside message tags with namespace http://wso2.org/hl7.",
                    e);
            handleException(mllpContext, "Error while generating HL7 response. Not in required format.");
        } catch (HL7Exception e) {
            log.error("Error while generating HL7 ACK response from payload.", e);
            handleException(mllpContext, "Error while generating ACK from payload.");
        }
    }

    public boolean isAutoAck() {
        return autoAck;
    }

    private void handleException(MLLPContext mllpContext, String msg) {
        if (mllpContext.isAutoAck()) {
            try {
                mllpContext.setNackMode(true);
                mllpContext.setHl7Message(HL7MessageUtils.createNack(mllpContext.getHl7Message(), msg));
                mllpContext.requestOutput();
            } catch (HL7Exception e) {
                log.error("Error while generating NACK response.", e);
            }
        } else {
            processError(mllpContext, new Exception(msg));
        }
    }

    private class TimeoutHandler implements Runnable {
        private MLLPContext context;
        private String messageId;

        public TimeoutHandler(MLLPContext context, String messageId) {
            this.context = context;
            this.messageId = messageId;
        }

        public void run() {
            if (messageId.equals(context.getMessageId())) {
                try {
                    log.warn("Timed out while waiting for HL7 Response to be generated.");
                    context.setHl7Message(HL7MessageUtils.createNack(context.getHl7Message(),
                                                                     "Timed out while waiting for HL7 Response to be generated."));
                    context.setMessageId("TIMEOUT");
                    context.requestOutput();
                } catch (HL7Exception e) {
                    log.error("Could not generate timeout NACK response.", e);
                }
            }
        }
    }
}
