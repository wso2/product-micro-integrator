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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.server.LauncherConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

public class ICPStartupUtils {
    private static final Log log = LogFactory.getLog(ICPStartupUtils.class);
    public static final String ICP_CONFIG_ENABLED = "icp_config.enabled";
    private static volatile String runtimeId = null;
    private static final String RUNTIME_ID_FILENAME = ".icp_runtime_id";
    protected static final String ICP_RUNTIME_LOG_SUFFIX = "icp.runtime.log.suffix";
    public static final String ICP_CONFIG_RUNTIME = "icp_config.runtime";

    /**
     * Checks if ICP is configured and enabled.
     * Returns true only if explicitly enabled via configuration.
     *
     * @return true if ICP is enabled, false otherwise
     */
    public static boolean isICPConfigured() {
        Object icpEnabled = getConfigs().get(ICP_CONFIG_ENABLED);
        return icpEnabled != null && "true".equalsIgnoreCase(icpEnabled.toString());
    }

    /**
     * Initializes the runtime ID from cache, file, or generates a new one.
     * Also sets the runtime ID as a system property for logging.
     *
    * @throws IOException if the runtime ID file path cannot be resolved (e.g., carbon.home is missing),
    *                     or if there's an error reading or writing the runtime ID file
     */
    public static synchronized void initRuntimeId() throws IOException {
        // Return if already initialized
        if (runtimeId != null && !runtimeId.trim().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Runtime ID already initialized: " + runtimeId);
            }
            return;
        }

        Path runtimeIdPath;
        try {
            runtimeIdPath = getRuntimeIdPath();
        } catch (IllegalStateException e) {
            throw new IOException("Cannot resolve ICP runtime ID file path", e);
        }

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
        Object configuredRuntimeId = getConfigs().get(ICP_CONFIG_RUNTIME);
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
        if (log.isDebugEnabled()) {
            log.debug("Generated new runtime ID: " + newRuntimeId);
        }
    }

    /**
     * Returns the initialized runtime ID from the in-memory cache without performing any I/O.
     * This accessor is thread-safe since {@code runtimeId} is declared {@code volatile}.
     * Returns {@code null} only if {@link #initRuntimeId()} has not completed initialization.
     *
     * @return cached runtime ID, or {@code null} if not initialized yet
     */
    public static String getRuntimeId() {
        return runtimeId;
    }

    /**
     * Returns the parsed configuration map by querying {@link ConfigParser} at call time,
     * ensuring the most up-to-date configuration is used rather than a snapshot taken at
     * class-load time.
     *
     * @return the current parsed configuration map
     */
    private static Map<String, Object> getConfigs() {
        return ConfigParser.getParsedConfigs();
    }

    /**
     * Resolves the absolute path to the runtime ID file under the MI home directory.
     * Uses the {@code carbon.home} system property to locate the home directory.
     *
     * @return absolute Path to the runtime ID file
     * @throws IllegalStateException if the carbon.home system property is not set
     */
    private static Path getRuntimeIdPath() {
        String carbonHome = System.getProperty(LauncherConstants.CARBON_HOME);
        if (carbonHome == null || carbonHome.trim().isEmpty()) {
            throw new IllegalStateException(
                    "System property '" + LauncherConstants.CARBON_HOME + "' is not set; "
                    + "cannot resolve the ICP runtime ID file path.");
        }
        return Paths.get(carbonHome, RUNTIME_ID_FILENAME);
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
