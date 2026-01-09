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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.websocket.transport.WebsocketConnectionFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class SSLUtil {

    private static final String PROTOCOL = "TLS";
    private static final String PKIX = "PKIX";
    private static String trustStoreType = "JKS";
    private static final String JCE_PROVIDER = "security.jce.provider";

    private static SSLContext serverSSLCtx = null;
    private static SSLContext clientSSLCtx = null;

    private static final Log LOGGER = LogFactory.getLog(SSLUtil.class);

    public static SSLContext createServerSSLContext(final String keyStoreLocation, final String keyStorePwd) {
        try {
            if (serverSSLCtx == null) {
                KeyStore keyStore = KeyStore.getInstance(WebsocketConnectionFactory.getTrustStoreType());
                keyStore.load(new FileInputStream(keyStoreLocation), keyStorePwd.toCharArray());
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getKeyManagerType());
                keyManagerFactory.init(keyStore, keyStorePwd.toCharArray());
                serverSSLCtx = SSLContext.getInstance(PROTOCOL);
                serverSSLCtx.init(keyManagerFactory.getKeyManagers(), null, null);
            }
        } catch (UnrecoverableKeyException | KeyManagementException | NoSuchAlgorithmException | CertificateException | KeyStoreException | IOException e) {
            LOGGER.error("Exception was thrown while building the server SSL Context", e);
        }

        return serverSSLCtx;
    }

    public static SSLContext createClientSSLContext(final String trustStoreLocation, final String trustStorePwd) {
        try {
            if (clientSSLCtx == null) {
                KeyStore trustStore = KeyStore.getInstance(WebsocketConnectionFactory.getTrustStoreType());
                trustStore.load(new FileInputStream(trustStoreLocation), trustStorePwd.toCharArray());
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(getTrustManagerType());
                trustManagerFactory.init(trustStore);
                clientSSLCtx = SSLContext.getInstance(PROTOCOL);
                clientSSLCtx.init(null, trustManagerFactory.getTrustManagers(), null);
            }
        } catch (KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            LOGGER.error("Exception was thrown while building the client SSL Context", e);
        }

        return clientSSLCtx;

    }

    public static TrustManagerFactory createTrustmanager(final String trustStoreLocation, final String trustStorePwd) {
        TrustManagerFactory trustManagerFactory = null;
        try {
            if (clientSSLCtx == null) {
                KeyStore trustStore = KeyStore.getInstance(WebsocketConnectionFactory.getTrustStoreType());
                trustStore.load(new FileInputStream(trustStoreLocation), trustStorePwd.toCharArray());
                trustManagerFactory = TrustManagerFactory.getInstance(getTrustManagerType());
                trustManagerFactory.init(trustStore);
            }
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            LOGGER.error("Exception was thrown while building the client SSL Context", e);
        }
        return trustManagerFactory;
    }

    private static String getTrustManagerType() {
        String provider = System.getProperty(JCE_PROVIDER);
        if (StringUtils.isNotEmpty(provider)) {
            return PKIX;
        }
        return TrustManagerFactory.getDefaultAlgorithm();
    }

    private static String getKeyManagerType() {
        String provider = System.getProperty(JCE_PROVIDER);
        if (StringUtils.isNotEmpty(provider)) {
            return PKIX;
        }
        return KeyManagerFactory.getDefaultAlgorithm();
    }

}
