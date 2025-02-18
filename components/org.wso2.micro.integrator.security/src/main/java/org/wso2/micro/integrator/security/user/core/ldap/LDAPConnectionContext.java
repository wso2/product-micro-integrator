/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.security.user.core.ldap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.micro.integrator.security.UnsupportedSecretTypeException;
import org.wso2.micro.integrator.security.user.api.RealmConfiguration;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.core.UserStoreConfigConstants;
import org.wso2.micro.integrator.security.user.core.UserStoreException;
import org.wso2.micro.integrator.security.user.core.dto.CorrelationLogDTO;
import org.wso2.micro.integrator.security.util.Secret;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

public class LDAPConnectionContext {

    private static Log log = LogFactory.getLog(LDAPConnectionContext.class);
    @SuppressWarnings("rawtypes")
    private Hashtable environment;
    private SortedMap<Integer, SRVRecord> dcMap;

    private Hashtable environmentForDNS;

    private String DNSDomainName;

    private boolean readOnly = false;

    private static final String CONNECTION_TIME_OUT = "LDAPConnectionTimeout";

    private static final String READ_TIME_OUT = "ReadTimeout";

    private static final Log correlationLog = LogFactory.getLog("correlation");

    private static String initialContextFactoryClass = "com.sun.jndi.dns.DnsContextFactory";

    private static final String CORRELATION_LOG_CALL_TYPE_VALUE = "ldap";
    private static final String CORRELATION_LOG_INITIALIZATION_METHOD_NAME = "initialization";
    private static final String CORRELATION_LOG_INITIALIZATION_ARGS = "empty";
    private static final int CORRELATION_LOG_INITIALIZATION_ARGS_LENGTH = 0;
    private static final String CORRELATION_LOG_SEPARATOR = "|";
    private static final String CORRELATION_LOG_SYSTEM_PROPERTY = "enableCorrelationLogs";
    public static final String CIRCUIT_STATE_OPEN = "open";
    public static final String CIRCUIT_STATE_CLOSE = "close";

    private String ldapConnectionCircuitBreakerState;
    private long thresholdTimeoutInMilliseconds;
    private long thresholdStartTime;
    private boolean startTLSEnabled;

    static {
        String initialContextFactoryClassSystemProperty = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
        if (initialContextFactoryClassSystemProperty != null && initialContextFactoryClassSystemProperty.length() > 0) {
            initialContextFactoryClass = initialContextFactoryClassSystemProperty;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public LDAPConnectionContext(RealmConfiguration realmConfig) throws UserStoreException {

        //if DNS is enabled, populate DC Map
        String DNSUrl = realmConfig.getUserStoreProperty(LDAPConstants.DNS_URL);
        if (DNSUrl != null) {
            DNSDomainName = realmConfig.getUserStoreProperty(LDAPConstants.DNS_DOMAIN_NAME);
            if (DNSDomainName == null) {
                throw new UserStoreException("DNS is enabled, but DNS domain name not provided.");
            } else {
                environmentForDNS = new Hashtable();
                environmentForDNS.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactoryClass);
                environmentForDNS.put("java.naming.provider.url", DNSUrl);
                populateDCMap();
            }
            //need to keep track of if the user store config is read only
            String readOnlyString = realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY);
            if (readOnlyString != null) {
                readOnly = Boolean.parseBoolean(readOnlyString);
            }
        }

        String rawConnectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
        String connectionURL = null;
        //if DNS enabled in AD case, this can be null
        if (rawConnectionURL != null) {
            String portInfo = rawConnectionURL.split(":")[2];

            String port = null;

            // if the port contains a template string that refers to carbon.xml
            if ((portInfo.contains("${")) && (portInfo.contains("}"))) {
                port = Integer.toString(MicroIntegratorBaseUtils.getPortFromServerConfig(portInfo));
            }

            if (port != null) {
                connectionURL = rawConnectionURL.replace(portInfo, port);
            } else {
                // if embedded-ldap is not enabled,
                connectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
            }
        }

        String connectionName = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_NAME);
        String connectionPassword = realmConfig
                .getUserStoreProperty(LDAPConstants.CONNECTION_PASSWORD);

