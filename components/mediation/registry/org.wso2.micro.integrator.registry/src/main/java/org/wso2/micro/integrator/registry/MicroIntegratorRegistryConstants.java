/**
 * Copyright (c) 2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.micro.integrator.registry;

/**
 *
 */
public class MicroIntegratorRegistryConstants {

    // MicroIntegratorRegistryConstants for MI registry
    public static final int LOCAL_HOST_REGISTRY = 100;
    public static final int REMOTE_HOST_REGISTRY = 101;
    public static final int REGISTRY_MODE = LOCAL_HOST_REGISTRY;
    // this will be overwritten if localRegistry parameter is set
    public static final String LOCAL_REGISTRY_ROOT = "org/wso2/micro/integrator/registry/";
    public static final String REGISTRY_FILE = "file";
    public static final String REGISTRY_FOLDER = "folder";
    public static final String FOLDER = "http://wso2.org/projects/esb/registry/types/folder";
    // use if the exact FILE type is not known
    public static final String FILE = "http://wso2.org/projects/esb/registry/types/file";

    public static final String CONF_REG_ROOT = "ConfigRegRoot";
    public static final String REG_ROOT = "RegRoot";
    public static final String GOV_REG_ROOT = "GovRegRoot";
    public static final String LOCAL_REG_ROOT = "LocalRegRoot";

    public static final String LIST = "list";

    public static final String PROTOCOL_FILE = "file";
    public static final String PROTOCOL_HTTP = "http";
    public static final String PROTOCOL_HTTPS = "https";
    public static final String FILE_PROTOCOL_PREFIX = "file:";

    public static final String RESOURCES_PREFIX = "resources:";
    public static final String CONFIG_REGISTRY_PREFIX = "conf:";
    public static final String GOVERNANCE_REGISTRY_PREFIX = "gov:";
    public static final String LOCAL_REGISTRY_PREFIX = "local:";

    public static final String MI_RESOURCES_DIRECTORY_NAME = "mi-resources";
    public static final String CONFIG_DIRECTORY_NAME = "config";
    public static final String GOVERNANCE_DIRECTORY_NAME = "governance";
    public static final String LOCAL_DIRECTORY_NAME = "local";

    public static final char URL_SEPARATOR_CHAR = '/';
    public static final String URL_SEPARATOR = "/";
    public static final String PROPERTY_EXTENTION = ".properties";
    public static final String COLLECTION_PROPERTY_EXTENTION = ".collections" + PROPERTY_EXTENTION;

    public static final String DEFAULT_MEDIA_TYPE = "text/plain";

    public static final String CONNECTOR_SECURE_VAULT_CONFIG_REPOSITORY = "conf:/repository/components/secure-vault";
    public static final String TYPE_KEY = "type";
    public static final String NAME_KEY = "name";
    public static final String ERROR_KEY = "error";

    public static final String FILE_TYPE_DIRECTORY = "directory";
    public static final String HIDDEN_FILE_PREFIX = ".";
    public static final String CHILD_FILES_LIST_KEY = "files";
    public static final String PROPERTIES_KEY = "properties";
    public static final String VALUE_KEY = "value";

    //PROPERTY_FILE_VALUE is used to identify existing ".properties" files without the content file.
    public static final String PROPERTY_FILE_VALUE = "property file";
    public static final String CONFIGURATION_REGISTRY_PATH = "registry/config";
    public static final String GOVERNANCE_REGISTRY_PATH = "registry/governance";
    public static final String LOCAL_REGISTRY_PATH = "registry/local";
}
