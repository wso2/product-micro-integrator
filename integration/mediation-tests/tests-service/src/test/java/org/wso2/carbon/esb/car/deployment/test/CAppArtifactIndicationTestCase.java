/*
 *Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.esb.car.deployment.test;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

/**
 * This class can be used to upload .car application to the server and verify whether that proxy service artifact got
 * deployed through CApp
 * Related JIRA: https://wso2.org/jira/browse/ESBJAVA-3438
 */
public class CAppArtifactIndicationTestCase extends ESBIntegrationTest {

    String carFileName = "esb-artifacts-car";

    @Test(groups = { "wso2.esb" }, description = "test car application deployment")
    public void carDeploymentTest() throws Exception {
        super.init();

        Assert.assertTrue(checkCarbonAppExistence(carFileName),"car application deployment failed");
        log.info(carFileName + " deployed successfully");
    }

    @Test(groups = "wso2.esb", enabled = true, description = "Test whether proxy service get deployed through capp")
    public void testProxyServiceIsCApp() throws Exception {

        Assert.assertTrue(checkProxyServiceExistence("sampleCustomProxy"),"transform Proxy service deployment failed");
    }

}
