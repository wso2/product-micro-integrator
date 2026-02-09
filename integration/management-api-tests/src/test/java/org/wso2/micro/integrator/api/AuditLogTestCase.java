/*
 *Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *WSO2 Inc. licenses this file to you under the Apache License,
 *Version 2.0 (the "License"); you may not use this file except
 *in compliance with the License.
 *You may obtain a copy of the License at
 *
 *http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an
 *"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *KIND, either express or implied.  See the License for the
 *specific language governing permissions and limitations
 *under the License.
 */

package org.wso2.micro.integrator.api;

import org.apache.http.HttpResponse;
import org.awaitility.Awaitility;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.exceptions.AutomationFrameworkException;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.ESBTestConstant;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;
import org.wso2.esb.integration.common.utils.common.TestConfigurationProvider;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AuditLogTestCase extends ESBIntegrationTest {
    private String carbonHome;
    private int offset;
    private CarbonLogReader carbonLogReader = new CarbonLogReader();
    private String accessToken;

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init();
        carbonLogReader.start();
    }


    @Test(groups = {"wso2.esb" }, priority = 1,
          description = "Test user login logs")
    public void testUserLoging() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic YWRtaW46YWRtaW4=");
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "login";
        SimpleHttpClient client = new SimpleHttpClient();

        HttpResponse response = client.doGet(endpoint, headers);
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        String responsePayload = client.getResponsePayload(response);
        JSONObject responseJSON = new JSONObject(responsePayload);
        accessToken = (String) responseJSON.get("AccessToken");
        Assert.assertTrue(carbonLogReader.checkForLog("admin logged in at", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 2,
          description = "Test activate deactivate message processor")
    public void testMessageProcessor() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "message-processors";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testMessageProcessor\",\"status\": \"inactive\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"message_processor\",\"info\":\"{\\\"messageProcessorName\\\":\\\"testMessageProcessor\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test proxy service enable/disable logs")
    public void testProxyService() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "proxy-services";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testProxy\",\"status\": \"inactive\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"proxy_service\",\"info\":\"{\\\"proxyName\\\":\\\"testProxy\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test proxy service enable/disable traces log")
    public void testProxyServiceTrace() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "proxy-services";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testProxy\",\"trace\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"proxy_service_trace\",\"info\":\"{\\\"proxyName\\\":\\\"testProxy\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
            description = "Test proxy service enable/disable statistics log")
    public void testProxyServiceStatistics() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + "proxy-services";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testProxy\",\"statistics\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"proxy_service_statistics\",\"info\":\"{\\\"proxyName\\\":\\\"testProxy\\\"}\"}", 120));

        response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testProxy\",\"statistics\": \"disable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"proxy_service_statistics\",\"info\":\"{\\\"proxyName\\\":\\\"testProxy\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test activate/deactivate endpoint")
    public void testEndpoint() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "endpoints";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testEndpoint\",\"status\": \"inactive\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"endpoint\",\"info\":\"{\\\"endpointName\\\":\\\"testEndpoint\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test enable disable endpoint trace")
    public void testEndpointTrace() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "endpoints";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testEndpoint\",\"trace\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"endpoint_trace\",\"info\":\"{\\\"endpointName\\\":\\\"testEndpoint\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
            description = "Test enable disable endpoint statistics")
    public void testEndpointStatistics() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + "endpoints";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testEndpoint\",\"statistics\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"endpoint_statistics\",\"info\":\"{\\\"endpointName\\\":\\\"testEndpoint\\\"}\"}", 120));

        response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testEndpoint\",\"statistics\": \"disable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"endpoint_statistics\",\"info\":\"{\\\"endpointName\\\":\\\"testEndpoint\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test enable disable API trace")
    public void testAPITrace() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "apis";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testApi\",\"trace\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"api_trace\",\"info\":\"{\\\"apiName\\\":\\\"testApi\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
            description = "Test enable disable API statistics")
    public void testAPIStatistics() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + "apis";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testApi\",\"statistics\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"api_statistics\",\"info\":\"{\\\"apiName\\\":\\\"testApi\\\"}\"}", 120));

        response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testApi\",\"statistics\": \"disable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"api_statistics\",\"info\":\"{\\\"apiName\\\":\\\"testApi\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test enable disable sequence trace")
    public void testSequenceTrace() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "sequences";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testSequence\",\"trace\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"sequence_trace\",\"info\":\"{\\\"sequenceName\\\":\\\"testSequence\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
            description = "Test enable disable sequence statistics")
    public void testSequenceStatistics() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + "sequences";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testSequence\",\"statistics\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"sequence_statistics\",\"info\":\"{\\\"sequenceName\\\":\\\"testSequence\\\"}\"}", 120));

        response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testSequence\",\"statistics\": \"disable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"sequence_statistics\",\"info\":\"{\\\"sequenceName\\\":\\\"testSequence\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test enable disable inbound endpoint trace")
    public void testInboundEndpointTrace() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "inbound-endpoints";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testInboundEndpoint\",\"trace\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"inbound_endpoint_trace\",\"info\":\"{\\\"inboundEndpointName\\\":\\\"testInboundEndpoint\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
            description = "Test enable disable inbound endpoint statistics")
    public void testInboundEndpointStatistics() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + "inbound-endpoints";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testInboundEndpoint\",\"statistics\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"inbound_endpoint_statistics\",\"info\":\"{\\\"inboundEndpointName\\\":\\\"testInboundEndpoint\\\"}\"}", 120));

        response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testInboundEndpoint\",\"statistics\": \"disable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"inbound_endpoint_statistics\",\"info\":\"{\\\"inboundEndpointName\\\":\\\"testInboundEndpoint\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test enable disable sequence template trace")
    public void testSequenceTemplateTrace() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "templates";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testSequenceTemplate\", \"type\": \"sequence\",\"trace\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"sequence_template_trace\",\"info\":\"{\\\"sequenceName\\\":\\\"testSequenceTemplate\\\",\\\"sequenceType\\\":\\\"sequence\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
            description = "Test enable disable sequence template statistics")
    public void testSequenceTemplateStatistics() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + "templates";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testSequenceTemplate\", \"type\": \"sequence\",\"statistics\": \"enable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"enabled\",\"type\":\"sequence_template_statistics\",\"info\":\"{\\\"sequenceName\\\":\\\"testSequenceTemplate\\\",\\\"sequenceType\\\":\\\"sequence\\\"}\"}", 120));

        response = client.doPost(endpoint, getHeaderMap(), "{\"name\": \"testSequenceTemplate\", \"type\": \"sequence\",\"statistics\": \"disable\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                response.getStatusLine().getStatusCode() +
                " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"disabled\",\"type\":\"sequence_template_statistics\",\"info\":\"{\\\"sequenceName\\\":\\\"testSequenceTemplate\\\",\\\"sequenceType\\\":\\\"sequence\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 3,
          description = "Test update logger")
    public void testUpdateLogger() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "logging";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPatch(endpoint, getHeaderMap(), "{\"loggerName\": \"com-atomikos\", \"loggingLevel\": \"DEBUG\"}", "application/json");
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"updated\",\"type\":\"log_level\",\"info\":\"{\\\"loggerName\\\":\\\"com-atomikos\\\",\\\"loggingLevel\\\":\\\"DEBUG\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 4,
          description = "Test upload CAPP")
    public void testCAppUpload() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        File file = new File(TestConfigurationProvider.getResourceLocation(ESBTestConstant.ESB_PRODUCT_GROUP)
                             + "/capp/esb-artifacts-car_1.0.0.car");
        deployCarbonApplication(file, getHeaderMap());
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"created\",\"type\":\"carbon_application\",\"info\":\"{\\\"cAppFileName\\\":\\\"esb-artifacts-car_1.0.0.car\\\"}\"}", 120));
    }

    @Test(groups = {"wso2.esb" }, priority = 4, dependsOnMethods = "testCAppUpload",
          description = "Test remove CAPP")
    public void testCAppRemove() throws IOException, InterruptedException, AutomationFrameworkException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + "applications/esb-artifacts-car_1.0.0";
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doDelete(endpoint, getHeaderMap());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode(), "Invalid response status " +
                                                                           response.getStatusLine().getStatusCode() +
                                                                           " returned. Expected status code is 200");
        Assert.assertTrue(carbonLogReader.checkForLog("{\"performedBy\":\"admin\",\"action\":\"deleted\",\"type\":\"carbon_application\",\"info\":\"{\\\"cAppFileName\\\":\\\"esb-artifacts-car_1.0.0\\\"}\"}", 120));
    }

    @AfterClass(alwaysRun = true)
    public void cleanState() throws Exception {
        super.cleanup();
    }

    /**
     * Return the the location of the server instance.
     * @return carbon home
     */
    private String getCarbonHome() {
        return carbonHome;
    }
    private Map<String, String> getHeaderMap() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer ".concat(accessToken));
        return headers;
    }
}
