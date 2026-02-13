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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import org.apache.synapse.aspects.flow.statistics.tracing.opentelemetry.OpenTelemetryManagerHolder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.aspects.flow.statistics.tracing.opentelemetry.management.OpenTelemetryManager;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.observability.metric.handler.MetricReporter;
import org.wso2.micro.integrator.observability.util.MetricConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * OpenTelemetry implementation of the MetricReporter interface.
 * Handles the translation between Prometheus-style label value arrays and OTel Attributes.
 */
public class OpenTelemetryReporter implements MetricReporter {

    private final Meter meter;

    private DoubleCounter TOTAL_REQUESTS_RECEIVED_PROXY_SERVICE;
    private DoubleCounter TOTAL_REQUESTS_RECEIVED_API;
    private DoubleCounter TOTAL_REQUESTS_RECEIVED_INBOUND_ENDPOINT;
    private DoubleCounter TOTAL_REQUESTS_RECEIVED_DATA_SERVICE;
    private DoubleCounter ERROR_REQUESTS_RECEIVED_PROXY_SERVICE;
    private DoubleCounter ERROR_REQUESTS_RECEIVED_API;
    private DoubleCounter ERROR_REQUESTS_RECEIVED_INBOUND_ENDPOINT;
    private DoubleCounter ERROR_REQUESTS_RECEIVED_DATA_SERVICE;

    private DoubleHistogram PROXY_LATENCY_HISTOGRAM;
    private DoubleHistogram API_LATENCY_HISTOGRAM;
    private DoubleHistogram INBOUND_ENDPOINT_LATENCY_HISTOGRAM;
    private DoubleHistogram DATA_SERVICE_LATENCY_HISTOGRAM;

    private DoubleGauge SERVER_UP;
    private DoubleGauge SERVICE_UP;
    private DoubleGauge SERVER_VERSION;

    private static final Log log = LogFactory.getLog(OpenTelemetryReporter.class);

    private List<Double> proxyLatencyBuckets;
    private List<Double> apiLatencyBuckets;
    private List<Double> inboundEndpointLatencyBuckets;
    private List<Double> dataServiceLatencyBuckets;

    private final Map<String, Object> metricMap = new HashMap<>();
    private final Map<String, String[]> metricLabelKeys = new HashMap<>();

    private volatile boolean initialized = false;

    public OpenTelemetryReporter() {
        OpenTelemetryManager manager = OpenTelemetryManagerHolder.getOpenTelemetryManager();
        if (manager == null) {
            throw new IllegalStateException("OpenTelemetry is not initialized. Please ensure that " +
                    "OpenTelemetry is enabled and properly configured.");
        }
        this.meter = manager.getTelemetryMeter();
    }

    private synchronized void initializeOnce() {
        if (initialized) {
            return;
        }
        createBuckets();
        initialized = true;

    }

