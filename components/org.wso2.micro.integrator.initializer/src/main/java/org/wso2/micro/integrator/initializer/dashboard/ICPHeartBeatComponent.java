/*
 * Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.micro.integrator.initializer.dashboard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.synapse.api.API;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.AxisConfiguration;
import org.wso2.carbon.inbound.endpoint.internal.http.api.ConfigurationLoader;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.application.deployer.CarbonApplication;
import org.wso2.micro.application.deployer.config.Artifact;
import org.wso2.micro.core.util.StringUtils;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.micro.integrator.initializer.deployment.application.deployer.CappDeployer;
import org.wso2.micro.integrator.registry.MicroIntegratorRegistry;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.wso2.micro.integrator.initializer.dashboard.Constants.*;

/**
 * Manages heartbeats from micro integrator to new ICP with JWT authentication,
 * delta heartbeat optimization, and comprehensive artifact metadata.
 */
public class ICPHeartBeatComponent {

    private ICPHeartBeatComponent() {
    }

    private static final Log log = LogFactory.getLog(ICPHeartBeatComponent.class);
    private static final Map<String, Object> configs = ConfigParser.getParsedConfigs();
    private static String lastRuntimeHash = null;
    private static String cachedJwtToken = null;
    private static long jwtTokenExpiry = 0;
    private static String runtimeIdFile = ".mi_runtime_id";
    private static String runtimeId = null;

    /**
     * Lazily initializes and returns the runtime ID.
     * Only initializes when ICP is configured.
     *
     * @return the runtime ID
     * @throws IOException if there's an error reading or writing the runtime ID file
     */
    private static synchronized String getRuntimeId() throws IOException {
        if (runtimeId == null) {
            runtimeId = initRuntimeId();
        }
        return runtimeId;
    }

    /**
     * Initializes the runtime ID from file or generates a new one.
     *
     * @return the runtime ID
     * @throws IOException if there's an error reading or writing the runtime ID file
     */
    private static String initRuntimeId() throws IOException {
        // Use current working directory for the runtime ID file
        Path runtimeIdPath = Paths.get(runtimeIdFile);

        // Check if file exists and read the UUID
        if (Files.exists(runtimeIdPath)) {
            String existingId = Files.readString(runtimeIdPath);
            // Validate it's not empty and trim whitespace
            String trimmedId = existingId.trim();
            if (!trimmedId.isEmpty()) {
                return trimmedId;
            }
        }

        // Generate new UUID if file doesn't exist or is empty
        String newRuntimeId = UUID.randomUUID().toString();
        Files.writeString(runtimeIdPath, newRuntimeId);
        return newRuntimeId;
    }

    /**
     * Starts the ICP heartbeat executor service that sends periodic delta heartbeats
     * and full heartbeats when requested by the ICP.
     */
    public static void invokeICPHeartbeatExecutorService() {
        String icpUrl = getConfigValue(ICP_CONFIG_URL, DEFAULT_ICP_URL);
        if (icpUrl == null) {
            log.warn("ICP URL not configured. ICP heartbeat will not be started.");
            return;
        }
        long interval = getInterval();
        log.info("Starting ICP heartbeat service. Interval: " + interval + "s");

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        Runnable runnableTask = () -> {
            try {
                sendDeltaHeartbeat(icpUrl);
            } catch (Exception e) {
                log.error("Error occurred while sending delta heartbeat to ICP.", e);
            }
        };
        
        // Initial delay of 5 seconds, then send at configured interval
        scheduledExecutorService.scheduleAtFixedRate(runnableTask, 5, interval, TimeUnit.SECONDS);
    }

    /**
     * Sends a delta heartbeat to ICP with only runtime hash.
     * If ICP detects a hash mismatch, it will respond with fullHeartbeatRequired=true.
     */
    private static void sendDeltaHeartbeat(String icpUrl) {
        try {
            // Build full payload to calculate hash
            JsonObject fullPayload = buildFullHeartbeatPayload(false);
            String currentHash = fullPayload.get("runtimeHash").getAsString();

            // Build delta payload
            JsonObject deltaPayload = new JsonObject();
            deltaPayload.addProperty("runtime", getRuntimeId());
            deltaPayload.addProperty("runtimeHash", currentHash);

            // Create timestamp in Ballerina time:Utc format [seconds, nanoseconds_fraction]
            deltaPayload.add("timestamp", createBallerinaTimestamp());

            String deltaEndpoint = icpUrl + ICP_DELTA_HEARTBEAT_ENDPOINT;
            JsonObject response = sendHeartbeatRequest(deltaEndpoint, deltaPayload);

            if (response != null && response.has("fullHeartbeatRequired") 
                    && response.get("fullHeartbeatRequired").getAsBoolean()) {
                log.info("ICP requested full heartbeat. Sending full heartbeat with all artifacts.");
                sendFullHeartbeat(icpUrl);
                lastRuntimeHash = currentHash;
            } else if (response != null && response.has("acknowledged") 
                    && response.get("acknowledged").getAsBoolean()) {
                log.debug("Delta heartbeat acknowledged by ICP.");
                lastRuntimeHash = currentHash;
            } else {
                log.debug("Unexpected response from ICP delta heartbeat.");
            }
        } catch (Exception e) {
            log.error("Error sending delta heartbeat to ICP.", e);
        }
    }

    /**
     * Sends a full heartbeat to ICP with all artifact metadata.
     */
    private static void sendFullHeartbeat(String icpUrl) {
        try {
            JsonObject fullPayload = buildFullHeartbeatPayload(true);
            String fullEndpoint = icpUrl + ICP_HEARTBEAT_ENDPOINT;
            
            JsonObject response = sendHeartbeatRequest(fullEndpoint, fullPayload);
            
            if (response != null && response.has("acknowledged") 
                    && response.get("acknowledged").getAsBoolean()) {
                log.info("Full heartbeat acknowledged by ICP.");
            } else {
                log.error("Unexpected response from ICP full heartbeat." + response.toString());
            }
        } catch (Exception e) {
            log.error("Error sending full heartbeat to ICP.", e);
        }
    }

