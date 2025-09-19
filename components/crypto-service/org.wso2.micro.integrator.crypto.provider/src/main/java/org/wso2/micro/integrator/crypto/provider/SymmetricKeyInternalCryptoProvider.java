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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.crypto.provider;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.crypto.api.CryptoException;
import org.wso2.carbon.crypto.api.InternalCryptoProvider;
import org.wso2.micro.integrator.crypto.provider.internal.DefaultCryptoProviderComponent;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * The symmetric key implementation of {@link InternalCryptoProvider}
 */
public class SymmetricKeyInternalCryptoProvider implements InternalCryptoProvider {

    private static Log log = LogFactory.getLog(SymmetricKeyInternalCryptoProvider.class);
    private String secretKey;

    public SymmetricKeyInternalCryptoProvider(String secretKey) {

        this.secretKey = secretKey;
    }

    /**
     * Computes and returns the ciphertext of the given cleartext, using the underlying key store.
     *
     * @param cleartext               The cleartext to be encrypted.
     * @param algorithm               The encryption / decryption algorithm
     * @param javaSecurityAPIProvider
     * @return the ciphertext
     * @throws CryptoException
     */
    @Override
    public byte[] encrypt(byte[] cleartext, String algorithm, String javaSecurityAPIProvider) throws CryptoException {

        try {
            Cipher cipher;
            if (StringUtils.isBlank(javaSecurityAPIProvider)) {
                javaSecurityAPIProvider = DefaultCryptoProviderComponent.getPreferredJceProvider();
            }
            if (StringUtils.isBlank(javaSecurityAPIProvider)) {
                cipher = Cipher.getInstance(algorithm);
            } else {
                cipher = Cipher.getInstance(algorithm, javaSecurityAPIProvider);
            }

            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(algorithm));
            return cipher.doFinal(cleartext);
        } catch (InvalidKeyException | NoSuchPaddingException | BadPaddingException | NoSuchProviderException | IllegalBlockSizeException | NoSuchAlgorithmException e) {
            String errorMessage = String
                    .format("An error occurred while encrypting using the algorithm : '%s'", algorithm);

            // Log the exception from client libraries, to avoid missing information if callers code doesn't log it
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }

            throw new CryptoException(errorMessage, e);
        }
    }

    /**
     * Computes and returns the cleartext of the given ciphertext.
     *
     * @param ciphertext              The ciphertext to be decrypted.
     * @param algorithm               The encryption / decryption algorithm
     * @param javaSecurityAPIProvider
     * @return The cleartext
     * @throws CryptoException If something unexpected happens during the decryption operation.
     */
    public byte[] decrypt(byte[] ciphertext, String algorithm, String javaSecurityAPIProvider) throws CryptoException {

        try {
            Cipher cipher;
            if (StringUtils.isBlank(javaSecurityAPIProvider)) {
                javaSecurityAPIProvider = DefaultCryptoProviderComponent.getPreferredJceProvider();
            }
            if (StringUtils.isBlank(javaSecurityAPIProvider)) {
                cipher = Cipher.getInstance(algorithm);
            } else {
                cipher = Cipher.getInstance(algorithm, javaSecurityAPIProvider);
            }

            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(algorithm));

            return cipher.doFinal(ciphertext);
        } catch (InvalidKeyException | NoSuchPaddingException | BadPaddingException | NoSuchProviderException | IllegalBlockSizeException | NoSuchAlgorithmException e) {
            String errorMessage = String
                    .format("An error occurred while decrypting using the algorithm : '%s'", algorithm);

            // Log the exception from client libraries, to avoid missing information if callers code doesn't log it
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }

            throw new CryptoException(errorMessage, e);
        }
    }

    private SecretKeySpec getSecretKey(String algorithm) {

        return new SecretKeySpec(secretKey.getBytes(), 0, secretKey.getBytes().length, algorithm);
    }

}
