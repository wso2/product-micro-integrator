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
package org.wso2.micro.integrator.management.apis;

import com.nimbusds.jose.JOSEException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.management.apis.security.handler.AuthConstants;
import org.wso2.micro.integrator.management.apis.security.handler.JWTConfig;
import org.wso2.micro.integrator.management.apis.security.handler.JWTInMemoryTokenStore;
import org.wso2.micro.integrator.management.apis.security.handler.JWTTokenCleanupTask;
import org.wso2.micro.integrator.management.apis.security.handler.JWTTokenGenerator;
import org.wso2.micro.integrator.management.apis.security.handler.JWTTokenInfoDTO;
import org.wso2.micro.integrator.management.apis.security.handler.JWTTokenStore;
import org.wso2.micro.integrator.management.apis.security.handler.SecurityUtils;
import org.wso2.micro.integrator.security.MicroIntegratorSecurityUtils;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.wso2.micro.integrator.management.apis.Constants.INTERNAL_SERVER_ERROR;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;

/**
 * Resource for login. This obtains JWT token. This is basic auth protected.
 */
public class LoginResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(LoginResource.class);

    // HTTP method types supported by the resource
    Set<String> methods;

    public LoginResource() {

        methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
    }

    @Override
    public Set<String> getMethods() {

        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {

        JWTConfig jwtConfig = JWTConfig.getInstance();
        if (jwtConfig == null || jwtConfig.getJwtConfigDto() == null) {
            handleServerError(axis2MessageContext, "JWT configuration error");
            return true;
        }
        if (!jwtConfig.getJwtConfigDto().isJwtHandlerEngaged()) {
            LOG.error("/Login is accessible only when JWT based auth handler is engaged");
            handleServerError(axis2MessageContext, "Login is accessible only when JWT based auth handler is engaged");
            return true;
        }

        //Init token store
        JWTTokenStore tokenStore =
                JWTInMemoryTokenStore.getInstance(jwtConfig.getJwtConfigDto().getTokenStoreSize());

        //UUID used as unique token
        UUID uuid = UUID.randomUUID();
        String randomUUIDString = uuid.toString();
        String username = messageContext.getProperty(USERNAME_PROPERTY).toString();
        JWTTokenInfoDTO newToken = new JWTTokenInfoDTO(username);
        newToken.setToken(randomUUIDString);
        try {
            if (SecurityUtils.isAdmin(messageContext, username)) {
                newToken.setScope(AuthConstants.JWT_TOKEN_ADMIN_SCOPE);
            } else {
                newToken.setScope(AuthConstants.JWT_TOKEN_DEFAULT_SCOPE);
            }
        } catch (UserStoreException e) {
            final String errorMessage = "Error occurred authenticating user.";
            LOG.error(errorMessage, e);
            handleServerError(axis2MessageContext, errorMessage);
            return true;
        }
        newToken.setIssuer((String) axis2MessageContext.getProperty(NhttpConstants.SERVICE_PREFIX));
        long time = System.currentTimeMillis();
        String expiryConfig = JWTConfig.getInstance().getJwtConfigDto().getExpiry();
        newToken.setLastAccess(time); // Assign creation time initially
        long expiryDuration = AuthConstants.DEFAULT_EXPIRY_DURATION;
        if (!StringUtils.isEmpty(expiryConfig)) {
            expiryDuration = Long.parseLong(expiryConfig);
        }
        newToken.setExpiry(time + (expiryDuration * 1000)); //convert to millis
        String jwtHash = null;
        try {
            jwtHash = populateJWTToken(newToken);
        } catch (JOSEException e) {
            LOG.error("Error occurred while generating JWT token", e);
            handleServerError(axis2MessageContext, "Error occurred while generating JWT token");
            return true;
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Error occurred while generating key pairs", e);
            handleServerError(axis2MessageContext, "Error occurred while generating key pairs");
            return true;
        }
        newToken.setHash(jwtHash);
        if (!tokenStore.putToken(jwtHash, newToken)) {
            //Token store has been exhausted
            handleServerError(axis2MessageContext, "Max concurrent access limit exceeded");
            return true;
        }

        JSONObject jsonPayload = new JSONObject();
        jsonPayload.put(AuthConstants.RESPONSE_JSON_TOKEN_FIELD, jwtHash);
        Utils.setJsonPayLoad(axis2MessageContext, jsonPayload);
        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
        JWTTokenCleanupTask.startCleanupTask();
        Date currentTime = new Date(time);
        AuditLogger.logAuditMessage(username + " logged in at [" + currentTime.toString() + "]");
        return true;
    }

    /**
     * Create JWT Token using JWT Token info DTO
     *
     * @param jwtToken JWT Token info object
     * @return Serialized JWT Token string
     * @throws JOSEException
     * @throws NoSuchAlgorithmException
     */
    public String populateJWTToken(JWTTokenInfoDTO jwtToken) throws JOSEException, NoSuchAlgorithmException {

        JWTTokenGenerator generator = new JWTTokenGenerator();
        return generator.generateJWTToken(jwtToken);
    }

    /**
     * Generate and sets error json response
     *
     * @param axis2MessageContext msg ctx
     * @param errorDetail         Error string
     */
    private void handleServerError(org.apache.axis2.context.MessageContext axis2MessageContext, String errorDetail) {

        Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject(errorDetail));
        axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.INTERNAL_SERVER_ERROR);
        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
    }

}