    /**
     * Sends HTTP request to ICP with JWT authentication.
     */
    private static JsonObject sendHeartbeatRequest(String endpoint, JsonObject payload) {
        try (CloseableHttpClient client = createHttpClient()) {
            HttpPost httpPost = new HttpPost(endpoint);
            
            // Add JWT token to Authorization header
            String jwtToken = "";
            try {
                jwtToken = generateOrGetCachedJwtToken();
            } catch (Exception e) {
                log.error("Error while jwtToken creation ", e);
            }
            httpPost.setHeader("Authorization", "Bearer " + jwtToken);
            httpPost.setHeader("Accept", HEADER_VALUE_APPLICATION_JSON);
            httpPost.setHeader("Content-type", HEADER_VALUE_APPLICATION_JSON);
            
            StringEntity entity = new StringEntity(payload.toString(), "UTF-8");
            httpPost.setEntity(entity);
            
            CloseableHttpResponse response = client.execute(httpPost);
            return getJsonResponse(response);
        } catch (Exception e) {
            log.error("Error sending heartbeat request to ICP at: " + endpoint, e);
            return null;
        }
    }

    /**
     * Builds the full heartbeat payload with all artifact metadata.
     */
    private static JsonObject buildFullHeartbeatPayload(boolean includeTimestamp) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("runtime", getRuntimeId());
        payload.addProperty("runtimeType", RUNTIME_TYPE_MI);
        payload.addProperty("status", RUNTIME_STATUS_RUNNING);
        payload.addProperty("environment", getEnvironment());
        payload.addProperty("project", getProject());
        payload.addProperty("component", getComponent());
        payload.addProperty("version", getMicroIntegratorVersion());
        
        // Node information
        JsonObject nodeInfo = new JsonObject();
        nodeInfo.addProperty("platformName", "WSO2 Micro Integrator");
        nodeInfo.addProperty("platformVersion", getMicroIntegratorVersion());
        nodeInfo.addProperty("platformHome", System.getProperty("carbon.home"));
        nodeInfo.addProperty("osName", System.getProperty("os.name"));
        nodeInfo.addProperty("osVersion", System.getProperty("os.version"));
        nodeInfo.addProperty("osArch", System.getProperty("os.arch"));
        nodeInfo.addProperty("javaVersion", System.getProperty("java.version"));
        nodeInfo.addProperty("carbonHome", System.getProperty("carbon.home"));
        nodeInfo.addProperty("javaVendor", System.getProperty("java.vendor"));
        
        Runtime runtime = Runtime.getRuntime();
        nodeInfo.addProperty("totalMemory", runtime.totalMemory());
        nodeInfo.addProperty("freeMemory", runtime.freeMemory());
        nodeInfo.addProperty("maxMemory", runtime.maxMemory());
        nodeInfo.addProperty("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        payload.add("nodeInfo", nodeInfo);
        // Artifacts
        JsonObject artifacts = collectArtifacts();
        payload.add("artifacts", artifacts);
        
        // Hash (exclude timestamp for hash calculation)
        String hash = calculateHash(payload);
        payload.addProperty("runtimeHash", hash);
        
        // Add timestamp if requested
        if (includeTimestamp) {
            payload.add("timestamp", createBallerinaTimestamp());
        }
        
        // Validate payload structure for ICP compatibility
        payload = validateHeartbeatPayload(payload);
        
        log.info("Full heartbeat payload: " + payload.toString());
        return payload;
    }

    /**
     * Collects comprehensive artifact metadata from all MI Management API resources
     */
    private static JsonObject collectArtifacts() {
        JsonObject artifacts = new JsonObject();
        
        try {
            // Check if Synapse environment is available
            if (MicroIntegratorBaseUtils.getSynapseEnvironment() == null) {
                log.warn("Synapse environment is not available yet, returning empty artifacts");
                return createEmptyArtifactsStructure();
            }
            
            SynapseConfiguration synapseConfig = MicroIntegratorBaseUtils.getSynapseEnvironment()
                    .getSynapseConfiguration();
                    
            if (synapseConfig == null) {
                log.warn("Synapse configuration is not available yet, returning empty artifacts");
                return createEmptyArtifactsStructure();
            }
            
            // Collect all artifact types as available in Management API
            
            // 1. REST APIs
            artifacts.add("apis", collectRestApis(synapseConfig));
            
            // 2. Proxy Services  
            artifacts.add("proxyServices", collectProxyServices(synapseConfig));
            
            // 3. Endpoints
            artifacts.add("endpoints", collectEndpoints(synapseConfig));
            
            // 4. Inbound Endpoints
            artifacts.add("inboundEndpoints", collectInboundEndpoints(synapseConfig));
            
            // 5. Sequences
            artifacts.add("sequences", collectSequences(synapseConfig));
            
            // 6. Tasks
            artifacts.add("tasks", collectTasks(synapseConfig));
            
            // 7. Templates
            artifacts.add("templates", collectTemplates(synapseConfig));
            
            // 8. Message Stores
            artifacts.add("messageStores", collectMessageStores(synapseConfig));
            
            // 9. Message Processors
            artifacts.add("messageProcessors", collectMessageProcessors(synapseConfig));
            
            // 10. Local Entries
            artifacts.add("localEntries", collectLocalEntries(synapseConfig));
            
            // 11. Data Services (requires separate access)
            artifacts.add("dataServices", collectDataServices(synapseConfig));
            
            // 12. Carbon Applications (requires separate access)
            artifacts.add("carbonApps", collectCarbonApps());
            
            // 13. Data Sources (requires separate access)
            artifacts.add("dataSources", collectDataSources());
            
            // 14. Connectors (requires separate access)
            artifacts.add("connectors", collectConnectors(synapseConfig));
            
            // 15. Registry Resources (requires separate access)
            artifacts.add("registryResources", collectRegistryResources(synapseConfig));
            
            // 16. Listeners (HTTP/HTTPS ports)
            artifacts.add("listeners", collectListeners());
            
        } catch (Exception e) {
            log.error("Error collecting artifacts from MI configuration.", e);
            return createEmptyArtifactsStructure();
        }
        
        return artifacts;
    }
    
    private static JsonObject createEmptyArtifactsStructure() {
        JsonObject artifacts = new JsonObject();
        artifacts.add("apis", new JsonArray());
        artifacts.add("proxyServices", new JsonArray());
        artifacts.add("endpoints", new JsonArray());
        artifacts.add("inboundEndpoints", new JsonArray());
        artifacts.add("sequences", new JsonArray());
        artifacts.add("tasks", new JsonArray());
        artifacts.add("templates", new JsonArray());
        artifacts.add("messageStores", new JsonArray());
        artifacts.add("messageProcessors", new JsonArray());
        artifacts.add("localEntries", new JsonArray());
        artifacts.add("dataServices", new JsonArray());
        artifacts.add("carbonApps", new JsonArray());
        artifacts.add("dataSources", new JsonArray());
        artifacts.add("connectors", new JsonArray());
        artifacts.add("registryResources", new JsonArray());
        artifacts.add("listeners", new JsonArray());
        artifacts.add("systemInfo", new JsonObject());
        return artifacts;
    }
    
