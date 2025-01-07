/*
 *  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
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
 *
 */
package org.wso2.micro.integrator.dataservices.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the global constants defined by data services.
 */
public final class DBConstants {

    private DBConstants() {
        throw new AssertionError();
    }

    public static final String SWAGGER_RESOURCE_PATH = "org.wso2.ws.dataservice.dataservice.swagger.path";
    public static final String WSO2_DS_NAMESPACE = "http://ws.wso2.org/dataservice";
    public static final String DATA_SERVICE_OBJECT = "org.wso2.ws.dataservice.dataservice.obj";
    public static final String DB_SERVICE_DEPLOYER = "org.wso2.ws.dataservice.dataservice.deployer";
    public static final String DB_SERVICE_TYPE = "data_service";
    public static final String DB_SERVICE_REPO = "local_org.wso2.ws.dataservice.db_service_repo";
    public static final String DB_SERVICE_REPO_VALUE = "dataservices";
    public static final String DB_SERVICE_EXTENSION = "local_org.wso2.ws.dataservice.db_service_extension";
    public static final String DB_SERVICE_EXTENSION_VALUE = "dbs";
    public static final String BATCH_OPERATON_NAME_SUFFIX = "_batch_req";
    public static final String REQUEST_BOX_ELEMENT = "request_box";
    public static final String DEFAULT_CHAR_SET_TYPE = "UTF-8";
    public static final String DATA_SERVICES_JMX_DOMAIN = "org.wso2.carbon.dataservices.jmx";
    public static final String DEFAULT_CONFIG_ID = "default";
    public static final String AXIS2_SERVICE_TYPE = "serviceType";
    public static final String AXIS2_SERVICE_GROUP = "serviceGroup";
    public static final String AXIS2_SERVICE = "service";
    public static final String DBS_SERVICES_XML_SUFFIX = "_services.xml";
    public static final String DS_FAULT_NAME = "org.wso2.carbon.dataservices.core.DataServiceFault";
    public static final String DS_FAULT_ELEMENT = "DataServiceFault";
    public static final String MSG_CONTEXT_USERNAME_PROPERTY = "username";
    public static final String DBS_FILE_EXTENSION = "dbs";
    public static final String TARGET_NAMESPACE = "targetNamespace";
    public static final String COMPLEX_TYPE = "complexType";
    public static final String LABEL_SUCESSFULL = "successful";
    public static final String DEFAULT_XSD_PREFIX = "xs";
    public static final String DEFAULT_XSD_TYPE = DEFAULT_XSD_PREFIX + ":string";
    public static final String EMPTY_QUERY_ID = "__dataservices_empty_query__";
    public static final String EMPTY_END_BOXCAR_QUERY_ID = "__dataservices_empty_end_boxcar_query__";
    public static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";
    public static final String DEFAULT_RDF_PREFIX = "rdf";
    public static final String RDF_ABOUT = "about";
    public static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String XSI_PREFIX = "xsi";
    public static final String NIL = "nil";
    public static final String CONTRACT_FIRST_QUERY_SUFFIX = "Query";
    public static final String CONTRACT_FIRST_DUMMY_SQL = "dummy";
    public static final String GOV_REGISTRY_PATH_PREFIX = "gov:";
    public static final String CONF_REGISTRY_PATH_PREFIX = "conf:";
    public static final int DEFAULT_DBCP_MIN_POOL_SIZE = 1;
    public static final int DEFAULT_DBCP_MAX_POOL_SIZE = 5;
    public static final String STANDARD_TRANSACTION_MANAGER_JNDI_NAME = "java:comp/TransactionManager";
    public static final String STANDARD_USER_TRANSACTION_JNDI_NAME = "java:comp/UserTransaction";
    public static final String REQUEST_STATUS_SUCCESSFUL_MESSAGE = "SUCCESSFUL";
    public static final String REQUEST_STATUS_WRAPPER_ELEMENT = "REQUEST_STATUS";
    public static final String DATA_SERVICE_NAME = "dataServiceName";
    /* Initial index of UDT attributes */
    public static final int UDT_ATTRIBUTE_INITIAL_INDEX = 0;
    public static final String SECUREVAULT_NAMESPACE = "http://org.wso2.securevault/configuration";
    public static final String DATA_SERVICE_RESPONSE_WRAPPER_ELEMENT = "DATA_SERVICE_RESPONSE";
    public static final String DATA_SERVICE_REQUEST_BOX_RESPONSE_WRAPPER_ELEMENT = "DATA_SERVICE_REQUEST_BOX_RESPONSE";
    public static final String SECURITY_MODULE_NAME = "rampart";
    public static final String TENANT_IN_ONLY_MESSAGE = "TENANT_IN_ONLY_MESSAGE";
    public static final String DISABLE_CURRENT_PARAMS_IN_LOG = "dss.disable.current.params";
        
    /**
     * Codes to be used as fault codes.
     */
    public static final class FaultCodes {

        private FaultCodes() {
            throw new AssertionError();
        }

        public static String DATABASE_ERROR = "DATABASE_ERROR";
        public static String CONNECTION_UNAVAILABLE_ERROR = "CONNECTION_UNAVAILABLE_ERROR";
        public static String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static String INCOMPATIBLE_PARAMETERS_ERROR = "INCOMPATIBLE_PARAMETERS_ERROR";
        public static String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    }

    /**
     * Parameters in the fault message
     */
    public static final class FaultParams {

        private FaultParams() {
            throw new AssertionError();
        }

        public static String CURRENT_PARAMS= "current_params";
        public static String CURRENT_REQUEST_NAME = "current_request_name";
        public static String NESTED_EXCEPTION = "nested_exception";
        public static String SOURCE_DATA_SERVICE = "source_data_service";
        public static String LOCATION = "location";
        public static String DEFAULT_NAMESPACE = "default_namespace";
        public static String DESCRIPTION = "description";
        public static String DATA_SERVICE_NAME = "data_service_name";
        public static String DS_CODE = "ds_code";
    }

