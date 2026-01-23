package org.wso2.micro.integrator.observability.metric.handler.opentelemetry;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.micro.integrator.observability.util.MetricConstants;

public class OpenTelemetryReporterTest {

    private OpenTelemetryReporter reporter;

    @BeforeClass
    public void setUp() {
        reporter = new OpenTelemetryReporter();
        reporter.initMetrics();
    }

    @Test
    public void testCreateMetrics() {
        String[] labels = { "label1", "label2" };
        try {
            reporter.createMetrics("TestService", MetricConstants.COUNTER, "test_counter", "Test Help", labels);
            reporter.createMetrics("TestService", MetricConstants.HISTOGRAM, "test_histogram", "Test Help", labels);
        } catch (Exception e) {
            Assert.fail("CreateMetrics should not throw exception");
        }
    }

    @Test
    public void testIncrementCount() {
        String[] labels = { "val1", "val2" };
        try {
            reporter.createMetrics("TestService", MetricConstants.COUNTER, "inc_counter", "Test Help",
                    new String[] { "key1", "key2" });
            reporter.incrementCount("inc_counter", labels);
        } catch (Exception e) {
            Assert.fail("IncrementCount should not throw exception");
        }
    }

    @Test
    public void testTimer() {
        String[] labels = { "val1", "val2" };
        Object timer = null;
        try {
            reporter.createMetrics("TestService", MetricConstants.HISTOGRAM, "timer_metric", "Test Help",
                    new String[] { "key1", "key2" });
            timer = reporter.getTimer("timer_metric", labels);
            Assert.assertNotNull(timer, "Timer object should not be null");

            // tiny sleep
            Thread.sleep(10);

            reporter.observeTime(timer);
        } catch (Exception e) {
            Assert.fail("Timer operations should not throw exception: " + e.getMessage());
        }
    }
}
