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

import static org.wso2.micro.integrator.management.apis.Constants.ICP_AUTHENTICATED_PROPERTY;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;

public class JWTTokenSecurityHandler extends AuthenticationHandlerAdapter {

    private static final Log LOG = LogFactory.getLog(JWTTokenSecurityHandler.class);
    private String name;

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
            if (tryICPHmacAuthentication(messageContext, authHeaderToken)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryICPHmacAuthentication(MessageContext messageContext, String token) {
        Object icpEnabled = ConfigParser.getParsedConfigs().get(ICP_CONFIG_ENABLED);
        if (!Boolean.TRUE.equals(icpEnabled)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("ICP is not enabled. Skipping ICP HMAC JWT authentication attempt.");
            }
            return false;
        }
        Object secretObj = ConfigParser.getParsedConfigs().get(ICP_JWT_HMAC_SECRET);
        if (secretObj == null || secretObj.toString().trim().isEmpty()) {
            LOG.warn("HMAC secret not configured for ICP JWT validation");
            return false;
        }
        try {
            HMACJWTTokenGenerator validator = new HMACJWTTokenGenerator(secretObj.toString().trim());
            if (validator.validateToken(token)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("HMAC JWT token validated successfully for ICP Management API request");
                }
                messageContext.setProperty(ICP_AUTHENTICATED_PROPERTY, true);
                messageContext.setProperty(USERNAME_PROPERTY, "icp-service");
                return true;
            }
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid HMAC secret configured for ICP JWT validation", e);
        }
        return false;
    }

}
