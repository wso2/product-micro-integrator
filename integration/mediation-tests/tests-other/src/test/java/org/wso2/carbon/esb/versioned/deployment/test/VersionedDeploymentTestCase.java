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
    private static final String paymentAPI100ExpectedPayload = "{\"weather\":{\"main\":\"Clear\",\"description\":\"CLEAR SKY\",\"icon\":\"01d\",\"daylight_hours\":11.743055555555555},\"temp\":{\"temp\":23.760000000000048,\"temp_description\":\"Hot\"},\"wind\":{\"speed\":3.87,\"deg\":39},\"pressure\":1011,\"humidity\":\"87%\",\"visibility\":10000}";
    private static final String paymentAPI101ExpectedPayload = "{\"weather\":{\"main\":\"Rainy\",\"description\":\"LIGHT RAIN\",\"icon\":\"10d\",\"daylight_hours\":11.743055555555555},\"temp\":{\"temp\":23.760000000000048,\"temp_description\":\"Hot\"},\"wind\":{\"speed\":3.87,\"deg\":39},\"pressure\":1011,\"humidity\":\"87%\",\"visibility\":10000}";
    private ServerConfigurationManager serverConfigurationManager;

    @BeforeClass(alwaysRun = true)
    public void uploadSynapseConfig() throws Exception {

        super.init();
        serverConfigurationManager = new ServerConfigurationManager(context);
        carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        File templateCAPP = new File(
                getESBResourceLocation() + File.separator + "versionedDeployment" + File.separator +
                        TEMPLATE_CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(templateCAPP);
        assertTrue(Utils.checkForLog(carbonLogReader, "Successfully Deployed Carbon Application : " +
                "com.microintegrator.projects__template__1.0.1", 20), "Failed to deploy template car");

        File payment100CAPP = new File(
                getESBResourceLocation() + File.separator + "versionedDeployment" + File.separator +
                        PAYMENT_100_CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(payment100CAPP);
        assertTrue(Utils.checkForLog(carbonLogReader, "Successfully Deployed Carbon Application : " +
                "com.microintegrator.projects__payment__1.0.0", 20), "Failed to deploy template car");

        File payment101CAPP = new File(
                getESBResourceLocation() + File.separator + "versionedDeployment" + File.separator +
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

        Assert.assertEquals(httpResponse1.getStatusLine().getStatusCode(), 200);
        // Assert the JSON payloads
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse1), Object.class),
                new Gson().fromJson(paymentAPI100ExpectedPayload, Object.class));

        HttpResponse httpResponse2 = httpClient.doGet(paymentAPI101URL, headers);

        Assert.assertEquals(httpResponse2.getStatusLine().getStatusCode(), 200);
        // Assert the JSON payloads
        org.testng.Assert.assertEquals(new Gson().fromJson(SimpleHttpClient.responseEntityBodyToString(httpResponse2), Object.class),
                new Gson().fromJson(paymentAPI101ExpectedPayload, Object.class));

        carbonLogReader.checkForLog("Log from seq from local entry v1.0.1", DEFAULT_TIMEOUT);
        carbonLogReader.stop();
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {

        serverConfigurationManager.removeFromCarbonapps(PAYMENT_101_CAPP_NAME);
        serverConfigurationManager.removeFromCarbonapps(PAYMENT_100_CAPP_NAME);
        serverConfigurationManager.removeFromCarbonapps(TEMPLATE_CAPP_NAME);
        super.cleanup();
    }
}