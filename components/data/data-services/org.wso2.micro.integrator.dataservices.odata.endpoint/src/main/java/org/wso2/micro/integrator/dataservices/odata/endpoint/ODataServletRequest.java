/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.dataservices.odata.endpoint;

import javax.ws.rs.core.MediaType;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.collections4.iterators.IteratorEnumeration;
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.util.MessageHelper;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ODataServletRequest implements HttpServletRequest {

    private MessageContext axis2MessageContext;
    private static final String DEFAULT_CONTEXT_PATH = "/odata";
    private final Map axis2TransportHeaders;

    ODataServletRequest(MessageContext messageContext) {
        this.axis2MessageContext = messageContext;
        this.axis2TransportHeaders = MessageHelper.getClonedTransportHeaders(messageContext);
    }

    @Override
    public String getAuthType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cookie[] getCookies() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getDateHeader(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHeader(String s) {
        return (String)axis2TransportHeaders.get(s);
    }

    @Override
    public Enumeration getHeaders(String s) {
        List headerValues = new ArrayList();
        headerValues.add(axis2TransportHeaders.get(s));
        return new IteratorEnumeration(headerValues.iterator());
    }

    @Override
    public Enumeration getHeaderNames() {
        return new IteratorEnumeration(axis2TransportHeaders.keySet().iterator());
    }

    @Override
    public int getIntHeader(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getMethod() {
        return (String)axis2MessageContext.getProperty("HTTP_METHOD");
    }

    @Override
    public String getPathInfo() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPathTranslated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContextPath() {
        return DEFAULT_CONTEXT_PATH;
    }

    @Override
    public String getQueryString() {
        String queryString = null;
        String url = getRequestURI();
        int queryStringPosition = url.indexOf('?');
        if (queryStringPosition != -1) {
            queryString = url.substring(queryStringPosition + 1);
        }
        return queryString;
    }

    @Override
    public String getRemoteUser() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUserInRole(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Principal getUserPrincipal() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRequestedSessionId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRequestURI() {
        return axis2MessageContext.getProperty("TransportInURL").toString();
    }

    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        url.append(axis2MessageContext.getProperty("SERVICE_PREFIX"));
        String serviceURL = (String) axis2MessageContext.getProperty("TransportInURL");
        int queryStringPosition =  serviceURL.indexOf('?');
        if (queryStringPosition != -1) {
            url.append(serviceURL, 1, serviceURL.indexOf('?'));
        } else {
            url.append(serviceURL.substring(1));
        }
        return url;
    }

    @Override
    public String getServletPath() {
        return StringUtils.EMPTY;
    }

    @Override
    public HttpSession getSession(boolean b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpSession getSession() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean authenticate(HttpServletResponse httpServletResponse) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void login(String s, String s1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void logout() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Part> getParts() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Part getPart(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getAttribute(String s) {
        return null;
    }

    @Override
    public Enumeration getAttributeNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCharacterEncoding() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCharacterEncoding(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getContentLength() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContentType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletInputStream getInputStream() throws AxisFault {
        OMElement content = axis2MessageContext.getEnvelope().getBody().getFirstElement();
        if (content != null) {
            String contentType = (String) axis2MessageContext.getProperty(Constants.Configuration.CONTENT_TYPE);
            if (contentType.contains(MediaType.APPLICATION_JSON)) {
                ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                JsonUtil.writeAsJson(axis2MessageContext, byteOutputStream);
                return new RequestServletInputStream(new ByteArrayInputStream(byteOutputStream.toByteArray()));
            } else if (contentType.contains(MediaType.APPLICATION_XML)) {
                return new RequestServletInputStream(new ByteArrayInputStream(content.toString().getBytes()));
            }
        }
        return null;
    }

    private class RequestServletInputStream extends ServletInputStream {

        InputStream sourceStream;

        public RequestServletInputStream(InputStream sourceStream) {
            this.sourceStream = sourceStream;
        }

        @Override
        public int read() throws IOException {
            return this.sourceStream.read();
        }

        @Override
        public void close() throws IOException {
            super.close();
            this.sourceStream.close();
        }
    }

    @Override
    public String getParameter(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration getParameterNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getParameterValues(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map getParameterMap() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getProtocol() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getScheme() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServerName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getServerPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRemoteAddr() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRemoteHost() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(String s, Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAttribute(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Locale getLocale() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration getLocales() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSecure() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRealPath(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getRemotePort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLocalName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLocalAddr() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getLocalPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletContext getServletContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAsyncStarted() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAsyncSupported() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DispatcherType getDispatcherType() {
        throw new UnsupportedOperationException();
    }

    public MessageContext getAxis2MessageContext() {
        return axis2MessageContext;
    }

}
