/*
 *  Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.esb.api.test;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

import java.io.IOException;

public class DefaultSequenceWithMultipleMethodsTest extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.init();
        verifyAPIExistence("DefaultSequenceWithMultipleMethods.xml");
    }

    @Test(groups = {"wso2.esb"}, description = "Sending HTTP GET request for the resources")
    public void testDefaultSequenceWithMultipleMethods() throws IOException {

        HttpResponse response = HttpRequestUtil.sendGetRequest(
                getApiInvocationURL("defaultSequenceWithMultipleMethods/abc/123"), null);

        String payloadABC = "{\"message\": \"Resource abc invoked\"}";

        Assert.assertEquals(response.getData(), payloadABC, "Expected payload not received for abc resource.");

        response = HttpRequestUtil.sendGetRequest(
                getApiInvocationURL("defaultSequenceWithMultipleMethods/xyz/123"), null);

        String payloadXYZ = "{\"message\": \"Resource xyz invoked\"}";

        Assert.assertEquals(response.getData(), payloadXYZ, "Expected payload not received for xyz resource.");

        response = HttpRequestUtil.sendGetRequest(
                getApiInvocationURL("defaultSequenceWithMultipleMethods/def"), null);

        String inputPayload = "{\"message\": \"Default resource invoked\"}";

        Assert.assertEquals(response.getData(), inputPayload, "Expected payload not received for Default resource.");
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {

        super.cleanup();
    }
}
