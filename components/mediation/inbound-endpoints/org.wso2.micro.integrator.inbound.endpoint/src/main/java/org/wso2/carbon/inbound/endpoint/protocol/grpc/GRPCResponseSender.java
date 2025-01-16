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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.inbound.endpoint.protocol.grpc;


import io.grpc.stub.StreamObserver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.inbound.InboundResponseSender;
import org.wso2.carbon.inbound.endpoint.protocol.grpc.util.Event;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.apache.axis2.Constants.Configuration.MESSAGE_TYPE;

public class GRPCResponseSender implements InboundResponseSender {
    private StreamObserver<Event> responseObserver;
    private static final Log log = LogFactory.getLog(GRPCResponseSender.class.getName());
    GRPCResponseSender(StreamObserver<Event> responseObserver) {
        this.responseObserver = responseObserver;
    }
    @Override
    public void sendBack(MessageContext messageContext) {
        if (responseObserver != null) {
            org.apache.axis2.context.MessageContext msgContext = ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            String content;
            String contentType = msgContext.getProperty(MESSAGE_TYPE).toString();
            if (log.isDebugEnabled()) {
                log.debug("Message content type retrieved in the message is: " + contentType);
            }
            if (contentType.equalsIgnoreCase(InboundGRPCConstants.CONTENT_TYPE_JSON_MIME_TYPE)) {
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(JsonUtil.getJsonPayload(msgContext)))) {
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    content = stringBuilder.toString();
                } catch (IOException e) {
                    String msg = "Error occurred while converting payload to json. " + e.getMessage();
                    log.error(msg, e);
                    throw new SynapseException(msg, e);
                }
            } else if (contentType.equalsIgnoreCase(InboundGRPCConstants.CONTENT_TYPE_XML_MIME_TYPE) ||
                    contentType.equalsIgnoreCase(InboundGRPCConstants.CONTENT_TYPE_TEXT_MIME_TYPE)) {
                content = msgContext.getEnvelope().getBody().toString();
            } else {
                String msg = "Error occurred when sending response. " + contentType + " type not supported";
                log.error(msg);
                throw new SynapseException(msg);
            }
            if (log.isDebugEnabled()) {
                log.debug("Extracted content: " + content);
            }
            Event.Builder responseBuilder = Event.newBuilder();
            responseBuilder.setPayload(content);
            Event response = responseBuilder.build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } else {
            String msg = "Message context doesn't contain gRPC Response Observer. " +
                    "Please make sure the gRPC call accepts a response ";
            log.error(msg);
            throw new SynapseException(msg);
        }
    }
}
