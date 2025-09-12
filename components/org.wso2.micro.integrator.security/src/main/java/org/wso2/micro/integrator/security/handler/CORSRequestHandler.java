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
import org.apache.synapse.api.API;
import org.apache.synapse.api.ApiUtils;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.cors.SynapseCORSConfiguration;
import org.apache.synapse.api.dispatch.RESTDispatcher;
import org.apache.synapse.config.xml.rest.VersionStrategyFactory;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.rest.AbstractHandler;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.rest.RESTUtils;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CORSRequestHandler extends AbstractHandler implements ManagedLifecycle {

    private static final Log log = LogFactory.getLog(CORSRequestHandler.class);

    private String allowHeaders;
    private String allowCredentials;
    private boolean allowCredentialsEnabled;
    private Set<String> allowedOrigins;
    private String allowedMethods;
    private List<String> allowedMethodList;
    private String maxAge;

    public static final String METHOD_NOT_FOUND_ERROR_MSG = "Method not allowed for given API resource";
    public static final String RESOURCE_NOT_FOUND_ERROR_MSG = "No matching resource found for given API Request";

    public void init(SynapseEnvironment synapseEnvironment) {

    }

    @Override
    public void destroy() {

    }

    public boolean handleRequest(MessageContext messageContext) {

        if (log.isDebugEnabled()) {
            log.debug("CORSRequestHandler: handleRequest called");
        }

        // If CORS is enabled per API, skip global CORS handler
        messageContext.setProperty(RESTConstants.INTERNAL_CORS_PER_API_ENABLED, Boolean.TRUE);

        String httpMethod = (String) ((Axis2MessageContext) messageContext).getAxis2MessageContext()
                .getProperty(Constants.Configuration.HTTP_METHOD);
        API selectedApi = getSelectedAPI(messageContext);
        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext)
                .getAxis2MessageContext();
        Map<String, String> headers = (Map<String, String>)
                axis2MC.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        String corsRequestMethod = (String) headers.get("Access-Control-Request-Method");

        Resource selectedResource = null;
        setSubRequestPath(selectedApi, messageContext);

        if (selectedApi != null) {
            if ((messageContext.getProperty(RESTConstants.SELECTED_RESOURCE) != null)) {
                selectedResource = getSelectedResource(messageContext, httpMethod, corsRequestMethod);
            } else {
                Resource[] allAPIResources = selectedApi.getResources();
                Set<Resource> acceptableResources
                        = getAcceptableResources(allAPIResources, httpMethod, corsRequestMethod);

                if (!acceptableResources.isEmpty()) {
                    for (RESTDispatcher dispatcher : RESTUtils.getDispatchers()) {
                        Resource resource = dispatcher.findResource(messageContext, acceptableResources);
                        if (resource != null) {
                            selectedResource = resource;
                            if (selectedResource.getDispatcherHelper()
                                    .getString() != null && !selectedResource.getDispatcherHelper().getString()
                                    .contains("/*")) {
                                break;
                            }
                        }
                    }
                    if (selectedResource == null) {
                        handleResourceNotFound(messageContext, Arrays.asList(allAPIResources));
                        return false;
                    }
                }
                //If no acceptable resources are found
                else {
                    //We're going to send a 405 or a 404. Run the following logic to determine which.
                    handleResourceNotFound(messageContext, Arrays.asList(allAPIResources));
                    return false;
                }

            }
            //No matching resource found
            if (selectedResource == null) {
                //Respond with a 404
                onResourceNotFoundError(messageContext, HttpStatus.SC_NOT_FOUND, RESOURCE_NOT_FOUND_ERROR_MSG);
                return false;
            }
        }

        // Handle CORS preflight OPTIONS request
        if (RESTConstants.METHOD_OPTIONS.equalsIgnoreCase(httpMethod)) {

            //If the OPTIONS method is explicity specified in the resource
            if (Arrays.asList(selectedResource.getMethods()).contains(RESTConstants.METHOD_OPTIONS)) {
                //We will not handle the CORS headers, let the back-end do it.
                return true;
            }
            setCORSHeaders(messageContext, selectedResource, true);
            send(messageContext, HttpStatus.SC_OK);
            return false;
        }
        setCORSHeaders(messageContext, selectedResource, false);
        return true;
    }

    public boolean handleResponse(MessageContext messageContext) {

        if (log.isDebugEnabled()) {
            log.debug("CORSRequestHandler: handleResponse called");
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

    public String getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(String allowedMethods) {
        this.allowedMethods = allowedMethods;
        if (allowedMethods != null) {
            allowedMethodList = Arrays.asList(allowedMethods.split(","));
        }
    }

    public List<String> getAllowedMethodList() {
        return allowedMethodList;
    }

    public String getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(String maxAge) {
        this.maxAge = maxAge;
    }

    public String getAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(String allowCredentials) {
        this.allowCredentialsEnabled = Boolean.parseBoolean(allowCredentials);
        this.allowCredentials = allowCredentials;
    }

    private API getSelectedAPI(MessageContext messageContext) {

        Object apiObject = messageContext.getProperty(RESTConstants.PROCESSED_API);
        if (apiObject != null) {
            return (API) apiObject;
        } else {
            String apiName = (String) messageContext.getProperty(RESTConstants.SYNAPSE_REST_API);
            return messageContext.getConfiguration().getAPI(apiName);
        }
    }

    /**
     * Obtain the selected resource from the message context for CORSRequestHandler.
     *
     * @return selected resource
     */
    public Resource getSelectedResource(MessageContext messageContext,
                                               String httpMethod, String corsRequestMethod) {
        Resource selectedResource = null;
        Resource resource = (Resource) messageContext.getProperty(RESTConstants.SELECTED_RESOURCE);
        String [] resourceMethods = resource.getMethods();
        if ((RESTConstants.METHOD_OPTIONS.equals(httpMethod) && resourceMethods != null
                && Arrays.asList(resourceMethods).contains(corsRequestMethod))
                || (resourceMethods != null && Arrays.asList(resourceMethods).contains(httpMethod))) {
            selectedResource = resource;
        }
        return selectedResource;
    }

    /**
     * Select acceptable resources from the set of all resources based on requesting methods.
     *
     * @return set of acceptable resources
     */
    public Set<Resource> getAcceptableResources(Resource[] allAPIResources,
                                                       String httpMethod, String corsRequestMethod) {
        List<Resource> acceptableResourcesList = new LinkedList<>();
        for (Resource resource : allAPIResources) {
            //If the requesting method is OPTIONS or if the Resource contains the requesting method
            if (resource.getMethods() != null && Arrays.asList(resource.getMethods()).contains(httpMethod) &&
                    RESTConstants.METHOD_OPTIONS.equals(httpMethod)) {
                acceptableResourcesList.add(0, resource);
            } else if ((RESTConstants.METHOD_OPTIONS.equals(httpMethod) && resource.getMethods() != null &&
                    Arrays.asList(resource.getMethods()).contains(corsRequestMethod)) ||
                    (resource.getMethods() != null && Arrays.asList(resource.getMethods()).contains(httpMethod))) {
                acceptableResourcesList.add(resource);
            }
        }
        return new LinkedHashSet<>(acceptableResourcesList);
    }

    private void handleResourceNotFound(MessageContext messageContext, List<Resource> allAPIResources) {

        Resource uriMatchingResource = null;

        for (RESTDispatcher dispatcher : RESTUtils.getDispatchers()) {
            uriMatchingResource = dispatcher.findResource(messageContext, allAPIResources);
            //If a resource with a matching URI was found.
            if (uriMatchingResource != null) {
                onResourceNotFoundError(messageContext, HttpStatus.SC_METHOD_NOT_ALLOWED, METHOD_NOT_FOUND_ERROR_MSG);
                return;
            }
        }

        //If a resource with a matching URI was not found.
        //Respond with a 404.
        onResourceNotFoundError(messageContext, HttpStatus.SC_NOT_FOUND, RESOURCE_NOT_FOUND_ERROR_MSG);
    }

    private void onResourceNotFoundError(MessageContext messageContext, int statusCode, String errorMessage) {

        org.apache.axis2.context.MessageContext msgCtx =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        msgCtx.setProperty(PassThroughConstants.HTTP_SC, statusCode);
        msgCtx.removeProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        msgCtx.setProperty("NIO-ACK-Requested", true);
    }

    /**
     * This method used to set CORS headers into message context
     *
     * @param messageContext   message context for set cors headers as properties
     * @param selectedResource resource according to the request
     */
    public void setCORSHeaders(MessageContext messageContext,
                               Resource selectedResource, boolean updateTransportHeaders) {

        org.apache.axis2.context.MessageContext axis2MC =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        Map<String, String> headers =
                (Map) axis2MC.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        String requestOrigin = headers.get(RESTConstants.CORS_HEADER_ORIGIN);
        String allowedOrigin = getAllowedOrigins(requestOrigin);

        //Set the access-Control-Allow-Credentials header in the response only if it is specified to true in the api-manager configuration
        //and the allowed origin is not the wildcard (*)
        if (allowCredentialsEnabled && !"*".equals(allowedOrigin)) {
            messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_ALLOW_CREDENTIALS, "true");
        }

        messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN, allowedOrigin);

        String allowedMethods;
        StringBuffer allowedMethodsBuffer = new StringBuffer(20);
        if (selectedResource != null) {
            String[] methods = selectedResource.getMethods();
            for (String method : methods) {
                if (this.allowedMethodList.contains(method)) {
                    allowedMethodsBuffer.append(method).append(',');
                }
            }
            allowedMethods = allowedMethodsBuffer.toString();
            if (allowedMethods.endsWith(",")) {
                allowedMethods = allowedMethods.substring(0, allowedMethods.length() - 1);
            }
        } else {
            allowedMethods = this.allowedMethods;
        }
        if ("*".equals(allowHeaders)) {
            String localHeaders = headers.get("Access-Control-Request-Headers");
            messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_ALLOW_HEADERS, localHeaders);
        } else {
            messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_ALLOW_HEADERS, allowHeaders);
        }
        messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_ALLOW_METHODS, allowedMethods);
        if (maxAge != null) {
            messageContext.setProperty(RESTConstants.INTERNAL_CORS_HEADER_ACCESS_CTL_MAX_AGE, maxAge);
        }

        if (updateTransportHeaders) {
            headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_METHODS, allowedMethods);
            headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN, allowedOrigin);
            headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_HEADERS,
                    allowHeaders);
            if (allowCredentialsEnabled && !"*".equals(allowedOrigin)) {
                headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_CREDENTIALS, "true");
            }
            if (maxAge != null) {
                headers.put(RESTConstants.CORS_HEADER_ACCESS_CTL_MAX_AGE, maxAge);
            }
        }
    }

    private void setSubRequestPath(API api, MessageContext synCtx) {

        Object requestSubPath = synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH);
        if (requestSubPath != null) {
            return;
        }
        String subPath = null;
        String path = ApiUtils.getFullRequestPath(synCtx);
        if (api != null) {
            if (VersionStrategyFactory.TYPE_URL.equals(api.getVersionStrategy().getVersionType())) {
                subPath = path.substring(
                        api.getContext().length() + api.getVersionStrategy().getVersion().length() + 1);
            } else {
                subPath = path.substring(api.getContext().length());
            }
        }
        if (subPath != null && subPath.isEmpty()) {
            subPath = "/";
        }
        synCtx.setProperty(RESTConstants.REST_SUB_REQUEST_PATH, subPath);
    }

    public void send(MessageContext messageContext, int status) {

        org.apache.axis2.context.MessageContext axis2MC =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        axis2MC.setProperty(NhttpConstants.HTTP_SC, status);
        messageContext.setResponse(true);
        messageContext.setProperty(SynapseConstants.RESPONSE, "true");
        messageContext.setTo(null);
        axis2MC.removeProperty(Constants.Configuration.CONTENT_TYPE);
        Axis2Sender.sendBack(messageContext);
    }
}
