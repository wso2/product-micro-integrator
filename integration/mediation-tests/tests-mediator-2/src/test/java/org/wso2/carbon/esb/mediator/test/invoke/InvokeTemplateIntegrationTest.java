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
package org.wso2.carbon.esb.mediator.test.invoke;

import org.apache.commons.lang.StringUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertTrue;

/**
 * This class tests Invoke Mediator with Template support to validate connector response
 */
public class InvokeTemplateIntegrationTest extends ESBIntegrationTest {

    CarbonLogReader carbonLogReader;

    @BeforeClass(alwaysRun = true)
    public void uploadSynapseConfig() throws Exception {

        super.init();
        carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();
    }

    @Test(groups = "wso2.esb", description = "Testing Invoke mediator with Template support with GET method when " +
            "overwriteBody is false")
    public void testInvokeMediatorWithOverwriteBodyTrueAndGetMethod() throws Exception {

        carbonLogReader.clearLogs();
        executeSequenceAndAssertResponse("weatherintegration", "/overwrite/false", "", "GET",
                "The return payload does not match the expectedPayload", null , null);
        assertTrue(carbonLogReader.checkForLog("The weather template returned city in header as LONDON. " +
                "Status code : 200.", DEFAULT_TIMEOUT));
    }

    @Test(groups = "wso2.esb", description = "Testing Invoke mediator with Template support with GET method when " +
            "overwriteBody is true")
    public void testInvokeMediatorWithOverwriteBodyFalseAndGetMethod() throws Exception {

        carbonLogReader.clearLogs();
        String expectedOutput = "{\"city\": \"LONDON\", \"temperature - C\" : \"23\", \"weather\" : \"cloudy\"}";
        executeSequenceAndAssertResponse("weatherintegration", "/overwrite/true",
                expectedOutput, "GET",
                "The return payload does not match the expectedPayload", null, null);
        assertTrue(carbonLogReader.checkForLog("The weather template returned city in header as LONDON. " +
                "Status code : 200.", DEFAULT_TIMEOUT));
    }

    @Test(groups = "wso2.esb", description = "Testing Invoke mediator with Template support with POST method when " +
            "overwriteBody is false")
    public void testInvokeMediatorWithOverwriteBodyTrueAndPostMethod() throws Exception {

            carbonLogReader.clearLogs();
            String inputPayload = "{\"city\":\"LONDON\"}";
            executeSequenceAndAssertResponse("weatherintegration", "/overwrite/false", inputPayload, "POST",
                    "The return payload does not match the expectedPayload", inputPayload, "application/json");
            assertTrue(carbonLogReader.checkForLog("The weather template returned city in header as LONDON. " +
                    "Status code : 200.", DEFAULT_TIMEOUT));
    }

    @Test(groups = "wso2.esb", description = "Testing Invoke mediator with Template support with POST method when " +
            "overwriteBody is true")
    public void testInvokeMediatorWithOverwriteBodyFalseAndPostMethod() throws Exception {

            carbonLogReader.clearLogs();
            String inputPayload = "{\"city\":\"LONDON\"}";
            String expectedOutput = "{\"city\": \"LONDON\", \"temperature - C\" : \"23\", \"weather\" : \"cloudy\"}";
            executeSequenceAndAssertResponse("weatherintegration", "/overwrite/true",
                    expectedOutput, "POST",
                    "The return payload does not match the expectedPayload", inputPayload, "application/json");
            assertTrue(carbonLogReader.checkForLog("The weather template returned city in header as LONDON. " +
                    "Status code : 200.", DEFAULT_TIMEOUT));
    }

    @Test(groups = "wso2.esb", description = "Testing Invoke mediator with Template support with POST method when " +
            "overwriteBody is false and the input payload is xml")
    public void testInvokeMediatorWithOverwriteBodyTrueAndPostMethodWithXMLPayload() throws Exception {

            carbonLogReader.clearLogs();
            String inputPayload = "<city>LONDON</city>";
            executeSequenceAndAssertResponse("weatherintegration", "/overwrite/false", inputPayload, "POST",
                    "The return payload does not match the expectedPayload", inputPayload, "application/xml");
            assertTrue(carbonLogReader.checkForLog("The weather template returned city in header as LONDON. " +
                    "Status code : 200.", DEFAULT_TIMEOUT));
    }

    @Test(groups = "wso2.esb", description = "Testing Invoke mediator with Template support with POST method when " +
            "overwriteBody is true and the input payload is xml")
    public void testInvokeMediatorWithOverwriteBodyFalseAndPostMethodWithXMLPayload() throws Exception {

                carbonLogReader.clearLogs();
                String inputPayload = "<city>LONDON</city>";
                String expectedOutput = "{\"city\": \"LONDON\", \"temperature - C\" : \"23\", \"weather\" : \"cloudy\"}";
                executeSequenceAndAssertResponse("weatherintegration", "/overwrite/true",
                        expectedOutput, "POST",
                        "The return payload does not match the expectedPayload", inputPayload, "application/xml");
                assertTrue(carbonLogReader.checkForLog("The weather template returned city in header as LONDON. " +
                        "Status code : 200.", DEFAULT_TIMEOUT));
    }



    private void executeSequenceAndAssertResponse(String apiName, String resource, String expectedOutput,
                                                  String httpMethod, String errorMessage, String inputPayload,
                                                  String contentType) throws Exception {

        String invocationURL = getApiInvocationURL(apiName) + resource;
        Map<String, String> headers = new HashMap<String, String>();
        if (StringUtils.isNotEmpty(contentType)) {
            headers.put("Content-Type", contentType);
        }
        HttpResponse httpResponse;
        if (httpMethod.equalsIgnoreCase("POST")) {
            URL endpoint = new URL(invocationURL);
            httpResponse = HttpRequestUtil.doPost(endpoint, inputPayload, headers);
        } else {
            httpResponse = HttpRequestUtil.doGet(invocationURL, headers);
        }
        Assert.assertEquals(httpResponse.getResponseCode(), 200, "Response code mismatched");
        Assert.assertEquals(httpResponse.getData(), expectedOutput, errorMessage);
    }
}
