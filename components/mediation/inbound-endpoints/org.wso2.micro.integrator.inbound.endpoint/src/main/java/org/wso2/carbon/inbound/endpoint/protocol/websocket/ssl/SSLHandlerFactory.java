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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
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
    private static final String BOUNCY_CASTLE_PROVIDER = "BC";
    private static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
    private static final String SECURITY_JCE_PROVIDER = "security.jce.provider";
    private static final String BCJSSE = "BCJSSE";

    public SSLHandlerFactory(InboundWebsocketSSLConfiguration sslConfiguration) {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        String provider = getPreferredJceProvider();
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        try {
            KeyManagerFactory keyManagerFactory;
            KeyStore keyStore = getKeyStore(sslConfiguration.getKeyStore(), sslConfiguration.getKeyStorePass(),
                    provider);
            if (provider != null) {
                keyManagerFactory = KeyManagerFactory.getInstance(algorithm, BCJSSE);
            } else {
                keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
            }
            keyManagerFactory.init(keyStore, sslConfiguration.getCertPass() != null ?
                    sslConfiguration.getCertPass().toCharArray() :
                    sslConfiguration.getKeyStorePass().toCharArray());
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            TrustManager[] trustManagers = null;
            if (sslConfiguration.getTrustStore() != null) {
                this.needClientAuth = true;
                KeyStore trustStore = getKeyStore(sslConfiguration.getTrustStore(),
                        sslConfiguration.getTrustStorePass(), provider);
                TrustManagerFactory trustManagerFactory;
                if (provider != null) {
                    trustManagerFactory = TrustManagerFactory.getInstance(algorithm, BCJSSE);
                } else {
                    trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
                }
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
            if (provider != null) {
                serverContext = SSLContext.getInstance(protocol, BCJSSE);
            } else {
                serverContext = SSLContext.getInstance(protocol);
            }
            serverContext.init(keyManagers, trustManagers, null);
            cipherSuites = sslConfiguration.getCipherSuites();
            sslProtocols = sslConfiguration.getSslProtocols();
        } catch (UnrecoverableKeyException | KeyManagementException | NoSuchAlgorithmException | KeyStoreException |
                 IOException ex) {
            throw new IllegalArgumentException("Failed to initialize the server side SSLContext", ex);
        } catch (NoSuchProviderException e) {
            throw new IllegalArgumentException("Specified security provider is not available in this environment: ", e);
        }
    }

    private static KeyStore getKeyStore(File keyStore, String keyStorePassword, String provider) throws IOException,
            NoSuchProviderException {
        KeyStore keyStoreInstance;
        try (InputStream is = Files.newInputStream(keyStore.toPath())) {
            if (provider != null) {
                String extension = getFileExtension(keyStore);
                String type = !extension.isEmpty() ? extension.toUpperCase() : "BCFKS";
                keyStoreInstance = KeyStore.getInstance(type, provider);
            } else {
                keyStoreInstance = KeyStore.getInstance("JKS");
            }
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

    private static String getFileExtension(File file) {
        String fileName = file.getName();
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i+1);
        }
        return "";
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
