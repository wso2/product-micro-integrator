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

package org.wso2.carbon.inbound.endpoint.protocol.hl7.management;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.transport.passthru.util.BufferFactory;
import org.wso2.carbon.inbound.endpoint.common.AbstractInboundEndpointManager;
import org.wso2.carbon.inbound.endpoint.common.Constants;
import org.wso2.carbon.inbound.endpoint.inboundfactory.InboundRequestProcessorFactoryImpl;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.HL7MessagePreprocessor;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.core.HL7Processor;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.core.InboundHL7IOReactor;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.core.MLLPConstants;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.util.Axis2HL7Constants;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.util.HL7Configuration;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;

public class HL7EndpointManager extends AbstractInboundEndpointManager {
    private static final Log log = LogFactory.getLog(HL7EndpointManager.class);
    private static final String HL7_BUFFER_SIZE_PROPERTY = "read_buffer_size";
    private static final int DEFAULT_HL7_BUFFER_SIZE = 8 * 1024;

    private static HL7EndpointManager instance = new HL7EndpointManager();

    private HL7EndpointManager() {
        super();
    }

    public static HL7EndpointManager getInstance() {
        return instance;
    }

    @Override
    public boolean startListener(int port, String name, InboundProcessorParams params) {
        log.info("Starting HL7 Inbound Endpoint on port " + port);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put(MLLPConstants.INBOUND_PARAMS, params);
        int bufferSizeHL7 = HL7Configuration.getInstance().getIntProperty(HL7_BUFFER_SIZE_PROPERTY, DEFAULT_HL7_BUFFER_SIZE);
        parameters.put(MLLPConstants.INBOUND_HL7_BUFFER_FACTORY,
                       new BufferFactory(bufferSizeHL7, new HeapByteBufferAllocator(), 1024));
        validateParameters(params, parameters);

        HL7Processor hl7Processor = new HL7Processor(parameters);
        parameters.put(MLLPConstants.HL7_REQ_PROC, hl7Processor);

        return InboundHL7IOReactor.bind(port, hl7Processor);
    }

    @Override
    public boolean startEndpoint(int port, String name, InboundProcessorParams params) {

        params.getProperties().setProperty(MLLPConstants.HL7_INBOUND_TENANT_DOMAIN, Constants.SUPER_TENANT_DOMAIN_NAME);

        String epName = dataStore.getListeningEndpointName(port, Constants.SUPER_TENANT_DOMAIN_NAME);
        if (epName != null) {
            if (epName.equalsIgnoreCase(name)) {
                log.info(epName + " Endpoint is already started in port : " + port);
            } else {
                String msg = "Another endpoint named : " + epName + " is currently using this port: " + port;
                log.warn(msg);
                throw new SynapseException(msg);
            }
        } else {
            dataStore.registerListeningEndpoint(port, Constants.SUPER_TENANT_DOMAIN_NAME,
                                                InboundRequestProcessorFactoryImpl.Protocols.hl7.toString(), name,
                                                params);
            return startListener(port, name, params);
        }

        return false;
    }

    @Override
    public void closeEndpoint(int port) {

        dataStore.unregisterListeningEndpoint(port, Constants.SUPER_TENANT_DOMAIN_NAME);

        if (!InboundHL7IOReactor.isEndpointRunning(port)) {
            log.info("Listener Endpoint is not started");
            return;
        } else if (dataStore.isEndpointRegistryEmpty(port)) {
            // if no other endpoint is working on this port. close the listening endpoint
            InboundHL7IOReactor.unbind(port);
        }
    }

    public void pauseEndpoint(int port) {
        InboundHL7IOReactor.pauseEndpoint(port);
    }

