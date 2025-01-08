/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.micro.core.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.core.Constants;
import org.wso2.micro.integrator.core.internal.CarbonCoreDataHolder;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.commons.MiscellaneousUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The purpose of this class is to centrally manage the key stores.
 * Load key stores only once.
 * Reloading them over and over result a in a performance penalty.
 */
public class KeyStoreManager {

    private KeyStore primaryKeyStore = null;
    private KeyStore registryKeyStore = null;
    private KeyStore internalKeyStore = null;
    private static final ConcurrentMap<String, KeyStore> keyStoreMap = new ConcurrentHashMap<>();
    private static final Lock lock = new ReentrantLock();

    private static ConcurrentHashMap<String, KeyStoreManager> mtKeyStoreManagers = new ConcurrentHashMap<>();
    private static Log log = LogFactory.getLog(KeyStoreManager.class);

    private int tenantId;

    private CarbonServerConfigurationService serverConfigService;

    private KeyStoreManager(int tenantId, CarbonServerConfigurationService serverConfigService) {
        this.serverConfigService = serverConfigService;
        this.tenantId = tenantId;
    }

    public CarbonServerConfigurationService getServerConfigService() {
        return serverConfigService;
    }

    /**
     * Get a KeyStoreManager instance for that tenant. This method will return an KeyStoreManager
     * instance if exists, or creates a new one. Only use this at runtime, or else,
     * use KeyStoreManager#getInstance(UserRegistry, ServerConfigurationService).
     *
     * @param tenantId id of the corresponding tenant
     * @return KeyStoreManager instance for that tenant
     */
    public static KeyStoreManager getInstance(int tenantId) {
        return getInstance(tenantId, CarbonCoreDataHolder.getInstance().
                getServerConfigurationService());
    }

