/*
 * Copyright (c) (2017-2022), WSO2 Inc. (http://www.wso2.com).
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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
 * under the License.
 */

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch;

import com.damnhandy.uri.template.UriTemplate;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import junit.framework.TestCase;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.om.util.StAXUtils;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SequenceType;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.api.API;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.rest.RestRequestHandler;
import org.apache.synapse.aspects.ComponentType;
import org.apache.synapse.aspects.flow.statistics.elasticsearch.ElasticMetadata;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingEvent;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingFlow;
import org.apache.synapse.aspects.flow.statistics.util.StatisticsConstants;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.endpoints.HTTPEndpointFactory;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2SynapseEnvironment;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.EndpointDefinition;
import org.apache.synapse.endpoints.HTTPEndpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.netty.BridgeConstants;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.producer.AnalyticsCustomDataProvider;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.schema.ElasticDataSchema;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

public class ElasticStatisticsTest extends TestCase {
    private static final String SERVER_INFO_SERVER_NAME = "wso2.dev";
    private static final String SERVER_INFO_HOST_NAME = "dev.local";
    private static final String SERVER_INFO_IP_ADDRESS = "1.2.3.4";
    private static final String SERVER_INFO_PUBLISHER_ID = "WSO2_UNIT_TEST";

    private static final String TEST_API_NAME = "TestAPI";
    private static final String TEST_API_CONTEXT = "/test";
    private static final String TEST_API_URL = "/test/admin?search=wso2";
    private static final String TEST_API_METHOD = "GET";
    private static final String TEST_API_PROTOCOL = "https";
    private static final String TEST_SEQUENCE_NAME = "TestSequenceName";
    private static final String TEST_PROXY_SERVICE = "TestProxyServiceName";
    private static final String TEST_ENDPOINT_NAME = "TestEndpointName";
    private static final String TEST_REMOTE_HOST = "127.0.0.1";
    private static final String TEST_CONTENT_TYPE = "application/json";
    private static final String TEST_FLOW_ID = "WSO2_UT_00001";
    private static final int STATIC_LATENCY = 5678;
    private static final int TENANT_ID = 1234;

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private final TestElasticStatisticsPublisher publisher = new TestElasticStatisticsPublisher();
    ServerConfigurationInformation sysConfig = null;
    private boolean oneTimeSetupComplete = false;
    private Axis2MessageContext messageContext = null;
    private Axis2SynapseEnvironment synapseEnvironment = null;

    private static MessageContext createSynapseMessageContext(
            SynapseConfiguration testConfig) throws Exception {

        SynapseEnvironment synEnv
                = new Axis2SynapseEnvironment(new ConfigurationContext(new AxisConfiguration()),
                testConfig);
        Axis2MessageContext synCtx;
        org.apache.axis2.context.MessageContext mc = new org.apache.axis2.context.MessageContext();
        mc.setIncomingTransportName(TEST_API_PROTOCOL);
        synCtx = new Axis2MessageContext(mc, testConfig, synEnv);

        XMLStreamReader parser = StAXUtils.createXMLStreamReader(new StringReader("<test>value</test>"));

        SOAPEnvelope envelope;
        envelope = OMAbstractFactory.getSOAP11Factory().getDefaultEnvelope();
        OMDocument omDoc = OMAbstractFactory.getSOAP11Factory().createOMDocument();
        omDoc.addChild(envelope);

        SOAPBody body = envelope.getBody();
        StAXOMBuilder builder = new StAXOMBuilder(parser);
        OMElement bodyElement = builder.getDocumentElement();
        body.addChild(bodyElement);
        synCtx.setEnvelope(envelope);

        String url = TEST_API_URL;

        synCtx.setProperty(Constants.Configuration.HTTP_METHOD, TEST_API_METHOD);
        synCtx.setProperty(BridgeConstants.REMOTE_HOST, TEST_REMOTE_HOST);
        synCtx.setProperty(BridgeConstants.CONTENT_TYPE_HEADER, TEST_CONTENT_TYPE);
        synCtx.setProperty(NhttpConstants.REST_URL_POSTFIX, url.substring(1));

        Axis2MessageContext axisCtx = synCtx;
        axisCtx.getAxis2MessageContext().setProperty(Constants.Configuration.HTTP_METHOD, TEST_API_METHOD);
        axisCtx.getAxis2MessageContext().setProperty(
                Constants.Configuration.TRANSPORT_IN_URL, "https://" + SERVER_INFO_HOST_NAME + url);
        return synCtx;
    }

