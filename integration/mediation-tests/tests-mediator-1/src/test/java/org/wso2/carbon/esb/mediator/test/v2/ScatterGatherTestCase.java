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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.testng.Assert.assertTrue;

public class ScatterGatherTestCase extends ESBIntegrationTest {

    SimpleHttpClient httpClient = new SimpleHttpClient();
    CarbonLogReader carbonLogReader = new CarbonLogReader();

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.init();
        carbonLogReader.start();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Scatter-Gather mediator with JSON body replace")
    public void testScatterGatherJSONBodyReplace() throws IOException {

        String expectedResponse = "[\n" +
                "   {\n" +
                "      \"name\":\"pet1\",\n" +
                "      \"type\":\"dog\",\n" +
                "      \"requestId\":1114567\n" +
                "   },\n" +
                "   {\n" +
                "      \"name\":\"pet2\",\n" +
                "      \"type\":\"cat\",\n" +
                "      \"requestId\":1114567\n" +
                "   },\n" +
                "   {\n" +
                "      \"name\":\"pet3\",\n" +
                "      \"type\":\"mock-backend\",\n" +
                "      \"requestId\":1114567\n" +
                "   }\n" +
                "]";

        String requestPayload = "{\n" +
                "    \"requestId\": 1114567\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "scatter-gather/json-body-replace";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expectedResponse);
        assertTrue(areJsonElementsEquivalent(expectedJSON, responseJSON), "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Scatter-Gather mediator with JSON and variable output")
    public void testScatterGatherJSONVariableOutput() throws IOException, InterruptedException {

        String requestPayload = "{\n" +
                "    \"requestId\": 1114567\n" +
                "}";
        String expected = "{\n" +
                "   \"response\":{\n" +
                "      \"requestData\":{\n" +
                "         \"requestId\":1114567\n" +
                "      },\n" +
                "      \"scatterGatherOutput\":[\n" +
                "         {\n" +
                "            \"name\":\"pet1\",\n" +
                "            \"type\":\"dog\",\n" +
                "            \"requestId\":1114567\n" +
                "         },\n" +
                "         {\n" +
                "            \"name\":\"pet2\",\n" +
                "            \"type\":\"cat\",\n" +
                "            \"requestId\":1114567\n" +
                "         },\n" +
                "         {\n" +
                "            \"name\":\"pet3\",\n" +
                "            \"type\":\"mock-backend\",\n" +
                "            \"requestId\":1114567\n" +
                "         }\n" +
                "      ]\n" +
                "   }\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "scatter-gather/json-variable-output";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expected);
        assertTrue(areJsonElementsEquivalent(expectedJSON, responseJSON), "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Scatter-Gather mediator with XML body replace")
    public void testScatterGatherXMLBodyReplace() throws IOException, ParserConfigurationException, SAXException {

        String expectedResponse = "<scatter_response><pet><name>pet1</name><type>cat</type><requestId>78658</requestId>" +
                "</pet><pet><name>pet2</name><type>mock-backend</type><requestId>78658</requestId></pet></scatter_response>";

        String requestPayload = "<root>\n" +
                "    <requestId>78658</requestId>\n" +
                "</root>";

        String serviceURL = getMainSequenceURL() + "scatter-gather/xml-body-replace";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/xml");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        Document document1 = parseXML(expectedResponse);
        Document document2 = parseXML(responsePayload);

        if (!document1.isEqualNode(document2)) {
            Assert.fail("Response payload mismatched: " + responsePayload + " expected: " + expectedResponse);
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Scatter-Gather mediator with XML and variable output")
    public void testScatterGatherXMLVariableOutput() throws IOException, InterruptedException, ParserConfigurationException, SAXException {

        carbonLogReader.clearLogs();
        String requestPayload = "<root>\n" +
                "    <requestId>78658</requestId>\n" +
                "</root>";

        String serviceURL = getMainSequenceURL() + "scatter-gather/xml-variable-output";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/xml");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        Document document1 = parseXML(requestPayload);
        Document document2 = parseXML(responsePayload);

        if (!document1.isEqualNode(document2)) {
            Assert.fail("Response payload mismatched: " + responsePayload + " expected: " + requestPayload);
        }

        boolean logFound = carbonLogReader
                .checkForLog("Scatter Gather XML output = <scatter_response><pet><name>pet1</name><type>cat</type>" +
                        "<requestId>78658</requestId></pet><pet><name>pet2</name><type>dog</type><requestId>78658</requestId>" +
                        "</pet></scatter_response>", DEFAULT_TIMEOUT);
        Assert.assertTrue(logFound, "Scatter Gather result not set to variable");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Scatter-Gather mediator with Aggregation condition")
    public void testScatterGatherJSONCondition() throws IOException {

        String expectedResponse = "[\n" +
                "   {\n" +
                "      \"name\":\"pet1\",\n" +
                "      \"type\":\"dog\",\n" +
                "      \"requestId\":1114567\n" +
                "   },\n" +
                "   {\n" +
                "      \"name\":\"pet3\",\n" +
                "      \"type\":\"dog\",\n" +
                "      \"requestId\":1114567\n" +
                "   }\n" +
                "]";

        String requestPayload = "{\n" +
                "    \"requestId\": 1114567\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "scatter-gather/aggregate-condition";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expectedResponse);
        assertTrue(areJsonElementsEquivalent(expectedJSON, responseJSON), "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Scatter-Gather mediator when a path fails")
    public void testScatterGatherJSON404EPClone() throws IOException {

        String expectedResponse = "[\n" +
                "   {\n" +
                "      \"name\":\"pet1\",\n" +
                "      \"type\":\"dog\",\n" +
                "      \"requestId\":1114567\n" +
                "   },\n" +
                "   {\n" +
                "      \"name\":\"pet3\",\n" +
                "      \"type\":\"dog\",\n" +
                "      \"requestId\":1114567\n" +
                "   }\n" +
                "]";

        String requestPayload = "{\n" +
                "    \"requestId\": 1114567\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "scatter-gather/not-found-ep";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expectedResponse);
        assertTrue(areJsonElementsEquivalent(expectedJSON, responseJSON), "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Scatter-Gather mediator nested sequential calls")
    public void testScatterGatherNestedInSequentialMode() throws IOException {

        String expectedResponse = "[\n" +
                "    {\n" +
                "        \"pet\": {\n" +
                "            \"name\": \"pet1\",\n" +
                "            \"type\": \"dog\",\n" +
                "            \"requestId\": 123\n" +
                "        }\n" +
                "    },\n" +
                "    {\n" +
                "        \"pet\": {\n" +
                "            \"name\": \"pet2\",\n" +
                "            \"type\": \"cat\",\n" +
                "            \"requestId\": 123\n" +
                "        }\n" +
                "    }\n" +
                "]";

        String requestPayload = "{\n" +
                "    \"requestId\": 123\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "scatter-gather/nested-sequential";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expectedResponse);
        assertTrue(areJsonElementsEquivalent(expectedJSON, responseJSON), "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Scatter-Gather mediator nested parallele calls")
    public void testScatterGatherNestedInParallelMode() throws IOException {

        String expectedResponse = "[\n" +
                "    {\n" +
                "        \"pet\": {\n" +
                "            \"name\": \"pet1\",\n" +
                "            \"type\": \"dog\",\n" +
                "            \"requestId\": 123\n" +
                "        }\n" +
                "    },\n" +
                "    {\n" +
                "        \"pet\": {\n" +
                "            \"name\": \"pet2\",\n" +
                "            \"type\": \"cat\",\n" +
                "            \"requestId\": 123\n" +
                "        }\n" +
                "    }\n" +
                "]";

        String requestPayload = "{\n" +
                "    \"requestId\": 123\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "scatter-gather/nested-parallel";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expectedResponse);
        assertTrue(areJsonElementsEquivalent(expectedJSON, responseJSON), "Response payload mismatched");
    }

    private static boolean areJsonElementsEquivalent(JsonElement e1, JsonElement e2) {

        if (e1.isJsonObject() && e2.isJsonObject()) {
            JsonObject obj1 = e1.getAsJsonObject();
            JsonObject obj2 = e2.getAsJsonObject();

            if (obj1.size() != obj2.size()) {
                return false;
            }

            for (String key : obj1.keySet()) {
                if (!obj2.has(key) || !areJsonElementsEquivalent(obj1.get(key), obj2.get(key))) {
                    return false;
                }
            }
            return true;
        } else if (e1.isJsonArray() && e2.isJsonArray()) {
            if (e1.getAsJsonArray().size() != e2.getAsJsonArray().size()) {
                return false;
            }

            List<JsonElement> list1 = new ArrayList<>();
            e1.getAsJsonArray().forEach(list1::add);
            List<JsonElement> list2 = new ArrayList<>();
            e2.getAsJsonArray().forEach(list2::add);

            list1.sort(Comparator.comparing(JsonElement::toString));
            list2.sort(Comparator.comparing(JsonElement::toString));

            for (int i = 0; i < list1.size(); i++) {
                if (!areJsonElementsEquivalent(list1.get(i), list2.get(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return e1.equals(e2);
        }
    }

    private static Document parseXML(String xml) throws IOException, ParserConfigurationException, SAXException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
    }

    @AfterClass(alwaysRun = true)
    private void destroy() throws Exception {

        carbonLogReader.stop();
        super.cleanup();
    }
}
