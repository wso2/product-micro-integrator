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

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;

public class HMACJWTTokenGenerator {

    private static final Log log = LogFactory.getLog(HMACJWTTokenGenerator.class);

    /**
     * Clock skew tolerance in milliseconds (60 seconds) to account for time differences
     * between ICP and MI servers in distributed environments.
     */
    private static final long CLOCK_SKEW_TOLERANCE_MS = 60_000L;

    private final String hmacSecret;

    public HMACJWTTokenGenerator(String hmacSecret) {
        if (hmacSecret == null || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.error("Invalid HMAC secret provided - must be at least 256 bits (32 bytes)");
            throw new IllegalArgumentException("HMAC secret must be at least 256 bits (32 bytes)");
        }
        this.hmacSecret = hmacSecret;
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

        // Create HMAC signer
        JWSSigner signer = new MACSigner(hmacSecret.getBytes(StandardCharsets.UTF_8));

        // Create and sign JWT
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).build(),
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
            JWSVerifier verifier = new MACVerifier(hmacSecret.getBytes(StandardCharsets.UTF_8));

            // Verify signature
            if (!signedJWT.verify(verifier)) {
                log.warn("JWT signature verification failed");
                return null;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();

            // Verify expiry with clock skew tolerance
            if (expiry == null) {
                log.warn("JWT token is missing expiry claim");
                return null;
            }

            // Apply clock skew tolerance: allow tokens that expired within the last 60 seconds
            Date now = new Date(System.currentTimeMillis() - CLOCK_SKEW_TOLERANCE_MS);
            if (!now.before(expiry)) {
                log.warn("JWT token has expired (with " + CLOCK_SKEW_TOLERANCE_MS + "ms clock skew tolerance)");
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

        try {
            String subject = claims.getSubject();
            if (subject != null && !subject.isEmpty()) {
                return subject;
            }
            String issuer = claims.getIssuer();
            if (issuer != null && !issuer.isEmpty()) {
                return issuer;
            }
            return defaultUsername;
        } catch (Exception e) {
            log.error("Error extracting username from JWT claims", e);
            return null;
        }
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