    /**
     * Validates and sanitizes artifacts structure to ensure compatibility with ICP GraphQL API
     */
    private static JsonObject validateAndSanitizeArtifacts(JsonObject artifacts) {
        try {
            log.debug("Validating artifacts structure for ICP compatibility");
            
            // Ensure all expected artifact arrays exist and are not null
            String[] requiredArrays = {
                "apis", "proxyServices", "endpoints", "inboundEndpoints", "sequences", 
                "tasks", "templates", "messageStores", "messageProcessors", "localEntries",
                "dataServices", "carbonApps", "dataSources", "connectors", "registryResources", "listeners"
            };
            
            for (String arrayName : requiredArrays) {
                if (!artifacts.has(arrayName) || artifacts.get(arrayName).isJsonNull()) {
                    log.warn("Missing or null artifact array: " + arrayName + ", adding empty array");
                    artifacts.add(arrayName, new JsonArray());
                } else if (!artifacts.get(arrayName).isJsonArray()) {
                    log.warn("Invalid artifact array type for: " + arrayName + ", replacing with empty array");
                    artifacts.add(arrayName, new JsonArray());
                }
            }
            
            // Ensure systemInfo exists and is a valid object
            if (!artifacts.has("systemInfo") || artifacts.get("systemInfo").isJsonNull()) {
                log.warn("Missing or null systemInfo, adding empty object");
                artifacts.add("systemInfo", new JsonObject());
            } else if (!artifacts.get("systemInfo").isJsonObject()) {
                log.warn("Invalid systemInfo type, replacing with empty object");
                artifacts.add("systemInfo", new JsonObject());
            }
            
            // Validate and sanitize individual artifact arrays
            for (String arrayName : requiredArrays) {
                JsonArray artifactArray = artifacts.getAsJsonArray(arrayName);
                JsonArray sanitizedArray = new JsonArray();
                
                for (int i = 0; i < artifactArray.size(); i++) {
                    try {
                        com.google.gson.JsonElement element = artifactArray.get(i);
                        if (element != null && element.isJsonObject()) {
                            JsonObject artifactObj = element.getAsJsonObject();
                            
                            // Ensure each artifact has at least a name and type
                            if (!artifactObj.has("name") || artifactObj.get("name").isJsonNull()) {
                                artifactObj.addProperty("name", "UNKNOWN_" + arrayName.toUpperCase() + "_" + i);
                            }
                            if (!artifactObj.has("type") || artifactObj.get("type").isJsonNull()) {
                                // Create proper type name by removing trailing 's' and capitalizing
                                String typeName = arrayName.replaceAll("s$", "");
                                typeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                                artifactObj.addProperty("type", typeName);
                            }
                            
                            sanitizedArray.add(artifactObj);
                        } else {
                            log.warn("Invalid artifact object in " + arrayName + " at index " + i + ", skipping");
                        }
                    } catch (Exception e) {
                        log.warn("Error validating artifact in " + arrayName + " at index " + i + ", skipping", e);
                    }
                }
                
                artifacts.add(arrayName, sanitizedArray);
            }
            
            log.debug("Artifacts validation completed successfully");
            return artifacts;
            
        } catch (Exception e) {
            log.error("Error validating artifacts structure, returning empty structure", e);
            return createEmptyArtifactsStructure();
        }
    }
    
    /**
     * Validates the entire heartbeat payload structure for Ballerina GraphQL API compatibility
     */
    private static JsonObject validateHeartbeatPayload(JsonObject payload) {
        try {
            log.debug("Validating heartbeat payload structure for ICP GraphQL API compatibility");
            
            // Ensure all required root-level properties exist and have correct types
            if (!payload.has("runtime") || payload.get("runtime").isJsonNull()) {
                payload.addProperty("runtime", UUID.randomUUID().toString());
                log.warn("Missing runtime, added default UUID");
            }
            
            if (!payload.has("runtimeType") || payload.get("runtimeType").isJsonNull()) {
                payload.addProperty("runtimeType", "MI");
                log.warn("Missing runtimeType, added default 'MI'");
            }
            
            if (!payload.has("status") || payload.get("status").isJsonNull()) {
                payload.addProperty("status", "RUNNING");
                log.warn("Missing status, added default 'RUNNING'");
            }
            
            if (!payload.has("environment") || payload.get("environment").isJsonNull()) {
                payload.addProperty("environment", "dev");
                log.warn("Missing environment, added default 'dev'");
            }
            
            if (!payload.has("project") || payload.get("project").isJsonNull()) {
                payload.addProperty("project", "default");
                log.warn("Missing project, added default 'default'");
            }
            
            if (!payload.has("component") || payload.get("component").isJsonNull()) {
                payload.addProperty("component", "micro-integrator");
                log.warn("Missing component, added default 'micro-integrator'");
            }
            
            if (!payload.has("version") || payload.get("version").isJsonNull()) {
                payload.addProperty("version", "4.4.0");
                log.warn("Missing version, added default '4.4.0'");
            }
            
            // Validate nodeInfo structure
            if (!payload.has("nodeInfo") || payload.get("nodeInfo").isJsonNull() || !payload.get("nodeInfo").isJsonObject()) {
                JsonObject nodeInfo = new JsonObject();
                nodeInfo.addProperty("platformName", "wso2-mi");
                nodeInfo.addProperty("platformVersion", "4.4.0");
                nodeInfo.addProperty("platformHome", System.getProperty("carbon.home", "/opt/wso2mi"));
                nodeInfo.addProperty("osName", System.getProperty("os.name", "unknown"));
                nodeInfo.addProperty("osVersion", System.getProperty("os.version", "unknown"));
                nodeInfo.addProperty("javaVersion", System.getProperty("java.version", "unknown"));
                payload.add("nodeInfo", nodeInfo);
                log.warn("Missing or invalid nodeInfo, added default nodeInfo structure");
            }
            
            // Validate artifacts structure
            if (!payload.has("artifacts") || payload.get("artifacts").isJsonNull() || !payload.get("artifacts").isJsonObject()) {
                payload.add("artifacts", createEmptyArtifactsStructure());
                log.warn("Missing or invalid artifacts, added empty artifacts structure");
            }
            
            // Ensure runtimeHash exists
            if (!payload.has("runtimeHash") || payload.get("runtimeHash").isJsonNull()) {
                payload.addProperty("runtimeHash", "");
                log.warn("Missing runtimeHash, added empty string");
            }
            
            // Validate timestamp structure if present
            if (payload.has("timestamp") && !payload.get("timestamp").isJsonNull()) {
                if (!payload.get("timestamp").isJsonArray()) {
                    payload.add("timestamp", createBallerinaTimestamp());
                    log.warn("Invalid timestamp format, replaced with valid Ballerina timestamp");
                } else {
                    JsonArray timestamp = payload.getAsJsonArray("timestamp");
                    if (timestamp.size() != 2) {
                        payload.add("timestamp", createBallerinaTimestamp());
                        log.warn("Invalid timestamp array size, replaced with valid Ballerina timestamp");
                    }
                }
            }
            
            log.debug("Heartbeat payload validation completed successfully");
            return payload;
            
        } catch (Exception e) {
            log.error("Error validating heartbeat payload structure, returning minimal payload", e);
            
            // Create minimal valid payload
            JsonObject minimalPayload = new JsonObject();
            minimalPayload.addProperty("runtime", UUID.randomUUID().toString());
            minimalPayload.addProperty("runtimeType", "MI");
            minimalPayload.addProperty("status", "RUNNING");
            minimalPayload.addProperty("environment", "dev");
            minimalPayload.addProperty("project", "default");
            minimalPayload.addProperty("component", "micro-integrator");
            minimalPayload.addProperty("version", "4.4.0");
            minimalPayload.addProperty("runtimeHash", "");
            
            JsonObject nodeInfo = new JsonObject();
            nodeInfo.addProperty("platformName", "wso2-mi");
            nodeInfo.addProperty("platformVersion", "4.4.0");
            minimalPayload.add("nodeInfo", nodeInfo);
            
            minimalPayload.add("artifacts", createEmptyArtifactsStructure());
            
            return minimalPayload;
        }
    }

