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

package org.wso2.carbon.inbound.endpoint.protocol.mqtt;

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
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.wso2.carbon.inbound.endpoint.inboundfactory.InboundRequestProcessorFactoryImpl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Inject mqtt message into the sequence
 */
public class MqttInjectHandler {
    private static final Log log = LogFactory.getLog(MqttInjectHandler.class);

    private String injectingSeq;
    private String onErrorSeq;
    private boolean sequential;
    private String contentType;
    private SynapseEnvironment synapseEnvironment;

    /**
     * constructor initialize parameters and synapseEnvironment
     *
     * @param injectingSeq
     * @param onErrorSeq
     * @param sequential
     * @param synapseEnvironment
     */
    public MqttInjectHandler(String injectingSeq, String onErrorSeq, boolean sequential,
                             SynapseEnvironment synapseEnvironment, String contentType) {
        this.injectingSeq = injectingSeq;
        this.onErrorSeq = onErrorSeq;
        this.sequential = sequential;
        this.synapseEnvironment = synapseEnvironment;
        this.contentType = contentType;
    }

    /**
     * Inject MQTT message into esb sequence.
     *
     * @param mqttMessage MQTT message
     * @param name name of the inbound endpoint
     * @param topicName MQTT topic from which the message was received
     * @return true if the MQTT message was processed and injected to mediation successfully.
     */
    public boolean invoke(MqttMessage mqttMessage, String name, String topicName) {
        return invoke(new MqttMessageContext(mqttMessage, topicName, null, null), name);
    }
    /**
     * Inject MQTT message into esb sequence.
     *
     * @param mqttMessageContext MQTT message context
     * @param name name of the inbound endpoint
     * @return true if the MQTT message was processed and injected to mediation successfully.
     */
    public boolean invoke(MqttMessageContext mqttMessageContext, String name) {

        try {
            org.apache.synapse.MessageContext msgCtx = createMessageContext();

            msgCtx.setProperty(MqttConstants.MQTT_TOPIC_NAME, mqttMessageContext.getTopic());
            msgCtx.setProperty(SynapseConstants.IS_INBOUND, true);
            if (RuntimeStatisticCollector.isStatisticsEnabled()) {
                populateStatisticsMetadata(msgCtx, mqttMessageContext);
            }

            if (name != null) {
                msgCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, name);
                msgCtx.setProperty(SynapseConstants.ARTIFACT_NAME, SynapseConstants.FAIL_SAFE_MODE_INBOUND_ENDPOINT + name);
                InboundEndpoint inboundEndpoint = msgCtx.getConfiguration().getInboundEndpoint(name);
                CustomLogSetter.getInstance().setLogAppender(inboundEndpoint.getArtifactContainerName());
            }

            String message = mqttMessageContext.getMqttMessage().toString();

            if (log.isDebugEnabled()) {
                log.debug("Processed MQTT Message of Content-type : " + contentType);
            }
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
                    log.error("Error while creating message builder :: " + axisFault.getMessage());

                }
                if (builder == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("No message builder found for type '" + type + "'. Falling back to SOAP.");
                    }
                    builder = new SOAPBuilder();
                }
            }

            OMElement documentElement = null;

            InputStream in = new AutoCloseInputStream(new ByteArrayInputStream(message.getBytes()));
            documentElement = builder.processDocument(in, contentType, axis2MsgCtx);

            // Inject the message to the sequence.
            msgCtx.setEnvelope(TransportUtils.createSOAPEnvelope(documentElement));
            if (injectingSeq == null || ("").equals(injectingSeq)) {
                log.error("Sequence name not specified. Sequence : " + injectingSeq);
                return false;
            }
            SequenceMediator seq = (SequenceMediator) synapseEnvironment.getSynapseConfiguration()
                    .getSequence(injectingSeq);

            if (seq != null) {
                if (!seq.isInitialized()) {
                    seq.init(synapseEnvironment);
                }
                seq.setErrorHandler(onErrorSeq);
                if (log.isDebugEnabled()) {
                    log.debug("injecting message to sequence : " + injectingSeq);
                }
                if (!synapseEnvironment.injectInbound(msgCtx, seq, this.sequential)) {
                    return false;
                }
            } else {
                log.error("Sequence: " + injectingSeq + " not found");
            }

        } catch (Exception e) {
            log.error("Error while processing the MQTT Message");
        }
        return true;

    }

    /**
     * Create the initial message context
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

    private void populateStatisticsMetadata(org.apache.synapse.MessageContext synCtx, MqttMessageContext mqttMessageContext) {
        Map<String, Object> statisticsDetails = new HashMap<String, Object>();
        statisticsDetails.put(InboundEndpointConstants.INBOUND_ENDPOINT_PROTOCOL,
                InboundRequestProcessorFactoryImpl.Protocols.mqtt.toString());
        statisticsDetails.put(SynapseConstants.HOSTNAME, mqttMessageContext.getHost());
        statisticsDetails.put(SynapseConstants.PORT, mqttMessageContext.getPort());
        statisticsDetails.put(SynapseConstants.TOPIC, mqttMessageContext.getTopic());
        synCtx.setProperty(SynapseConstants.STATISTICS_METADATA, statisticsDetails);
    }
}
