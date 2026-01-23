package org.wso2.micro.integrator.log4j2.plugins;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class OpenTelemetryAppenderTest {

    @Test
    public void testAppenderCreation() {
        OpenTelemetryAppender appender = OpenTelemetryAppender.createAppender("OTelAppender", null, null, true);
        Assert.assertNotNull(appender, "Appender should not be null");
    }

    @Test
    public void testAppend() {
        // Since we didn't inject a Mock OTel, this test mainly verifies no exceptions
        // are thrown
        // and that the append logic executes.
        // Real end-to-end verification without Mock OTel is hard in unit test without
        // dependency injection.

        OpenTelemetryAppender appender = OpenTelemetryAppender.createAppender("OTelAppender", null, null, true);
        appender.start();

        LogEvent event = Log4jLogEvent.newBuilder()
                .setLoggerName("TestLogger")
                .setLevel(Level.INFO)
                .setMessage(new SimpleMessage("Test Message"))
                .setTimeMillis(System.currentTimeMillis())
                .build();

        try {
            appender.append(event);
        } catch (Exception e) {
            Assert.fail("Append should not throw exception: " + e.getMessage());
        }

        appender.stop();
    }
}
