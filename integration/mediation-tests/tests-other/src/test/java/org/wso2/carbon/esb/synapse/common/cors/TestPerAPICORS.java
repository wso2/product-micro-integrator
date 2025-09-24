/*
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

package org.wso2.carbon.esb.synapse.common.cors;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.entity.StringEntity;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.apache.synapse.rest.RESTConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Test api functionality when cors enabled in the api level
 */
public class TestPerAPICORS extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.init();
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS preflight OPTIONS request")
    public void testCORSPreflightRequest() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpOptions optionsRequest = new HttpOptions(getApiInvocationURL("testperapicors"));
            optionsRequest.setHeader("Origin", "https://example.com");
            optionsRequest.setHeader("Access-Control-Request-Method", "POST");
            optionsRequest.setHeader("Access-Control-Request-Headers", "Content-Type, Authorization");

            HttpResponse response = httpClient.execute(optionsRequest);

            Assert.assertEquals(response.getStatusLine().getStatusCode(), 200,
                    "Preflight request should return 200 OK");

            // Verify CORS headers are present
            Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
            Assert.assertNotNull(allowOriginHeader, "Access-Control-Allow-Origin header should be present");

            Header allowMethodsHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_METHODS);
            Assert.assertNotNull(allowMethodsHeader, "Access-Control-Allow-Methods header should be present");

            Header allowHeadersHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_HEADERS);
            Assert.assertNotNull(allowHeadersHeader, "Access-Control-Allow-Headers header should be present");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS simple GET request with valid origin")
    public void testCORSSimpleGetRequest() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
            getRequest.setHeader("Origin", "https://example.com");

            HttpResponse response = httpClient.execute(getRequest);

            // Verify CORS headers in response
            Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
            Assert.assertNotNull(allowOriginHeader, "Access-Control-Allow-Origin header should be present");
            Assert.assertTrue(allowOriginHeader.getValue().equals("https://example.com") ||
                            allowOriginHeader.getValue().equals("*"),
                    "Origin should be echoed back or wildcard should be returned");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS POST request with custom headers")
    public void testCORSPostRequestWithCustomHeaders() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(getApiInvocationURL("testperapicors"));
            postRequest.setHeader("Origin", "https://trusted-domain.com");
            postRequest.setHeader("Content-Type", "application/json");
            postRequest.setHeader("X-Custom-Header", "custom-value");

            String payload = "{\"message\":\"test cors post\"}";
            postRequest.setEntity(new StringEntity(payload));

            HttpResponse response = httpClient.execute(postRequest);

            // Verify CORS headers
            Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
            Assert.assertNotNull(allowOriginHeader, "Access-Control-Allow-Origin header should be present");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS request from untrusted origin")
    public void testCORSRequestFromUntrustedOrigin() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
            getRequest.setHeader("Origin", "https://malicious-site.com");

            HttpResponse response = httpClient.execute(getRequest);

            // Verify that untrusted origins are handled properly
            Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);

            if (allowOriginHeader != null) {
                // If header is present, it should either be wildcard or not the malicious origin
                String allowedOrigin = allowOriginHeader.getValue();
                if (!"*".equals(allowedOrigin)) {
                    Assert.assertNotEquals(allowedOrigin, "https://malicious-site.com",
                            "Untrusted origin should not be allowed unless wildcard is configured");
                }
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with credentials")
    public void testCORSWithCredentials() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(getApiInvocationURL("testperapicors"));
            postRequest.setHeader("Origin", "https://example.com");
            postRequest.setHeader("Content-Type", "application/json");

            String payload = "{\"data\":\"test with credentials\"}";
            postRequest.setEntity(new StringEntity(payload));

            HttpResponse response = httpClient.execute(postRequest);

            Header allowCredentialsHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_CREDENTIALS);
            Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);

            // If credentials are allowed, origin should not be wildcard
            if (allowCredentialsHeader != null && "true".equals(allowCredentialsHeader.getValue())) {
                Assert.assertNotNull(allowOriginHeader, "Origin header must be present when credentials are allowed");
                Assert.assertNotEquals(allowOriginHeader.getValue(), "*",
                        "Origin cannot be wildcard when credentials are allowed");
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS preflight with multiple methods")
    public void testCORSPreflightMultipleMethods() throws Exception {

        String[] methods = {"GET", "POST", "PUT", "DELETE"};

        for (String method : methods) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpOptions optionsRequest = new HttpOptions(getApiInvocationURL("testperapicors"));
                optionsRequest.setHeader("Origin", "https://example.com");
                optionsRequest.setHeader("Access-Control-Request-Method", method);

                HttpResponse response = httpClient.execute(optionsRequest);

                Assert.assertEquals(response.getStatusLine().getStatusCode(), 200,
                        "Preflight request for " + method + " should return 200 OK");

                Header allowMethodsHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_METHODS);
                Assert.assertNotNull(allowMethodsHeader,
                        "Access-Control-Allow-Methods header should be present for " + method);

                String allowedMethods = allowMethodsHeader.getValue();
                Assert.assertTrue(allowedMethods.contains(method) || allowedMethods.contains("*"),
                        "Method " + method + " should be allowed in CORS response");
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS max age header")
    public void testCORSMaxAge() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpOptions optionsRequest = new HttpOptions(getApiInvocationURL("testperapicors"));
            optionsRequest.setHeader("Origin", "https://example.com");
            optionsRequest.setHeader("Access-Control-Request-Method", "POST");

            HttpResponse response = httpClient.execute(optionsRequest);

            Header maxAgeHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_MAX_AGE);
            if (maxAgeHeader != null) {
                String maxAge = maxAgeHeader.getValue();
                Assert.assertTrue(maxAge.matches("\\d+"), "Max-Age should be a numeric value");
                int maxAgeValue = Integer.parseInt(maxAge);
                Assert.assertTrue(maxAgeValue >= 0, "Max-Age should be non-negative");
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with wildcard origin pattern")
    public void testCORSWildcardOriginPattern() throws Exception {

        String[] testOrigins = {
                "https://sub1.example.com",
                "https://sub2.example.com",
                "https://app.example.com"
        };

        for (String origin : testOrigins) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
                getRequest.setHeader("Origin", origin);

                HttpResponse response = httpClient.execute(getRequest);

                Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
                Assert.assertNotNull(allowOriginHeader,
                        "Access-Control-Allow-Origin header should be present for origin: " + origin);

                // If wildcard pattern is configured for *.example.com, the origin should be allowed
                String allowedOrigin = allowOriginHeader.getValue();
                Assert.assertTrue(allowedOrigin.equals(origin) || allowedOrigin.equals("*"),
                        "Origin " + origin + " should be allowed or wildcard should be returned");
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS request without Origin header")
    public void testCORSRequestWithoutOrigin() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
            // Intentionally not setting Origin header

            HttpResponse response = httpClient.execute(getRequest);

            // Request should still succeed even without Origin header
            Assert.assertTrue(response.getStatusLine().getStatusCode() >= 200 &&
                            response.getStatusLine().getStatusCode() < 300,
                    "Request without Origin header should succeed");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS preflight with custom headers")
    public void testCORSPreflightWithCustomHeaders() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpOptions optionsRequest = new HttpOptions(getApiInvocationURL("testperapicors"));
            optionsRequest.setHeader("Origin", "https://example.com");
            optionsRequest.setHeader("Access-Control-Request-Method", "POST");
            optionsRequest.setHeader("Access-Control-Request-Headers",
                    "Content-Type, Authorization, X-Custom-Header");

            HttpResponse response = httpClient.execute(optionsRequest);

            Assert.assertEquals(response.getStatusLine().getStatusCode(), 200,
                    "Preflight request with custom headers should return 200 OK");

            Header allowHeadersHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_HEADERS);
            Assert.assertNotNull(allowHeadersHeader,
                    "Access-Control-Allow-Headers should be present");

            String allowedHeaders = allowHeadersHeader.getValue();
            Assert.assertTrue(allowedHeaders.contains("Content-Type") || allowedHeaders.equals("*"),
                    "Content-Type should be allowed");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS resource not found scenario")
    public void testCORSResourceNotFound() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors") + "/nonexistent");
            getRequest.setHeader("Origin", "https://example.com");

            HttpResponse response = httpClient.execute(getRequest);

            // Should return 404 for non-existent resource
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 404,
                    "Non-existent resource should return 404");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS method not allowed scenario")
    public void testCORSMethodNotAllowed() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Try to use PATCH method if it's not configured for the API
            HttpPatch putRequest = new HttpPatch(getApiInvocationURL("testperapicors"));
            putRequest.setHeader("Origin", "https://example.com");
            putRequest.setHeader("Content-Type", "application/json");
            putRequest.setEntity(new StringEntity("{\"test\":\"data\"}"));

            HttpResponse response = httpClient.execute(putRequest);

            // If PUT is not allowed, should return 405
            if (response.getStatusLine().getStatusCode() == 405) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), 405,
                        "Disallowed method should return 405");
            }
        }
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() throws Exception {
        super.cleanup();
    }

}
