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

public class MTLSConfiguration {

    private boolean disableCNFValidation;
    private boolean enableClientCertificateValidation;
    private String clientCertificateHeader;
    private boolean clientCertificateEncode;

    public MTLSConfiguration(boolean disableCNFValidation, boolean enableClientCertificateValidation,
                             String clientCertificateHeader, boolean clientCertificateEncode) {
        this.disableCNFValidation = disableCNFValidation;
        this.enableClientCertificateValidation = enableClientCertificateValidation;
        this.clientCertificateHeader = clientCertificateHeader;
        this.clientCertificateEncode = clientCertificateEncode;

    }

    public boolean isCNFValidationEnabled() {

        return !disableCNFValidation;
    }

    public void setDisableCNFValidation(boolean disableCNFValidation) {

        this.disableCNFValidation = disableCNFValidation;
    }

    public boolean isClientCertificateValidationEnabled() {

        return enableClientCertificateValidation;
    }

    public void setEnableClientCertificateValidation(boolean enableClientCertificateValidation) {

        this.enableClientCertificateValidation = enableClientCertificateValidation;
    }

    public String getClientCertificateHeader() {

        return clientCertificateHeader;
    }

    public void setClientCertificateHeader(String clientCertificateHeader) {

        if (clientCertificateHeader != null && !clientCertificateHeader.trim().isEmpty()) {
            this.clientCertificateHeader = clientCertificateHeader;
        } else {
            this.clientCertificateHeader = OAuthConstants.BASE64_ENCODED_CLIENT_CERTIFICATE_HEADER;
        }
    }

    public boolean isClientCertificateEncode() {

        return clientCertificateEncode;
    }

    public void setClientCertificateEncode(boolean clientCertificateEncode) {

        this.clientCertificateEncode = clientCertificateEncode;
    }

}
