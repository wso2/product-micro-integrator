# ICP Agent — Implementation Review

**Branch:** `icp2-mi-agent`

---

## Overview

The **ICP (Integration Control Plane) Agent** is a new subsystem added to WSO2 Micro Integrator that allows a central control plane to monitor, manage, and control MI runtime instances. It replaces/supplements the older dashboard heartbeat mechanism with a richer, JWT-secured, delta-optimised protocol.

---

## 1. Architecture: The ICP Agent

### 1.1 Why It's Needed

The legacy dashboard communicated with MI via a minimal heartbeat (`{ product, groupId, nodeId, interval, mgtApiUrl }`). The ICP requires:
- A **full inventory of all running artifacts** (15 types: APIs, Proxies, Sequences, Tasks, CApps, Data Services, etc.)
- **Runtime control** — remotely enable/disable artifacts, toggle tracing/statistics
- An **efficient delta protocol** to avoid sending large payloads on every heartbeat
- **HMAC-JWT security** so only trusted ICP instances can invoke control APIs

### 1.2 Dual Heartbeat Strategy (`ICPHeartBeatComponent.java`)

| Mode | What is Sent | When |
|---|---|---|
| **Delta heartbeat** | `{ runtime, runtimeHash, timestamp }` | Every interval (default: 10 s) |
| **Full heartbeat** | Complete artifact inventory for all 15 types | When ICP responds with `fullHeartbeatRequired: true` |

The hash is computed over the full payload (excluding dynamic fields like memory). A stable runtime therefore sends only tiny delta packets each interval — the full payload is only requested when ICP detects a hash change.

**Backward compatibility** is preserved in `HeartBeatComponent.java` — it checks whether ICP is configured and delegates accordingly, or falls back to the legacy dashboard flow.

---

## 2. Components and How Each Works

### 2.1 Management API Layer

All ICP endpoints are registered under `/icp/*` via `ICPInternalApi.java` and are protected by `ICPJWTSecurityHandler`.

| File | Endpoint | How It Works |
|---|---|---|
| `ICPInternalApi.java` | `/icp/*` | Registers all ICP sub-resources as an internal API. Only activated when `-DenableICPApi=true` is passed at startup. |
| `ICPArtifactResource.java` | `GET /icp/artifacts` | Accepts `?type=<artifactType>&name=<name>`. Looks up the artifact in `SynapseConfiguration` and serialises it to JSON using the same serialisers used by the public Management API. |
| `ICPStatusResource.java` | `POST /icp/artifacts/status` | Reads `{ name, type, status }` from the request body and delegates to `ArtifactStatusManager` to start/stop/activate/deactivate the artifact at runtime. |
| `ICPTracingResource.java` | `POST /icp/artifacts/tracing` | Reads `{ name, type }` and `enable`/`disable` flag; delegates to `ArtifactTracingManager` to toggle Synapse tracing for the artifact. |
| `ICPStatisticsResource.java` | `POST /icp/artifacts/statistics` | Reads `{ name, type }` and `enable`/`disable` flag; delegates to `ArtifactStatisticsManager` to toggle statistics collection. |
| `ICPGetParamsResource.java` | `GET /icp/artifacts/parameters` | Accepts `?type=<type>&name=<name>`. Returns key-value parameter maps for inbound-endpoints, message processors, and datasources by reading the runtime configuration objects. |
| `ICPGetLocalEntryValueResource.java` | `GET /icp/artifacts/local-entry` | Accepts `?name=<name>`. For `inline` local entries returns the stored value; for `url_src` entries it fetches the remote URL and returns the content. |
| `WsdlResource.java` | `GET /icp/artifacts/wsdl` | Accepts `?type=<proxy\|dataservice>&name=<name>`. Fetches the WSDL document for proxy services or data services. |

### 2.2 Manager Utility Classes

These classes centralise the runtime control logic that the ICP resource classes delegate to. They were extracted from existing resource classes to prevent duplication and are now further refactored using `ArtifactOperationHelper`.

