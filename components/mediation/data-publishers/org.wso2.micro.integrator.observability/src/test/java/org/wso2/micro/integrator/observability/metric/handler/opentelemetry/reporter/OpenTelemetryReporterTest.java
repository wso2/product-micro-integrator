/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.observability.metric.handler.opentelemetry.reporter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.apache.synapse.aspects.flow.statistics.tracing.opentelemetry.OpenTelemetryManagerHolder;
import org.apache.synapse.aspects.flow.statistics.tracing.opentelemetry.management.OpenTelemetryManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.observability.util.MetricConstants;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

/**
 * Unit tests for {@link OpenTelemetryReporter}.
 *
 * Verifies that gauge metrics (server_up, service_up, server_version) are set to
 * the correct semantic values (1 = up, 0 = down) rather than epoch timestamps.
 * This is the root cause of issue #4710 where Grafana dashboards displayed incorrect
 * uptime/service-status values when using OTel instead of the Prometheus reporter.
 */
public class OpenTelemetryReporterTest {

    private OpenTelemetryReporter reporter;
    private Meter mockMeter;
    private DoubleGauge mockServerUpGauge;
    private DoubleGauge mockServiceUpGauge;
    private DoubleGauge mockServerVersionGauge;
    private MockedStatic<OpenTelemetryManagerHolder> mockedHolder;
    private MockedStatic<ConfigParser> mockedConfigParser;

    @BeforeMethod
    public void setUp() {
        mockMeter = mock(Meter.class);
        mockServerUpGauge = mock(DoubleGauge.class);
        mockServiceUpGauge = mock(DoubleGauge.class);
        mockServerVersionGauge = mock(DoubleGauge.class);

        OpenTelemetryManager mockManager = mock(OpenTelemetryManager.class);
        when(mockManager.getTelemetryMeter()).thenReturn(mockMeter);

        mockedHolder = mockStatic(OpenTelemetryManagerHolder.class);
        mockedHolder.when(OpenTelemetryManagerHolder::getOpenTelemetryManager).thenReturn(mockManager);

        mockedConfigParser = mockStatic(ConfigParser.class);
        mockedConfigParser.when(ConfigParser::getParsedConfigs).thenReturn(new HashMap<>());

        // Gauge builder chains — each metric gets its own distinct builder/gauge
        setupGaugeBuilder(MetricConstants.SERVER_UP, mockServerUpGauge);
        setupGaugeBuilder(MetricConstants.SERVICE_UP, mockServiceUpGauge);
        setupGaugeBuilder(MetricConstants.SERVER_VERSION, mockServerVersionGauge);

        // Counter builder chains
        setupCounterBuilder(MetricConstants.PROXY_REQUEST_COUNT_TOTAL);
        setupCounterBuilder(MetricConstants.PROXY_REQUEST_COUNT_ERROR_TOTAL);
        setupCounterBuilder(MetricConstants.API_REQUEST_COUNT_TOTAL);
        setupCounterBuilder(MetricConstants.API_REQUEST_COUNT_ERROR_TOTAL);
        setupCounterBuilder(MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_TOTAL);
        setupCounterBuilder(MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_ERROR_TOTAL);
        setupCounterBuilder(MetricConstants.DATA_SERVICE_REQUEST_COUNT_TOTAL);
        setupCounterBuilder(MetricConstants.DATA_SERVICE_REQUEST_COUNT_ERROR_TOTAL);

        // Histogram builder chains
        setupHistogramBuilder(MetricConstants.PROXY_LATENCY_SECONDS);
        setupHistogramBuilder(MetricConstants.API_LATENCY_SECONDS);
        setupHistogramBuilder(MetricConstants.INBOUND_ENDPOINT_LATENCY_SECONDS);
        setupHistogramBuilder(MetricConstants.DATA_SERVICE_LATENCY_SECONDS);

        reporter = new OpenTelemetryReporter();
        reporter.initMetrics();
    }

    @AfterMethod
    public void tearDown() {
        mockedHolder.close();
        mockedConfigParser.close();
    }

    // -------------------------------------------------------------------------
    // serverUp
    // -------------------------------------------------------------------------

    /**
     * Issue #4710 — serverUp() must publish gauge value 1 (server is running),
     * not the Unix epoch timestamp (~1.74e9).
     */
    @Test
    public void testServerUpSetsGaugeToOne() {
        reporter.serverUp("localhost", "8290", "/usr/lib/jvm/java-21", "21");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServerUpGauge).set(valueCaptor.capture(), any(Attributes.class));

