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
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.util.DateUtils;
import org.apache.axis2.Constants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.endpoints.auth.AuthException;
import org.apache.synapse.rest.RESTConstants;
import org.wso2.micro.integrator.security.handler.oauth.CacheProvider;
import org.wso2.micro.integrator.security.handler.oauth.HttpClientConfiguration;
import org.wso2.micro.integrator.security.handler.oauth.OAuthConstants;
import org.wso2.micro.integrator.security.handler.oauth.OAuthSecurityException;
import org.wso2.micro.integrator.security.handler.oauth.OAuthUtil;
import org.wso2.micro.integrator.security.handler.oauth.RevocationCheckException;
import org.wso2.micro.integrator.security.handler.oauth.SignedJWTInfo;
import org.wso2.micro.integrator.security.handler.oauth.TokenRevocationChecker;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JWTValidator {

    private static final Log log = LogFactory.getLog(JWTValidator.class);

    private final TokenRevocationChecker tokenRevocationChecker;
    private final String jwksEndpoint;
    private final HttpClientConfiguration httpClientConfiguration;

    public JWTValidator(TokenRevocationChecker tokenRevocationChecker,
                        String jwksEndpoint, HttpClientConfiguration httpClientConfiguration) {

        this.tokenRevocationChecker = tokenRevocationChecker;
        this.jwksEndpoint = jwksEndpoint;
        this.httpClientConfiguration = httpClientConfiguration;
    }

    /**
     * Authenticate an incoming request represented by the provided {@link SignedJWTInfo}.
     *
     * <p>This method performs the high-level JWT authentication flow:
     * <ol>
     *   <li>Compute a token identifier ("jti" claim or signature) for cache/revocation checks.</li>
     *   <li>If a {@link TokenRevocationChecker} is configured, invoke it to short-circuit revoked tokens.</li>
     *   <li>Obtain a {@link JWTValidationInfo} via {@link #getJwtValidationInfo} (which may
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
        String matchingResource = (String) synCtx.getProperty(RESTConstants.SELECTED_RESOURCE);
        String jwtTokenIdentifier = getJWTTokenIdentifier(signedJWTInfo);
        String jwtHeader = signedJWTInfo.getSignedJWT().getHeader().toString();

        //TODO: handle cnf validation

        if (StringUtils.isNotEmpty(jwtTokenIdentifier) && tokenRevocationChecker != null) {
            // Check whether the token is revoked or not by invoking the plugged in token revocation checker
            // implementation. If the token is revoked, we can directly return without validating the signature.
            try {
                if (tokenRevocationChecker.isRevoked(signedJWTInfo.getToken(),
                        signedJWTInfo.getJwtClaimsSet().getClaims())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Token retrieved from the revoked jwt token map. Token: "
                                + OAuthUtil.getMaskedToken(jwtHeader));
                    }
                    log.error("Invalid JWT token. " + OAuthUtil.getMaskedToken(jwtHeader));
                    throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                            "Invalid JWT token");
                }
            } catch (RevocationCheckException e) {
                log.error("Error while checking token revocation status for token: " + OAuthUtil
                        .getMaskedToken(jwtHeader), e);
                throw new OAuthSecurityException("Error while checking token revocation status", e);
            }
        }

        JWTValidationInfo jwtValidationInfo = getJwtValidationInfo(signedJWTInfo, jwtTokenIdentifier, synCtx);

        if (jwtValidationInfo.isValid()) {
            // Validate scopes
            JWTClaimsSet jwtClaimsSet = signedJWTInfo.getJwtClaimsSet();
            if (!validateScopes(matchingResource, httpMethod, synCtx, jwtClaimsSet)) {
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
    private String getJWTTokenIdentifier(SignedJWTInfo signedJWTInfo) {

        JWTClaimsSet jwtClaimsSet = signedJWTInfo.getJwtClaimsSet();
        if (jwtClaimsSet != null) {
            String jti = jwtClaimsSet.getJWTID();
            if (StringUtils.isNotEmpty(jti)) {
                return jti;
            }
        }
        return signedJWTInfo.getSignedJWT().getSignature().toString();
    }

    /**
     * Obtain a {@link JWTValidationInfo} for a token identified by {@code jti}.
     *
     * <p>This method first attempts to return a cached validation result if available:
     * <ul>
     *   <li>If the provided {@code signedJWTInfo} already has validation status {@code VALID}
     *       and an entry exists in the token key cache, the cached {@link JWTValidationInfo}
     *       is returned after calling {@link #checkTokenExpiration} to ensure it is not expired.</li>
     *   <li>If the token is present in the invalid-token cache, a {@link JWTValidationInfo}
     *       with {@code valid=false} and {@link OAuthConstants#API_AUTH_INVALID_CREDENTIALS}
     *       as the validation code is returned.</li>
     * </ul>
     * If no cached result exists, the method performs a full validation by invoking
     * {@link #validateJwtToken}, updates the {@code signedJWTInfo} validation status, and
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
    private JWTValidationInfo getJwtValidationInfo(SignedJWTInfo signedJWTInfo, String jti,
                                                   MessageContext messageContext) throws OAuthSecurityException {

        String jwtHeader = signedJWTInfo.getSignedJWT().getHeader().toString();
        JWTValidationInfo jwtValidationInfo = null;

        // The "KEY_CACHE" contains traces of the already validated access tokens. Check the token cache
        // to find whether the token is already validated. If the token is present in the cache and the validation
        // status is valid, we can directly return from the cache without validating the signature again.
        // If the token is present in the invalid token cache, we can directly return without validating the signature.
        Object keyCache = CacheProvider.getTokenCache().get(jti);
        if (SignedJWTInfo.ValidationStatus.VALID.equals(signedJWTInfo.getValidationStatus()) && keyCache != null) {
            JWTValidationInfo tempJWTValidationInfo = (JWTValidationInfo) keyCache;
            checkTokenExpiration(jti, tempJWTValidationInfo);
            jwtValidationInfo = tempJWTValidationInfo;
        } else if (CacheProvider.getInvalidTokenCache().get(jti) != null) {
            if (log.isDebugEnabled()) {
                log.debug("Token retrieved from the invalid token cache. Token: " + OAuthUtil
                        .getMaskedToken(jwtHeader));
            }
            log.error("Invalid JWT token. " + OAuthUtil.getMaskedToken(jwtHeader));

            jwtValidationInfo = new JWTValidationInfo();
            jwtValidationInfo.setValidationCode(OAuthConstants.API_AUTH_INVALID_CREDENTIALS);
            jwtValidationInfo.setValid(false);
        }

        if (jwtValidationInfo == null) {
            jwtValidationInfo = validateJwtToken(signedJWTInfo, messageContext);
            signedJWTInfo.setValidationStatus(jwtValidationInfo.isValid() ?
                    SignedJWTInfo.ValidationStatus.VALID : SignedJWTInfo.ValidationStatus.INVALID);

            if (jwtValidationInfo.isValid()) {
                CacheProvider.getTokenCache().put(jti, jwtValidationInfo);
            } else {
                CacheProvider.getInvalidTokenCache().put(jti, Boolean.TRUE);
            }
        }
        return jwtValidationInfo;
    }

    /**
     * Check whether the jwt token is expired or not.
     *
     * @param tokenIdentifier The token Identifier of JWT.
     * @param payload         The payload of the JWT token.
     * @return the {@link JWTValidationInfo} containing the updated validation and expiration status of the token
     */
    private JWTValidationInfo checkTokenExpiration(String tokenIdentifier, JWTValidationInfo payload) {

        long timestampSkew = getTimeStampSkewInSeconds();

        Date now = new Date();
        Date exp = new Date(payload.getExpiryTime());
        if (!DateUtils.isAfter(exp, now, timestampSkew)) {
            CacheProvider.getTokenCache().remove(tokenIdentifier);
            CacheProvider.getInvalidTokenCache().put(tokenIdentifier, Boolean.TRUE);
            payload.setValid(false);
            payload.setValidationCode(OAuthConstants.API_AUTH_ACCESS_TOKEN_EXPIRED);
            payload.setExpired(true);
            return payload;
        }
        return payload;
    }

    protected boolean validateTokenExpiry(JWTClaimsSet jwtClaimsSet) {

        long timestampSkew = getTimeStampSkewInSeconds();
        Date now = new Date();
        Date exp = jwtClaimsSet.getExpirationTime();
        return exp == null || DateUtils.isAfter(exp, now, timestampSkew);
    }

    /**
     * Validate the provided Signed JWT and produce a {@link JWTValidationInfo} describing
     * the validation result and extracted token details.
     *
     * <p>Validation performed by this method:
     * <ul>
     *   <li>Verify the JWT signature (via {@link #validateSignature}).</li>
     *   <li>Check token expiry against the current time allowing for configured timestamp skew
     *       (via {@link #validateTokenExpiry}).</li>
     *   <li>If validation succeeds, populate scopes (via {@link #getTransformedScopes}), issuer,
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
     *                                causes (for example {@link java.text.ParseException},
     *                                {@link com.nimbusds.jose.JOSEException}, {@link java.io.IOException})
     *                                are wrapped in this exception.
     */
    private JWTValidationInfo validateJwtToken(SignedJWTInfo signedJWTInfo, MessageContext messageContext)
            throws OAuthSecurityException {

        JWTValidationInfo jwtValidationInfo = new JWTValidationInfo();
        boolean state;
        String errorMessage;
        try {
            state = validateSignature(signedJWTInfo.getSignedJWT(), messageContext);
            if (state) {
                JWTClaimsSet jwtClaimsSet = signedJWTInfo.getJwtClaimsSet();
                state = validateTokenExpiry(jwtClaimsSet);
                if (state) {
                    jwtValidationInfo.setScopes(getTransformedScopes(jwtClaimsSet));
                    createJWTValidationInfoFromJWT(jwtValidationInfo, jwtClaimsSet);
                    jwtValidationInfo.setRawPayload(signedJWTInfo.getToken());
                    return jwtValidationInfo;
                } else {
                    errorMessage = "JWT token is expired. Token: " + OAuthUtil.getMaskedToken(signedJWTInfo.getToken());
                }
            } else {
                errorMessage = "JWT signature validation failed. Token: " + OAuthUtil.getMaskedToken(signedJWTInfo.getToken());

            }
            log.error(errorMessage);
            jwtValidationInfo.setValid(false);
            jwtValidationInfo.setValidationCode(OAuthConstants.API_AUTH_INVALID_CREDENTIALS);
            return jwtValidationInfo;
        } catch (ParseException e) {
            throw new OAuthSecurityException("Error while parsing JWT Token: "
                    + OAuthUtil.getMaskedToken(signedJWTInfo.getToken()), e);
        }
    }

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
                JWKSet jwkSet;
                // Check JWKSet Available in Cache
                Object jwksCache = CacheProvider.getJwksCache().get(jwksEndpoint);
                if (jwksCache != null) {
                    jwkSet = (JWKSet) jwksCache;
                } else {
                    jwkSet = retrieveJWKSet(jwksEndpoint, messageContext);
                }

                if (jwkSet != null) {
                    CacheProvider.getJwksCache().put(jwksEndpoint, jwkSet);
                } else {
                    return false;
                }

                if (jwkSet.getKeyByKeyId(keyID) == null) {
                    jwkSet = retrieveJWKSet(jwksEndpoint, messageContext);
                    if (jwkSet != null) {
                        CacheProvider.getJwksCache().put(jwksEndpoint, jwkSet);
                    }
                }

                if (jwkSet.getKeyByKeyId(keyID) == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Key with ID " + keyID + " not found in JWKS endpoint");
                    }
                    return false;
                } else if (jwkSet.getKeyByKeyId(keyID) instanceof RSAKey) {
                    RSAKey keyByKeyId = (RSAKey) jwkSet.getKeyByKeyId(keyID);
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

    private JWKSet retrieveJWKSet(String jwksEndpoint, MessageContext messageContext)
            throws IOException, ParseException, OAuthSecurityException, AuthException {

        JWKSet jwkSet;
        String jwksInfo = JWTUtil.retrieveJWKSConfiguration(jwksEndpoint, httpClientConfiguration, messageContext);
        if (jwksInfo != null) {
            jwkSet = JWKSet.parse(jwksInfo);
        } else {
            throw new OAuthSecurityException("Invalid JWKS endpoint.");
        }
        return jwkSet;
    }

    protected long getTimeStampSkewInSeconds() {

        return OAuthConstants.DEFAULT_TIMESTAMP_SKEW_IN_SECONDS;
    }

    private void createJWTValidationInfoFromJWT(JWTValidationInfo jwtValidationInfo,
                                                JWTClaimsSet jwtClaimsSet)
            throws ParseException {

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
     * Validate scopes bound to the resource of the API being invoked against the scopes specified
     * in the JWT token payload.
     *
     * @param matchingResource         Accessed API resource
     * @param httpMethod               API resource's HTTP method
     * @param synCtx                   MessageContext
     * @throws OAuthSecurityException in case of scope validation failure
     */
    private boolean validateScopes(String matchingResource, String httpMethod, MessageContext synCtx,
                                   JWTClaimsSet jwtClaimsSet) throws OAuthSecurityException {

        // Format the lookup key
        String lookupKey = httpMethod + ":" + matchingResource;

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

        List<String> tokenScopesClaims = Collections.emptyList();

        try {
            String scopeClaim = OAuthConstants.SCOPE;
            if (jwtClaimsSet.getClaim(scopeClaim) instanceof String) {
                tokenScopesClaims = Arrays.asList(jwtClaimsSet.getStringClaim(scopeClaim)
                        .split(OAuthConstants.SCOPE_DELIMITER));
            } else if (jwtClaimsSet.getClaim(scopeClaim) instanceof List) {
                tokenScopesClaims = jwtClaimsSet.getStringListClaim(scopeClaim);
            }
        } catch (ParseException e) {
            throw new OAuthSecurityException("Error while parsing JWT claims", e);
        }

        // Intersection Check (Does the user have ANY of the required scopes?)
        for (String required : requiredScopes) {
            if (tokenScopesClaims.contains(required)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getTransformedScopes(JWTClaimsSet jwtClaimsSet) throws OAuthSecurityException {

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
}