    /**
     * Autocommit values.
     */
    public enum AutoCommit {
        DEFAULT, AUTO_COMMIT_ON, AUTO_COMMIT_OFF;
    }

    /**
     * Constants related to data types.
     */
    public static final class DataTypes {

        private DataTypes() {
            throw new AssertionError();
        }

        public static final String CHAR = "CHAR";
        public static final String LONG = "LONG";
        public static final String STRING = "STRING";
        public static final String VARCHAR = "VARCHAR";
        public static final String NVARCHAR = "NVARCHAR";
        public static final String TEXT = "TEXT";
        public static final String NUMERIC = "NUMERIC";
        public static final String DECIMAL = "DECIMAL";
        public static final String MONEY = "MONEY";
        public static final String SMALLMONEY = "SMALLMONEY";
        public static final String BIT = "BIT";
        public static final String ANYURI = "ANYURI";
        public static final String BOOLEAN = "BOOLEAN";
        public static final String TINYINT = "TINYINT";
        public static final String SMALLINT = "SMALLINT";
        public static final String INTEGER = "INTEGER";
        public static final String BIGINT = "BIGINT";
        public static final String REAL = "REAL";
        public static final String FLOAT = "FLOAT";
        public static final String DOUBLE = "DOUBLE";
        public static final String BINARY = "BINARY";
        public static final String BLOB = "BLOB";
        public static final String CLOB = "CLOB";
        public static final String VARBINARY = "VARBINARY";
        public static final String LONG_VARBINARY = "LONG VARBINARY";
        public static final String IMAGE = "IMAGE";
        public static final String DATE = "DATE";
        public static final String TIME = "TIME";
        public static final String TIMESTAMP = "TIMESTAMP";
        public static final String ORACLE_REF_CURSOR = "ORACLE_REF_CURSOR";
        public static final String STRUCT = "STRUCT";
        public static final String ARRAY = "ARRAY";
        public static final String QUERY_STRING = "QUERY_STRING";
        public static final String VARINT = "VARINT";
        public static final String UUID = "UUID";
        public static final String INETADDRESS = "INETADDRESS";
        public static final String SQLXML = "SQLXML";
    }

    /**
     * Constants to define swagger data types;
     */
    public static final class SwaggerDataTypes {

        private SwaggerDataTypes() {
            throw new AssertionError();
        }

        public static final String STRING = "string";
        public static final String NUMBER = "number";
        public static final String INTEGER = "integer";
        public static final String BOOLEAN = "boolean";
        public static final String ARRAY = "array";
        public static final String OBJECT = "object";

    }

    /**
     * Constants related Data service generator using data source.
     */
    public static final class DataServiceGenerator {

        private DataServiceGenerator() {
            throw new AssertionError();
        }

        public static final String CONFIG_ID = "default";
        public static final String ROW_ELEMENT_NAME_SUFFIX = "Collection";
        public static final String SERVICE_NAME_SUFFIX = "_DataService";
        public static final String SINGLE_SERVICE_DESCRIPTION = "single service for whole database";
        public static final String MUTLIPLE_SERVICE_DESCRIPTION = "multiple services per each table";
        public static final String ACTIVE = "active";
        public static final String TABLE_NAME = "TABLE_NAME";
        public static final String TABLE_SCHEM = "TABLE_SCHEM";
        public static final String COLUMN_NAME = "COLUMN_NAME";
        public static final String QUERY_PARAM = "query-param";
        public static final String INSERT_ = "insert_";
        public static final String _QUERY = "_query";
        public static final String _OPERATION = "_operation";
        public static final String UPDATE_ = "update_";
        public static final String DELETE_ = "delete_";
        public static final String SELECT_WITH_KEY = "select_with_key_";
        public static final String SELECT_ALL = "select_all_";
        public static final String DATA_TYPE = "DATA_TYPE";
        public static final String IN = "IN";
        public static final String SCALAR = "SCALAR";
        public static final String COLUMN = "column";
        public static final String ELEMENT = "element";
    }

    public static final class XSDTypes {

        private XSDTypes() {
            throw new AssertionError();
        }


        public static final String STRING = "STRING";
        public static final String BOOLEAN = "BOOLEAN";
        public static final String DECIMAL = "DECIMAL";
        public static final String FLOAT = "FLOAT";
        public static final String LONG = "LONG";
        public static final String DOUBLE = "DOUBLE";
        public static final String INTEGER = "INTEGER";
        public static final String DATE = "DATE";
        public static final String DATETIME = "DATETIME";
        public static final String TIME = "TIME";
        public static final String GYEARMONTH = "GYEARMONTH";
        public static final String GYEAR = "GYEAR";
        public static final String GMONTHDAY = "GMONTHDAY";
        public static final String GDAY = "GDAY";
        public static final String GMONTH = "GMONTH";
        public static final String HEXBINARY = "HEXBINARY";
        public static final String BASE64BINARY = "BASE64BINARY";
        public static final String ANYURI = "ANYURI";
        public static final String QNAME = "QNAME";
        public static final String NOTATION = "NOTATION";
    }

    /**
     * Result types
     */
    public static final class ResultTypes {

        private ResultTypes() {
            throw new AssertionError();
        }

        public static final int RDF = 0x01;
        public static final int XML = 0x02;
        public static final int JSON = 0x03;

    }

    /**
     * Data categories
     */
    public static final class DataCategory {

        private DataCategory() {
            throw new AssertionError();
        }

        public static final int VALUE = 0x01;
        public static final int REFERENCE = 0x02;

    }


    /**
     * Constants related to data source types.
     */
    public static final class DataSourceTypes {

        private DataSourceTypes() {
            throw new AssertionError();
        }