    @Override
    public void createMetrics(String serviceType, String type, String metricName, String metricHelp,
                              String[] properties) {

        if (!initialized) {
            initializeOnce();
        }

        metricLabelKeys.put(metricName, properties);

        if (serviceType.equalsIgnoreCase(SERVICE.PROXY.name())) {
            if (type.equals(MetricConstants.COUNTER)) {
                TOTAL_REQUESTS_RECEIVED_PROXY_SERVICE = meter.counterBuilder(MetricConstants.PROXY_REQUEST_COUNT_TOTAL)
                        .setDescription(metricHelp).ofDoubles().build();
                metricMap.put(metricName, TOTAL_REQUESTS_RECEIVED_PROXY_SERVICE);

            } else if (type.equals(MetricConstants.HISTOGRAM)) {
                PROXY_LATENCY_HISTOGRAM = meter.histogramBuilder(MetricConstants.PROXY_LATENCY_SECONDS)
                        .setDescription(metricHelp)
                        .setExplicitBucketBoundariesAdvice(proxyLatencyBuckets).build();
                metricMap.put(metricName, PROXY_LATENCY_HISTOGRAM);
            }
        } else if (serviceType.equalsIgnoreCase(SERVICE.API.name())) {
            if (type.equals(MetricConstants.COUNTER)) {
                TOTAL_REQUESTS_RECEIVED_API = meter.counterBuilder(MetricConstants.API_REQUEST_COUNT_TOTAL)
                        .setDescription(metricHelp).ofDoubles().build();
                metricMap.put(metricName, TOTAL_REQUESTS_RECEIVED_API);
            } else if (type.equals(MetricConstants.HISTOGRAM)) {
                API_LATENCY_HISTOGRAM = meter.histogramBuilder(MetricConstants.API_LATENCY_SECONDS)
                        .setDescription(metricHelp).setExplicitBucketBoundariesAdvice(apiLatencyBuckets).build();
                metricMap.put(metricName, API_LATENCY_HISTOGRAM);
            }
        } else if (serviceType.equalsIgnoreCase(SERVICE.INBOUND_ENDPOINT.name())) {
            if (type.equals(MetricConstants.COUNTER)) {
                TOTAL_REQUESTS_RECEIVED_INBOUND_ENDPOINT = meter.counterBuilder(MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_TOTAL)
                        .setDescription(metricHelp).ofDoubles().build();
                metricMap.put(metricName, TOTAL_REQUESTS_RECEIVED_INBOUND_ENDPOINT);
            } else if (type.equals(MetricConstants.HISTOGRAM)) {
                INBOUND_ENDPOINT_LATENCY_HISTOGRAM = meter.histogramBuilder(MetricConstants.INBOUND_ENDPOINT_LATENCY_SECONDS)
                        .setDescription(metricHelp).setExplicitBucketBoundariesAdvice(inboundEndpointLatencyBuckets)
                        .build();
                metricMap.put(metricName, INBOUND_ENDPOINT_LATENCY_HISTOGRAM);
            }
        } else if (serviceType.equalsIgnoreCase(SERVICE.DATA_SERVICE.name())) {
            if (type.equals(MetricConstants.COUNTER)) {
                TOTAL_REQUESTS_RECEIVED_DATA_SERVICE = meter.counterBuilder(MetricConstants.DATA_SERVICE_REQUEST_COUNT_TOTAL)
                        .setDescription(metricHelp).ofDoubles().build();
                metricMap.put(metricName, TOTAL_REQUESTS_RECEIVED_DATA_SERVICE);
            } else if (type.equals(MetricConstants.HISTOGRAM)) {
                DATA_SERVICE_LATENCY_HISTOGRAM = meter.histogramBuilder(MetricConstants.DATA_SERVICE_LATENCY_SECONDS)
                        .setDescription(metricHelp)
                        .setExplicitBucketBoundariesAdvice(dataServiceLatencyBuckets).build();
                metricMap.put(metricName, DATA_SERVICE_LATENCY_HISTOGRAM);
            }
        } else if (serviceType.equals(MetricConstants.SERVER)) {
            SERVER_UP = meter.gaugeBuilder(MetricConstants.SERVER_UP)
                    .setDescription("Server status").build();
            metricMap.put(MetricConstants.SERVER_UP, SERVER_UP);
        } else if (serviceType.equals(MetricConstants.VERSION)) {
            SERVER_VERSION = meter.gaugeBuilder(MetricConstants.SERVER_VERSION).setDescription(metricHelp)
                            .build();
            metricMap.put(MetricConstants.SERVER_VERSION, SERVER_VERSION);
        } else {
            SERVICE_UP = meter.gaugeBuilder(MetricConstants.SERVICE_UP).setDescription("Service status")
                            .build();
            metricMap.put(MetricConstants.SERVICE_UP, SERVICE_UP);
        }
    }

    @Override
    public void initMetrics() {
        this.initializeServeMetrics();
        this.initializeServerVersionMetrics();
        this.initializeArtifactDeploymentMetrics();

        this.initializeApiMetrics();
        this.initializeProxyMetrics();
        this.initializeInboundEndpointMetrics();
        this.initializeDataServiceMetrics();
    }

