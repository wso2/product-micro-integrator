/*
 *  Copyright (c) 2023, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.esb.hotdeployment.test;

import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.ESBTestConstant;
import org.wso2.esb.integration.common.utils.common.TestConfigurationProvider;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

/**
 * Test case to verify the hot deployment of log4j2 configs.
 */
public class Log4j2ConfigsHotDeploymentTestCase extends ESBIntegrationTest {

    private final String SERVER_CONF_DIR =
            System.getProperty(ESBTestConstant.CARBON_HOME) + File.separator + "conf" + File.separator;
    private final String SOURCE_DIR =
            TestConfigurationProvider.getResourceLocation(ESBTestConstant.ESB_PRODUCT_GROUP) + File.separator
                    + "hotdeployment" + File.separator;
    private CarbonLogReader carbonLogReader;

    @BeforeClass(alwaysRun = true)
    public void deployService() throws Exception {
        super.init();
        carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();
    }

    @Test(groups = { "wso2.esb" }, description = "Tests hto deploying log4j2 configs")
    public void testHotDeployingLog4j2Configs() throws Exception {
        // test the proxy with disabled wire logs
        axis2Client.sendSimpleStockQuoteRequest(getProxyServiceURLHttp("StockQuoteProxy"), null, "WSO2");
        Assert.assertTrue(carbonLogReader.checkForLog(
                "Proxy Service StockQuoteProxy received a new message from", DEFAULT_TIMEOUT));

        // Add the log4j2 properties file with enabled wire logs
        deployLog4j2ConfigWithWireLogs();
        // wait to deploy the log4j2 configs (scan interval is 5 seconds)
        Thread.sleep(10000);

        // test the proxy with enabled wire logs
        carbonLogReader.clearLogs();
        axis2Client.sendSimpleStockQuoteRequest(getProxyServiceURLHttp("StockQuoteProxy"), null, "WSO2");
        Assert.assertTrue(carbonLogReader.checkForLog("HTTP-Listener I/O dispatcher", DEFAULT_TIMEOUT));
        Assert.assertTrue(carbonLogReader.checkForLog(">> \"POST /services/StockQuoteProxy", DEFAULT_TIMEOUT));

        // Add the log4j2 properties file with disabled wire logs
        undeployLog4j2ConfigWithWireLogs();
        // wait to deploy the log4j2 configs (scan interval is 5 seconds)
        Thread.sleep(10000);

        carbonLogReader.clearLogs();
        axis2Client.sendSimpleStockQuoteRequest(getProxyServiceURLHttp("StockQuoteProxy"), null, "WSO2");
        Assert.assertTrue(carbonLogReader.checkForLog(
                "Proxy Service StockQuoteProxy received a new message from", DEFAULT_TIMEOUT));
        Assert.assertFalse(carbonLogReader.checkForLog("HTTP-Listener I/O dispatcher", 10));
    }

    private void deployLog4j2ConfigWithWireLogs() throws Exception {
        String log4jFile = SOURCE_DIR + "log4j2withWire.properties";
        File destFile = new File(SERVER_CONF_DIR + "log4j2.properties");
        FileUtils.copyFile(new File(log4jFile), destFile, false);
        // Explicitly update the modification time so that the change get detected on Windows.
        Files.setLastModifiedTime(destFile.toPath(), FileTime.fromMillis(System.currentTimeMillis()));
    }

    private void undeployLog4j2ConfigWithWireLogs() throws Exception {
        String log4jFile = SOURCE_DIR + "log4j2.properties";
        File destFile = new File(SERVER_CONF_DIR + "log4j2.properties");
        FileUtils.copyFile(new File(log4jFile), destFile, false);
        // Explicitly update the modification time so that the change get detected on Windows.
        Files.setLastModifiedTime(destFile.toPath(), FileTime.fromMillis(System.currentTimeMillis()));
    }

    @AfterClass(alwaysRun = true)
    public void unDeployService() throws Exception {
        carbonLogReader.stop();
        super.cleanup();
    }
}
