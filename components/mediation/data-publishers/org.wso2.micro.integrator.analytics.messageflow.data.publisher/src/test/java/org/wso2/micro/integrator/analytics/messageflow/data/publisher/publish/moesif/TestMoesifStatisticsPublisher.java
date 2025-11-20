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

import com.google.gson.JsonObject;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Test implementation of MoesifStatisticsPublisher.
 */
public class TestMoesifStatisticsPublisher extends MoesifStatisticsPublisher {
    private final Queue<JsonObject> metricsQueue = new LinkedList<>();

    public TestMoesifStatisticsPublisher() {
        super();
    }

    @Override
    protected void publishAnalytic(JsonObject metadataPayload) {
        metricsQueue.offer(metadataPayload);

        if (log.isDebugEnabled()) {
            log.debug("Test captured Moesif metric: " + metadataPayload.toString());
        }
    }

    /**
     * Enable the publisher for testing.
     */
    public void enableService() {
        this.enabled = true;
    }

    /**
     * Disable the publisher for testing.
     */
    public void disableService() {
        this.enabled = false;
    }

    /**
     * Get whether the publisher is enabled.
     *
     * @return Is the publisher enabled or not
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the latest analytics data captured.
     *
     * @return The latest JsonObject analytics data
     */
    public JsonObject getAnalyticData() {
        return metricsQueue.poll();
    }

    /**
     * Get the number of analytics captured.
     *
     * @return The analytics count
     */
    public int getAnalyticsCount() {
        return  metricsQueue.size();
    }

    /**
     * Reset the test publisher state.
     */
    public void reset() {
        metricsQueue.clear();
    }
}