    @Override
    public void initErrorMetrics(String serviceType, String type, String metricName,
                                 String metricHelp, String[] properties) {
        metricLabelKeys.put(metricName, properties);

        if (serviceType.equals(SERVICE.API.name())) {
            ERROR_REQUESTS_RECEIVED_API = meter.counterBuilder(MetricConstants.API_REQUEST_COUNT_ERROR_TOTAL)
                    .setDescription(metricHelp).ofDoubles().build();
            metricMap.put(metricName, ERROR_REQUESTS_RECEIVED_API);
        } else if (serviceType.equals(SERVICE.PROXY.name())) {
            ERROR_REQUESTS_RECEIVED_PROXY_SERVICE = meter.counterBuilder(MetricConstants.PROXY_REQUEST_COUNT_ERROR_TOTAL)
                    .setDescription(metricHelp).ofDoubles().build();
            metricMap.put(metricName, ERROR_REQUESTS_RECEIVED_PROXY_SERVICE);
        } else if (serviceType.equals(SERVICE.INBOUND_ENDPOINT.name())) {
            ERROR_REQUESTS_RECEIVED_INBOUND_ENDPOINT = meter.counterBuilder(MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_ERROR_TOTAL)
                    .setDescription(metricHelp).ofDoubles().build();
            metricMap.put(metricName, ERROR_REQUESTS_RECEIVED_INBOUND_ENDPOINT);
        } else if (serviceType.equals(SERVICE.DATA_SERVICE.name())) {
            ERROR_REQUESTS_RECEIVED_DATA_SERVICE = meter.counterBuilder(MetricConstants.DATA_SERVICE_REQUEST_COUNT_ERROR_TOTAL)
                    .setDescription(metricHelp).ofDoubles().build();
            metricMap.put(metricName, ERROR_REQUESTS_RECEIVED_DATA_SERVICE);
        }
    }

    @Override
    public void incrementCount(String metricName, String[] properties) {
        Object counter = metricMap.get(metricName);
        if (counter instanceof DoubleCounter) {
            Attributes attributes = buildAttributes(metricName, properties);
            ((DoubleCounter) counter).add(1, attributes);
        } else {
            log.warn("Counter metric not found: " + metricName);
        }
    }

    @Override
    public void decrementCount(String metricName, String[] properties) {

    }

    @Override
    public Object getTimer(String metricName, String[] properties) {
        Object metric = metricMap.get(metricName);
        if (metric instanceof DoubleHistogram) {
            Attributes attributes = buildAttributes(metricName, properties);
            return new OTelTimer((DoubleHistogram) metric, attributes);
        } else {
            log.warn("Timer metric not found: " + metricName);
            return null;

        }
    }

    @Override
    public void observeTime(Object timer) {
        if (timer instanceof OTelTimer) {
            ((OTelTimer) timer).observeDuration();
        } else {
            log.warn("Timer metric not found");
        }
    }

    @Override
    public void serverUp(String host, String port, String javaHome, String javaVersion) {
        Object gauge = metricMap.get(MetricConstants.SERVER_UP);
        if (gauge instanceof DoubleGauge) {
            Attributes attributes = buildAttributes(MetricConstants.SERVER_UP,
                    new String[]{host, port, javaHome, javaVersion});
            double epochSeconds = System.currentTimeMillis() / 1000.0;
            ((DoubleGauge) gauge).set(epochSeconds, attributes);
        } else {
            log.warn("Gauge metric not found: " + MetricConstants.SERVER_UP);
        }
    }

    @Override
    public void serverVersion(String version, String updateLevel) {
        Object gauge = metricMap.get(MetricConstants.SERVER_VERSION);
        if (gauge instanceof DoubleGauge) {
            Attributes attributes = buildAttributes(MetricConstants.SERVER_VERSION,
                    new String[]{version, updateLevel});
            double epochSeconds = System.currentTimeMillis() / 1000.0;
            ((DoubleGauge) gauge).set(epochSeconds, attributes);
        } else {
            log.warn("Gauge metric not found: " + MetricConstants.SERVER_VERSION);
        }
    }

