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

package org.wso2.carbon.esb.hl7.inbound.transport.test;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class HL7InboundPreprocessorTest extends ESBIntegrationTest {

    private CarbonLogReader logReader = null;

    private ServerConfigurationManager configurationManager;

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        configurationManager = new ServerConfigurationManager(new AutomationContext());
        String hl7Toml =
                FrameworkPathUtil.getSystemResourceLocation() + "artifacts" + File.separator + "ESB" + File.separator
                        + "hl7" + File.separator + "conf" + File.separator + "deployment.toml";
        configurationManager.applyMIConfigurationWithRestart(new File(hl7Toml));
        super.init();
        logReader = new CarbonLogReader();
        logReader.start();
    }

    @AfterClass(alwaysRun = true)
    public void restoreServerConfiguration() throws Exception {

        logReader.stop();
        configurationManager.restoreToLastMIConfiguration();
    }

    @Test(groups = { "wso2.esb" },
            description = "Test HL7 PreProcessor")
    public void testHL7MessagePreprocessorInboundAutoAck() throws Exception {

        logReader.clearLogs();
        Utils.deploySynapseConfiguration(addInbound(), "hl7_inbound", Utils.ArtifactType.INBOUND_ENDPOINT, false);
        Assert.assertTrue(logReader.checkForLog("Starting HL7 Inbound Endpoint on port 20003", DEFAULT_TIMEOUT),
                          "Inbound deployment failed");
        // send request
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/xml");
        String apiUrl = getMainSequenceURL() + "hl7/inbound20003";
        HttpRequestUtil.doGet(apiUrl, headers);
        boolean found = logReader.checkForLog("Message = HL7 Message Received via Inbound Endpoint", DEFAULT_TIMEOUT);
        Assert.assertTrue(found, "Hl7 Message not received in inbound.");
        Assert.assertTrue(logReader.checkForLog("Encoding ER7", DEFAULT_TIMEOUT),
                          "HL7MessagePreprocessor not working" + " as expected.");
        Utils.undeploySynapseConfiguration("hl7_inbound", Utils.ArtifactType.INBOUND_ENDPOINT, false);
    }

    private OMElement addInbound() throws Exception {
        return AXIOMUtil.stringToOM("<inboundEndpoint xmlns=\"http://ws.apache.org/ns/synapse\"\n"
                                            + "                 name=\"hl7_inbound\"\n"
                                            + "                 sequence=\"hl7-log\"\n"
                                            + "                 onError=\"fault\"\n"
                                            + "                 protocol=\"hl7\"\n"
                                            + "                 suspend=\"false\">\n" + "   <parameters>\n"
                                            + "      <parameter name=\"inbound.hl7.ValidateMessage\">true</parameter>\n"
                                            + "      <parameter name=\"inbound.hl7.Port\">20003</parameter>\n"
                                            + "      <parameter name=\"inbound.hl7.TimeOut\">3000</parameter>\n"
                                            + "      <parameter name=\"inbound.hl7.MessagePreProcessor\">org.wso2.sample.MessageFilter</parameter>\n"
                                            + "      <parameter name=\"inbound.hl7.AutoAck\">true</parameter>\n"
                                            + "      <parameter name=\"inbound.hl7.BuildInvalidMessages\">true</parameter>\n"
                                            + "      <parameter name=\"inbound.hl7.PassThroughInvalidMessages\">true</parameter>\n"
                                            + "      <parameter name=\"inbound.hl7.CharSet\">UTF-8</parameter>\n"
                                            + "   </parameters>\n" + "</inboundEndpoint>");
    }
}
