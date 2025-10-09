/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.esb.versioned.deployment.test;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.base.CarbonBaseUtils;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertTrue;

/**
 * Testcase to test versioned deployment for synapse artifacts.
 */
public class VersionedDeploymentTestCase extends ESBIntegrationTest {

    private CarbonLogReader carbonLogReader;
    private static final String TEMPLATE_CAPP_NAME = "template_1.0.1.car";
    private static final String PAYMENT_100_CAPP_NAME = "payment_1.0.0.car";
    private static final String PAYMENT_101_CAPP_NAME = "payment_1.0.1.car";
    private static final String BANKING_SERVICE_CAPP_NAME = "bankingservices_1.0.0.car";
    private static final String paymentAPI100ExpectedPayload = "{\"weather\":{\"main\":\"Clear\",\"description\":\"CLEAR SKY\",\"icon\":\"01d\",\"daylight_hours\":11.743055555555555},\"temp\":{\"temp\":23.760000000000048,\"temp_description\":\"Hot\"},\"wind\":{\"speed\":3.87,\"deg\":39},\"pressure\":1011,\"humidity\":\"87%\",\"visibility\":10000}";
    private static final String paymentAPI101ExpectedPayload = "{\"weather\":{\"main\":\"Rainy\",\"description\":\"LIGHT RAIN\",\"icon\":\"10d\",\"daylight_hours\":11.743055555555555},\"temp\":{\"temp\":23.760000000000048,\"temp_description\":\"Hot\"},\"wind\":{\"speed\":3.87,\"deg\":39},\"pressure\":1011,\"humidity\":\"87%\",\"visibility\":10000}";
    private ServerConfigurationManager serverConfigurationManager;
    private static final String VERSIONED_CAPP_FOLDER = "versionedDeployment";

