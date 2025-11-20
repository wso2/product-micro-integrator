/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif;

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
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.PublisherTestUtils;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticConstants;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.SampleCustomDataProvider;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MoesifStatisticsTest extends TestCase {
    private final TestMoesifStatisticsPublisher publisher = new TestMoesifStatisticsPublisher();
    private ServerConfigurationInformation sysConfig = null;
    private boolean oneTimeSetupComplete = false;
    private Axis2MessageContext messageContext = null;
    private Axis2SynapseEnvironment synapseEnvironment = null;

    private static MessageContext createSynapseMessageContext(SynapseConfiguration testConfig) throws Exception {
        SynapseEnvironment synEnv = new Axis2SynapseEnvironment(new ConfigurationContext(new AxisConfiguration()),
                testConfig);
        Axis2MessageContext synCtx;
        org.apache.axis2.context.MessageContext mc = new org.apache.axis2.context.MessageContext();
        mc.setIncomingTransportName(PublisherTestUtils.TEST_API_PROTOCOL);
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

        String url = PublisherTestUtils.TEST_API_URL;

        synCtx.setProperty(Constants.Configuration.HTTP_METHOD, PublisherTestUtils.TEST_API_METHOD);
        synCtx.setProperty(BridgeConstants.REMOTE_HOST, PublisherTestUtils.TEST_REMOTE_HOST);
        synCtx.setProperty(BridgeConstants.CONTENT_TYPE_HEADER, PublisherTestUtils.TEST_CONTENT_TYPE);
        synCtx.setProperty(NhttpConstants.REST_URL_POSTFIX, url.substring(1));

        Axis2MessageContext axisCtx = synCtx;
        axisCtx.getAxis2MessageContext().setProperty(Constants.Configuration.HTTP_METHOD,
                PublisherTestUtils.TEST_API_METHOD);
        axisCtx.getAxis2MessageContext().setProperty(Constants.Configuration.TRANSPORT_IN_URL,
                "https://" + PublisherTestUtils.SERVER_INFO_HOST_NAME + url);
        return synCtx;
    }

    private void setupSynapseConfig(SynapseConfiguration synapseConfig) throws XMLStreamException {
        API api = new API(PublisherTestUtils.TEST_API_NAME, PublisherTestUtils.TEST_API_CONTEXT);
        Resource resource = new Resource();
        api.addResource(resource);
        synapseConfig.addAPI(api.getName(), api);

        SequenceMediator sequence = new SequenceMediator();
        sequence.setSequenceType(SequenceType.NAMED);
        sequence.setName(PublisherTestUtils.TEST_SEQUENCE_NAME);
        synapseConfig.addSequence(PublisherTestUtils.TEST_SEQUENCE_NAME, sequence);

        HTTPEndpointFactory factory = new HTTPEndpointFactory();
        OMElement em = AXIOMUtil.stringToOM(
                "<endpoint><http method=\"GET\" uri-template=\"https://wso2.com\"/></endpoint>");
        EndpointDefinition endpoint = factory.createEndpointDefinition(em);
        HTTPEndpoint httpEndpoint = new HTTPEndpoint();
        httpEndpoint.setName(PublisherTestUtils.TEST_ENDPOINT_NAME);
        httpEndpoint.setHttpMethod(PublisherTestUtils.TEST_API_METHOD);
        httpEndpoint.setDefinition(endpoint);
        httpEndpoint.setUriTemplate(UriTemplate.fromTemplate("https://wso2.com"));
        synapseConfig.addEndpoint(PublisherTestUtils.TEST_ENDPOINT_NAME, httpEndpoint);
        httpEndpoint.init(synapseEnvironment);

        ProxyService proxyService = new ProxyService(PublisherTestUtils.TEST_PROXY_SERVICE);
        synapseConfig.addProxyService(PublisherTestUtils.TEST_PROXY_SERVICE, proxyService);
    }

    private void oneTimeSetup() throws Exception {
        if (oneTimeSetupComplete) {
            return;
        }

        sysConfig = new ServerConfigurationInformation();
        sysConfig.setServerName(PublisherTestUtils.SERVER_INFO_SERVER_NAME);
        sysConfig.setHostName(PublisherTestUtils.SERVER_INFO_HOST_NAME);
        sysConfig.setIpAddress(PublisherTestUtils.SERVER_INFO_IP_ADDRESS);
        publisher.setPublisherId(PublisherTestUtils.SERVER_INFO_PUBLISHER_ID);
        publisher.setServerConfig(sysConfig);
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
        flow.addEvent(createPublishingEvent(ComponentType.SEQUENCE, PublisherTestUtils.TEST_SEQUENCE_NAME));
        publisher.enableService();
        publisher.process(flow, PublisherTestUtils.TENANT_ID);
        assertTrue(publisher.isEnabled());
        assertEquals(1, publisher.getAnalyticsCount());
        verifyMoesifSchema(publisher.getAnalyticData(), MoesifPayloadType.SEQUENCE);
        publisher.reset();
        publisher.disableService();
        publisher.process(flow, PublisherTestUtils.TENANT_ID);
        assertEquals(0, publisher.getAnalyticsCount());
    }

    public void testSequenceAnalytics() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.SEQUENCE, PublisherTestUtils.TEST_SEQUENCE_NAME));
        publisher.process(flow, PublisherTestUtils.TENANT_ID);
        assertEquals(1, publisher.getAnalyticsCount());
        verifyMoesifSchema(publisher.getAnalyticData(), MoesifPayloadType.SEQUENCE);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, PublisherTestUtils.TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    public void testApiResourceAnalytics() {
        RestRequestHandler handler = new RestRequestHandler();
        handler.process(messageContext);
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.API, PublisherTestUtils.TEST_API_NAME));
        publisher.process(flow, PublisherTestUtils.TENANT_ID);
        assertEquals(1, publisher.getAnalyticsCount());
        verifyMoesifSchema(publisher.getAnalyticData(), MoesifPayloadType.API);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, PublisherTestUtils.TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    public void testEndpointAnalytics() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.ENDPOINT, PublisherTestUtils.TEST_ENDPOINT_NAME));
        publisher.process(flow, PublisherTestUtils.TENANT_ID);
        assertEquals(1, publisher.getAnalyticsCount());
        verifyMoesifSchema(publisher.getAnalyticData(), MoesifPayloadType.ENDPOINT);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, PublisherTestUtils.TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    public void testProxyServiceAnalytics() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.PROXYSERVICE, PublisherTestUtils.TEST_PROXY_SERVICE));
        publisher.process(flow, PublisherTestUtils.TENANT_ID);
        verifyMoesifSchema(publisher.getAnalyticData(), MoesifPayloadType.PROXY_SERVICE);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, PublisherTestUtils.TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    public void testInboundEndpointAnalytics() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.INBOUNDENDPOINT, PublisherTestUtils.TEST_INBOUND_ENDPOINT));
        publisher.process(flow, PublisherTestUtils.TENANT_ID);
        verifyMoesifSchema(publisher.getAnalyticData(), MoesifPayloadType.INBOUND_ENDPOINT);

        for (int i = 0; i < 100; ++i) {
            publisher.process(flow, PublisherTestUtils.TENANT_ID);
        }
        assertEquals(100, publisher.getAnalyticsCount());
    }

    public void testTransactionIdFormat() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.API, PublisherTestUtils.TEST_API_NAME));
        publisher.process(flow, PublisherTestUtils.TENANT_ID);

        JsonObject analytic = publisher.getAnalyticData();
        assertTrue(analytic.has(MoesifConstants.TRANSACTION_ID));
        String transactionId = analytic.get(MoesifConstants.TRANSACTION_ID).getAsString();

        // Verify UUID format: 36 characters with hyphens
        assertEquals(36, transactionId.length());
        assertTrue(transactionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));

        // Verify it's a valid UUID
        try {
            UUID.fromString(transactionId);
        } catch (IllegalArgumentException e) {
            fail("Transaction ID should be a valid UUID format");
        }
    }

    public void testTransactionIdUniqueness() {
        PublishingFlow flow = new PublishingFlow();
        flow.addEvent(createPublishingEvent(ComponentType.API, PublisherTestUtils.TEST_API_NAME));

        Map<String, Integer> transactionIds = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            publisher.reset();
            publisher.process(flow, PublisherTestUtils.TENANT_ID);
            JsonObject analytic = publisher.getAnalyticData();
            String transactionId = analytic.get(MoesifConstants.TRANSACTION_ID).getAsString();
            transactionIds.put(transactionId, transactionIds.getOrDefault(transactionId, 0) + 1);
        }

        assertEquals(100, transactionIds.size());
        for (Integer count : transactionIds.values()) {
            assertEquals(1, count.intValue());
        }
    }

    private PublishingEvent createPublishingEvent(ComponentType componentType, String componentName) {
        PublishingEvent event = new PublishingEvent();
        event.setComponentType(StatisticsConstants.getComponentTypeToString(componentType));
        event.setFlowId(PublisherTestUtils.TEST_FLOW_ID);
        event.setComponentName(componentName);
        long currentTime = Instant.now().toEpochMilli();
        event.setStartTime(currentTime - PublisherTestUtils.STATIC_LATENCY);
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
        Map<String, Object> contextProperties = new HashMap<>(messageContext.getProperties());
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

    private void verifyMoesifSchema(JsonObject analytic, MoesifPayloadType payloadType) {
        assertNotNull(analytic);

        assertTrue("actionName field is missing", analytic.has(MoesifConstants.ACTION_NAME));
        assertTrue("transactionId field is missing", analytic.has(MoesifConstants.TRANSACTION_ID));
        assertTrue("request field is missing", analytic.has(MoesifConstants.REQUEST));
        assertTrue("metadata field is missing", analytic.has(MoesifConstants.METADATA));

        verifyActionName(analytic.get(MoesifConstants.ACTION_NAME), payloadType);
        verifyTransactionId(analytic.get(MoesifConstants.TRANSACTION_ID));
        verifyRequest(analytic.get(MoesifConstants.REQUEST));
        verifyMetadata(analytic.get(MoesifConstants.METADATA), payloadType);
    }

    private void verifyActionName(JsonElement actionNameElement, MoesifPayloadType payloadType) {
        assertNotNull(actionNameElement);
        assertTrue(actionNameElement.isJsonPrimitive());

        String actionName = actionNameElement.getAsString();
        switch (payloadType) {
            case API:
                assertEquals(MoesifConstants.API_ACTION_NAME, actionName);
                break;
            case SEQUENCE:
                assertEquals(MoesifConstants.SEQUENCE_ACTION_NAME, actionName);
                break;
            case ENDPOINT:
                assertEquals(MoesifConstants.ENDPOINT_ACTION_NAME, actionName);
                break;
            case PROXY_SERVICE:
                assertEquals(MoesifConstants.PROXY_SERVICE_ACTION_NAME, actionName);
                break;
            case INBOUND_ENDPOINT:
                assertEquals(MoesifConstants.INBOUND_ENDPOINT_ACTION_NAME, actionName);
                break;
        }
    }

    private void verifyTransactionId(JsonElement transactionIdElement) {
        assertNotNull(transactionIdElement);
        assertTrue(transactionIdElement.isJsonPrimitive());

        String transactionId = transactionIdElement.getAsString();
        assertEquals(36, transactionId.length());

        try {
            UUID.fromString(transactionId);
        } catch (IllegalArgumentException e) {
            fail("Transaction ID should be a valid UUID");
        }
    }

    private void verifyRequest(JsonElement requestElement) {
        assertNotNull(requestElement);
        assertTrue(requestElement.isJsonObject());

        JsonObject request = requestElement.getAsJsonObject();
        assertTrue(request.has(MoesifConstants.REQUEST_TIME));

        String time = request.get(MoesifConstants.REQUEST_TIME).getAsString();
        try {
            Instant.parse(time);
        } catch (DateTimeParseException e) {
            fail("Request time should be in ISO8601 format. Found: " + time);
        }
    }

    private void verifyMetadata(JsonElement metadataElement, MoesifPayloadType payloadType) {
        assertNotNull(metadataElement);
        assertTrue(metadataElement.isJsonObject());

        JsonObject metadata = metadataElement.getAsJsonObject();

        assertTrue(metadata.has(ElasticConstants.EnvelopDef.SERVER_INFO));
        verifyServerInfo(metadata.get(ElasticConstants.EnvelopDef.SERVER_INFO));

        assertTrue(metadata.has(ElasticConstants.EnvelopDef.ENTITY_TYPE));
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.LATENCY));
        assertEquals(PublisherTestUtils.STATIC_LATENCY, metadata.get(ElasticConstants.EnvelopDef.LATENCY).getAsInt());

        // Verify payload-specific metadata
        switch (payloadType) {
            case API:
                verifyApiMetadata(metadata);
                break;
            case SEQUENCE:
                verifySequenceMetadata(metadata);
                break;
            case ENDPOINT:
                verifyEndpointMetadata(metadata);
                break;
            case PROXY_SERVICE:
                verifyProxyServiceMetadata(metadata);
                break;
            case INBOUND_ENDPOINT:
                verifyInboundEndpointPayload(metadata);
        }
    }

    private void verifyServerInfo(JsonElement serverInfoElement) {
        assertNotNull(serverInfoElement);
        assertTrue(serverInfoElement.isJsonObject());

        JsonObject serverInfo = serverInfoElement.getAsJsonObject();
        assertTrue(serverInfo.has(ElasticConstants.ServerMetadataFieldDef.HOST_NAME));
        assertTrue(serverInfo.has(ElasticConstants.ServerMetadataFieldDef.SERVER_NAME));
        assertTrue(serverInfo.has(ElasticConstants.ServerMetadataFieldDef.IP_ADDRESS));
    }

    private void verifyApiMetadata(JsonObject metadata) {
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.API_DETAILS));
        JsonObject apiDetails = metadata.get(ElasticConstants.EnvelopDef.API_DETAILS).getAsJsonObject();
        assertTrue(apiDetails.has(ElasticConstants.EnvelopDef.API));
        assertEquals(PublisherTestUtils.TEST_API_NAME, apiDetails.get(ElasticConstants.EnvelopDef.API).getAsString());
        assertTrue(apiDetails.has(ElasticConstants.EnvelopDef.API_CONTEXT));
        assertEquals(PublisherTestUtils.TEST_API_CONTEXT, apiDetails.get(ElasticConstants.EnvelopDef.API_CONTEXT)
                .getAsString());

        // Verify HTTP properties
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.REMOTE_HOST));
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.CONTENT_TYPE));
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.HTTP_METHOD));
    }

    private void verifySequenceMetadata(JsonObject metadata) {
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.SEQUENCE_DETAILS));
        JsonObject sequenceDetails = metadata.get(ElasticConstants.EnvelopDef.SEQUENCE_DETAILS).getAsJsonObject();
        assertTrue(sequenceDetails.has(ElasticConstants.EnvelopDef.SEQUENCE_NAME));
        assertEquals(PublisherTestUtils.TEST_SEQUENCE_NAME, sequenceDetails
                .get(ElasticConstants.EnvelopDef.SEQUENCE_NAME).getAsString());
    }

    private void verifyEndpointMetadata(JsonObject metadata) {
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.ENDPOINT_DETAILS));
        JsonObject endpointDetails = metadata.get(ElasticConstants.EnvelopDef.ENDPOINT_DETAILS).getAsJsonObject();
        assertTrue(endpointDetails.has(ElasticConstants.EnvelopDef.ENDPOINT_NAME));
        assertEquals(PublisherTestUtils.TEST_ENDPOINT_NAME, endpointDetails
                .get(ElasticConstants.EnvelopDef.ENDPOINT_NAME).getAsString());
    }

    private void verifyProxyServiceMetadata(JsonObject metadata) {
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.PROXY_SERVICE_DETAILS));
        JsonObject proxyDetails = metadata.get(ElasticConstants.EnvelopDef.PROXY_SERVICE_DETAILS).getAsJsonObject();
        assertTrue(proxyDetails.has(ElasticConstants.EnvelopDef.PROXY_SERVICE_NAME));
        assertEquals(PublisherTestUtils.TEST_PROXY_SERVICE, proxyDetails
                .get(ElasticConstants.EnvelopDef.PROXY_SERVICE_NAME).getAsString());

        // Verify HTTP properties for proxy service
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.REMOTE_HOST));
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.CONTENT_TYPE));
    }

    private void verifyInboundEndpointPayload(JsonObject metadata) {
        assertTrue(metadata.has(ElasticConstants.EnvelopDef.INBOUND_ENDPOINT_DETAILS));
        JsonObject endpointDetails = metadata.get(ElasticConstants.EnvelopDef.INBOUND_ENDPOINT_DETAILS)
                .getAsJsonObject();
        assertTrue(endpointDetails.has(ElasticConstants.EnvelopDef.INBOUND_ENDPOINT_NAME));
        assertEquals(PublisherTestUtils.TEST_INBOUND_ENDPOINT, endpointDetails
                .get(ElasticConstants.EnvelopDef.INBOUND_ENDPOINT_NAME).getAsString());
    }

    enum MoesifPayloadType {
        PROXY_SERVICE,
        ENDPOINT,
        API,
        SEQUENCE,
        INBOUND_ENDPOINT
    }
}