        if (log.isDebugEnabled()) {
            log.debug("Connection Name :: " + connectionName + ", Connection URL :: " + connectionURL);
        }

        environment = new Hashtable();

        String initialContextFactory = realmConfig.getUserStoreProperty(LDAPConstants.LDAP_INITIAL_CONTEXT_FACTORY);
        if (initialContextFactory == null || initialContextFactory.isEmpty()) {
            initialContextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
        }
        environment.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");

        /**
         * In carbon JNDI context we need to by pass specific tenant context and we need the base
         * context for LDAP operations.
         */
        environment.put(UserCoreConstants.REQUEST_BASE_CONTEXT, "true");

        if (connectionName != null) {
            environment.put(Context.SECURITY_PRINCIPAL, connectionName);
        }

        if (connectionPassword != null) {
            environment.put(Context.SECURITY_CREDENTIALS, connectionPassword);
        }

        if (connectionURL != null) {
            environment.put(Context.PROVIDER_URL, connectionURL);
        }

        // Enable connection pooling if property is set in user-mgt.xml
        boolean isLDAPConnectionPoolingEnabled = false;
        String value = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_POOLING_ENABLED);

        if (value != null && !value.trim().isEmpty()) {
            isLDAPConnectionPoolingEnabled = Boolean.parseBoolean(value);
        }

        environment.put("com.sun.jndi.ldap.connect.pool", isLDAPConnectionPoolingEnabled ? "true" : "false");

        // set referral status if provided in configuration.
        if (realmConfig.getUserStoreProperty(LDAPConstants.PROPERTY_REFERRAL) != null) {
            environment.put("java.naming.referral",
                    realmConfig.getUserStoreProperty(LDAPConstants.PROPERTY_REFERRAL));
        }

        String binaryAttribute = realmConfig.getUserStoreProperty(LDAPConstants.LDAP_ATTRIBUTES_BINARY);

        if (binaryAttribute != null) {
            environment.put(LDAPConstants.LDAP_ATTRIBUTES_BINARY, binaryAttribute);
        }

        //Set connect timeout if provided in configuration. Otherwise set default value
        String connectTimeout = realmConfig.getUserStoreProperty(CONNECTION_TIME_OUT);
        String readTimeout = realmConfig.getUserStoreProperty(READ_TIME_OUT);
        if (connectTimeout != null && !connectTimeout.trim().isEmpty()) {
            environment.put("com.sun.jndi.ldap.connect.timeout", connectTimeout);
        } else {
            environment.put("com.sun.jndi.ldap.connect.timeout", "5000");
        }

        if (StringUtils.isNotEmpty(readTimeout)) {
            environment.put("com.sun.jndi.ldap.read.timeout", readTimeout);
        }

        // Set StartTLS option if provided in the configuration. Otherwise normal connection.
        startTLSEnabled = Boolean.parseBoolean(realmConfig.getUserStoreProperty(
                UserStoreConfigConstants.STARTTLS_ENABLED));

        // Set waiting time to re-establish LDAP connection after couple of failure attempts if specified otherwise
        // set default time.
        String retryWaitingTime = realmConfig.getUserStoreProperty(UserStoreConfigConstants.CONNECTION_RETRY_DELAY);
        if (StringUtils.isNotEmpty(retryWaitingTime)) {
            thresholdTimeoutInMilliseconds = getThresholdTimeoutInMilliseconds(retryWaitingTime);
        } else {
            thresholdTimeoutInMilliseconds = 120000;
        }
        // By-default set to close state.
        ldapConnectionCircuitBreakerState = CIRCUIT_STATE_CLOSE;
        thresholdStartTime = 0;
    }

    public DirContext getContext() throws UserStoreException {

        DirContext context = null;

        // Implemented basic circuit breaker logic to reduce the resource consumption.
        switch (ldapConnectionCircuitBreakerState) {
        case CIRCUIT_STATE_OPEN:
            long circuitOpenDuration = System.currentTimeMillis() - thresholdStartTime;
            if (circuitOpenDuration >= thresholdTimeoutInMilliseconds) {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Trying to obtain LDAP connection, connection URL: " + environment
                                .get(Context.PROVIDER_URL) + " when circuit breaker state: "
                                + ldapConnectionCircuitBreakerState + " and circuit breaker open duration: "
                                + circuitOpenDuration + "ms.");
                    }
                    context = getDirContext();
                    ldapConnectionCircuitBreakerState = CIRCUIT_STATE_CLOSE;
                    thresholdStartTime = 0;
                    break;
                } catch (UserStoreException e) {
                    log.error("Error occurred while obtaining LDAP connection. Connection URL: " + environment
                            .get(Context.PROVIDER_URL), e);
                    thresholdStartTime = System.currentTimeMillis();
                    if (log.isDebugEnabled()) {
                        log.debug("LDAP connection circuit breaker state set to: " + ldapConnectionCircuitBreakerState);
                    }
                    throw new UserStoreException("Error occurred while obtaining LDAP connection.", e);
                }
            } else {
                throw new UserStoreException(
                        "LDAP connection circuit breaker is in open state for " + circuitOpenDuration
                                + "ms and has not reach the threshold timeout: " + thresholdTimeoutInMilliseconds
                                + "ms, hence avoid establishing the LDAP connection.");
            }
        case CIRCUIT_STATE_CLOSE:
            try {
                if (log.isDebugEnabled()) {
                    log.debug("LDAP connection circuit breaker state: " + ldapConnectionCircuitBreakerState
                            + ", so trying to obtain the LDAP connection, connection URL: "
                            + environment.get(Context.PROVIDER_URL));
                }
                context = getDirContext();
                break;
            } catch (UserStoreException e) {
                log.error("Error occurred while obtaining LDAP connection. Connection URL: "
                        + environment.get(Context.PROVIDER_URL), e);
                log.error("Trying again to get connection.");
                try {
                    context = getDirContext();
                    break;
                } catch (Exception e1) {
                    log.error("Error occurred while obtaining connection for the second time.", e1);
                    ldapConnectionCircuitBreakerState = CIRCUIT_STATE_OPEN;
                    thresholdStartTime = System.currentTimeMillis();
                    throw new UserStoreException("Error occurred while obtaining LDAP connection, LDAP connection "
                            + "circuit breaker state set to: " + ldapConnectionCircuitBreakerState, e1);
                }
            }
        default:
            throw new UserStoreException("Unknown LDAP connection circuit breaker state.");
        }
        return context;
    }

    private DirContext getDirContext() throws UserStoreException {

        DirContext context = null;
        //if dcMap is not populated, it is not DNS case
        if (dcMap == null) {
            try {
                context = getLdapContext(environment,null);

            } catch (NamingException e) {
                log.error("Error obtaining connection. " + e.getMessage(), e);
                log.error("Trying again to get connection.");

                try {
                    context = getLdapContext(environment,null);
                } catch (Exception e1) {
                    log.error("Error obtaining connection for the second time" + e.getMessage(), e);
                    throw new UserStoreException("Error obtaining connection. " + e.getMessage(), e);
                }

            }
        } else if (dcMap != null && dcMap.size() != 0) {
            try {
                //first try the first entry in dcMap, if it fails, try iteratively
                Integer firstKey = dcMap.firstKey();
                SRVRecord firstRecord = dcMap.get(firstKey);
                //compose the connection URL
                environment.put(Context.PROVIDER_URL, getLDAPURLFromSRVRecord(firstRecord));

                context = getLdapContext(environment,null);

            } catch (NamingException e) {
                log.error("Error obtaining connection to first Domain Controller." + e.getMessage(), e);
                log.info("Trying to connect with other Domain Controllers");

                for (Integer integer : dcMap.keySet()) {
                    try {
                        SRVRecord srv = dcMap.get(integer);
                        environment.put(Context.PROVIDER_URL, getLDAPURLFromSRVRecord(srv));
                        context = getLdapContext(environment,null);
                        break;
                    } catch (NamingException e1) {
                        if (integer == (dcMap.lastKey())) {
                            log.error("Error obtaining connection for all " + integer + " Domain Controllers."
                                    + e.getMessage(), e);
                            throw new UserStoreException("Error obtaining connection. " + e.getMessage(), e);
                        }
                    }
                }
            }
        }
        return context;
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    public void updateCredential(String connectionPassword) {
        /*
         * update the password otherwise it is not possible to connect again if admin password
         * changed
         */
        this.environment.put(Context.SECURITY_CREDENTIALS, connectionPassword);
    }

    /**
     * Updates the connection password
     *
     * @param connectionPassword
     */
    public void updateCredential(Object connectionPassword) throws UserStoreException {

        /*
         * update the password otherwise it is not possible to connect again if admin password
         * changed
         */
        Secret connectionPasswordObj;
        try {
            connectionPasswordObj = Secret.getSecret(connectionPassword);
        } catch (UnsupportedSecretTypeException e) {
            throw new UserStoreException("Unsupported credential type", e);
        }

        byte[] passwordBytes = connectionPasswordObj.getBytes();
        this.environment.put(Context.SECURITY_CREDENTIALS, Arrays.copyOf(passwordBytes, passwordBytes.length));

        connectionPasswordObj.clear();
    }

    private void populateDCMap() throws UserStoreException {

        try {
            //get the directory context for DNS
            DirContext dnsContext = new InitialDirContext(environmentForDNS);
            //compose the DNS service to be queried
            String DNSServiceName = LDAPConstants.ACTIVE_DIRECTORY_DOMAIN_CONTROLLER_SERVICE + DNSDomainName;
            //query the DNS
            Attributes attributes = dnsContext.getAttributes(DNSServiceName, new String[]{LDAPConstants.SRV_ATTRIBUTE_NAME});
            Attribute srvRecords = attributes.get(LDAPConstants.SRV_ATTRIBUTE_NAME);
            //there can be multiple records with same domain name - get them all
            NamingEnumeration srvValues = srvRecords.getAll();
            dcMap = new TreeMap<Integer, SRVRecord>();
            //extract all SRV Records for _ldap._tcp service under the specified domain and populate dcMap
            //int forcedPriority = 0;
            while (srvValues.hasMore()) {
                String value = srvValues.next().toString();
                SRVRecord srvRecord = new SRVRecord();
                String valueItems[] = value.split(" ");
                String priority = valueItems[0];
                if (priority != null) {
                    int priorityInt = Integer.parseInt(priority);

                    /*if ((priorityInt == forcedPriority) || (priorityInt < forcedPriority)) {
                        forcedPriority++;
                        priorityInt = forcedPriority;
                    }*/
                    srvRecord.setPriority(priorityInt);
                }/* else {
                    forcedPriority++;
                    srvRecord.setPriority(forcedPriority);
                }*/
                String weight = valueItems[1];
                if (weight != null) {
                    srvRecord.setWeight(Integer.parseInt(weight));
                }
                String port = valueItems[2];
                if (port != null) {
                    srvRecord.setPort(Integer.parseInt(port));
                }
                String host = valueItems[3];
                if (host != null) {
                    srvRecord.setHostName(host);
                }
                //we index dcMap on priority basis, therefore, priorities must be different
                dcMap.put(srvRecord.getPriority(), srvRecord);
            }
            //iterate over the SRVRecords for Active Directory Domain Controllers and figure out the
            //host records for that
            for (SRVRecord srvRecord : dcMap.values()) {
                Attributes hostAttributes = dnsContext.getAttributes(
                        srvRecord.getHostName(), new String[]{LDAPConstants.A_RECORD_ATTRIBUTE_NAME});
                Attribute hostRecord = hostAttributes.get(LDAPConstants.A_RECORD_ATTRIBUTE_NAME);
                //we know there is only one IP value for a given host. So we do just get, not getAll
                srvRecord.setHostIP((String) hostRecord.get());
            }
        } catch (NamingException e) {
            log.error("Error obtaining information from DNS Server" + e.getMessage(), e);
            throw new UserStoreException("Error obtaining information from DNS Server " + e.getMessage(), e);
        }
    }

    private String getLDAPURLFromSRVRecord(SRVRecord srvRecord) {

        String ldapURL = null;
        if (readOnly) {
            ldapURL = "ldap://" + srvRecord.getHostIP() + ":" + srvRecord.getPort();
        } else {
            ldapURL = "ldaps://" + srvRecord.getHostIP() + ":" + srvRecord.getPort();
        }
        return ldapURL;
    }

    @Deprecated
    public LdapContext getContextWithCredentials(String userDN, String password)
            throws UserStoreException, NamingException, AuthenticationException {

        LdapContext context = null;

        // Implemented basic circuit breaker logic to reduce the resource consumption.
        switch (ldapConnectionCircuitBreakerState) {
        case CIRCUIT_STATE_OPEN:
            long circuitOpenDuration = System.currentTimeMillis() - thresholdStartTime;
            if (circuitOpenDuration >= thresholdTimeoutInMilliseconds) {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Trying to obtain LDAP connection, connection URL: " + environment
                                .get(Context.PROVIDER_URL) + " when circuit breaker state: "
                                + ldapConnectionCircuitBreakerState + " and circuit breaker open duration: "
                                + circuitOpenDuration + "ms.");
                    }
                    context = getLdapContextWithCredentials(userDN, password);
                    ldapConnectionCircuitBreakerState = CIRCUIT_STATE_CLOSE;
                    thresholdStartTime = 0;
                    break;
                } catch (UserStoreException e) {
                    log.error("Error occurred while obtaining LDAP connection. Connection URL: " + environment
                            .get(Context.PROVIDER_URL), e);
                    thresholdStartTime = System.currentTimeMillis();
                    if (log.isDebugEnabled()) {
                        log.debug("LDAP connection circuit breaker state set to: " + ldapConnectionCircuitBreakerState);
                    }
                    throw new UserStoreException("Error occurred while obtaining LDAP connection.", e);
                }
            } else {
                throw new UserStoreException(
                        "LDAP connection circuit breaker is in open state for " + circuitOpenDuration
                                + "ms and has not reach the threshold timeout: " + thresholdTimeoutInMilliseconds
                                + "ms, hence avoid establishing the LDAP connection.");
            }
        case CIRCUIT_STATE_CLOSE:
            try {
                if (log.isDebugEnabled()) {
                    log.debug("LDAP connection circuit breaker state: " + ldapConnectionCircuitBreakerState
                            + ", so trying to obtain the LDAP connection. " + "Connection URL: "
                            + environment.get(Context.PROVIDER_URL));
                }
                context = getLdapContextWithCredentials(userDN, password);
                break;
            } catch (UserStoreException e) {
                log.error("Error occurred while obtaining LDAP connection. Connection URL: "
                        + environment.get(Context.PROVIDER_URL), e);
                log.error("Trying again to get connection.");
                try {
                    context = getLdapContextWithCredentials(userDN, password);
                    break;
                } catch (Exception e1) {
                    log.error("Error occurred while obtaining connection for the second time.", e1);
                    ldapConnectionCircuitBreakerState = CIRCUIT_STATE_OPEN;
                    thresholdStartTime = System.currentTimeMillis();
                    throw new UserStoreException("Error occurred while obtaining LDAP connection, LDAP connection "
                            + "circuit breaker state set to: " + ldapConnectionCircuitBreakerState, e1);
                }
            }
        default:
            throw new UserStoreException("Unknown LDAP connection circuit breaker state.");
        }
        return context;
    }

    private LdapContext getLdapContextWithCredentials(String userDN, String password)
            throws NamingException, UserStoreException {

        //create a temp env for this particular authentication session by copying the original env
        // following logic help to re use the connection pool in authentication
        Hashtable<String, String> tempEnv = new Hashtable<String, String>();
        for (Object key : environment.keySet()) {
            if (Context.SECURITY_PRINCIPAL.equals((String) key) || Context.SECURITY_CREDENTIALS.equals((String) key)
                    || Context.SECURITY_AUTHENTICATION.equals((String) key)) {
                // skip adding to environment
            } else {
                tempEnv.put((String) key, (String) environment.get(key));
            }
        }

        tempEnv.put(Context.SECURITY_AUTHENTICATION, "none");

        return getContextForEnvironmentVariables(tempEnv);
    }

    /**
     * Returns the LDAPContext for the given credentials
     *
     * @param userDN   user DN
     * @param password user password
     * @return returns The LdapContext instance if credentials are valid
     * @throws UserStoreException
     * @throws NamingException
     */
    public LdapContext getContextWithCredentials(String userDN, Object password)
            throws UserStoreException, NamingException {

        Secret credentialObj;
        try {
            credentialObj = Secret.getSecret(password);
        } catch (UnsupportedSecretTypeException e) {
            throw new UserStoreException("Unsupported credential type", e);
        }

        try {
            //create a temp env for this particular authentication session by copying the original env
            Hashtable<String, Object> tempEnv = new Hashtable<>();
            for (Object key : environment.keySet()) {
                tempEnv.put((String) key, environment.get(key));
            }
            //replace connection name and password with the passed credentials to this method
            tempEnv.put(Context.SECURITY_PRINCIPAL, userDN);
            tempEnv.put(Context.SECURITY_CREDENTIALS, credentialObj.getBytes());

            return getContextForEnvironmentVariables(tempEnv);
        } finally {
            credentialObj.clear();
        }
    }

    private LdapContext getContextForEnvironmentVariables(Hashtable<?, ?> environment)
            throws UserStoreException, NamingException {

        LdapContext context = null;

        Hashtable<Object, Object> tempEnv = new Hashtable<>();
        tempEnv.putAll(environment);
        //if dcMap is not populated, it is not DNS case
        if (dcMap == null) {
            //replace environment properties with these credentials
            context = getLdapContext(tempEnv, null);
        } else if (dcMap != null && dcMap.size() != 0) {
            try {
                //first try the first entry in dcMap, if it fails, try iteratively
                Integer firstKey = dcMap.firstKey();
                SRVRecord firstRecord = dcMap.get(firstKey);
                //compose the connection URL
                tempEnv.put(Context.PROVIDER_URL, getLDAPURLFromSRVRecord(firstRecord));
                context = getLdapContext(tempEnv, null);

            } catch (AuthenticationException e) {
                throw e;
            } catch (NamingException e) {
                log.error("Error obtaining connection to first Domain Controller.", e);
                log.info("Trying to connect with other Domain Controllers");

                for (Integer integer : dcMap.keySet()) {
                    try {
                        SRVRecord srv = dcMap.get(integer);
                        tempEnv.put(Context.PROVIDER_URL, getLDAPURLFromSRVRecord(srv));
                        context = getLdapContext(environment, null);
                        break;
                    } catch (AuthenticationException e1) {
                        throw e1;
                    } catch (NamingException e1) {
                        if (integer == (dcMap.lastKey())) {
                            throw new UserStoreException(
                                    "Error obtaining connection for all " + integer + " Domain Controllers.", e1);
                        }
                    }
                }
            }
        }
        return context;
    }

    /**
     * Creates the proxy for directory context and wrap the context.
     * Calculate the time taken for creation
     *
     * @param environment Used to get provider url and principal
     * @return The wrapped context
     * @throws NamingException
     */
    private DirContext getDirContext(Hashtable<?, ?> environment) throws NamingException {

        if (Boolean.parseBoolean(System.getProperty(CORRELATION_LOG_SYSTEM_PROPERTY))) {
            final Class[] proxyInterfaces = new Class[]{DirContext.class};
            long start = System.currentTimeMillis();

            DirContext context = new InitialDirContext(environment);

            Object proxy = Proxy.newProxyInstance(LDAPConnectionContext.class.getClassLoader(), proxyInterfaces,
                    new LdapContextInvocationHandler(context));

            long delta = System.currentTimeMillis() - start;

            CorrelationLogDTO correlationLogDTO = new CorrelationLogDTO();
            correlationLogDTO.setStartTime(start);
            correlationLogDTO.setDelta(delta);
            correlationLogDTO.setEnvironment(environment);
            correlationLogDTO.setMethodName(CORRELATION_LOG_INITIALIZATION_METHOD_NAME);
            correlationLogDTO.setArgsLength(CORRELATION_LOG_INITIALIZATION_ARGS_LENGTH);
            correlationLogDTO.setArgs(CORRELATION_LOG_INITIALIZATION_ARGS);
            logDetails(correlationLogDTO);
            return (DirContext) proxy;
        } else {
            return new InitialDirContext(environment);
        }
    }

    /**
     * Creates the proxy for LDAP context and wrap the context.
     * Calculate the time taken for creation
     *
     * @param environment        Used to get provider url and principal
     * @param connectionControls The wrapped context
     * @return ldap connection context
     * @throws NamingException
     */
    private LdapContext getLdapContext(Hashtable<?, ?> environment, Control[] connectionControls)
            throws NamingException, UserStoreException {

        if (Boolean.parseBoolean(System.getProperty(CORRELATION_LOG_SYSTEM_PROPERTY))) {
            final Class[] proxyInterfaces = new Class[]{LdapContext.class};
            long start = System.currentTimeMillis();

            LdapContext context = initializeLdapContext(environment, connectionControls);

            Object proxy = Proxy.newProxyInstance(LDAPConnectionContext.class.getClassLoader(), proxyInterfaces,
                    new LdapContextInvocationHandler(context));

            long delta = System.currentTimeMillis() - start;

            CorrelationLogDTO correlationLogDTO = new CorrelationLogDTO();
            correlationLogDTO.setStartTime(start);
            correlationLogDTO.setDelta(delta);
            correlationLogDTO.setEnvironment(environment);
            correlationLogDTO.setMethodName(CORRELATION_LOG_INITIALIZATION_METHOD_NAME);
            correlationLogDTO.setArgsLength(CORRELATION_LOG_INITIALIZATION_ARGS_LENGTH);
            correlationLogDTO.setArgs(CORRELATION_LOG_INITIALIZATION_ARGS);
            logDetails(correlationLogDTO);
            return (LdapContext) proxy;
        } else {
            return initializeLdapContext(environment, connectionControls);
        }
    }

    /**
     * Initialize the LDAP context.
     *
     * @param environment        environment used to create the initial Context.
     * @param connectionControls connection request controls for the initial context.
     * @return ldap connection context.
     * @throws NamingException    if a naming exception is encountered.
     * @throws UserStoreException if a user store related exception is encountered.
     */
    private LdapContext initializeLdapContext(Hashtable<?, ?> environment, Control[] connectionControls)
            throws NamingException, UserStoreException {

        if (startTLSEnabled) {
            return LdapContextWrapper.startTLS(environment, connectionControls);
        } else {
            return new InitialLdapContext(environment, connectionControls);
        }
    }

    /**
     * Proxy Class that is used to calculate and log the time taken for queries
     */
    private class LdapContextInvocationHandler implements InvocationHandler {

        private Object previousContext;

        public LdapContextInvocationHandler(Object previousContext) {

            this.previousContext = previousContext;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            long start = System.currentTimeMillis();
            Object result = method.invoke(this.previousContext, args);
            long delta = System.currentTimeMillis() - start;
            String methodName = method.getName();
            int argsLength = 0;

            if (args != null) {
                argsLength = args.length;
            }

            if (!StringUtils.equalsIgnoreCase("close", methodName)) {
                CorrelationLogDTO correlationLogDTO = new CorrelationLogDTO();
                correlationLogDTO.setStartTime(start);
                correlationLogDTO.setDelta(delta);
                correlationLogDTO.setEnvironment(((DirContext) this.previousContext).getEnvironment());
                correlationLogDTO.setMethodName(methodName);
                correlationLogDTO.setArgsLength(argsLength);
                correlationLogDTO.setArgs(stringify(args));
                logDetails(correlationLogDTO);
            }
            return result;
        }

        /**
         * Creates a argument string by appending the values in the array
         *
         * @param arr Arguments
         * @return Argument string
         */
        private String stringify(Object[] arr) {

            StringBuilder sb = new StringBuilder();
            if (arr == null) {
                sb.append("null");
            } else {
                sb.append(" ");
                for (int i = 0; i < arr.length; i++) {
                    Object o = arr[i];
                    if (o == null){
                        continue;
                    }
                    sb.append(o.toString());
                    if (i < arr.length - 1) {
                        sb.append(",");
                    }
                }
            }
            return sb.toString();
        }
    }

    /**
     * Logs the details from the LDAP query
     *
     * @param correlationLogDTO Contains all details that should be to logged
     */
    private void logDetails(CorrelationLogDTO correlationLogDTO) {

        String providerUrl = "No provider url found";
        String principal = "No principal found";

        if (correlationLogDTO.getEnvironment().containsKey("java.naming.provider.url")) {
            providerUrl = (String) environment.get("java.naming.provider.url");
        }

        if (environment.containsKey("java.naming.security.principal")) {
            principal = (String) environment.get("java.naming.security.principal");
        }

        if (correlationLog.isInfoEnabled()) {
            List<String> logPropertiesList = new ArrayList<>();
            logPropertiesList.add(Long.toString(correlationLogDTO.getDelta()));
            logPropertiesList.add(CORRELATION_LOG_CALL_TYPE_VALUE);
            logPropertiesList.add(Long.toString(correlationLogDTO.getStartTime()));
            logPropertiesList.add(correlationLogDTO.getMethodName());
            logPropertiesList.add(providerUrl);
            logPropertiesList.add(principal);
            logPropertiesList.add(Integer.toString(correlationLogDTO.getArgsLength()));
            logPropertiesList.add(correlationLogDTO.getArgs());
            correlationLog.info(createFormattedLog(logPropertiesList));
        }
    }

    /**
     * Creates the log line that should be printed
     *
     * @param logPropertiesList Contains the log values that should be printed in the log
     * @return The log line
     */
    private String createFormattedLog(List<String> logPropertiesList) {

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String property : logPropertiesList) {
            sb.append(property);
            if (count < logPropertiesList.size() - 1) {
                sb.append(CORRELATION_LOG_SEPARATOR);
            }
            count++;
        }
        return sb.toString();
    }

    /**
     * Convert retry waiting time string to long.
     *
     * @param retryWaitingTime
     * @return
     * @throws UserStoreException
     */
    private long getThresholdTimeoutInMilliseconds(String retryWaitingTime) throws UserStoreException {

        try {
            return Long.parseLong(retryWaitingTime);
        } catch (NumberFormatException e) {
            throw new UserStoreException("Error occurred while parsing ConnectionRetryDelay property value. value: "
                    + UserStoreConfigConstants.CONNECTION_RETRY_DELAY);
        }
    }
}
