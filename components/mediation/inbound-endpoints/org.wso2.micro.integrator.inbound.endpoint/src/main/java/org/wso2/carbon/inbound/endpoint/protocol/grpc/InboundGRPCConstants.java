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

public class InboundGRPCConstants {
    public static final String INBOUND_ENDPOINT_PARAMETER_GRPC_PORT = "inbound.grpc.port";
    public static final String CONTENT_TYPE_JSON = "json";
    public static final String CONTENT_TYPE_JSON_MIME_TYPE = "application/json";
    public static final String CONTENT_TYPE_XML = "xml";
    public static final String CONTENT_TYPE_XML_MIME_TYPE = "text/xml";
    public static final String CONTENT_TYPE_TEXT = "text";
    public static final String CONTENT_TYPE_TEXT_MIME_TYPE = "text/plain";
    public static final String HEADER_MAP_SEQUENCE_PARAMETER_NAME = "sequence";
    public static final String HEADER_MAP_CONTENT_TYPE_PARAMETER_NAME = "Content-Type";
    public static final int DEFAULT_INBOUND_ENDPOINT_GRPC_PORT = 8888;
    public static final String UNDEPLOYMENT_GRACE_TIMEOUT = "undeployment.grace.timeout";
}
