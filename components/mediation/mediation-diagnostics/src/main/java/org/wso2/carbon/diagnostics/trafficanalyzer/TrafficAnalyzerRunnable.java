/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
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
 * under the License.
 */

package org.wso2.carbon.diagnostics.trafficanalyzer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.diagnostics.utils.ConfigMapHolder;
import org.wso2.diagnostics.utils.Constants;
import org.wso2.diagnostics.utils.JMXDataRetriever;

import java.time.Duration;
import java.time.Instant;

/**
 * This class is responsible for analyzing the traffic of the server.
 */
public class TrafficAnalyzerRunnable implements Runnable {

    private static final Logger log = LogManager.getLogger(TrafficAnalyzerRunnable.class);
    private final SimpleMovingAverage movingAverage;
    int httpListenerValue = 0;
    int httpSenderValue = 0;
    int httpsListenerValue = 0;
    int httpsSenderValue = 0;
    String pid;
    String attribute;
    int delay;
    int delayCounter;
    private static Instant lastNotification;

    public TrafficAnalyzerRunnable(String pid, int windowSize, int delay, String attribute) {

        this.attribute = attribute;
        this.pid = pid;
        movingAverage = new SimpleMovingAverage(windowSize);
        this.delay = delay;
        delayCounter = 0;
    }

    public void run() {
        if (log.isDebugEnabled()) {
            log.debug("Traffic analyzer thread executing for pid: " + pid + ", attribute: " + attribute +
                    ", delay: " + delay + ", delay counter: " + delayCounter + ", last notification: " + lastNotification);
        }
        int newHttpListenerAttributeValue = JMXDataRetriever.getIntAttributeValue("http-listener", pid, attribute);
        needToAlert(httpListenerValue, newHttpListenerAttributeValue, "http-listener");
        httpListenerValue = newHttpListenerAttributeValue;

        int newHttpsListenerAttributeValue = JMXDataRetriever.getIntAttributeValue("https-listener", pid, attribute);
        needToAlert(httpsListenerValue, newHttpsListenerAttributeValue, "https-listener");
        httpsListenerValue = newHttpsListenerAttributeValue;

        int newHttpSenderAttributeValue = JMXDataRetriever.getIntAttributeValue("http-sender", pid, attribute);
        needToAlert(httpSenderValue, newHttpSenderAttributeValue, "http-sender");
        httpSenderValue = newHttpSenderAttributeValue;

        int newHttpsSenderAttributeValue = JMXDataRetriever.getIntAttributeValue("https-sender", pid, attribute);
        needToAlert(httpsSenderValue, newHttpsSenderAttributeValue, "https-sender");
        httpsSenderValue = newHttpsSenderAttributeValue;

    }

    private void needToAlert(int oldValue, int newValue, String type) {

        if (log.isDebugEnabled()) {
            log.debug("Checking if need to alert for attribute: " + attribute + ", type: " + type +
                    ", current Threshold: " + movingAverage.getCurrentThreshold() + ", new value: " + newValue);
        }
        boolean doNotify = true;
        int notifyInterval = Integer.parseInt(
                (String) ConfigMapHolder.getInstance().getConfigMap().get(Constants.TRAFFIC_NOTIFY_INTERVAL));
        if (lastNotification != null) {
            Duration duration = Duration.between(lastNotification, Instant.now());
            if (duration.getSeconds() < notifyInterval) {
                doNotify = false;
            }
        }
        if (this.detectAnomalies(newValue) && doNotify) {
            lastNotification = Instant.now();
            log.info("Attribute " +  attribute + " of type " + type + " increased more than the threshold, old value: " +
                    oldValue + ", new value: " + newValue + ", threshold: " + movingAverage.getCurrentThreshold());
        }
    }

    // Update EMAs for each time frame
    public void update(int newDataPoint) {

        movingAverage.update(newDataPoint);
    }

    public Double getThreshold() {

        return movingAverage.getCurrentThreshold();
    }

    public Boolean detectAnomalies(int newData) {

        if (newData <= 0) {
            return Boolean.FALSE;
        }
        this.update(newData);
        Double threshold = getThreshold();
        boolean isAnomaly = newData > threshold;
        if (isAnomaly & delayCounter == 0) {
            delayCounter = delay;
            return Boolean.TRUE;
        }
        if (delayCounter != 0) {
            delayCounter -= 1;
        }
        return Boolean.FALSE;
    }
}
