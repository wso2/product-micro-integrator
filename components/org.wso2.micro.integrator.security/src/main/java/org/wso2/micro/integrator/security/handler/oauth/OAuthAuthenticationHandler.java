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

package org.wso2.micro.integrator.security.handler.oauth;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.axis2.Constants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.api.API;
import org.apache.synapse.api.ApiUtils;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.dispatch.RESTDispatcher;
import org.apache.synapse.api.version.VersionStrategy;
import org.apache.synapse.config.xml.rest.VersionStrategyFactory;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.rest.AbstractHandler;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.security.handler.oauth.jwt.JWTValidator;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;

public class OAuthAuthenticationHandler extends AbstractHandler implements ManagedLifecycle {

    private static final Log log = LogFactory.getLog(OAuthAuthenticationHandler.class);

    // Handler properties
    private static final String DEFAULT_SECURITY_HEADER = HttpHeaders.AUTHORIZATION;
    private String authorizationHeader = DEFAULT_SECURITY_HEADER;
    private ArrayList<String> trustedIssuers;
    private String jwksEndpoint;
    private Boolean removeOAuthHeadersFromOutMessage = true;
    private TokenRevocationChecker tokenRevocationChecker;

    // Global properties
    private int tokenCacheTimeout;
    private HttpClientConfiguration httpClientConfiguration;

    @Override
    public void init(SynapseEnvironment synapseEnvironment) {

        if (tokenCacheTimeout == 0) {
            Object tokenCacheTimeoutConfig = ConfigParser.getParsedConfigs().get(OAuthConstants.CACHE_EXPIRY);
            if (tokenCacheTimeoutConfig != null) {
                this.tokenCacheTimeout = ((Long) tokenCacheTimeoutConfig).intValue();
            }
        }

        if (trustedIssuers == null) {
            Object trustedIssuersConfig = ConfigParser.getParsedConfigs().get(OAuthConstants.TRUSTED_ISSUERS);
            if (trustedIssuersConfig != null) {
                this.trustedIssuers = (ArrayList<String>) Arrays.asList(((String)trustedIssuersConfig).split("\\s*,\\s*"));
            }
        }

        // Set default timeouts. These can be overridden by configs if needed.
        int connectionTimeout = 3000;
        int socketTimeout = 3000;
        int requestTimeout = 3000;
        HttpClientConfiguration.Builder builder  = new HttpClientConfiguration.Builder();
        new HttpClientConfiguration.Builder().withConnectionParams(connectionTimeout, requestTimeout, socketTimeout);
        httpClientConfiguration = builder.build();
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean handleRequest(MessageContext messageContext) {

        try {
            // Check for the matching resource before doing any authentication related processing.
            String matchingResource = getApiElectedResource(messageContext);
            if (matchingResource == null) {
                log.error("No matching resource found for request path: "
                        + messageContext.getProperty(RESTConstants.REST_FULL_REQUEST_PATH));
                throw new OAuthSecurityException(OAuthConstants.API_AUTH_INCORRECT_API_RESOURCE,
                        OAuthConstants.API_AUTH_INCORRECT_API_RESOURCE_MESSAGE);
            }

            Map headers = (Map) ((Axis2MessageContext) messageContext).getAxis2MessageContext().
                    getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
            if (headers == null) {
                log.error("No transport headers found in the message context.");
                throw new OAuthSecurityException(OAuthConstants.API_AUTH_MISSING_CREDENTIALS,
                        OAuthConstants.API_AUTH_MISSING_CREDENTIALS_MESSAGE);
            }

            String authHeader = (String) headers.get(authorizationHeader);
            if (authHeader == null) {
                log.error("Expected authorization header with the name '"
                        .concat(authorizationHeader).concat("' is not found."));
                throw new OAuthSecurityException(OAuthConstants.API_AUTH_MISSING_CREDENTIALS,
                        OAuthConstants.API_AUTH_MISSING_CREDENTIALS_MESSAGE);
            }

            String[] elements = authHeader.split(OAuthConstants.CONSUMER_KEY_SEGMENT_DELIMITER);
            String authScheme = elements[0];
            String accessToken = elements[1];

            // Currently, this handler only supports JWT tokens. Hence, the authentication scheme should be Bearer.
            if (!OAuthConstants.BEARER.equals(authScheme)) {
                log.error("Unsupported authentication scheme for JWT: " + authScheme);
                throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                        OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
            }

            //Initial guess of a JWT token using the presence of a DOT.
            if (StringUtils.isEmpty(accessToken) || !accessToken.contains(OAuthConstants.DOT)) {
                log.error("The provided credential does not follow the JWT format");
                throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                        OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
            }

            String[] JWTElements = accessToken.split("\\.");

            if (JWTElements.length != 3) {
                log.error("The provided credential does not follow the JWT format");
                throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                        OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
            }

            SignedJWTInfo signedJWTInfo;
            try {
                signedJWTInfo = getSignedJwtInfo(accessToken);
                // TODO: check what happens if the claimset is null
                String issuer = signedJWTInfo.getJwtClaimsSet().getIssuer();

                if (StringUtils.isNotEmpty(issuer) && trustedIssuers != null
                        && trustedIssuers.contains(issuer)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Issuer: " + issuer + "found for authenticate token "
                                + OAuthUtil.getMaskedToken(accessToken));
                    }
                } else {
                    log.error("The token issuer is not in the list of trusted issuers.");
                    throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                            OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
                }
            } catch (ParseException e) {
                log.error("Error while parsing the access token: " + OAuthUtil.getMaskedToken(accessToken), e);
                throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                        OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE, e);
            }

