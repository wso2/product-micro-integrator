/*
 *Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *WSO2 Inc. licenses this file to you under the Apache License,
 *Version 2.0 (the "License"); you may not use this file except
 *in compliance with the License.
 *You may obtain a copy of the License at
 *
 *http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an
 *"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *KIND, either express or implied.  See the License for the
 *specific language governing permissions and limitations
 *under the License.
 */

package org.wso2.carbon.esb.jms.transport.test;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.esb.integration.common.extensions.jmsserver.ActiveMQServerExtension;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Check if the message processor runs the forwarder with the specified interval along with the cron interval
 * https://wso2.org/jira/browse/ESBJAVA-3034
 */
public class MSMPCronForwarderCase extends ESBIntegrationTest {

    private final int NUMBER_OF_MESSAGES = 4;
    private CarbonLogReader carbonLogReader = new CarbonLogReader();

    @BeforeClass(alwaysRun = true)
    protected void init() throws Exception {
        // START THE ESB
        super.init();
        if (!ActiveMQServerExtension.isMQServerStarted()) {
            log.info("ActiveMQ Server is not started. Hence starting the MQServer");
            ActiveMQServerExtension.startMQServer();
            Thread.sleep(60000);
            Awaitility.await()
                    .pollInterval(org.awaitility.Duration.ONE_MINUTE)
                    .atMost(org.awaitility.Duration.FIVE_MINUTES)
                    .until(isLogWritten());
        } else {
            log.info("ActiveMQ Server has already started.");
        }
        carbonLogReader.start();
    }

    @Test(groups = { "wso2.esb" }, description = "Test Cron Forwarding of message processor")
    public void testMessageProcessorCronForwader() throws Exception {
        if (!Boolean.parseBoolean(System.getenv("CI_BUILD_SKIP"))) {
            // SEND THE REQUEST
            String addUrl = getProxyServiceURLHttp("MSMPRetrytest");
            String payload = "{\"name\":\"Jack\"}";

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Content-Type", "application/json");

            // need to send two request
            HttpResponse response1 = HttpRequestUtil.doPost(new URL(addUrl), payload, headers);
            HttpResponse response2 = HttpRequestUtil.doPost(new URL(addUrl), payload, headers);
            HttpResponse response3 = HttpRequestUtil.doPost(new URL(addUrl), payload, headers);
            HttpResponse response4 = HttpRequestUtil.doPost(new URL(addUrl), payload, headers);

            // IT HAS TO BE 202 ACCEPTED
            assertEquals(response1.getResponseCode(), 202, "ESB failed to send 202 even after setting FORCE_SC_ACCEPTED");
            assertEquals(response2.getResponseCode(), 202, "ESB failed to send 202 even after setting FORCE_SC_ACCEPTED");
            assertEquals(response3.getResponseCode(), 202, "ESB failed to send 202 even after setting FORCE_SC_ACCEPTED");
            assertEquals(response4.getResponseCode(), 202, "ESB failed to send 202 even after setting FORCE_SC_ACCEPTED");

            // WAIT FOR THE MESSAGE PROCESSOR TO TRIGGER
            //        Thread.sleep(60000);
            Awaitility.await()
                    .pollInterval(org.awaitility.Duration.ONE_MINUTE)
                    .atMost(org.awaitility.Duration.FIVE_MINUTES)
                    .until(isLogWritten());
            assertTrue(carbonLogReader.checkForLog("Jack", 60, NUMBER_OF_MESSAGES));
        }
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        carbonLogReader.stop();
    }

    private Callable<Boolean> isLogWritten() {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                return carbonLogReader.checkForLog("Jack", DEFAULT_TIMEOUT, NUMBER_OF_MESSAGES);
            }
        };
    }

    private Callable<Boolean> isServerStarted() {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                log.info("isMQServerStarted: " + ActiveMQServerExtension.isMQServerStarted());
                return ActiveMQServerExtension.isMQServerStarted();
            }
        };
    }
}
