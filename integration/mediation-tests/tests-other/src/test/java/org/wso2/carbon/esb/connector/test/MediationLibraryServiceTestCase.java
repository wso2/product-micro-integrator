/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.esb.connector.test;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

import java.net.URL;

/**
 * Test the functionality of MediationLibraryServiceComponent
 */
public class MediationLibraryServiceTestCase extends ESBIntegrationTest {

    /**
     * Deploy and enable the hello connector.
     *
     * @throws Exception
     */
    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init();
    }

    /**
     * Test for invoking connector service.
     *
     * @throws Exception
     */
    @Test(groups = "wso2.esb", description = "Test connector upload and invoke.")
    public void invokeConnectorTest() throws Exception {

        String apiURI = "http://localhost:8480/library-service/get-message";
        String expectedOutput = "<message>Bob</message>";
        HttpResponse httpResponse = HttpRequestUtil.doPost(new URL(apiURI), "");
        Assert.assertEquals(httpResponse.getData(), expectedOutput, "Invoking hello connector fails.");
    }

    /**
     * Delete the hello connector.
     *
     * @throws Exception
     */
    @AfterClass(enabled = false)
    public void cleanup() throws Exception {
        Thread.sleep(5000);
    }
}
