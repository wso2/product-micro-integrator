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

package org.wso2.micro.integrator.websocket.transport.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class SSLUtil {

    private static String KEY_STORE_TYPE = "JKS";
    private static String TRUST_STORE_TYPE = "JKS";
    private static String KEY_MANAGER_TYPE = "SunX509";
    private static String TRUST_MANAGER_TYPE = "SunX509";
    private static String PROTOCOL = "TLSv1.2";

    private static SSLContext serverSSLCtx = null;
    private static SSLContext clientSSLCtx = null;
    private static final String BOUNCY_CASTLE_PROVIDER = "BC";
    private static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
    private static final String SECURITY_JCE_PROVIDER = "security.jce.provider";

    private static final Log LOGGER = LogFactory.getLog(SSLUtil.class);

    public static SSLContext createServerSSLContext(final String keyStoreLocation, final String keyStorePwd,
                                                    String type) {
        try {
            if (serverSSLCtx == null) {
                type = (type != null) ? type : TRUST_STORE_TYPE;
                String provider = getPreferredJceProvider();
                KeyStore keyStore;
                if (provider != null) {
                    keyStore = KeyStore.getInstance(type, provider);
                } else {
                    keyStore = KeyStore.getInstance(type);
                }
                keyStore.load(Files.newInputStream(Paths.get(keyStoreLocation)), keyStorePwd.toCharArray());
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KEY_MANAGER_TYPE);
                keyManagerFactory.init(keyStore, keyStorePwd.toCharArray());
                serverSSLCtx = SSLContext.getInstance(PROTOCOL);
                serverSSLCtx.init(keyManagerFactory.getKeyManagers(), null, null);
            }
        } catch (UnrecoverableKeyException | KeyManagementException | NoSuchAlgorithmException | CertificateException |
                 KeyStoreException | IOException | NoSuchProviderException e) {
            LOGGER.error("Exception was thrown while building the server SSL Context", e);
        }

        return serverSSLCtx;
    }

    public static SSLContext createClientSSLContext(final String trustStoreLocation, final String trustStorePwd,
                                                    String type) {
        try {
            if (clientSSLCtx == null) {
                type = (type != null) ? type : TRUST_STORE_TYPE;
                String provider = getPreferredJceProvider();
                KeyStore trustStore;
                if (provider != null) {
                    trustStore = KeyStore.getInstance(type, provider);
                } else {
                    trustStore = KeyStore.getInstance(type);
                }
                trustStore.load(Files.newInputStream(Paths.get(trustStoreLocation)), trustStorePwd.toCharArray());
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_TYPE);
                trustManagerFactory.init(trustStore);
                clientSSLCtx = SSLContext.getInstance(PROTOCOL);
                clientSSLCtx.init(null, trustManagerFactory.getTrustManagers(), null);
            }
        } catch (KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException |
                 IOException | NoSuchProviderException e) {
            LOGGER.error("Exception was thrown while building the client SSL Context", e);
        }

        return clientSSLCtx;

    }

    public static TrustManagerFactory createTrustmanager(final String trustStoreLocation, final String trustStorePwd,
                                                         String type) {
        TrustManagerFactory trustManagerFactory = null;
        try {
            if (clientSSLCtx == null) {
                type = (type != null) ? type : TRUST_STORE_TYPE;
                String provider = getPreferredJceProvider();
                KeyStore trustStore;
                if (provider != null) {
                    trustStore = KeyStore.getInstance(type, provider);
                } else {
                    trustStore = KeyStore.getInstance(type);
                }
                trustStore.load(Files.newInputStream(Paths.get(trustStoreLocation)), trustStorePwd.toCharArray());
                trustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_TYPE);
                trustManagerFactory.init(trustStore);
            }
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException|
                 NoSuchProviderException e) {
            LOGGER.error("Exception was thrown while building the client SSL Context", e);
        }
        return trustManagerFactory;
    }

    /**
     * Get the preferred JCE provider.
     *
     * @return the preferred JCE provider
     */
    public static String getPreferredJceProvider() {
        String provider = System.getProperty(SECURITY_JCE_PROVIDER);
        if (provider != null && (provider.equalsIgnoreCase(BOUNCY_CASTLE_FIPS_PROVIDER) ||
                provider.equalsIgnoreCase(BOUNCY_CASTLE_PROVIDER))) {
            return provider;
        }
        return null;
    }
}
