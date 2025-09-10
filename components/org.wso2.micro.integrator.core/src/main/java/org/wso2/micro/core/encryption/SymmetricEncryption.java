/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.core.encryption;

import org.apache.axiom.om.util.Base64;
import org.wso2.micro.core.util.CryptoException;
import org.wso2.micro.core.util.CryptoUtil;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Properties;

public class SymmetricEncryption {

    private static SymmetricEncryption instance = null;

    private static SecretKey symmetricKey = null;
    private static boolean isSymmetricKeyFromFile = false;
    private static String symmetricKeyEncryptAlgoDefault = "AES";
    private static String symmetricKeySecureVaultAliasDefault = "symmetric.key.value";
    private String propertyKey = "symmetric.key";
    private String symmetricKeyEncryptEnabled;
    private String symmetricKeyEncryptAlgo;
    private String symmetricKeySecureVaultAlias;

    public synchronized static SymmetricEncryption getInstance() {
        if (instance == null) {
            instance = new SymmetricEncryption();
        }
        return instance;
    }

    public void generateSymmetricKey() throws CryptoException {

        OutputStream output = null;
        KeyGenerator generator = null;
        String secretAlias;
        String encryptionAlgo = null;
        Properties properties;

        try {
            CarbonServerConfigurationService serverConfiguration = CarbonServerConfigurationService.getInstance();
            symmetricKeyEncryptEnabled = serverConfiguration.getFirstProperty("SymmetricEncryption.IsEnabled");

            if (!Boolean.parseBoolean(symmetricKeyEncryptEnabled)) {
                return;
            }

            symmetricKeyEncryptAlgo = serverConfiguration.getFirstProperty("SymmetricEncryption.Algorithm");
            symmetricKeySecureVaultAlias = serverConfiguration.getFirstProperty("SymmetricEncryption.SecureVaultAlias");

            String filePath = MicroIntegratorBaseUtils.getCarbonHome() + File.separator + "repository" + File.separator + "resources" +
                    File.separator + "security" + File.separator + "symmetric-key.properties";

            File file = new File(filePath);
            if (file.exists()) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    properties = new Properties();
                    properties.load(fileInputStream);
                }

                SecretResolver secretResolver = SecretResolverFactory.create(properties);
                if (symmetricKeySecureVaultAlias == null) {
                    secretAlias = symmetricKeySecureVaultAliasDefault;
                } else {
                    secretAlias = symmetricKeySecureVaultAlias;
                }

                if (symmetricKeyEncryptAlgo == null) {
                    encryptionAlgo = symmetricKeyEncryptAlgoDefault;
                } else {
                    encryptionAlgo = symmetricKeyEncryptAlgo;
                }

                if (secretResolver != null && secretResolver.isInitialized()) {
                    if (secretResolver.isTokenProtected(secretAlias)) {
                        symmetricKey = new SecretKeySpec(Base64.decode(secretResolver.resolve(secretAlias)), 0,
                                Base64.decode(secretResolver.resolve(secretAlias)).length, encryptionAlgo);
                    } else {
                        symmetricKey = new SecretKeySpec(Base64.decode((String) properties.get(secretAlias)), 0,
                                Base64.decode((String) properties.get(secretAlias)).length, encryptionAlgo);
                    }
                } else if (properties.containsKey(propertyKey)) {
                    symmetricKey = new SecretKeySpec(properties.getProperty(propertyKey).getBytes(), 0,
                            properties.getProperty(propertyKey).getBytes().length, encryptionAlgo);
                }

                if (symmetricKey != null) {
                    isSymmetricKeyFromFile = true;
                }
            }

            if (!isSymmetricKeyFromFile) {
                throw new CryptoException("Error in generating symmetric key. Symmetric key is not available.");
            }
        } catch (Exception e) {
            throw new CryptoException("Error in generating symmetric key", e);
        }
    }

    public byte[] encryptWithSymmetricKey(byte[] plainText) throws CryptoException {
        Cipher c = null;
        byte[] encryptedData = null;
        String encryptionAlgo;
        String symmetricKeyInRegistry;
        String provider = CryptoUtil.getJceProvider();
        try {
            if (symmetricKeyEncryptAlgo == null) {
                encryptionAlgo = symmetricKeyEncryptAlgoDefault;
            } else {
                encryptionAlgo = symmetricKeyEncryptAlgo;
            }
            if (provider != null) {
                c = Cipher.getInstance(encryptionAlgo, provider);
            } else {
                c = Cipher.getInstance(encryptionAlgo);
            }
            c.init(Cipher.ENCRYPT_MODE, symmetricKey);
            encryptedData = c.doFinal(plainText);
        } catch (Exception e) {
            throw new CryptoException("Error when encrypting data.", e);
        }
        return encryptedData;
    }

    public byte[] decryptWithSymmetricKey(byte[] encryptionBytes) throws CryptoException {
        Cipher c = null;
        byte[] decryptedData = null;
        String encryptionAlgo;
        String provider = CryptoUtil.getJceProvider();
        try {
            if (symmetricKeyEncryptAlgo == null) {
                encryptionAlgo = symmetricKeyEncryptAlgoDefault;
            } else {
                encryptionAlgo = symmetricKeyEncryptAlgo;
            }
            if (provider != null) {
                c = Cipher.getInstance(encryptionAlgo, provider);
            } else {
                c = Cipher.getInstance(encryptionAlgo);
            }
            c.init(Cipher.DECRYPT_MODE, symmetricKey);
            decryptedData = c.doFinal(encryptionBytes);
        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException |
                NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new CryptoException("Error when decrypting data.", e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
        return decryptedData;
    }

    public String getSymmetricKeyEncryptEnabled() {
        return symmetricKeyEncryptEnabled;
    }
}
