/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer.dashboard;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.config.mapper.ConfigParser;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;

public class HMACJWTTokenGenerator {

    private static final Log log = LogFactory.getLog(HMACJWTTokenGenerator.class);
    private static final Map<String, Object> configs = ConfigParser.getParsedConfigs();

    /**
     * Clock skew tolerance in milliseconds to account for time differences
     * between ICP and MI servers in distributed environments.
     * Configurable via deployment.toml (icp_config.jwt_clock_skew_tolerance_ms), defaults to 60 seconds.
     */
    private static final long CLOCK_SKEW_TOLERANCE_MS = getClockSkewTolerance();

    private final String hmacSecret;

    /**
     * Retrieves the clock skew tolerance configuration from deployment.toml.
     * Falls back to the default value if not configured.
     * Clamps the value to >= 0 to prevent unexpected token rejection behavior.
     *
     * @return clock skew tolerance in milliseconds
     */
    private static long getClockSkewTolerance() {
        Object configuredClockSkew = configs.get(Constants.ICP_JWT_CLOCK_SKEW_TOLERANCE_MS);
        if (configuredClockSkew != null) {
            try {
                long tolerance = Long.parseLong(configuredClockSkew.toString());
                if (tolerance < 0) {
                    log.warn("Invalid clock skew tolerance configured: " + tolerance
                            + "ms (negative values not allowed). Using default: "
                            + Constants.DEFAULT_JWT_CLOCK_SKEW_TOLERANCE_MS + "ms");
                    return Constants.DEFAULT_JWT_CLOCK_SKEW_TOLERANCE_MS;
                }
                return tolerance;
            } catch (NumberFormatException e) {
                log.warn("Invalid clock skew tolerance configured: " + configuredClockSkew
                        + ". Using default: " + Constants.DEFAULT_JWT_CLOCK_SKEW_TOLERANCE_MS + "ms", e);
            }
        }
        return Constants.DEFAULT_JWT_CLOCK_SKEW_TOLERANCE_MS;
    }

    public HMACJWTTokenGenerator(String hmacSecret) {
        if (hmacSecret == null) {
            throw new IllegalArgumentException("HMAC secret cannot be null");
        }
        this.hmacSecret = hmacSecret;

        byte[] keyMaterialBytes = getKeyMaterial().getBytes(StandardCharsets.UTF_8);
        if (keyMaterialBytes.length < 32) {
            log.error("Key material insufficient for HS256: " + keyMaterialBytes.length
                    + " bytes (requires 32 bytes)");
            throw new IllegalArgumentException("HS256 requires 32-byte key, found "
                    + keyMaterialBytes.length + " bytes");
        }
    }

    private String getKeyId() {
        int dotIndex = hmacSecret.indexOf('.');
        if (dotIndex > 0 && dotIndex < hmacSecret.length() - 1) {
            String kid = hmacSecret.substring(0, dotIndex);
            return kid;
        }
        return null;
    }

    private String getKeyMaterial() {
        int dotIndex = hmacSecret.indexOf('.');
        if (dotIndex > 0 && dotIndex < hmacSecret.length() - 1) {
            return hmacSecret.substring(dotIndex + 1);
        }
        return hmacSecret;
    }

    /**
     * Generate JWT Token with HMAC SHA256
     */
    public String generateToken(String issuer, String audience, String scope, long expiryTimeSeconds)
            throws JOSEException {
        if (log.isDebugEnabled()) {
            log.debug("Generating HMAC JWT token for issuer: " + issuer + ", audience: " + audience);
        }

        // Calculate expiry
        long expiryMillis = System.currentTimeMillis() + (expiryTimeSeconds * 1000);

        // Build claims
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .expirationTime(new Date(expiryMillis))
                .issueTime(new Date())
                .claim("scope", scope)
                .build();

        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.HS256);
        String kid = getKeyId();
        if (kid != null) {
            headerBuilder.keyID(kid);
            if (log.isDebugEnabled()) {
                log.debug("Adding kid (Key ID) to JWT header: " + kid);
            }
        }
        String keyMaterial = getKeyMaterial();
        JWSSigner signer = new MACSigner(keyMaterial.getBytes(StandardCharsets.UTF_8));


