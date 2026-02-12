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

import static org.testng.Assert.assertTrue;

/**
 * Testcase to test Synapse Expressions to fetch registry, registry property, path query and function params
 */
public class SynapseExpressionTestCase extends ESBIntegrationTest {

    private CarbonLogReader carbonLogReader;
    private static final String targetApiName = "synapseexpression_api";
    private static final String CAPP_NAME = "SynapseExpressionTestCase_1.0.0.car";
    private ServerConfigurationManager serverConfigurationManager;

    @BeforeClass(alwaysRun = true)
    public void uploadSynapseConfig() throws Exception {
        super.init();
        serverConfigurationManager = new ServerConfigurationManager(context);
        carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();
    }

    @Test(groups = "wso2.esb", description = "Testcase to test Synapse Expressions to fetch registry, registry " +
            "property, path query and function params")
    public void testSynapseExpressions() throws Exception {
        File metadataCAPP = new File(
                getESBResourceLocation() + File.separator + "synapseExpressions" + File.separator +
                        CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(metadataCAPP);
        assertTrue(Utils.checkForLog(carbonLogReader,
                "API named 'SynapseExpression_api' has been deployed from file",20), "API Deployment failed");

        String contentType = "application/json";
        String payload = "{\"hello\": \"world\"}";
        String url = getApiInvocationURL(targetApiName);
        url = url + "/abc/hello/pqr?qparam1=zxc&qparam2=uio";

        Map<String, String> headers = new HashMap<>(1);
        headers.put("Content-Type", contentType);

        SimpleHttpClient httpClient = new SimpleHttpClient();
        httpClient.doPost(url, headers, payload, contentType);

        carbonLogReader.checkForLog("pathParam1 = abc, pathParam2 = pqr, queryParam1 = zxc, queryParam2 = uio," +
                " regProp = hello world, regValue = John", DEFAULT_TIMEOUT);
        String logs = carbonLogReader.getLogs();
        assertTrue(logs.contains("pathParam1 = abc"));
        assertTrue(logs.contains("pathParam2 = pqr"));
        assertTrue(logs.contains("queryParam1 = zxc"));
        assertTrue(logs.contains("queryParam2 = uio"));
        assertTrue(logs.contains("regProp = hello world"));
        assertTrue(logs.contains("regValue = John"));
        assertTrue(logs.contains("funcParam1 = hello"));
        assertTrue(logs.contains("funcParam2 = world"));
        carbonLogReader.stop();
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        serverConfigurationManager.removeFromCarbonapps(CAPP_NAME);
        super.cleanup();
    }
}