    private void setupSynapseConfig(SynapseConfiguration synapseConfig) throws XMLStreamException {
        API api = new API(TEST_API_NAME, TEST_API_CONTEXT);
        Resource resource = new Resource();
        api.addResource(resource);
        synapseConfig.addAPI(api.getName(), api);

        SequenceMediator sequence = new SequenceMediator();
        sequence.setSequenceType(SequenceType.NAMED);
        sequence.setName(TEST_SEQUENCE_NAME);
        synapseConfig.addSequence(TEST_SEQUENCE_NAME, sequence);

        HTTPEndpointFactory factory = new HTTPEndpointFactory();
        OMElement em = AXIOMUtil.stringToOM(
                "<endpoint><http method=\"GET\" uri-template=\"https://wso2.com\"/></endpoint>");
        EndpointDefinition endpoint = factory.createEndpointDefinition(em);
        HTTPEndpoint httpEndpoint = new HTTPEndpoint();
        httpEndpoint.setName(TEST_ENDPOINT_NAME);
        httpEndpoint.setHttpMethod(TEST_API_METHOD);
        httpEndpoint.setDefinition(endpoint);
        httpEndpoint.setUriTemplate(UriTemplate.fromTemplate("https://wso2.com"));
        synapseConfig.addEndpoint(TEST_ENDPOINT_NAME, httpEndpoint);
        httpEndpoint.init(synapseEnvironment);

        ProxyService proxyService = new ProxyService(TEST_PROXY_SERVICE);
        synapseConfig.addProxyService(TEST_PROXY_SERVICE, proxyService);
    }

    private void oneTimeSetup() throws Exception {
        if (oneTimeSetupComplete) {
            return;
        }

        sysConfig = new ServerConfigurationInformation();
        sysConfig.setServerName(SERVER_INFO_SERVER_NAME);
        sysConfig.setHostName(SERVER_INFO_HOST_NAME);
        sysConfig.setIpAddress(SERVER_INFO_IP_ADDRESS);
        ElasticDataSchema.setPublisherId(SERVER_INFO_PUBLISHER_ID);
        ElasticDataSchema.setupServerMetadata(sysConfig);
        ConfigurationContext axis2ConfigurationContext = new ConfigurationContext(new AxisConfiguration());
        axis2ConfigurationContext.getAxisConfiguration().addParameter(SynapseConstants.SYNAPSE_ENV, synapseEnvironment);
        SynapseConfiguration config = new SynapseConfiguration();
        synapseEnvironment = new Axis2SynapseEnvironment(axis2ConfigurationContext, config);
        setupSynapseConfig(config);
        messageContext = (Axis2MessageContext) createSynapseMessageContext(config);
        oneTimeSetupComplete = true;
    }

    @Override
    protected void setUp() throws Exception {
        oneTimeSetup();
        publisher.enableService();
    }

    @Override
    protected void tearDown() {
        publisher.reset();
    }

