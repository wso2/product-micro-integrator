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
package org.wso2.micro.integrator.server;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Unit tests to verify that the -DavoidConfigHashRead=true JVM property has been removed from
 * startup scripts (fix for https://github.com/wso2/product-micro-integrator/issues/4770).
 *
 * When avoidConfigHashRead=true is set, the ConfigParser skips regenerating derived config files
 * (e.g. axis2.xml) if deployment.toml's hash has not changed. This means manual edits to
 * generated files persist across restarts, violating the "deployment.toml as single source of
 * truth" principle. The fix removes this flag unconditionally from all startup scripts.
 */
public class StartupScriptConfigTest {

    private static final String AVOID_CONFIG_HASH_READ_FLAG = "avoidConfigHashRead";
    private static final String PROJECT_ROOT = resolveProjectRoot();

    @DataProvider(name = "startupScripts")
    public Object[][] startupScripts() {
        return new Object[][] {
            { "distribution/src/scripts/micro-integrator.sh" },
            { "distribution/src/scripts/micro-integrator.bat" },
            { "distribution/src/conf/wrapper.conf" }
        };
    }

    /**
     * Verifies that each startup script does NOT contain the -DavoidConfigHashRead flag.
     * This flag caused the ConfigParser to skip config regeneration from deployment.toml on
     * restart when deployment.toml had not changed, allowing manual edits to persist in
     * generated files like axis2.xml.
     */
    @Test(dataProvider = "startupScripts",
          description = "Startup scripts must not set -DavoidConfigHashRead=true")
    public void testAvoidConfigHashReadFlagAbsent(String relativeScriptPath) throws IOException {
        File scriptFile = new File(PROJECT_ROOT, relativeScriptPath);
        Assert.assertTrue(scriptFile.exists(),
                "Startup script not found: " + scriptFile.getAbsolutePath());

        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
        Assert.assertFalse(content.contains(AVOID_CONFIG_HASH_READ_FLAG),
                "Startup script '" + relativeScriptPath + "' must not contain '-D"
                        + AVOID_CONFIG_HASH_READ_FLAG + "=true'. "
                        + "This flag prevents deployment.toml from overriding manually edited "
                        + "generated config files (e.g. axis2.xml) on server restart.");
    }

    /**
     * Verifies that when the legacy avoidConfigUpdate=true system property is set, the
     * handleConfiguration() method in Main converts it to configParseOnly=true and
     * resets avoidConfigUpdate to false. This ensures deployment.toml is always used as
     * the single source of truth even if an external tool mistakenly sets avoidConfigUpdate.
     */
    @Test(description = "avoidConfigUpdate=true must be converted to configParseOnly=true")
    public void testAvoidConfigUpdatePropertyConversion() throws Exception {
        String originalAvoidConfigUpdate = System.getProperty(Main.AVOID_CONFIGURATION_UPDATE);
        String originalConfigParseOnly = System.getProperty(Main.ONLY_PARSE_CONFIGURATION);
        String originalCarbonHome = System.getProperty("carbon.home");
        String originalCarbonConfigDir = System.getProperty("carbon.config.dir.path");
        try {
            // handleConfiguration() calls Paths.get(carbon.home, ...) first, so supply a temp dir
            // to avoid NPE before the property-conversion block is reached.
            String tempDir = System.getProperty("java.io.tmpdir");
            System.setProperty("carbon.home", tempDir);
            System.setProperty("carbon.config.dir.path", tempDir);

            System.setProperty(Main.AVOID_CONFIGURATION_UPDATE, "true");
            System.clearProperty(Main.ONLY_PARSE_CONFIGURATION);

            // Invoke the private handleConfiguration() method via reflection.
            // The property conversion happens before ConfigParser.parse(), so even though
            // ConfigParser will fail (no real deployment files in the temp dir) the properties
            // are already updated by the time the exception propagates.
            Method method = Main.class.getDeclaredMethod("handleConfiguration");
            method.setAccessible(true);
            try {
                method.invoke(null);
            } catch (Exception ignored) {
                // ConfigParser.parse() fails in a unit-test environment; that is expected.
            }

            Assert.assertEquals(System.getProperty(Main.AVOID_CONFIGURATION_UPDATE), "false",
                    "avoidConfigUpdate must be reset to false after conversion");
            Assert.assertEquals(System.getProperty(Main.ONLY_PARSE_CONFIGURATION), "true",
                    "configParseOnly must be set to true when avoidConfigUpdate was true");
        } finally {
            restoreProperty(Main.AVOID_CONFIGURATION_UPDATE, originalAvoidConfigUpdate);
            restoreProperty(Main.ONLY_PARSE_CONFIGURATION, originalConfigParseOnly);
            restoreProperty("carbon.home", originalCarbonHome);
            restoreProperty("carbon.config.dir.path", originalCarbonConfigDir);
        }
    }

    /**
     * Verifies that when avoidConfigUpdate is NOT set, the configParseOnly property is
     * NOT auto-set. The normal code path (ConfigParser.parse()) is used directly.
     */
    @Test(description = "configParseOnly must not be set when avoidConfigUpdate is absent")
    public void testNoConversionWhenAvoidConfigUpdateAbsent() throws Exception {
        String originalAvoidConfigUpdate = System.getProperty(Main.AVOID_CONFIGURATION_UPDATE);
        String originalConfigParseOnly = System.getProperty(Main.ONLY_PARSE_CONFIGURATION);
        String originalCarbonHome = System.getProperty("carbon.home");
        String originalCarbonConfigDir = System.getProperty("carbon.config.dir.path");
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            System.setProperty("carbon.home", tempDir);
            System.setProperty("carbon.config.dir.path", tempDir);

            System.clearProperty(Main.AVOID_CONFIGURATION_UPDATE);
            System.clearProperty(Main.ONLY_PARSE_CONFIGURATION);

            Method method = Main.class.getDeclaredMethod("handleConfiguration");
            method.setAccessible(true);
            try {
                method.invoke(null);
            } catch (Exception ignored) {
                // ConfigParser.parse() fails in a unit-test environment; that is expected.
            }

            Assert.assertNull(System.getProperty(Main.ONLY_PARSE_CONFIGURATION),
                    "configParseOnly must remain unset when avoidConfigUpdate was not set");
        } finally {
            restoreProperty(Main.AVOID_CONFIGURATION_UPDATE, originalAvoidConfigUpdate);
            restoreProperty(Main.ONLY_PARSE_CONFIGURATION, originalConfigParseOnly);
            restoreProperty("carbon.home", originalCarbonHome);
            restoreProperty("carbon.config.dir.path", originalCarbonConfigDir);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static String resolveProjectRoot() {
        // Walk up from the module directory to find the project root (contains distribution/).
        File dir = new File(System.getProperty("user.dir"));
        while (dir != null) {
            if (new File(dir, "distribution").isDirectory()) {
                return dir.getAbsolutePath();
            }
            dir = dir.getParentFile();
        }
        return System.getProperty("user.dir");
    }
}
