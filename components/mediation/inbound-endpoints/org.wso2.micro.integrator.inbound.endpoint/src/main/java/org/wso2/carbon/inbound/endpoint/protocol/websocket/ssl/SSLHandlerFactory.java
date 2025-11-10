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

package org.wso2.carbon.inbound.endpoint.protocol.websocket.ssl;

import io.netty.handler.ssl.SslHandler;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SSLHandlerFactory {

    private static final String protocol = "TLSv1.2";
    private final SSLContext serverContext;
    private boolean needClientAuth;
    private final String[] cipherSuites;
    private final String[] sslProtocols;
    private static final String PKIX = "PKIX";
    private static final String JCE_PROVIDER = "security.jce.provider";

    public SSLHandlerFactory(InboundWebsocketSSLConfiguration sslConfiguration) {
        try {
            KeyStore keyStore = getKeyStore(sslConfiguration.getKeyStore(), sslConfiguration.getKeyStorePass(),
                    sslConfiguration.getKeyStoreType());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getKeyManagerType());
            keyManagerFactory.init(keyStore, sslConfiguration.getCertPass() != null ?
                    sslConfiguration.getCertPass().toCharArray() :
                    sslConfiguration.getKeyStorePass().toCharArray());
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            TrustManager[] trustManagers = null;
            if (sslConfiguration.getTrustStore() != null) {
                this.needClientAuth = true;
                KeyStore trustStore = getKeyStore(sslConfiguration.getTrustStore(),
                        sslConfiguration.getTrustStorePass(), sslConfiguration.getTrustStoreType());
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(getTrustManagerType());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
            serverContext = SSLContext.getInstance(protocol);
            serverContext.init(keyManagers, trustManagers, null);
            cipherSuites = sslConfiguration.getCipherSuites();
            sslProtocols = sslConfiguration.getSslProtocols();
        } catch (UnrecoverableKeyException | KeyManagementException | NoSuchAlgorithmException | KeyStoreException | IOException ex) {
            throw new IllegalArgumentException("Failed to initialize the server side SSLContext", ex);
        }
    }

    private static KeyStore getKeyStore(File keyStore, String keyStorePassword,
                                        String keyStoreType) throws IOException {
        KeyStore keyStoreInstance;
        try (InputStream is = new FileInputStream(keyStore)) {
            keyStoreInstance = KeyStore.getInstance(keyStoreType);
            keyStoreInstance.load(is, keyStorePassword.toCharArray());
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new IOException(e);
        }
        return keyStoreInstance;
    }

    public SslHandler create() {
        SSLEngine engine = serverContext.createSSLEngine();
        if (cipherSuites != null) {
            engine.setEnabledCipherSuites(cipherSuites);
        }
        if (sslProtocols != null) {
            engine.setEnabledProtocols(sslProtocols);
        }
        engine.setNeedClientAuth(needClientAuth);
        engine.setUseClientMode(false);
        return new SslHandler(engine);
    }

    private static String getKeyManagerType() {
        if (StringUtils.isNotEmpty(System.getProperty(JCE_PROVIDER))) {
            return PKIX;
        } else {
            return KeyManagerFactory.getDefaultAlgorithm();
        }
    }

    private static String getTrustManagerType() {
        if (StringUtils.isNotEmpty(System.getProperty(JCE_PROVIDER))) {
            return PKIX;
        } else {
            return TrustManagerFactory.getDefaultAlgorithm();
        }
    }

}
