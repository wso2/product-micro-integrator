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
 *
 */

package org.wso2.carbon.esb.mediator.test.payload.factory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;
import org.xml.sax.SAXException;

import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.testng.Assert.assertEquals;

/**
 * Test case for payload factory mediator with synapse expressions
 */
public class PayloadFactorySynapseExpressionTestCase extends ESBIntegrationTest {

    SimpleHttpClient httpClient = new SimpleHttpClient();

    @BeforeClass(alwaysRun = true)
    public void uploadSynapseConfig() throws Exception {

        super.init();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Payload factory mediator with json to json transformation")
    public void testPayloadFactoryJsonToJson() throws IOException {

        String expectedResponse = "{\n" +
                "    \"variable\": 123,\n" +
                "    \"username\": \"[]John&\\nDoe]\",\n" +
                "    \"escapedVariable\": \"123\",\n" +
                "    \"bool\": true,\n" +
                "    \"escapedBool\": \"true\",\n" +
                "    \"escapedObject\": \"{\\\"type\\\":\\\"cat\\\\n\\\\rdog\\\",\\\"name\\\":\\\"john\\\"}\",\n" +
                "    \"object\": {\n" +
                "        \"type\": \"cat\\n\\rdog\",\n" +
                "        \"name\": \"john\"\n" +
                "    }\n" +
                "}";

        String requestPayload = "{\n" +
                "    \"customer_name\": \"[]John&\\nDoe]\",\n" +
                "    \"pet\": {\n" +
                "        \"type\":\"cat\\n\\rdog\",\n" +
                "        \"name\":\"john\"\n" +
                "    },\n" +
                "    \"path\": true\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "synapseExpressionPayload/json-json";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);
        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expectedResponse);
        assertEquals(responseJSON, expectedJSON, "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Payload factory mediator with json to xml transformation")
    public void testPayloadFactoryJsonToXML() throws IOException, ParserConfigurationException, SAXException {

        String expectedResponse = "<sleepOperation><username>123&lt;456</username><name>abc\\\\ndef</name>" +
                "<load><fruit>apple</fruit></load><data><type>cat&amp;</type><name>abc\\\\ndef</name></data>" +
                "<special_json_str>123\\n456</special_json_str></sleepOperation>";

        String requestPayload = "{\n" +
                "    \"customer_name\": \"John<Doe\",\n" +
                "    \"pet\": {\n" +
                "        \"type\":\"cat&\",\n" +
                "        \"name\":\"abc\\\\ndef\"\n" +
                "    },\n" +
                "    \"path\": true\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "synapseExpressionPayload/payload-json-xml";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        Document document1 = parseXML(expectedResponse);
        Document document2 = parseXML(responsePayload);

        if (!document1.isEqualNode(document2)) {
            Assert.fail("Response payload mismatched: " + responsePayload + " expected: " + expectedResponse);
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Payload factory mediator with xml to xml transformation")
    public void testPayloadFactoryXMLToXML() throws IOException, ParserConfigurationException, SAXException {

        String expectedResponse = "<sleepOperation><username>John&lt;</username><load>123&lt;456</load><json>123\\n456</json><data><pet>\n" +
                "        <type>cat</type>\n" +
                "        <name>abc\\nxyz</name>\n" +
                "    </pet></data></sleepOperation>";

        String requestPayload = "<root>\n" +
                "    <customer_name>John&lt;</customer_name>\n" +
                "    <pet>\n" +
                "        <type>cat</type>\n" +
                "        <name>abc\\nxyz</name>\n" +
                "    </pet>\n" +
                "    <path>true</path>\n" +
                "</root>";

        String serviceURL = getMainSequenceURL() + "synapseExpressionPayload/payload-xml-xml";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/xml");
        String responsePayload = httpClient.getResponsePayload(httpResponse);

        Document document1 = parseXML(expectedResponse);
        Document document2 = parseXML(responsePayload);

        if (!document1.isEqualNode(document2)) {
            Assert.fail("Response payload mismatched: " + responsePayload + " expected: " + expectedResponse);
        }
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Payload factory mediator with xml to json transformation")
    public void testPayloadFactoryXMLToJson() throws IOException {

        String expectedResponse = "{\n" +
                "    \"string\": \"John<\",\n" +
                "    \"escapedObject\": \"<pet><type>cat</type><name>emily\\\\\\\\ntini</name></pet>\",\n" +
                "    \"escapedObject2\": \"<pet2>\\n        <type>dog&lt;</type>\\n        <name>John\\\\\\\\nWayne</name>\\n    </pet2>\",\n" +
                "    \"object\": {\n" +
                "        \"pet\": {\n" +
                "            \"type\": \"cat\",\n" +
                "            \"name\": \"emily\\\\ntini\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"xml\": \"123<456\",\n" +
                "    \"json\": \"123\\n456\"\n" +
                "}";

        String requestPayload = "<root>\n" +
                "    <customer_name>John&lt;</customer_name>\n" +
                "    <pet><type>cat</type><name>emily\\\\ntini</name></pet>\n" +
                "    <pet2>\n" +
                "        <type>dog&lt;</type>\n" +
                "        <name>John\\\\nWayne</name>\n" +
                "    </pet2>\n" +
                "    <path>true</path>\n" +
                "</root>";

        String serviceURL = getMainSequenceURL() + "synapseExpressionPayload/payload-xml-json";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/xml");
        String responsePayload = httpClient.getResponsePayload(httpResponse);
        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expectedResponse);
        assertEquals(responseJSON, expectedJSON, "Response payload mismatched");
    }

    @Test(groups = {"wso2.esb"}, description = "Testing Payload factory mediator with Freemarker template")
    public void testPayloadFactoryFreemarker() throws IOException {

        String expectedResponse = "{\n" +
                "    \"name\": \"Johe Doe\",\n" +
                "    \"id\": 123\n" +
                "}";

        String requestPayload = "{\n" +
                "    \"firstname\": \"Johe\",\n" +
                "    \"lastname\": \"Doe\",\n" +
                "    \"path\": true\n" +
                "}";

        String serviceURL = getMainSequenceURL() + "synapseExpressionPayload/payload-freemarker";
        HttpResponse httpResponse = httpClient.doPost(serviceURL, null, requestPayload, "application/json");
        String responsePayload = httpClient.getResponsePayload(httpResponse);
        JsonElement responseJSON = JsonParser.parseString(responsePayload);
        JsonElement expectedJSON = JsonParser.parseString(expectedResponse);
        assertEquals(responseJSON, expectedJSON, "Response payload mismatched");
    }

    private static Document parseXML(String xml) throws IOException, ParserConfigurationException, SAXException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
    }

    @AfterClass(alwaysRun = true)
    private void destroy() throws Exception {

        super.cleanup();
    }
}
