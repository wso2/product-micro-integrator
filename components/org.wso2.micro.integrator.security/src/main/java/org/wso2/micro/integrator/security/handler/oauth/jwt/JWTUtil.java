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

package org.wso2.micro.integrator.security.handler.oauth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.synapse.MessageContext;
import org.apache.synapse.endpoints.ProxyConfigs;
import org.apache.synapse.endpoints.auth.AuthException;
import org.apache.synapse.endpoints.auth.oauth.OAuthUtils;
import org.wso2.micro.integrator.security.handler.oauth.HttpClientConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

public class JWTUtil {

    private static final Log log = LogFactory.getLog(JWTUtil.class);

    /**
     * Retrieve the JWKS (JSON Web Key Set) from the provided JWKS endpoint URL.
     *
     * <p>This method creates a secure HTTP client using the supplied
     * {@code httpClientConfiguration}, performs an HTTP GET against {@code jwksEndpoint},
     * and returns the response body as a String when the endpoint responds with HTTP 200 (OK).
     * Any non-200 status code will cause the method to return {@code null}.</p>
     *
     * @param jwksEndpoint the JWKS endpoint URL to fetch (e.g. {@code https://example.com/.well-known/jwks.json})
     * @param httpClientConfiguration configuration containing timeouts and proxy settings used to create the HTTP client
     * @param messageContext optional Synapse {@link MessageContext} used when creating the secure client (may be null)
     * @return the JWKS JSON payload as a String when the response status is 200, or {@code null} for other response statuses
     * @throws IOException when an I/O error occurs while executing the request or reading the response body
     * @throws AuthException when creating or configuring the secure HTTP client fails due to authentication/proxy configuration
     */
     public static String retrieveJWKSConfiguration(String jwksEndpoint, HttpClientConfiguration httpClientConfiguration,
                                                    MessageContext messageContext) throws IOException, AuthException {

         ProxyConfigs proxyConfigs = getProxyConfigs(httpClientConfiguration);
         try (CloseableHttpClient httpClient = OAuthUtils.getSecureClient(jwksEndpoint, messageContext,
                 httpClientConfiguration.getConnectionTimeout(), httpClientConfiguration.getRequestTimeout(),
                 httpClientConfiguration.getSocketTimeout(), proxyConfigs, null)) {
             HttpGet httpGet = new HttpGet(jwksEndpoint);
             try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                 if (response.getStatusLine().getStatusCode() == 200) {
                     HttpEntity entity = response.getEntity();
                     try (InputStream content = entity.getContent()) {
                         return IOUtils.toString(content, StandardCharsets.UTF_8);
                     }
                 } else {
                     return null;
                 }
             }
         }
     }

    private static ProxyConfigs getProxyConfigs(HttpClientConfiguration httpClientConfiguration) {

        ProxyConfigs proxyConfigs = new ProxyConfigs();
        proxyConfigs.setProxyEnabled(httpClientConfiguration.isProxyEnabled());
        proxyConfigs.setProxyHost(httpClientConfiguration.getProxyHost());
        proxyConfigs.setProxyPort(String.valueOf(httpClientConfiguration.getProxyPort()));
        proxyConfigs.setProxyProtocol(httpClientConfiguration.getProxyProtocol());
        proxyConfigs.setProxyUsername(httpClientConfiguration.getProxyUsername());
        proxyConfigs.setProxyPassword(Arrays.toString(httpClientConfiguration.getProxyPassword()));
        return proxyConfigs;
    }

    /**
     * Verify the signature of the provided Signed JWT using the supplied RSA public key.
     *
     * <p>This method supports RSA-based algorithms (RS256, RS384, RS512) and the
     * RSASSA-PSS algorithm PS256. The method constructs an {@link com.nimbusds.jose.JWSVerifier}
     * backed by the provided {@code publicKey} and invokes the JWT verification
     * routine.</p>
     *
     * <p>Behavior notes:
     * <ul>
     *   <li>If the JWT header algorithm is not one of the supported algorithms the method
     *       will log an error and return {@code false}.</li>
     *   <li>If verification fails (for example due to a signature mismatch or
     *       a {@link com.nimbusds.jose.JOSEException}), the method logs the error and returns
     *       {@code false}.</li>
     * </ul>
     * </p>
     *
     * @param jwt the parsed {@link SignedJWT} whose signature should be verified
     * @param publicKey the RSA public key to use for verification
     *                  (must be an instance of {@link java.security.interfaces.RSAPublicKey})
     * @return {@code true} when the signature algorithm is supported and the signature verification
     *         succeeds; {@code false} otherwise
     */
    public static boolean verifyTokenSignature(SignedJWT jwt, RSAPublicKey publicKey) {

        JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
        if ((JWSAlgorithm.RS256.equals(algorithm) || JWSAlgorithm.RS512.equals(algorithm) ||
                JWSAlgorithm.RS384.equals(algorithm)) || JWSAlgorithm.PS256.equals(algorithm)) {
            try {
                JWSVerifier jwsVerifier = new RSASSAVerifier(publicKey);
                return jwt.verify(jwsVerifier);
            } catch (JOSEException e) {
                log.error("Error while verifying JWT signature", e);
                return false;
            }
        } else {
            log.error("Public key is not a RSA");
            return false;
        }
    }
}
