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
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
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

    protected OpenTelemetryAppender(String name, Filter filter, Layout<? extends Serializable> layout,
            boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions, null);
        LOGGER.info("Initializing OpenTelemetryAppender with name: " + name);

        // Initialize OpenTelemetry Logs SDK specific for this Appender or use global?
        // Ideally we should use a global one, but for isolation here we build one.
        // In a real scenario, this should be shared or initialized via a Service
        // Component.

        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "wso2-micro-integrator")));

        OtlpGrpcLogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
                .setEndpoint("http://localhost:4317")
                .build();

        SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
                .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setLoggerProvider(sdkLoggerProvider)
                .buildAndRegisterGlobal();

        this.logger = openTelemetry.getLogsBridge().get("org.wso2.micro.integrator");
    }

    @PluginFactory
    public static OpenTelemetryAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) boolean ignoreExceptions) {

        if (name == null) {
            LOGGER.error("No name provided for OpenTelemetryAppender");
            return null;
        }
        return new OpenTelemetryAppender(name, filter, layout, ignoreExceptions);
    }

    @Override
    public void append(LogEvent event) {
        if (logger == null) {
            LOGGER.warn("OpenTelemetry logger not initialized, skipping log event");
            return;
        }

        Instant instant = Instant.ofEpochMilli(event.getTimeMillis());

        logger.logRecordBuilder(instant)
                .setSeverity(mapSeverity(event.getLevel()))
                .setSeverityText(event.getLevel().name())
                .setBody(event.getMessage().getFormattedMessage())
                .setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("thread.name"), event.getThreadName())
                .setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("logger.name"), event.getLoggerName())
                .setContext(Context.current())
                .emit();
    }

    private Severity mapSeverity(org.apache.logging.log4j.Level level) {
        switch (level.StandardLevel) {
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
