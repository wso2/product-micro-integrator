/*
 *  Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
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
package org.wso2.carbon.esb.synapse.expression.test;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Regression test for issue #4436:
 * Accessing a text/plain registry resource via a synapse expression must return the plain text
 * content directly, without attempting Base64 decoding.
 *
 * Before the fix, invoking registry() on a text/plain resource caused:
 *   java.lang.IllegalArgumentException: Illegal base64 character 20
 * because spaces (0x20) are not valid Base64 characters.
 */
public class SynapseExpressionTextPlainRegistryTestCase extends ESBIntegrationTest {

    private CarbonLogReader carbonLogReader;
    private static final String CAPP_NAME = "SynapseExpressionTextPlainTestCase_1.0.0.car";
    private static final String API_NAME = "synapseexpressiontextplain";
    private static final String EXPECTED_TEXT = "Hello from registry text resource";
    private ServerConfigurationManager serverConfigurationManager;

    @BeforeClass(alwaysRun = true)
    public void setUp() throws Exception {
        super.init();
        serverConfigurationManager = new ServerConfigurationManager(context);
        carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();
    }

    @Test(groups = "wso2.esb",
          description = "Regression test for issue #4436: text/plain registry resources must be "
                  + "returned as plain text, not Base64-decoded")
    public void testTextPlainRegistryResourceViaSynapseExpression() throws Exception {
        // Deploy the CApp that contains the text/plain registry resource and a test API
        File capp = new File(
                getESBResourceLocation() + File.separator + "synapseExpressions" + File.separator + CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(capp);
        assertTrue(Utils.checkForLog(carbonLogReader,
                "API named 'SynapseExpressionTextPlain_api' has been deployed from file", 20),
                "API deployment failed");

        // Invoke the API
        String url = getApiInvocationURL(API_NAME);
        SimpleHttpClient httpClient = new SimpleHttpClient();
        Map<String, String> headers = new HashMap<>();
        httpClient.doGet(url, headers);

        // The log mediator logs: regTextValue = Hello from registry text resource
        assertTrue(carbonLogReader.checkForLog("regTextValue = " + EXPECTED_TEXT, DEFAULT_TIMEOUT),
                "Expected log entry not found; text/plain registry resource was not returned correctly");

        // Verify the Base64 error is NOT present in the logs
        String logs = carbonLogReader.getLogs();
        assertFalse(logs.contains("Illegal base64 character"),
                "Base64 decoding error found in logs; fix was not applied correctly");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        carbonLogReader.stop();
        serverConfigurationManager.removeFromCarbonapps(CAPP_NAME);
        super.cleanup();
    }
}