            if (log.isDebugEnabled()) {
                log.debug("Authentication started for JWT tokens");
            }

            JWTValidator jwtValidator = new JWTValidator(tokenRevocationChecker, jwksEndpoint,
                    httpClientConfiguration);
            jwtValidator.authenticate(signedJWTInfo, messageContext);

        } catch (OAuthSecurityException e) {
            handleAuthFailure(messageContext, e);
            return false;
        } finally {
            if (removeOAuthHeadersFromOutMessage) {
                // Remove the auth header from the transport headers to prevent it from being propagated to the backend.
                Map headers = (Map) ((Axis2MessageContext) messageContext).getAxis2MessageContext().
                        getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
                if (headers != null) {
                    headers.remove(authorizationHeader);
                }
            }
        }

        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) {

        return false;
    }

    private String getSecurityHeader() {

        return authorizationHeader;
    }

    /**
     * To set the Authorization Header.
     *
     * @param authorizationHeader the Authorization Header of the API request.
     */
    public void setAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader != null) {
            this.authorizationHeader = authorizationHeader;
        }
    }

    public void setTokenRevocationChecker(String revocationChecker) {
        if (revocationChecker != null) {
            Class clazz = null;
            try {
                clazz = JWTValidator.class.getClassLoader().loadClass(revocationChecker);
                this.tokenRevocationChecker = (TokenRevocationChecker) clazz.newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                //TODO: Do we need to throw an exception here?
            }
        }
    }

    public void setTrustedIssuers(String trustedIssuers) {

        if (trustedIssuers != null && !trustedIssuers.isEmpty()) {
            this.trustedIssuers = new ArrayList<>(Arrays.asList(trustedIssuers.split("\\s*,\\s*")));
        }
    }

    public void setTokenCacheTimeout(String tokenCacheTimeout) {

        if (tokenCacheTimeout != null) {
            this.tokenCacheTimeout = Integer.parseInt(tokenCacheTimeout);
        }
    }

    public void setRemoveOAuthHeadersFromOutMessage(String removeOAuthHeadersFromOutMessage) {

        this.removeOAuthHeadersFromOutMessage = Boolean.valueOf(removeOAuthHeadersFromOutMessage);
    }

    public Boolean getRemoveOAuthHeadersFromOutMessage() {

        return removeOAuthHeadersFromOutMessage;
    }

    public void setJwksEndpoint(String jwksEndpoint) {

        this.jwksEndpoint = jwksEndpoint;
    }

    /**
     * Get signed JWT info for access token
     *
     * @param accessToken Access token
     * @return SignedJWTInfo
     * @throws ParseException if an error occurs
     */
    private SignedJWTInfo getSignedJwtInfo(String accessToken) throws ParseException {

        String signature = accessToken.split("\\.")[2];
        SignedJWTInfo signedJWTInfo = null;

        // Check if a cache is configured for signed JWT parsing.
        // If so, try to get the signed JWT info from the cache using the token signature as the key.
        Cache signedJWTParseCache = CacheProvider.getSignedJWTParseCache();
        if (signedJWTParseCache != null) {
            // Check if the signed JWT info is already available in the cache.
            // If not, parse the token and populate the cache.
            Object cachedEntry = signedJWTParseCache.get(signature);
            if (cachedEntry != null) {
                signedJWTInfo = (SignedJWTInfo) cachedEntry;
            }
            if (signedJWTInfo == null || !signedJWTInfo.getToken().equals(accessToken)) {
                SignedJWT signedJWT = SignedJWT.parse(accessToken);
                JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
                signedJWTInfo = new SignedJWTInfo(accessToken, signedJWT, jwtClaimsSet);
                signedJWTParseCache.put(signature, signedJWTInfo);
            }
        } else {
            SignedJWT signedJWT = SignedJWT.parse(accessToken);
            JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
            signedJWTInfo = new SignedJWTInfo(accessToken, signedJWT, jwtClaimsSet);
        }
        return signedJWTInfo;
    }

    private void handleAuthFailure(MessageContext messageContext, OAuthSecurityException e) {

        messageContext.setProperty(SynapseConstants.ERROR_CODE, e.getErrorCode());
        messageContext.setProperty(SynapseConstants.ERROR_MESSAGE,
                OAuthConstants.getAuthenticationFailureMessage(e.getErrorCode()));
        messageContext.setProperty(SynapseConstants.ERROR_EXCEPTION, e);

        //Setting error description which will be available to the handler
        String errorDetail = OAuthConstants.getFailureMessageDetailDescription(e.getErrorCode(), e.getMessage());
        messageContext.setProperty(SynapseConstants.ERROR_DETAIL, errorDetail);

        // By default we send a 401 response back
        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext).
                getAxis2MessageContext();

        int status;
        if (e.getErrorCode() == OAuthConstants.API_AUTH_GENERAL_ERROR ||
                e.getErrorCode() == OAuthConstants.API_AUTH_MISSING_OPEN_API_DEF) {
            status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        } else if (e.getErrorCode() == OAuthConstants.API_AUTH_INCORRECT_API_RESOURCE ||
                e.getErrorCode() == OAuthConstants.API_AUTH_FORBIDDEN ||
                e.getErrorCode() == OAuthConstants.API_OAUTH_INVALID_AUDIENCES ||
                e.getErrorCode() == OAuthConstants.INVALID_SCOPE) {
            status = HttpStatus.SC_FORBIDDEN;
        } else {
            status = HttpStatus.SC_UNAUTHORIZED;
            Map<String, String> headers =
                    (Map) axis2MC.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
            if (headers != null) {
                headers.put(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"WSO2 API Manager\""
                        + " error=\"invalid_token\""
                        + ", error_description=\"The provided token is invalid\"");
            }
        }
        sendFault(messageContext, status);
    }

    public static void sendFault(MessageContext messageContext, int status) {
        messageContext.setTo(null);
        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext).
                getAxis2MessageContext();
        axis2MC.setProperty(NhttpConstants.HTTP_SC, status);
        Axis2Sender.sendBack(messageContext);
    }

    public String getApiElectedResource(MessageContext messageContext)
            throws OAuthSecurityException {

        API selectedApi = (API) messageContext.getProperty(RESTConstants.PROCESSED_API);

        if (selectedApi == null) {
            String msg = "Could not find the API for "
                    + messageContext.getProperty(RESTConstants.REST_FULL_REQUEST_PATH);;
            log.error(msg);
            throw new OAuthSecurityException(msg);
        }

        VersionStrategy versionStrategy = selectedApi.getVersionStrategy();
        String context = selectedApi.getContext();

        String path = ApiUtils.getFullRequestPath(messageContext);
        String subPath;
        if (versionStrategy.getVersionType().equals(VersionStrategyFactory.TYPE_URL)) {
            //for URL based
            //request --> http://{host:port}/context/version/path/to/resource
            subPath = path.substring(context.length() + versionStrategy.getVersion().length() + 1);
        } else {
            subPath = path.substring(context.length());
        }
        if ("".equals(subPath)) {
            subPath = "/";
        }
        messageContext.setProperty(RESTConstants.REST_SUB_REQUEST_PATH, subPath);

        Resource selectedResource = null;
        String resourceString;

        Resource[] selectedAPIResources = selectedApi.getResources();

        List<Resource> acceptableResourcesList = new LinkedList<>();

        String httpMethod = (String) ((Axis2MessageContext) messageContext).getAxis2MessageContext()
                .getProperty(Constants.Configuration.HTTP_METHOD);

        for (Resource resource : selectedAPIResources) {
            //If the requesting method is OPTIONS or if the Resource contains the requesting method
            if (RESTConstants.METHOD_OPTIONS.equals(httpMethod) &&
                    (resource.getMethods() != null && Arrays.asList(resource.getMethods()).contains(httpMethod))) {
                acceptableResourcesList.add(0, resource);
            } else if (RESTConstants.METHOD_OPTIONS.equals(httpMethod) ||
                    (resource.getMethods() != null && Arrays.asList(resource.getMethods()).contains(httpMethod))) {
                acceptableResourcesList.add(resource);
            }
        }

        Set<Resource> acceptableResources = new LinkedHashSet<>(acceptableResourcesList);

        if (!acceptableResources.isEmpty()) {
            for (RESTDispatcher dispatcher : ApiUtils.getDispatchers()) {
                Resource resource = dispatcher.findResource(messageContext, acceptableResources);
                if (resource != null && Arrays.asList(resource.getMethods()).contains(httpMethod)) {
                    selectedResource = resource;
                    if (selectedResource.getDispatcherHelper()
                            .getString() != null && !selectedResource.getDispatcherHelper().getString()
                            .contains("/*")) {
                        break;
                    }
                }
            }
        }

        if (selectedResource == null) {
            //No matching resource found.
            String msg = "Could not find matching resource for "
                    + messageContext.getProperty(RESTConstants.REST_FULL_REQUEST_PATH);;
            log.error(msg);
            return null;
        }

        resourceString = selectedResource.getDispatcherHelper().getString();
        messageContext.setProperty(RESTConstants.SELECTED_RESOURCE, resourceString);
        return resourceString;
    }

}
