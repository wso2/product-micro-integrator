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

package org.wso2.micro.integrator.mediation.security.vault;

import org.wso2.micro.integrator.utils.utils.ServerConstants;

public interface SecureVaultConstants {
	public static final String SECRET_CONF = "secret-conf.properties";
	public static final String CONF_LOCATION = "conf.location";
	public static final String SECURITY_DIR = "security";
	/* Default configuration file path for secret manager */
	String PROP_DEFAULT_CONF_LOCATION = "secret-manager.properties";
	/*
	 * If the location of the secret manager configuration is provided as a
	 * property- it's name
	 */
	String PROP_SECRET_MANAGER_CONF = "secret.manager.conf";
	/* Property key for secretRepositories */
	String PROP_SECRET_REPOSITORIES = "secretRepositories";
	/* Type of the secret repository */
	String PROP_PROVIDER = "provider";
	/* Dot string */
	String DOT = ".";

	// property key for global secret provider
	String PROP_SECRET_PROVIDER = "carbon.secretProvider";

	String CONF_CONNECTOR_SECURE_VAULT_CONFIG_PROP_LOOK =
	                                                                          "conf:/repository/components/secure-vault";

	/**
	 * System property to specify customized docker secret root directory
	 */
	String PROP_DOCKER_SECRET_ROOT_DIRECTORY = "ei.secret.docker.root.dir";
	String PROP_DOCKER_SECRET_ROOT_DIRECTORY_DEFAULT = "/run/secrets/";
	String PROP_DOCKER_SECRET_ROOT_DIRECTORY_DEFAULT_WIN = "C:\\ProgramData\\Docker\\secrets";

	/**
	 * System property to specify customized file secret root directory
	 */
	String PROP_FILE_SECRET_ROOT_DIRECTORY = "ei.secret.file.root.dir";
	String PROP_FILE_SECRET_ROOT_DIRECTORY_DEFAULT = System.getProperty(ServerConstants.CARBON_HOME);

	String FILE_PROTOCOL_PREFIX = "file:";


}
