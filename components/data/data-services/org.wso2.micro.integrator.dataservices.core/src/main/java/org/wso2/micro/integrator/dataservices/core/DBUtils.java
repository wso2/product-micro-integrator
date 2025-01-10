/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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
package org.wso2.micro.integrator.dataservices.core;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.databinding.utils.ConverterUtil;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.java2wsdl.TypeTable;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.commons.resolvers.ResolverFactory;
import org.apache.synapse.config.SynapseConfiguration;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.micro.core.Constants;
import org.wso2.micro.core.util.xml.XMLPrettyPrinter;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.common.DBConstants.DBSFields;
import org.wso2.micro.integrator.dataservices.common.DBConstants.RDBMSEngines;
import org.wso2.micro.integrator.dataservices.common.RDBMSUtils;
import org.wso2.micro.integrator.dataservices.core.auth.AuthorizationProvider;
import org.wso2.micro.integrator.dataservices.core.auth.UserStoreAuthorizationProvider;
import org.wso2.micro.integrator.dataservices.core.description.config.Config;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.ExternalParam;
import org.wso2.micro.integrator.dataservices.core.engine.ExternalParamCollection;
import org.wso2.micro.integrator.dataservices.core.engine.InternalParam;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;
import org.wso2.micro.integrator.dataservices.core.internal.DataServicesDSComponent;
import org.wso2.micro.integrator.ndatasource.core.utils.DataSourceUtils;
import org.wso2.micro.integrator.security.MicroIntegratorSecurityUtils;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.InitialContext;
import javax.transaction.TransactionManager;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

/**
 * Utility class for data services based operations.
 */
public class DBUtils {

    private static final Log log = LogFactory.getLog(DBUtils.class);

    private static Pattern udtPattern = Pattern.compile("(.*?(\\[\\d+\\]))");

    private static ScheduledExecutorService globalExecutorService = Executors
            .newSingleThreadScheduledExecutor();

    private static HashMap<String, String> conversionTypes = null;

    private static HashMap<String, String> xsdSqlTypeMap = null;

    private static String currentParamsDisabledProperty;

    /* initialize the conversion types */

    static {
        conversionTypes = new HashMap<String, String>();
        conversionTypes.put(DBConstants.DataTypes.CHAR, "java.lang.String");
        conversionTypes.put(DBConstants.DataTypes.STRING, "java.lang.String");
        conversionTypes.put(DBConstants.DataTypes.QUERY_STRING, "java.lang.String");
        conversionTypes.put(DBConstants.DataTypes.VARCHAR, "java.lang.String");
        conversionTypes.put(DBConstants.DataTypes.NVARCHAR, "java.lang.String");
        conversionTypes.put(DBConstants.DataTypes.TEXT, "java.lang.String");
        conversionTypes.put(DBConstants.DataTypes.NUMERIC, "java.math.BigDecimal");
        conversionTypes.put(DBConstants.DataTypes.DECIMAL, "java.math.BigDecimal");
        conversionTypes.put(DBConstants.DataTypes.MONEY, "java.math.BigDecimal");
        conversionTypes.put(DBConstants.DataTypes.SMALLMONEY, "java.math.BigDecimal");
        conversionTypes.put(DBConstants.DataTypes.BIT, "boolean");
        conversionTypes.put(DBConstants.DataTypes.BOOLEAN, "boolean");
        conversionTypes.put(DBConstants.DataTypes.TINYINT, "byte");
        conversionTypes.put(DBConstants.DataTypes.SMALLINT, "short");
        conversionTypes.put(DBConstants.DataTypes.INTEGER, "int");
        conversionTypes.put(DBConstants.DataTypes.BIGINT, "long");
        conversionTypes.put(DBConstants.DataTypes.REAL, "float");
        conversionTypes.put(DBConstants.DataTypes.FLOAT, "double");
        conversionTypes.put(DBConstants.DataTypes.DOUBLE, "double");
        conversionTypes.put(DBConstants.DataTypes.BINARY, "base64Binary"); /* byte[] */
        conversionTypes.put(DBConstants.DataTypes.VARBINARY, "base64Binary"); /* byte[] */
        conversionTypes.put(DBConstants.DataTypes.LONG_VARBINARY, "base64Binary"); /* byte [] */
        conversionTypes.put(DBConstants.DataTypes.IMAGE, "base64Binary"); /* byte[] */
        conversionTypes.put(DBConstants.DataTypes.BLOB, "base64Binary"); /* byte[] */
        conversionTypes.put(DBConstants.DataTypes.DATE, "java.sql.Date");
        conversionTypes.put(DBConstants.DataTypes.TIME, "java.sql.Time");
        conversionTypes.put(DBConstants.DataTypes.TIMESTAMP, "java.sql.Timestamp");
        conversionTypes.put(DBConstants.DataTypes.ANYURI, "java.net.URI");
        conversionTypes.put(DBConstants.DataTypes.STRUCT, "java.sql.Struct");
        conversionTypes.put(DBConstants.DataTypes.SQLXML, "java.sql.SQLXML");

        conversionTypes.put(DBConstants.DataTypes.VARINT, "java.math.BigInteger");
        conversionTypes.put(DBConstants.DataTypes.UUID, "java.lang.String");
        conversionTypes.put(DBConstants.DataTypes.INETADDRESS, "java.lang.String");
        conversionTypes.put(DBConstants.DataTypes.CLOB, "java.lang.String");

        xsdSqlTypeMap = new HashMap<String, String>();
        xsdSqlTypeMap.put("string", DBConstants.DataTypes.STRING);
        xsdSqlTypeMap.put("boolean", DBConstants.DataTypes.BOOLEAN);
        xsdSqlTypeMap.put("int", DBConstants.DataTypes.INTEGER);
        xsdSqlTypeMap.put("integer", DBConstants.DataTypes.INTEGER);
        xsdSqlTypeMap.put("long", DBConstants.DataTypes.LONG);
        xsdSqlTypeMap.put("float", DBConstants.DataTypes.FLOAT);
        xsdSqlTypeMap.put("double", DBConstants.DataTypes.DOUBLE);
        xsdSqlTypeMap.put("decimal", DBConstants.DataTypes.DECIMAL);
        xsdSqlTypeMap.put("dateTime", DBConstants.DataTypes.TIMESTAMP);
        xsdSqlTypeMap.put("time", DBConstants.DataTypes.TIME);
        xsdSqlTypeMap.put("date", DBConstants.DataTypes.DATE);
        xsdSqlTypeMap.put("base64Binary", DBConstants.DataTypes.BINARY);
        xsdSqlTypeMap.put("binary", DBConstants.DataTypes.BINARY);
    }
    
