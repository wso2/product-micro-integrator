package org.wso2.micro.integrator.initializer.dashboard;

public class Constants {

    private Constants() {

    }

    public static final String PRODUCT_MI = "mi";
    public static final String NODE_ID_SYSTEM_PROPERTY = "node.id";
    public static final String DASHBOARD_CONFIG_URL = "dashboard_config.dashboard_url";
    public static final String DASHBOARD_CONFIG_GROUP_ID = "dashboard_config.group_id";
    public static final String DASHBOARD_CONFIG_NODE_ID = "dashboard_config.node_id";
    public static final String DASHBOARD_CONFIG_HEARTBEAT_INTERVAL = "dashboard_config.heartbeat_interval";
    public static final String DASHBOARD_CONFIG_MANAGEMENT_HOSTNAME = "dashboard_config.management_hostname";
    public static final String DASHBOARD_CONFIG_MANAGEMENT_PORT = "dashboard_config.management_port";

    // New ICP Configuration
    public static final String ICP_CONFIG_URL = "icp_config.icp_url";
    public static final String ICP_CONFIG_RUNTIME = "icp_config.runtime";
    public static final String ICP_CONFIG_ENVIRONMENT = "icp_config.environment";
    public static final String ICP_CONFIG_PROJECT = "icp_config.project";
    public static final String ICP_CONFIG_COMPONENT = "icp_config.component";
    public static final String ICP_CONFIG_ENABLED = "icp_config.enabled";
    
    // JWT Configuration
    public static final String ICP_JWT_KEYSTORE_PATH = "icp_config.jwt_keystore_path";
    public static final String ICP_JWT_KEYSTORE_PASSWORD = "icp_config.jwt_keystore_password";
    public static final String ICP_JWT_KEY_ALIAS = "icp_config.jwt_key_alias";
    public static final String ICP_JWT_KEY_PASSWORD = "icp_config.jwt_key_password";
    public static final String ICP_JWT_ISSUER = "icp_config.jwt_issuer";
    public static final String ICP_JWT_AUDIENCE = "icp_config.jwt_audience";
    public static final String ICP_JWT_EXPIRY_SECONDS = "icp_config.jwt_expiry_seconds";
    
    // Default ICP Configuration
    public static final String DEFAULT_ENVIRONMENT = "production";
    public static final String DEFAULT_PROJECT = "default";
    public static final String DEFAULT_COMPONENT = "default";
    public static final String DEFAULT_JWT_ISSUER = "icp-runtime-jwt-issuer";
    public static final String DEFAULT_JWT_AUDIENCE = "icp-server";
    public static final long DEFAULT_JWT_EXPIRY_SECONDS = 3600;
    public static final String RUNTIME_TYPE_MI = "MI";
    public static final String RUNTIME_STATUS_RUNNING = "RUNNING";

    public static final String DEFAULT_GROUP_ID = "default";
    public static final long DEFAULT_HEARTBEAT_INTERVAL = 5;

    public static final String HEADER_VALUE_APPLICATION_JSON = "application/json";
    public static final String FORWARD_SLASH = "/";
    public static final String COLON = ":";
    public static final String HTTPS_PREFIX = "https://";
    public static final String MANAGEMENT = "management";
    
    // ICP Endpoints
    public static final String ICP_HEARTBEAT_ENDPOINT = "/icp/heartbeat";
    public static final String ICP_DELTA_HEARTBEAT_ENDPOINT = "/icp/deltaHeartbeat";
}
