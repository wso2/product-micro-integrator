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

package org.wso2.carbon.esb.car.deployment.test;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

public class CarbonApplicationDeploymentTestCase extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init();
    }

    @Test(groups = { "wso2.esb" }, description = "test endpoint deployment from car file")
    public void endpointDeploymentTest() throws Exception {

        Assert.assertTrue(checkEndpointExistence("addressEndpoint"),"AddressEndpoint Endpoint deployment failed");
        Assert.assertTrue(checkEndpointExistence("loadBalanceEndpoint"),"LoadBalanceEndpoint Endpoint deployment failed");
        Assert.assertTrue(checkEndpointExistence("wsdlEndpoint"),"WSDLEndpoint Endpoint deployment failed");
        Assert.assertTrue(checkEndpointExistence("failOverEndpoint"),"FailOverEndpoint Endpoint deployment failed");
        Assert.assertTrue(checkEndpointExistence("defaultEndpoint"),"DefaultEndpoint Endpoint deployment failed");

    }

    @Test(groups = { "wso2.esb" }, description = "test sequence deployment from car file")
    public void sequenceDeploymentTest() throws Exception {

        Assert.assertTrue(checkSequenceExistence("sampleSequence"),"sampleSequence deployment failed");
        Assert.assertTrue(checkSequenceExistence("sampleFaultSequence"),"sampleFaultSequence deployment failed");
        Assert.assertTrue(checkSequenceExistence("sampleSequenceWithErrorSequence"),"sampleSequenceWithErrorSequence deployment failed");

    }

    @Test(groups = { "wso2.esb" }, description = "test API deployment from car file")
    public void apiDeploymentTest() throws Exception {
        Assert.assertTrue(checkApiExistence("SampleAPI"), "SampleAPI deployment failed");

    }

    @Test(groups = { "wso2.esb" }, description = "test proxy service deployment from car file")
    public void proxyServiceDeploymentTest() throws Exception {

        Assert.assertTrue(checkProxyServiceExistence("samplePassThroughProxy"), "Pass Through Proxy service deployment failed");
        Assert.assertTrue(checkProxyServiceExistence("transformProxySample"), "transform Proxy service deployment failed");
        Assert.assertTrue(checkProxyServiceExistence("sampleCustomProxy"), "Custom Proxy service deployment failed");

    }


}
