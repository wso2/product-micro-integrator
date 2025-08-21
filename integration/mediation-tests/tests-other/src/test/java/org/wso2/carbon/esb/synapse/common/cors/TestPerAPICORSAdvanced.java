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

/**
 * Advanced CORS integration tests covering edge cases and error scenarios
 */
public class TestPerAPICORSAdvanced extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {
        super.init();
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with empty Origin header")
    public void testCORSWithEmptyOrigin() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
            getRequest.setHeader("Origin", "");

            HttpResponse response = httpClient.execute(getRequest);

            // Request should still succeed with empty origin
            Assert.assertTrue(response.getStatusLine().getStatusCode() >= 200 &&
                response.getStatusLine().getStatusCode() < 300,
                "Request with empty Origin header should succeed");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS preflight with missing request method header")
    public void testCORSPreflightMissingRequestMethod() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpOptions optionsRequest = new HttpOptions(getApiInvocationURL("testperapicors"));
            optionsRequest.setHeader("Origin", "https://example.com");
            // Intentionally not setting Access-Control-Request-Method

            HttpResponse response = httpClient.execute(optionsRequest);

            Assert.assertEquals(response.getStatusLine().getStatusCode(), 200,
                "Preflight request without request method should still return 200");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with complex wildcard origin patterns")
    public void testCORSComplexWildcardPatterns() throws Exception {

        String[] testOrigins = {
            "https://test.subdomain.com",
            "https://api.subdomain.com",
            "https://app-prod.subdomain.com"
        };

        for (String origin : testOrigins) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
                getRequest.setHeader("Origin", origin);

                HttpResponse response = httpClient.execute(getRequest);

                Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
                Assert.assertNotNull(allowOriginHeader,
                    "Access-Control-Allow-Origin header should be present for: " + origin);

                String allowedOrigin = allowOriginHeader.getValue();
                Assert.assertTrue(allowedOrigin.equals(origin) || allowedOrigin.equals("*"),
                    "Complex wildcard pattern should match origin: " + origin);
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with multiple request headers")
    public void testCORSMultipleRequestHeaders() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpOptions optionsRequest = new HttpOptions(getApiInvocationURL("testperapicors"));
            optionsRequest.setHeader("Origin", "https://example.com");
            optionsRequest.setHeader("Access-Control-Request-Method", "POST");
            optionsRequest.setHeader("Access-Control-Request-Headers",
                "Content-Type, Authorization, X-Custom-Header, X-API-Key, X-Request-ID");

            HttpResponse response = httpClient.execute(optionsRequest);

            Assert.assertEquals(response.getStatusLine().getStatusCode(), 200,
                "Preflight with multiple headers should return 200");

            Header allowHeadersHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_HEADERS);
            Assert.assertNotNull(allowHeadersHeader, "Allow-Headers should be present");

            String allowedHeaders = allowHeadersHeader.getValue().toLowerCase();
            Assert.assertTrue(allowedHeaders.contains("content-type") || allowedHeaders.equals("*"),
                "Content-Type should be allowed");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with case-insensitive headers")
    public void testCORSCaseInsensitiveHeaders() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
            getRequest.setHeader("origin", "https://example.com"); // lowercase origin

            HttpResponse response = httpClient.execute(getRequest);