    /**
     * Creates a Ballerina time:Utc compatible timestamp array [seconds, nanoseconds_fraction].
     */
    private static JsonArray createBallerinaTimestamp() {
        Instant now = Instant.now();
        JsonArray timestampArray = new JsonArray();
        timestampArray.add(now.getEpochSecond()); // seconds since epoch as int
        timestampArray.add(now.getNano() / 1_000_000_000.0); // nanoseconds as decimal fraction
        return timestampArray;
    }

    /**
     * Calculates MD5 hash of the payload (excluding timestamp and dynamic memory values).
     */
    private static String calculateHash(JsonObject payload) {
        try {
            // Create a copy and remove timestamp for consistent hashing
            JsonObject payloadCopy = payload.deepCopy();
            payloadCopy.remove("timestamp"); 
            if (payloadCopy.has("nodeInfo") && payloadCopy.get("nodeInfo").isJsonObject()) {
                JsonObject nodeInfo = payloadCopy.getAsJsonObject("nodeInfo");
                nodeInfo.remove("freeMemory");
                nodeInfo.remove("usedMemory");
                nodeInfo.remove("maxMemory");
                nodeInfo.remove("totalMemory");
            }
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(payloadCopy.toString().getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            log.error("Error calculating hash for heartbeat payload.", e);
            return "";
        }
    }

    /**
     * Generates a new JWT token or returns cached token if still valid.
     */
    private static String generateOrGetCachedJwtToken() throws Exception {
        long currentTime = System.currentTimeMillis();
        // Return cached token if it's still valid (with 5 minute buffer)
        if (cachedJwtToken != null && currentTime < (jwtTokenExpiry - 300000)) {
            return cachedJwtToken;
        }
        String jwtHmacSecret = getConfigValue(ICP_JWT_HMAC_SECRET, DEFAULT_JWT_HMAC_SECRET);
        HMACJWTTokenGenerator hmacJWTTokenGenerator = new HMACJWTTokenGenerator(jwtHmacSecret);
        String issuer = getConfigValue(ICP_JWT_ISSUER, DEFAULT_JWT_ISSUER);
        String audience = getConfigValue(ICP_JWT_AUDIENCE, DEFAULT_JWT_AUDIENCE);
        String scope = getConfigValue(ICP_JWT_SCOPE, DEFAULT_JWT_SCOPE);
        long expirySeconds = getJwtExpirySeconds();
        // Generate new token
        cachedJwtToken = hmacJWTTokenGenerator.generateToken(issuer, audience, scope, expirySeconds);
        jwtTokenExpiry = currentTime + (expirySeconds * 1000);
        return cachedJwtToken;
    }

    /**
     * Creates an HTTP client with SSL support.
     */
    private static CloseableHttpClient createHttpClient() throws Exception {
        return HttpClients.custom()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(
                        SSLContexts.custom()
                                .loadTrustMaterial(null, (TrustStrategy) new TrustSelfSignedStrategy())
                                .build(),
                        NoopHostnameVerifier.INSTANCE))
                .build();
    }

    /**
     * Gets the environment name.
     */
    private static String getEnvironment() {
        return getConfigValue(ICP_CONFIG_ENVIRONMENT, DEFAULT_ENVIRONMENT);
    }

    /**
     * Gets the project name.
     */
    private static String getProject() {
        return getConfigValue(ICP_CONFIG_PROJECT, DEFAULT_PROJECT);
    }

    /**
     * Gets the component name.
     */
    private static String getComponent() {
        return getConfigValue(ICP_CONFIG_COMPONENT, DEFAULT_COMPONENT);
    }

    /**
     * Gets the heartbeat interval in seconds.
     */
    private static long getInterval() {
        long interval = DEFAULT_HEARTBEAT_INTERVAL;
        Object configuredInterval = configs.get(ICP_CONFIG_HEARTBEAT_INTERVAL);
        if (configuredInterval != null) {
            interval = Integer.parseInt(configuredInterval.toString());
        }
        return interval;
    }

    /**
     * Gets the JWT token expiry time in seconds.
     */
    private static long getJwtExpirySeconds() {
        Object expiry = configs.get(ICP_JWT_EXPIRY_SECONDS);
        if (expiry != null) {
            return Long.parseLong(expiry.toString());
        }
        return DEFAULT_JWT_EXPIRY_SECONDS;
    }

    /**
     * Gets the MI version.
     */
    private static String getMicroIntegratorVersion() {
        return System.getProperty("product.version", "4.4.0");
    }

