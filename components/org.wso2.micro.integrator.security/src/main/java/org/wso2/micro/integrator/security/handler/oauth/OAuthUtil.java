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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.nhttp.config.TrustStoreHolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import java.util.Map;

public class OAuthUtil {

    private static final Log log = LogFactory.getLog(OAuthUtil.class);


    /**
     * Returns a masked token for a given token.
     *
     * @param token token to be masked
     * @return masked token.
     */
    public static String getMaskedToken(String token) {

        TokenMaskingDataHolder tokenMaskingDataHolder = TokenMaskingDataHolder.getInstance();
        StringBuilder maskedTokenBuilder = new StringBuilder();

        if (token != null) {
            int allowedVisibleLen;
            if (tokenMaskingDataHolder.getTokenMinVisibleLengthRatio() > 0) {
                allowedVisibleLen = tokenMaskingDataHolder.getTokenMaxVisibleLength();
            } else {
                allowedVisibleLen = Math.min(token.length() / tokenMaskingDataHolder.getTokenMinVisibleLengthRatio(),
                        tokenMaskingDataHolder.getTokenMaxVisibleLength());
            }

            if (token.length() > tokenMaskingDataHolder.getTokenMaxLength()) {
                maskedTokenBuilder.append("...");
                maskedTokenBuilder.append(String.join("",
                        Collections.nCopies(tokenMaskingDataHolder.getTokenMaxLength(),
                                tokenMaskingDataHolder.getTokenMaskChar())));
            } else {
                maskedTokenBuilder.append(String.join("",
                        Collections.nCopies(token.length()
                                - allowedVisibleLen, tokenMaskingDataHolder.getTokenMaskChar())));
            }
            maskedTokenBuilder.append(token.substring(token.length() - allowedVisibleLen));
        }
        return maskedTokenBuilder.toString();
    }

    /**
     * Validate Certificate exist in TrustStore
     *
     * @param certificate
     * @return true if certificate exist in truststore
     * @throws OAuthSecurityException
     */
    public static boolean isCertificateExistsInListenerTrustStore(Certificate certificate)
            throws OAuthSecurityException {

        if (certificate != null) {
            try {
                KeyStore trustStore = TrustStoreHolder.getInstance().getClientTrustStore();
                if (trustStore != null) {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    byte[] certificateEncoded = certificate.getEncoded();
                    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(certificateEncoded)) {
                        java.security.cert.X509Certificate x509Certificate =
                                (java.security.cert.X509Certificate) cf.generateCertificate(byteArrayInputStream);
                        String certificateAlias = trustStore.getCertificateAlias(x509Certificate);
                        if (certificateAlias != null) {
                            return true;
                        }
                    }
                }
            } catch (KeyStoreException | CertificateException | IOException e) {
                String msg = "Error in validating certificate existence";
                log.error(msg, e);
                throw new OAuthSecurityException(msg, e);
            }
        }
        return false;
    }

    public static Certificate getCertificateFromParentTrustStore(String certAlias) throws OAuthSecurityException {

        Certificate publicCert = null;
        //Read the client-truststore.jks into a KeyStore
        try {
            KeyStore trustStore = TrustStoreHolder.getInstance().getClientTrustStore();
            if (trustStore != null) {
                // Read public certificate from trust store
                publicCert = trustStore.getCertificate(certAlias);
            }
        } catch (KeyStoreException e) {
            String msg = "Error in retrieving public certificate from the trust store with alias : "
                    + certAlias;
            log.error(msg, e);
            throw new OAuthSecurityException(msg, e);
        }
        return publicCert;
    }

    public static Certificate getClientCertificateFromHeader(
            org.apache.axis2.context.MessageContext axis2MessageContext, String clientCertificateHeader,
            boolean isEncoded) throws OAuthSecurityException {

        Map headers =
                (Map) axis2MessageContext.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);

        String certificate = (String) headers.get(clientCertificateHeader);
        byte[] bytes;
        if (certificate != null) {
            if (isEncoded) {
                try {
                    certificate = URLDecoder.decode(certificate, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    String msg = "Error while URL decoding certificate";
                    throw new OAuthSecurityException(msg, e);
                }
                certificate = getX509certificateContent(certificate);
                bytes = Base64.decodeBase64(certificate);
            } else {
                bytes = certificate.getBytes();
            }

            try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                return cf.generateCertificate(inputStream);
            } catch (IOException | CertificateException e) {
                String msg = "Error while converting into X509Certificate";
                throw new OAuthSecurityException(msg, e);
            }
        }

        return null;
    }

    public static String getX509certificateContent(String certificate) {
        String content = certificate.replaceAll(OAuthConstants.BEGIN_CERTIFICATE_STRING, "")
                .replaceAll(OAuthConstants.END_CERTIFICATE_STRING, "");

        return content.trim();
    }

    public static Certificate getClientCertificate(org.apache.axis2.context.MessageContext axis2MessageContext,
                                                   MTLSConfiguration mtlsConfiguration)
            throws OAuthSecurityException {

        Certificate[] certs = getClientCertificatesChain(axis2MessageContext, mtlsConfiguration);
        return (certs != null && certs.length > 0) ? certs[0] : null;
    }

    /**
     * Fetches client certificate chain from axis2MessageContext.
     * @param axis2MessageContext   Relevant axis2MessageContext
     * @return                      Array containing client certificate chain
     * @throws OAuthSecurityException when an error occurs while fetching the client certificate chain
     */
    public static Certificate[] getClientCertificatesChain(
            org.apache.axis2.context.MessageContext axis2MessageContext, MTLSConfiguration mtlsConfiguration)
            throws OAuthSecurityException {

        Certificate[] certs = null;
        Object sslCertObject = axis2MessageContext.getProperty(NhttpConstants.SSL_CLIENT_AUTH_CERT);
        Certificate certificateFromMessageContext = null;

        if (sslCertObject instanceof Certificate[] && ((Certificate[]) sslCertObject).length > 0) {
            certs = (Certificate[]) sslCertObject;
            certificateFromMessageContext = certs[0];
        } else if (sslCertObject instanceof Certificate[]) {
            certs = (Certificate[]) sslCertObject;
        }

        Map headers = (Map) axis2MessageContext.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        if (headers != null && headers.containsKey(mtlsConfiguration.getClientCertificateHeader())) {
            try {
                Certificate headerCertificate = getClientCertificateFromHeader(
                        axis2MessageContext,
                        mtlsConfiguration.getClientCertificateHeader(),
                        mtlsConfiguration.isClientCertificateEncode());
                if (headerCertificate == null) {
                    return certs;
                }

                if (mtlsConfiguration.isClientCertificateValidationEnabled()) {
                    if (certificateFromMessageContext == null ||
                            !headerCertificate.equals(certificateFromMessageContext) ||
                            !OAuthUtil.isCertificateExistsInListenerTrustStore(headerCertificate)) {
                        throw new OAuthSecurityException(
                                "Client certificate header does not match a trusted transport certificate");
                        }
                }
                return new Certificate[] { headerCertificate };
            } catch (OAuthSecurityException e) {
                String msg = "Error while validating into Certificate Existence";
                log.error(msg, e);
                throw new OAuthSecurityException(msg, e);
            }
        }
        return certs;
    }
}
