/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.management.apis.security.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.integrator.initializer.dashboard.HMACJWTTokenGenerator;

import static org.wso2.micro.integrator.initializer.dashboard.Constants.ICP_CONFIG_ENABLED;
import static org.wso2.micro.integrator.initializer.dashboard.Constants.ICP_JWT_HMAC_SECRET;
import org.wso2.micro.integrator.management.apis.Constants;
import org.wso2.micro.integrator.management.apis.ManagementApiUndefinedException;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Map;

import static org.wso2.micro.integrator.management.apis.Constants.ICP_AUTHENTICATED_PROPERTY;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;

public class JWTTokenSecurityHandler extends AuthenticationHandlerAdapter {

    private static final Log LOG = LogFactory.getLog(JWTTokenSecurityHandler.class);
    private static final String DEFAULT_ICP_USERNAME = "icp-service";
    private static final Map<String, Object> configs = ConfigParser.getParsedConfigs();

    private String name;
    private HMACJWTTokenGenerator hmacValidator = null;
    private String cachedHmacSecret = null;
    private Boolean icpConfigValid = null; // Cache ICP configuration validation state


    public JWTTokenSecurityHandler(String context) throws CarbonException, XMLStreamException, IOException,
            ManagementApiUndefinedException {
        super(context);
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

        if ((Constants.REST_API_CONTEXT + Constants.PREFIX_LOGIN).contentEquals(messageContext.getTo().getAddress())) {
            //Login request is basic auth
            if (useCarbonUserStore) {
                //Uses carbon user store
                try {
                    return processAuthRequestWithCarbonUserStore(messageContext, authHeaderToken);
                } catch (UserStoreException e) {
                    LOG.error("Error while authenticating with carbon user store", e);
                }
            } else {
                //Uses in memory user store
                return processAuthRequestWithFileBasedUserStore(messageContext, authHeaderToken);
            }
        } else {
            //Other resources apart from /login should be authenticated from JWT based auth
            JWTTokenStore tokenStore = JWTInMemoryTokenStore.getInstance();
            JWTTokenInfoDTO jwtTokenInfoDTO = tokenStore.getToken(authHeaderToken);
            if (jwtTokenInfoDTO != null && !jwtTokenInfoDTO.isRevoked()) {
                jwtTokenInfoDTO.setLastAccess(System.currentTimeMillis()); //Record last successful access
                messageContext.setProperty(USERNAME_PROPERTY, jwtTokenInfoDTO.getUsername());
                return true;
            }
            // Fallback: if ICP is enabled, try validating as an HMAC JWT issued by ICP
            return tryICPHmacAuthentication(messageContext, authHeaderToken);
        }
        return false;
    }

    private synchronized HMACJWTTokenGenerator getOrCreateHmacValidator(String secret) {
        if (hmacValidator == null || !secret.equals(cachedHmacSecret)) {
            try {
                hmacValidator = new HMACJWTTokenGenerator(secret);
                cachedHmacSecret = secret;
                icpConfigValid = true;
            } catch (IllegalArgumentException e) {
                // Secret validation failed in constructor (less than 32 bytes)
                LOG.error("Invalid HMAC secret configured for ICP JWT validation: " + e.getMessage());
                icpConfigValid = false;
                throw e;
            }
        }
        return hmacValidator;
    }

    private boolean tryICPHmacAuthentication(MessageContext messageContext, String token) {
        // Return early if we've already determined ICP config is invalid
        if (Boolean.FALSE.equals(icpConfigValid)) {
            return false;
        }

        Object icpEnabled = configs.get(ICP_CONFIG_ENABLED);
        // Handle both Boolean and String "true" from config
        if (icpEnabled == null || !Boolean.parseBoolean(String.valueOf(icpEnabled))) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("ICP is not enabled, skipping HMAC JWT authentication");
            }
            return false;
        }
        Object secretObj = configs.get(ICP_JWT_HMAC_SECRET);
        if (secretObj == null) {
            LOG.warn("HMAC secret not configured for ICP JWT validation");
            icpConfigValid = false;
            return false;
        }
        String secret = secretObj.toString().trim();
        if (secret.isEmpty()) {
            LOG.warn("HMAC secret is empty for ICP JWT validation");
            icpConfigValid = false;
            return false;
        }

        // Resolve secret from secure vault if it's in $secret{alias} format
        secret = SecurityUtils.resolveSecretValue(secret);

        // Secret length validation now happens in HMACJWTTokenGenerator constructor
        // No need to validate here - constructor will throw IllegalArgumentException if invalid
        try {
            HMACJWTTokenGenerator validator = getOrCreateHmacValidator(secret);
            String username = validator.getUsernameFromToken(token, DEFAULT_ICP_USERNAME);
            if (username != null) {
                if (DEFAULT_ICP_USERNAME.equals(username)) {
                    LOG.warn("HMAC JWT token username defaulted to '" + DEFAULT_ICP_USERNAME + "' (no subject or issuer in token)");
                }
                // Log at INFO level for security audit trail
                if (LOG.isDebugEnabled()) {
                    LOG.debug("HMAC JWT authentication successful for ICP Management API request. User: " + username);
                }
                messageContext.setProperty(ICP_AUTHENTICATED_PROPERTY, true);
                messageContext.setProperty(USERNAME_PROPERTY, username);
                return true;
            }
        } catch (IllegalArgumentException e) {
            // Constructor validation failed - this is cached so we won't retry
            LOG.error("Invalid HMAC secret configured for ICP JWT validation", e);
        }
        return false;
    }

}
