/*
Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.initializer.handler.transaction.security;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.initializer.handler.transaction.TransactionConstants;
import org.wso2.micro.integrator.initializer.handler.transaction.exception.TransactionCounterException;
import org.wso2.micro.integrator.initializer.handler.transaction.exception.TransactionCounterInitializationException;
import org.wso2.micro.integrator.initializer.utils.DeployerUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.xml.bind.DatatypeConverter;

/**
 * This feature is deprecated and will be removed in a future release.
 * @deprecated
 *
 * This class will provide the required methods to encrypt a given value.
 */
public class CryptoUtil {

    private static final Log LOG = LogFactory.getLog(CryptoUtil.class);

    private CryptoUtil() {
    }

    /**
     * Initializes the Cipher.
     *
     * @return cipher.
     * @throws TransactionCounterInitializationException - when something goes wrong while initializing the cipher.
     */
    public static Cipher initializeCipher() throws TransactionCounterInitializationException {
        Cipher cipher;
        String provider = DeployerUtil.getJceProvider();
        try {
            PublicKey publicKey = loadPublicKey();
            if (provider != null) {
                cipher = Cipher.getInstance(TransactionConstants.ENCRYPTION_ALGORITHM, provider);
            } else {
                cipher = Cipher.getInstance(TransactionConstants.ENCRYPTION_ALGORITHM);
            }
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            LOG.debug(
                    "Successfully initialized the Cipher to be used in the transaction count encryption process in "
                            + "the Transaction Count Handler component.");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidKeySpecException
                | IOException e) {
            throw new TransactionCounterInitializationException("Error initializing Cipher ", e);
        } catch (NoSuchProviderException e) {
            throw new TransactionCounterInitializationException("Specified security provider is not available " +
                    "in this environment: ", e);
        }
        return cipher;
    }

    /**
     * Encrypt the plain text value.
     *
     * @param cipher         init cipher
     * @param plainTextValue transaction count in plain text.
     * @return encrypted transaction count
     * @throws TransactionCounterException
     */
    public static String doEncryption(Cipher cipher, String plainTextValue) throws TransactionCounterException {
        String encodedValue;
        try {
            byte[] encryptedPassword = cipher.doFinal(plainTextValue.getBytes(StandardCharsets.UTF_8));
            encodedValue = DatatypeConverter.printBase64Binary(encryptedPassword);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new TransactionCounterException("Error encrypting transaction count ", e);
        }
        return encodedValue;
    }

    private static PublicKey loadPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchProviderException {

        String publicKeyPEM = FileUtils.readFileToString(new File(TransactionConstants.PUBLIC_KEY),
                                                         StandardCharsets.UTF_8);
        // strip of header, footer, newlines, whitespaces.
        publicKeyPEM = publicKeyPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        // decode to get the binary DER representation.
        byte[] publicKeyDER = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory keyFactory;
        String provider = DeployerUtil.getJceProvider();
        if (provider != null) {
            keyFactory = KeyFactory.getInstance("RSA", provider);
        } else {
            keyFactory = KeyFactory.getInstance("RSA");
        }
        return keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyDER));
    }
}
