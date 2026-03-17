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
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.axis2.Constants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.api.ApiUtils;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.endpoints.auth.AuthException;
import org.apache.synapse.rest.RESTConstants;
import org.wso2.micro.integrator.security.handler.oauth.CacheProvider;
import org.wso2.micro.integrator.security.handler.oauth.HttpClientConfiguration;
import org.wso2.micro.integrator.security.handler.oauth.MTLSConfiguration;
import org.wso2.micro.integrator.security.handler.oauth.OAuthConstants;
import org.wso2.micro.integrator.security.handler.oauth.OAuthSecurityException;
import org.wso2.micro.integrator.security.handler.oauth.OAuthUtil;
import org.wso2.micro.integrator.security.handler.oauth.RevocationCheckException;
import org.wso2.micro.integrator.security.handler.oauth.SignedJWTInfo;
import org.wso2.micro.integrator.security.handler.oauth.TokenRevocationHandler;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class JWTValidator {

    private static final Log log = LogFactory.getLog(JWTValidator.class);

    private final String jwksEndpoint;
    private final ArrayList<String> trustedIssuers;
    private final ArrayList<String> audience;
    private final Long maxIssuedAtAgeSeconds;
    private final TokenRevocationHandler tokenRevocationHandler;
    private final HttpClientConfiguration httpClientConfiguration;
    private final MTLSConfiguration mtlsConfiguration;

    public JWTValidator(String jwksEndpoint, ArrayList<String> trustedIssuers, ArrayList<String> audience,
                        Long maxIssuedAtAgeSeconds, TokenRevocationHandler tokenRevocationHandler,
                        HttpClientConfiguration httpClientConfiguration, MTLSConfiguration mtlsConfiguration) {

        this.jwksEndpoint = jwksEndpoint;
        this.trustedIssuers = trustedIssuers;
        this.audience = audience;
        this.maxIssuedAtAgeSeconds = maxIssuedAtAgeSeconds;
        this.tokenRevocationHandler = tokenRevocationHandler;
        this.httpClientConfiguration = httpClientConfiguration;
        this.mtlsConfiguration = mtlsConfiguration;
    }

    /**
     * Authenticate an incoming request represented by the provided {@link SignedJWTInfo}.
     *
     * <p>This method performs the high-level JWT authentication flow:
     * <ol>
     *   <li>Compute a token identifier ("jti" claim or signature) for cache/revocation checks.</li>
     *   <li>If a {@link TokenRevocationHandler} is configured, invoke it to short-circuit revoked tokens.</li>
     *   <li>Obtain a {@link JWTValidationInfo} via {@link #validateJWT} (which may
     *       use in-memory caches or validate the token signature/expiry).</li>
     *   <li>If the token is valid, validate resource scopes (via {@link #validateScopes}),
     *       store scope and claim information into the message context, and return successfully.</li>
     *   <li>If the token is invalid or an error occurs, an {@link OAuthSecurityException} is thrown
     *       containing an appropriate OAuth error code.</li>
     * </ol>
     * </p>
     *
     * @param signedJWTInfo parsed signed JWT and its claims representing the incoming token (must not be null)
     * @param synCtx the Synapse {@link MessageContext} for the current request (used for scope lookup)
     * @throws OAuthSecurityException if the token is revoked, invalid, expired, or an internal error occurs
     *                                while validating the token
     */
    public void authenticate(SignedJWTInfo signedJWTInfo, MessageContext synCtx)
            throws OAuthSecurityException {

        org.apache.axis2.context.MessageContext axis2MsgContext =
                ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        String httpMethod = (String) axis2MsgContext.getProperty(Constants.Configuration.HTTP_METHOD);
        String jwtTokenIdentifier = JWTUtil.getJWTTokenIdentifier(signedJWTInfo);
        String jwtHeader = signedJWTInfo.getSignedJWT().getHeader().toString();

        if (isTokenRevoked(signedJWTInfo)) {
            if (log.isDebugEnabled()) {
                log.debug("Token retrieved from the revoked jwt token map.");
            }
            log.error("Invalid JWT token.");
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    "Invalid JWT token");
        }

        JWTValidationInfo jwtValidationInfo = validateJWT(signedJWTInfo, jwtTokenIdentifier, synCtx);

        if (jwtValidationInfo.isValid()) {
            // Validate scopes
            String matchingResource = (String) synCtx.getProperty(RESTConstants.SELECTED_RESOURCE);
            if (!validateScopes(matchingResource, httpMethod, synCtx, jwtValidationInfo)) {
                throw new OAuthSecurityException(OAuthConstants.INVALID_SCOPE,
                        OAuthConstants.INVALID_SCOPE_MESSAGE);
            }
            synCtx.setProperty(OAuthConstants.SCOPES, jwtValidationInfo.getScopes().toString());
            synCtx.setProperty(OAuthConstants.JWT_CLAIMS, jwtValidationInfo.getClaims());

            if (log.isDebugEnabled()) {
                log.debug("JWT authentication successful.");
            }

        } else {
            throw new OAuthSecurityException(jwtValidationInfo.getValidationCode(),
                    OAuthConstants.getAuthenticationFailureMessage(jwtValidationInfo.getValidationCode()));
        }
    }

    public boolean isTokenRevoked(SignedJWTInfo signedJWTInfo) throws OAuthSecurityException {

        if (tokenRevocationHandler != null) {
            try {
                return tokenRevocationHandler.isRevoked(signedJWTInfo.getToken(),
                        signedJWTInfo.getJwtClaimsSet().getClaims());
            } catch (RevocationCheckException e) {
                log.error("Error while checking token revocation status for token.", e);
                throw new OAuthSecurityException("Error while checking token revocation status", e);
            } catch (RuntimeException e) {
                log.error("Unexpected error while checking token revocation status for token.", e);
                throw new OAuthSecurityException("Error while checking token revocation status", e);
            }
        }
        return false;
    }

    /**
     * Obtain a {@link JWTValidationInfo} for a token identified by {@code jti}.
     *
     * <p>This method first attempts to return a cached validation result if available:
     * <ul>
     *   <li>If the provided {@code signedJWTInfo} already has validation status {@code VALID}
     *       and an entry exists in the token key cache, the cached {@link JWTValidationInfo}
     *       is returned after calling {@link JWTUtil#isTokenExpired} to ensure it is not expired.</li>
     *   <li>If the token is present in the invalid-token cache, a {@link JWTValidationInfo}
     *       with {@code valid=false} and {@link OAuthConstants#API_AUTH_INVALID_CREDENTIALS}
     *       as the validation code is returned.</li>
     * </ul>
     * If no cached result exists, the method performs a full validation by invoking
     * {@link #validateToken}, updates the {@code signedJWTInfo} validation status, and
     * populates the appropriate caches based on the validation outcome.</p>
     *
     * <p>Side effects:
     * <ul>
     *   <li>May update cache entries via {@link CacheProvider} (token/key/invalid-token caches).</li>
     *   <li>Sets {@link SignedJWTInfo} validation status to VALID or INVALID depending on the result.</li>
     * </ul>
     * </p>
     *
     * @param signedJWTInfo parsed signed JWT and claims for which validation info is requested (must not be null)
     * @param jti token identifier (typically the JWT "jti" claim or a signature-derived id)
     * @param messageContext Synapse {@link MessageContext} used by deeper validation steps (may be null)
     * @return a populated {@link JWTValidationInfo} describing whether the token is valid and containing extracted claims and scopes
     * @throws OAuthSecurityException if an error occurs while performing full token validation (for example parsing/JWKS retrieval/signature verification errors)
     */
     private JWTValidationInfo validateJWT(SignedJWTInfo signedJWTInfo, String jti,
                                           MessageContext messageContext) throws OAuthSecurityException {

         // Check for CNF validation
         if (mtlsConfiguration.isCNFValidationEnabled()) {
             validateCertificateBinding(signedJWTInfo, messageContext);
         }

         JWTValidationInfo jwtValidationInfo = null;
         // The validation status can be set to NOT_VALIDATED from the previous step where the certificate binding
         // is validated. If the validation status is NOT_VALIDATED, it means the JWT token needs to be validated
         // again as the certificate binding validation can change the validation status of the signedJWTInfo object.
         // Hence, the token cache will not be checked in such a scenario.
         if (!SignedJWTInfo.ValidationStatus.NOT_VALIDATED.equals(signedJWTInfo.getValidationStatus())) {
             // The "KEY_CACHE" contains traces of the already validated access tokens. Check the token cache to find
             // whether the token is already validated. If the token is present in the cache and the validation status
             // is valid, we can directly return from the cache without validating the signature again. If the token is
             // present in the invalid token cache, we can directly return without validating the signature.
             jwtValidationInfo = JWTUtil.getJWTValidationInfoFromCache(jti, signedJWTInfo, mtlsConfiguration);
         }

         if (jwtValidationInfo == null) {
             // No cached validation info exists, perform full validation including signature validation, expiry check,
             // and CNF validation.
             jwtValidationInfo = validateToken(signedJWTInfo, messageContext);

             if (jwtValidationInfo.isValid()) {
                 signedJWTInfo.setValidationStatus(SignedJWTInfo.ValidationStatus.VALID);
                 CacheProvider.getTokenCache().put(jti, jwtValidationInfo);
             } else {
                 signedJWTInfo.setValidationStatus(SignedJWTInfo.ValidationStatus.INVALID);
                 if (!jwtValidationInfo.isCnfFailed()) {
                     CacheProvider.getInvalidTokenCache().put(jti, Boolean.TRUE);
                 }
             }
         }
         return jwtValidationInfo;
     }

    /**
     * Validate the provided Signed JWT and produce a {@link JWTValidationInfo} describing
     * the validation result and extracted token details.
     *
     * <p>Validation performed by this method:
     * <ul>
     *   <li>Verify the JWT signature (via {@link #validateSignature}).</li>
     *   <li>Check token expiry against the current time allowing for configured timestamp skew
     *       (via {@link JWTUtil#validateTokenExpiry}).</li>
     *   <li>If validation succeeds, populate scopes (via {@link JWTUtil#getTransformedScopes}), issuer,
     *       expiry/issue times, subject and jti into the returned {@link JWTValidationInfo} and
     *       set the raw token payload.</li>
     * </ul>
     * </p>
     *
     * <p>If validation fails due to an invalid signature or an expired token the returned
     * {@link JWTValidationInfo} will have {@code valid=false} and its {@code validationCode}
     * will be set to {@link OAuthConstants#API_AUTH_INVALID_CREDENTIALS}.</p>
     *
     * @param signedJWTInfo  parsed signed JWT and claims to validate (must not be null)
     * @param messageContext Synapse {@link MessageContext} used for JWKS retrieval and
     *                       other context (may be null depending on caller behaviour)
     * @return a {@link JWTValidationInfo} populated with validation outcome and token details
     * @throws OAuthSecurityException if an error occurs while parsing the JWT, retrieving
     *                                JWKS information, or verifying the signature. Underlying
     *                                causes (for example {@link ParseException},
     *                                {@link JOSEException}, {@link IOException})
     *                                are wrapped in this exception.
     */
    private JWTValidationInfo validateToken(SignedJWTInfo signedJWTInfo, MessageContext messageContext)
            throws OAuthSecurityException {

        JWTValidationInfo jwtValidationInfo = new JWTValidationInfo();
        boolean state;
        try {
            // 1. First we need to validate the signature of the JWT token. If the signature validation is failed,
            // we can directly return without validating the claims payload.
            state = validateSignature(signedJWTInfo.getSignedJWT(), messageContext);
            if (!state) {
                log.error("JWT signature validation failed.");
                jwtValidationInfo.setValid(false);
                jwtValidationInfo.setValidationCode(OAuthConstants.API_AUTH_INVALID_CREDENTIALS);
                return jwtValidationInfo;
            }

            // 2. Validate the claims payload of the token after validating the signature of the token.
            state = validateClaimsPayload(signedJWTInfo, jwtValidationInfo);
            if (state) {
                JWTUtil.populateValidationInfoFromClaims(jwtValidationInfo, signedJWTInfo.getJwtClaimsSet());
                jwtValidationInfo.setRawPayload(signedJWTInfo.getToken());
            } else {
                jwtValidationInfo.setValid(false);
                jwtValidationInfo.setValidationCode(OAuthConstants.API_AUTH_INVALID_CREDENTIALS);
            }
            return jwtValidationInfo;
        } catch (ParseException e) {
            throw new OAuthSecurityException("Error while parsing JWT Token", e);
        }
    }

    /**
     * Validates the logical claims (exp, cnf, iat) of the JWT.
     *
     * @param signedJWTInfo   The parsed JWT and its claims.
     * @param jwtValidationInfo Object to capture specific validation failure details.
     * @return true if all security claims are valid.
     * @throws OAuthSecurityException if a terminal security policy is violated.
     */
    public boolean validateClaimsPayload(SignedJWTInfo signedJWTInfo, JWTValidationInfo jwtValidationInfo)
            throws OAuthSecurityException {

        JWTClaimsSet jwtClaimsSet = signedJWTInfo.getJwtClaimsSet();

        if (!isIssuerTrusted(signedJWTInfo)) {
            log.error("The token issuer is not in the list of trusted issuers.");
            return false;
        }

        if (!validateAudience(jwtClaimsSet, audience)) {
            log.error("The token audience does not match the expected audience.");
            return false;
        }

        // 1. Expiry Check (Fastest & most common failure)
        if (!JWTUtil.validateTokenExpiry(jwtClaimsSet)) {
            log.error("JWT token is expired.");
            jwtValidationInfo.setExpired(true);
            return false;
        }

        // 2. CNF (Confirmation) Claim Check (For mTLS/Sender-Constrained tokens)
        try {
            if (!JWTUtil.validateCNFClaim(signedJWTInfo, mtlsConfiguration)) {
                log.error("JWT token CNF claim validation failed.");
                jwtValidationInfo.setCnfFailed(true);
                return false;
            }
        } catch (ParseException e) {
            log.error("Error while parsing 'cnf' claim in JWT Token", e);
            return false;
        }

        // 3. Issued At (iat) Policy Check (Replay & Clock Skew)
        if (!validateIssuedAtPolicy(jwtClaimsSet, JWTUtil.getTimeStampSkewInSeconds(), maxIssuedAtAgeSeconds)) {
            log.error("JWT iat policy validation failed.");
            return false;
        }

        return true; // Everything passed
    }

    /**
     * Validates that the 'aud' (Audience) claim contains this Resource Server's identifier.
     *
     * @param jwtClaimsSet   The claims extracted from the token.
     * @param expectedAudience The unique identifier for your API (e.g., "<a href="https://api.myapp.com">...</a>").
     * @return true if the expected audience is present in the token's 'aud' claim.
     */
    public boolean validateAudience(JWTClaimsSet jwtClaimsSet, ArrayList<String> expectedAudience) {

        if (expectedAudience == null || expectedAudience.isEmpty()
                || expectedAudience.contains(OAuthConstants.ALL_AUDIENCES)) {
            return true;
        }

        List<String> tokenAudiences = jwtClaimsSet.getAudience();

        // 1. Presence Check
        if (tokenAudiences == null || tokenAudiences.isEmpty()) {
            log.error("Missing mandatory 'aud' (Audience) claim.");
            return false;
        }

        // 2. Membership Check
        // Per RFC 9068, the RS MUST verify that it is identified by one of the audiences.
        for (String aud : expectedAudience) {
            if (tokenAudiences.contains(aud)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate the cryptographic signature of a parsed {@link SignedJWT}.
     *
     * <p>High-level behavior:
     * <ol>
     *   <li>Extracts the Key ID ("kid") from the JWT header. If no kid is present, the method
     *       returns {@code false} (signature cannot be validated without a key identifier).</li>
     *   <li>If a JWKS endpoint was configured for the validator (the {@code jwksEndpoint} field):
     *     <ul>
     *       <li>Try to retrieve a cached {@link com.nimbusds.jose.jwk.JWK} for the endpoint/key id via
     *           {@link JWTUtil#getJWKFromCache}.</li>
     *       <li>If none is cached, fetch the JWKS from the remote endpoint using
     *           {@link JWTUtil#fetchRemoteJWKS} (this may populate the JWKS cache) and then look up the key.</li>
     *       <li>If the located key is an RSA key the method extracts an {@link RSAPublicKey} and delegates to
     *           {@link JWTUtil#verifyTokenSignature(SignedJWT, RSAPublicKey)} for signature verification.</li>
     *       <li>If the key is absent or the key type is not supported the method returns {@code false}.
     *     </ul>
     *   </li>
     *   <li>If no JWKS endpoint is configured, the method delegates to
     *       {@link JWTUtil#verifyTokenSignature(SignedJWT, String)} which resolves a public key by alias
     *       (the method passes the kid as the alias in this mode) and verifies the signature.</li>
     * </ol>
     *
     * <p>Return and error behavior:
     * <ul>
     *   <li>Returns {@code true} when a supported key is found and the signature verification succeeds.</li>
     *   <li>Returns {@code false} when verification cannot be performed (missing kid, key not found, unsupported key type,
     *       signature algorithm mismatch or verification failure) — callers should treat {@code false} as an
     *       authentication failure.</li>
     *   <li>Throws {@link OAuthSecurityException} when an unexpected error occurs while retrieving or parsing remote
     *       JWKS or during network/proxy/certificate operations; such errors are logged and wrapped to provide a
     *       consistent exception type to callers.</li>
     * </ul>
     *
     * <p>Side-effects:
     * <ul>
     *   <li>May call {@link JWTUtil#fetchRemoteJWKS} which will fetch and parse a remote JWKS and populate the
     *       JWKS cache via {@link org.wso2.micro.integrator.security.handler.oauth.CacheProvider}.</li>
     *   <li>The method itself does not mutate the incoming {@link SignedJWT}.</li>
     * </ul>
     *
     * <p>Thread-safety: the method is stateless and safe to call concurrently; any cache or network operations
     * rely on the thread-safety of underlying utilities and the configured {@link CacheProvider}.
     *
     * @param signedJWT the parsed JWT whose signature should be validated (must not be null)
     * @param messageContext optional Synapse {@link MessageContext} used for JWKS fetch (may be null)
     * @return {@code true} if the signature was successfully verified against a supported key; {@code false} otherwise
     * @throws OAuthSecurityException if an error occurs while retrieving or parsing JWKS or when network/auth
     *                                errors occur during JWKS retrieval
     */
    protected boolean validateSignature(SignedJWT signedJWT, MessageContext messageContext)
            throws OAuthSecurityException {

        try {
            String keyID = signedJWT.getHeader().getKeyID();
            if (StringUtils.isEmpty(keyID)) {
                if (log.isDebugEnabled()) {
                    log.debug("Key ID is not present in the JWT header");
                }
                return false;
            }
            if (jwksEndpoint != null) {
                // 1. Get JWKSet from Cache
                JWK key = JWTUtil.getJWKFromCache(jwksEndpoint, keyID);

                if (key == null) {
                    JWKSet publicKeys = JWTUtil.fetchRemoteJWKS(jwksEndpoint, httpClientConfiguration, messageContext);
                    key = publicKeys.getKeyByKeyId(keyID);
                }

                if (key == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Key with ID " + keyID + " not found in JWKS endpoint");
                    }
                    return false;
                } else if (key instanceof RSAKey keyByKeyId) {
                    RSAPublicKey rsaPublicKey = keyByKeyId.toRSAPublicKey();
                    if (rsaPublicKey != null) {
                        return JWTUtil.verifyTokenSignature(signedJWT, rsaPublicKey);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Key Algorithm not supported");
                    }
                    return false; // return false to produce 401 unauthenticated response
                }
            } else {
                return JWTUtil.verifyTokenSignature(signedJWT, keyID);
            }
            return false;
        } catch (ParseException e) {
            log.error("Error while parsing JWKS information", e);
            throw new OAuthSecurityException("Error while parsing JWT", e);
        } catch (JOSEException e) {
            log.error("Error while verifying token signature", e);
            throw new OAuthSecurityException("Error while verifying token signature", e);
        } catch (IOException | AuthException e) {
            log.error("Error while connecting to JWKS endpoint", e);
            throw new OAuthSecurityException("Error while connecting to JWKS endpoint", e);
        } catch (OAuthSecurityException e) {
            log.error("Error while retrieving JWKS information", e);
            throw new OAuthSecurityException(e.getMessage(), e);
        }
    }

    /**
     * Validate scopes bound to the resource of the API being invoked against the scopes specified
     * in the JWT token payload.
     *
     * @param matchingResource         Accessed API resource
     * @param httpMethod               API resource's HTTP method
     * @param synCtx                   MessageContext
     * @throws OAuthSecurityException in case of scope validation failure
     */
    private boolean validateScopes(String matchingResource, String httpMethod, MessageContext synCtx,
                                   JWTValidationInfo jwtValidationInfo) throws OAuthSecurityException {

        // Format the lookup key
        String lookupKey = ApiUtils.getResourceScopeKey(httpMethod, matchingResource);

        // Get the required scopes from our pre-processed map
        Map<String, List<String>> resourceScopeMap =
                (Map<String, List<String>>) synCtx.getProperty(RESTConstants.RESOURCE_SCOPE_MAP);
        if (resourceScopeMap == null) {
            if (log.isDebugEnabled()) {
                log.debug("RESOURCE_SCOPE_MAP not found in message context. Skipping scope validation.");
            }
            return true;
        }

        List<String> requiredScopes = resourceScopeMap.get(lookupKey);
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return true; // No scopes required = Open Access
        }

        // Get scopes from the token claims
        List<String> tokenScopesClaims = jwtValidationInfo.getScopes();

        // Intersection Check (Does the user have ANY of the required scopes?)
        for (String required : requiredScopes) {
            for (String tokenScope : tokenScopesClaims) {
                if (required.equals(tokenScope)) { // exact, case-sensitive match
                    return true;
                }
            }
        }
        return false;
    }

    private void validateCertificateBinding(SignedJWTInfo signedJWTInfo, MessageContext messageContext) {

        try {
            org.apache.axis2.context.MessageContext axis2MsgContext =
                    ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            Certificate clientCertificate = OAuthUtil.getClientCertificate(axis2MsgContext, mtlsConfiguration);
            String cachedClientCertHash = signedJWTInfo.getClientCertificateHash();
            signedJWTInfo.setClientCertificate(clientCertificate);
            String newClientCertHash = signedJWTInfo.getClientCertificateHash();
            if (cachedClientCertHash != null) {
                // If cachedClientCertHash is not null, the signedJWTInfo object is obtained from the cache.
                // This means a request has been sent previously and the signedJWTInfo resultant object has
                // been stored in the cache.
                if (!cachedClientCertHash.equals(newClientCertHash)) {
                    // This scenario can happen when the previous request and the current request contains two
                    // different certificates. In such a scenario, we cannot guarantee the validationStatus
                    // signedJWTInfo object obtained from the cache to be correct. Hence, the validationStatus of
                    // the signedJWTInfo is set to NOT_VALIDATED so that the JWT token will be validated again.
                    signedJWTInfo.setValidationStatus(SignedJWTInfo.ValidationStatus.NOT_VALIDATED);
                }
            } else if (newClientCertHash != null) {
                // This scenario can happen in two different instances.
                // 1. When the signedJWTInfo object is not obtained from the cache and the current request contains
                //    a certificate in the request header. This scenario depicts a situation where the JWT has not
                //    been validated yet. Hence, the validationStatus of the signedJWTInfo is set to NOT_VALIDATED.
                // 2. When the signedJWTInfo object is obtained from the cache (cachedClientCertHash becomes null
                //    when the previous request do not contain a certificate in the request header) and the current
                //    request contains a certificate in the request header. In such a scenario, we cannot guarantee
                //    the validationStatus signedJWTInfo object as the certificate has not been validated. Hence,
                //    the validationStatus of the signedJWTInfo is set to NOT_VALIDATED so that the JWT token will
                //    be validated again.
                signedJWTInfo.setValidationStatus(SignedJWTInfo.ValidationStatus.NOT_VALIDATED);
            }
        } catch (OAuthSecurityException e) {
            log.error("Error while obtaining client certificate. Marking token as not validated.", e);
            // Clear any potentially stale certificate information from cached SignedJWTInfo
            signedJWTInfo.setClientCertificate(null);
            signedJWTInfo.setValidationStatus(SignedJWTInfo.ValidationStatus.NOT_VALIDATED);
        }
    }

    /**
     * Validates the 'iat' (Issued At) claim against a configured policy.
     * Ensures the token wasn't issued in the future (allowing for clock skew)
     * and isn't older than the maximum allowed age.
     *
     * @param claims The JWT claims set.
     * @param clockSkew Seconds allowed for clock drift.
     * @param maxAge Seconds allowed since issuance.
     * @throws OAuthSecurityException if the iat is outside the allowed period.
     */
    public boolean validateIssuedAtPolicy(JWTClaimsSet claims, long clockSkew, Long maxAge)
            throws OAuthSecurityException {

        Date iat = claims.getIssueTime();
        if (iat == null) {
            throw new OAuthSecurityException("Missing mandatory 'iat' claim");
        }

        long currentTimeMillis = System.currentTimeMillis();
        long iatTimeMillis = iat.getTime();

        // 1. Check for "Future" tokens (Clock Skew)
        // If IAT is > Current Time + Skew, someone's clock is wrong or it's a forgery.
        if (iatTimeMillis > (currentTimeMillis + (clockSkew * 1000))) {
            log.error("Token issued in the future. iat: " + iat);
            return false;
        }

        // 2. Check for "Stale" tokens (Max Age Policy)
        // If Current Time - IAT > Max Age, the token is too old for our security policy.
        if (maxAge != null && (currentTimeMillis - iatTimeMillis) > (maxAge * 1000)) {
            log.error("Token exceeds maximum allowed age. Issued at: " + iat);
            return false;
        }

        log.debug("Issued-at policy validation passed.");
        return true;
    }

    private boolean isIssuerTrusted(SignedJWTInfo signedJWTInfo) throws OAuthSecurityException {

        JWTClaimsSet jwtClaimsSet = signedJWTInfo.getJwtClaimsSet();
        if (jwtClaimsSet == null) {
            log.error("JWT claim set is null for token.");
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
        }
        String issuer = jwtClaimsSet.getIssuer();

        if (StringUtils.isNotEmpty(issuer) && trustedIssuers != null
                && trustedIssuers.contains(issuer)) {
            if (log.isDebugEnabled()) {
                log.debug("Issuer: " + issuer + " found for authentication token. "
                        + "Proceeding with authentication.");
            }
            return true;
        }
        return false;
    }
}
