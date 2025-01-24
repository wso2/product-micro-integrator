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
package org.wso2.carbon.inbound.endpoint.protocol.kafka;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.UUIDGenerator;
import org.apache.axis2.AxisFault;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.builder.BuilderUtil;
import org.apache.axis2.builder.SOAPBuilder;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.aspects.flow.statistics.collectors.RuntimeStatisticCollector;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.inbound.InboundEndpointConstants;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.customlogsetter.CustomLogSetter;
import org.wso2.carbon.inbound.endpoint.inboundfactory.InboundRequestProcessorFactoryImpl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * KafkaInjectHandler uses to mediate the received Kafka message
 */
public class KAFKAInjectHandler implements InjectHandler {
    private static final Log log = LogFactory.getLog(KAFKAInjectHandler.class);

    private String injectingSeq;
    private String onErrorSeq;
    private boolean sequential;
    private SynapseEnvironment synapseEnvironment;
    private String contentType;

    public KAFKAInjectHandler(String injectingSeq, String onErrorSeq, boolean sequential,
                              SynapseEnvironment synapseEnvironment, String contentType) {
        this.injectingSeq = injectingSeq;
        this.onErrorSeq = onErrorSeq;
        this.sequential = sequential;
        this.synapseEnvironment = synapseEnvironment;
        this.contentType = contentType;
    }

    /**
     * Determine the message builder to use, set the message payload to the message context and
     * inject the message to the sequence
     */
    public boolean invoke(Object object, String name) {
        KafkaMessageContext kafkaMessageContext = (KafkaMessageContext) object;

        org.apache.synapse.MessageContext msgCtx = createMessageContext();
        msgCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, name);
        msgCtx.setProperty(SynapseConstants.ARTIFACT_NAME, SynapseConstants.FAIL_SAFE_MODE_INBOUND_ENDPOINT + name);
        msgCtx.setProperty(SynapseConstants.IS_INBOUND, true);
        if (RuntimeStatisticCollector.isStatisticsEnabled()) {
            populateStatisticsMetadata(msgCtx, kafkaMessageContext);
        }
        InboundEndpoint inboundEndpoint = msgCtx.getConfiguration().getInboundEndpoint(name);
        CustomLogSetter.getInstance().setLogAppender(inboundEndpoint.getArtifactContainerName());
        log.debug("Processed Kafka Message ");
        MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) msgCtx)
                .getAxis2MessageContext();
        // Determine the message builder to use
        Builder builder = null;
        if (contentType == null) {
            log.debug("No content type specified. Using SOAP builder.");
            builder = new SOAPBuilder();
        } else {
            int index = contentType.indexOf(';');
            String type = index > 0 ? contentType.substring(0, index) : contentType;
            try {
                builder = BuilderUtil.getBuilderFromSelector(type, axis2MsgCtx);
            } catch (AxisFault axisFault) {
                log.error("Error while creating message builder :: " + axisFault.getMessage(), axisFault);

            }
            if (builder == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No message builder found for type '" + type + "'. Falling back to SOAP.");
                }
                builder = new SOAPBuilder();
            }
        }
        OMElement documentElement = null;
        // set the message payload to the message context
        InputStream in = new AutoCloseInputStream(new ByteArrayInputStream(kafkaMessageContext.getMsg()));
        try {
            documentElement = builder.processDocument(in, contentType, axis2MsgCtx);
        } catch (AxisFault axisFault) {
            log.error("Error while processing message :: " + axisFault.getMessage(), axisFault);
        }

        try {
            msgCtx.setEnvelope(TransportUtils.createSOAPEnvelope(documentElement));
        } catch (AxisFault axisFault) {
            log.error("Error while setting message payload to the message context :: " + axisFault.getMessage(),
                      axisFault);
        }
        // Inject the message to the sequence.

        if (injectingSeq == null || injectingSeq.equals("")) {
            log.error("Sequence name not specified. Sequence : " + injectingSeq);
            return false;
        }
        SequenceMediator seq = (SequenceMediator) synapseEnvironment.getSynapseConfiguration()
                .getSequence(injectingSeq);
        if (seq != null) {
            if (log.isDebugEnabled()) {
                log.debug("injecting message to sequence : " + injectingSeq);
            }
            if (!seq.isInitialized()) {
                seq.init(synapseEnvironment);
            }
            seq.setErrorHandler(onErrorSeq);
            synapseEnvironment.injectInbound(msgCtx, seq, sequential);
        } else {
            log.error("Sequence: " + injectingSeq + " not found");
        }

        return true;
    }

    /**
     * Create the initial message context for kafka
     */
    private org.apache.synapse.MessageContext createMessageContext() {
        org.apache.synapse.MessageContext msgCtx = synapseEnvironment.createMessageContext();
        MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) msgCtx)
                .getAxis2MessageContext();
        axis2MsgCtx.setServerSide(true);
        axis2MsgCtx.setMessageID(UUIDGenerator.getUUID());
        msgCtx.setProperty(MessageContext.CLIENT_API_NON_BLOCKING, true);
        return msgCtx;
    }

    private void populateStatisticsMetadata(org.apache.synapse.MessageContext synCtx, KafkaMessageContext kafkaMessageContext) {
        Map<String, Object> statisticsDetails = new HashMap<String, Object>();
        statisticsDetails.put(InboundEndpointConstants.INBOUND_ENDPOINT_PROTOCOL,
                InboundRequestProcessorFactoryImpl.Protocols.kafka.toString());
        statisticsDetails.put(SynapseConstants.CONNECTION, kafkaMessageContext.getConnection());
        statisticsDetails.put(SynapseConstants.TOPIC, kafkaMessageContext.getTopic());
        synCtx.setProperty(SynapseConstants.STATISTICS_METADATA, statisticsDetails);
    }
}
