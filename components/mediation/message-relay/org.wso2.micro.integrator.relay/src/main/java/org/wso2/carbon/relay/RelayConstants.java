/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.relay;

import javax.xml.namespace.QName;

public final class RelayConstants {

    public static final String RELAY_POLICY_NS =
            "http://www.wso2.org/ns/2010/01/carbon/message-relay";

    public static final String INCLUDE_HIDDEN_SERVICES = "includeHiddenServices";

    public static final QName RELAY_ASSERSION_QNAME = new QName(RELAY_POLICY_NS, "RelayAssertion");

    public static final QName INCLUDE_HIDDEN_SERVICES_QNAME =
            new QName(RELAY_POLICY_NS, INCLUDE_HIDDEN_SERVICES);

    public static final QName SERVICES_QNAME = new QName(RELAY_POLICY_NS, "services");
    public static final QName SERVICE_QNAME = new QName(RELAY_POLICY_NS, "service");

    public static final QName BUILDERS_QNAME = new QName(RELAY_POLICY_NS, "builders");
    public static final QName MESSAGE_BUILDER_QNAME = new QName(RELAY_POLICY_NS, "messageBuilder");
    public static final QName CONTENT_TYPE_QNAME = new QName("contentType");
    public static final QName CLASS_NAME_QNAME = new QName("class");

    public static final QName FORMATTER_CLASS_NAME_QNAME = new QName("class");

    public static final String RELAY_CONFIG_PARAM = "__relay_configuration__";

    public static final QName BINARY_CONTENT_QNAME =
            new QName("http://ws.apache.org/commons/ns/payload", "binary");
}