        public static final String RDBMS = "RDBMS";
        public static final String RDF = "RDF";
        public static final String SPARQL = "SPARQL";
        public static final String CSV = "CSV";
        public static final String EXCEL = "EXCEL";
        public static final String JNDI = "JNDI";
        public static final String GDATA_SPREADSHEET = "GDATA_SPREADSHEET";
        public static final String CARBON = "CARBON_DATASOURCE";
        public static final String WEB = "WEB_CONFIG";
        public static final String CASSANDRA = "Cassandra";
        public static final String MONGODB = "MongoDB";
        public static final String CUSTOM = "CUSTOM";
        public static final String CUSTOM_TABULAR = "CUSTOM_TABULAR";
        public static final String CUSTOM_QUERY = "CUSTOM_QUERY";
    }

    /**
     * Constants related to RDBMS data source, DSS v2.x.
     */
    public static final class RDBMS_OLD {

        private RDBMS_OLD() {
            throw new AssertionError();
        }

        public static final String DRIVER = "org.wso2.ws.dataservice.driver";
        public static final String PROTOCOL = "org.wso2.ws.dataservice.protocol";
        public static final String USER = "org.wso2.ws.dataservice.user";
        public static final String PASSWORD = "org.wso2.ws.dataservice.password";
        public static final String QUERY_TIMEOUT = "org.wso2.ws.dataservice.query_timeout";
        public static final String AUTO_COMMIT = "org.wso2.ws.dataservice.autocommit";
        public static final String FETCH_DIRECTION = "org.wso2.ws.dataservice.fetch_direction";
        public static final String FETCH_SIZE = "org.wso2.ws.dataservice.fetch_size";
        public static final String MAX_FIELD_SIZE = "org.wso2.ws.dataservice.max_field_size";
        public static final String MAX_ROWS = "org.wso2.ws.dataservice.max_rows";
        public static final String FORCE_STORED_PROC = "org.wso2.ws.dataservice.force_stored_proc";
        public static final String FORCE_JDBC_BATCH_REQUESTS = "org.wso2.ws.dataservice.force_jdbc_batch_requests";

        public static final String TRANSACTION_ISOLATION = "org.wso2.ws.dataservice.transaction_isolation";
        public static final String INITIAL_SIZE = "org.wso2.ws.dataservice.initial_size";
        public static final String MAX_POOL_SIZE = "org.wso2.ws.dataservice.maxpoolsize";
        public static final String MAX_IDLE = "org.wso2.ws.dataservice.max_idle";
        public static final String MIN_POOL_SIZE = "org.wso2.ws.dataservice.minpoolsize";
        public static final String MAX_WAIT = "org.wso2.ws.dataservice.max_wait";
        public static final String VALIDATION_QUERY = "org.wso2.ws.dataservice.validation_query";
        public static final String TEST_ON_BORROW = "org.wso2.ws.dataservice.test_on_borrow";
        public static final String TEST_ON_RETURN = "org.wso2.ws.dataservice.test_on_return";
        public static final String TEST_WHILE_IDLE = "org.wso2.ws.dataservice.test_while_idle";
        public static final String TIME_BETWEEN_EVICTION_RUNS_MILLS = "org.wso2.ws.dataservice.time_between_eviction_runs_mills";
        public static final String NUM_TESTS_PER_EVICTION_RUN = "org.wso2.ws.dataservice.num_test_per_eviction_run";
        public static final String MIN_EVICTABLE_IDLE_TIME_MILLIS = "org.wso2.ws.dataservice.min_evictable_idle_time_millis";
        public static final String REMOVE_ABANDONED = "org.wso2.ws.dataservice.remove_abandoned";
        public static final String REMOVE_ABONDONED_TIMEOUT = "org.wso2.ws.dataservice.remove_abandoned_timeout";
        public static final String LOG_ABANDONED = "org.wso2.ws.dataservice.log_abandoned";

        public static final String XA_DATASOURCE_CLASS = "org.wso2.ws.dataservice.xa_datasource_class";
        public static final String XA_DATASOURCE_PROPS = "org.wso2.ws.dataservice.xa_datasource_properties";
    }
    
    /**
     * Constants related to RDBMS data source, DSS v3.0+.
     */
    public static final class RDBMS {

        private RDBMS() {
            throw new AssertionError();
        }
        
        public static final String DEFAULT_AUTOCOMMIT = "defaultAutoCommit";
        public static final String DEFAULT_READONLY = "defaultReadOnly";
        public static final String DEFAULT_TX_ISOLATION = "defaultTransactionIsolation";
        public static final String DEFAULT_CATALOG = "defaultCatalog";
        public static final String DRIVER_CLASSNAME = "driverClassName";
        public static final String URL = "url";
        public static final String USERNAME = "username";
        public static final String PASSWORD = "password";
        public static final String MAX_ACTIVE = "maxActive";
        public static final String MAX_IDLE = "maxIdle";
        public static final String MIN_IDLE = "minIdle";
        public static final String INITIAL_SIZE = "initialSize";
        public static final String MAX_WAIT = "maxWait";
        public static final String TEST_ON_BORROW = "testOnBorrow";
        public static final String TEST_ON_RETURN = "testOnReturn";
        public static final String TEST_WHILE_IDLE = "testWhileIdle";
        public static final String VALIDATION_QUERY = "validationQuery";
        public static final String VALIDATOR_CLASSNAME = "validatorClassName";
        public static final String TIME_BETWEEN_EVICTION_RUNS_MILLIS = "timeBetweenEvictionRunsMillis";
        public static final String NUM_TESTS_PER_EVICTION_RUN = "numTestsPerEvictionRun";
        public static final String MIN_EVICTABLE_IDLE_TIME_MILLIS = "minEvictableIdleTimeMillis";
        public static final String REMOVE_ABANDONED = "removeAbandoned";
        public static final String REMOVE_ABANDONED_TIMEOUT = "removeAbandonedTimeout";
        public static final String LOG_ABANDONED = "logAbandoned";
        public static final String CONNECTION_PROPERTIES = "connectionProperties";
        public static final String INIT_SQL = "initSQL";
        public static final String JDBC_INTERCEPTORS = "jdbcInterceptors";
        public static final String VALIDATION_INTERVAL = "validationInterval";
        public static final String JMX_ENABLED = "jmxEnabled";
        public static final String FAIR_QUEUE = "fairQueue";
        public static final String ABANDON_WHEN_PERCENTAGE_FULL = "abandonWhenPercentageFull";
        public static final String MAX_AGE = "maxAge";
        public static final String USE_EQUALS = "useEquals";
        public static final String SUSPECT_TIMEOUT = "suspectTimeout";
        public static final String VALIDATION_QUERY_TIMEOUT = "validationQueryTimeout";
        public static final String ALTERNATE_USERNAME_ALLOWED = "alternateUsernameAllowed";
        public static final String DATASOURCE_CLASSNAME = "dataSourceClassName";
        public static final String DATASOURCE_PROPS = "dataSourceProps";
        public static final String FORCE_STORED_PROC = "forceStoredProc";
        public static final String FORCE_JDBC_BATCH_REQUESTS = "forceJDBCBatchRequests";
        public static final String QUERY_TIMEOUT = "queryTimeout";
        public static final String AUTO_COMMIT = "autoCommit";
        public static final String FETCH_DIRECTION = "fetchDirection";
        public static final String FETCH_SIZE = "fetchSize";
        public static final String MAX_FIELD_SIZE = "maxFieldSize";
        public static final String MAX_ROWS = "maxRows";        
        public static final String DYNAMIC_USER_AUTH_CLASS = "dynamicUserAuthClass";
        public static final String DYNAMIC_USER_AUTH_MAPPING = "dynamicUserAuthMapping";
        public static final String USERNAME_WILDCARD = "*";
        public static final String DSS_TIMERZONE = "dss.timezone";
        public static final String DSS_LEGACY_TIMEZONE_MODE = "dss.legacy.timezone.mode";
        public static final String TIMEZONE_UTC = "UTC";
    }

