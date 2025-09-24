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

package org.wso2.micro.integrator.websocket.transport;

public class WebsocketConstants {

    public static final String WS = "ws";
    public static final String WSS = "wss";

    public static final int WEBSOCKET_DEFAULT_WS_PORT = 80;
    public static final int WEBSOCKET_DEFAULT_WSS_PORT = 443;

    public static final String UNIVERSAL_SOURCE_IDENTIFIER = "universal.source.identifier";
    public static final String WEBSOCKET_SOURCE_CHANNEL_IDENTIFIER = "websocket.source.channel.identifier";
    public static final String WEBSOCKET_OUTFLOW_DISPATCH_SEQUENCE = "ws.outflow.dispatch.sequence";
    public static final String WEBSOCKET_OUTFLOW_DISPATCH_FAULT_SEQUENCE = "ws.outflow.dispatch.fault.sequence";
    public static final String CONTENT_TYPE = "websocket.accept.contenType";

    public static final String WEBSOCKET_SOURCE_HANDSHAKE_PRESENT = "websocket.source.handshake.present";
    public static final String WEBSOCKET_TARGET_HANDSHAKE_PRESENT = "websocket.target.handshake.present";

    public static final String WEBSOCKET_BINARY_FRAME_PRESENT = "websocket.binary.frame.present";
    public static final String WEBSOCKET_BINARY_FRAME = "websocket.binary.frame";

    public static final String WEBSOCKET_TEXT_FRAME_PRESENT = "websocket.text.frame.present";
    public static final String WEBSOCKET_TEXT_FRAME = "websocket.text.frame";

    public static final String WEBSOCKET_TARGET_HANDLER_CONTEXT = "websocket.target.handler.context";
    public static final String WEBSOCKET_SOURCE_HANDLER_CONTEXT = "websocket.source.handler.context";

    public static final String TRUST_STORE_LOCATION = "ws.trust.store.location";
    public static final String TRUST_STORE_PASSWORD = "ws.trust.store.Password";
    public static final String TRUST_STORE_TYPE = "ws.trust.store.type";
    public static final String TRUST_STORE_CONFIG_ELEMENT = "ws.trust.store";

    public static final String SYNAPSE_SUBPROTOCOL_PREFIX = "synapse";
    public static final String WEBSOCKET_SUBSCRIBER_PATH = "websocket.subscriber.path";

    public static final String WEBSOCKET_CUSTOM_HEADER_PREFIX = "websocket.custom.header.";
    public static final String WEBSOCKET_CUSTOM_HEADER_CONFIG = "ws.custom.header";
    public static final String WEBSOCKET_HOSTNAME_VERIFICATION_CONFIG = "ws.client.enable.hostname.verification";

    public static final String CONNECTION_TERMINATE = "connection.terminate";

    public static final int WEBSOCKET_UPSTREAM_ERROR_SC = 1014;
}
