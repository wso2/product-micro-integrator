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

package org.wso2.carbon.esb.https.inbound.transport.test;

import org.apache.axiom.om.OMElement;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.engine.context.beans.User;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.clients.SecureServiceClient;

import java.io.File;

public class HttpsInboundTransportTestCase extends ESBIntegrationTest {

    private SecureServiceClient secureAxisServiceClient;
    private static final String INBOUND_NAME = "HTTPSInboundTestIn";

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {

        init();
        secureAxisServiceClient = new SecureServiceClient();
        CarbonLogReader reader = new CarbonLogReader();
        reader.start();
        Utils.deploySynapseConfiguration(getArtifactConfig(), INBOUND_NAME, Utils.ArtifactType.INBOUND_ENDPOINT, false);
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            Assert.assertTrue(reader.checkForLog("Pass-through HttpsListenerEP Listener started on 0:0:0:0:0:0:0:0:8087", 60));
        } else {
            Assert.assertTrue(reader.checkForLog("Pass-through HttpsListenerEP Listener started on 0.0.0.0:8087", 60));
        }
        reader.stop();
    }

    @Test(groups = "wso2.esb",
            description = "Test HTTPS Inbound")
    public void testSecureProxyEndPointThruUri() throws Exception {

        OMElement response = secureAxisServiceClient.
                sendSecuredStockQuoteRequest(context.getSuperTenant().getContextUser(), "https://localhost:8087/", "WSO2", false);
        Assert.assertNotNull(response);
        Assert.assertEquals("getQuoteResponse", response.getLocalName());
        Utils.undeploySynapseConfiguration(INBOUND_NAME, Utils.ArtifactType.INBOUND_ENDPOINT, false);
    }

    private OMElement getArtifactConfig() throws Exception {

        String path = String.join(File.separator, "artifacts", "ESB", "https.inbound.transport", "synapse.xml");
        return esbUtils.loadResource(path);
    }
}