    @Override
    public void serverDown(String host, String port, String javaVersion, String javaHome) {
        Object gauge =  metricMap.get(MetricConstants.SERVER_UP);
        if (gauge instanceof DoubleGauge) {
            Attributes attributes = buildAttributes(MetricConstants.SERVER_UP,
                    new String[]{host, port, javaHome, javaVersion});
            ((DoubleGauge) gauge).set(0, attributes);
        } else {
            log.warn("Gauge metric not found: " + MetricConstants.SERVER_UP);
        }
    }

    @Override
    public void serviceUp(String serviceName, String serviceType) {
        Object gauge = metricMap.get(MetricConstants.SERVICE_UP);
        if (gauge instanceof  DoubleGauge) {
            double epochSeconds = System.currentTimeMillis() / 1000.0;
            Attributes attributes = buildAttributes(MetricConstants.SERVICE_UP,
                    new String[]{serviceName, serviceType});
            ((DoubleGauge) gauge).set(epochSeconds, attributes);

            if (serviceType.equals(SynapseConstants.PROXY_SERVICE_TYPE)) {
                setCounterValue(TOTAL_REQUESTS_RECEIVED_PROXY_SERVICE, serviceName, serviceType);
                setCounterValue(ERROR_REQUESTS_RECEIVED_PROXY_SERVICE, serviceName, serviceType);
            } else if (serviceType.equals(SynapseConstants.FAIL_SAFE_MODE_API)) {
                setCounterValue(TOTAL_REQUESTS_RECEIVED_API, serviceName, serviceType);
                setCounterValue(ERROR_REQUESTS_RECEIVED_API, serviceName, serviceType);
            } else {
                setCounterValue(TOTAL_REQUESTS_RECEIVED_INBOUND_ENDPOINT, serviceName, serviceType);
                setCounterValue(ERROR_REQUESTS_RECEIVED_INBOUND_ENDPOINT, serviceName, serviceType);
            }
        } else {
            log.warn("Gauge metric not found: " + MetricConstants.SERVICE_UP);
        }
    }

    @Override
    public void serviceDown(String serviceName, String serviceType) {
        Object gauge =  metricMap.get(MetricConstants.SERVICE_UP);
        if (gauge instanceof DoubleGauge) {
            Attributes attributes = buildAttributes(MetricConstants.SERVICE_UP,
                    new String[]{serviceName, serviceType});
            ((DoubleGauge) gauge).set(0, attributes);
        } else {
            log.warn("Gauge metric not found: " + MetricConstants.SERVICE_UP);
        }
    }

    enum SERVICE {
        PROXY,
        API,
        INBOUND_ENDPOINT,
        DATA_SERVICE
    }

    /**
     * Create the API related metrics.
     */
    public void initializeApiMetrics() {
        String[] labels = {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE,
                MetricConstants.INVOCATION_URL};
        createMetrics(SynapseConstants.FAIL_SAFE_MODE_API, MetricConstants.COUNTER,
                MetricConstants.API_REQUEST_COUNT_TOTAL,
                "Total number of requests to an api", labels);
        createMetrics(SynapseConstants.FAIL_SAFE_MODE_API, MetricConstants.HISTOGRAM,
                MetricConstants.API_LATENCY_SECONDS,
                "Latency of requests to an api", labels);
        initializeApiErrorMetrics();
    }

    /**
     * Create the proxy services related metrics.
     */
    public void initializeProxyMetrics() {
        String[] labels = {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE};

        createMetrics(SynapseConstants.PROXY_SERVICE_TYPE, MetricConstants.COUNTER,
                MetricConstants.PROXY_REQUEST_COUNT_TOTAL,
                "Total number of requests to a proxy service", labels);
        createMetrics(SynapseConstants.PROXY_SERVICE_TYPE, MetricConstants.HISTOGRAM,
                MetricConstants.PROXY_LATENCY_SECONDS,
                "Latency of requests to a proxy service", labels);

        initializeProxyErrorMetrics();
    }

