/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.esb.endpoint.test;

import org.apache.axiom.om.OMElement;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.ESBTestConstant;
import org.wso2.esb.integration.common.utils.common.TestConfigurationProvider;

import java.io.File;

import static org.testng.Assert.assertTrue;

public class EndpointErrorTest extends ESBIntegrationTest {

    private CarbonLogReader logReader;
    private final String SOURCE_DIR =
            TestConfigurationProvider.getResourceLocation(ESBTestConstant.ESB_PRODUCT_GROUP) + File.separator
                    + "proxyconfig" + File.separator + "proxy" + File.separator + "customProxy" + File.separator;

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init();
        verifyProxyServiceExistence("EndpointErrorTestProxy");
        logReader = new CarbonLogReader();
    }

    @Test(groups = { "wso2.esb" }, description = "Introduction to Proxy Services")
    public void testSequenceError() throws Exception {
        OMElement response = axis2Client
                .sendSimpleStockQuoteRequest(getProxyServiceURLHttp("EndpointErrorTestProxy"), null, "WSO2");

        Assert.assertTrue(response.toString().contains("RightErrorSequence Fault Handler Called"),
                "Wrong fault sequence executed");
    }

    @Test(groups = {"wso2.esb"}, description = "Faulty Proxy Services with unavailable WSDL Endpoint")
    public void testWSDLEndpointError() throws Exception {

        logReader.start();
        log.info("Copying the corrupted WSDL endpoint proxy service file...");
        deployProxyService("WSDLEndpointErrorTestProxy", SOURCE_DIR);
        Thread.sleep(15000);

        assertTrue(logReader.assertIfLogExists("proxy-services" + File.separator + "WSDLEndpointErrorTestProxy.xml : Failed"),
                "Faulty WSDL endpoint deployment was not failed!");
        logReader.stop();
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        super.cleanup();
    }
}