    public static final Map<String, String> RDBMSv2ToV3Map = new HashMap<String, String>();
    
    static {
    	RDBMSv2ToV3Map.put(RDBMS_OLD.DRIVER, RDBMS.DRIVER_CLASSNAME);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.PROTOCOL, RDBMS.URL);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.USER, RDBMS.USERNAME);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.PASSWORD, RDBMS.PASSWORD);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.QUERY_TIMEOUT, RDBMS.QUERY_TIMEOUT);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.AUTO_COMMIT, RDBMS.AUTO_COMMIT);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.FETCH_DIRECTION, RDBMS.FETCH_DIRECTION);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.FETCH_SIZE, RDBMS.FETCH_SIZE);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.MAX_FIELD_SIZE, RDBMS.MAX_FIELD_SIZE);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.MAX_ROWS, RDBMS.MAX_ROWS);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.FORCE_JDBC_BATCH_REQUESTS, RDBMS.FORCE_JDBC_BATCH_REQUESTS);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.FORCE_STORED_PROC, RDBMS.FORCE_STORED_PROC);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.TRANSACTION_ISOLATION, RDBMS.DEFAULT_TX_ISOLATION);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.INITIAL_SIZE, RDBMS.INITIAL_SIZE);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.MAX_POOL_SIZE, RDBMS.MAX_ACTIVE);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.MAX_IDLE, RDBMS.MAX_IDLE);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.MIN_POOL_SIZE, RDBMS.MIN_IDLE);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.MAX_WAIT, RDBMS.MAX_WAIT);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.VALIDATION_QUERY, RDBMS.VALIDATION_QUERY);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.TEST_ON_BORROW, RDBMS.TEST_ON_BORROW);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.TEST_ON_RETURN, RDBMS.TEST_ON_RETURN);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.TEST_WHILE_IDLE, RDBMS.TEST_WHILE_IDLE);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.TIME_BETWEEN_EVICTION_RUNS_MILLS, RDBMS.TIME_BETWEEN_EVICTION_RUNS_MILLIS);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.NUM_TESTS_PER_EVICTION_RUN, null);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.MIN_EVICTABLE_IDLE_TIME_MILLIS, RDBMS.MIN_EVICTABLE_IDLE_TIME_MILLIS);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.REMOVE_ABANDONED, RDBMS.REMOVE_ABANDONED);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.REMOVE_ABONDONED_TIMEOUT, RDBMS.REMOVE_ABANDONED_TIMEOUT);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.LOG_ABANDONED, RDBMS.LOG_ABANDONED);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.XA_DATASOURCE_CLASS, RDBMS.DATASOURCE_CLASSNAME);
    	RDBMSv2ToV3Map.put(RDBMS_OLD.XA_DATASOURCE_PROPS, RDBMS.DATASOURCE_PROPS);
    }

    /**
     * Constants related to Database configuration properties
     */
    public static final class DBCPConfig {

        private DBCPConfig() {
            throw new AssertionError();
        }

        public static final String DRIVER_CLASS_NAME = "driverClassName";
        public static final String URL = "url";
        public static final String USER = "user";
        public static final String DEFAULT_TRANSACTION_ISOLATION = "defaultTransactionIsolation";
        public static final String PASSWORD = "password";
        public static final String INITIAL_SIZE = "initialSize";
        public static final String MAX_ACTIVE = "maxActive";
        public static final String MIN_IDLE = "minIdle";
        public static final String MAX_IDLE = "maxIdle";
        public static final String MAX_WAIT = "maxWait";
        public static final String VALIDATION_QUERY = "validationQuery";
        public static final String TEST_ON_BORROW = "testOnBorrow";
        public static final String TEST_ON_RETURN = "testOnReturn";
        public static final String TEST_WHILE_IDLE = "testWhileIdle";
        public static final String TIME_BETWEEN_EVICTION_RUNS_MILLIS = "timeBetweenEvictionRunsMillis";
        public static final String NUM_TESTS_PER_EVICTION_RUN = "numTestsPerEvictionRun";
        public static final String MIN_EVICTABLE_IDLE_TIME_MILLS = "minEvictableIdleTimeMillis";
        public static final String REMOVE_ABANDONED = "removeAbandoned";
        public static final String REMOVE_ABANDONED_TIMEOUT = "removeAbandonedTimeout";
        public static final String LOG_ABANDONED = "logAbandoned";

        public static final String TRANSACTION_UNKNOWN = "TRANSACTION_UNKNOWN";
    }

    
    /**
     * Constants related to JNDI data source.
     */
    public static final class JNDI {

        private JNDI() {
            throw new AssertionError();
        }

        public static final String INITIAL_CONTEXT_FACTORY = "jndi_context_class";
        public static final String PROVIDER_URL = "jndi_provider_url";
        public static final String RESOURCE_NAME = "jndi_resource_name";
        public static final String USERNAME = "jndi_username";
        public static final String PASSWORD = "jndi_password";
        public static final String DATASOURCE = "jndi_datasource";
    }

    /**
     * Constants related to google spreadsheet data source.
     */
    public static final class GSpread {

        private GSpread() {
            throw new AssertionError();
        }

        public static final String USERNAME = "gspread_username";
        public static final String PASSWORD = "gspread_password";
        public static final String VISIBILITY = "gspread_visibility";
        public static final String DATASOURCE = "gspread_datasource";
        public static final String CLIENT_ID = "gspread_client_id";
        public static final String CLIENT_SECRET = "gspread_client_secret";
        public static final String ACCESS_TOKEN = "gspread_access_token";
        public static final String REDIRECT_URIS = "gspread_redirect_uris";
        public static final String REFRESH_TOKEN = "gspread_refresh_token";
        public static final String WORKSHEET_NUMBER = "worksheetnumber";
        public static final String STARTING_ROW = "startingrow";
        public static final String MAX_ROW_COUNT = "maxrowcount";
        public static final String HAS_HEADER = "hasheader";
        public static final String SHEET_NAME = "sheetName";
        public static final String HEADER_ROW = "headerrow";
    }

    /**
     * Constants related to CSV data source.
     */
    public static final class CSV {

        private CSV() {
            throw new AssertionError();
        }

        public static final String COLUMN_SEPARATOR = "csv_columnseperator";
        public static final String STARTING_ROW = "csv_startingrow";
        public static final String MAX_ROW_COUNT = "csv_maxrowcount";
        public static final String HAS_HEADER = "csv_hasheader";
        public static final String DATASOURCE = "csv_datasource";
        public static final String COLUMN_SEPERATOR = "columnseperator";
        public static final String HEADER_ROW = "csv_headerrow";
    }
    
    /**
     * Constants related to Cassandra data source.
     */
    public static final class Cassandra {

        private Cassandra() {
            throw new AssertionError();
        }

        /* string (multiple values with comma separated) */
        public static final String CASSANDRA_SERVERS = "cassandraServers";
        /* string */
        public static final String KEYSPACE = "keyspace";
        /* integer */
        public static final String PORT = "port";
        /* string */
        public static final String CLUSTER_NAME = "clusterName";
        /* "LZ4", "SNAPPY", "NONE" */
        public static final String COMPRESSION = "compression";
        /* string */
        public static final String USERNAME = "username";
        /* string */
        public static final String PASSWORD = "password";
        /* "RoundRobinPolicy", "LatencyAwareRoundRobinPolicy", "TokenAwareRoundRobinPolicy" */
        public static final String LOAD_BALANCING_POLICY = "loadBalancingPolicy";
        /* boolean */
        public static final String ENABLE_JMX_REPORTING = "enableJMXReporting";
        /* boolean */
        public static final String ENABLE_METRICS = "enableMetrics";
        /* inter */
        public static final String LOCAL_CORE_CONNECTIONS_PER_HOST = "localCoreConnectionsPerHost";
        /* integer */
        public static final String REMOTE_CORE_CONNECTIONS_PER_HOST = "remoteCoreConnectionsPerHost";
        /* integer */
        public static final String LOCAL_MAX_CONNECTIONS_PER_HOST = "localMaxConnectionsPerHost";
        /* integer */
        public static final String REMOTE_MAX_CONNECTIONS_PER_HOST = "remoteMaxConnectionsPerHost";
        /* integer */
        public static final String LOCAL_NEW_CONNECTION_THRESHOLD= "localNewConnectionThreshold";
        /* integer */
        public static final String REMOTE_NEW_CONNECTION_THRESHOLD = "remoteNewConnectionThreshold";
        /* integer */
        public static final String LOCAL_MAX_REQUESTS_PER_CONNECTION = "localMaxRequestsPerConnection";
        /* integer */
        public static final String REMOTE_MAX_REQUESTS_PER_CONNECTION = "remoteMaxRequestsPerConnection";
        /* integer */
        public static final String PROTOCOL_VERSION = "protocolVersion";
        /* "ALL", "ANY", "EACH_QUORUM", "LOCAL_ONE", "LOCAL_QUORUM", "LOCAL_SERIAL", "ONE", "QUORUM", "SERIAL", "THREE", "TWO" */
        public static final String CONSISTENCY_LEVEL = "consistencyLevel";
        /* integer */
        public static final String FETCH_SIZE = "fetchSize";
        /* "ALL", "ANY", "EACH_QUORUM", "LOCAL_ONE", "LOCAL_QUORUM", "LOCAL_SERIAL", "ONE", "QUORUM", "SERIAL", "THREE", "TWO" */
        public static final String SERIAL_CONSISTENCY_LEVEL = "serialConsistencyLevel";
        /* "ConstantReconnectionPolicy", "ExponentialReconnectionPolicy",  */
        public static final String RECONNECTION_POLICY = "reconnectionPolicy";
        /* long */
        public static final String CONSTANT_RECONNECTION_POLICY_DELAY = "constantReconnectionPolicyDelay";
        /* long */
        public static final String EXPONENTIAL_RECONNECTION_POLICY_BASE_DELAY = "exponentialReconnectionPolicyBaseDelay";
        /* long */
        public static final String EXPONENTIAL_RECONNECTION_POLICY_MAX_DELAY = "exponentialReconnectionPolicyMaxDelay";
        /* "DefaultRetryPolicy", "DowngradingConsistencyRetryPolicy", "FallthroughRetryPolicy", 
         * "LoggingDefaultRetryPolicy", "LoggingDowngradingConsistencyRetryPolicy", "LoggingFallthroughRetryPolicy" */
        public static final String RETRY_POLICY = "retryPolicy";
        /* integer */
        public static final String CONNECTION_TIMEOUT_MILLIS = "connectionTimeoutMillis";
        /* boolean */
        public static final String KEEP_ALIVE = "keepAlive";
        /* integer */
        public static final String READ_TIMEOUT_MILLIS = "readTimeoutMillis";
        /* integer */
        public static final String RECEIVER_BUFFER_SIZE = "receiverBufferSize";
        /* boolean */
        public static final String REUSE_ADDRESS = "reuseAddress";
        /* integer */
        public static final String SEND_BUFFER_SIZE = "sendBufferSize";
        /* integer */
        public static final String SO_LINGER = "soLinger";
        /* boolean */
        public static final String TCP_NODELAY = "tcpNoDelay";
        /* boolean */
        public static final String ENABLE_SSL = "enableSSL";
        /* string */
        public static final String DATA_CENTER = "dataCenter";
        /* boolean */
        public static final String ALLOW_REMOTE_DCS_FOR_LOCAL_CONSISTENCY_LEVEL = "allowRemoteDCsForLocalConsistencyLevel";

    }

    /**
     * Constants related to Excel data source.
     */
    public static final class Excel {

        private Excel() {
            throw new AssertionError();
        }

        public static final String DATASOURCE = "excel_datasource";
        public static final String WORKBOOK_NAME = "workbookname";
        public static final String STARTING_ROW = "startingrow";
        public static final String MAX_ROW_COUNT = "maxrowcount";
        public static final String HAS_HEADER = "hasheader";
        public static final String HEADER_ROW = "headerrow";
    }

    /**
     * Constants related to MongoDB data source.
     */
    public static final class MongoDB {

        private MongoDB() {
            throw new AssertionError();
        }

        public static final String SERVERS = "mongoDB_servers";
        public static final String AUTHENTICATION_TYPE = "mongoDB_authentication_type";
        public static final String USERNAME = "username";
        public static final String PASSWORD = "password";
        public static final String DATABASE = "mongoDB_database";
        public static final String WRITE_CONCERN = "mongoDB_write_concern";
        public static final String READ_PREFERENCE = "mongoDB_read_preference";
        public static final String AUTO_CONNECT_RETRY = "mongoDB_autoConnectRetry";
        public static final String CONNECT_TIMEOUT = "mongoDB_connectTimeout";
        public static final String MAX_WAIT_TIME = "mongoDB_maxWaitTime";
        public static final String SOCKET_TIMEOUT = "mongoDB_socketTimeout";
        public static final String SSL_ENABLED = "mongoDB_ssl_enabled";
        public static final String AUTH_SOURCE = "mongoDB_auth_source";
        public static final String CONNECTIONS_PER_HOST = "mongoDB_connectionsPerHost";
        public static final String RESULT_COLUMN_NAME = "Document";

        public static class MongoOperationLabels {

            public static final String COUNT = "count";
            public static final String DROP = "drop";
            public static final String FIND = "find";
            public static final String FIND_ONE = "findOne";
            public static final String INSERT = "insert";
            public static final String REMOVE = "remove";
            public static final String UPDATE = "update";
            public static final String EXISTS = "exists";
            public static final String CREATE = "create";

        }

        public static enum MongoOperation {
            COUNT,
            DROP,
            FIND,
            FIND_ONE,
            INSERT,
            REMOVE,
            UPDATE,
            EXISTS,
            CREATE
        }

        public static class MongoAuthenticationTypes {
            public static final String PLAIN = "PLAIN";
            public static final String SCRAM_SHA_1 = "SCRAM-SHA-1";
            public static final String MONGODB_CR = "MONGODB-CR";
            public static final String GSSAPI = "GSSAPI";
            public static final String MONGODB_X509 = "MONGODB-X509";
        }

    }

    /**
     * Constants related to RDF data source.
     */
    public static final class RDF {

        private RDF() {
            throw new AssertionError();
        }

        public static final String DATASOURCE = "rdf_datasource";

    }

    /**
     * Constants related to SPARQL data sources
     */
    public static final class SPARQL {

        private SPARQL() {
            throw new AssertionError();
        }

        public static final String DATASOURCE = "sparql_datasource";
    }

    /**
     * Constants related to carbon data sources.
     */
    public static final class CarbonDatasource {

        private CarbonDatasource() {
            throw new AssertionError();
        }

        public static final String NAME = "carbon_datasource_name";
    }

    /**
     * Constants related to web data sources.
     */
    public static final class WebDatasource {

        private WebDatasource() {
            throw new AssertionError();
        }

        public static final String WEB_CONFIG = "web_harvest_config";
        public static final String QUERY_VARIABLE = "scraperVariable";
    }
    
    /**
     * Constants related to custom data sources.
     */
    public static final class CustomDataSource {

        private CustomDataSource() {
            throw new AssertionError();
        }
    	/**
    	 * The data source id is a unique name to identify the custom data source,
    	 * this property will be set in the parameters to the "init" method.
    	 */
    	public static final String DATASOURCE_ID = "__DATASOURCE_ID__";
        public static final String DATA_SOURCE_TABULAR_CLASS = "custom_tabular_datasource_class";
        public static final String DATA_SOURCE_QUERY_CLASS = "custom_query_datasource_class";
        public static final String DATA_SOURCE_PROPS = "custom_datasource_props";
    }

    /**
     * Constants which represents the element/attribute names of a dbs file.
     */
    public static final class DBSFields {

        private DBSFields() {
            throw new AssertionError();
        }

        public static final String DATA = "data";
        public static final String TRANSPORTS = "transports";
        public static final String POLICY = "policy";
        public static final String POLICY_KEY = "key";
        public static final String ENABLESEC = "enableSec";
        public static final String CONFIG = "config";
        public static final String ENABLE_ODATA = "enableOData";
        public static final String QUERY = "query";
        public static final String OPERATION = "operation";
        public static final String RESOURCE = "resource";
        public static final String ID = "id";
        public static final String SQL = "sql";
        public static final String DIALECT = "dialect";
        public static final String SPARQL = "sparql";
        public static final String SCRAPER_VARIABLE = "scraperVariable";
        public static final String EXCEL = "excel";
        public static final String GSPREAD = "gspread";
        public static final String RESULT = "result";
        public static final String ROW_NAME = "rowName";
        public static final String ELEMENT = "element";
        public static final String ATTRIBUTE = "attribute";
        public static final String NAME = "name";
        public static final String SERVICE_GROUP = "serviceGroup";
        public static final String CALL_QUERY = "call-query";
        public static final String CALL_QUERY_GROUP = "call-query-group";
        public static final String HREF = "href";
        public static final String COLUMN = "column";
        public static final String XSD_TYPE = "xsdType";
        public static final String OUTPUT_TYPE = "outputType";
        public static final String RDF_REF_URI = "rdf-ref-uri";
        public static final String RDF_BASE_URI = "rdfBaseURI";
        public static final String RESULT_TYPE_RDF = "rdf";
        public static final String RESULT_TYPE_XML = "xml";
        public static final String RESULT_TYPE_JSON = "json";
        public static final String TYPE = "type";
        public static final String SQL_TYPE = "sqlType";
        public static final String PARAM = "param";
        public static final String PARAM_TYPE = "paramType";
        public static final String ORDINAL = "ordinal";
        public static final String WITH_PARAM = "with-param";
        public static final String QUERY_PARAM = "query-param";
        public static final String VALUE = "value";
        public static final String ENABLE_BATCH_REQUESTS = "enableBatchRequests";
        public static final String ENABLE_BOXCARRING = "enableBoxcarring";
        public static final String DISABLE_STREAMING = "disableStreaming";
        public static final String DISABLE_LEGACY_BOXCARRING_MODE = "disableLegacyBoxcarringMode";
        public static final String RETURN_REQUEST_STATUS = "returnRequestStatus";
        public static final String SERVICE_STATUS = "serviceStatus";
        public static final String BASE_URI = "baseURI";
        public static final String USE_CONFIG = "useConfig";
        public static final String DESCRIPTION = "description";
        public static final String EMPTY_PARAM_FIX = "emptyParamFix";
        public static final String EVENT_TRIGGER = "event-trigger";
        public static final String XA_DATASOURCE = "xa-datasource";
        public static final String PROPERTY = "property";
        public static final String PROPERTIES = "properties";
        public static final String LANGUAGE = "language";
        public static final String EXPRESSION = "expression";
        public static final String TARGET_TOPIC = "target-topic";
        public static final String SUBSCRIPTIONS = "subscriptions";
        public static final String SUBSCRIPTION = "subscription";
        public static final String INPUT_EVENT_TRIGGER = "input-event-trigger";
        public static final String OUTPUT_EVENT_TRIGGER = "output-event-trigger";
        public static final String DEFAULT_NAMESPACE = "defaultNamespace";
        public static final String XSLT_PATH = "xsltPath";
        public static final String DEFAULT_VALUE = "defaultValue";
        public static final String REQUIRED_ROLES = "requiredRoles";
        public static final String VALIDATE_LENGTH = "validateLength";
        public static final String MINIMUM = "minimum";
        public static final String MAXIMUM = "maximum";
        public static final String VALIDATE_PATTERN = "validatePattern";
        public static final String PATTERN = "pattern";
        public static final String VALIDATE_LONG_RANGE = "validateLongRange";
        public static final String VALIDATE_DOUBLE_RANGE = "validateDoubleRange";
        public static final String VALIDATE_CUSTOM = "validateCustom";
        public static final String CLASS = "class";
        public static final String PATH = "path";
        public static final String METHOD = "method";
        public static final String WEB = "web";
        public static final String RDF = "RDF";
        public static final String RDF_DESCRIPTION = "Description";
        public static final String RDF_ABOUT = "about";
        public static final String RDF_DATATYPE = "datatype";
        public static final String RDF_RESOURCE = "resource";
        public static final String ROW_ID = "ROW_ID";
        public static final String RETURN_GENERATED_KEYS = "returnGeneratedKeys";
        public static final String RETURN_UPDATED_ROW_COUNT = "returnUpdatedRowCount";
        public static final String KEY_COLUMNS = "keyColumns";
        public static final String EXPORT = "export";
        public static final String OPTIONAL = "optional";
        public static final String FORCED_DEFAULT = "forceDefault";
        public static final String EXPORT_TYPE = "exportType";
        public static final String INPUT_NAMESPACE = "inputNamespace";
        public static final String NAMESPACE = "namespace";
        public static final String SERVICE_NAMESPACE = "serviceNamespace";
        public static final String PASSWORD_PROVIDER = "passwordProvider";
        public static final String PASSWORD_MANAGER = "passwordManager";
        public static final String PROTECTED_TOKENS = "protectedTokens";
        public static final String ENABLE_DTP = "enableDTP";
        public static final String TRANSACTION_MANAGER_JNDI_NAME = "txManagerJNDIName";
        public static final String USE_COLUMN_NUMBERS = "useColumnNumbers";
        public static final String ESCAPE_NON_PRINTABLE_CHAR = "escapeNonPrintableChar";
        public static final String STRUCT_TYPE = "structType";
        public static final String SWAGGER_LOCATION = "publishSwagger";
    }

    /**
     * Event-trigger languages.
     */
    public static final class EventTriggerLanguages {

        private EventTriggerLanguages() {
            throw new AssertionError();
        }

        public static final String XPATH = "XPath";
    }

    /**
     * Service status values.
     */
    public static final class ServiceStatusValues {

        private ServiceStatusValues() {
            throw new AssertionError();
        }

        public static final String ACTIVE = "active";
        public static final String INACTIVE = "inactive";
    }

    /**
     * GSpread visibility values.
     */
    public static final class GSpreadVisibility {

        private GSpreadVisibility() {
            throw new AssertionError();
        }

        public static final String PUBLIC = "public";
        public static final String PRIVATE = "private";
    }

    /**
     * List of RDBMS engines.
     */
    public static final class RDBMSEngines {

        private RDBMSEngines() {
            throw new AssertionError();
        }

        public static final String MYSQL = "mysql";
        public static final String DERBY = "derby";
        public static final String MSSQL = "mssqlserver";
        public static final String ORACLE = "oracle";
        public static final String DB2 = "db2";
        public static final String HSQLDB = "hsqldb";
        public static final String POSTGRESQL = "postgresql";
        public static final String SYBASE = "sybase";
        public static final String H2 = "h2";
        public static final String INFORMIX_SQLI = "informix-sqli";
        public static final String GENERIC = "Generic";
    }

    /**
     * Query parameter types.
     */
    public static final class QueryParamTypes {

        private QueryParamTypes() {
            throw new AssertionError();
        }

        public static final String SCALAR = "SCALAR";
        public static final String ARRAY = "ARRAY";
    }

    /**
     * Query types.
     */
    public static final class QueryTypes {

        private QueryTypes() {
            throw new AssertionError();
        }

        public static final String IN = "IN";
        public static final String OUT = "OUT";
        public static final String INOUT = "INOUT";
    }

    /**
     * Boxcarring operation names.
     */
    public static final class BoxcarringOps {

        private BoxcarringOps() {
            throw new AssertionError();
        }

        public static final String BEGIN_BOXCAR = "begin_boxcar";
        public static final String END_BOXCAR = "end_boxcar";
        public static final String ABORT_BOXCAR = "abort_boxcar";
    }

    /**
     * Elements used in event notifications.
     */
    public static final class EventNotification {

        private EventNotification() {
            throw new AssertionError();
        }

        public static final String MESSAGE_WRAPPER = "data-services-event";
        public static final String SERVICE_NAME = "service-name";
        public static final String QUERY_ID = "query-id";
        public static final String TIME = "time";
        public static final String CONTENT = "content";
    }

    /**
     * Values for advanced sql query properties.
     */
    public static final class AdvancedSQLProps {

        private AdvancedSQLProps() {
            throw new AssertionError();
        }

        public static final String FETCH_DIRECTION_FORWARD = "forward";
        public static final String FETCH_DIRECTION_REVERSE = "reverse";
    }

    /**
     * SQL query types that are non-stored procedures.
     */
    public static final String[] SQL_NORMAL_QUERY_TYPES = {"SELECT", "INSERT",
            "UPDATE", "DELETE", "CREATE", "ALTER", "DROP"};

    /**
     * JDBC URL prefixes used to identify the RDBMS engine from the driver.
     */
    public static final class JDBCDriverPrefixes {

        private JDBCDriverPrefixes() {
            throw new AssertionError();
        }

        public static final String MYSQL = "jdbc:mysql";
        public static final String DERBY = "jdbc:derby";
        public static final String MSSQL = "jdbc:sqlserver";
        public static final String ORACLE = "jdbc:oracle";
        public static final String DB2 = "jdbc:db2";
        public static final String HSQLDB = "jdbc:hsqldb";
        public static final String POSTGRESQL = "jdbc:postgresql";
        public static final String SYBASE = "jdbc:sybase";
        public static final String H2 = "jdbc:h2";
        public static final String INFORMIX = "jdbc:informix-sqli";
    }
    
    /**
     * Data Services SQL Driver URL prefixes used to specify Excel and GSpread URLs
     */
    public static final class DSSQLDriverPrefixes {
    	
    	private DSSQLDriverPrefixes() {
            throw new AssertionError();
        }
    	
    	public static final String JDBC_PREFIX = "jdbc";
        public static final String EXCEL_PREFIX = "jdbc:wso2:excel";
        public static final String GSPRED_PREFIX = "jdbc:wso2:gspread";
        public static final String PROVIDER_PREFIX = "wso2";
        public static final String FILE_PATH = "filePath";
    }

    /**
     * Represent XADatasource class for a driver
     */
    public static final class XAJDBCDriverClasses {

        private XAJDBCDriverClasses() {
            throw new AssertionError();
        }

        public static final String MYSQL = "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource";
        public static final String DERBY = "org.apache.derby.jdbc.EmbeddedXADataSource";
        public static final String MSSQL = "com.microsoft.sqlserver.jdbc.SQLServerXADataSource";
        public static final String ORACLE = "oracle.jdbc.xa.client.OracleXADataSource";
        public static final String DB2 = "com.ibm.db2.jcc.DB2XADataSource";
        public static final String HSQLDB = "org.hsqldb.jdbc.pool.JDBCXADataSource";
        public static final String POSTGRESQL = "org.postgresql.xa.PGXADataSource";
        public static final String SYBASE = "com.sybase.jdbc3.jdbc.SybXADataSource";
        public static final String H2 = "org.h2.jdbcx.JdbcDataSource";
        public static final String INFORMIX = "com.informix.jdbc.jdbc2.optional.InformixXADataSource";
    }

    /**
     * Parameters required for the AuthorizationProvider instance initiation
     */
    public static final class AuthorizationProviderConfig {
        private AuthorizationProviderConfig() {
            throw new AssertionError();
        }
        public static final String ELEMENT_NAME_AUTHORIZATION_PROVIDER= "authorization_provider";
        public static final String ATTRIBUTE_NAME_CLASS = "class";
        public static final String AUTHORIZATION_PROVIDER_SERVICE_PARAMETER = "AuthorizationProviderServiceParam";
    }
    
}