    /**
     * Helper method to get configuration value with fallback.
     */
    private static String getConfigValue(String key, String defaultValue) {
        Object value = configs.get(key);
        return (value != null) ? value.toString() : defaultValue;
    }

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
     * Parses JSON response from HTTP response.
     */
    private static JsonObject getJsonResponse(CloseableHttpResponse response) {
        try {
            HttpEntity entity = response.getEntity();
            String stringResponse = EntityUtils.toString(entity, "UTF-8");
            Gson gson = new Gson();
            return gson.fromJson(stringResponse, JsonObject.class);
        } catch (Exception e) {
            log.debug("Error parsing JSON response from ICP.", e);
            return null;
        }
    }

    // ===== ARTIFACT COLLECTION METHODS =====
    
    /**
     * Collects REST API information from Synapse Configuration
     */
    private static JsonArray collectRestApis(SynapseConfiguration synapseConfig) {
        JsonArray apis = new JsonArray();
        try {
            Collection<API> apiCollection = synapseConfig.getAPIs();
            for (API api : apiCollection) {
                JsonObject apiObj = new JsonObject();
                apiObj.addProperty("name", api.getName());
                apiObj.addProperty("context", api.getContext());
                apiObj.addProperty("version", api.getVersion());
                apiObj.addProperty("host", api.getHost());
                apiObj.addProperty("port", api.getPort());
                apiObj.addProperty("type", "API");
                apiObj.addProperty("state", "ENABLED");
                
                // Collect API resources
                JsonArray resources = new JsonArray();
                if (api.getResources() != null) {
                    for (org.apache.synapse.api.Resource resource : api.getResources()) {
                        JsonObject resourceObj = new JsonObject();
                        
                        String resourcePath = "";
                        try {
                            if (resource.getDispatcherHelper() != null) {
                                resourcePath = resource.getDispatcherHelper().getString();
                            }
                            if (resourcePath == null || resourcePath.isEmpty()) {
                                resourcePath = "/*";
                            }
                        } catch (Exception ex) {
                            resourcePath = "/*";
                        }
                        resourceObj.addProperty("path", resourcePath);
                        
                        if (resource.getMethods() != null && resource.getMethods().length > 0) {
                            resourceObj.addProperty("methods", String.join(",", resource.getMethods()));
                        }
                        resources.add(resourceObj);
                    }
                }
                apiObj.add("resources", resources);
                apis.add(apiObj);
            }
        } catch (Exception e) {
            log.error("Error collecting REST APIs", e);
        }
        return apis;
    }
    
    /**
     * Collects Proxy Service information from Synapse Configuration
     */
    private static JsonArray collectProxyServices(SynapseConfiguration synapseConfig) {
        JsonArray proxies = new JsonArray();
        try {
            Collection<ProxyService> proxyCollection = synapseConfig.getProxyServices();
            for (ProxyService proxy : proxyCollection) {
                JsonObject proxyObj = new JsonObject();
                proxyObj.addProperty("name", proxy.getName());
                proxyObj.addProperty("type", "ProxyService");
                proxyObj.addProperty("state", proxy.isRunning() ? "ENABLED" : "DISABLED");
                
                // Transport information
                if (proxy.getTransports() != null) {
                    JsonArray transports = new JsonArray();
                    for (Object transport : proxy.getTransports()) {
                        transports.add(transport.toString());
                    }
                    proxyObj.add("transports", transports);
                }
                
                proxies.add(proxyObj);
            }
        } catch (Exception e) {
            log.error("Error collecting Proxy Services", e);
        }
        return proxies;
    }
    
    /**
     * Collects Endpoint information from Synapse Configuration
     */
    private static JsonArray collectEndpoints(SynapseConfiguration synapseConfig) {
        JsonArray endpoints = new JsonArray();
        try {
            Map<String, org.apache.synapse.endpoints.Endpoint> endpointMap = synapseConfig.getDefinedEndpoints();
            for (Map.Entry<String, org.apache.synapse.endpoints.Endpoint> entry : endpointMap.entrySet()) {
                JsonObject endpointObj = new JsonObject();
                org.apache.synapse.endpoints.Endpoint endpoint = entry.getValue();
                endpointObj.addProperty("name", entry.getKey());
                endpointObj.addProperty("type", endpoint.getClass().getSimpleName());
                endpoints.add(endpointObj);
            }
        } catch (Exception e) {
            log.error("Error collecting Endpoints", e);
        }
        return endpoints;
    }
    
    /**
     * Collects Inbound Endpoint information
     */
    private static JsonArray collectInboundEndpoints(SynapseConfiguration synapseConfig) {
        JsonArray inboundEndpoints = new JsonArray();
        try {
            Collection<org.apache.synapse.inbound.InboundEndpoint> inboundCollection = 
                synapseConfig.getInboundEndpoints();
            for (org.apache.synapse.inbound.InboundEndpoint inbound : inboundCollection) {
                JsonObject inboundObj = new JsonObject();
                inboundObj.addProperty("name", inbound.getName());
                inboundObj.addProperty("protocol", inbound.getProtocol());
                inboundObj.addProperty("state", "ENABLED");
                inboundEndpoints.add(inboundObj);
            }
        } catch (Exception e) {
            log.error("Error collecting Inbound Endpoints", e);
        }
        return inboundEndpoints;
    }
    
    /**
     * Collects Sequence information from Synapse Configuration
     */
    private static JsonArray collectSequences(SynapseConfiguration synapseConfig) {
        JsonArray sequences = new JsonArray();
        try {
            Map<String, org.apache.synapse.mediators.base.SequenceMediator> seqMap = 
                synapseConfig.getDefinedSequences();
            for (Map.Entry<String, org.apache.synapse.mediators.base.SequenceMediator> entry : seqMap.entrySet()) {
                JsonObject seqObj = new JsonObject();
                seqObj.addProperty("name", entry.getKey());
                seqObj.addProperty("type", "Sequence");
                sequences.add(seqObj);
            }
        } catch (Exception e) {
            log.error("Error collecting Sequences", e);
        }
        return sequences;
    }
    
    /**
     * Collects Task information from Synapse Configuration
     */
    private static JsonArray collectTasks(SynapseConfiguration synapseConfig) {
        JsonArray tasks = new JsonArray();
        try {
            // Note: Task collection might require different approach based on MI version
            // For now, we'll create a placeholder implementation
            log.debug("Tasks collection placeholder - requires TaskManager integration");
        } catch (Exception e) {
            log.error("Error collecting Tasks", e);
        }
        return tasks;
    }
    
