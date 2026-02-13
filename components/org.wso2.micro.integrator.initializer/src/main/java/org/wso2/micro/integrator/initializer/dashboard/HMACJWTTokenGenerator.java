/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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

package org.wso2.micro.integrator.initializer.dashboard;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class HMACJWTTokenGenerator {

    private static final Log log = LogFactory.getLog(HMACJWTTokenGenerator.class);

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

}
