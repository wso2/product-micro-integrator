package org.wso2.micro.integrator.initializer.dashboard;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class HMACJWTTokenGenerator {

    private final String hmacSecret;

    public HMACJWTTokenGenerator(String hmacSecret) {
        if (hmacSecret == null || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("HMAC secret must be at least 256 bits (32 bytes)");
        }
        this.hmacSecret = hmacSecret;
    }

    /**
     * Generate JWT Token with HMAC SHA256
     */
    public String generateToken(String issuer, String audience, String scope, long expiryTimeSeconds)
            throws JOSEException {

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