        // Create and sign JWT
        SignedJWT signedJWT = new SignedJWT(
                headerBuilder.build(),
                claimsSet
        );

        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    /**
     * Validates the JWT signature and expiry with clock skew tolerance.
     * Returns the validated JWTClaimsSet if valid, or null if invalid.
     *
     * @param token the serialized JWT string
     * @return validated JWTClaimsSet if token is valid, null otherwise
     */
    private JWTClaimsSet verifyAndGetClaims(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String keyMaterial = getKeyMaterial();
            JWSVerifier verifier = new MACVerifier(keyMaterial.getBytes(StandardCharsets.UTF_8));

            // Verify signature
            if (!signedJWT.verify(verifier)) {
                if (log.isDebugEnabled()) {
                    log.debug("JWT signature verification failed");
                }
                return null;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims == null) {
                if (log.isDebugEnabled()) {
                    log.debug("JWT token has no claims");
                }
                return null;
            }

            String expectedKid = getKeyId();
            if (expectedKid != null) {
                String tokenKid = signedJWT.getHeader().getKeyID();
                if (tokenKid == null) {
                    log.warn("JWT missing kid. Expected: " + expectedKid);
                    return null;
                }
                if (!expectedKid.equals(tokenKid)) {
                    log.warn("JWT kid mismatch. Expected: " + expectedKid + ", Actual: " + tokenKid);
                    return null;
                }
            }

            Date expiry = claims.getExpirationTime();

            // Verify expiry with clock skew tolerance
            if (expiry == null) {
                log.warn("JWT token is missing expiry claim");
                return null;
            }

            // Apply clock skew tolerance: allow tokens that expired within the last 60 seconds
            Date now = new Date(System.currentTimeMillis() - CLOCK_SKEW_TOLERANCE_MS);
            if (!now.before(expiry)) {
                if (log.isDebugEnabled()) {
                    log.debug("JWT token has expired (with " + CLOCK_SKEW_TOLERANCE_MS + "ms clock skew tolerance)");
                }
                return null;
            }

            if (log.isDebugEnabled()) {
                log.debug("JWT token validated successfully");
            }
            return claims;

        } catch (ParseException | JOSEException e) {
            log.error("Error validating HMAC JWT token", e);
            return null;
        }
    }

    /**
     * Validates the JWT token signed with HMAC SHA256 and returns the username extracted from the
     * claims. Checks the {@code sub} (subject) claim first, then falls back to the {@code iss}
     * (issuer) claim. If neither is present, returns the provided default username.
     * Returns {@code null} if the token signature is invalid or the token has expired,
     * so callers can treat a non-null return as proof of a valid token.
     *
     * @param token the serialized JWT string
     * @param defaultUsername the default username to return if neither subject nor issuer is present
     * @return the username from the token claims if the token is valid, or {@code null} if invalid
     */
    public String getUsernameFromToken(String token, String defaultUsername) {
        JWTClaimsSet claims = verifyAndGetClaims(token);
        if (claims == null) {
            return null;
        }
        String subject = claims.getSubject();
        if (subject != null && !subject.isEmpty()) {
            return subject;
        }
        String issuer = claims.getIssuer();
        if (issuer != null && !issuer.isEmpty()) {
            return issuer;
        }
        return defaultUsername;
    }

    /**
     * Validates the JWT token signed with HMAC SHA256 and returns the username extracted from the
     * claims. Checks the {@code sub} (subject) claim first, then falls back to the {@code iss}
     * (issuer) claim. Returns {@code null} if the token signature is invalid, the token has expired,
     * or if neither subject nor issuer is present.
     *
     * @param token the serialized JWT string
     * @return the username from the token claims if the token is valid, or {@code null} if invalid
     */
    public String getUsernameFromToken(String token) {
        return getUsernameFromToken(token, null);
    }

}
