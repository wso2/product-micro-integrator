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
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.util.DateUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.security.handler.oauth.CacheProvider;
import org.wso2.micro.integrator.security.handler.oauth.HttpClientConfiguration;
import org.wso2.micro.integrator.security.handler.oauth.MTLSConfiguration;
import org.wso2.micro.integrator.security.handler.oauth.OAuthConstants;
import org.wso2.micro.integrator.security.handler.oauth.OAuthSecurityException;
import org.wso2.micro.integrator.security.handler.oauth.OAuthUtil;
import org.wso2.micro.integrator.security.handler.oauth.SignedJWTInfo;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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
     public static String fetchRemoteJwksJson(String jwksEndpoint, HttpClientConfiguration httpClientConfiguration,
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
        proxyConfigs.setProxyPassword(new String(httpClientConfiguration.getProxyPassword()));
        return proxyConfigs;
    }

    /**
     * Verify the signature of the provided Signed JWT using the supplied RSA public key.
     *
     * <p>This method supports RSA-based signature algorithms (RS256, RS384, RS512) as well
     * as RSASSA-PSS algorithms (PS256, PS384, PS512). It constructs an appropriate
     * {@link com.nimbusds.jose.JWSVerifier} (an {@link com.nimbusds.jose.crypto.RSASSAVerifier})
     * backed by the provided {@code publicKey} and asks the Nimbus JWT implementation to verify
     * the signature.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If the JWT header's alg is not one of the supported algorithms this method will
     *       log an error and return {@code false}.</li>
     *   <li>If the signature verification step throws a {@link com.nimbusds.jose.JOSEException}
     *       (for example due to a malformed signature), this method will log the error and
     *       return {@code false}.</li>
     *   <li>On successful verification it returns {@code true}.</li>
     * </ul>
     *
     * @param jwt the parsed {@link SignedJWT} whose signature should be verified (must not be null)
     * @param publicKey the RSA public key to use for verification
     *                  (must be an instance of {@link java.security.interfaces.RSAPublicKey})
     * @return {@code true} when the signature algorithm is supported and the signature verification succeeds;
     *         {@code false} otherwise
     */
    public static boolean verifyTokenSignature(SignedJWT jwt, RSAPublicKey publicKey) {

        JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
        if (JWSAlgorithm.RS256.equals(algorithm) || JWSAlgorithm.RS512.equals(algorithm)
                || JWSAlgorithm.RS384.equals(algorithm) || JWSAlgorithm.PS256.equals(algorithm)
                || JWSAlgorithm.PS384.equals(algorithm) || JWSAlgorithm.PS512.equals(algorithm)) {
            try {
                JWSVerifier jwsVerifier = new RSASSAVerifier(publicKey);
                return jwt.verify(jwsVerifier);
            } catch (JOSEException e) {
                log.error("Error while verifying JWT signature", e);
                return false;
            }
        } else {
            log.error("Unsupported JWT signature algorithm: " + algorithm);
            return false;
        }
    }

    /**
     * Verify the JWT token signature using a public certificate alias from the parent truststore.
     *
     * <p>This convenience overload resolves the public certificate specified by {@code alias}
     * from the parent truststore, extracts the public key, and then delegates to
     * {@link #verifyTokenSignature(SignedJWT, RSAPublicKey)}.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If the certificate with the given alias is not found an {@link OAuthSecurityException}
     *       is thrown.</li>
     *   <li>If the certificate's public key is not an RSA key or not a RSASSA-PSS key,
     *       an {@link OAuthSecurityException} is thrown.</li>
     *   <li>On successful verification this method returns {@code true}.</li>
     * </ul>
     *
     * @param jwt the parsed {@link SignedJWT} whose signature should be verified
     * @param alias the keystore alias name under which the public certificate is stored in the parent truststore
     * @return {@code true} when verification succeeds using the certificate identified by {@code alias}
     * @throws OAuthSecurityException when the certificate cannot be retrieved or when the public key is not RSA
     */
    public static boolean verifyTokenSignature(SignedJWT jwt, String alias) throws OAuthSecurityException {

        Certificate publicCert;
        //Read the client-truststore.jks into a KeyStore
        try {
            publicCert = OAuthUtil.getCertificateFromParentTrustStore(alias);
        } catch (OAuthSecurityException e) {
            throw new OAuthSecurityException("Error retrieving certificate from truststore ",e);
        }

        if (publicCert != null) {
            JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
            if (JWSAlgorithm.RS256.equals(algorithm) || JWSAlgorithm.RS512.equals(algorithm) ||
                    JWSAlgorithm.RS384.equals(algorithm) || JWSAlgorithm.PS256.equals(algorithm)
                    || JWSAlgorithm.PS384.equals(algorithm) || JWSAlgorithm.PS512.equals(algorithm)) {
                if (!(publicCert.getPublicKey() instanceof RSAPublicKey)) {
                    throw new OAuthSecurityException("Public key is not RSA");
                }
                return verifyTokenSignature(jwt, (RSAPublicKey) publicCert.getPublicKey());
            } else {
                log.error("Unsupported JWT signature algorithm: " + algorithm);
                throw new OAuthSecurityException("Unsupported JWT signature algorithm: " + algorithm);
            }
        } else {
            log.error("Couldn't find a public certificate with alias " + alias + " to verify the signature");
            throw new OAuthSecurityException(
                    "Couldn't find a public certificate with alias " + alias + " to verify the signature");
        }
    }

    public static long getTimeStampSkewInSeconds() {

        Object clockSkewSeconds = ConfigParser.getParsedConfigs().get(OAuthConstants.CLOCK_SKEW_SECONDS);
        if (clockSkewSeconds instanceof Number) {
            return ((Number) clockSkewSeconds).longValue();
        } else if (clockSkewSeconds != null) {
            log.warn("Invalid clock skew configuration value: " + clockSkewSeconds
                    + ". Falling back to default: " + OAuthConstants.DEFAULT_TIMESTAMP_SKEW_IN_SECONDS + " seconds.");
        }
        return OAuthConstants.DEFAULT_TIMESTAMP_SKEW_IN_SECONDS;
    }

    /**
     * Extract and normalize scopes from a JWT claims set.
     *
     * <p>This helper reads the scope claim from the provided {@link com.nimbusds.jwt.JWTClaimsSet}
     * and returns a list of individual scope values in canonical form.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If the scope claim is represented as a single {@link String}, the string is split using the
     *       delimiter defined by {@link org.wso2.micro.integrator.security.handler.oauth.OAuthConstants#SCOPE_DELIMITER}
     *       and the resulting tokens are returned as a {@link java.util.List}.</li>
     *   <li>If the scope claim is represented as a JSON array (mapped to a {@link java.util.List} by Nimbus),
     *       the method returns that list via {@link com.nimbusds.jwt.JWTClaimsSet#getStringListClaim(String)}.</li>
     *   <li>If the scope claim is missing or cannot be parsed, the method returns a single-element list containing
     *       {@link org.wso2.micro.integrator.security.handler.oauth.OAuthConstants#OAUTH2_DEFAULT_SCOPE}.</li>
     * </ul>
     *
     * <p>Notes:
     * <ul>
     *   <li>The method translates underlying {@link java.text.ParseException}
     *       into {@link org.wso2.micro.integrator.security.handler.oauth.OAuthSecurityException}
     *       to provide a unified error type for callers.</li>
     *   <li>The returned list is safe for downstream scope checks; the method does not mutate the incoming
     *       {@link com.nimbusds.jwt.JWTClaimsSet}.</li>
     * </ul>
     *
     * @param jwtClaimsSet the parsed JWT claims set from which to extract scopes
     * @return a {@link java.util.List} of scope strings extracted from the token; if no scopes are present or
     *         parsing fails, returns a list containing
     *         {@link org.wso2.micro.integrator.security.handler.oauth.OAuthConstants#OAUTH2_DEFAULT_SCOPE}
     * @throws org.wso2.micro.integrator.security.handler.oauth.OAuthSecurityException if an error occurs while parsing
     *         claims
     */
    public static List<String> getTransformedScopes(JWTClaimsSet jwtClaimsSet) throws OAuthSecurityException {

        try {
            String scopeClaim = OAuthConstants.SCOPE;
            if (jwtClaimsSet.getClaim(scopeClaim) instanceof String) {
                return Arrays.asList(jwtClaimsSet.getStringClaim(scopeClaim)
                        .split(OAuthConstants.SCOPE_DELIMITER));
            } else if (jwtClaimsSet.getClaim(scopeClaim) instanceof List) {
                return jwtClaimsSet.getStringListClaim(scopeClaim);
            }
        } catch (ParseException e) {
            throw new OAuthSecurityException("Error while parsing JWT claims", e);
        }
        return List.of(OAuthConstants.OAUTH2_DEFAULT_SCOPE);
    }

    /**
     * Returns an identifier for the provided Signed JWT.
     *
     * <p>The method first attempts to read the standard JWT ID ("jti") claim from the
     * JWT claims set. If the "jti" claim is present and not empty, it is returned.
     * Otherwise the method falls back to returning the JWT signature string to
     * uniquely identify the token.</p>
     *
     * @param signedJWTInfo SignedJWTInfo containing the parsed SignedJWT and claims
     * @return the JWT identifier (the "jti" claim if present and non-empty, otherwise
     *         the JWT signature string)
     */
    public static String getJWTTokenIdentifier(SignedJWTInfo signedJWTInfo) {

        JWTClaimsSet jwtClaimsSet = signedJWTInfo.getJwtClaimsSet();
        if (jwtClaimsSet != null) {
            String issuer = jwtClaimsSet.getIssuer();
            String jti = jwtClaimsSet.getJWTID();
            if (StringUtils.isNotEmpty(jti)) {
                return issuer + "_" + jti;
            }
        }
        return signedJWTInfo.getSignedJWT().getSignature().toString();
    }

    /**
     * Attempt to retrieve a cached {@link JWTValidationInfo} for the token identified by {@code jti}.
     *
     * <p>Behavioral summary:
     * <ul>
     *   <li>If the supplied {@link SignedJWTInfo} has a parsing status of {@link SignedJWTInfo.ValidationStatus#VALID}
     *       and a token validation entry exists in the token cache for {@code jti}, the cached entry is inspected
     *     for freshness and payload equality.</li>
     *   <li>If the cached entry is expired, this method will mark the cached entry as expired/invalid,
     *       remove it from the token cache, add the {@code jti} to the invalid-token cache, and return the
     *       (now-expired) {@link JWTValidationInfo}.</li>
     *   <li>If the cached entry's raw payload differs from the currently presented token, the method will
     *       return a newly created {@link JWTValidationInfo} marked invalid (validation code
     *       {@link OAuthConstants#API_AUTH_INVALID_CREDENTIALS}). Note: in this case the token caches are
     *       not mutated by this method.</li>
     *   <li>If certificate-binding (CNF) validation fails for the cached entry, the method will return a newly
     *       created {@link JWTValidationInfo} marked invalid (validation code
     *       {@link OAuthConstants#API_AUTH_INVALID_CREDENTIALS}) without mutating the caches. This path treats
     *       the token as structurally valid but not bound to the expected client certificate.</li>
     *   <li>If no cached entry exists but the invalid-token cache contains {@code jti}, the method will return a
     *       new {@link JWTValidationInfo} marked invalid (validation code
     *       {@link OAuthConstants#API_AUTH_INVALID_CREDENTIALS}).</li>
     *   <li>When a valid, non-expired cached entry is present, and both the raw payload and CNF checks pass,
     *       the method returns the cached {@link JWTValidationInfo} (fast-path to skip full validation).</li>
     * </ul>
     *
     * <p>Side effects:
     * <ul>
     *   <li>Expired cached entries are removed from the token cache and the {@code jti} is recorded in the
     *       invalid-token cache.</li>
     *   <li>Other failure paths (raw payload mismatch, CNF failure) do NOT mutate the caches; they return an
     *       invalid {@link JWTValidationInfo} to force full validation by the caller.</li>
     * </ul>
     *
     * @param jti the token identifier (typically issuer + "_" + jti claim or a fallback signature-based id)
     * @param signedJWTInfo the parsed token wrapper containing the JWT, claims and parsing validation status
     * @param mtlsConfiguration mTLS configuration used when performing CNF (certificate binding) checks
     * @return a cached {@link JWTValidationInfo} when a valid, matching cache entry exists; a newly created
     *         {@link JWTValidationInfo} marked invalid when the token is known or determined to be invalid; or
     *         {@code null} when no cached information is available and the token has not previously been recorded
     *         as invalid.
     * @throws OAuthSecurityException if a general error occurs while reading or updating cache state (for example
     *         when parsing a certificate thumbprint fails)
     *
     * @implNote This method is intended as a fast-path optimization to avoid repeated full validation for tokens
     * that were already validated. Callers MUST perform full validation when no suitable cached entry is returned.
     * The method mutates the caches only in the expired-entry path; other failure cases intentionally avoid
     * cache mutations so that cache entries remain consistent with external token state.
     */
    public static JWTValidationInfo getJWTValidationInfoFromCache(String jti, SignedJWTInfo signedJWTInfo,
                                                                  MTLSConfiguration mtlsConfiguration)
            throws OAuthSecurityException {

        JWTValidationInfo jwtValidationInfo = null;
        JWTValidationInfo cachedJWTValidationInfo = (JWTValidationInfo) CacheProvider.getTokenCache().get(jti);
        if (SignedJWTInfo.ValidationStatus.VALID.equals(signedJWTInfo.getValidationStatus())
                && cachedJWTValidationInfo != null) {

            boolean isExpired = isTokenExpired(cachedJWTValidationInfo);
            if (isExpired) {
                log.error("JWT token validation failed. Reason: Expired Token. "
                        + OAuthUtil.getMaskedToken(signedJWTInfo.getToken()));
                cachedJWTValidationInfo.setExpired(true);
                cachedJWTValidationInfo.setValid(false);
                cachedJWTValidationInfo.setValidationCode(OAuthConstants.API_AUTH_ACCESS_TOKEN_EXPIRED);
                CacheProvider.getTokenCache().remove(jti);
                CacheProvider.getInvalidTokenCache().put(jti, Boolean.TRUE);
                return cachedJWTValidationInfo;
            }

            //check accessToken
            if (!cachedJWTValidationInfo.getRawPayload().equals(signedJWTInfo.getToken())) {
                log.error("JWT token validation failed. Reason: Invalid Token. "
                        + OAuthUtil.getMaskedToken(signedJWTInfo.getToken()));
                jwtValidationInfo = new JWTValidationInfo();
                jwtValidationInfo.setValidationCode(OAuthConstants.API_AUTH_INVALID_CREDENTIALS);
                jwtValidationInfo.setValid(false);
                return jwtValidationInfo;
            }

            try {
                boolean isValidCertificateBoundAccessToken = validateCNFClaim(signedJWTInfo, mtlsConfiguration);
                if (!isValidCertificateBoundAccessToken) {
                    jwtValidationInfo = new JWTValidationInfo();
                    jwtValidationInfo.setValidationCode(OAuthConstants.API_AUTH_INVALID_CREDENTIALS);
                    jwtValidationInfo.setValid(false);
                    // Here we are not adding the token to the invalid token cache since the token itself is not invalid,
                    // but it is not bound to the correct certificate. The token will be considered invalid during the
                    // validation process and an appropriate error response will be sent back to the client.
                    return jwtValidationInfo;
                }
            } catch (ParseException e) {
                log.error("Error while parsing the certificate thumbprint", e);
                throw new OAuthSecurityException(OAuthConstants.API_AUTH_GENERAL_ERROR,
                        OAuthConstants.API_AUTH_GENERAL_ERROR_MESSAGE, e);
            }
            return cachedJWTValidationInfo;

        } else if (CacheProvider.getInvalidTokenCache().get(jti) != null) {
            if (log.isDebugEnabled()) {
                log.debug("Token retrieved from the invalid token cache.");
            }
            log.error("Invalid JWT token.");

            jwtValidationInfo = new JWTValidationInfo();
            jwtValidationInfo.setValidationCode(OAuthConstants.API_AUTH_INVALID_CREDENTIALS);
            jwtValidationInfo.setValid(false);
        }
        return jwtValidationInfo;
    }

    /**
     * Validate whether a JWT represents a certificate-bound access token (cnf) when
     * Mutual TLS (mTLS) CNF validation is enabled.
     *
     * <p>When {@code mtlsConfiguration.isCNFValidationEnabled()} is true this method
     * verifies that the incoming {@code signedJWTInfo} contains a client certificate,
     * a non-empty client certificate hash and a parsed certificate thumbprint, and that
     * the certificate hash equals the thumbprint embedded in the token. If any of the
     * required values are missing or the values do not match, the method returns
     * {@code false}.</p>
     *
     * <p>If CNF validation is disabled (the common case when mTLS is not required),
     * the method returns {@code true} indicating the token is considered valid with
     * respect to certificate binding.</p>
     *
     * @param signedJWTInfo parsed JWT information containing certificate-related fields
     * @param mtlsConfiguration mTLS configuration holding CNF validation settings
     * @return {@code true} when CNF validation is disabled, or when enabled and the token's
     *         certificate thumbprint matches the client certificate hash; {@code false}
     *         otherwise
     * @throws ParseException included for compatibility with callers that parse certificate
     *                        thumbprints; implementations that perform parsing errors may
     *                        propagate this exception
     */
    public static boolean validateCNFClaim(SignedJWTInfo signedJWTInfo,
                                           MTLSConfiguration mtlsConfiguration)
            throws ParseException {

        if (mtlsConfiguration.isCNFValidationEnabled()) {
            if (signedJWTInfo.getClientCertificate() == null
                    || StringUtils.isEmpty(signedJWTInfo.getClientCertificateHash())
                    || signedJWTInfo.getCertificateThumbprint() == null) {
                return false;
            }
            return signedJWTInfo.getClientCertificateHash().equals(signedJWTInfo.getCertificateThumbprint());
        }
        return true;
    }

    public static boolean isTokenExpired(JWTValidationInfo payload) {

        if (payload.getExpiryTime() == 0) {
            return false; // No expiry claim = treat as non-expiring token
        }

        Date now = new Date();
        Date exp = new Date(payload.getExpiryTime());
        return !DateUtils.isAfter(exp, now, JWTUtil.getTimeStampSkewInSeconds());
    }

    public static boolean validateTokenExpiry(JWTClaimsSet jwtClaimsSet) {

        long timestampSkew = JWTUtil.getTimeStampSkewInSeconds();
        Date now = new Date();
        Date exp = jwtClaimsSet.getExpirationTime();
        return exp == null || DateUtils.isAfter(exp, now, timestampSkew);
    }


    public static void populateValidationInfoFromClaims(JWTValidationInfo jwtValidationInfo, JWTClaimsSet jwtClaimsSet)
            throws ParseException, OAuthSecurityException {

        jwtValidationInfo.setScopes(JWTUtil.getTransformedScopes(jwtClaimsSet));
        jwtValidationInfo.setIssuer(jwtClaimsSet.getIssuer());
        jwtValidationInfo.setValid(true);
        jwtValidationInfo.setClaims(new HashMap<>(jwtClaimsSet.getClaims()));
        if (jwtClaimsSet.getExpirationTime() != null){
            jwtValidationInfo.setExpiryTime(jwtClaimsSet.getExpirationTime().getTime());
        }
        if (jwtClaimsSet.getIssueTime() != null){
            jwtValidationInfo.setIssuedTime(jwtClaimsSet.getIssueTime().getTime());
        }
        jwtValidationInfo.setUser(jwtClaimsSet.getSubject());
        jwtValidationInfo.setJti(jwtClaimsSet.getJWTID());
    }

    /**
     * Fetches the JWKS (JSON Web Key Set) from a remote JWKS endpoint and parses it into a {@link JWKSet}.
     *
     * <p>This is a convenience helper that delegates to {@link #fetchRemoteJwksJson(String, HttpClientConfiguration,
     * MessageContext)} to obtain the raw JWKS JSON payload and then parses it into a {@link JWKSet} instance.
     *
     * <p>Behavior notes:
     * <ul>
     *   <li>If the remote endpoint returns a successful payload (HTTP 200), this method parses the JSON and
     *       returns a {@link JWKSet} representing the keys.</li>
     *   <li>If the remote endpoint does not return a 200 OK (or the payload is empty), the method throws an
     *       {@link OAuthSecurityException} indicating an invalid JWKS endpoint.</li>
     * </ul>
     *
     * <p>Thread-safety: the method is thread-safe as it does not mutate shared state; each invocation creates
     * a new {@link JWKSet} instance when parsing succeeds.
     *
     * @param jwksEndpoint the remote JWKS endpoint URL (for example, {@code https://issuer/.well-known/jwks.json})
     * @param httpClientConfiguration HTTP client configuration used to create the secure client (timeouts, proxy, etc.)
     * @param messageContext optional Synapse {@link MessageContext} used when creating the secure client (may be null)
     * @return a parsed {@link JWKSet} when the remote endpoint returns a valid JWKS JSON payload
     * @throws IOException when an I/O error occurs while executing the HTTP request or reading the response body
     * @throws ParseException when the JWKS JSON payload cannot be parsed into a {@link JWKSet}
     * @throws OAuthSecurityException when the JWKS endpoint returns no usable payload (treated as an invalid endpoint)
     * @throws AuthException when configuring the secure HTTP client fails due to authentication/proxy configuration
     */
    public static JWKSet fetchRemoteJWKS(String jwksEndpoint, HttpClientConfiguration httpClientConfiguration,
                                         MessageContext messageContext)
            throws IOException, ParseException, OAuthSecurityException, AuthException {

        JWKSet jwkSet;
        String jwksInfo = JWTUtil.fetchRemoteJwksJson(jwksEndpoint, httpClientConfiguration, messageContext);
        if (jwksInfo != null) {
            jwkSet = JWKSet.parse(jwksInfo);
            CacheProvider.getJwksCache().put(jwksEndpoint, jwkSet);
        } else {
            throw new OAuthSecurityException("Invalid JWKS endpoint.");
        }
        return jwkSet;
    }

    /**
     * Attempt to retrieve a {@link JWK} from the JWKS cache for a given endpoint and key id.
     *
     * <p>Behavior:
     * <ul>
     *   <li>Looks up a cached {@link JWKSet} using {@code jwksEndpoint} as the cache key.</li>
     *   <li>If a non-null {@link JWKSet} is present, returns the {@link JWK} whose key id matches {@code keyID}
     *       (via {@link JWKSet#getKeyByKeyId(String)}).</li>
     *   <li>If no JWKS is cached for the endpoint or the requested key ID is not present, the method returns
     *       {@code null}.</li>
     * </ul>
     *
     * @param jwksEndpoint the JWKS endpoint URL used as the cache key (for example, {@code https://issuer/.well-known/jwks.json})
     * @param keyID the key identifier ("kid") to lookup within the cached JWKS
     * @return the {@link JWK} with the requested {@code keyID} if present in the cached JWKS; otherwise {@code null}
     */
    public static JWK getJWKFromCache(String jwksEndpoint, String keyID) {
        JWKSet jwkSet = (JWKSet) CacheProvider.getJwksCache().get(jwksEndpoint);

        if (jwkSet != null) {
            return jwkSet.getKeyByKeyId(keyID);
        }

        return null;
    }
}
