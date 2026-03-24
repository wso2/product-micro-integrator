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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.axis2.Constants;
import org.apache.axis2.util.JavaUtils;
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
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.config.xml.rest.VersionStrategyFactory;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.endpoints.auth.AuthConstants;
import org.apache.synapse.rest.AbstractHandler;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.security.handler.oauth.jwt.JWTValidator;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.cache.Cache;

public class OAuth2AuthorizationHandler extends AbstractHandler implements ManagedLifecycle {

    private static final Log log = LogFactory.getLog(OAuth2AuthorizationHandler.class);

    // Handler properties
    private String authorizationHeader = HttpHeaders.AUTHORIZATION;
    private ArrayList<String> trustedIssuers;
    private String jwksEndpoint;
    private boolean removeOAuthHeadersFromOutMessage = true;
    private TokenRevocationHandler tokenRevocationHandler;
    private ArrayList<String> audience;
    private ArrayList<String> allowedAlgorithms;
    private Long maxIssuedAtAgeSeconds;

    // HTTP client related properties
    private Integer connectionTimeout;
    private Integer socketTimeout;
    private Integer connectionRequestTimeout;
    private Boolean enableProxy;
    private String proxyHost;
    private Integer proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private String proxyProtocol;
    private HttpClientConfiguration httpClientConfiguration;

    // mTLS related properties
    private boolean disableCNFValidation = false;
    private boolean enableClientCertificateValidation = false;
    private String clientCertificateHeader;
    private boolean clientCertificateEncode = true;
    private MTLSConfiguration mTLSConfiguration;

