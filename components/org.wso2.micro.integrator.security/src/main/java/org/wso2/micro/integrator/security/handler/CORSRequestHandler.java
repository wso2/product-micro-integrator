/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.security.handler;

import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpStatus;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.api.cors.SynapseCORSConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.rest.AbstractHandler;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CORSRequestHandler extends AbstractHandler implements ManagedLifecycle {

    private static final Log log = LogFactory.getLog(CORSRequestHandler.class);
    private String apiImplementationType;
    private String allowHeaders;
    private String allowCredentials;
    private Set<String> allowedOrigins;
    private String exposeHeaders;
    private boolean initializeHeaderValues;
    private String allowedMethods;
    private List<String> allowedMethodList;
    private boolean allowCredentialsEnabled;
    private String authorizationHeader;
    private String apiKeyHeader;

    public void init(SynapseEnvironment synapseEnvironment) {
        // Initialize header values from properties if not already set
        if (!initializeHeaderValues) {
            initializeHeaders();
            initializeHeaderValues = true;
        }
    }

    @Override
    public void destroy() {

    }

    void initializeHeaders() {

        // This method can be used to set defaults if needed
        if (allowHeaders == null) {
            allowHeaders = "authorization,Access-Control-Allow-Origin,Content-Type,SOAPAction,apikey,Internal-Key";
        }
        if (allowedOrigins == null) {
            allowedOrigins = new HashSet<String>(Arrays.asList("*"));
        }
        if (allowedMethods == null) {
            allowedMethods = "GET,PUT,POST,DELETE,PATCH,OPTIONS";
            allowedMethodList = Arrays.asList(allowedMethods.split(","));
        }
        if (authorizationHeader == null) {
            authorizationHeader = "Authorization";
        }
        if (apiKeyHeader == null) {
            apiKeyHeader = "ApiKey";
        }
    }

    public boolean handleRequest(MessageContext messageContext) {

        if (log.isDebugEnabled()) {
            log.debug("CORSRequestHandler: handleRequest called");
        }
        if (SynapseCORSConfiguration.getInstance().isEnabled()) {
            // if global CORS is enabled, per API CORS configuration is not needed
            if (log.isDebugEnabled()) {
                log.debug("CORSRequestHandler: Using global CORS configuration");
            }
            return true; // Continue processing with global CORS configuration
        }
        // Handle preflight OPTIONS request
        org.apache.axis2.context.MessageContext axis2MsgCtx =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        Map headers = (Map) axis2MsgCtx.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        String httpMethod = (String) axis2MsgCtx.getProperty(Constants.Configuration.HTTP_METHOD);
        String origin = headers != null ? (String) headers.get("Origin") : null;

        if (RESTConstants.METHODS.OPTIONS.toString().equalsIgnoreCase(httpMethod)) {
            String allowedOrigin = getAllowedOrigins(origin);
            if (allowedOrigin != null) {
                axis2MsgCtx.setProperty(PassThroughConstants.HTTP_SC, HttpStatus.SC_OK);
                axis2MsgCtx.setProperty(PassThroughConstants.NO_ENTITY_BODY, Boolean.TRUE);
                axis2MsgCtx.setProperty(SynapseConstants.RESPONSE, "true");
                if (headers == null) {
                    headers = new java.util.HashMap();
                }
                headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN, allowedOrigin);
                headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_METHODS, allowedMethods);
                headers.put(RESTConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_HEADERS, allowHeaders);
                headers.put("Access-Control-Allow-Credentials", allowCredentials);
                axis2MsgCtx.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, headers);
                return false; // Stop further processing for preflight
            }
        }

//        messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_ALLOW_METHODS, allowedMethods);
//        messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN, allowedOrigins);
//        messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_ALLOW_HEADERS,
//                allowHeaders);

        return true;
    }

    public boolean handleResponse(MessageContext messageContext) {

        if (log.isDebugEnabled()) {
            log.debug("CORSRequestHandler: handleResponse called");
        }
        if (SynapseCORSConfiguration.getInstance().isEnabled()) {
            // if global CORS is enabled, per API CORS configuration is not needed
            if (log.isDebugEnabled()) {
                log.debug("CORSRequestHandler: Using global CORS configuration for response");
            }
            return true; // Continue processing with global CORS configuration
        }
        org.apache.axis2.context.MessageContext axis2MsgCtx =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        Map headers = (Map) axis2MsgCtx.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        String origin = headers != null ? (String) headers.get("Origin") : null;
        String allowedOrigin = getAllowedOrigins(origin);
        if (allowedOrigin != null) {
            if (headers == null) {
                headers = new java.util.HashMap();
            }
            headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN, allowedOrigin);
            headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_METHODS, allowedMethods);
            headers.put(RESTConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_HEADERS, allowHeaders);
            headers.put("Access-Control-Allow-Credentials", allowCredentials);
            if (exposeHeaders != null) {
                headers.put("Access-Control-Expose-Headers", exposeHeaders);
            }
            axis2MsgCtx.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, headers);
        }
        return true;
    }

    public String getAllowHeaders() {
        return allowHeaders;
    }

    public void setAllowHeaders(String allowHeaders) {
        this.allowHeaders = allowHeaders;
    }

    public String getAllowedOrigins(String origin) {
        if (allowedOrigins.contains("*")) {
            return "*";
        } else if (allowedOrigins.contains(origin)) {
            return origin;
        } else if (origin != null) {
            for (String allowedOrigin : allowedOrigins) {
                if (allowedOrigin.contains("*")) {
                    Pattern pattern = Pattern.compile(allowedOrigin.replace("*", ".*"));
                    Matcher matcher = pattern.matcher(origin);
                    if (matcher.find()) {
                        return origin;
                    }
                }
            }
        }
        return null;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = new HashSet<String>(Arrays.asList(allowedOrigins.split(",")));
    }

    public String getApiImplementationType() {
        return apiImplementationType;
    }

    public void setApiImplementationType(String apiImplementationType) {
        this.apiImplementationType = apiImplementationType;
    }

    // For backward compatibility with 1.9.0 since the property name is inline
    public String getInline() {
        return getApiImplementationType();
    }

    // For backward compatibility with 1.9.0 since the property name is inline
    public void setInline(String inlineType) {
        setApiImplementationType(inlineType);
    }

    public String isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(String allowCredentials) {
        this.allowCredentialsEnabled = Boolean.parseBoolean(allowCredentials);
        this.allowCredentials = allowCredentials;
    }

    public String getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(String allowedMethods) {
        this.allowedMethods = allowedMethods;
        if (allowedMethods != null) {
            allowedMethodList = Arrays.asList(allowedMethods.split(","));
        }
    }

    public String getAuthorizationHeader() {
        return authorizationHeader;
    }

    public void setAuthorizationHeader(String authorizationHeader) {
        this.authorizationHeader = authorizationHeader;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }
}
