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

import com.nimbusds.jose.util.X509CertUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.minidev.json.JSONObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Map;
import java.util.Optional;

/**
 * JWT internal Representation
 */
public class SignedJWTInfo implements Serializable {

    private static final Log log = LogFactory.getLog(SignedJWTInfo.class);
    private static final long serialVersionUID = 1L;

    private String token;
    private transient SignedJWT signedJWT;
    private JWTClaimsSet jwtClaimsSet;
    private ValidationStatus validationStatus = ValidationStatus.NOT_VALIDATED;
    private Certificate clientCertificate; //holder of key certificate cnf
    private String clientCertificateHash; //holder of key certificate cnf

    public enum ValidationStatus {
        NOT_VALIDATED, INVALID, VALID
    }

    public SignedJWTInfo(String token, SignedJWT signedJWT, JWTClaimsSet jwtClaimsSet) {

        this.token = token;
        this.signedJWT = signedJWT;
        this.jwtClaimsSet = jwtClaimsSet;
    }

    public SignedJWTInfo() {

    }

    public SignedJWT getSignedJWT() {

        return signedJWT;
    }

    public void setSignedJWT(SignedJWT signedJWT) {

        this.signedJWT = signedJWT;
    }

    public JWTClaimsSet getJwtClaimsSet() {

        return jwtClaimsSet;
    }

    public void setJwtClaimsSet(JWTClaimsSet jwtClaimsSet) {

        this.jwtClaimsSet = jwtClaimsSet;
    }

    public String getToken() {

        return token;
    }

    public void setToken(String token) {

        this.token = token;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getClientCertificateHash() {

        return clientCertificateHash;
    }

    public Certificate getClientCertificate() {

        return clientCertificate;
    }

    public void setClientCertificate(Certificate clientCertificate) {

        this.clientCertificate = clientCertificate;
        if (clientCertificate != null) {
            Optional<X509Certificate> x509Cert = convert(clientCertificate);
            if (x509Cert.isPresent()) {
                clientCertificateHash = X509CertUtils.computeSHA256Thumbprint(x509Cert.get()).toString();
            } else {
                clientCertificateHash = null;
            }
        } else {
            this.clientCertificateHash = null;
        }
    }

    public String getCertificateThumbprint() throws ParseException {

        if (null != jwtClaimsSet && jwtClaimsSet.getClaim(OAuthConstants.CNF) != null) {
            Map<String, Object> thumbPrintMap = jwtClaimsSet.getJSONObjectClaim(OAuthConstants.CNF);
            JSONObject thumbprintJson = new JSONObject(thumbPrintMap);
            return thumbprintJson.getAsString(OAuthConstants.DIGEST);
        }
        return null;
    }

    /**
     * Convert javax.security.cert.X509Certificate to java.security.cert.X509Certificate
     *
     * @param cert the certificate to be converted
     * @return java.security.cert.X509Certificate type certificate
     */
    public static Optional<X509Certificate> convert(Certificate cert) {

        if (cert != null) {
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cert.getEncoded())) {

                java.security.cert.CertificateFactory certificateFactory
                        = java.security.cert.CertificateFactory.getInstance("X.509");
                return Optional.of((java.security.cert.X509Certificate) certificateFactory.generateCertificate(
                        byteArrayInputStream));
            } catch (java.security.cert.CertificateException e) {
                log.error("Error while generating the certificate", e);
            } catch (IOException e) {
                log.error("Error while retrieving the encoded certificate", e);
            }
        }
        return Optional.ofNullable(null);
    }
}
