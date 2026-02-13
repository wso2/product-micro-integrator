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

package org.wso2.micro.integrator.icp.apis.security.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.integrator.management.apis.ManagementApiUndefinedException;
import org.wso2.micro.integrator.management.apis.security.handler.AuthenticationHandlerAdapter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Security handler for ICP endpoints that validates HMAC-based JWT tokens.
 * Similar to JWTTokenSecurityHandler but uses HMAC secret authentication.
 */
public class ICPJWTSecurityHandler extends AuthenticationHandlerAdapter {

    private static final Log LOG = LogFactory.getLog(ICPJWTSecurityHandler.class);
    private static final String DEFAULT_JWT_HMAC_SECRET = "default-secret-key-at-least-32-characters-long-for-hs256";

    private String name;
    private String jwtHmacSecret = DEFAULT_JWT_HMAC_SECRET;

    /**
     * Constructor required by ConfigurationLoader.
     * @param context The API context path
     */
    public ICPJWTSecurityHandler(String context) throws CarbonException, XMLStreamException, IOException,
            ManagementApiUndefinedException {
        super(context);
        LOG.info("ICPJWTSecurityHandler initialized for context: " + context);
    }

    @Override
    public Boolean invoke(MessageContext messageContext) {
        return super.invoke(messageContext);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected Boolean authenticate(MessageContext messageContext, String authHeaderToken) {
        // Validate HMAC JWT token
        if (validateHMACJWT(authHeaderToken)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("HMAC JWT token validated successfully");
            }
            return true;
        } else {
            LOG.warn("HMAC JWT token validation failed");
            return false;
        }
    }

    /**
     * Validates HMAC-based JWT token.
     */
    private boolean validateHMACJWT(String token) {
        try {
            // Split JWT into parts
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                LOG.warn("Invalid JWT structure");
                return false;
            }

            String headerPayload = parts[0] + "." + parts[1];
            String signature = parts[2];

            // Verify signature using HMAC-SHA256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    jwtHmacSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] expectedSignature = mac.doFinal(headerPayload.getBytes(StandardCharsets.UTF_8));
            byte[] providedSignatureBytes = Base64.getUrlDecoder().decode(signature);

            if (!MessageDigest.isEqual(expectedSignature, providedSignatureBytes)) {
                LOG.warn("JWT signature verification failed");
                return false;
            }

            // Decode payload to check expiry
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            // Extract exp claim using Gson
            try {
                JsonObject claims = JsonParser.parseString(payloadJson).getAsJsonObject();
                if (!claims.has("exp")) {
                    LOG.warn("JWT token is missing the required exp claim");
                    return false;
                }
                if (!claims.get("exp").isJsonPrimitive() || !claims.get("exp").getAsJsonPrimitive().isNumber()) {
                    LOG.warn("JWT exp claim is not a numeric value");
                    return false;
                }
                long exp = claims.get("exp").getAsLong();
                long currentTime = System.currentTimeMillis() / 1000;
                if (currentTime >= exp) {
                    LOG.warn("JWT token has expired");
                    return false;
                }
            } catch (JsonSyntaxException e) {
                LOG.warn("Failed to parse JWT payload as JSON", e);
                return false;
            }

            return true;

        } catch (Exception e) {
            LOG.error("Error validating HMAC JWT", e);
            return false;
        }
    }

    /**
     * Sets the JWT HMAC secret. Called by the handler configuration parser.
     * Can be configured in internal-apis.xml as:
     * <handler class="..." name="ICPJWTSecurityHandler">
     *     <JwtHmacSecret>your-secret-here</JwtHmacSecret>
     * </handler>
     */
    public void setJwtHmacSecret(String secret) {
        if (secret != null && !secret.trim().isEmpty()) {
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                LOG.warn("JWT HMAC secret should be at least 32 bytes. Using provided secret anyway.");
            }
            this.jwtHmacSecret = secret;
            LOG.info("JWT HMAC secret configured from internal-apis.xml");
        }
    }

}