    public void testPublisherEnabledState() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.SEQUENCE, TEST_SEQUENCE_NAME));
        publisher.enableService();
        publisher.process(flow, TENANT_ID);
        assertTrue(publisher.isEnabled());
        assertEquals(1, publisher.getAnalyticsCount());
        verifySchema(publisher.getAnalyticData(), AnalyticPayloadType.NON_STANDARD);
        publisher.reset();
        publisher.disableService();
        publisher.process(flow, TENANT_ID);
        assertEquals(0, publisher.getAnalyticsCount());
    }

    public void testSequenceAnalytics() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.SEQUENCE, TEST_SEQUENCE_NAME));
        publisher.process(flow, TENANT_ID);
        assertEquals(1, publisher.getAnalyticsCount());
        verifySchema(publisher.getAnalyticData(), AnalyticPayloadType.SEQUENCE);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    public void testApiResourceAnalytics() {
        RestRequestHandler handler = new RestRequestHandler();
        handler.process(messageContext);
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.API, TEST_API_NAME));
        publisher.process(flow, TENANT_ID);
        assertEquals(1, publisher.getAnalyticsCount());
        verifySchema(publisher.getAnalyticData(), AnalyticPayloadType.API);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    public void testEndpointAnalytics() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.ENDPOINT, TEST_ENDPOINT_NAME));
        publisher.process(flow, TENANT_ID);
        assertEquals(1, publisher.getAnalyticsCount());
        verifySchema(publisher.getAnalyticData(), AnalyticPayloadType.ENDPOINT);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    public void testProxyServiceAnalytics() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.PROXYSERVICE, TEST_PROXY_SERVICE));
        publisher.process(flow, TENANT_ID);
        verifySchema(publisher.getAnalyticData(), AnalyticPayloadType.PROXY_SERVICE);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    private PublishingEvent createPublishingEvent(ComponentType componentType, String componentName) {
        PublishingEvent event = new PublishingEvent();
        event.setComponentType(StatisticsConstants.getComponentTypeToString(componentType));
        event.setFlowId(TEST_FLOW_ID);
        event.setComponentName(componentName);
        long currentTime = Instant.now().toEpochMilli();
        event.setStartTime(currentTime - STATIC_LATENCY);
        event.setEndTime(currentTime);
        event.setDuration(event.getEndTime() - event.getStartTime());
        event.setEntryPoint("EP");
        event.setFaultCount(0);
        event.setElasticMetadata(createElasticMetadata());
        return event;
    }

    private ElasticMetadata createElasticMetadata() {
        AnalyticsCustomDataProvider customDataProvider = new SampleCustomDataProvider();
        Map<String, Object> customProperties = customDataProvider.getCustomProperties(messageContext);
        Map<String, Object> contextProperties =  new HashMap<>(messageContext.getProperties());
        contextProperties.computeIfAbsent(SynapseConstants.ANALYTICS_METADATA, k -> new HashMap<>());
        ((HashMap<String, Object>) contextProperties.get(SynapseConstants.ANALYTICS_METADATA)).putAll(customProperties);
        return new ElasticMetadata(
                messageContext.getConfiguration(),
                messageContext.isFaultResponse(),
                messageContext.getMessageID(),
                messageContext.getContextEntries(),
                contextProperties
        );
    }

    private void verifySchema(JsonObject analytic, AnalyticPayloadType payloadType) {
        assertNotNull(analytic);
        verifySchemaVersion(analytic.get(ElasticConstants.EnvelopDef.SCHEMA_VERSION));
        verifyServerInfo(analytic.get(ElasticConstants.EnvelopDef.SERVER_INFO));
        verifyTimestamp(analytic.get(ElasticConstants.EnvelopDef.TIMESTAMP));

        JsonElement payloadElement = analytic.get(ElasticConstants.EnvelopDef.PAYLOAD);
        switch (payloadType) {
            case PROXY_SERVICE:
                verifyProxyServicePayload(payloadElement);
                break;
            case ENDPOINT:
                verifyEndpointPayload(payloadElement);
                break;
            case API:
                verifyApiResourcePayload(payloadElement);
                break;
            case SEQUENCE:
                verifySequencePayload(payloadElement);
                break;
            default:
                assertTrue(payloadElement.isJsonObject());
        }
        verifyCustomProperties(payloadElement);
    }

    private void verifyServerInfo(JsonElement serverInfoElement) {
        assertNotNull(serverInfoElement);
        assertTrue(serverInfoElement.isJsonObject());

        JsonObject dataObject = serverInfoElement.getAsJsonObject();
        assertTrue(dataObject.has(ElasticConstants.ServerMetadataFieldDef.HOST_NAME));
        assertEquals(SERVER_INFO_HOST_NAME,
                dataObject.get(ElasticConstants.ServerMetadataFieldDef.HOST_NAME).getAsString());
        assertTrue(dataObject.has(ElasticConstants.ServerMetadataFieldDef.SERVER_NAME));
        assertEquals(SERVER_INFO_SERVER_NAME,
                dataObject.get(ElasticConstants.ServerMetadataFieldDef.SERVER_NAME).getAsString());
        assertTrue(dataObject.has(ElasticConstants.ServerMetadataFieldDef.IP_ADDRESS));
        assertEquals(SERVER_INFO_IP_ADDRESS,
                dataObject.get(ElasticConstants.ServerMetadataFieldDef.IP_ADDRESS).getAsString());
        assertTrue(dataObject.has(ElasticConstants.ServerMetadataFieldDef.PUBLISHER_ID));
        assertEquals(SERVER_INFO_PUBLISHER_ID,
                dataObject.get(ElasticConstants.ServerMetadataFieldDef.PUBLISHER_ID).getAsString());
    }

    private void verifyTimestamp(JsonElement timestampElement) {
        assertNotNull(timestampElement);

        try {
            Instant.parse(timestampElement.getAsString());
        } catch (DateTimeParseException e) {
            fail("timestamp should be in ISO8601 format. Found: " + timestampElement.getAsString());
        }
    }

    private void verifySchemaVersion(JsonElement schemaVersionElement) {
        assertNotNull(schemaVersionElement);
        assertEquals(CURRENT_SCHEMA_VERSION, schemaVersionElement.getAsInt());
    }

    private void verifySequencePayload(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        verifyCommonPayloadFields(payload);
        assertTrue(payload.has(ElasticConstants.EnvelopDef.SEQUENCE_DETAILS));
        assertTrue(payload.get(ElasticConstants.EnvelopDef.SEQUENCE_DETAILS).isJsonObject());

        JsonObject sequenceDetails = payload.get(ElasticConstants.EnvelopDef.SEQUENCE_DETAILS).getAsJsonObject();
        assertTrue(sequenceDetails.has(ElasticConstants.EnvelopDef.SEQUENCE_NAME));
    }

    private void verifyApiResourcePayload(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        verifyCommonPayloadFields(payload);

        assertTrue(payload.has(ElasticConstants.EnvelopDef.REMOTE_HOST));
        assertTrue(payload.has(ElasticConstants.EnvelopDef.CONTENT_TYPE));
        assertTrue(payload.has(ElasticConstants.EnvelopDef.HTTP_METHOD));
        assertEquals(TEST_API_METHOD, payload.get(ElasticConstants.EnvelopDef.HTTP_METHOD).getAsString());

        assertTrue(payload.has(ElasticConstants.EnvelopDef.API_DETAILS));
        assertTrue(payload.get(ElasticConstants.EnvelopDef.API_DETAILS).isJsonObject());

        JsonObject apiDetails = payload.get(ElasticConstants.EnvelopDef.API_DETAILS).getAsJsonObject();
        assertTrue(apiDetails.has(ElasticConstants.EnvelopDef.API));
        assertEquals(TEST_API_NAME, apiDetails.get(ElasticConstants.EnvelopDef.API).getAsString());
        assertTrue(apiDetails.has(ElasticConstants.EnvelopDef.SUB_REQUEST_PATH));
        assertTrue(apiDetails.has(ElasticConstants.EnvelopDef.API_CONTEXT));
        assertEquals(TEST_API_CONTEXT, apiDetails.get(ElasticConstants.EnvelopDef.API_CONTEXT).getAsString());
        assertTrue(apiDetails.has(ElasticConstants.EnvelopDef.METHOD));
        assertEquals(TEST_API_METHOD, apiDetails.get(ElasticConstants.EnvelopDef.METHOD).getAsString());
    }

    private void verifyEndpointPayload(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        verifyCommonPayloadFields(payload);

        assertTrue(payload.has(ElasticConstants.EnvelopDef.ENDPOINT_DETAILS));
        JsonObject endpointDetails = payload.get(ElasticConstants.EnvelopDef.ENDPOINT_DETAILS).getAsJsonObject();
        assertTrue(endpointDetails.has(ElasticConstants.EnvelopDef.ENDPOINT_NAME));
        assertEquals(TEST_ENDPOINT_NAME, endpointDetails.get(ElasticConstants.EnvelopDef.ENDPOINT_NAME).getAsString());
    }

    private void verifyProxyServicePayload(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        verifyCommonPayloadFields(payload);

        assertTrue(payload.has(ElasticConstants.EnvelopDef.PROXY_SERVICE_DETAILS));
        JsonObject proxyServiceDetails = payload.get(ElasticConstants.EnvelopDef.PROXY_SERVICE_DETAILS).getAsJsonObject();
        assertTrue(proxyServiceDetails.has(ElasticConstants.EnvelopDef.PROXY_SERVICE_NAME));
    }

    private void verifyCommonPayloadFields(JsonObject payload) {
        assertTrue(payload.has(ElasticConstants.EnvelopDef.ENTITY_TYPE));
        assertTrue(payload.has(ElasticConstants.EnvelopDef.ENTITY_CLASS_NAME));
        assertTrue(payload.has(ElasticConstants.EnvelopDef.FAULT_RESPONSE));
        assertTrue(payload.has(ElasticConstants.EnvelopDef.FAILURE));
        assertTrue(payload.has(ElasticConstants.EnvelopDef.LATENCY));
        assertEquals(STATIC_LATENCY, payload.get(ElasticConstants.EnvelopDef.LATENCY).getAsInt());
        assertTrue(payload.has(ElasticConstants.EnvelopDef.METADATA));
    }

    private void verifyCustomProperties(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        assertTrue(payload.has(ElasticConstants.EnvelopDef.METADATA));
        assertTrue(payload.get(ElasticConstants.EnvelopDef.METADATA).isJsonObject());

        JsonObject metadata = payload.get(ElasticConstants.EnvelopDef.METADATA).getAsJsonObject();
        assertTrue(metadata.has("messageBody"));
        assertTrue(metadata.get("messageBody").isJsonPrimitive());

        String messageBody = metadata.get("messageBody").getAsString();
        assertEquals(messageContext.getEnvelope().getBody().toString(), messageBody);
    }

    enum AnalyticPayloadType {
        PROXY_SERVICE,
        ENDPOINT,
        API,
        SEQUENCE,
        NON_STANDARD
    }
}
