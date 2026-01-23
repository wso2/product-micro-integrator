/*
 * Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.observability.metric.handler.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.observability.metric.handler.MetricReporter;
import org.wso2.micro.integrator.observability.util.MetricConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Class for instrumenting OpenTelemetry Metrics and exporting via OTLP.
 */
public class OpenTelemetryReporter implements MetricReporter {

    private static final Log log = LogFactory.getLog(OpenTelemetryReporter.class);
    private Meter meter;
    private final Map<String, LongCounter> counterMap = new HashMap<>();
    private final Map<String, DoubleHistogram> histogramMap = new HashMap<>();
    private final Map<String, String[]> metricKeys = new HashMap<>();

    // OTel Resource attributes
    private static final String SERVICE_NAME_VAL = "wso2-micro-integrator";

    @Override
    public void initMetrics() {
        try {
            log.info("Initializing OpenTelemetry Metric Reporter");
            // Initialize OpenTelemetry SDK
            Resource resource = Resource.getDefault()
                    .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, SERVICE_NAME_VAL)));

            OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                    .setEndpoint("http://localhost:4317")
                    .build();

            SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
                    .registerMetricReader(PeriodicMetricReader.builder(metricExporter).build())
                    .setResource(resource)
                    .build();

            OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                    .setMeterProvider(sdkMeterProvider)
                    .buildAndRegisterGlobal();

            this.meter = openTelemetry.getMeter("org.wso2.micro.integrator.observability");

            initializeServeMetrics();
            initializeServerVersionMetrics();
            initializeArtifactDeploymentMetrics();

            initializeProxyMetrics();
            initializeApiMetrics();
            initializeInboundEndpointMetrics();
            initializeDataServiceMetrics();
            log.info("OpenTelemetry Metric Reporter initialized successfully");

        } catch (Exception e) {
            log.error("Error initializing OpenTelemetry Metric Reporter", e);
        }
    }

    public void initializeProxyMetrics() {
        String[] labels = { MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE };
        createMetrics(MetricConstants.PROXY_REQUEST_COUNT_TOTAL, MetricConstants.COUNTER, labels);
        createMetrics(MetricConstants.PROXY_LATENCY_SECONDS, MetricConstants.HISTOGRAM, labels);
        createMetrics(MetricConstants.PROXY_REQUEST_COUNT_ERROR_TOTAL, MetricConstants.COUNTER, labels);
    }

    public void initializeApiMetrics() {
        String[] labels = { MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE,
                MetricConstants.INVOCATION_URL };
        createMetrics(MetricConstants.API_REQUEST_COUNT_TOTAL, MetricConstants.COUNTER, labels);
        createMetrics(MetricConstants.API_LATENCY_SECONDS, MetricConstants.HISTOGRAM, labels);
        createMetrics(MetricConstants.API_REQUEST_COUNT_ERROR_TOTAL, MetricConstants.COUNTER, labels);
    }

    public void initializeInboundEndpointMetrics() {
        String[] labels = { MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE };
        createMetrics(MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_TOTAL, MetricConstants.COUNTER, labels);
        createMetrics(MetricConstants.INBOUND_ENDPOINT_LATENCY_SECONDS, MetricConstants.HISTOGRAM, labels);
        createMetrics(MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_ERROR_TOTAL, MetricConstants.COUNTER, labels);
    }

    public void initializeDataServiceMetrics() {
        String[] labels = { MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE };
        createMetrics(MetricConstants.DATA_SERVICE_REQUEST_COUNT_TOTAL, MetricConstants.COUNTER, labels);
        createMetrics(MetricConstants.DATA_SERVICE_LATENCY_SECONDS, MetricConstants.HISTOGRAM, labels);
        createMetrics(MetricConstants.DATA_SERVICE_REQUEST_COUNT_ERROR_TOTAL, MetricConstants.COUNTER, labels);
    }

    // Internal helper
    private void createMetrics(String metricName, String type, String[] labelNames) {
        createMetrics("UNKNOWN", type, metricName, "Help text for " + metricName, labelNames);
    }

    @Override
    public void createMetrics(String serviceType, String type, String metricName, String metricHelp,
            String[] properties) {
        if (metricName == null || type == null)
            return;

        // Store keys for attribute mapping
        metricKeys.put(metricName, properties);

        if (MetricConstants.COUNTER.equals(type)) {
            if (!counterMap.containsKey(metricName)) {
                LongCounter counter = meter.counterBuilder(metricName)
                        .setDescription(metricHelp)
                        .build();
                counterMap.put(metricName, counter);
            }
        } else if (MetricConstants.HISTOGRAM.equals(type)) {
            if (!histogramMap.containsKey(metricName)) {
                DoubleHistogram histogram = meter.histogramBuilder(metricName)
                        .setDescription(metricHelp)
                        .build();
                histogramMap.put(metricName, histogram);
            }
        }
    }

    @Override
    public void initErrorMetrics(String serviceType, String type, String metricName, String metricHelp,
            String[] properties) {
        createMetrics(serviceType, type, metricName, metricHelp, properties);
    }

    @Override
    public void incrementCount(String metricName, String[] properties) {
        LongCounter counter = counterMap.get(metricName);
        if (counter != null) {
            counter.add(1, buildAttributes(metricName, properties));
        }
    }

    @Override
    public void decrementCount(String metricName, String[] properties) {
        // No-op for monotonic counters
    }

    @Override
    public Object getTimer(String metricName, String[] properties) {
        DoubleHistogram histogram = histogramMap.get(metricName);
        if (histogram != null) {
            return new OTelTimer(System.nanoTime(), histogram, buildAttributes(metricName, properties));
        }
        return null;
    }

    @Override
    public void observeTime(Object timer) {
        if (timer instanceof OTelTimer) {
            OTelTimer otelTimer = (OTelTimer) timer;
            double durationSeconds = (System.nanoTime() - otelTimer.startTime) / 1_000_000_000.0;
            otelTimer.histogram.record(durationSeconds, otelTimer.attributes);
        }
    }

    private Attributes buildAttributes(String metricName, String[] values) {
        String[] keys = metricKeys.get(metricName);
        if (keys == null || values == null || keys.length != values.length) {
            return Attributes.empty();
        }
        AttributesBuilder builder = Attributes.builder();
        for (int i = 0; i < keys.length; i++) {
            builder.put(AttributeKey.stringKey(keys[i]), values[i]);
        }
        return builder.build();
    }

    private static class OTelTimer {
        long startTime;
        DoubleHistogram histogram;
        Attributes attributes;

        OTelTimer(long startTime, DoubleHistogram histogram, Attributes attributes) {
            this.startTime = startTime;
            this.histogram = histogram;
            this.attributes = attributes;
        }
    }

    // Unimplemented methods
    @Override
    public void serverUp(String h, String p, String jh, String jv) {
    }

    @Override
    public void serverVersion(String v, String ul) {
    }

    @Override
    public void serverDown(String h, String p, String jv, String jh) {
    }

    @Override
    public void serviceUp(String sn, String st) {
    }

    @Override
    public void serviceDown(String sn, String st) {
    }

    // Stub methods for internal use placeholders
    private void initializeServeMetrics() {
    }

    private void initializeServerVersionMetrics() {
    }

    private void initializeArtifactDeploymentMetrics() {
    }
}
