/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.server.util;

import org.wso2.config.mapper.ConfigParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

public class ICPStartupUtils {
    private static final Map<String, Object> configs = ConfigParser.getParsedConfigs();
    public static final String ICP_CONFIG_ENABLED = "icp_config.enabled";
    private static String runtimeId = null;
    private static final String runtimeIdFile = ".icp_runtime_id";
    protected static final String ICP_RUNTIME_LOG_SUFFIX = "icp.runtime.log.suffix";
    public static final String ICP_CONFIG_RUNTIME = "icp_config.runtime";

    /**
     * Checks if ICP is configured and enabled.
     * Returns true only if explicitly enabled via configuration.
     *
     * @return true if ICP is enabled, false otherwise
     */
    public static boolean isICPConfigured() {
        Object icpEnabled = configs.get(ICP_CONFIG_ENABLED);
        return icpEnabled != null && "true".equalsIgnoreCase(icpEnabled.toString());
    }

    /**
     * Initializes the runtime ID from cache, file, or generates a new one.
     * Also sets the runtime ID as a system property for logging.
     *
     * @throws IOException if there's an error reading or writing the runtime ID file
     */
    public static synchronized void initRuntimeId() throws IOException {
        // Return if already initialized
        if (runtimeId != null && !runtimeId.trim().isEmpty()) {
            return;
        }

        Path runtimeIdPath = Paths.get(runtimeIdFile);

        // Read from persisted file if present
        if (Files.exists(runtimeIdPath)) {
            String existingId = Files.readString(runtimeIdPath).trim();
            if (!existingId.isEmpty()) {
                runtimeId = existingId;
                setRuntimeIdSystemProperty(existingId);
                return;
            }
        }

        // Generate new ID as: <configured-runtime-id>-<uuid> if configured; else <uuid>
        String configuredPrefix = null;
        Object configuredRuntimeId = configs.get(ICP_CONFIG_RUNTIME);
        if (configuredRuntimeId != null) {
            String cfgId = configuredRuntimeId.toString().trim();
            if (!cfgId.isEmpty()) {
                configuredPrefix = cfgId;
            }
        }

        String newRuntimeId = (configuredPrefix != null ? configuredPrefix + "-" : "")
                + UUID.randomUUID();
        Files.writeString(runtimeIdPath, newRuntimeId);
        runtimeId = newRuntimeId;
        setRuntimeIdSystemProperty(newRuntimeId);
    }

    /**
     * Sets the runtime ID as a system property for logging purposes.
     *
     * @param id the runtime ID to set
     */
    private static void setRuntimeIdSystemProperty(String id) {
        if (System.getProperty(ICP_RUNTIME_LOG_SUFFIX) == null) {
            System.setProperty(ICP_RUNTIME_LOG_SUFFIX, "[icp.runtimeId=" + id + "]");
        }
    }
}
