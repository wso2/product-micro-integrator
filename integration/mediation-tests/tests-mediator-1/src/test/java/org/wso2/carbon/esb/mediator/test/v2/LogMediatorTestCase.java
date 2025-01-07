/*
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.esb.mediator.test.v2;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;

import java.io.IOException;

public class LogMediatorTestCase extends ESBIntegrationTest {

    SimpleHttpClient httpClient = new SimpleHttpClient();

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.init();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Log mediator INFO level")
    public void testLogMediatorINFO() throws IOException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String requestPayload = "{\n" +
                "    \"data\": {\n" +
                "        \"name\": \"John\",\n" +
                "        \"food\": {\n" +
                "            \"cal\": \"55\",\n" +
                "            \"sugar\": \"none\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "log-mediator-template/info";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), 200, "Response code mismatched");
        EntityUtils.consumeQuietly(httpResponse.getEntity());

        boolean logLine1 = carbonLogReader
                .checkForLog("Processing info message: {\"name\":\"John\",\"food\":{\"cal\":\"55\",\"sugar\":\"none\"}} " +
                        "using endpoint http://localhost:8480/log-mediator-template/mock-backend-json", DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine1, "Log mediator INFO message Line 1 not logged");

        boolean logLine2 = carbonLogReader
                .checkForLog("Backend result = {\"pet\":{\"name\":\"pet3\",\"type\":\"mock-backend\"}}|" +
                        "requestID = John_123123|test = abc123", DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine2, "Log mediator INFO message Line 2 not logged");

        carbonLogReader.stop();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Log mediator WARN level")
    public void testLogMediatorWARN() throws IOException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String requestPayload = "{\n" +
                "    \"data\": {\n" +
                "        \"name\": \"John\",\n" +
                "        \"food\": {\n" +
                "            \"cal\": \"55\",\n" +
                "            \"sugar\": \"none\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "log-mediator-template/warn";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), 200, "Response code mismatched");
        EntityUtils.consumeQuietly(httpResponse.getEntity());

        boolean logLine1 = carbonLogReader
                .checkForLog("Processing message: {\"name\":\"John\",\"food\":{\"cal\":\"55\",\"sugar\":\"none\"}} " +
                        "using endpoint", DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine1, "Log mediator WARN message Line 1 not logged");

        boolean logLine2 = carbonLogReader
                .checkForLog("Log warn message, payload = {\"pet\":{\"name\":\"pet3\",\"type\":\"mock-backend\"}}, " +
                        "content-type-header = application/json; charset=UTF-8", DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine2, "Log mediator WARN message Line 2 not logged");

        carbonLogReader.stop();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Log mediator ERROR level")
    public void testLogMediatorERROR() throws IOException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String requestPayload = "{\n" +
                "    \"data\": {\n" +
                "        \"name\": \"John\",\n" +
                "        \"food\": {\n" +
                "            \"cal\": \"55\",\n" +
                "            \"sugar\": \"none\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "log-mediator-template/error";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), 200, "Response code mismatched");
        EntityUtils.consumeQuietly(httpResponse.getEntity());

        boolean logLine1 = carbonLogReader
                .checkForLog("Processing error message: {\"name\":\"John\",\"food\":{\"cal\":\"55\",\"sugar\":\"none\"}}",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine1, "Log mediator ERROR message Line 1 not logged");

        boolean logLine2 = carbonLogReader
                .checkForLog("Error occurred while processing backend response, STATUS_CODE = 200", DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine2, "Log mediator ERROR message Line 2 not logged");

        carbonLogReader.stop();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Log mediator FATAL level")
    public void testLogMediatorFATAL() throws IOException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String requestPayload = "<data><name>John</name><food><cal>55</cal><sugar>none</sugar></food></data>";

        String serviceURL = getMainSequenceURL() + "log-mediator-template/fatal";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/xml");
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), 200, "Response code mismatched");
        EntityUtils.consumeQuietly(httpResponse.getEntity());

        boolean logLine1 = carbonLogReader
                .checkForLog("Critical issue detected: <food><cal>55</cal><sugar>none</sugar></food>, prop1 = synapse_prop1",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine1, "Log mediator FATAL message Line 1 not logged");
        carbonLogReader.stop();
    }

    @AfterClass(alwaysRun = true)
    private void destroy() throws Exception {

        super.cleanup();
    }
}