#### `ArtifactStatusManager.java` — Start/Stop/Activate/Deactivate

Handles five artifact types, each exposed as a static method:

| Method | Artifact | Operations |
|---|---|---|
| `changeProxyServiceStatus` | Proxy Service | `active` → `proxyService.start(config)`, `inactive` → `proxyService.stop(config)`. Respects pinned-server list before acting. |
| `changeEndpointStatus` | Endpoint | `active` → `ep.getContext().switchOn()`, `inactive` → `ep.getContext().switchOff()` |
| `changeMessageProcessorStatus` | Message Processor | `active` → `mp.activate()`, `inactive` → `mp.deactivate()` |
| `changeInboundEndpointStatus` | Inbound Endpoint | `active` → `ie.activate()`, `inactive` → `ie.deactivate()`. Returns the `DynamicControlOperationResult` message on failure. |
| `changeTaskStatus` | Task (StartUpController) | `active` → `activate()`, `inactive` → `deactivate()`, `trigger` → `trigger()` |

All methods use `ArtifactOperationHelper.handleStatusOperation()` to perform the null-check and build the audit `info` object before invoking the lambda that performs the actual state change.

#### `ArtifactTracingManager.java` — Toggle Tracing

Handles five artifact types: Proxy, Endpoint, Inbound Endpoint, API, Sequence.

Each method reads the artifact name from the JSON payload, looks it up in `SynapseConfiguration`, then calls `Utils.handleTracing(...)` which sets the `AspectConfiguration.setTracingState()` flag. Most artifact types are handled generically via `ArtifactOperationHelper.handleAspectOperation()`. Endpoints require an additional `getDefinition() != null` check and are kept explicit.

#### `ArtifactStatisticsManager.java` — Toggle Statistics Collection

Handles six artifact types: Proxy, Endpoint, Inbound Endpoint, API, Sequence, Template.

Same pattern as tracing — reads name from payload, looks up artifact, calls `Utils.handleStatistics(...)` which sets `AspectConfiguration.setStatisticsEnable()`. Templates additionally require a `type` field and only sequence templates are supported; a missing template name now returns a proper 400 error (NPE bug fixed — see §4.2 below).

### 2.3 Generic Helper — `ArtifactOperationHelper.java` *(new)*

A new utility class that captures the repeated pattern shared across all three manager classes:

1. Look up artifact by name
2. Return a 4xx error when the artifact is `null`
3. Build a one-entry `info` JSONObject
4. Delegate to the operation-specific handler

**Two generic methods:**

```java
handleAspectOperation(artifact, name, notFoundMsg, infoKey,
    performedBy, auditLogType, artifactType,
    getConfig, axis2MC, operation)
```
Used by `ArtifactStatisticsManager` and `ArtifactTracingManager` for all artifact types except Endpoint (which needs an extra null-check on `getDefinition()`).

```java
handleStatusOperation(artifact, notFoundMsg, infoKey, name,
    axis2MC, operation)
```
Used by `ArtifactStatusManager` for all five artifact types. The `operation` BiFunction receives the resolved artifact and pre-built info object, and performs the actual state change and audit logging.

### 2.4 Security Layer

#### `ICPJWTSecurityHandler.java` — Inbound JWT Validation
Validates HMAC-SHA256 JWT tokens on all ICP API calls:
1. Extracts the `Authorization: Bearer <token>` header
2. Base64-decodes the header and payload sections
3. Recomputes the HMAC-SHA256 signature using the configured shared secret
4. Compares with the provided signature
5. Checks the `exp` claim to reject expired tokens

#### `HMACJWTTokenGenerator.java` — Outbound JWT Generation
Generates JWT tokens that MI attaches to heartbeat requests sent to ICP:
1. Uses the Nimbus JOSE+JWT library to build a signed JWT
2. Claims include `iss`, `sub`, `iat`, `exp` (configurable TTL)
3. Signs with `JWSAlgorithm.HS256` using the shared HMAC secret
4. Token is cached and reused until expiry to avoid re-signing every heartbeat

