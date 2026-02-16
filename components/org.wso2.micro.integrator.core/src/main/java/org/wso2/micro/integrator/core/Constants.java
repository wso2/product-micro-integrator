/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.core;

/**
 * Contants for Integrator Class
 */
public class Constants {

    public static final String INTEGRATOR_HEADER = "Integrator_header";
    public static final String DATASERVICE_JSON_BUILDER = "dsJsonBuilder";
    public static final String DATASERVICE_JSON_FORMATTER = "dsJsonFormatter";
    public static final String PASSTHRU_JSON_BUILDER = "passthruJsonBuilder";
    public static final String PASSTHRU_JSON_FORMATTER = "passthruJsonFormatter";
    public static final String HOT_DEPLOYMENT = "hotdeployment";

    /**
     * Constants used to resolve environment variables and system properties
     */
    public static final String SYS_PROPERTY_PLACEHOLDER_PREFIX = "$sys{";
    public static final String ENV_VAR_PLACEHOLDER_PREFIX = "$env{";
    public static final String DYNAMIC_PROPERTY_PLACEHOLDER_PREFIX = "${";
    public static final String PLACEHOLDER_SUFFIX = "}";

    public static final int NO_CONTENT = 204;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_IMPLEMENTED = 501;
    public static final int BAD_GATEWAY = 502;
    public static final String ENABLE_VERSIONED_CAPP_DEPLOYMENT = "enableVersionedCAppDeployment";
    public static final String PRIMARY_BUNDLE_CONTEXT = "primaryBundleContext";
}
