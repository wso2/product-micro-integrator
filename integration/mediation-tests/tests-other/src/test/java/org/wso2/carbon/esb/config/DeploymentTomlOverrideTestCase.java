/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
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
package org.wso2.carbon.esb.config;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.esb.integration.common.extensions.carbonserver.CarbonServerExtension;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Integration test for https://github.com/wso2/product-micro-integrator/issues/4770.
 *
 * Verifies that deployment.toml is always treated as the single source of truth:
 * even if a generated config file (axis2.xml) is manually modified between restarts,
 * MI must regenerate it from deployment.toml on the next startup rather than keeping
 * the manual change.
 *
 * Root cause: -DavoidConfigHashRead=true in startup scripts caused ConfigParser to skip
 * regeneration when deployment.toml's hash was unchanged. Removing that flag ensures
 * axis2.xml (and other generated files) are always regenerated from deployment.toml.
 */
public class DeploymentTomlOverrideTestCase extends ESBIntegrationTest {

    private static final String FHIR_CONTENT_TYPE = "application/fhir+json";
    private static final String EXPECTED_BUILDER_CLASS =
            "org.wso2.micro.integrator.core.json.JsonStreamBuilder";
    private static final String MANUALLY_MODIFIED_CLASS =
            "org.wso2.micro.integrator.core.json.MANUALLY_MODIFIED_CLASS";

    private ServerConfigurationManager serverConfigurationManager;

    @BeforeClass(alwaysRun = true)
    public void initialize() throws Exception {
        super.init();
        serverConfigurationManager = new ServerConfigurationManager(
                new AutomationContext("ESB", TestUserMode.SUPER_TENANT_ADMIN));

        // Apply deployment.toml that includes a [[custom_message_builders]] entry, then restart.
        serverConfigurationManager.applyMIConfigurationWithRestart(
                new File(getESBResourceLocation() + File.separator + "config"
                        + File.separator + "deploymentTomlOverride"
                        + File.separator + "deployment.toml"));
        super.init();
    }

    /**
     * After the first startup the generated axis2.xml must already contain the message builder
     * entry declared in deployment.toml.
     */
    @Test(groups = { "wso2.esb" },
          description = "axis2.xml must contain the message builder declared in deployment.toml",
          priority = 1)
    public void testAxis2XmlGeneratedFromDeploymentToml() throws IOException {
        String axis2Content = readAxis2Xml();
        Assert.assertTrue(axis2Content.contains(FHIR_CONTENT_TYPE),
                "axis2.xml must contain the content type '" + FHIR_CONTENT_TYPE
                        + "' configured in deployment.toml");
        Assert.assertTrue(axis2Content.contains(EXPECTED_BUILDER_CLASS),
                "axis2.xml must reference the builder class '" + EXPECTED_BUILDER_CLASS
                        + "' configured in deployment.toml");
    }

    /**
     * After manually modifying axis2.xml and restarting the server WITHOUT changing
     * deployment.toml, the server must regenerate axis2.xml from deployment.toml and
     * the manual modification must NOT be present.
     *
     * This is the core regression test for issue #4770.
     */
    @Test(groups = { "wso2.esb" },
          description = "deployment.toml must override manually modified axis2.xml on restart",
          priority = 2,
          dependsOnMethods = "testAxis2XmlGeneratedFromDeploymentToml")
    public void testDeploymentTomlOverridesManualAxisXmlEdit() throws Exception {
        // Manually corrupt axis2.xml with an invalid class name.
        injectManualModificationIntoAxis2Xml();

        // Confirm the manual modification is in place before restarting.
        String axis2ContentBeforeRestart = readAxis2Xml();
        Assert.assertTrue(axis2ContentBeforeRestart.contains(MANUALLY_MODIFIED_CLASS),
                "Pre-condition: manually modified class must be present in axis2.xml before restart");

        // Restart the server without changing deployment.toml.
        CarbonServerExtension.restartServer();
        super.init();

        // After restart, deployment.toml must have overridden the manual change.
        String axis2ContentAfterRestart = readAxis2Xml();
        Assert.assertFalse(axis2ContentAfterRestart.contains(MANUALLY_MODIFIED_CLASS),
                "axis2.xml must NOT contain the manually modified class '" + MANUALLY_MODIFIED_CLASS
                        + "' after restart — deployment.toml is the single source of truth");
        Assert.assertTrue(axis2ContentAfterRestart.contains(EXPECTED_BUILDER_CLASS),
                "axis2.xml must contain the original builder class '" + EXPECTED_BUILDER_CLASS
                        + "' from deployment.toml after restart");
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() throws Exception {
        serverConfigurationManager.restoreToLastConfiguration();
        super.cleanup();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String readAxis2Xml() throws IOException {
        String carbonHome = System.getProperty(ServerConstants.CARBON_HOME);
        File axis2Xml = Paths.get(carbonHome, "conf", "axis2", "axis2.xml").toFile();
        Assert.assertTrue(axis2Xml.exists(), "axis2.xml must exist at: " + axis2Xml.getAbsolutePath());
        return new String(Files.readAllBytes(axis2Xml.toPath()), StandardCharsets.UTF_8);
    }

    private void injectManualModificationIntoAxis2Xml() throws IOException {
        String carbonHome = System.getProperty(ServerConstants.CARBON_HOME);
        File axis2Xml = Paths.get(carbonHome, "conf", "axis2", "axis2.xml").toFile();
        String content = new String(Files.readAllBytes(axis2Xml.toPath()), StandardCharsets.UTF_8);
        String modified = content.replace(EXPECTED_BUILDER_CLASS, MANUALLY_MODIFIED_CLASS);
        Files.write(axis2Xml.toPath(), modified.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING);
    }
}