    @BeforeClass(alwaysRun = true)
    public void uploadSynapseConfig() throws Exception {

        super.init();
        serverConfigurationManager = new ServerConfigurationManager(context);
        carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        // Set env variables for linux and mac
        File newSynapseProps = new File(
                getESBResourceLocation() + File.separator + VERSIONED_CAPP_FOLDER + File.separator + "synapse.properties");
        File oldSynapseProps =
                new File(CarbonBaseUtils.getCarbonHome() + File.separator + "conf" + File.separator + "synapse.properties");
        serverConfigurationManager.applyConfigurationWithoutRestart(newSynapseProps, oldSynapseProps, true);

        serverConfigurationManager.restartMicroIntegrator();

        File templateCAPP = new File(
                getESBResourceLocation() + File.separator + VERSIONED_CAPP_FOLDER + File.separator +
                        TEMPLATE_CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(templateCAPP);
        assertTrue(Utils.checkForLog(carbonLogReader, "Successfully Deployed Carbon Application : " +
                "com.microintegrator.projects__template__1.0.1", 20), "Failed to deploy template car");

        File payment100CAPP = new File(
                getESBResourceLocation() + File.separator + VERSIONED_CAPP_FOLDER + File.separator +
                        PAYMENT_100_CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(payment100CAPP);
        assertTrue(Utils.checkForLog(carbonLogReader, "Initializing API: " +
                "com.microintegrator.projects__payment__1.0.0__PaymentAPI", 20), "Failed to deploy versioned API");
        assertTrue(Utils.checkForLog(carbonLogReader, "Successfully Deployed Carbon Application : " +
                "com.microintegrator.projects__payment__1.0.0", 20), "Failed to deploy template car");

        File payment101CAPP = new File(
                getESBResourceLocation() + File.separator + VERSIONED_CAPP_FOLDER + File.separator +
                        PAYMENT_101_CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(payment101CAPP);
        assertTrue(Utils.checkForLog(carbonLogReader, "Successfully Deployed Carbon Application : " +
                "com.microintegrator.projects__payment__1.0.1", 20), "Failed to deploy template car");
    }

    @Test(groups = "wso2.esb", description = "Testcase to test versioned deployment for synapse artifacts")
    public void testVersionedDeployment() throws Exception {

        String paymentAPI100URL = getMainSequenceURL() + "/com.microintegrator.projects/payment/1.0.0/paymentapi";
        String paymentAPI101URL = getMainSequenceURL() + "/com.microintegrator.projects/payment/1.0.1/paymentapi";
        Map<String, String> headers = new HashMap<>(1);
        SimpleHttpClient httpClient = new SimpleHttpClient();
        HttpResponse httpResponse1 = httpClient.doGet(paymentAPI100URL, headers);

        Assert.assertEquals(200, httpResponse1.getStatusLine().getStatusCode());
        // Assert the JSON payloads
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse1), Object.class),
                new Gson().fromJson(paymentAPI100ExpectedPayload, Object.class));

        HttpResponse httpResponse2 = httpClient.doGet(paymentAPI101URL, headers);

        Assert.assertEquals(200, httpResponse2.getStatusLine().getStatusCode());
        // Assert the JSON payloads
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse2), Object.class),
                new Gson().fromJson(paymentAPI101ExpectedPayload, Object.class));

        carbonLogReader.checkForLog("Log from seq from local entry v1.0.1", DEFAULT_TIMEOUT);
    }

    @Test(groups = "wso2.esb", description = "Testcase to test versioned deployment for connectors")
    public void testVersionedDeploymentConnectors() throws Exception {

        String seqTemplateAPI = getMainSequenceURL() + "/com.microintegrator.projects/payment/1.0.0/mockapi";
        String httpConnectorAPI = getMainSequenceURL() + "/com.microintegrator.projects/payment/1.0.0/mockapi/test";
        Map<String, String> headers = new HashMap<>(1);
        SimpleHttpClient httpClient = new SimpleHttpClient();
        HttpResponse httpResponse1 = httpClient.doGet(seqTemplateAPI, headers);

        Assert.assertEquals(200, httpResponse1.getStatusLine().getStatusCode());
        String seqTemplateExpectedPayload = "{\"messageID\": \"123\",\"data\": \"Hello,World\"}";
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse1), Object.class),
                new Gson().fromJson(seqTemplateExpectedPayload, Object.class));

        HttpResponse httpResponse2 = httpClient.doGet(httpConnectorAPI, headers);
        Assert.assertEquals(200, httpResponse2.getStatusLine().getStatusCode());
        String httpConnectorExpectedPayload = "{\"time\":\"10:00:76\",\"data\": {\"messageID\":\"123\",\"data\":\"Hello,World\"}}";
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse2), Object.class),
                new Gson().fromJson(httpConnectorExpectedPayload, Object.class));
    }

    @Test(groups = "wso2.esb", description = "Testcase to test non versioned deployment when versioned service expose is true",
    dependsOnMethods = "testVersionedDeploymentConnectors")
    public void testNonVersionedDeploymentWithServiceExpose() throws Exception {

        File bankingService = new File(
                getESBResourceLocation() + File.separator + VERSIONED_CAPP_FOLDER + File.separator +
                        BANKING_SERVICE_CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(bankingService);

        carbonLogReader.checkForLog("The ProxyService 'BankProxy' is not included in a versioned CApp. " +
                "Versioned service exposure is skipped; deploying as a regular ProxyService.", DEFAULT_TIMEOUT);
        carbonLogReader.checkForLog("The API 'BankAPI' is not included in a versioned CApp. " +
                "Versioned service exposure is skipped; deploying as a regular API.", DEFAULT_TIMEOUT);

        carbonLogReader.checkForLog("Successfully Deployed Carbon Application : bankingservices_1.0.0", DEFAULT_TIMEOUT);

        String bankAPI = getMainSequenceURL() + "/bankapi";
        Map<String, String> headers = new HashMap<>(1);
        SimpleHttpClient httpClient = new SimpleHttpClient();
        HttpResponse httpResponse1 = httpClient.doGet(bankAPI, headers);

        Assert.assertEquals(200, httpResponse1.getStatusLine().getStatusCode());
        // Assert the JSON payloads
        String expectedAPI = "{\"message\":\"Welcome to ABC Bank API\"}";
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse1), Object.class),
                new Gson().fromJson(expectedAPI, Object.class));

        String bankProxy = getMainSequenceURL() + "/services/BankProxy";
        httpResponse1 = httpClient.doGet(bankProxy, headers);

        Assert.assertEquals(200, httpResponse1.getStatusLine().getStatusCode());
        // Assert the JSON payloads
        String expectedProxy = "{\"message\":\"Welcome to ABC Bank Proxy\"}";
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse1), Object.class),
                new Gson().fromJson(expectedProxy, Object.class));
    }

    @Test(groups = "wso2.esb", description = "Testcase to test versioned deployment when versioned service expose is false",
          dependsOnMethods = "testNonVersionedDeploymentWithServiceExpose")
    public void testVersionedDeploymentWithoutServiceExpose() throws Exception {

        serverConfigurationManager.restoreToLastConfiguration(true);
        carbonLogReader.checkForLog("Duplicate resource definition by the name: PaymentAPI", DEFAULT_TIMEOUT);

        // Remove Payment CApp v1.0.1 and restart
        serverConfigurationManager.removeFromCarbonapps(PAYMENT_101_CAPP_NAME);
        serverConfigurationManager.restartMicroIntegrator();
        carbonLogReader.checkForLog("Initializing API: PaymentAPI", DEFAULT_TIMEOUT);
        carbonLogReader.checkForLog("Successfully Deployed Carbon Application : com.microintegrator.projects__payment__1.0.0", DEFAULT_TIMEOUT);

        String paymentAPI100URL = getMainSequenceURL() + "/paymentapi";
        Map<String, String> headers = new HashMap<>(1);
        SimpleHttpClient httpClient = new SimpleHttpClient();
        HttpResponse httpResponse1 = httpClient.doGet(paymentAPI100URL, headers);

        Assert.assertEquals(200, httpResponse1.getStatusLine().getStatusCode());
        // Assert the JSON payloads
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse1), Object.class),
                new Gson().fromJson(paymentAPI100ExpectedPayload, Object.class));
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {

        carbonLogReader.stop();
        serverConfigurationManager.removeFromCarbonapps(PAYMENT_101_CAPP_NAME);
        serverConfigurationManager.removeFromCarbonapps(PAYMENT_100_CAPP_NAME);
        serverConfigurationManager.removeFromCarbonapps(TEMPLATE_CAPP_NAME);
        serverConfigurationManager.removeFromCarbonapps(BANKING_SERVICE_CAPP_NAME);
        super.cleanup();
    }
}
