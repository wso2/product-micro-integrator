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

package org.wso2.micro.core;

public class Constants {
    public static final String SUPER_TENANT_DOMAIN_NAME = "carbon.super";
    public static final int SUPER_TENANT_ID = -1234;
    public static final String SUPER_TENANT_ID_STR = "-1234";
    public static final int INVALID_TENANT_ID = -1;

    //Server Constants
    public static final String CARBON_HOME = "carbon.home";
    public static final String REQUEST_BASE_CONTEXT = "org.wso2.carbon.context.RequestBaseContext";


    public static final String CARBON_SERVER_XML_NAMESPACE = "http://wso2.org/projects/carbon/carbon.xml";
    public static final String ANNOTATION = "annotation";
    public static final String SERVER_URL = "ServerURL";
    public static final String AXIS2_CONFIG_SERVICE = "org.apache.axis2.osgi.config.service";

    //primary key store
    public static final String SERVER_PRIMARY_KEYSTORE_FILE = "Security.KeyStore.Location";
    public static final String SERVER_PRIMARY_KEYSTORE_PASSWORD = "Security.KeyStore.Password";
    public static final String SERVER_PRIMARY_KEYSTORE_KEY_ALIAS = "Security.KeyStore.KeyAlias";
    public static final String SERVER_PRIVATE_KEY_PASSWORD = "Security.KeyStore.KeyPassword";
    public static final String SERVER_PRIMARY_KEYSTORE_TYPE = "Security.KeyStore.Type";
    public static final String SERVER_PRIVATE_STORE_CONFIG = "keystore.secondary";
    public static final String SERVER_PRIVATE_KEYSTORE_TYPE = "type";
    public static final String SERVER_PRIVATE_KEYSTORE_FILE = "file_name";
    public static final String SERVER_PRIVATE_KEYSTORE_PASSWORD = "password";

    //Internal key store which is used for encryption and decryption purpose
    public static final String SERVER_INTERNAL_KEYSTORE_FILE = "Security.InternalKeyStore.Location";
    public static final String SERVER_INTERNAL_KEYSTORE_PASSWORD = "Security.InternalKeyStore.Password";
    public static final String SERVER_INTERNAL_KEYSTORE_KEY_ALIAS = "Security.InternalKeyStore.KeyAlias";
    public static final String SERVER_INTERNAL_PRIVATE_KEY_PASSWORD = "Security.InternalKeyStore.KeyPassword";
    public static final String SERVER_INTERNAL_KEYSTORE_TYPE = "Security.InternalKeyStore.Type";

    //properties
    public static final String PROP_PASSWORD = "password";
    public static final String PROP_ROLE = "role";
    public static final String PROP_DESCRIPTION = "description";
    public static final String PROP_USERS = "users";
    public static final String PROP_PRIVATE_KEY_ALIAS = "privatekeyAlias";
    public static final String PROP_TYPE = "type";
    public static final String PROP_PRIVATE_KEY_PASS = "privatekeyPass";

    //Registry store
    public static final String SERVER_REGISTRY_KEYSTORE_FILE = "Security.RegistryKeyStore.Location";
    public static final String SERVER_REGISTRY_KEYSTORE_PASSWORD = "Security.RegistryKeyStore.Password";
    public static final String SERVER_REGISTRY_KEYSTORE_KEY_ALIAS = "Security.RegistryKeyStore.KeyAlias";
    public static final String SERVER_REGISTRY_KEY_PASSWORD = "Security.RegistryKeyStore.KeyPassword";
    public static final String SERVER_REGISTRY_KEYSTORE_TYPE = "Security.RegistryKeyStore.Type";


    public static final String HTTP_GET_REQUEST_PROCESSOR_SERVICE = "org.wso2.carbon.osgi.httpGetRequestProcessorService";

    public static final String ADMIN_SERVICE_PARAM_NAME = "adminService";

    public static final String USER_MGT_XML_PATH = "wso2.user.mgt.xml";
    public static final String COMPONENT_REP0 = "components.repo";
    public static final String AXIS2_REPO = "axis2.repo";


    public static final String REPO_WRITE_MODE = "carbon.repo.write.mode";

    public static final String HTTP_TRANSPORT = "http";
    public static final String HTTPS_TRANSPORT = "https";
    public static final String TRANSPORT_PORT = "port";
    public static final String SERVER_PORT_OFFSET = "portOffset";

    // Security Constants
    public static final String ALLOW_ROLES_PROXY_PARAM_NAME = "allowRoles";
    public static final String ALLOWED_ROLES_PARAM_NAME = "org.wso2.carbon.security.allowedroles";
    public static final String AUTHORIZATION = "Authorization";
    public static final String CARBON_SEC_CONFIG = "CarbonSecConfig";
    public static final String CUSTOM_SECURITY_SCENARIO = "customScenario";
    public static final String CUSTOM_SECURITY_SCENARIO_SUMMARY = "Custom security policy";
    public static final String ENCRYPTED = "encrypted";
    public static final String KERBEROS = "Kerberos";
    public static final String NAME_LABEL = "name";
    public static final String PROPERTY_LABEL = "property";
    public static final String RAMPART_MODULE_NAME = "rampart";
    public static final String SECURITY_NAMESPACE = "http://www.wso2.org/products/carbon/security";
    public static final String TRUST = "Trust";
    public static final String TRUST_MODULE = "rahas";

    public static final String BOUNCY_CASTLE_PROVIDER = "BC";
    public static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
}