### 2.5 Heartbeat Component (`ICPHeartBeatComponent.java`)

The core of the ICP agent. Runs as a scheduled background thread:

1. **Runtime ID persistence** — Reads the pre-generated UUID from `.icp_runtime_id` (written at server startup by `ICPStartupUtils`). Throws an `IOException` if the file is missing, rather than generating the ID on the fly.

2. **Artifact collection** — Collects all 15 artifact types from `SynapseConfiguration` on demand:
   - REST APIs, Proxy Services, Endpoints, Inbound Endpoints, Sequences, Tasks, Templates, Message Stores, Message Processors, Local Entries, Data Services, Carbon Applications, Data Sources, Connectors, Registry Resources.
   - Each artifact includes its `cappName` (owning Carbon Application) via a pre-built lookup map (see §4 below).

3. **Delta protocol** — Computes an MD5 hash of the full artifact payload. Sends only `{ runtimeHash, timestamp }` each interval. ICP responds with `fullHeartbeatRequired: true` when it detects a hash mismatch or on the first contact, triggering a full payload send.

4. **JWT token attachment** — Calls `HMACJWTTokenGenerator` to get a (cached) JWT and attaches it as `Authorization: Bearer` header on all outbound requests to ICP.

### 2.6 Startup Initialization (`ICPStartupUtils.java` + `Main.java`)

The runtime ID is now generated **eagerly at server startup** rather than lazily on the first heartbeat:

- `ICPStartupUtils.isICPConfigured()` — reads `deployment.toml` via `ConfigParser` and returns `true` when `icp_config.enabled = true`.
- `ICPStartupUtils.initRuntimeId()` — generates the runtime ID (`<configuredPrefix>-<UUID>` or plain UUID) and writes it to `.icp_runtime_id` if the file does not already exist.
- `Main.java` calls `ICPStartupUtils.initRuntimeId()` before extensions are invoked, so the file is guaranteed to exist by the time the heartbeat thread starts.
- `ICPHeartBeatComponent.getRuntimeId()` is simplified to only **read** the file; it throws an `IOException` if the file is absent, making the missing-file case an explicit startup failure rather than a silent recovery.

### 2.7 Configuration

| Change | Purpose |
|---|---|
| `deployment-icp-sample.toml` | Sample config file with `icp_config.enabled`, `icp_config.url`, `icp_config.jwt_hmac_secret`, `icp_config.heartbeat_interval` |
| `internal-apis.xml.j2` | Jinja2 template — conditionally registers `ICPApi` only when `icp_config.enabled == true` |
| `internal-apis.xml` | Static version with `ICPApi` registered with `ICPJWTSecurityHandler` |
| `-DenableICPApi=true` in startup scripts | Activates the ICP internal API at boot, independent of `enableManagementApi` |
| `pom.xml` (initializer) | Adds Nimbus JOSE+JWT dependency for standards-compliant JWT token generation |

---

## 3. Why `-DenableICPApi=true` is Required Despite Jinja Template Configuration

The ICP Internal API requires **two separate conditions** to be enabled, implementing a two-tier control mechanism:

### 3.1 First Tier: Configuration File (Jinja Template)

The Jinja template in `internal-apis.xml.j2` conditionally includes the ICP API definition based on the `deployment.toml` configuration:

```jinja
{% if icp_config is defined and icp_config.enabled == true %}
<api name="ICPApi" protocol="https http" class="org.wso2.micro.integrator.icp.apis.ICPInternalApi">
    ...
</api>
{% endif %}
```

This determines whether the API configuration appears in the generated `internal-apis.xml` file. When `icp_config.enabled = true` in `deployment.toml`, the config tool will generate the API entry in the XML file.

### 3.2 Second Tier: Runtime System Property Check

When the ICP API entry is present in `internal-apis.xml`, `ConfigurationLoader.java` performs an additional runtime check during the loading phase:

