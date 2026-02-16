/*
 * Copyright (c) 2022, WSO2 LLC (http://www.wso2.com).
 *
 * WSO2 LLC licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.wso2.esb.integration.common.extensions.carbonserver.CarbonTestServerManager;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Test case to test the correlation configs of the management API.
 */
public class ConfigsResourceTestCase extends ESBIntegrationTest {

    private String carbonHome;
    private CarbonTestServerManager server;
    private int offset;

    /**
     * Sets environment.
     *
     * @throws Exception the exception
     */
    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {

        super.init();
        offset = portOffset + 10;
    }

    /**
     * Test retrieve correlation configs. This will check whether the management API retrieves
     * proper correlation config status which is 'false' and reads the correlation log file
     * which should be blank.
     *
     * @throws IOException                  the io exception
     * @throws AutomationFrameworkException the automation framework exception
     */
    @Test(groups = {"wso2.esb"}, description = "Test get configs resource for correlation")
    public void testRetrieveCorrelationConfigs() throws IOException, AutomationFrameworkException {

        startNewServer(false);
        getCorrelationLogStateAndAssert(offset, false);
        String apiLogFilePath = carbonHome + File.separator + "repository"
                + File.separator + "logs" + File.separator + "correlation.log";
        BufferedReader bufferedReader = new BufferedReader(new FileReader(apiLogFilePath));
        String logLine = bufferedReader.readLine();
        Assert.assertNull(logLine);
    }

    /**
     * Test update correlation configs. This will enable the correlation configs using the
     * management API. It will check whether the update was successful by retrieving the
     * correlation logs status from the management API and reading the correlation log file
     * for logs.
     *
     * @throws IOException the io exception
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testRetrieveCorrelationConfigs"},
            description = "Test put configs resource for correlation")
    public void testUpdateCorrelationConfigsEnable() throws IOException {

        updateCorrelationLogState(offset, true);
        getCorrelationLogStateAndAssert(offset, true);

        String apiLogFilePath = carbonHome + File.separator + "repository"
                + File.separator + "logs" + File.separator + "correlation.log";
        BufferedReader bufferedReader = new BufferedReader(new FileReader(apiLogFilePath));
        String logLine = bufferedReader.readLine();
        Assert.assertNotNull(logLine);
        while ((logLine = bufferedReader.readLine()) != null) {
            Assert.assertTrue(logLine.contains("HTTP State Transition") ||
                    logLine.contains("ROUND-TRIP LATENCY") || logLine.contains("Thread switch latency"));
        }
    }

    /**
     * Test update correlation configs disable. This will disable the correlation logs using the
     * Management API. It will check whether the update was successful by retrieving the correlation
     * logs status from the management API and checking the correlation log file for no logs.
     *
     * @throws IOException the io exception
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testUpdateCorrelationConfigsEnable"},
            description = "Test put configs resource for correlation")
    public void testUpdateCorrelationConfigsDisable() throws IOException {

        updateCorrelationLogState(offset, false);

        String apiLogFilePath = carbonHome + File.separator + "repository"
                + File.separator + "logs" + File.separator + "correlation.log";
        BufferedReader bufferedReader = new BufferedReader(new FileReader(apiLogFilePath));
        String logLine = bufferedReader.readLine();
        Assert.assertNotNull(logLine);
        while ((logLine = bufferedReader.readLine()) != null) {
            Assert.assertTrue(logLine.contains("HTTP State Transition") ||
                    logLine.contains("ROUND-TRIP LATENCY") || logLine.contains("Thread switch latency"));
        }

        getCorrelationLogStateAndAssert(offset, false);
        logLine = bufferedReader.readLine();
        log.info("Read the following line from the correlation log file after disabling : " + logLine);
        Assert.assertNull(logLine);
    }

    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testUpdateCorrelationConfigsDisable"},
            description = "Test put configs resource for correlation")
    public void testUpdateCorrelationConfigsRandom() throws IOException {
        // Update the correlation state to true and then send a random string as enabled field
        updateCorrelationLogState(offset, true);
        getCorrelationLogStateAndAssert(offset, true);

        String accessToken = TokenUtil.getAccessToken(hostName, offset);
        Assert.assertNotNull(accessToken);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + offset) + "/management/"
                + "configs";

        JSONObject payloadConfigs = new JSONObject();
        payloadConfigs.put("enabled", "abcd");  //Random string as the enabled field
        JSONObject payload = new JSONObject();
        payload.put("configName", "correlation");
        payload.put("configs", payloadConfigs);

        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPut(endpoint, headers, payload.toString(), "application/json");
        String responsePayload = client.getResponsePayload(response);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
        JSONObject jsonResponse = new JSONObject(responsePayload);
        Assert.assertTrue(jsonResponse.has("message"));
        Assert.assertEquals(jsonResponse.get("message"), "Successfully Updated Correlation Logs Status");

        // Should be false because a random string has been sent
        getCorrelationLogStateAndAssert(offset, false);
    }


    /**
     * Test retrieve correlation configs with system parameter. This will start up the server
     * with the enableCorrelationLogs flag. It will check whether the GET request of correlation
     * config retrieves the proper status which is 'true'. It will check whether a PUT request
     * will override the setting from the system property. ( Should not be able to override ).
     * This is confirmed by reading the correlation log file again.
     *
     * @throws IOException                  the io exception
     * @throws AutomationFrameworkException the automation framework exception
     */
    @Test(groups = {"wso2.esb"}, dependsOnMethods = {"testUpdateCorrelationConfigsRandom"},
            description = "Test get configs resource for correlation with system parameter")
    public void testRetrieveCorrelationConfigsWithSystemParameter() throws IOException, AutomationFrameworkException {

        stopServer();
        startNewServer(true);
        getCorrelationLogStateAndAssert(offset, true);
        updateCorrelationLogState(offset, false);

        String apiLogFilePath = carbonHome + File.separator + "repository"
                + File.separator + "logs" + File.separator + "correlation.log";
        BufferedReader bufferedReader = new BufferedReader(new FileReader(apiLogFilePath));
        String logLine = bufferedReader.readLine();
        Assert.assertNotNull(logLine);
        while ((logLine = bufferedReader.readLine()) != null) {
            Assert.assertTrue(logLine.contains("HTTP State Transition") ||
                    logLine.contains("ROUND-TRIP LATENCY") || logLine.contains("Thread switch latency"));
        }

        getCorrelationLogStateAndAssert(offset, true);

        while ((logLine = bufferedReader.readLine()) != null) {
            Assert.assertTrue(logLine.contains("HTTP State Transition") ||
                    logLine.contains("ROUND-TRIP LATENCY") || logLine.contains("Thread switch latency"));
        }
    }