    /**
     * Create the inbound endpoint related metrics.
     */
    public void initializeInboundEndpointMetrics() {
        String[] labels = {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE};

        createMetrics("INBOUND_ENDPOINT", MetricConstants.COUNTER,
                MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_TOTAL,
                "Total number of requests to an inbound endpoint.", labels);
        createMetrics("INBOUND_ENDPOINT", MetricConstants.HISTOGRAM,
                MetricConstants.INBOUND_ENDPOINT_LATENCY_SECONDS,
                "Latency of requests to an inbound endpoint.", labels);

        initializeInboundEndpointErrorMetrics();
    }

    /**
     * Create data services related metrics.
     */
    public void initializeDataServiceMetrics() {
        String[] labels = {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE};

        createMetrics("DATA_SERVICE", MetricConstants.COUNTER,
                MetricConstants.DATA_SERVICE_REQUEST_COUNT_TOTAL,
                "Total number of requests to a data service.", labels);
        createMetrics("DATA_SERVICE", MetricConstants.HISTOGRAM,
                MetricConstants.DATA_SERVICE_LATENCY_SECONDS,
                "Latency of requests to a data service.", labels);

        initializeDataServiceErrorMetrics();
    }

    /**
     * Create the metrics related to server startup.
     */
    public void initializeServeMetrics() {
        createMetrics(MetricConstants.SERVER, MetricConstants.GAUGE, MetricConstants.SERVER_UP,
                "Server Status", new String[]{MetricConstants.HOST, MetricConstants.PORT,
                        MetricConstants.JAVA_HOME_LABEL, MetricConstants.JAVA_VERSION_LABEL});
    }

    /**
     * Create the metrics related to server version.
     */
    public void initializeServerVersionMetrics() {
        createMetrics(MetricConstants.VERSION, MetricConstants.GAUGE,
                MetricConstants.SERVER_VERSION,
                "Version and Update Level of Server",
                new String[]{MetricConstants.VERSION_LABEL, MetricConstants.UPDATE_LEVEL_LABEL});
    }

    /**
     * Create the metrics related to service deployment.
     */
    public void initializeArtifactDeploymentMetrics() {
        createMetrics(MetricConstants.SERVICE, MetricConstants.GAUGE, MetricConstants.SERVICE_UP,
                "Service Status",
                new String[]{MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE});

    }

    /**
     * Load the user defined Histogram bucket upper limits configurations from the deployment.toml
     * file else assign the default bucket configuration values.
     */
    private void createBuckets() {
        proxyLatencyBuckets = extractLatencyBuckets(MetricConstants.PROXY_LATENCY_BUCKETS);
        apiLatencyBuckets = extractLatencyBuckets(MetricConstants.API_LATENCY_BUCKETS);
        inboundEndpointLatencyBuckets = extractLatencyBuckets(MetricConstants.INBOUND_ENDPOINT_LATENCY_BUCKETS);
        dataServiceLatencyBuckets = extractLatencyBuckets(MetricConstants.DATA_SERVICE_LATENCY_BUCKETS);
    }

    private List<Double>  extractLatencyBuckets(String name) {
        Map<String, Object> configs = ConfigParser.getParsedConfigs();
        Object bucketsObject = configs.get(MetricConstants.METRIC_HANDLER + "." + name);
        List<Double> defaultBuckets = Arrays.asList(0.19, 0.20, 0.25, 0.30, 0.35, 0.40, 0.50, 0.60, 1.0, 5.0);
        if (bucketsObject == null) {
            return defaultBuckets;
        }
        if (bucketsObject instanceof List) {
            List<?> bucketList = (List<?>) bucketsObject;
            List<Double> latencyBuckets = new ArrayList<>();
            for (Object bucket : bucketList) {
                if (bucket instanceof Number) {
                    latencyBuckets.add(((Number) bucket).doubleValue());
                } else {
                    log.warn("Invalid bucket values for " + name + ". Using default bucket values.");
                    return defaultBuckets;
                }
            }
            return latencyBuckets;
        } else {
            log.warn("Invalid bucket values for " + name + ". Using default bucket values.");
            return defaultBuckets;
        }
    }