    private void validateParameters(InboundProcessorParams params, Map<String, Object> parameters) {
        if (!params.getProperties().getProperty(MLLPConstants.PARAM_HL7_AUTO_ACK).equalsIgnoreCase("true") && !params
                .getProperties().getProperty(MLLPConstants.PARAM_HL7_AUTO_ACK).equalsIgnoreCase("false")) {
            log.warn("Parameter inbound.hl7.AutoAck in HL7 inbound " + params.getName() + " is not valid. Default "
                             + "value of true will be used.");
            params.getProperties().setProperty(MLLPConstants.PARAM_HL7_AUTO_ACK, "true");
        }

        try {
            Integer.valueOf(params.getProperties().getProperty(MLLPConstants.PARAM_HL7_TIMEOUT));
        } catch (NumberFormatException e) {
            log.warn("Parameter inbound.hl7.TimeOut in HL7 inbound " + params.getName()
                             + " is not valid. Default timeout " + "of " + MLLPConstants.DEFAULT_HL7_TIMEOUT
                             + " milliseconds will be used.");
            params.getProperties()
                    .setProperty(MLLPConstants.PARAM_HL7_TIMEOUT, String.valueOf(MLLPConstants.DEFAULT_HL7_TIMEOUT));
        }

        try {
            if (params.getProperties().getProperty(MLLPConstants.PARAM_HL7_PRE_PROC) != null) {
                final HL7MessagePreprocessor preProcessor = (HL7MessagePreprocessor) Class
                        .forName(params.getProperties().getProperty(MLLPConstants.PARAM_HL7_PRE_PROC)).newInstance();

                Parser preProcParser = new PipeParser() {
                    public Message parse(String message) throws HL7Exception {
                        message = preProcessor.process(message, Axis2HL7Constants.MessageType.V2X,
                                                       Axis2HL7Constants.MessageEncoding.ER7);
                        return super.parse(message);
                    }
                };

                parameters.put(MLLPConstants.HL7_PRE_PROC_PARSER_CLASS, preProcParser);
            }
        } catch (Exception e) {
            log.error("Error creating message preprocessor for HL7 inbound " + params.getName() + ": ", e);
        }

        try {
            if (params.getProperties().getProperty(MLLPConstants.PARAM_HL7_CHARSET) == null) {
                params.getProperties()
                        .setProperty(MLLPConstants.PARAM_HL7_CHARSET, MLLPConstants.UTF8_CHARSET.displayName());
                parameters.put(MLLPConstants.HL7_CHARSET_DECODER, MLLPConstants.UTF8_CHARSET.newDecoder());
            } else {
                parameters.put(MLLPConstants.HL7_CHARSET_DECODER,
                               Charset.forName(params.getProperties().getProperty(MLLPConstants.PARAM_HL7_CHARSET))
                                       .newDecoder());
            }
        } catch (UnsupportedCharsetException e) {
            parameters.put(MLLPConstants.HL7_CHARSET_DECODER, MLLPConstants.UTF8_CHARSET.newDecoder());
            log.error("Unsupported charset '" + params.getProperties().getProperty(MLLPConstants.PARAM_HL7_CHARSET)
                              + "' specified in HL7 inbound " + params.getName()
                              + ". Default UTF-8 will be used instead.");
        }

        if (params.getProperties().getProperty(MLLPConstants.PARAM_HL7_VALIDATE) == null) {
            params.getProperties().setProperty(MLLPConstants.PARAM_HL7_VALIDATE, "true");
        }

        if (params.getProperties().getProperty(MLLPConstants.PARAM_HL7_BUILD_RAW_MESSAGE) == null) {
            params.getProperties().setProperty(MLLPConstants.PARAM_HL7_BUILD_RAW_MESSAGE, "false");
        } else {
            if (!params.getProperties().getProperty(MLLPConstants.PARAM_HL7_BUILD_RAW_MESSAGE).equalsIgnoreCase("true")
                    && !params.getProperties().getProperty(MLLPConstants.PARAM_HL7_BUILD_RAW_MESSAGE)
                    .equalsIgnoreCase("false")) {
                params.getProperties().setProperty(MLLPConstants.PARAM_HL7_BUILD_RAW_MESSAGE, "false");
            }
        }

        if (params.getProperties().getProperty(MLLPConstants.PARAM_HL7_PASS_THROUGH_INVALID_MESSAGES) == null) {
            params.getProperties().setProperty(MLLPConstants.PARAM_HL7_PASS_THROUGH_INVALID_MESSAGES, "false");
        } else {
            if (!params.getProperties().getProperty(MLLPConstants.PARAM_HL7_PASS_THROUGH_INVALID_MESSAGES)
                    .equalsIgnoreCase("true") && !params.getProperties()
                    .getProperty(MLLPConstants.PARAM_HL7_PASS_THROUGH_INVALID_MESSAGES).equalsIgnoreCase("false")) {
                params.getProperties().setProperty(MLLPConstants.PARAM_HL7_PASS_THROUGH_INVALID_MESSAGES, "false");
            }
        }
    }
}
