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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InboundWebsocketSSLConfiguration {
    private final File keyStore;
    private final String keyStorePass;
    private String keyStoreType = "JKS";
    private String certPass;
    private File trustStore;
    private String trustStorePass;
    private String trustStoreType = "JKS";
    private String[] sslProtocols;
    private String[] cipherSuites;

    public String[] getSslProtocols() {
        return sslProtocols;
    }

    public void setSslProtocols(String[] sslProtocols) {
        this.sslProtocols = sslProtocols;
    }

    public String[] getCipherSuites() {
        return cipherSuites;
    }

    public void setCipherSuites(String[] cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    public InboundWebsocketSSLConfiguration(File keyStore, String keyStorePass, String keyStoreType) {
        this.keyStore = keyStore;
        this.keyStorePass = keyStorePass;
        this.keyStoreType = keyStoreType;
    }

    public String getCertPass() {
        return certPass;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public InboundWebsocketSSLConfiguration setCertPass(String certPass) {
        this.certPass = certPass;
        return this;
    }

    public File getTrustStore() {
        return trustStore;
    }

    public InboundWebsocketSSLConfiguration setTrustStore(File trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    public String getTrustStorePass() {
        return trustStorePass;
    }

    public InboundWebsocketSSLConfiguration setTrustStorePass(String trustStorePass) {
        this.trustStorePass = trustStorePass;
        return this;
    }

    public InboundWebsocketSSLConfiguration setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
        return this;
    }

    public File getKeyStore() {
        return keyStore;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }


    public String getKeyStorePass() {
        return keyStorePass;
    }

    public static class SSLConfigurationBuilder {

        private final String keyStoreFile;
        private final String keyStorePass;
        private String keyStoreType = "JKS";
        private final String trustStoreFile;
        private final String trustStorePass;
        private String trustStoreType = "JKS";
        private String certPass;
        private String sslProtocols;
        private String cipherSuites;

        public SSLConfigurationBuilder(String keyStoreFile, String keyStorePass, String trustStoreFile,
                                       String trustStorePass, String certPass, String keyStoreType,
                                       String trustStoreType) {
            this.keyStoreFile = keyStoreFile;
            this.keyStorePass = keyStorePass;
            this.keyStoreType = keyStoreType;
            this.trustStoreFile = trustStoreFile;
            this.trustStorePass = trustStorePass;
            this.trustStoreType = trustStoreType;
            this.certPass = certPass;

        }

        public SSLConfigurationBuilder(String keyStoreFile, String keyStorePass, String trustStoreFile,
                                       String trustStorePass, String certPass, String sslProtocols,
                                       String cipherSuites, String keyStoreType,
                                       String trustStoreType) {
            this.keyStoreFile = keyStoreFile;
            this.keyStorePass = keyStorePass;
            this.keyStoreType = keyStoreType;
            this.trustStoreFile = trustStoreFile;
            this.trustStorePass = trustStoreType;
            this.certPass = certPass;
            this.sslProtocols = sslProtocols;
            this.cipherSuites = cipherSuites;
        }

        public InboundWebsocketSSLConfiguration build() {
            if (certPass == null) {
                certPass = keyStorePass;
            }
            if (keyStoreFile == null || keyStorePass == null) {
                throw new IllegalArgumentException("keyStoreFile or keyStorePass not defined ");
            }
            if (keyStoreType == null) {
                keyStoreType = "JKS";
            }
            File keyStore = new File(keyStoreFile);
            if (!keyStore.exists()) {
                throw new IllegalArgumentException("KeyStore File " + keyStoreFile + " not found");
            }
            InboundWebsocketSSLConfiguration sslConfig = new InboundWebsocketSSLConfiguration(keyStore, keyStorePass,
                    keyStoreType).setCertPass(certPass);
            if (trustStoreFile != null) {
                File trustStore = new File(trustStoreFile);
                if (!trustStore.exists()) {
                    throw new IllegalArgumentException("trustStore File " + trustStoreFile + " not found");
                }
                if (trustStorePass == null) {
                    throw new IllegalArgumentException("trustStorePass is not defined ");
                }
                if (trustStoreType == null) {
                    trustStoreType = "JKS";
                }
                sslConfig.setTrustStore(trustStore).setTrustStorePass(trustStorePass);
            }

            if (sslProtocols == null || sslProtocols.trim().isEmpty()) {
                sslProtocols = "TLS";
            }

            String[] preferredSSLProtocols = sslProtocols.trim().split(",");
            List<String> protocolList = new ArrayList<>(preferredSSLProtocols.length);
            for (String protocol : preferredSSLProtocols) {
                if (!protocol.trim().isEmpty()) {
                    protocolList.add(protocol.trim());
                }
            }
            preferredSSLProtocols = protocolList.toArray(new String[protocolList.size()]);

            sslConfig.setSslProtocols(preferredSSLProtocols);

            if (cipherSuites != null && cipherSuites.trim().length() != 0) {

                String[] preferredCipherSuites = cipherSuites.trim().split(",");
                List<String> cipherSuiteList = new ArrayList<>(preferredCipherSuites.length);
                for (String cipherSuite : preferredCipherSuites) {
                    if (!cipherSuite.trim().isEmpty()) {
                        cipherSuiteList.add(cipherSuite.trim());
                    }
                }
                preferredCipherSuites = cipherSuiteList.toArray(new String[cipherSuiteList.size()]);

                sslConfig.setCipherSuites(preferredCipherSuites);
            }
            return sslConfig;
        }

    }

}
