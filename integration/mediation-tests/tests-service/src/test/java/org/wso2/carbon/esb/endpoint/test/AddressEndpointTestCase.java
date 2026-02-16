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

package org.wso2.carbon.esb.endpoint.test;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.AxisFault;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.ESBTestConstant;

import java.io.IOException;

public class AddressEndpointTestCase extends ESBIntegrationTest {

    private final String ENDPOINT_NAME = "InvalidPropertyAddressEndPoint";
    private final String ENDPOINT_NAME1 = "addressEpTest1";

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {
        super.init();
    }

    @Test(groups = { "wso2.esb" }, description = "Sending a Message to a Address endpoint")
    public void testSendingToAddressEndpoint() throws Exception {
        OMElement response = axis2Client.sendSimpleStockQuoteRequest(getProxyServiceURLHttp("addressEndpointTestProxy"),
                getBackEndServiceUrl(ESBTestConstant.SIMPLE_STOCK_QUOTE_SERVICE), "WSO2");
        Assert.assertNotNull(response);
        Assert.assertTrue(response.toString().contains("WSO2 Company"));

    }

    @Test(groups = { "wso2.esb" }, description = "Sending a Message to a Address endpoint in Config Reg")
    public void testSendingToAddressEndpoint_ConfigReg() throws Exception {
        OMElement response = axis2Client
                .sendSimpleStockQuoteRequest(getProxyServiceURLHttp("addressEndPointRegistryConfigProxy"),
                        getBackEndServiceUrl(ESBTestConstant.SIMPLE_STOCK_QUOTE_SERVICE), "WSO2");
        Assert.assertNotNull(response);
        Assert.assertTrue(response.toString().contains("WSO2 Company"));

    }

    @Test(groups = { "wso2.esb" }, description = "Sending a Message to a Invalid Address endpoint")
    public void testSendingToInvalidAddressEndpoint() {
        try {
            OMElement response = axis2Client
                    .sendSimpleStockQuoteRequest(getProxyServiceURLHttp("invalidAddressEndpointProxy"),
                            getBackEndServiceUrl(ESBTestConstant.SIMPLE_STOCK_QUOTE_SERVICE), "WSO2");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof AxisFault);
        }
    }

    @Test(groups = { "wso2.esb" }, description = "Sending a Message to a Address endpoint has a Invalid Property")
    public void testSendingToInvalidPropertyAddressEndpoint()
            throws IOException {

        boolean isDeployed = checkEndpointExistence(ENDPOINT_NAME);
        Assert.assertFalse(isDeployed, "andun scope deployed. But unsupported scope");
    }
}
