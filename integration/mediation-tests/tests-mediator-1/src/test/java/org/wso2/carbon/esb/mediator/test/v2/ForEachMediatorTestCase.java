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
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.testng.Assert.assertEquals;

public class ForEachMediatorTestCase extends ESBIntegrationTest {

    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    private final String requestPayloadXML = "<data><list><name>apple</name><type>fruit</type></list><list><name>carrot</name>" +
            "<type>vegetable</type></list></data>";

    private final String requestPayload = "{\n" +
            "    \"data\": {\n" +
            "        \"list\": [\n" +
            "            {\n" +
            "                \"name\": \"apple\",\n" +
            "                \"type\": \"fruit\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"carrot\",\n" +
            "                \"type\": \"vegetable\"\n" +
            "            }\n" +
            "        ]\n" +
            "    }\n" +
            "}";

    private final String expected = "{\n" +
            "    \"data\": {\n" +
            "        \"list\": [\n" +
            "            {\n" +
            "                \"_name\": \"apple\",\n" +
            "                \"age\": 5,\n" +
            "                \"status\": true\n" +
            "            },\n" +
            "            {\n" +
            "                \"_name\": \"carrot\",\n" +
            "                \"age\": 5,\n" +
            "                \"status\": false\n" +
            "            }\n" +
            "        ]\n" +
            "    }\n" +
            "}";

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.init();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ForEach mediator with JSON array replace")
    public void testForEachJSONBodyReplace() throws IOException {

        String serviceURL = getMainSequenceURL() + "foreach/json-body-replace-1";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expected);
        assertEquals(responseJSON, expectedJSON, "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ForEach mediator with JSON array replace with external call")
    public void testForEachJSONBodyReplaceWithCallMediator() throws IOException {

        String serviceURL = getMainSequenceURL() + "foreach/json-body-replace-2";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expected);
        assertEquals(responseJSON, expectedJSON, "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ForEach mediator with JSON array output to variable")
    public void testForEachJSONBody_VariableOutput() throws IOException {

        String serviceURL = getMainSequenceURL() + "foreach/json-body-variable-output";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        String expected = "{\n" +
                "    \"foreachResult\": [\n" +
                "        {\n" +
                "            \"_name\": \"apple\",\n" +
                "            \"age\": 5\n" +
                "        },\n" +
                "        {\n" +
                "            \"_name\": \"carrot\",\n" +
                "            \"age\": 5\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expected);
        assertEquals(responseJSON, expectedJSON, "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ForEach mediator with JSON array defined as a variable")
    public void testForEachJSONVariable() throws IOException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String serviceURL = getMainSequenceURL() + "foreach/json-var-replace";
        HttpResponse httpResponse = httpClient.doGet(serviceURL, null);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), 200, "Response code mismatched");
        EntityUtils.consumeQuietly(httpResponse.getEntity());

        boolean logLine1 = carbonLogReader
                .checkForLog("Processed message : [{\"_name\":\"guava\",\"age\":5},{\"_name\":\"beet\",\"age\":5}]",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine1, "JSON array defined as a variable not replaced");
        carbonLogReader.stop();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ForEach mediator with JSON array defined as a variable and set output to a variable")
    public void testForEachJSONVariable_VariableOutput() throws IOException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String requestPayload = "{\"data\":\"abc\"}";

        String serviceURL = getMainSequenceURL() + "foreach/json-var-variable-output";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), 200, "Response code mismatched");
        EntityUtils.consumeQuietly(httpResponse.getEntity());

        boolean logLine1 = carbonLogReader
                .checkForLog("Original list : {\"data\":{\"list\":[{\"name\":\"apple\",\"type\":\"fruit\"}," +
                                "{\"name\":\"carrot\",\"type\":\"vegetable\"}]}}",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine1, "Original list not logged");

        boolean logLine2 = carbonLogReader
                .checkForLog("Foreach output : [{\"_name\":\"apple\",\"age\":5},{\"_name\":\"carrot\",\"age\":5}]",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine2, "Foreach output not logged");

        boolean logLine3 = carbonLogReader
                .checkForLog("Request Body : {\"data\":\"abc\"}",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine3, "Request body not logged");

        carbonLogReader.stop();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ForEach mediator with XML list derived from body")
    public void testForEachXMLListFromBody() throws IOException, ParserConfigurationException, SAXException {

        String expectedResponse = "<data><person><surname>apple</surname><age>10</age></person>" +
                "<person><surname>carrot</surname><age>10</age></person></data>";

        String serviceURL = getMainSequenceURL() + "foreach/xml-body-replace";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayloadXML, "application/xml");
        String responsePayload = httpClient.getResponsePayload(httpResponse);
        Document document1 = parseXML(expectedResponse);
        Document document2 = parseXML(responsePayload);

        if (!document1.isEqualNode(document2)) {
            Assert.fail("Response payload mismatched: " + responsePayload + " expected: " + expectedResponse);
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ForEach mediator with XML list derived from body and set output to a variable")
    public void testForEachXMLListFromBody_VariableOutput() throws IOException, ParserConfigurationException, SAXException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String serviceURL = getMainSequenceURL() + "foreach/xml-body-variable-output";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayloadXML, "application/xml");

        boolean logLine = carbonLogReader
                .checkForLog("Processed result : <foreach_result><person><surname>apple</surname><age>2</age>" +
                                "</person><person><surname>carrot</surname><age>2</age></person></foreach_result>",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine, "Foreach output not logged");

        carbonLogReader.stop();

        String responsePayload = httpClient.getResponsePayload(httpResponse);
        Document document1 = parseXML(requestPayloadXML);
        Document document2 = parseXML(responsePayload);

        if (!document1.isEqualNode(document2)) {
            Assert.fail("Response payload mismatched: " + responsePayload + " expected: " + requestPayloadXML);
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ForEach mediator with XML list derived from a variable and set output to a variable")
    public void testForEachXMLListFromVariable_VariableOutput() throws IOException, ParserConfigurationException, SAXException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String serviceURL = getMainSequenceURL() + "foreach/xml-var-variable-output";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayloadXML, "application/xml");

        boolean originalList = carbonLogReader
                .checkForLog("Original collection : <data><list><name>apple</name><type>fruit</type></list>" +
                                "<list><name>carrot</name><type>vegetable</type></list></data>",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(originalList, "Original XML list not logged");

        boolean forEachOutput = carbonLogReader
                .checkForLog("Processed collection : <foreach_result><person><surname>apple</surname><age>2</age></person>" +
                                "<person><surname>carrot</surname><age>2</age></person></foreach_result>",
                        DEFAULT_TIMEOUT);
        Assert.assertTrue(forEachOutput, "Foreach output not logged");

        carbonLogReader.stop();

        String responsePayload = httpClient.getResponsePayload(httpResponse);
        Document document1 = parseXML(requestPayloadXML);
        Document document2 = parseXML(responsePayload);

        if (!document1.isEqualNode(document2)) {
            Assert.fail("Response payload mismatched: " + responsePayload + " expected: " + requestPayloadXML);
        }
    }

    private static Document parseXML(String xml) throws IOException, ParserConfigurationException, SAXException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xml.getBytes()));
        document.normalizeDocument();
        return document;
    }

    @AfterClass(alwaysRun = true)
    private void destroy() throws Exception {

        super.cleanup();
    }
}