    /**
     * Collects Template information from Synapse Configuration
     */
    private static JsonArray collectTemplates(SynapseConfiguration synapseConfig) {
        JsonArray templates = new JsonArray();
        try {
            // Endpoint Templates
            Map<String, org.apache.synapse.endpoints.Template> endpointTemplates = 
                synapseConfig.getEndpointTemplates();
            for (Map.Entry<String, org.apache.synapse.endpoints.Template> entry : endpointTemplates.entrySet()) {
                JsonObject templateObj = new JsonObject();
                templateObj.addProperty("name", entry.getKey());
                templateObj.addProperty("type", "EndpointTemplate");
                templates.add(templateObj);
            }
        } catch (Exception e) {
            log.error("Error collecting Templates", e);
        }
        return templates;
    }
    
    /**
     * Collects Message Store information from Synapse Configuration
     */
    private static JsonArray collectMessageStores(SynapseConfiguration synapseConfig) {
        JsonArray messageStores = new JsonArray();
        try {
            Map<String, org.apache.synapse.message.store.MessageStore> storeMap = 
                synapseConfig.getMessageStores();
            for (Map.Entry<String, org.apache.synapse.message.store.MessageStore> entry : storeMap.entrySet()) {
                JsonObject storeObj = new JsonObject();
                org.apache.synapse.message.store.MessageStore store = entry.getValue();
                storeObj.addProperty("name", entry.getKey());
                storeObj.addProperty("type", store.getClass().getSimpleName());
                storeObj.addProperty("size", store.size());
                messageStores.add(storeObj);
            }
        } catch (Exception e) {
            log.error("Error collecting Message Stores", e);
        }
        return messageStores;
    }
    
    /**
     * Collects Message Processor information from Synapse Configuration
     */
    private static JsonArray collectMessageProcessors(SynapseConfiguration synapseConfig) {
        JsonArray messageProcessors = new JsonArray();
        try {
            Map<String, org.apache.synapse.message.processor.MessageProcessor> processorMap = 
                synapseConfig.getMessageProcessors();
            for (Map.Entry<String, org.apache.synapse.message.processor.MessageProcessor> entry : processorMap.entrySet()) {
                JsonObject processorObj = new JsonObject();
                org.apache.synapse.message.processor.MessageProcessor processor = entry.getValue();
                processorObj.addProperty("name", entry.getKey());
                processorObj.addProperty("type", processor.getClass().getSimpleName());
                processorObj.addProperty("state", "ENABLED"); // Default state
                messageProcessors.add(processorObj);
            }
        } catch (Exception e) {
            log.error("Error collecting Message Processors", e);
        }
        return messageProcessors;
    }
    
