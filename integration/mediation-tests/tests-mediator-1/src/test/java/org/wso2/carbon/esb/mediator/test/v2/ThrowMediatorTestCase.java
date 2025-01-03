/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
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
 *  under the License.
 */
package org.wso2.carbon.esb.mediator.test.v2;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;

import java.io.IOException;

/**
 * Test case for the Throw mediator.
 */
public class ThrowMediatorTestCase extends ESBIntegrationTest {

    SimpleHttpClient httpClient = new SimpleHttpClient();

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {
        super.init();
    }

    @Test(groups = {"wso2.esb"}, description = "Testing ThrowError mediator")
    public void testThrowErrorMediator() throws IOException, InterruptedException {

        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        String serviceURL = getMainSequenceURL() + "testThrowError";
        HttpResponse httpResponse = httpClient.doGet(serviceURL, null);
        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), 202, "Response code mismatched");
        EntityUtils.consumeQuietly(httpResponse.getEntity());

        boolean logLine = carbonLogReader
                .checkForLog("ERROR_CODE = ERROR_TYPE, ERROR_MESSAGE = Error message from expression", DEFAULT_TIMEOUT);
        Assert.assertTrue(logLine, "ThrowError mediator log not found in the log");

        carbonLogReader.stop();
    }

    @AfterClass(alwaysRun = true)
    private void destroy() throws Exception {
        super.cleanup();
    }
}