    /**
     * Set the counter value with the appropriate label values.
     *
     * @param counter     The counter metric
     * @param serviceName The service name
     * @param serviceType The service type
     */
    public void setCounterValue(DoubleCounter counter, String serviceName, String serviceType) {
        Attributes attributes;
        if (serviceType.equals(SynapseConstants.FAIL_SAFE_MODE_API)) {
            attributes = buildAttributes(MetricConstants.API_REQUEST_COUNT_TOTAL,
                    new String[] {serviceName, serviceType, ""});
        } else if (serviceType.equals(SynapseConstants.PROXY_SERVICE_TYPE)) {
            attributes = buildAttributes(MetricConstants.PROXY_REQUEST_COUNT_TOTAL,
                    new String[] {serviceName, serviceType});
        } else {
            attributes = buildAttributes(MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_TOTAL,
                    new String[] {serviceName, serviceType});
        }
        counter.add(0, attributes);
    }

    /**
     * Create the metrics related to failed API requests.
     */
    public void initializeApiErrorMetrics() {
        initErrorMetrics("API", MetricConstants.COUNTER,
                MetricConstants.API_REQUEST_COUNT_ERROR_TOTAL,
                "Total number of error requests to an api", new String[]{MetricConstants.SERVICE_NAME,
                        MetricConstants.SERVICE_TYPE, MetricConstants.INVOCATION_URL});
    }

    /**
     * Create the metrics related to failed proxy services.
     */
    public void initializeProxyErrorMetrics() {
        initErrorMetrics("PROXY", MetricConstants.COUNTER,
                MetricConstants.PROXY_REQUEST_COUNT_ERROR_TOTAL,
                "Total number of error requests to a proxy service", new String[]
                        {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE});
    }

    /**
     * Create the metrics related to failed inbound endpoints.
     */
    public void initializeInboundEndpointErrorMetrics() {
        initErrorMetrics("INBOUND_ENDPOINT", MetricConstants.COUNTER,
                MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_ERROR_TOTAL, "Total number of error" +
                        " requests when receiving the message by an inbound endpoint.",
                new String[]{MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE});
    }

    /**
     * Create the metrics related to failed dataservices.
     */
    public void initializeDataServiceErrorMetrics() {
        initErrorMetrics("DATA_SERVICE", MetricConstants.COUNTER,
                MetricConstants.DATA_SERVICE_REQUEST_COUNT_ERROR_TOTAL, "Total number of error" +
                        " requests to a data service.",
                new String[]{MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE});
    }

    /**
     * Constructs OpenTelemetry Attributes based on the stored keys and provided values.
     */
    private Attributes buildAttributes(String metricName, String[] values) {
        String[] keys = metricLabelKeys.get(metricName);
        AttributesBuilder builder = Attributes.builder();

        if (keys != null && values != null) {
            int length = Math.min(keys.length, values.length);
            for (int i = 0; i < length; i++) {
                builder.put(AttributeKey.stringKey(keys[i]), values[i]);
            }
        }
        return builder.build();
    }

    /**
     * Inner class to handle timing context, replacing Prometheus Timer object.
     */
    private static class OTelTimer {
        private final long startTime;
        private final DoubleHistogram histogram;
        private final Attributes attributes;

        public OTelTimer(DoubleHistogram histogram, Attributes attributes) {
            this.startTime = System.nanoTime();
            this.histogram = histogram;
            this.attributes = attributes;
        }

        public void observeDuration() {
            double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            histogram.record(durationSeconds, attributes);
        }
    }
}