    /**
     * Collects Local Entry information from Synapse Configuration
     */
    private static JsonArray collectLocalEntries(SynapseConfiguration synapseConfig) {
        JsonArray localEntries = new JsonArray();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> entryMap = synapseConfig.getLocalRegistry();
            for (Map.Entry<String, Object> entry : entryMap.entrySet()) {
                JsonObject entryObj = new JsonObject();
                entryObj.addProperty("name", entry.getKey());
                entryObj.addProperty("type", "LocalEntry");
                entryObj.addProperty("valueType", entry.getValue().getClass().getSimpleName());
                localEntries.add(entryObj);
            }
        } catch (Exception e) {
            log.error("Error collecting Local Entries", e);
        }
        return localEntries;
    }
    
    /**
     * Collects Data Service information from Synapse Configuration
     */
    private static JsonArray collectDataServices(SynapseConfiguration synapseConfig) {
        JsonArray dataServices = new JsonArray();
        try {
            if (synapseConfig == null || synapseConfig.getAxisConfiguration() == null) {
                log.debug("Synapse configuration or Axis configuration is not available for data services collection");
                return dataServices;
            }
            
            AxisConfiguration axisConfiguration = synapseConfig.getAxisConfiguration();
            
            // Get available data service names using DBUtils
            String[] dataServiceNames = org.wso2.micro.integrator.dataservices.core.DBUtils.getAvailableDS(axisConfiguration);
            
            for (String serviceName : dataServiceNames) {
                JsonObject dsObj = new JsonObject();
                try {
                    dsObj.addProperty("name", serviceName);
                    dsObj.addProperty("type", "DataService");
                    
                    // Try to get the DataService object for more details
                    AxisService axisService = axisConfiguration.getServiceForActivation(serviceName);
                    if (axisService != null) {
                        // Get DataService object from axis service parameter
                        Parameter dsParam = axisService.getParameter("DataService");
                        if (dsParam != null && dsParam.getValue() instanceof org.wso2.micro.integrator.dataservices.core.engine.DataService) {
                            org.wso2.micro.integrator.dataservices.core.engine.DataService dataService = 
                                (org.wso2.micro.integrator.dataservices.core.engine.DataService) dsParam.getValue();
                            
                            // Add service description if available
                            if (dataService.getDescription() != null) {
                                dsObj.addProperty("description", dataService.getDescription());
                            }
                            
                            // Add config information
                            if (dataService.getConfigs() != null && !dataService.getConfigs().isEmpty()) {
                                JsonArray configs = new JsonArray();
                                for (String configId : dataService.getConfigs().keySet()) {
                                    configs.add(configId);
                                }
                                dsObj.add("configs", configs);
                            }
                            
                            // Add operation count
                            if (dataService.getOperationNames() != null) {
                                dsObj.addProperty("operationCount", dataService.getOperationNames().size());
                            }
                            
                            // Add query count  
                            if (dataService.getQueries() != null) {
                                dsObj.addProperty("queryCount", dataService.getQueries().size());
                            }
                        }
                        
                        // Add service status
                        dsObj.addProperty("state", axisService.isActive() ? "ENABLED" : "DISABLED");
                    }
                    
                    dataServices.add(dsObj);
                } catch (Exception e) {
                    log.warn("Error processing data service: " + serviceName, e);
                    // Add basic info even if detailed processing fails
                    JsonObject basicDsObj = new JsonObject();
                    basicDsObj.addProperty("name", serviceName);
                    basicDsObj.addProperty("type", "DataService");
                    basicDsObj.addProperty("state", "UNKNOWN");
                    dataServices.add(basicDsObj);
                }
            }
        } catch (Exception e) {
            log.error("Error collecting Data Services", e);
        }
        return dataServices;
    }
    
    /**
     * Collects Carbon Application information in the required heartbeat format
     */
    private static JsonArray collectCarbonApps() {
        JsonArray carbonApps = new JsonArray();
        try {
            // Get active Carbon Applications
            Collection<CarbonApplication> activeApps = CappDeployer.getCarbonApps();
            for (CarbonApplication app : activeApps) {
                JsonObject appObj = convertCarbonAppToHeartbeatFormat(app, "active", getRuntimeId());
                if (appObj != null) {
                    carbonApps.add(appObj);
                }
            }
            
            // Get faulty Carbon Applications  
            Collection<CarbonApplication> faultyApps = CappDeployer.getFaultyCAppObjects();
            for (CarbonApplication app : faultyApps) {
                JsonObject appObj = convertCarbonAppToHeartbeatFormat(app, "faulty", getRuntimeId());
                if (appObj != null) {
                    carbonApps.add(appObj);
                }
            }
            
        } catch (Exception e) {
            log.error("Error collecting Carbon Apps", e);
        }
        return carbonApps;
    }
    
    /**
     * Converts a CarbonApplication to the required heartbeat format with name, nameIgnoreCase, and nodes structure
     */
    private static JsonObject convertCarbonAppToHeartbeatFormat(CarbonApplication carbonApp, String status, String runtimeId) {
        if (carbonApp == null) {
            return null;
        }
        
        JsonObject appObj = new JsonObject();
        String appName = carbonApp.getAppName();
        
        // Main structure
        appObj.addProperty("name", appName); 
        appObj.addProperty("runtimeId", runtimeId);
        appObj.addProperty("version", carbonApp.getAppVersion());
        appObj.addProperty("status", status);
               
        // Create nodes array with single node (this MI instance)
        // JsonArray nodes = new JsonArray();
        // JsonObject nodeObj = new JsonObject();

        // Create details JSON string
        // JsonObject details = new JsonObject();
        // details.addProperty("name", appName);
        // details.addProperty("version", carbonApp.getAppVersion());
        // details.addProperty("status", status);
        
        // // Collect artifacts contained in this Carbon App
        // JsonArray artifacts = new JsonArray();
        // try {
        //     if (carbonApp.getAppConfig() != null && 
        //         carbonApp.getAppConfig().getApplicationArtifact() != null &&
        //         carbonApp.getAppConfig().getApplicationArtifact().getDependencies() != null) {
                
        //         for (Artifact.Dependency dependency : carbonApp.getAppConfig().getApplicationArtifact().getDependencies()) {
        //             Artifact artifact = dependency.getArtifact();
                    
        //             if (artifact != null && artifact.getName() != null) {
        //                 JsonObject artifactObj = new JsonObject();
        //                 artifactObj.addProperty("name", artifact.getName());
                        
        //                 // Extract artifact type (remove prefix if present)
        //                 String artifactType = artifact.getType();
        //                 if (artifactType != null && artifactType.contains("/")) {
        //                     artifactType = artifactType.split("/")[1];
        //                 }
        //                 artifactObj.addProperty("type", artifactType);
                        
        //                 artifacts.add(artifactObj);
        //             }
        //         }
        //     }
        // } catch (Exception e) {
        //     log.warn("Error processing artifacts for Carbon App: " + appName, e);
        // }
        
        // details.add("artifacts", artifacts);
        
        // // Convert details to JSON string and add to node
        // nodeObj.addProperty("details", details.toString());
        // nodes.add(nodeObj);
        
        // appObj.add("nodes", nodes);
        
        return appObj;
    }
    
    /**
     * Collects Data Source information
     */
    private static JsonArray collectDataSources() {
        JsonArray dataSources = new JsonArray();
        try {
            // Note: This would require access to the DataSource manager
            // For now, returning empty array as it requires integration with DS manager
            log.debug("Data Sources collection not yet implemented - requires DS manager integration");
        } catch (Exception e) {
            log.error("Error collecting Data Sources", e);
        }
        return dataSources;
    }
    
    /**
     * Collects Connector information from Synapse Configuration
     */
    private static JsonArray collectConnectors(SynapseConfiguration synapseConfig) {
        JsonArray connectors = new JsonArray();
        try {
            log.info("Starting connector collection for ICP heartbeat");
            
            if (synapseConfig == null) {
                log.warn("SynapseConfiguration is null, returning empty connector list");
                return connectors;
            }
            
            // Get synapse libraries (which include connectors) 
            Map<String, org.apache.synapse.libraries.model.Library> libraryMap = synapseConfig.getSynapseLibraries();
            
            if (libraryMap == null || libraryMap.isEmpty()) {
                log.info("No connectors/libraries found in SynapseConfiguration");
                return connectors;
            }
            
            log.info("Found " + libraryMap.size() + " libraries/connectors to process");
            
            int processedCount = 0;
            int errorCount = 0;
            
            for (Map.Entry<String, org.apache.synapse.libraries.model.Library> entry : libraryMap.entrySet()) {
                try {
                    String qualifiedName = entry.getKey();
                    org.apache.synapse.libraries.model.Library library = entry.getValue();
                    
                    if (library instanceof org.apache.synapse.libraries.model.SynapseLibrary) {
                        org.apache.synapse.libraries.model.SynapseLibrary synapseLibrary = 
                            (org.apache.synapse.libraries.model.SynapseLibrary) library;
                        
                        JsonObject connectorObj = new JsonObject();
                        connectorObj.addProperty("name", synapseLibrary.getName());
                        connectorObj.addProperty("package", synapseLibrary.getPackage());
                        connectorObj.addProperty("qualifiedName", qualifiedName);
                        connectorObj.addProperty("type", "Connector");
                        
                        // Add description if available
                        if (synapseLibrary.getDescription() != null) {
                            connectorObj.addProperty("description", synapseLibrary.getDescription());
                        }
                        
                        // Add status information
                        Boolean libStatus = synapseLibrary.getLibStatus();
                        String status = (libStatus != null && libStatus) ? "enabled" : "disabled";
                        connectorObj.addProperty("status", status);
                        
                        // Add file name if available
                        if (synapseLibrary.getFileName() != null) {
                            connectorObj.addProperty("fileName", synapseLibrary.getFileName());
                        }
                        
                        connectors.add(connectorObj);
                        processedCount++;
                        
                        if (log.isDebugEnabled()) {
                            log.debug("Processed connector: " + synapseLibrary.getName() + 
                                " (package: " + synapseLibrary.getPackage() + ", status: " + status + ")");
                        }
                    } else {
                        log.debug("Skipping non-SynapseLibrary entry: " + qualifiedName + 
                            " (type: " + library.getClass().getSimpleName() + ")");
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.warn("Error processing connector entry: " + entry.getKey(), e);
                    
                    // Add basic error entry
                    JsonObject errorConnectorObj = new JsonObject();
                    errorConnectorObj.addProperty("name", "ERROR_" + entry.getKey());
                    errorConnectorObj.addProperty("type", "Connector");
                    errorConnectorObj.addProperty("status", "error");
                    errorConnectorObj.addProperty("error", e.getMessage());
                    connectors.add(errorConnectorObj);
                }
            }
            
            log.info("Connector collection completed. Processed: " + processedCount + 
                ", Errors: " + errorCount + ", Total connectors collected: " + connectors.size());
                
        } catch (Exception e) {
            log.error("Error collecting Connectors from SynapseConfiguration", e);
        }
        return connectors;
    }
    
    /**
     * Collects Registry Resources from MicroIntegratorRegistry
     */
    private static JsonArray collectRegistryResources(SynapseConfiguration synapseConfig) {
        JsonArray registryResources = new JsonArray();
        try {
            log.info("Starting registry resources collection for ICP heartbeat");
            
            if (synapseConfig == null) {
                log.warn("SynapseConfiguration is null, returning empty registry resources list");
                return registryResources;
            }
            
            // Get the registry from synapse configuration
            org.apache.synapse.registry.Registry synapseRegistry = synapseConfig.getRegistry();
            if (synapseRegistry == null) {
                log.warn("No registry found in SynapseConfiguration, returning empty registry resources list");
                return registryResources;
            }
            
            if (!(synapseRegistry instanceof org.wso2.micro.integrator.registry.MicroIntegratorRegistry)) {
                log.warn("Registry is not MicroIntegratorRegistry type, cannot collect resources. Type: " + 
                    synapseRegistry.getClass().getSimpleName());
                return registryResources;
            }
            
            org.wso2.micro.integrator.registry.MicroIntegratorRegistry microIntegratorRegistry = 
                (org.wso2.micro.integrator.registry.MicroIntegratorRegistry) synapseRegistry;
            
            String regRoot = microIntegratorRegistry.getRegRoot();
            log.info("Registry root path: " + regRoot);
            
            if (regRoot == null || regRoot.trim().isEmpty()) {
                log.warn("Registry root path is null or empty, returning empty registry resources list");
                return registryResources;
            }
            
            // Get the root directory contents
            java.io.File rootDir = new java.io.File(regRoot);
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                log.warn("Registry root directory does not exist or is not a directory: " + regRoot);
                return registryResources;
            }
            
            // Get immediate children of the registry root
            org.json.JSONArray childrenList = microIntegratorRegistry.getChildrenList(regRoot, regRoot);
            
            if (childrenList == null) {
                log.warn("Failed to get children list from registry root");
                return registryResources;
            }
            
            log.info("Found " + childrenList.length() + " registry resources to process");
            
            int processedCount = 0;
            int errorCount = 0;
            
            // Convert JSONArray to JsonArray and add metadata
            for (int i = 0; i < childrenList.length(); i++) {
                try {
                    org.json.JSONObject child = childrenList.getJSONObject(i);
                    
                    JsonObject resourceObj = new JsonObject();
                    
                    // Basic properties
                    if (child.has("name")) {
                        resourceObj.addProperty("name", child.getString("name"));
                    }
                    if (child.has("type")) {
                        resourceObj.addProperty("type", child.getString("type"));
                    }
                    if (child.has("path")) {
                        resourceObj.addProperty("path", child.getString("path"));
                    }
                    if (child.has("mediaType")) {
                        resourceObj.addProperty("mediaType", child.getString("mediaType"));
                    }
                    if (child.has("size")) {
                        resourceObj.addProperty("size", child.getString("size"));
                    }
                    if (child.has("lastModified")) {
                        resourceObj.addProperty("lastModified", child.getString("lastModified"));
                    }
                    
                    // Properties array if exists
                    if (child.has("properties")) {
                        org.json.JSONArray properties = child.getJSONArray("properties");
                        JsonArray propsArray = new JsonArray();
                        for (int j = 0; j < properties.length(); j++) {
                            org.json.JSONObject prop = properties.getJSONObject(j);
                            JsonObject propObj = new JsonObject();
                            if (prop.has("name")) {
                                propObj.addProperty("name", prop.getString("name"));
                            }
                            if (prop.has("value")) {
                                propObj.addProperty("value", prop.getString("value"));
                            }
                            propsArray.add(propObj);
                        }
                        resourceObj.add("properties", propsArray);
                    }
                    
                    registryResources.add(resourceObj);
                    processedCount++;
                    
                    if (log.isDebugEnabled()) {
                        String resourceName = child.has("name") ? child.getString("name") : "unknown";
                        String resourceType = child.has("type") ? child.getString("type") : "unknown";
                        log.debug("Processed registry resource: " + resourceName + " (type: " + resourceType + ")");
                    }
                    
                } catch (Exception e) {
                    errorCount++;
                    log.warn("Error processing registry resource at index " + i, e);
                    
                    // Add basic error entry
                    JsonObject errorResourceObj = new JsonObject();
                    errorResourceObj.addProperty("name", "ERROR_RESOURCE_" + i);
                    errorResourceObj.addProperty("type", "error");
                    errorResourceObj.addProperty("error", e.getMessage());
                    registryResources.add(errorResourceObj);
                }
            }
            
            log.info("Registry resources collection completed. Processed: " + processedCount + 
                ", Errors: " + errorCount + ", Total registry resources collected: " + registryResources.size());
            
            // Log the response data for debugging
            if (log.isDebugEnabled()) {
                log.debug("Registry resources response data: " + registryResources.toString());
            } else {
                log.info("Registry resources response summary - Count: " + registryResources.size() + 
                    ", Root directory: " + regRoot);
            }
                
        } catch (Exception e) {
            log.error("Error collecting Registry Resources from SynapseConfiguration", e);
        }
        return registryResources;
    }
    
    /**
     * Collects Listener information (HTTP/HTTPS ports)
     */
    private static JsonArray collectListeners() {
        JsonArray listeners = new JsonArray();
        try {
            // HTTP Listener
            JsonObject httpListener = new JsonObject();
            httpListener.addProperty("protocol", "http");
            httpListener.addProperty("port", ConfigurationLoader.getInternalInboundHttpPort());
            httpListener.addProperty("host", "0.0.0.0");
            listeners.add(httpListener);
            
            // HTTPS Listener
            JsonObject httpsListener = new JsonObject();
            httpsListener.addProperty("protocol", "https");
            httpsListener.addProperty("port", ConfigurationLoader.getInternalInboundHttpsPort());
            httpsListener.addProperty("host", "0.0.0.0");
            listeners.add(httpsListener);
        } catch (Exception e) {
            log.error("Error collecting Listeners", e);
        }
        return listeners;
    }
    
}