    private static SecretResolver secretResolver;

    private static XMLOutputFactory xmlOutputFactory;

    /** pre-fetch the XMLOutputFactory */
    static {
        xmlOutputFactory = XMLOutputFactory.newInstance();
    }

    private static XMLInputFactory xmlInputFactory;

    /** pre-fetch the XMLInputFactory */
    static {
        xmlInputFactory = XMLInputFactory.newInstance();
        Map props = loadFactoryProperties("XMLInputFactory.properties");
        if (props != null) {
            for (Object o : props.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                xmlInputFactory.setProperty((String) entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * This method load factory properties from the file.
     * @param name the name of the file.
     * @return Map of properties.
     */
    private static Map loadFactoryProperties(String name) {
        ClassLoader classLoader = DBUtils.class.getClassLoader();
        InputStream in = classLoader.getResourceAsStream(name);
        if (in == null) {
            return null;
        } else {
            try {
                Properties rawProps = new Properties();
                Map props = new HashMap();
                rawProps.load(in);
                for (Map.Entry<Object, Object> objectObjectEntry : rawProps.entrySet()) {
                    Map.Entry entry = (Map.Entry) objectObjectEntry;
                    String strValue = (String) entry.getValue();
                    Object value;
                    switch (strValue) {
                        case "true":
                            value = Boolean.TRUE;
                            break;
                        case "false":
                            value = Boolean.FALSE;
                            break;
                        default:
                            try {
                                value = Integer.valueOf(strValue);
                            } catch (NumberFormatException ex) {
                                value = strValue;
                            }
                            break;
                    }
                    props.put(entry.getKey(), value);
                }
                if (log.isDebugEnabled()) {
                    log.debug("Loaded factory properties from " + name + ": " + props);
                }
                return props;
            } catch (IOException e) {
                log.error("Failed to read from: " + name, e);
                return null;
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error("Failed to close the input stream of: " + name, e);
                }
            }
        }
    }

    static {
        currentParamsDisabledProperty = System.getProperty(DBConstants.DISABLE_CURRENT_PARAMS_IN_LOG);
    }

    public static String getCurrentParamsDisabledProperty() {
        return currentParamsDisabledProperty;
    }

    private static OMFactory omFactory;

    /** pre-fetch the OMFactory */
    static {
        omFactory = OMAbstractFactory.getOMFactory();
    }

    public static XMLOutputFactory getXMLOutputFactory() {
        return xmlOutputFactory;
    }

    public static XMLInputFactory getXMLInputFactory() {
        return xmlInputFactory;
    }

    public static OMFactory getOMFactory() {
        return omFactory;
    }

    /**
     * Converts from DS SQL types to Java types, e.g. "STRING" -> "java.lang.String".
     */
    public static String getJavaTypeFromSQLType(String sqlType) {
        return conversionTypes.get(sqlType);
    }

    /**
     * Converts from XML schema types to DS SQL types, e.g. "string" -> "STRING".
     */
    public static String getSQLTypeFromXsdType(String xsdType) {
        String sqlType = xsdSqlTypeMap.get(xsdType);
        if (sqlType == null) {
            sqlType = DBConstants.DataTypes.STRING;
        }
        return sqlType;
    }

    public static String getCurrentContextUsername(DataService dataService) {
        MessageContext ctx = MessageContext.getCurrentMessageContext();
        if (ctx != null) {
            try {
                return dataService.getAuthorizationProvider().getUsername(ctx);
            } catch (DataServiceFault dataServiceFault) {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Retrieves the current user's roles given the username.
     *
     * @param username The username
     * @return The user roles
     * @throws DataServiceFault
     */
    public static String[] getUserRoles(String username) throws DataServiceFault {
        try {
            return MicroIntegratorSecurityUtils.getUserStoreManager().getRoleListOfUser(username);
        } catch (UserStoreException e) {
            throw new DataServiceFault(e, "Error while getting the user role");
        }
    }


    /**
     * Retrieves all roles for a given tenantId to be used in role based filtering when creating dataservice.
     *
     * @return The user roles
     * @throws DataServiceFault
     */
    public static String[] getAllRoles(int tenantId) throws DataServiceFault {
//        RegistryService registryService = DataServicesDSComponent.getRegistryService();
//        try {
//            UserRealm realm = registryService.getUserRealm(tenantId);
//            String roles[] = realm.getUserStoreManager().getRoleNames();
//            return roles;
//        } catch (Exception e) {
//            String msg = "Error in retrieving the realm for the tenant id: " + tenantId
//                         + ". " + e.getMessage();
//            throw new DataServiceFault(msg);
//        }
        return new String[0];
    }

    /**
     * This method is to get current user tenant ID, This will be only called when creating data service with
     * role based filtering. So a user will invoke this method hence there should be active session when this is
     * called.
     *
     * @return tenantId
     * @throws DataServiceFault
     */
//    public static int getCurrentUserTenantId() throws DataServiceFault {
//        RealmService realmService = DataServicesDSComponent.getRealmService();
//        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
//        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
//        try {
//            if (tenantId < MultitenantConstants.SUPER_TENANT_ID) {
//                tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
//            }
//            if (tenantId < MultitenantConstants.SUPER_TENANT_ID) {
//                /* the tenant doesn't exist. */
//                log.error("The tenant doesn't exist. Tenant domain:" + tenantDomain);
//                throw new DataServiceFault("Access Denied. You are not authorized.");
//            }
//            if (!realmService.getTenantManager().isTenantActive(tenantId)) {
//                /* the tenant is not active. */
//                log.error("The tenant is not active. Tenant domain:" + tenantDomain);
//                throw new DataServiceFault("The tenant is not active. Tenant domain:"
//                                           + tenantDomain);
//            }
//            return tenantId;
//        } catch (Exception e) {
//            String msg = "Error in retrieving the realm for the tenant id: " + tenantId
//                         + ". " + e.getMessage();
//            throw new DataServiceFault(msg);
//        }
//    }

    public static boolean authenticate(String username, String password) throws DataServiceFault {
//    	try {
//            RegistryService registryService = DataServicesDSComponent.getRegistryService();
//            UserRealm realm = registryService.getUserRealm(
//                    PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId());
//    		username = MultitenantUtils.getTenantAwareUsername(username);
//    		return realm.getUserStoreManager().authenticate(username, password);
//    	} catch (Exception e) {
//			throw new DataServiceFault(e, "Error in authenticating user '" + username + "'");
//		}
        return true;
    }

    /**
     * This method returns the available data services names.
     *
     * @param axisConfiguration Axis configuration
     * @return names of available data services
     * @throws AxisFault
     */
    public static String[] getAvailableDS(AxisConfiguration axisConfiguration) throws AxisFault {
        List<String> serviceList = new ArrayList<>();
        Map<String, AxisService> map = axisConfiguration.getServices();
        Set<String> set = map.keySet();
        for (String serviceName : set) {
            try {
                AxisService axisService = axisConfiguration.getService(serviceName);
                Parameter parameter = axisService.getParameter(DBConstants.AXIS2_SERVICE_TYPE);
                if (parameter != null) {
                    if (DBConstants.DB_SERVICE_TYPE.equals(parameter.getValue().toString())) {
                        serviceList.add(serviceName);
                    }
                }
            } catch (AxisFault axisFault) {
                if (axisFault.getMessage().contains("inactive")) {
                    log.debug("Ignoring axisFault due to inactive service.");
                } else {
                    log.error("Error occurred while populating service " + serviceName + " : "
                              + axisFault.getMessage(), axisFault);
                }
            }
        }
        return serviceList.toArray(new String[serviceList.size()]);
    }

    /**
     * This method verifies whether there's an existing data service for the given name.
     *
     * @param axisConfiguration Axis configuration
     * @param dataService       Data service
     * @return Boolean (Is available)
     * @throws AxisFault
     */
    public static boolean isAvailableDS(AxisConfiguration axisConfiguration, String dataService) throws AxisFault {
        Map<String, AxisService> map = axisConfiguration.getServices();
        AxisService axisService = map.get(dataService);
        if (axisService != null) {
            Parameter parameter = axisService.getParameter(DBConstants.AXIS2_SERVICE_TYPE);
            if (parameter != null) {
                if (DBConstants.DB_SERVICE_TYPE.equals(parameter.getValue().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This method verifies whether there's an existing data service group for the given name data service group.
     *
     * @param axisConfiguration Axis configuration
     * @param dataServiceGroup  Data service Group
     * @return Boolean (Is available)
     * @throws AxisFault
     */
    public static boolean isAvailableDSServiceGroup(AxisConfiguration axisConfiguration, String dataServiceGroup)
            throws AxisFault {
        Iterator<AxisServiceGroup> map = axisConfiguration.getServiceGroups();
        while (map.hasNext()) {
            AxisServiceGroup serviceGroup = map.next();
            if (serviceGroup.getServiceGroupName().equals(dataServiceGroup)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method returns an active existing Axis Service for the given name data service group.
     *
     * @param axisConfiguration Axis configuration
     * @param serviceName       Data service Name
     * @return Boolean (Is available)
     * @throws AxisFault
     */
    public static AxisService getActiveAxisServiceAccordingToDataServiceGroup(AxisConfiguration axisConfiguration,
                                                                              String serviceName) throws AxisFault {
        Iterator<AxisServiceGroup> map = axisConfiguration.getServiceGroups();
        AxisServiceGroup serviceGroup = null;
        while (map.hasNext()) {
            serviceGroup = map.next();
            if ( serviceGroup.getServiceGroupName().equals(serviceName)) {
                break;
            } else {
                serviceGroup = null;
            }
        }

        if (serviceName.contains("/")) {
            String[] splitArray = serviceName.split("\\/");
            if (splitArray.length >= 1) {
                serviceName = splitArray[splitArray.length - 1];
            }
        }

        if (serviceGroup != null) {
            AxisService service = serviceGroup.getService(serviceName);
            if (service != null && service.isActive()) {
                return service;
            }
        }
        return null;
    }

    public static boolean isRegistryPath(String path) {
        if (path.startsWith(DBConstants.CONF_REGISTRY_PATH_PREFIX) || path.startsWith(DBConstants.GOV_REGISTRY_PATH_PREFIX)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Creates and returns an InputStream from the file path / http location given.
     *
     * @throws DataServiceFault
     * @see InputStream
     */
    public static InputStream getInputStreamFromPath(String path) throws IOException,
                                                                         DataServiceFault {
        InputStream ins = null;
        /*
            Security Comment :
            This path is trustworthy, path is configured in the dbs file.
         */
        if (path.startsWith("http://")) {
            /* This is a url file path */
            URL url = new URL(path);
            ins = url.openStream();
        } else if (isRegistryPath(path)) {
            ConfigurationContext configCtx = DataHolder.getInstance().getConfigurationContext();
            if (configCtx != null) {
                Parameter synCfgParam = configCtx.getAxisConfiguration().getParameter
                        (SynapseConstants.SYNAPSE_CONFIG);
                if (synCfgParam == null) {
                    throw new DeploymentException("SynapseConfiguration not found. " +
                            "Are you sure that you are running Synapse?");
                }
                SynapseConfiguration synapseConfig = (SynapseConfiguration) synCfgParam.getValue();
                String content = resolveRegistryEntryText(synapseConfig, path);
                ins = IOUtils.toInputStream(content, "UTF-8");
            }
        } else {
            File csvFile = new File(path);
            if (path.startsWith("." + File.separator) || path.startsWith(".." + File.separator)) {
                /* this is a relative path */
                path = csvFile.getAbsolutePath();
            }
            /* local file */
            ins = new FileInputStream(path);
        }
        return ins;
    }

    /**
     * Resolves the registry key
     * This method uses SynapseEnvironment to resolve the keys
     *
     * @param synapseConfig      SynapseConfiguration
     * @param regEntryKey registry entry key to be resolved
     * @return Resolved reg entry
     */
    private static String resolveRegistryEntryText(SynapseConfiguration synapseConfig, String regEntryKey) {
        Object regEntry = synapseConfig.getRegistry().lookup(regEntryKey);
        String resolvedValue = "";
        if (regEntry instanceof OMElement) {
            OMElement e = (OMElement) regEntry;
            resolvedValue = e.toString();
        } else if (regEntry instanceof OMText) {
            String rawValue = ((OMText) regEntry).getText();
            byte[] decodedBytes = Base64.decodeBase64(rawValue.getBytes(StandardCharsets.UTF_8));
            resolvedValue = new String(decodedBytes, StandardCharsets.UTF_8);
        } else if (regEntry instanceof String) {
            resolvedValue = (String) regEntry;
        }
        return resolvedValue;
    }


    /**
     * create a map which maps the column numbers to column names,
     * column numbers starts with 1 (1 based).
     */
    public static Map<Integer, String> createColumnMappings(String[] header) throws IOException {
        Map<Integer, String> mappings = null;
        if (header != null) {
            mappings = new HashMap<Integer, String>();
            /* add mappings: column index -> column name */
            for (int i = 0; i < header.length; i++) {
                mappings.put(i + 1, header[i]);
            }
        } else {
            mappings = new StringNumberMap();
        }
        return mappings;
    }

    /**
     * This class represents a Map class which always returns the value same as the key.
     */
    private static class StringNumberMap extends AbstractMap<Integer, String> {

        public Set<Entry<Integer, String>> entrySet() {
            return null;
        }

        @Override
        public String get(Object key) {
            return key.toString();
        }

    }

    /**
     * Utility method that returns a string which contains the stack trace of the given
     * Exception object.
     */
    public static String getStacktraceFromException(Throwable e) {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(byteOut);
        e.printStackTrace(writer);
        writer.close();
        String message = new String(byteOut.toByteArray());
        return message;
    }

    /**
     * Returns the most suitable value for the JDBC Result Set FetchSize property,
     * for the DBMS engine of the given JDBC URL.
     */
    public static int getOptimalRSFetchSizeForRDBMS(String jdbcUrl) {
        if (jdbcUrl == null) {
            return 1;
        }
        String rdbms = RDBMSUtils.getRDBMSEngine(jdbcUrl);
        if (rdbms.equals(RDBMSEngines.MYSQL)) {
            return Integer.MIN_VALUE;
        } else {
            return 1;
        }
    }

    /**
     * Returns whether or not to apply fetch size for the given jdbc connection
     */
    public static boolean getChangeFetchSizeForRDBMS(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String rdbms = RDBMSUtils.getRDBMSEngine(jdbcUrl);
        if (rdbms.equals(RDBMSEngines.MYSQL)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Create a Timestamp object from the given timestamp string.
     */
    public static Timestamp getTimestamp(String value) throws DataServiceFault, ParseException {
        if (value == null || value.isEmpty()){
            throw new DataServiceFault("Empty string or null value was found as timeStamp.");
        }
        return new Timestamp(ConverterUtil.convertToDateTime(value).getTimeInMillis());
    }

    /**
     * Create a Time object from the given time string.
     */
    public static Time getTime(String value) throws DataServiceFault, ParseException {
        if (value == null || value.isEmpty()) {
            throw new DataServiceFault("Empty string or null value was found as time.");
        }

        // ConverterUtil.convertToTime function requires hh:mm:ss type. However, OData version 4.8.0 returns
        // hh:mm for hh:mm:00 and following if block is needed to handle that issue
        if (value.length() == 5) {
            value += ":00";
        }

        return new Time(ConverterUtil.convertToTime(value).getAsCalendar().getTimeInMillis());
    }

    /**
     * Create a Date object from the given date string.
     */
    public static Date getDate(String value) throws DataServiceFault {
        /* if something goes wrong with converting the value to a date,
           * try with dateTime and get the date out it, this is because,
           * some service clients send a full date-time string for a date */
        try {
            java.util.Date date = ConverterUtil.convertToDate(value);
            if (null == date) {
                throw new DataServiceFault("Empty string or null value was found as date.");
            } else {
                return new Date(date.getTime());
            }
        } catch (Exception e) {
            Calendar calendarDate = ConverterUtil.convertToDateTime(value);
            if (null == calendarDate) {
                throw new DataServiceFault("Empty string or null value was found as date.");
            } else {
                return new Date(calendarDate.getTimeInMillis());
            }
        }
    }

    /**
     * Prettify a given XML string
     */
    public static String prettifyXML(String xmlContent) {
    	Element element = DataSourceUtils.stringToElement(xmlContent);
    	if (element == null) {
    	    throw new RuntimeException("Error in converting string to XML: " + xmlContent);
    	}
		removeWhitespaceInMixedContentElements(element);
    	xmlContent = DataSourceUtils.elementToString(element);
        ByteArrayInputStream byteIn = new ByteArrayInputStream(xmlContent.getBytes());
        XMLPrettyPrinter prettyPrinter = new XMLPrettyPrinter(byteIn);
        return prettyPrinter.xmlFormat().trim();
    }

    private static List<Node> getNodesAsList(Element element) {
    	List<Node> nodes = new ArrayList<Node>();
    	NodeList nodeList = element.getChildNodes();
    	int count = nodeList.getLength();
    	for (int i = 0; i < count; i++) {
    		nodes.add(nodeList.item(i));
    	}
    	return nodes;
    }

    private static List<Element> getChildElements(Element element) {
    	List<Element> childEls = new ArrayList<Element>();
    	for (Node tmpNode : getNodesAsList(element)) {
    		if (tmpNode.getNodeType() == Node.ELEMENT_NODE) {
    			childEls.add((Element) tmpNode);
    		}
    	}
    	return childEls;
    }

    private static List<Node> getWhitespaceNodes(Element element) {
    	List<Node> nodes = new ArrayList<Node>();
    	for (Node node : getNodesAsList(element)) {
    		if (node.getNodeType() == Node.TEXT_NODE &&
    				node.getNodeValue().trim().length() == 0) {
    			nodes.add(node);
    		}
    	}
    	return nodes;
    }

	private static void removeWhitespaceInMixedContentElements(Element element)  {
    	List<Element> childEls = getChildElements(element);
    	if (childEls.size() > 0) {
    		for (Node node : getWhitespaceNodes(element)) {
    			element.removeChild(node);
    		}
    		for (Element childEl : childEls) {
    			removeWhitespaceInMixedContentElements(childEl);
    		}
    	}
    }

    /**
     * Prettify a given XML file
     */
    public static void prettifyXMLFile(String filePath) throws IOException, URISyntaxException {
        File file = new File(new URI(filePath).normalize().toString());
        String prettyXML = prettifyXML(FileUtils.readFileToString(file));
        FileUtils.writeStringToFile(file, prettyXML);
    }

    /**
     * Encode the given string with base64 encoding.
     */
    public static String encodeBase64(String value) {
        try {
            return new String(Base64.encodeBase64(value.getBytes(
                    DBConstants.DEFAULT_CHAR_SET_TYPE)),
                    DBConstants.DEFAULT_CHAR_SET_TYPE);
        } catch (UnsupportedEncodingException ueo) {
            throw new RuntimeException(ueo);
        }
    }

    /**
     * Creates an AxisFault.
     */
    public static AxisFault createAxisFault(Exception e) {
        AxisFault fault;
        Throwable cause = e.getCause();
        if (cause != null) {
            fault = new AxisFault(e.getMessage(), cause);
        } else {
            fault = new AxisFault(e.getMessage());
        }
        fault.setDetail(DataServiceFault.extractFaultMessage(e));
        fault.setFaultCode(new QName(DBConstants.WSO2_DS_NAMESPACE,
                                     DataServiceFault.extractFaultCode(e)));
        return fault;
    }

    /**
     * Creates OMElement using error details.
     */
    public static OMElement createDSFaultOM(String msg) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMElement ele = fac.createOMElement(new QName(
                DBConstants.WSO2_DS_NAMESPACE, DBConstants.DS_FAULT_ELEMENT));
        ele.setText(msg);
        return ele;
    }

    public static String evaluateString(String source,
                                        ExternalParamCollection params) throws DataServiceFault {
        StringBuilder builder = new StringBuilder();
        /* http://www.product.fake/cd/{productCode} */
        int leftBracketIndex = source.indexOf('{', 0);
        int rightBracketIndex = source.indexOf('}', leftBracketIndex);
        if (leftBracketIndex == -1 || rightBracketIndex == -1) {
            throw new DataServiceFault("The source string: " + source + " is not parameterized.");
        }
        String paramName = source.substring(leftBracketIndex + 1, rightBracketIndex);
        /* workaround for different character case issues in column names */
        paramName = paramName.toLowerCase();
        ExternalParam exParam = params.getParam(paramName);
        if (exParam == null) {
            throw new DataServiceFault("The parameter: " + paramName +
                    " cannot be found for the source string: " + source);
        }
        String paramValue = exParam.getValue().getValueAsString();
        builder.append(source.subSequence(0, leftBracketIndex));
        builder.append(paramValue);
        builder.append(source.substring(rightBracketIndex + 1));
        return builder.toString();
    }

    /**
     * Schedules a given task for one-time execution using the executer framework.
     *
     * @param task  The task to be executed
     * @param delay The delay in milliseconds for the task to be executed
     */
    public static void scheduleTask(Runnable task, long delay) {
        globalExecutorService.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Check the given text is empty or not.
     *
     * @param text The text to be checked
     * @return true if text is null or trimmed text length is empty, or else false
     */
    public static boolean isEmptyString(String text) {
        if (text != null && text.trim().length() > 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Check the given password is encrypted or not, if its encrypted resolve the password.
     *
     * @param dataService Data service object
     * @param password    Password before resolving
     * @return Resolved password
     */
    public static String resolvePasswordValue(DataService dataService, String password) {
        SecretResolver secretResolver = dataService.getSecretResolver();
        if (secretResolver != null && secretResolver.isTokenProtected(password)) {
            return secretResolver.resolve(password);
        } else {
            return password;
        }
    }

    /**
     * Returns the best effort way of finding the current tenant id,
     * even if this is not in a current message request, i.e. deploying services.
     * Assumption: when tenants other than the super tenant is activated,
     * the registry service must be available. So, the service deployment and accessing the registry,
     * will happen in the same thread, without the callbacks being used.
     *
     * @return The tenant id
     */
    public static int getCurrentTenantId() {

        return Constants.SUPER_TENANT_ID;

    	/*try {
	    	int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
	    	if (tenantId == -1) {
                     throw new RuntimeException("Tenant id cannot be -1");
	        }
	    	return tenantId;
    	} catch (NoClassDefFoundError e) { // Workaround for Unit Test failure
    		return Constants.SUPER_TENANT_ID;
    	} catch (ExceptionInInitializerError e) {
    		return Constants.SUPER_TENANT_ID;
    	}*/

    }

    /**
     * Returns the simple schema type from the type name,
     */
    public static QName getSimpleSchemaTypeName(TypeTable typeTable, String typeName) {
        if (typeName.equals("java.net.URI")) {
            return new QName(DBConstants.XSD_NAMESPACE, "anyURI");
        }
        if (typeName.equals("java.sql.Struct")) {
            return new QName(DBConstants.XSD_NAMESPACE, "anyType");
        }
        return typeTable.getSimpleSchemaTypeName(typeName);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> extractProperties(OMElement propsParentEl) {
        Map<String, String> properties = new HashMap<String, String>();
        OMElement propEl = null;
        Iterator<OMElement> itr = propsParentEl.getChildrenWithName(new QName(DBSFields.PROPERTY));
        String text;
        while (itr.hasNext()) {
            propEl = itr.next();
            if (propEl.getChildElements().hasNext()) {
                text = propEl.toString();
            } else {
                if (propEl.getText() == null) {
                    text = null;
                } else {
                    text = ResolverFactory.getInstance().getResolver(propEl.getText()).resolve();
                }
            }
            if(text != null && !text.equals("")) {
            	properties.put(propEl.getAttributeValue(new QName(DBSFields.NAME)), text);
            }
        }
        return properties;
    }

    /**
     * Get the container managed transaction manager; if a JNDI name is given,
     * that name is looked for a TransactionManager object, if not, the standard JNDI
     * names are checked.
     *
     * @param txManagerJNDIName The user given JNDI name of the TransactionManager
     * @return The TransactionManager object
     * @throws DataServiceFault
     */
    public static TransactionManager getContainerTransactionManager(String txManagerJNDIName)
            throws DataServiceFault {
        TransactionManager txManager = null;
        if (txManagerJNDIName != null) {
            try {
                txManager = InitialContext.doLookup(txManagerJNDIName);
            } catch (Exception e) {
                throw new DataServiceFault(e,
                                           "Cannot find TransactionManager with the given JNDI name '" +
                                txManagerJNDIName + "'");
            }
        }
        /* get the transaction manager from the well known JNDI names from the cache */
        txManager = DBDeployer.getCachedTransactionManager();
        return txManager;
    }

    /**
     * Creates a new OMElement from the given element and build it and return.
     *
     * @param result The object to be cloned and built
     * @return The new cloned and built OMElement
     */
    public static OMElement cloneAndReturnBuiltElement(OMElement result) {
        StAXOMBuilder builder = new StAXOMBuilder(result.getXMLStreamReaderWithoutCaching());
        result = builder.getDocumentElement();
        result.build();
        return result;
    }

    /**
     * This util method is used to retrieve the string tokens resides in a particular
     * udt parameter.
     *
     * @param param Name of the parameter
     * @return
     */
    public static Queue<String> getTokens(String param) {
        boolean isString = false;
        Queue<String> tokens = new LinkedBlockingQueue<String>();
        char[] chars = param.toCharArray();
        StringBuilder columnName = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            Character c = chars[i];
            if (!".".equals(c.toString()) && !"[".equals(c.toString()) &&
                    !"]".equals(c.toString())) {
                isString = true;
                columnName.append(c.toString());
                if (i == chars.length - 1) {
                    tokens.add(columnName.toString());
                }
            } else {
                if (isString) {
                    tokens.add(columnName.toString());
                    columnName = new StringBuilder();
                    isString = false;
                }
                tokens.add(c.toString());
            }

        }
        return tokens;
    }

    /**
     * This method is used to embed syntaxes associated with UDT attribute notations to
     * a queue of string tokens extracted from a UDT parameter.
     *
     * @param tokens      Queue of string tokens
     * @param syntaxQueue Syntax embedded tokens
     * @param isIndex     Flag to determine whether a particular string token is an inidex
     *                    or a column name
     */
    public static void getSyntaxEmbeddedQueue(Queue<String> tokens, Queue<String> syntaxQueue,
                                              boolean isIndex) {
        if (!tokens.isEmpty()) {
            if ("[".equals(tokens.peek())) {
                isIndex = true;
                tokens.poll();
                syntaxQueue.add("INEDX_START");
                syntaxQueue.add(tokens.poll());
            } else if ("]".equals(tokens.peek())) {
                isIndex = false;
                tokens.poll();
                syntaxQueue.add("INDEX_END");
            } else if (".".equals(tokens.peek())) {
                tokens.poll();
                syntaxQueue.add("DOT");
                syntaxQueue.add("COLUMN");
                syntaxQueue.add(tokens.poll());
            } else {
                if (isIndex) {
                    syntaxQueue.add("INDEX");
                    syntaxQueue.add(tokens.poll());
                } else {
                    syntaxQueue.add("COLUMN");
                    syntaxQueue.add(tokens.poll());
                }
            }
            getSyntaxEmbeddedQueue(tokens, syntaxQueue, isIndex);
        }
    }

    public static String getConnectionURL4XADataSource(Config config) throws XMLStreamException {
        String connectionURL = null;
        String connectionProperty = config.getProperty(DBConstants.RDBMS.DATASOURCE_PROPS);
        if (connectionProperty != null) {
            OMElement payload = AXIOMUtil.stringToOM(connectionProperty);
            Map<String, String> properties = extractProperties(payload);
            Collection<String> propValues = properties.values();
            for (String propValue : propValues) {
                if (propValue.startsWith("jdbc:")) {
                    connectionURL = propValue;
                    break;
                }
            }
        }
        return connectionURL;
    }

    public static boolean isUDT(ParamValue paramValue) {
        return paramValue != null && (paramValue.getValueType() == ParamValue.PARAM_VALUE_UDT);
    }

    public static boolean isSQLArray(ParamValue paramValue) {
        return paramValue != null && (paramValue.getValueType() == ParamValue.PARAM_VALUE_ARRAY);
    }

    /**
     * Util method to parse index string and produce the list of nested indices.
     *
     * @param indexString Index String.
     * @return The list of nested indices.
     * @throws DataServiceFault DataServiceFault.
     */
    public static List<Integer> getNestedIndices(String indexString) throws DataServiceFault {
        List<Integer> indices = new ArrayList<Integer>();
        String[] temp = indexString.split("\\[");
        for (String s : temp) {
            if (!"".equals(s)) {
                try {
                    indices.add(Integer.parseInt(s.substring(0, s.indexOf("]"))));
                } catch (NumberFormatException e) {
                    throw new DataServiceFault("Unable to determine nested indices. Incompatible " +
                            "value specified for the attribute index");
                }
            }
        }
        return indices;
    }

    /**
     * Processes a particular SQL Array object and interprets its value as a ParamValue object.
     *
     * @param sqlArray   SQL Array element.
     * @param paramValue Parameter value object initialized to contain an array of ParamValues.
     * @return ParamValue object representing the SQL Array.
     * @throws SQLException Throws an SQL Exception if the result set is not accessible.
     */
    public static ParamValue processSQLArray(Array sqlArray,
                                             ParamValue paramValue) throws SQLException {
        ResultSet rs = sqlArray.getResultSet();
        while (rs.next()) {
            Object arrayEl = rs.getObject(2);
            if (arrayEl instanceof Struct) {
                paramValue.getArrayValue().add(new ParamValue((Struct) arrayEl));
            } else if (arrayEl instanceof Array) {
                paramValue.getArrayValue().add(processSQLArray(
                        (Array) arrayEl, new ParamValue(ParamValue.PARAM_VALUE_ARRAY)));
            } else {
                paramValue.getArrayValue().add(new ParamValue(String.valueOf(arrayEl)));
            }
        }
        rs.close();
        return paramValue;
    }

    /**
     * Extracts the UDT column name from a given parameter name
     *
     * @param param User specified parameter name
     * @return UDT column name
     */
    public static String extractUDTObjectName(String param) {
        Matcher m = udtPattern.matcher(param);
        if (m.find()) {
            String tmp = m.group();
            Pattern patternToGetIndex = Pattern.compile("\\[\\d+\\]");
            Matcher matcherToGetIndex = patternToGetIndex.matcher(tmp);
            if (matcherToGetIndex.find()) {
                int lengthOfIndexPart = matcherToGetIndex.group().length();
                return tmp.substring(0, tmp.length() - lengthOfIndexPart).trim();
            }
        }
        return null;
    }

	public static synchronized String loadFromSecureVault(String alias) {
		if (secretResolver == null) {
		    secretResolver = SecretResolverFactory.create((OMElement) null, false);
		    secretResolver.init(DataServicesDSComponent.
		    		getSecretCallbackHandlerService().getSecretCallbackHandler());
		}
		return secretResolver.resolve(alias);
	}

	public static OMElement wrapBoxCarringResponse(OMElement result) {
		OMFactory fac = OMAbstractFactory.getOMFactory();
		OMElement wrapperElement = fac.createOMElement(new QName(DBConstants.WSO2_DS_NAMESPACE,
                DBConstants.DATA_SERVICE_REQUEST_BOX_RESPONSE_WRAPPER_ELEMENT));
		if (result != null) {
			wrapperElement.addChild(result);
		}
		OMDocument doc = fac.createOMDocument();
		doc.addChild(wrapperElement);
		return doc.getOMDocumentElement();
	}

	public static void populateStandardCustomDSProps(Map<String, String> dsProps,
                                                     DataService dataService, Config config) {
		String dsInfo = dataService.getTenantId() + "#"
				+ dataService.getName() + "#" + config.getConfigId();
		dsProps.put(DBConstants.CustomDataSource.DATASOURCE_ID, UUID.nameUUIDFromBytes(
				dsInfo.getBytes(Charset.forName(DBConstants.DEFAULT_CHAR_SET_TYPE))).toString());
		if (log.isDebugEnabled()) {
			log.debug("Custom Inline Data Source; ID: " + dsInfo +
					" UUID:" + dsProps.get(DBConstants.CustomDataSource.DATASOURCE_ID));
		}
	}

	/**
	 * Convert the input parameter values to its types object values.
	 * @param params The input params
	 * @return The typed object values
	 * @throws DataServiceFault
	 */
	public static Object[] convertInputParamValues(List<InternalParam> params)
            throws DataServiceFault {
		Object[] result = new Object[params.size()];
		InternalParam param;
        for (int i = 0; i < result.length; i++) {
            param = params.get(i);
            try {
                result[i] = convertInputParamValue(param.getValue().getValueAsString(),
                                                   param.getSqlType());
            } catch (DataServiceFault dataServiceFault) {
                throw new DataServiceFault(dataServiceFault, "Error processing parameter - " + param.getName() + ", Error - " + dataServiceFault.getMessage());
            }
        }
        return result;
	}

	/**
	 * Convert the string input param value to its typed object value.
	 * @param value The string value of the input param
	 * @param type The type of the input value, defined at DBConstants.DataTypes.
	 * @return The typed object value of the input param
	 */
	public static Object convertInputParamValue(String value, String type) throws DataServiceFault {
		try {
			if (DBConstants.DataTypes.INTEGER.equals(type)) {
				return Integer.parseInt(value);
			} else if (DBConstants.DataTypes.LONG.equals(type)) {
				return Long.parseLong(value);
			} else if (DBConstants.DataTypes.FLOAT.equals(type)) {
				return Float.parseFloat(value);
			} else if (DBConstants.DataTypes.DOUBLE.equals(type)) {
				return Double.parseDouble(value);
			} else if (DBConstants.DataTypes.BOOLEAN.equals(type)) {
				return Boolean.parseBoolean(value);
			} else if (DBConstants.DataTypes.DATE.equals(type)) {
				return new java.util.Date(DBUtils.getDate(value).getTime());
			} else if (DBConstants.DataTypes.TIME.equals(type)) {
				Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(DBUtils.getTime(value).getTime());
				return cal;
			} else if (DBConstants.DataTypes.TIMESTAMP.equals(type)) {
				Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(DBUtils.getTimestamp(value).getTime());
				return cal;
			} else {
				return value;
			}
		} catch (Exception e) {
			throw new DataServiceFault(e);
		}
	}

//    public static String getTenantDomainFromId(int tid) {
//    	try {
//			return DataServicesDSComponent.getRealmService().getTenantManager()
//					.getTenant(tid).getDomain();
//		} catch (UserStoreException e) {
//			throw new RuntimeException(e);
//		}
//    }

    /**
     * Get roles using the AuthorizationProvider using the config given.
     *
     * @param authProviderConfig xml config.
     * @return role array
     * @throws DataServiceFault
     */
    public static String[] getAllRolesUsingAuthorizationProvider(String authProviderConfig)
            throws DataServiceFault {
        try {
            AuthorizationProvider authorizationProvider;
            if (authProviderConfig != null && !authProviderConfig.isEmpty()) {
                StAXOMBuilder builder = new StAXOMBuilder(new ByteArrayInputStream(authProviderConfig.getBytes(StandardCharsets.UTF_8)));
                OMElement documentElement =  builder.getDocumentElement();
                authorizationProvider = generateAuthProviderFromXMLOMElement(documentElement);
            } else {
                authorizationProvider = new UserStoreAuthorizationProvider();
            }
            return authorizationProvider.getAllRoles();
        } catch (XMLStreamException e) {
            throw new DataServiceFault(e, "Error reading XML file data - " + authProviderConfig + " Error - " + e.getMessage());
        }
    }

    /**
     * Helper method to generate Authorization provider using config element
     *
     * @param authorizationProviderElement config element
     * @return authorizationProvider
     * @throws DataServiceFault
     */
    public static AuthorizationProvider generateAuthProviderFromXMLOMElement(OMElement authorizationProviderElement)
            throws DataServiceFault {
        Class roleRetrieverClass = null;
        String roleRetrieverClassName = null;
        try {
            AuthorizationProvider authorizationProvider;
            roleRetrieverClassName = authorizationProviderElement.getAttributeValue(new QName(
                    DBConstants.AuthorizationProviderConfig.ATTRIBUTE_NAME_CLASS));
            //initialize the roleRetrieverElement
            roleRetrieverClass = Class.forName(roleRetrieverClassName);
            authorizationProvider = (AuthorizationProvider) roleRetrieverClass.newInstance();

            //read the properties in the authenticator element and set them in the authenticator.
            Iterator<OMElement> propertyElements = authorizationProviderElement.getChildrenWithName(new QName(
                    DBSFields.PROPERTY));
            Map<String, String> properties = new HashMap<String, String>();
            if (propertyElements != null) {
                while (propertyElements.hasNext()) {
                    OMElement propertyElement = propertyElements.next();
                    String attributeName = propertyElement.getAttributeValue(new QName(
                            DBSFields.NAME));
                    String attributeValue = propertyElement.getText();
                    properties.put(attributeName, attributeValue);
                }
            }
            authorizationProvider.init(properties);
            return authorizationProvider;
        } catch (ClassNotFoundException e) {
            throw new DataServiceFault(e, "Specified class - " + roleRetrieverClassName + " class cannot be found, Error - " +
                                          e.getMessage());
        } catch (InstantiationException e) {
            throw new DataServiceFault(e, "Initialisation Error for class - " + roleRetrieverClass + " Error - " +
                                          e.getMessage());
        } catch (IllegalAccessException e) {
            throw new DataServiceFault(e, "Illegal access attempt for class - " + roleRetrieverClass + " Error - " +
                                          e.getMessage());
        }
    }

}