    /**
     * Start a new server.
     *
     * @throws IOException
     * @throws AutomationFrameworkException
     */
    private void startNewServer(boolean enableCorrelationLogs) throws IOException, AutomationFrameworkException {

        HashMap<String, String> startupParameterMap = new HashMap<>();
        startupParameterMap.put("-DportOffset", String.valueOf(offset));
        startupParameterMap.put("-DenableCorrelationLogs", String.valueOf(enableCorrelationLogs));
        startupParameterMap.put("startupScript", "micro-integrator");

        server = new CarbonTestServerManager(context, System.getProperty("carbon.zip"), startupParameterMap);
        server.startServer();
        Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                until(isManagementApiAvailable());
        carbonHome = server.getCarbonHome();
        Assert.assertNotNull(carbonHome);
    }

    private void stopServer() throws AutomationFrameworkException {

        server.stopServer();
    }

    private void getCorrelationLogStateAndAssert(int offset, boolean correlationConfigStatus) throws IOException {

        String accessToken = TokenUtil.getAccessToken(hostName, offset);
        Assert.assertNotNull(accessToken);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + offset) + "/management/"
                + "configs?configName=correlation";

        SimpleHttpClient client = new SimpleHttpClient();

        HttpResponse response = client.doGet(endpoint, headers);
        String responsePayload = client.getResponsePayload(response);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
        JSONObject jsonResponse = new JSONObject(responsePayload);
        Assert.assertTrue(jsonResponse.has("configName"));
        Assert.assertEquals(jsonResponse.get("configName"), "correlation");
        Assert.assertTrue(jsonResponse.has("configs"));
        JSONObject responseConfigs = (JSONObject) jsonResponse.get("configs");
        Assert.assertTrue(responseConfigs.has("enabled"));
        Assert.assertEquals(responseConfigs.get("enabled"), correlationConfigStatus);
    }

    private void updateCorrelationLogState(int offset, boolean correlationConfigEnabled) throws IOException {

        String accessToken = TokenUtil.getAccessToken(hostName, offset);
        Assert.assertNotNull(accessToken);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + offset) + "/management/"
                + "configs";

        JSONObject payloadConfigs = new JSONObject();
        payloadConfigs.put("enabled", correlationConfigEnabled);
        JSONObject payload = new JSONObject();
        payload.put("configName", "correlation");
        payload.put("configs", payloadConfigs);

        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doPut(endpoint, headers, payload.toString(), "application/json");
        String responsePayload = client.getResponsePayload(response);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
        JSONObject jsonResponse = new JSONObject(responsePayload);
        Assert.assertTrue(jsonResponse.has("message"));
        Assert.assertEquals(jsonResponse.get("message"), "Successfully Updated Correlation Logs Status");
    }

    /**
     * Return if the management api is available.
     *
     * @return
     */
    public Callable<Boolean> isManagementApiAvailable() {

        return this::checkIfManagementApiAvailable;
    }

    /**
     * Check if the management api is available
     *
     * @return
     */
    private boolean checkIfManagementApiAvailable() {

        try (Socket socket = new Socket(hostName, DEFAULT_INTERNAL_API_HTTPS_PORT + offset)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clean state.
     *
     * @throws Exception the exception
     */
    @AfterClass(alwaysRun = true)
    public void cleanState() throws Exception {

        stopServer();
        System.setProperty("port.offset", String.valueOf(portOffset));
        System.setProperty("enableCorrelationLogs", "false");
        super.cleanup();
    }
}
