/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
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

package org.wso2.micro.integrator.security.handler.oauth;

import java.util.Arrays;
import javax.net.ssl.HostnameVerifier;

public class HttpClientConfiguration {

    private int requestTimeout;
    private int socketTimeout;
    private int connectionTimeout;
    private boolean proxyEnabled;
    private String proxyHost;
    private int proxyPort;
    private String proxyUsername;
    private char[] proxyPassword = new char[]{};
    private String[] nonProxyHosts = new String[]{};
    private String proxyProtocol;
    private HostnameVerifier hostnameVerifier;

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    private HttpClientConfiguration() {
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public char[] getProxyPassword() {
        return Arrays.copyOf(proxyPassword, proxyPassword.length);
    }

    public String[] getNonProxyHosts() {
        return Arrays.copyOf(nonProxyHosts, nonProxyHosts.length);
    }

    public String getProxyProtocol() {
        return proxyProtocol;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Builder class for {@code HttpClientConfiguration}
     */
    public static class Builder {

        private int connectionTimeout;
        private int requestTimeout;
        private int socketTimeout;
        private boolean proxyEnabled;
        private String proxyHost;
        private int proxyPort;
        private String proxyUsername;
        private char[] proxyPassword = new char[]{};
        private String[] nonProxyHosts = new String[]{};
        private String proxyProtocol;
        private HostnameVerifier hostnameVerifier;

        public Builder withConnectionParams(int connectionTimeout, int requestTimeout, int socketTimeout) {
            this.connectionTimeout = connectionTimeout;
            this.requestTimeout = requestTimeout;
            this.socketTimeout = socketTimeout;
            return this;
        }

        public Builder withProxy(String proxyHost, int proxyPort, String proxyUsername, String proxyPassword,
                                 String proxyProtocol) {
            this.proxyEnabled = true;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.proxyUsername = proxyUsername;
            this.proxyPassword = proxyPassword != null ? proxyPassword.toCharArray() : new char[]{};
            this.proxyProtocol = proxyProtocol;
            return this;
        }

        public Builder withHostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        public HttpClientConfiguration build() {
            HttpClientConfiguration configuration = new HttpClientConfiguration();
            configuration.requestTimeout = this.requestTimeout;
            configuration.socketTimeout = this.socketTimeout;
            configuration.connectionTimeout = this.connectionTimeout;
            configuration.proxyEnabled = this.proxyEnabled;
            configuration.proxyHost = this.proxyHost;
            configuration.proxyPort = this.proxyPort;
            configuration.proxyUsername = this.proxyUsername;
            configuration.proxyPassword = this.proxyPassword;
            configuration.proxyProtocol = this.proxyProtocol;
            configuration.nonProxyHosts = this.nonProxyHosts;
            configuration.hostnameVerifier = this.hostnameVerifier;
            return configuration;
        }
    }

}
