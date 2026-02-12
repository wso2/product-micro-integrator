
/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.security;

/**
 * This class will contain security related constants for WSO2 MI
 */
public class SecurityConstants {

    /**
     * This constant is used to configure lazy initialization of security configs. This is configured as system property
     * Default : true
     */
    public static final String MI_SECURITY_USERMGT_LAZY_INIT = "mi.security.usermgt.lazy.init";

    public static final String DEFAULT_LDAP_USERSTORE_MANAGER =
            "org.wso2.micro.integrator.security.user.core.ldap.ReadOnlyLDAPUserStoreManager";
    public static final String DEFAULT_JDBC_USERSTORE_MANAGER =
            "org.wso2.micro.integrator.security.user.core.jdbc.JDBCUserStoreManager";

    public static final String CASE_INSENSITIVE_ROLE_NAME_CHECK = "case_insensitive_role_name_check";

    public static final String WS_SECURITY_CONFIG = "ws_security";

    public static final String CASE_INSENSITIVE_USERNAME = "CaseInsensitiveUsername";
}
