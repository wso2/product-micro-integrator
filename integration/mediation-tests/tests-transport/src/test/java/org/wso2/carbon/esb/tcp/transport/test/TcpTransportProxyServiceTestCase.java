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
package org.wso2.carbon.esb.tcp.transport.test;

import org.apache.axiom.om.OMElement;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.annotations.ExecutionEnvironment;
import org.wso2.carbon.automation.engine.annotations.SetEnvironment;
import org.wso2.carbon.esb.tcp.transport.test.util.TcpClient;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

public class TcpTransportProxyServiceTestCase extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init();
    }

    @SetEnvironment(executionEnvironments = { ExecutionEnvironment.STANDALONE })
    @Test(groups = "wso2.esb", description = "Proxy service with Tcp transport")
    public void tcpTransportProxy() throws Exception {
        TcpClient tcpClient = new TcpClient();
        OMElement response;
        String tcpProxyUrl;
        tcpProxyUrl = "tcp://localhost:8290/services/tcpProxy/tcpProxy?contentType=application/soap+xml";
        response = tcpClient
                .sendSimpleStockQuote12(tcpProxyUrl, "TCPPROXY", tcpClient.CONTENT_TYPE_APPLICATIONS_SOAP_XML);
        Assert.assertTrue(response.toString().contains("TCPPROXY"), "Symbol not found in response message");
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        super.cleanup();
    }
}
