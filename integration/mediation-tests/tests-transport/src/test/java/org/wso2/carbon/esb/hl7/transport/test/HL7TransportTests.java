/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.esb.hl7.transport.test;

import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class HL7TransportTests extends ESBIntegrationTest {

    private ServerConfigurationManager configurationManager;

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        configurationManager = new ServerConfigurationManager(new AutomationContext());
        String hl7Toml =
                FrameworkPathUtil.getSystemResourceLocation() + "artifacts" + File.separator + "ESB" + File.separator
                        + "hl7" + File.separator + "conf" + File.separator + "deployment.toml";
        String hl7Proxy =
                FrameworkPathUtil.getSystemResourceLocation() + "artifacts" + File.separator + "ESB" + File.separator
                        + "hl7" + File.separator + "proxy";
        Utils.deploySynapseConfiguration(new File(hl7Proxy + File.separator + "HL7ReceiverProxy.xml"),
                                         Utils.ArtifactType.PROXY);
        configurationManager.applyMIConfigurationWithRestart(new File(hl7Toml));
        super.init();
    }

    @AfterClass(alwaysRun = true)
    public void restoreServerConfiguration() throws Exception {

        Utils.undeploySynapseConfiguration("HL7ReceiverProxy", Utils.ArtifactType.PROXY, false);
        configurationManager.restoreToLastMIConfiguration();
    }

    @Test(groups = { "wso2.esb" },
            description = "testing hl7 transport receiver and sender")
    public void testHLTransport() throws Exception {

        CarbonLogReader logReader = new CarbonLogReader();
        logReader.start();
        // send request
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/xml");
        String apiUrl = getMainSequenceURL() + "hl7/send";
        HttpResponse response = HttpRequestUtil.doGet(apiUrl, headers);

        Assert.assertTrue("Response not received after invoking hl7", response.getData().startsWith("MSH"));
        Assert.assertTrue("Hl7 message hasn't reached the back end",
                          logReader.checkForLog("MESSAGE = HL7 Receiver Proxy", DEFAULT_TIMEOUT));
        logReader.stop();
    }
}
