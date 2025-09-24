/*
 * Copyright 2018 WSO2, Inc. http://www.wso2.org
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
package org.wso2.micro.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.axiom.om.util.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.core.Constants;
import org.wso2.micro.core.encryption.SymmetricEncryption;
import org.wso2.micro.integrator.core.internal.CarbonCoreDataHolder;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;

import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import javax.crypto.Cipher;

/**
 * The utility class to encrypt/decrypt passwords to be stored in the
 * database.
 */
public class CryptoUtil {

    private static final String CIPHER_TRANSFORMATION_SYSTEM_PROPERTY = "org.wso2.CipherTransformation";
    private static final Log log = LogFactory.getLog(CryptoUtil.class);
    private final String primaryKeyStoreAlias;
    private final String internalKeyStoreAlias;
    private final String primaryKeyStoreKeyPass;
    private final String internalKeyStoreKeyPass;
    private final CarbonServerConfigurationService serverConfigService;
    private final Gson gson = new Gson();
    private static CryptoUtil instance = null;
    private static final char[] HEX_CHARACTERS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B',
                                                            'C', 'D', 'E', 'F'};

    /**
     * This method returns CryptoUtil object, where this should only be used at runtime,
     * after the server is properly initialized, or else, use the overloaded method,
     * CryptoUtil#getDefaultCryptoUtil(ServerConfigurationService).
     *
     * @return
     */
    public static CryptoUtil getDefaultCryptoUtil() {
        return getDefaultCryptoUtil(CarbonCoreDataHolder.getInstance().getServerConfigurationService());
    }

    /**
     * This method is used to get the CryptoUtil object given the ServerConfigurationService
     * service. This approach must be used if the CryptoUtil class is used in the server startup,
     * where the ServerConfigurationService may not be available at CarbonCoreDataHolder.
     * The same is also for RegistryService.
     *
     * @param serverConfigService The ServerConfigurationService object
     * @return The created or cached CryptoUtil instance
     */
    public synchronized static CryptoUtil getDefaultCryptoUtil(CarbonServerConfigurationService serverConfigService) {
        if (instance == null) {
            instance = new CryptoUtil(serverConfigService);
        }
        return instance;
    }

    private CryptoUtil(CarbonServerConfigurationService serverConfigService) {
        this.serverConfigService = serverConfigService;
        this.primaryKeyStoreAlias = this.serverConfigService.getFirstProperty(Constants.
                                                                                      SERVER_PRIMARY_KEYSTORE_KEY_ALIAS);
        this.internalKeyStoreAlias = this.serverConfigService.getFirstProperty(Constants.
                                                                                       SERVER_INTERNAL_KEYSTORE_KEY_ALIAS);
        this.primaryKeyStoreKeyPass = this.serverConfigService.getFirstProperty(Constants.
                                                                                        SERVER_PRIVATE_KEY_PASSWORD);
        this.internalKeyStoreKeyPass = this.serverConfigService.getFirstProperty(Constants.
                                                                                         SERVER_INTERNAL_PRIVATE_KEY_PASSWORD);
    }

    public CarbonServerConfigurationService getServerConfigService() {
        return serverConfigService;
    }

    /**
     * Encrypt a given plain text
     *
     * @param plainTextBytes The plaintext bytes to be encrypted
     * @param cipherTransformation The transformation that need to encrypt. If it is null, RSA is used as default
     * @param returnSelfContainedCipherText Create self-contained cipher text if true, return simple encrypted
     *                                      ciphertext otherwise.
     * @return The cipher text bytes
     * @throws org.wso2.micro.core.util.CryptoException On error during encryption
     */
    public byte[] encrypt(byte[] plainTextBytes, String cipherTransformation, boolean returnSelfContainedCipherText)
            throws org.wso2.micro.core.util.CryptoException {

        byte[] encryptedKey;
        org.wso2.micro.core.encryption.SymmetricEncryption encryption = org.wso2.micro.core.encryption.SymmetricEncryption
                .getInstance();
        String provider = getJceProvider();
        try {
            if (Boolean.parseBoolean(encryption.getSymmetricKeyEncryptEnabled())) {
                encryptedKey = encryption.encryptWithSymmetricKey(plainTextBytes);
            } else {
                Cipher keyStoreCipher;
                KeyStore keyStore;
                Certificate[] certs;
                org.wso2.micro.core.util.KeyStoreManager keyMan = org.wso2.micro.core.util.KeyStoreManager.getInstance(
                        Constants.SUPER_TENANT_ID,
                        this.getServerConfigService());
                if (keyMan.getInternalKeyStore() != null) {
                    keyStore = keyMan.getInternalKeyStore();
                    certs = keyStore.getCertificateChain(internalKeyStoreAlias);
                } else {
                    keyStore = keyMan.getPrimaryKeyStore();
                    certs = keyStore.getCertificateChain(primaryKeyStoreAlias);
                }
                boolean isCipherTransformEnabled = false;

                if (cipherTransformation != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Cipher transformation for encryption : " + cipherTransformation);
                    }
                    if (provider != null) {
                        keyStoreCipher = Cipher.getInstance(cipherTransformation, provider);
                    } else {
                        keyStoreCipher = Cipher.getInstance(cipherTransformation);
                    }
                    isCipherTransformEnabled = true;
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Default Cipher transformation for encryption : RSA");
                    }
                    if (provider != null) {
                        keyStoreCipher = Cipher.getInstance(Constants.RSA, provider);
                    } else {
                        keyStoreCipher = Cipher.getInstance(Constants.RSA);
                    }
                }

                keyStoreCipher.init(Cipher.ENCRYPT_MODE, certs[0].getPublicKey());
                if (isCipherTransformEnabled && plainTextBytes.length == 0) {
                    encryptedKey = "".getBytes();
                    if (log.isDebugEnabled()) {
                        log.debug("Empty value for plainTextBytes null will persist to DB");
                    }
                } else {
                    encryptedKey = keyStoreCipher.doFinal(plainTextBytes);
                }
                if (isCipherTransformEnabled && returnSelfContainedCipherText) {
                    encryptedKey = createSelfContainedCiphertext(encryptedKey, cipherTransformation, certs[0]);
                }
            }
        } catch (Exception e) {
            throw new org.wso2.micro.core.util.CryptoException("Error during encryption", e);
        }
        return encryptedKey;
    }

    /**
     * Encrypt a given plain text
     *
     * @param plainTextBytes The plaintext bytes to be encrypted
     * @return The cipher text bytes (self-contained ciphertext)
     * @throws org.wso2.micro.core.util.CryptoException On error during encryption
     */
    public byte[] encrypt(byte[] plainTextBytes) throws org.wso2.micro.core.util.CryptoException {
        //encrypt with transformation configured in carbon.properties as self contained ciphertext
        return encrypt(plainTextBytes, System.getProperty(CIPHER_TRANSFORMATION_SYSTEM_PROPERTY), true);
    }

    /**
     * Encrypt the given plain text with given transformation and base64 encode the encrypted content.
     *
     * @param plainText The plaintext value to be encrypted and base64
     *                  encoded
     * @param transformation The transformation used for encryption
     * @param returnSelfContainedCipherText Create self-contained cipher text if true, return simple encrypted
     *                                      ciphertext otherwise.
     * @return The base64 encoded cipher text
     * @throws org.wso2.micro.core.util.CryptoException On error during encryption
     */
    public String encryptAndBase64Encode(byte[] plainText, String transformation, boolean returnSelfContainedCipherText)
            throws org.wso2.micro.core.util.CryptoException {
        return Base64.encode(encrypt(plainText, transformation, returnSelfContainedCipherText));
    }

    /**
     * Encrypt the given plain text and base64 encode the encrypted content.
     *
     * @param plainText The plaintext value to be encrypted and base64
     *                  encoded
     * @return The base64 encoded cipher text
     * @throws org.wso2.micro.core.util.CryptoException On error during encryption
     */
    public String encryptAndBase64Encode(byte[] plainText) throws org.wso2.micro.core.util.CryptoException {
        return Base64.encode(encrypt(plainText));
    }

    /**
     * Decrypt the given cipher text value using the WSO2 WSAS key
     *
     * @param cipherTextBytes The cipher text to be decrypted
     * @return Decrypted bytes
     * @throws org.wso2.micro.core.util.CryptoException On an error during decryption
     */
    public byte[] decrypt(byte[] cipherTextBytes) throws org.wso2.micro.core.util.CryptoException {

        byte[] decryptedValue;
        org.wso2.micro.core.encryption.SymmetricEncryption encryption = org.wso2.micro.core.encryption.SymmetricEncryption
                .getInstance();
        String provider = getJceProvider();
        try {
            if (Boolean.parseBoolean(encryption.getSymmetricKeyEncryptEnabled())) {
                decryptedValue = encryption.decryptWithSymmetricKey(cipherTextBytes);
            } else {
                Cipher keyStoreCipher;
                KeyStore keyStore;
                PrivateKey privateKey;
                org.wso2.micro.core.util.KeyStoreManager keyMan = org.wso2.micro.core.util.KeyStoreManager.getInstance(
                        Constants.SUPER_TENANT_ID,
                        this.getServerConfigService());
                if (keyMan.getInternalKeyStore() != null) {
                    keyStore = keyMan.getInternalKeyStore();
                    privateKey = (PrivateKey) keyStore.getKey(internalKeyStoreAlias, internalKeyStoreKeyPass.toCharArray());
                } else {
                    keyStore = keyMan.getPrimaryKeyStore();
                    privateKey = (PrivateKey) keyStore.getKey(primaryKeyStoreAlias, primaryKeyStoreKeyPass.toCharArray());
                }
                String cipherTransformation = System.getProperty(CIPHER_TRANSFORMATION_SYSTEM_PROPERTY);
                boolean isCipherTransformEnabled = false;

                if (cipherTransformation != null) {
                    CipherHolder cipherHolder = cipherTextToCipherHolder(cipherTextBytes);
                    if (cipherHolder != null) {
                        // cipher with meta data
                        if (log.isDebugEnabled()) {
                            log.debug("Cipher transformation for decryption : " + cipherHolder.getTransformation());
                        }
                        if (provider != null) {
                            keyStoreCipher = Cipher.getInstance(cipherHolder.getTransformation(), provider);
                        } else {
                            keyStoreCipher = Cipher.getInstance(cipherHolder.getTransformation());
                        }
                        cipherTextBytes = cipherHolder.getCipherBase64Decoded();
                        isCipherTransformEnabled = true;
                    } else {
                        if (provider != null) {
                            keyStoreCipher = Cipher.getInstance(cipherTransformation, provider);
                        } else {
                            keyStoreCipher = Cipher.getInstance(cipherTransformation);
                        }
                        isCipherTransformEnabled = true;
                    }
                } else {
                    // This will reach if the user have removed org.wso2.CipherTransformation from the carbon.properties
                    // or delete carbon.properties file
                    if (provider != null) {
                        keyStoreCipher = Cipher.getInstance(Constants.RSA, provider);
                    } else {
                        keyStoreCipher = Cipher.getInstance(Constants.RSA);
                    }
                }

                keyStoreCipher.init(Cipher.DECRYPT_MODE, privateKey);

                if (isCipherTransformEnabled && cipherTextBytes.length == 0) {
                    decryptedValue = "".getBytes();
                    if (log.isDebugEnabled()) {
                        log.debug("Empty value for plainTextBytes null will persist to DB");
                    }
                } else {
                    decryptedValue = keyStoreCipher.doFinal(cipherTextBytes);
                }
            }
        } catch (Exception e) {
            throw new org.wso2.micro.core.util.CryptoException("errorDuringDecryption", e);
        }
        return decryptedValue;
    }


    /**
     * Decrypt the given cipher text value using the WSO2 WSAS key.
     *
     * IMPORTANT: Since this decrypt method is provided to force required transformation, this will not decrypt
     * self-contained ciphertexts. To decrypt self-contained ciphertext use decrypt(byte[] cipherTextBytes)
     *
     * @param cipherTextBytes The cipher text to be decrypted
     * @param cipherTransformation The transformation that need to decrypt. If it is null, RSA is used as default.
     *                             NOTE: If symmetric encryption enabled, cipherTransformation parameter will be ignored
     * @return Decrypted bytes
     * @throws org.wso2.micro.core.util.CryptoException On an error during decryption
     */
    public byte[] decrypt(byte[] cipherTextBytes, String cipherTransformation) throws
                                                                               org.wso2.micro.core.util.CryptoException {
        byte[] decryptedValue;
        String provider = getJceProvider();
        org.wso2.micro.core.encryption.SymmetricEncryption encryption = SymmetricEncryption.getInstance();
        try {
            if (Boolean.parseBoolean(encryption.getSymmetricKeyEncryptEnabled())) {
                decryptedValue = encryption.decryptWithSymmetricKey(cipherTextBytes);
            } else {
                Cipher keyStoreCipher;
                KeyStore keyStore;
                PrivateKey privateKey;
                org.wso2.micro.core.util.KeyStoreManager keyMan = KeyStoreManager
                        .getInstance(Constants.SUPER_TENANT_ID, this.getServerConfigService());
                if (keyMan.getInternalKeyStore() != null) {
                    keyStore = keyMan.getInternalKeyStore();
                    privateKey = (PrivateKey) keyStore.getKey(internalKeyStoreAlias, internalKeyStoreKeyPass.toCharArray());
                } else {
                    keyStore = keyMan.getPrimaryKeyStore();
                    privateKey = (PrivateKey) keyStore.getKey(primaryKeyStoreAlias, primaryKeyStoreKeyPass.toCharArray());
                }
                if (cipherTransformation != null) {
                    if (provider != null) {
                        keyStoreCipher = Cipher.getInstance(cipherTransformation, provider);
                    } else {
                        keyStoreCipher = Cipher.getInstance(cipherTransformation);
                    }
                } else {
                    if (provider != null) {
                        keyStoreCipher = Cipher.getInstance(Constants.RSA, provider);
                    } else {
                        keyStoreCipher = Cipher.getInstance(Constants.RSA);
                    }
                }

                keyStoreCipher.init(Cipher.DECRYPT_MODE, privateKey);

                if (cipherTextBytes.length == 0) {
                    decryptedValue = "".getBytes();
                    if (log.isDebugEnabled()) {
                        log.debug("Empty value for plainTextBytes null will persist to DB");
                    }
                } else {
                    decryptedValue = keyStoreCipher.doFinal(cipherTextBytes);
                }
            }
        } catch (Exception e) {
            throw new org.wso2.micro.core.util.CryptoException("errorDuringDecryption", e);
        }
        return decryptedValue;
    }

    /**
     * Base64 decode the given value and decrypt using the WSO2 WSAS key
     *
     * @param base64CipherText Base64 encoded cipher text
     * @return Base64 decoded, decrypted bytes
     * @throws org.wso2.micro.core.util.CryptoException On an error during decryption
     */
    public byte[] base64DecodeAndDecrypt(String base64CipherText) throws org.wso2.micro.core.util.CryptoException {
        return decrypt(Base64.decode(base64CipherText));
    }

    /**
     * Base64 decode the given value and decrypt using the WSO2 WSAS key.
     *
     * IMPORTANT: Since this decrypt method is provided to force required transformation, this will not decrypt
     * self-contained ciphertexts. To decrypt self-contained ciphertext use base64DecodeAndDecrypt(byte[] cipherTextBytes)
     *
     * @param base64CipherText Base64 encoded cipher text
     * @param transformation The transformation used for encryption
     * @return Base64 decoded, decrypted bytes
     * @throws org.wso2.micro.core.util.CryptoException On an error during decryption
     */
    public byte[] base64DecodeAndDecrypt(String base64CipherText, String transformation) throws
                                                                                         org.wso2.micro.core.util.CryptoException {
        return decrypt(Base64.decode(base64CipherText), transformation);
    }

    /**
     * Function to validate whether provided is self-contained ciphertext
     *
     * @param cipherBytes interested cipher text byte array
     * @return true if provided cipher is encripted using custom transformation, false if it is RSA
     */
    public boolean isSelfContainedCipherText(byte[] cipherBytes) {
        return cipherTextToCipherHolder(cipherBytes) != null;
    }

    /**
     * Function to Base64 decode the given value and validate whether provided is self-contained ciphertext
     *
     * @param base64CipherText interested cipher text byte array
     * @return true if provided cipher is self-contained cipher text
     */
    public boolean base64DecodeAndIsSelfContainedCipherText(String base64CipherText) throws CryptoException {
        return isSelfContainedCipherText(Base64.decode(base64CipherText));
    }

    /**
     * This util method will extract the original cipher text content from self-contained cipher
     *
     * @param cipher cipher text in as a byte array
     * @return returns
     */
    public byte[] extractOriginalCipher(byte[] cipher) {
        CipherHolder cipherHolder = cipherTextToCipherHolder(cipher);
        if (cipherHolder != null) {
            return cipherHolder.getCipherBase64Decoded();
        }
        return cipher;
    }

    /**
     * This function will create self-contained ciphertext with metadata
     *
     * @param originalCipher ciphertext need to wrap with metadata
     * @param transformation transformation used to encrypt ciphertext
     * @param certificate certificate that holds relevant keys used to encrypt
     * @return setf-contained ciphertext
     * @throws CertificateEncodingException
     * @throws NoSuchAlgorithmException
     */
    public byte[] createSelfContainedCiphertext(byte[] originalCipher, String transformation, Certificate certificate)
            throws CertificateEncodingException, NoSuchAlgorithmException {

        CipherHolder cipherHolder = new CipherHolder();
        cipherHolder.setCipherText(Base64.encode(originalCipher));
        cipherHolder.setTransformation(transformation);
        cipherHolder.setThumbPrint(calculateThumbprint(certificate));
        String cipherWithMetadataStr = gson.toJson(cipherHolder);
        if (log.isDebugEnabled()) {
            log.debug("Cipher with meta data : " + cipherWithMetadataStr);
        }
        return cipherWithMetadataStr.getBytes(Charset.defaultCharset());
    }

    /**
     * Function to convert cipher byte array to {@link CipherHolder}
     *
     * @param cipherText cipher text as a byte array
     * @return if cipher text is not a cipher with meta data
     */
    public CipherHolder cipherTextToCipherHolder(byte[] cipherText) {

        String cipherStr = new String(cipherText, Charset.defaultCharset());
        try {
            return gson.fromJson(cipherStr, CipherHolder.class);
        } catch (JsonSyntaxException e) {
            if (log.isDebugEnabled()) {
                log.debug("Deserialization failed since cipher string is not representing cipher with metadata");
            }
            return null;
        }
    }

    private String calculateThumbprint(Certificate certificate)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        String provider = getJceProvider();
        MessageDigest messageDigest;
        if (provider != null) {
            try {
                messageDigest = MessageDigest.getInstance("SHA-256", provider);
            } catch (NoSuchProviderException e) {
                throw new CertificateEncodingException(e);
            }
        } else {
            messageDigest = MessageDigest.getInstance("SHA-1");
        }

        messageDigest.update(certificate.getEncoded());
        byte[] digestByteArray = messageDigest.digest();

        // convert digest in form of byte array to hex format
        StringBuffer strBuffer = new StringBuffer();

        for (int i = 0; i < digestByteArray.length; i++) {
            int leftNibble = (digestByteArray[i] & 0xF0) >> 4;
            int rightNibble = (digestByteArray[i] & 0x0F);
            strBuffer.append(HEX_CHARACTERS[leftNibble]).append(HEX_CHARACTERS[rightNibble]);
        }

        return strBuffer.toString();
    }

    /**
     * Get the JCE provider to be used for encryption/decryption
     *
     * @return
     */
    public static String getJceProvider() {
        String provider = System.getProperty(Constants.SECURITY_JCE_PROVIDER);
        if (provider != null && (provider.equalsIgnoreCase(Constants.BOUNCY_CASTLE_FIPS_PROVIDER) ||
                provider.equalsIgnoreCase(Constants.BOUNCY_CASTLE_PROVIDER))) {
            return provider;
        }
        return null;
    }
}

