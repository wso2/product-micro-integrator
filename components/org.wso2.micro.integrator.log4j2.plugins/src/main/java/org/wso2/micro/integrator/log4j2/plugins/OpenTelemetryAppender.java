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
package org.wso2.micro.integrator.log4j2.plugins;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.ServiceAttributes;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Plugin(name = "OpenTelemetryAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class OpenTelemetryAppender extends AbstractAppender {

    private final Logger logger;
    private final SdkLoggerProvider sdkLoggerProvider;

    protected OpenTelemetryAppender(String name, Filter filter, Layout<? extends Serializable> layout,
            boolean ignoreExceptions, String endpoint) {
        super(name, filter, layout, ignoreExceptions, null);
        LOGGER.info("Initializing OpenTelemetryAppender with name: " + name);

        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, "wso2-micro-integrator")));

        String otlpEndpoint = endpoint;
        if (otlpEndpoint == null || otlpEndpoint.isEmpty()) {
            otlpEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
        }
        if (otlpEndpoint == null || otlpEndpoint.isEmpty()) {
            otlpEndpoint = "http://localhost:4317";
        }

        OtlpGrpcLogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build();

        this.sdkLoggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setLoggerProvider(sdkLoggerProvider)
                .build();

        this.logger = openTelemetry.getLogsBridge().get("org.wso2.micro.integrator");
    }

    @Override
    public void stop() {
        if (this.sdkLoggerProvider != null) {
            this.sdkLoggerProvider.close();
        }
        super.stop();
    }

    @PluginFactory
    public static OpenTelemetryAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) boolean ignoreExceptions,
            @PluginAttribute("endpoint") String endpoint) {

        if (name == null || name.trim().isEmpty()) {
            LOGGER.error("No name provided for OpenTelemetryAppender");
            return null;
        }
        return new OpenTelemetryAppender(name, filter, layout, ignoreExceptions, endpoint);
    }

    @Override
    public void append(LogEvent event) {
        if (logger == null) {
            LOGGER.warn("OpenTelemetry logger not initialized, skipping log event");
            return;
        }

        try {
            Instant instant = Instant.ofEpochMilli(event.getTimeMillis());
            logger.logRecordBuilder(instant)
                    .setSeverity(mapSeverity(event.getLevel()))
                    .setSeverityText(event.getLevel().name())
                    .setBody(event.getMessage().getFormattedMessage())
                    .setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("thread.name"),
                            event.getThreadName())
                    .setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("logger.name"),
                            event.getLoggerName())
                    .setContext(Context.current())
                    .emit();
        } catch (Exception e) {
            error("Unable to export log event via OpenTelemetry", event, e);
            if (!ignoreExceptions()) {
                throw new org.apache.logging.log4j.core.appender.AppenderLoggingException(e);
            }
        }
    }

    private Severity mapSeverity(org.apache.logging.log4j.Level level) {
        switch (level.getStandardLevel()) {
            case FATAL:
                return Severity.FATAL;
            case ERROR:
                return Severity.ERROR;
            case WARN:
                return Severity.WARN;
            case INFO:
                return Severity.INFO;
            case DEBUG:
                return Severity.DEBUG;
            case TRACE:
                return Severity.TRACE;
            default:
                return Severity.UNDEFINED;
        }
    }
}
