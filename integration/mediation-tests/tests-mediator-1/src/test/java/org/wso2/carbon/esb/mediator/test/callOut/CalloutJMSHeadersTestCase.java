/*
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.esb.mediator.test.callOut;

import org.apache.axiom.om.util.AXIOMUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.clients.axis2client.AxisServiceClient;

import static org.testng.Assert.assertTrue;

public class CalloutJMSHeadersTestCase extends ESBIntegrationTest {
    CarbonLogReader carbonLogReader = new CarbonLogReader();

    @BeforeClass(alwaysRun = true)
    protected void init() throws Exception {
        super.init();
        carbonLogReader.start();
    }

    @Test(groups = { "wso2.esb" }, description = "Callout JMS headers test case")
    public void testCalloutJMSHeaders() throws Exception {
        AxisServiceClient client = new AxisServiceClient();
        String payload = "<payload/>";
        AXIOMUtil.stringToOM(payload);

        client.sendRobust(AXIOMUtil.stringToOM(payload), getProxyServiceURLHttp("JMCalloutClientProxy"), "urn:mediate");
        boolean logFound = Utils.assertIfSystemLogContains(carbonLogReader, "RequestHeaderVal");
        assertTrue(logFound, "Required log entry not found");
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        carbonLogReader.stop();
    }
}