            // Should handle case-insensitive headers properly
            Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
            Assert.assertNotNull(allowOriginHeader,
                "Should handle case-insensitive Origin header");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with invalid origin format")
    public void testCORSInvalidOriginFormat() throws Exception {

        String[] invalidOrigins = {
            "invalid-url",
            "ftp://invalid-protocol.com",
            "javascript:alert('xss')",
            "data:text/html,<script>alert('xss')</script>"
        };

        for (String origin : invalidOrigins) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
                getRequest.setHeader("Origin", origin);

                HttpResponse response = httpClient.execute(getRequest);

                Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
                if (allowOriginHeader != null) {
                    String allowedOrigin = allowOriginHeader.getValue();
                    // Should not echo back invalid origins unless wildcard is used
                    if (!"*".equals(allowedOrigin)) {
                        Assert.assertNotEquals(allowedOrigin, origin,
                            "Invalid origin should not be echoed back: " + origin);
                    }
                }
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with very long header values")
    public void testCORSLongHeaderValues() throws Exception {

        // Create a very long origin string
        StringBuilder longOrigin = new StringBuilder("https://very-long-subdomain");
        for (int i = 0; i < 100; i++) {
            longOrigin.append("-part").append(i);
        }
        longOrigin.append(".example.com");

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
            getRequest.setHeader("Origin", longOrigin.toString());

            HttpResponse response = httpClient.execute(getRequest);

            // Should handle long headers gracefully
            Assert.assertTrue(response.getStatusLine().getStatusCode() >= 200 &&
                response.getStatusLine().getStatusCode() < 500,
                "Should handle long origin headers gracefully");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with special characters in origin")
    public void testCORSSpecialCharactersInOrigin() throws Exception {

        String[] specialOrigins = {
            "https://test-app.example.com",
            "https://test_app.example.com",
            "https://test.app-prod.example.com",
            "https://192.168.1.100:8080"
        };

        for (String origin : specialOrigins) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
                getRequest.setHeader("Origin", origin);

                HttpResponse response = httpClient.execute(getRequest);

                Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
                Assert.assertNotNull(allowOriginHeader,
                    "Should handle origin with special characters: " + origin);
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS preflight response headers completeness")
    public void testCORSPreflightResponseHeadersCompleteness() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpOptions optionsRequest = new HttpOptions(getApiInvocationURL("testperapicors"));
            optionsRequest.setHeader("Origin", "https://example.com");
            optionsRequest.setHeader("Access-Control-Request-Method", "POST");
            optionsRequest.setHeader("Access-Control-Request-Headers", "Content-Type");

            HttpResponse response = httpClient.execute(optionsRequest);

            // Verify all required CORS headers are present using constants
            String[] requiredHeaders = {
                RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN,
                RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_METHODS,
                RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_HEADERS
            };

            for (String headerName : requiredHeaders) {
                Header header = response.getFirstHeader(headerName);
                Assert.assertNotNull(header, "Required CORS header missing: " + headerName);
                Assert.assertNotNull(header.getValue(),
                    "Required CORS header value is null: " + headerName);
                Assert.assertFalse(header.getValue().trim().isEmpty(),
                    "Required CORS header value is empty: " + headerName);
            }
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS with concurrent requests")
    public void testCORSConcurrentRequests() throws Exception {

        int numThreads = 5;
        Thread[] threads = new Thread[numThreads];
        boolean[] results = new boolean[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpGet getRequest = new HttpGet(getApiInvocationURL("testperapicors"));
                    getRequest.setHeader("Origin", "https://example.com");

                    HttpResponse response = httpClient.execute(getRequest);
                    results[threadIndex] = response.getStatusLine().getStatusCode() == 200;
                } catch (Exception e) {
                    results[threadIndex] = false;
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all requests succeeded
        for (int i = 0; i < numThreads; i++) {
            Assert.assertTrue(results[i], "Concurrent request " + i + " failed");
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Test CORS headers persistence across redirects")
    public void testCORSHeadersPersistence() throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(getApiInvocationURL("testperapicors"));
            postRequest.setHeader("Origin", "https://example.com");
            postRequest.setHeader("Content-Type", "application/json");

            String payload = "{\"test\":\"persistence\"}";
            postRequest.setEntity(new StringEntity(payload));

            HttpResponse response = httpClient.execute(postRequest);

            // Verify CORS headers are maintained in response
            Header allowOriginHeader = response.getFirstHeader(RESTConstants.CORS_HEADER_ACCESS_CTL_ALLOW_ORIGIN);
            Assert.assertNotNull(allowOriginHeader,
                "CORS headers should persist in response");

            // Verify response has proper content
            Assert.assertTrue(response.getStatusLine().getStatusCode() >= 200 &&
                response.getStatusLine().getStatusCode() < 300,
                "Request should succeed with CORS headers intact");
        }
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() throws Exception {
        super.cleanup();
    }
}