    @Override
    public void init(SynapseEnvironment synapseEnvironment) {

        if (trustedIssuers == null) {
            Object trustedIssuersConfig = ConfigParser.getParsedConfigs().get(OAuthConstants.TRUSTED_ISSUERS);
            if (trustedIssuersConfig instanceof ArrayList<?> rawList) {
                this.trustedIssuers = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String) {
                        this.trustedIssuers.add((String) item);
                    } else {
                        throw new IllegalArgumentException("Invalid configuration for trusted issuers. "
                                + "Expected list of strings but found element of type: " + item.getClass().getName());
                    }
                }
            } else if (trustedIssuersConfig != null) {
                throw new IllegalArgumentException("Invalid configuration for trusted issuers. Expected a list of "
                        + "strings in the format [\"https://idp1.com\", \"https://idp2.com\"] but found: "
                        + trustedIssuersConfig.getClass().getName());
            }
        }

        if (audience == null) {
            Object audienceConfig = ConfigParser.getParsedConfigs().get(OAuthConstants.EXPECTED_AUDIENCE);
            if (audienceConfig instanceof ArrayList<?> rawList) {
                this.audience = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String) {
                        this.audience.add((String) item);
                    } else {
                        throw new IllegalArgumentException("Invalid configuration for expected audience. "
                                + "Expected list of strings but found element of type: " + item.getClass().getName());
                    }
                }
            } else if (audienceConfig != null) {
                throw new IllegalArgumentException("Invalid configuration for expected audience. Expected a list of "
                        + "strings in the format [\"audience1\", \"audience2\"] but found: "
                        + audienceConfig.getClass().getName());
            }
        }

        if (allowedAlgorithms == null) {
            Object allowedAlgorithmsConfig = ConfigParser.getParsedConfigs().get(OAuthConstants.ALLOWED_ALGORITHMS);
            if (allowedAlgorithmsConfig instanceof ArrayList<?> rawList) {
                this.allowedAlgorithms = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String) {
                        this.allowedAlgorithms.add((String) item);
                    } else {
                        throw new IllegalArgumentException("Invalid configuration for allowed algorithms. "
                                + "Expected list of strings but found element of type: " + item.getClass().getName());
                    }
                }
            } else if (allowedAlgorithmsConfig != null) {
                throw new IllegalArgumentException("Invalid configuration for allowed algorithms. Expected a list of "
                        + "strings in the format [\"RS256\", \"RS512\"] but found: "
                        + allowedAlgorithmsConfig.getClass().getName());

            }
        }

        Object maxIssuedAtAgeSecondsConfig = ConfigParser.getParsedConfigs()
                .get(OAuthConstants.MAX_ISSUED_AT_AGE_SECONDS);
        if (maxIssuedAtAgeSeconds == null) {
            if (maxIssuedAtAgeSecondsConfig instanceof Number) {
                long parsed = ((Number) maxIssuedAtAgeSecondsConfig).longValue();
                if (parsed < 0) {
                    throw new IllegalArgumentException("maxIssuedAtAgeSeconds must be >= 0");
                }
                maxIssuedAtAgeSeconds = parsed;
            } else if (maxIssuedAtAgeSecondsConfig != null) {
                throw new IllegalArgumentException("Invalid configuration for maxIssuedAtAgeSeconds. Expected "
                        + "a non-negative integer but found: " + maxIssuedAtAgeSecondsConfig.getClass().getName());
            }

        }

        mTLSConfiguration = new MTLSConfiguration(disableCNFValidation,
                enableClientCertificateValidation, clientCertificateHeader, clientCertificateEncode);

        initializeHttpClientConfiguration();

    }

    private void initializeHttpClientConfiguration() {

        if (connectionTimeout == null) {
            String connectionTimeoutConfig = SynapsePropertiesLoader.getPropertyValue(
                    OAuthConstants.OAUTH_GLOBAL_CONNECTION_TIMEOUT, null);
            connectionTimeout = 3000;

            if (connectionTimeoutConfig != null && !connectionTimeoutConfig.isEmpty()) {
                try {
                    connectionTimeout = Integer.parseInt(connectionTimeoutConfig);
                } catch (NumberFormatException e) {
                    log.warn("Invalid configuration for http client connection timeout. Expected an integer "
                            + "but found: " + connectionTimeoutConfig + ". Defaulting to 3000 ms.");
                }
            }
        }

        if (socketTimeout == null) {
            String socketTimeoutConfig = SynapsePropertiesLoader.getPropertyValue(
                    OAuthConstants.OAUTH_GLOBAL_SOCKET_TIMEOUT, null);
            socketTimeout = 3000;

            if (socketTimeoutConfig != null && !socketTimeoutConfig.isEmpty()) {
                try {
                    socketTimeout = Integer.parseInt(socketTimeoutConfig);
                } catch (NumberFormatException e) {
                    log.warn("Invalid configuration for http client socket timeout. Expected an integer but found: "
                            + socketTimeoutConfig + ". Defaulting to 3000 ms.");
                }
            }
        }

        if (connectionRequestTimeout == null) {
            String requestTimeoutConfig = SynapsePropertiesLoader.getPropertyValue(
                    OAuthConstants.OAUTH_GLOBAL_CONNECTION_REQUEST_TIMEOUT, null);
            connectionRequestTimeout = 3000;

            if (requestTimeoutConfig != null && !requestTimeoutConfig.isEmpty()) {
                try {
                    connectionRequestTimeout = Integer.parseInt(requestTimeoutConfig);
                } catch (NumberFormatException e) {
                    log.warn("Invalid configuration for http client connection request timeout. Expected an integer "
                            + "but found: " + requestTimeoutConfig + ". Defaulting to 3000 ms.");
                }
            }
        }

        HttpClientConfiguration.Builder builder = new HttpClientConfiguration.Builder()
                .withConnectionParams(connectionTimeout, connectionRequestTimeout, socketTimeout);

        if (enableProxy == null) {
            String enableProxyConfig = SynapsePropertiesLoader.getPropertyValue(OAuthConstants.OAUTH_GLOBAL_PROXY_ENABLED,
                    "false");
            if (enableProxyConfig != null) {
                enableProxy = Boolean.parseBoolean(enableProxyConfig);
            } else {
                enableProxy = false;
            }
        }

        if (Boolean.TRUE.equals(enableProxy)) {
            if (proxyHost == null) {
                String proxyHostConfig = SynapsePropertiesLoader.getPropertyValue(OAuthConstants.OAUTH_GLOBAL_PROXY_HOST,
                        null);
                if (proxyHostConfig != null) {
                    proxyHost = proxyHostConfig;
                }
            }

            if (proxyPort == null) {
                String proxyPortConfig = SynapsePropertiesLoader.getPropertyValue(OAuthConstants.OAUTH_GLOBAL_PROXY_PORT,
                        null);
                if (proxyPortConfig != null) {
                    try {
                        proxyPort = Integer.parseInt(proxyPortConfig);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid configuration for proxy port. Expected an integer "
                                + "but found: " + proxyPortConfig + ".", e);
                    }
                }
            }

            if (proxyUsername == null) {
                String proxyUsernameConfig = SynapsePropertiesLoader
                        .getPropertyValue(OAuthConstants.OAUTH_GLOBAL_PROXY_USERNAME, null);
                if (proxyUsernameConfig != null) {
                    proxyUsername = proxyUsernameConfig;
                }
            }

            if (proxyPassword == null) {
                String proxyPasswordConfig = SynapsePropertiesLoader
                        .getPropertyValue(OAuthConstants.OAUTH_GLOBAL_PROXY_PASSWORD, null);
                if (proxyPasswordConfig != null) {
                    proxyPassword = proxyPasswordConfig;
                }
            }

            if (proxyProtocol == null) {
                String proxyProtocolConfig = SynapsePropertiesLoader
                        .getPropertyValue(OAuthConstants.OAUTH_GLOBAL_PROXY_PROTOCOL, null);
                if (proxyProtocolConfig != null) {
                    proxyProtocol = proxyProtocolConfig;
                }
            }

            builder.withProxy(proxyHost, proxyPort, proxyUsername, proxyPassword, proxyProtocol);

        }

        this.httpClientConfiguration = builder.build();
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean handleRequest(MessageContext messageContext) {

        try {
            String accessToken = performJwtFormatSanityCheck(messageContext);

            // Check for the matching resource before doing any authentication related processing.
            resolveMatchingResource(messageContext);

            SignedJWTInfo signedJWTInfo = getSignedJwtInfo(accessToken);

            validateJWTHeaderMetadata(signedJWTInfo.getSignedJWT());

            validateMandatoryClaimsPresence(signedJWTInfo.getJwtClaimsSet());

            if (log.isDebugEnabled()) {
                log.debug("Authentication started for JWT tokens");
            }

            JWTValidator jwtValidator = new JWTValidator(jwksEndpoint, trustedIssuers, audience, maxIssuedAtAgeSeconds,
                    tokenRevocationHandler, httpClientConfiguration, mTLSConfiguration);
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

        return true;
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

    public void setTokenRevocationHandler(String tokenRevocationHandler) {
        if (tokenRevocationHandler != null) {
            Class<?> clazz;
            try {
                clazz = JWTValidator.class.getClassLoader().loadClass(tokenRevocationHandler);
                if (!TokenRevocationHandler.class.isAssignableFrom(clazz)) {
                    throw new IllegalStateException("Configured class does not implement TokenRevocationHandler: "
                            + tokenRevocationHandler);
                }
                this.tokenRevocationHandler = (TokenRevocationHandler) clazz.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException
                     | InvocationTargetException e) {
                log.error("Failed to instantiate TokenRevocationHandler: " + tokenRevocationHandler, e);
                throw new IllegalStateException("Failed to initialize TokenRevocationHandler: "
                        + tokenRevocationHandler, e);
            }
        }
    }

    public void setTrustedIssuers(String trustedIssuers) {

        if (trustedIssuers != null && !trustedIssuers.isEmpty()) {
            this.trustedIssuers = new ArrayList<>(Arrays.asList(trustedIssuers.split("\\s*,\\s*")));
        }
    }

    public void setRemoveOAuthHeadersFromOutMessage(String removeOAuthHeadersFromOutMessage) {

        this.removeOAuthHeadersFromOutMessage = JavaUtils.isTrueExplicitly(removeOAuthHeadersFromOutMessage, true);
    }

    public Boolean getRemoveOAuthHeadersFromOutMessage() {

        return removeOAuthHeadersFromOutMessage;
    }

    public void setJwksEndpoint(String jwksEndpoint) {

        this.jwksEndpoint = jwksEndpoint;
    }

    public void setDisableCNFValidation(String disableCNFValidation) {

        this.disableCNFValidation = JavaUtils.isTrueExplicitly(disableCNFValidation, false);
    }

    public void setEnableClientCertificateValidation(String enableClientCertificateValidation) {

        this.enableClientCertificateValidation = JavaUtils.isTrueExplicitly(enableClientCertificateValidation,
                false);
    }

    public void setClientCertificateHeader(String clientCertificateHeader) {

        this.clientCertificateHeader = clientCertificateHeader;
    }

    public void setClientCertificateEncode(String clientCertificateEncode) {

        this.clientCertificateEncode = JavaUtils.isTrueExplicitly(clientCertificateEncode, true);
    }

    public void setAllowedAlgorithms(String allowedAlgorithms) {

        if (allowedAlgorithms != null && !allowedAlgorithms.isEmpty()) {
            this.allowedAlgorithms = new ArrayList<>(Arrays.asList(allowedAlgorithms.split("\\s*,\\s*")));
        }
    }

    public void setAudience(String audience) {

        if (audience != null && !audience.isEmpty()) {
            this.audience = new ArrayList<>(Arrays.asList(audience.split("\\s*,\\s*")));
        }
    }

    public void setMaxIssuedAtAgeSeconds(String maxIssuedAtAgeSeconds) {

        try {
            long parsed = Long.parseLong(maxIssuedAtAgeSeconds);
            if (parsed < 0) {
                throw new IllegalArgumentException("maxIssuedAtAgeSeconds must be >= 0");
            }
            this.maxIssuedAtAgeSeconds = parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid maxIssuedAtAgeSeconds: " + maxIssuedAtAgeSeconds, e);
        }
    }

    public void setConnectionTimeout(String connectionTimeout) {

        try {
            this.connectionTimeout = Integer.parseInt(connectionTimeout);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid connection timeout: " + connectionTimeout, e);
        }
    }

    public void setSocketTimeout(String socketTimeout) {

        try {
            this.socketTimeout = Integer.parseInt(socketTimeout);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid socket timeout: " + socketTimeout, e);
        }
    }

    public void setConnectionRequestTimeout(String connectionRequestTimeout) {

        try {
            this.connectionRequestTimeout = Integer.parseInt(connectionRequestTimeout);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid connection request timeout: " + connectionRequestTimeout, e);
        }
    }

    public void setEnableProxy(String enableProxy) {

        if (enableProxy != null && !enableProxy.isEmpty()) {
            this.enableProxy = Boolean.parseBoolean(enableProxy);
        }
    }

    public void setProxyHost(String proxyHost) {

        if (Boolean.TRUE.equals(this.enableProxy) && (proxyHost == null || proxyHost.isEmpty())) {
           throw new IllegalArgumentException("Proxy host cannot be empty if proxy is enabled.");
        }
        this.proxyHost = proxyHost;
    }

    public void setProxyPort(String proxyPort) {

        if (Boolean.TRUE.equals(this.enableProxy)) {
            try {
                this.proxyPort = Integer.parseInt(proxyPort);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid proxy port: " + proxyPort, e);
            }
        }
    }

    public void setProxyUsername(String proxyUsername) {

        if (Boolean.TRUE.equals(this.enableProxy)) {
            this.proxyUsername = proxyUsername;
        }
    }

    public void setProxyPassword(String proxyPassword) {

        if (Boolean.TRUE.equals(this.enableProxy) && !proxyPassword.isEmpty()) {
            SecretResolver secretResolver = SecretResolverFactory.create(new Properties() {{
                setProperty(AuthConstants.PROXY_PASSWORD, proxyPassword);
            }});
            this.proxyPassword = MiscellaneousUtil.resolve(proxyPassword, secretResolver);
        }
    }

    public void setProxyProtocol(String proxyProtocol) {

        if (Boolean.TRUE.equals(this.enableProxy) && (proxyProtocol == null || proxyProtocol.isEmpty())) {
            throw new IllegalArgumentException("Proxy protocol cannot be empty if proxy is enabled.");
        }
        this.proxyProtocol = proxyProtocol;
    }

    /**
     * Performs a basic sanity check on the authorization header and extracts the JWT.
     * This ensures the header is present, uses the Bearer scheme, and has the
     * physical structure of a JWT (3 parts separated by 2 dots).
     *
     * @param messageContext The Axis2 message context.
     * @return The extracted JWT string if it passes the format sanity check.
     * @throws OAuthSecurityException If any structural validation fails.
     */
    public String performJwtFormatSanityCheck(MessageContext messageContext) throws OAuthSecurityException {

        // 1. Get Transport Headers
        Map headers = (Map) ((Axis2MessageContext) messageContext).getAxis2MessageContext()
                .getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);

        if (headers == null) {
            log.error("No transport headers found in the message context.");
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_MISSING_CREDENTIALS,
                    OAuthConstants.API_AUTH_MISSING_CREDENTIALS_MESSAGE);
        }

        // 2. Get Authorization Header
        String authHeader = (String) headers.get(authorizationHeader);
        if (StringUtils.isEmpty(authHeader)) {
            log.error("Expected authorization header '" + authorizationHeader + "' is missing or empty.");
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_MISSING_CREDENTIALS,
                    OAuthConstants.API_AUTH_MISSING_CREDENTIALS_MESSAGE);
        }

        // 3. Split Scheme and Token
        String[] elements = authHeader.trim().split("\\s+", 2);
        if (elements.length < 2) {
            log.error("Invalid authorization header format. Missing token segment.");
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
        }

        String authScheme = elements[0];
        String accessToken = elements[1];

        // 4. Scheme Sanity Check (Must be Bearer)
        if (!OAuthConstants.BEARER.equalsIgnoreCase(authScheme)) {
            log.error("Unsupported authentication scheme: " + authScheme);
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
        }

        // 5. JWT Structural Sanity Check (Check for exactly 2 dots)
        if (StringUtils.isEmpty(accessToken)) {
            log.error("The extracted access token is empty.");
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
        }

        // Using StringUtils to count dots is the most reliable sanity check
        int dotCount = StringUtils.countMatches(accessToken, ".");
        if (dotCount != 2) {
            log.error("JWT physical sanity check failed: Expected 2 dots, found " + dotCount);
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
        }

        return accessToken;
    }

    /**
     * Validates the JWT header claims (typ and alg) before signature verification.
     * Per RFC 9068, the typ MUST be 'at+jwt' and the alg MUST be a strong asymmetric one.
     *
     * @param jwt The parsed SignedJWT object.
     * @throws OAuthSecurityException If the header is invalid or uses a forbidden algorithm.
     */
    public void validateJWTHeaderMetadata(SignedJWT jwt) throws OAuthSecurityException {
        JWSHeader header = jwt.getHeader();

        // 1. Check 'typ' (Type) - REQUIRED by RFC 9068
        // Note: Some servers send 'application/at+jwt', some send 'at+jwt'
        JOSEObjectType typ = header.getType();
        if (typ == null || !(OAuthConstants.JWT_TYPE_AT_JWT.equals(typ.getType())
                || OAuthConstants.MEDIA_TYPE_JWT_ACCESS_TOKEN.equals(typ.getType()))) {
            log.error("Invalid JWT type. Expected 'at+jwt' or 'application/at+jwt', found: " + typ);
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE,
                    "Invalid token type");
        }

        // 2. Check 'alg' (Algorithm) - Protects against 'alg: none' and Key Confusion
        JWSAlgorithm alg = header.getAlgorithm();
        if (!isSupportedAlgorithm(alg)) {
            log.error("Unsupported or weak algorithm: " + alg);
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    "Unsuitable cryptographic algorithm");
        }

        log.debug("Header metadata validation successful.");
    }

    private boolean isSupportedAlgorithm(JWSAlgorithm alg) {

        if (alg == null) {
            log.error("Missing 'alg' in JWT header.");
            return false;
        }
        if (allowedAlgorithms != null && !allowedAlgorithms.isEmpty() && !allowedAlgorithms.contains(alg.getName())) {
            log.error("Algorithm " + alg.getName() + " is not in the list of allowed algorithms.");
            return false;
        }
        // By default, only allow strong asymmetric algorithms (RS256, etc.)
        return JWSAlgorithm.Family.RSA.contains(alg);
    }

    /**
     * Validates that all mandatory claims required by RFC 9068 are present
     * and logically valid.
     *
     * @param claims The JWTClaimsSet extracted from the validated signed JWT.
     * @throws OAuthSecurityException If any mandatory claim is missing or invalid.
     */
    public void validateMandatoryClaimsPresence(JWTClaimsSet claims)
            throws OAuthSecurityException {

        // 1. Check for Existence (The "Presence" Check)
        if (StringUtils.isEmpty(claims.getIssuer())) {
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    "Missing mandatory 'iss' claim");
        }
        if (claims.getExpirationTime() == null) {
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    "Missing mandatory 'exp' claim");
        }
        if (claims.getAudience() == null || claims.getAudience().isEmpty()) {
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    "Missing mandatory 'aud' claim");
        }
        if (StringUtils.isEmpty(claims.getSubject())) {
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    "Missing mandatory 'sub' claim");
        }
        if (StringUtils.isEmpty(claims.getJWTID())) {
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    "Missing mandatory 'jti' claim");
        }
        if (claims.getIssueTime() == null) {
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    "Missing mandatory 'iat' claim");
        }
        log.debug("Mandatory claims validation passed.");
    }

    /**
     * Get signed JWT info for access token.
     *
     * @param accessToken Access token
     * @return SignedJWTInfo
     * @throws OAuthSecurityException if an error occurs
     */
    private SignedJWTInfo getSignedJwtInfo(String accessToken) throws OAuthSecurityException {

        String signature = accessToken.split("\\.")[2];
        SignedJWTInfo signedJWTInfo = null;

        try {
            // Check if a cache is configured for signed JWT parsing.
            // If so, try to get the signed JWT info from the cache using the token signature as the key.
            Cache signedJWTParseCache = CacheProvider.getSignedJWTParseCache();
            if (signedJWTParseCache != null) {
                // Check if the signed JWT info is already available in the cache.
                // If not, parse the token and populate the cache.
                Object cachedEntry = signedJWTParseCache.get(signature);
                if (cachedEntry != null) {
                    SignedJWTInfo cached = (SignedJWTInfo) cachedEntry;
                    signedJWTInfo = new SignedJWTInfo(accessToken, cached.getSignedJWT(), cached.getJwtClaimsSet(),
                            cached.getValidationStatus(), cached.getClientCertificate(),
                            cached.getClientCertificateHash());
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
        } catch (ParseException e) {
            log.error("Error while parsing the access token.", e);
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INVALID_CREDENTIALS,
                    OAuthConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE, e);
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
                headers.put(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"WSO2 Micro Integrator\""
                        + " error=\"invalid_token\""
                        + ", error_description=\"The provided token is invalid\"");
            }
        }
        sendFault(messageContext, status);
    }

    private static void sendFault(MessageContext messageContext, int status) {
        messageContext.setTo(null);
        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext).
                getAxis2MessageContext();
        axis2MC.setProperty(NhttpConstants.HTTP_SC, status);
        Axis2Sender.sendBack(messageContext);
    }

    private void resolveMatchingResource(MessageContext messageContext)
            throws OAuthSecurityException {

        API selectedApi = (API) messageContext.getProperty(RESTConstants.PROCESSED_API);

        if (selectedApi == null) {
            String msg = "Could not find the API for "
                    + messageContext.getProperty(RESTConstants.REST_FULL_REQUEST_PATH);
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
        if (subPath.isEmpty()) {
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
                String[] resourceMethods = resource != null ? resource.getMethods() : null;
                if (resource != null && (resourceMethods == null
                        || Arrays.asList(resourceMethods).contains(httpMethod))) {
                    selectedResource = resource;
                    String dispatcherStr = selectedResource.getDispatcherHelper() != null
                            ? selectedResource.getDispatcherHelper().getString() : null;
                    if (dispatcherStr != null && !dispatcherStr.contains("/*")) {
                        break;
                    }
                }
            }
        }

        if (selectedResource == null) {
            //No matching resource found.
            log.error("No matching resource found for request path: "
                    + messageContext.getProperty(RESTConstants.REST_FULL_REQUEST_PATH));
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INCORRECT_API_RESOURCE,
                    OAuthConstants.API_AUTH_INCORRECT_API_RESOURCE_MESSAGE);
        }

        if (selectedResource.getDispatcherHelper() == null) {
            throw new OAuthSecurityException(OAuthConstants.API_AUTH_INCORRECT_API_RESOURCE,
                    OAuthConstants.API_AUTH_INCORRECT_API_RESOURCE_MESSAGE);
        }
        resourceString = selectedResource.getDispatcherHelper().getString();
        messageContext.setProperty(RESTConstants.SELECTED_RESOURCE, resourceString);
    }

}
