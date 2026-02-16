/*
 * Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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
package org.wso2.micro.integrator.ws.policies;

import org.apache.axiom.om.OMElement;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SecureServiceClient;
import org.wso2.esb.integration.common.utils.servers.axis2.SampleAxis2Server;

import javax.xml.namespace.QName;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class SecureProxyTestCase extends ESBIntegrationTest {

    private SecureServiceClient secureAxisServiceClient;

    private SampleAxis2Server axis2Server;

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {

        super.init();
        secureAxisServiceClient = new SecureServiceClient();
        axis2Server = new SampleAxis2Server();
        axis2Server.deployService(SampleAxis2Server.SIMPLE_STOCK_QUOTE_SERVICE);
        axis2Server.start();
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        axis2Server.stop();
        super.cleanup();
    }

    @Test(groups = "wso2.esb", description = "- Secure proxy" + "- Proxy service with providing endpoint through url")
    public void testSecureProxyEndPointThruUri() throws Exception {

        OMElement response = secureAxisServiceClient
                .sendSecuredStockQuoteRequest(context.getSuperTenant().getContextUser(), getProxyServiceURLHttps("StockQuoteProxyScenario11"), 1, "WSO2");

        String lastPrice = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "last")).getText();
        assertNotNull(lastPrice, "Fault: response message 'last' price null");

        String symbol = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "symbol")).getText();
        assertEquals(symbol, "WSO2", "Fault: value 'symbol' mismatched");

    }

    @Test(groups = "wso2.esb", description = "- Secure proxy" + "- Proxy service with providing endpoint from registry")
    public void testSecureProxyEndPointFromReg() throws Exception {

        OMElement response = secureAxisServiceClient
                .sendSecuredStockQuoteRequest(context.getSuperTenant().getContextUser(),
                        getProxyServiceURLHttps("StockQuoteProxyScenario12"), 1, "WSO2");

        String lastPrice = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "last")).getText();
        assertNotNull(lastPrice, "Fault: response message 'last' price null");

        String symbol = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "symbol")).getText();
        assertEquals(symbol, "WSO2", "Fault: value 'symbol' mismatched");

    }

    @Test(groups = "wso2.esb", description = "- Secure proxy" + "- Proxy service with publishing wsdl inline")
    public void testSecureProxyWSDLInline() throws Exception {

        OMElement response = secureAxisServiceClient
                .sendSecuredStockQuoteRequest(context.getSuperTenant().getContextUser(),
                        getProxyServiceURLHttps("StockQuoteProxyScenario13"), 1, "WSO2");

        String lastPrice = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "last")).getText();
        assertNotNull(lastPrice, "Fault: response message 'last' price null");

        String symbol = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "symbol")).getText();
        assertEquals(symbol, "WSO2", "Fault: value 'symbol' mismatched");

    }

    @Test(groups = "wso2.esb", description = "- Secure proxy" + "- Proxy service with publishing wsdl source uri")
    public void testSecureProxyWSDLSourceUri() throws Exception {

        OMElement response = secureAxisServiceClient
                .sendSecuredStockQuoteRequest(context.getSuperTenant().getContextUser(),
                        getProxyServiceURLHttps("StockQuoteProxyScenario14"), 1, "WSO2");

        String lastPrice = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "last")).getText();
        assertNotNull(lastPrice, "Fault: response message 'last' price null");

        String symbol = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "symbol")).getText();
        assertEquals(symbol, "WSO2", "Fault: value 'symbol' mismatched");

    }

    @Test(groups = "wso2.esb", description = "- Secure proxy" + "- Proxy service with publishing wsdl from registry")
    public void testSecureProxyWSDLFromReg() throws Exception {

        OMElement response = secureAxisServiceClient
                .sendSecuredStockQuoteRequest(context.getSuperTenant().getContextUser(),
                        getProxyServiceURLHttps("StockQuoteProxyScenario15"), 1, "WSO2");

        String lastPrice = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "last")).getText();
        assertNotNull(lastPrice, "Fault: response message 'last' price null");

        String symbol = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "symbol")).getText();
        assertEquals(symbol, "WSO2", "Fault: value 'symbol' mismatched");

    }

    @Test(groups = "wso2.esb", description = "- Secure proxy" + "- Proxy service Enabling only HTTPS")
    public void testSecureProxyEnableOnlyHTTPS() throws Exception {

        OMElement response = secureAxisServiceClient
                .sendSecuredStockQuoteRequest(context.getSuperTenant().getContextUser(),
                        getProxyServiceURLHttps("StockQuoteProxyScenario16"), 1, "WSO2");

        String lastPrice = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "last")).getText();
        assertNotNull(lastPrice, "Fault: response message 'last' price null");

        String symbol = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "symbol")).getText();
        assertEquals(symbol, "WSO2", "Fault: value 'symbol' mismatched");

    }
    
    @Test(groups = "wso2.esb", description = "- Secure proxy" + "- Proxy service Enabling only HTTP")
    public void testSecureProxyEnableOnlyHTTP() throws Exception {

        OMElement response = secureAxisServiceClient
                .sendSecuredStockQuoteRequest(context.getSuperTenant().getContextUser(),
                        getProxyServiceURLHttp("StockQuoteProxyScenario17"), 5, "WSO2");

        String lastPrice = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "last")).getText();
        assertNotNull(lastPrice, "Fault: response message 'last' price null");

        String symbol = response.getFirstElement()
                .getFirstChildWithName(new QName("http://services.samples/xsd", "symbol")).getText();
        assertEquals(symbol, "WSO2", "Fault: value 'symbol' mismatched");

    }

}