```java
// For each <api> element found in internal-apis.xml:
if (!Boolean.parseBoolean(
        System.getProperty(Constants.PREFIX_TO_ENABLE_INTERNAL_APIS + name))) {
    continue;  // Skip loading this API
}
```

Where:
- `Constants.PREFIX_TO_ENABLE_INTERNAL_APIS = "enable"`
- API name from the XML = `"ICPApi"`
- Therefore, it checks for system property: `enableICPApi`

**Without `-DenableICPApi=true` in the startup script, this check returns `false`, and the API is skipped during the loading phase**, even though it exists in the configuration file.

**Critical: This check only happens if the XML entry exists.** If the Jinja template didn't generate the `<api name="ICPApi">` element (because `icp_config.enabled != true`), the ConfigurationLoader never encounters it, so the system property is irrelevant.

### 3.3 Why This Two-Tier Design?

This design pattern provides:

1. **Configuration-time control** — The Jinja template determines what APIs *can* be enabled based on deployment configuration
2. **Runtime toggle** — The system property acts as a runtime switch to enable/disable APIs without modifying configuration files
3. **Startup optimization** — Allows selectively enabling internal APIs for specific deployments (e.g., enable Management API but not ICP API)
4. **Consistency across internal APIs** — All internal APIs (ManagementApi, ReadinessProbe, LivenessProbe, ICPApi) use the same loading mechanism

**Both conditions must be satisfied:**

1. ✅ Set `icp_config.enabled = true` in `deployment.toml` → Generates the `<api name="ICPApi">` entry in `internal-apis.xml`
2. ✅ Include `-DenableICPApi=true` in the startup script → Tells ConfigurationLoader to actually load the API

**Failure scenarios:**

| Scenario | `icp_config.enabled` | `-DenableICPApi` | Result |
|----------|---------------------|------------------|--------|
| ❌ Missing XML entry | `false` or unset | `true` | **Not loaded** — No XML entry to iterate over |
| ❌ Missing system property | `true` | `false` or unset | **Not loaded** — XML entry exists but loader skips it |
| ✅ Both conditions met | `true` | `true` | **Loaded** — API is active |

The Jinja template controls *what can be loaded* (structural availability), while the system property controls *what actually gets loaded* (runtime activation). Both gates must be open.

---

## 4. Sample `deployment.toml` Configuration

Add the following to `<MI_HOME>/conf/deployment.toml` to enable ICP:

```toml
# ========================================
# WSO2 Micro Integrator - ICP Configuration Sample
# ========================================

# ----------------------------------------
# ICP Configuration
# ----------------------------------------
[icp_config]
enabled     = true
runtime     = "mi-test"
environment = "prod"
project     = "sample-project"
integration = "sample-mi-integration"

# Off SSL verification for local setup
ssl_verify = false

# ICP URL can be configured when changed defaults to "https://localhost:9445"
# icp_url = "https://localhost:9445"

# Heartbeat interval in seconds (default: 10)
# heartbeat_interval = 10

# JWT Configurations
# ------------------
# JWT HMAC secret — must be at least 32 characters for HS256
# jwt_hmac_secret = "<REPLACE-WITH-A-SECURE-SECRET-AT-LEAST-32-CHARS>"
# jwt_issuer         = "icp-runtime-jwt-issuer"
# jwt_audience       = "icp-server"
# jwt_scope          = "runtime_agent"
# jwt_expiry_seconds = 3600

# ----------------------------------------------------------
# Legacy Dashboard Configuration (Optional)
# This can be enabled for backward compatibility or fallback
# ----------------------------------------------------------
# [dashboard_config]
# dashboard_url       = "https://dashboard-host:9743/dashboard/api/"
# group_id            = "default"
# node_id             = "node1"
# heartbeat_interval  = 5
# management_hostname = "mi-host.example.com"
# management_port     = 9154
```

> **Note:** The startup script must also pass `-DenableICPApi=true` (already added to `micro-integrator.sh` and `micro-integrator.bat`). Both this `deployment.toml` entry and the system property are required — see §3 above.

---