        double value = valueCaptor.getValue();
        assertEquals(value, 1.0, "serverUp() must set SERVER_UP gauge to 1.0 (not epoch timestamp)");
    }

    /**
     * Epoch timestamps are several orders of magnitude larger than 1.
     * Any value >= 1e6 is almost certainly wrong for an up/down gauge.
     */
    @Test
    public void testServerUpValueIsNotEpochTimestamp() {
        reporter.serverUp("localhost", "8290", "/usr/lib/jvm/java-21", "21");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServerUpGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertFalse(valueCaptor.getValue() > 1_000_000.0,
                "serverUp() must not publish epoch timestamp as gauge value, got: " + valueCaptor.getValue());
    }

    // -------------------------------------------------------------------------
    // serverDown
    // -------------------------------------------------------------------------

    /**
     * serverDown() already set 0 — verify this contract is preserved.
     */
    @Test
    public void testServerDownSetsGaugeToZero() {
        reporter.serverDown("localhost", "8290", "21", "/usr/lib/jvm/java-21");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServerUpGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertEquals(valueCaptor.getValue(), 0.0, "serverDown() must set SERVER_UP gauge to 0.0");
    }

    // -------------------------------------------------------------------------
    // serverVersion
    // -------------------------------------------------------------------------

    /**
     * Issue #4710 — serverVersion() must publish gauge value 1 so the
     * Grafana panel can display version info from the label attributes,
     * not from a meaningless epoch timestamp.
     */
    @Test
    public void testServerVersionSetsGaugeToOne() {
        reporter.serverVersion("4.6.0", "0");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServerVersionGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertEquals(valueCaptor.getValue(), 1.0,
                "serverVersion() must set SERVER_VERSION gauge to 1.0 (not epoch timestamp)");
    }

    @Test
    public void testServerVersionValueIsNotEpochTimestamp() {
        reporter.serverVersion("4.6.0", "2");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServerVersionGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertFalse(valueCaptor.getValue() > 1_000_000.0,
                "serverVersion() must not publish epoch timestamp, got: " + valueCaptor.getValue());
    }

    // -------------------------------------------------------------------------
    // serviceUp / serviceDown
    // -------------------------------------------------------------------------

    /**
     * Issue #4710 — serviceUp() must publish gauge value 1 (service is deployed),
     * not an epoch timestamp.
     */
    @Test
    public void testServiceUpForApiSetsGaugeToOne() {
        reporter.serviceUp("TestAPI", "api");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServiceUpGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertEquals(valueCaptor.getValue(), 1.0,
                "serviceUp() must set SERVICE_UP gauge to 1.0 for API type");
    }

    @Test
    public void testServiceUpForProxySetsGaugeToOne() {
        reporter.serviceUp("TestProxy", "proxy");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServiceUpGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertEquals(valueCaptor.getValue(), 1.0,
                "serviceUp() must set SERVICE_UP gauge to 1.0 for proxy type");
    }

    @Test
    public void testServiceUpForInboundEndpointSetsGaugeToOne() {
        reporter.serviceUp("TestInbound", "inbound-endpoint");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServiceUpGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertEquals(valueCaptor.getValue(), 1.0,
                "serviceUp() must set SERVICE_UP gauge to 1.0 for inbound-endpoint type");
    }

    @Test
    public void testServiceUpValueIsNotEpochTimestamp() {
        reporter.serviceUp("TestService", "api");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServiceUpGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertFalse(valueCaptor.getValue() > 1_000_000.0,
                "serviceUp() must not publish epoch timestamp as gauge value, got: " + valueCaptor.getValue());
    }

    @Test
    public void testServiceDownSetsGaugeToZero() {
        reporter.serviceDown("TestAPI", "api");

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockServiceUpGauge).set(valueCaptor.capture(), any(Attributes.class));

        assertEquals(valueCaptor.getValue(), 0.0,
                "serviceDown() must set SERVICE_UP gauge to 0.0");
    }

    // -------------------------------------------------------------------------
    // Constructor / initialization guards
    // -------------------------------------------------------------------------

    /**
     * Verify an {@link IllegalStateException} is thrown when the OTel manager
     * has not been initialized (null from the holder).
     */
    @Test(expectedExceptions = IllegalStateException.class)
    public void testConstructorThrowsWhenManagerIsNull() {
        mockedHolder.when(OpenTelemetryManagerHolder::getOpenTelemetryManager).thenReturn(null);
        new OpenTelemetryReporter();
    }

    /**
     * After initMetrics(), the reporter must have registered gauges for all
     * three gauge-type metrics (server_up, service_up, server_version).
     */
    @Test
    public void testInitMetricsRegistersAllGauges() {
        // If gauges were not registered, the set() calls in serverUp/serviceUp/serverVersion
        // would fall into the warn branch and never reach the mock.
        // The previous tests already confirm this indirectly, but we make it explicit here.
        reporter.serverUp("host", "8290", "/java", "21");
        reporter.serviceUp("svc", "api");
        reporter.serverVersion("4.6.0", "0");

        verify(mockServerUpGauge).set(any(double.class), any(Attributes.class));
        verify(mockServiceUpGauge).set(any(double.class), any(Attributes.class));
        verify(mockServerVersionGauge).set(any(double.class), any(Attributes.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setupGaugeBuilder(String metricName, DoubleGauge gaugeToReturn) {
        DoubleGaugeBuilder builder = mock(DoubleGaugeBuilder.class);
        when(mockMeter.gaugeBuilder(metricName)).thenReturn(builder);
        when(builder.setDescription(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(gaugeToReturn);
    }

    private void setupCounterBuilder(String metricName) {
        LongCounterBuilder longBuilder = mock(LongCounterBuilder.class);
        DoubleCounterBuilder doubleBuilder = mock(DoubleCounterBuilder.class);
        DoubleCounter counter = mock(DoubleCounter.class);
        when(mockMeter.counterBuilder(metricName)).thenReturn(longBuilder);
        when(longBuilder.setDescription(anyString())).thenReturn(longBuilder);
        when(longBuilder.ofDoubles()).thenReturn(doubleBuilder);
        when(doubleBuilder.build()).thenReturn(counter);
    }

    private void setupHistogramBuilder(String metricName) {
        DoubleHistogramBuilder builder = mock(DoubleHistogramBuilder.class);
        DoubleHistogram histogram = mock(DoubleHistogram.class);
        when(mockMeter.histogramBuilder(metricName)).thenReturn(builder);
        when(builder.setDescription(anyString())).thenReturn(builder);
        when(builder.setExplicitBucketBoundariesAdvice(anyList())).thenReturn(builder);
        when(builder.build()).thenReturn(histogram);
    }
}