    public static KeyStoreManager getInstance(int tenantId, CarbonServerConfigurationService serverConfigService) {
        MicroIntegratorBaseUtils.checkSecurity();
        String tenantIdStr = Integer.toString(tenantId);
        if (!mtKeyStoreManagers.containsKey(tenantIdStr)) {
            mtKeyStoreManagers.put(tenantIdStr, new KeyStoreManager(tenantId, serverConfigService));
        }
        return mtKeyStoreManagers.get(tenantIdStr);
    }
    /**
     * Get the key store object for the given key store name
     *
     * @param keyStoreName key store name
     * @return KeyStore object
     * @throws Exception If there is not a key store with the given name
     */
    public KeyStore getKeyStore(String keyStoreName) throws Exception {
        List<Map<String, String>> configList = (ArrayList) ConfigParser.getParsedConfigs().get(
                Constants.SERVER_PRIVATE_STORE_CONFIG);
        Map<String, String> keyStoreDetails = getPrivateKeyStoreDetails(keyStoreName, configList);
        if (keyStoreDetails == null) {
            return getPrimaryKeyStore();
        }
        CarbonServerConfigurationService config = this.getServerConfigService();
        String primaryKeyStorePath = config.getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_FILE);
        String privateKeyStorePath = keyStoreDetails.get(Constants.SERVER_PRIVATE_KEYSTORE_FILE);
        if (areSameKeyStore(primaryKeyStorePath, privateKeyStorePath)) {
            return getPrimaryKeyStore();
        }
        return getPrivateKeyStore(keyStoreName, keyStoreDetails, config);
    }

    /**
     * Get the key store name for the given file path
     * @param path File path of the key store
     * @return key store name
     */
    public static String getKeyStoreNameFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // Normalize the path to handle different path separators
        Path normalizedPath = Paths.get(path).normalize();
        return normalizedPath.getFileName().toString();
    }

    /**
     * This method loads the private key of a given key store
     *
     * @param keyStoreName name of the key store
     * @param alias        alias of the private key
     * @return private key corresponding to the alias
     */
    public Key getPrivateKey(String keyStoreName, String alias) {
        try {
                return getDefaultPrivateKey();

        } catch (Exception e) {
            log.error("Error loading the private key from the key store : " + keyStoreName);
            throw new SecurityException("Error loading the private key from the key store : " +
                    keyStoreName, e);
        }
    }

    /**
     * Get the key store password for the given key store name.
     * Note:  Caching has been not implemented for this method
     *
     * @param keyStoreName key store name
     * @return KeyStore object
     * @throws Exception If there is not a key store with the given name
     */
    public String getKeyStorePassword(String keyStoreName) throws Exception {

        // TODO need to implement this properly
        return "admin";
    }

    /**
     * Load the primary key store, this is allowed only for the super tenant
     *
     * @return primary key store object
     * @throws Exception Carbon Exception when trying to call this method from a tenant other
     *                   than tenant 0
     */
    public KeyStore getPrimaryKeyStore() throws Exception {
        if (tenantId == Constants.SUPER_TENANT_ID) {
            if (primaryKeyStore == null) {

                CarbonServerConfigurationService config = this.getServerConfigService();
                String file =
                        new File(config
                                .getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_FILE))
                                .getAbsolutePath();
                KeyStore store = KeyStore
                        .getInstance(config
                                .getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_TYPE));
                String password = config
                        .getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_PASSWORD);
                FileInputStream in = null;
                try {
                    in = new FileInputStream(file);
                    store.load(in, password.toCharArray());
                    primaryKeyStore = store;
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }
            }
            return primaryKeyStore;
        } else {
            throw new CarbonException("Permission denied for accessing primary key store. The primary key store is " +
                    "available only for the super tenant.");
        }
    }

    /**
     * Checks if two relative file paths point to the same key store.
     *
     * @param relativePath1 The first relative file path.
     * @param relativePath2 The second relative file path.
     * @return true if both paths point to the same key store, false otherwise.
     * @throws IOException If an I/O error occurs while resolving paths.
     */
    public static boolean areSameKeyStore(String relativePath1, String relativePath2) throws IOException {
        if (relativePath1 == null || relativePath2 == null) {
            throw new IllegalArgumentException("File paths cannot be null");
        }

        // Resolve the relative paths to absolute paths
        Path path1 = Paths.get(relativePath1).toRealPath();
        Path path2 = Paths.get(relativePath2).toRealPath();

        // Compare the absolute paths
        return Files.isSameFile(path1, path2);
    }

    /**
     * Get the key store object for the given key store name
     *
     * @param keyStoreName key store name
     * @return KeyStore object
     * @throws Exception If there is not a key store with the given name
     */
    public static KeyStore getPrivateKeyStore(String keyStoreName,
                                              Map<String, String> keyStoreDetails,
                                               CarbonServerConfigurationService configurationService) throws Exception {
        if (keyStoreName == null || keyStoreName.isEmpty()) {
            throw new IllegalArgumentException("KeyStore name cannot be null or empty");
        }

        KeyStore keyStore = keyStoreMap.get(keyStoreName);

        if (keyStore == null) {
            lock.lock();
            try {
                // Double-check to prevent race condition
                keyStore = keyStoreMap.get(keyStoreName);
                if (keyStore == null) {
                    String file =
                            new File(keyStoreDetails.get(Constants.SERVER_PRIVATE_KEYSTORE_FILE))
                                    .getAbsolutePath();
                    KeyStore store = KeyStore
                            .getInstance(keyStoreDetails.get(Constants.SERVER_PRIVATE_KEYSTORE_TYPE));
                    String password = keyStoreDetails.get(Constants.SERVER_PRIVATE_KEYSTORE_PASSWORD);
                    String alias = MiscellaneousUtil.getProtectedToken(password);
                    if (!StringUtils.isEmpty(alias)) {
                        password = configurationService.getResolvedValue(alias);
                    }
                    FileInputStream in = null;
                    try {
                        in = new FileInputStream(file);
                        store.load(in, password.toCharArray());
                        keyStoreMap.put(keyStoreName, store);
                        keyStore = store;
                    } finally {
                        if (in != null) {
                            in.close();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        return keyStore;
    }


    /**
     * Retrieves details of a private key store based on the provided input file name.
     *
     * @param keyStoreName The file name to search for.
     * @param configList    The list of maps containing key-value pairs.
     * @return The map containing details of the private key store if found, or null otherwise.
     */
    public static Map<String, String> getPrivateKeyStoreDetails(String keyStoreName,
                                                                List<Map<String, String>> configList) {
        if (keyStoreName == null || keyStoreName.isEmpty() || configList == null) {
            return null;
        }

        for (Map<String, String> map : configList) {
            // Extract the file name from the file path
            String filePath = map.get(Constants.SERVER_PRIVATE_KEYSTORE_FILE);
            String fileName = getKeyStoreNameFromPath(filePath);

            // Compare the extracted file name with the input file name
            if (keyStoreName.equals(fileName)) {
                return map;
            }
        }

        return null; // If no match is found
    }


    /**
     * Load the internal key store, this is allowed only for the super tenant
     *
     * @return internal key store object
     * @throws Exception Carbon Exception when trying to call this method from a tenant other
     *                   than tenant 0
     */
    public KeyStore getInternalKeyStore() throws Exception {

        if (tenantId == Constants.SUPER_TENANT_ID) {
            if (internalKeyStore == null) {
                CarbonServerConfigurationService config = this.getServerConfigService();
                if (config.
                        getFirstProperty(Constants.SERVER_INTERNAL_KEYSTORE_FILE) == null) {
                    return null;
                }
                String file = new File(config
                        .getFirstProperty(Constants.SERVER_INTERNAL_KEYSTORE_FILE))
                        .getAbsolutePath();
                KeyStore store = KeyStore.getInstance(config
                        .getFirstProperty(Constants.SERVER_INTERNAL_KEYSTORE_TYPE));
                String password = config
                        .getFirstProperty(Constants.SERVER_INTERNAL_KEYSTORE_PASSWORD);
                try (FileInputStream in = new FileInputStream(file)) {
                    store.load(in, password.toCharArray());
                    internalKeyStore = store;
                }
            }
            return internalKeyStore;
        } else {
            throw new CarbonException("Permission denied for accessing internal key store. The internal key store is " +
                    "available only for the super tenant.");
        }
    }

    /**
     * Load the register key store, this is allowed only for the super tenant
     *
     * @deprecated use {@link #getPrimaryKeyStore()} instead.
     *
     * @return register key store object
     * @throws Exception Carbon Exception when trying to call this method from a tenant other
     *                   than tenant 0
     */
    @Deprecated
    public KeyStore getRegistryKeyStore() throws Exception {
        if (tenantId == Constants.SUPER_TENANT_ID) {
            if (registryKeyStore == null) {

                CarbonServerConfigurationService config = this.getServerConfigService();
                String file =
                        new File(config
                                .getFirstProperty(Constants.SERVER_REGISTRY_KEYSTORE_FILE))
                                .getAbsolutePath();
                KeyStore store = KeyStore
                        .getInstance(config
                                .getFirstProperty(Constants.SERVER_REGISTRY_KEYSTORE_TYPE));
                String password = config
                        .getFirstProperty(Constants.SERVER_REGISTRY_KEYSTORE_PASSWORD);
                FileInputStream in = null;
                try {
                    in = new FileInputStream(file);
                    store.load(in, password.toCharArray());
                    registryKeyStore = store;
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }
            }
            return registryKeyStore;
        } else {
            throw new CarbonException("Permission denied for accessing registry key store. The registry key store is" +
                    " available only for the super tenant.");
        }
    }

    /**
     * Get the default private key, only allowed for tenant 0
     *
     * @return Private key
     * @throws Exception Carbon Exception for tenants other than tenant 0
     */
    public PrivateKey getDefaultPrivateKey() throws Exception {
        if (tenantId == Constants.SUPER_TENANT_ID) {
            CarbonServerConfigurationService config = this.getServerConfigService();
            String password = config
                    .getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_PASSWORD);
            String alias = config
                    .getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_KEY_ALIAS);
            return (PrivateKey) primaryKeyStore.getKey(alias, password.toCharArray());
        }
        throw new CarbonException("Permission denied for accessing primary key store. The primary key store is " +
                "available only for the super tenant.");
    }

    /**
     * Get default pub. key
     *
     * @return Public Key
     * @throws Exception Exception Carbon Exception for tenants other than tenant 0
     */
    public PublicKey getDefaultPublicKey() throws Exception {
        if (tenantId == Constants.SUPER_TENANT_ID) {
            CarbonServerConfigurationService config = this.getServerConfigService();
            String alias = config
                    .getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_KEY_ALIAS);
            return (PublicKey) primaryKeyStore.getCertificate(alias).getPublicKey();
        }
        throw new CarbonException("Permission denied for accessing primary key store. The primary key store is " +
                "available only for the super tenant.");
    }

    /**
     * Get the private key password
     *
     * @return private key password
     * @throws CarbonException Exception Carbon Exception for tenants other than tenant 0
     */
    public String getPrimaryPrivateKeyPasssword() throws CarbonException {
        if (tenantId == Constants.SUPER_TENANT_ID) {
            CarbonServerConfigurationService config = this.getServerConfigService();
            return config
                    .getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_PASSWORD);
        }
        throw new CarbonException("Permission denied for accessing primary key store. The primary key store is " +
                "available only for the super tenant.");
    }

    /**
     * This method is used to load the default public certificate of the primary key store
     *
     * @return Default public certificate
     * @throws Exception Permission denied for accessing primary key store
     */
    public X509Certificate getDefaultPrimaryCertificate() throws Exception {
        if (tenantId == Constants.SUPER_TENANT_ID) {
            CarbonServerConfigurationService config = this.getServerConfigService();
            String alias = config
                    .getFirstProperty(Constants.SERVER_PRIMARY_KEYSTORE_KEY_ALIAS);
            return (X509Certificate) getPrimaryKeyStore().getCertificate(alias);
        }
        throw new CarbonException("Permission denied for accessing primary key store. The primary key store is " +
                "available only for the super tenant.");
    }



    public KeyStore loadKeyStoreFromFileSystem(String keyStorePath, String password, String type) {
        MicroIntegratorBaseUtils.checkSecurity();
        String absolutePath = new File(keyStorePath).getAbsolutePath();
        FileInputStream inputStream = null;
        try {
            KeyStore store = KeyStore.getInstance(type);
            inputStream = new FileInputStream(absolutePath);
            store.load(inputStream, password.toCharArray());
            return store;
        } catch (Exception e) {
            String errorMsg = "Error loading the key store from the given location.";
            log.error(errorMsg);
            throw new SecurityException(errorMsg, e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                log.warn("Error when closing the input stream.", e);
            }
        }
    }
}
