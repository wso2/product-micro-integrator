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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.core.Constants;
import org.wso2.micro.integrator.security.UnsupportedSecretTypeException;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.api.Properties;
import org.wso2.micro.integrator.security.user.api.Property;
import org.wso2.micro.integrator.security.user.api.RealmConfiguration;
import org.wso2.micro.integrator.security.user.core.UserRealm;
import org.wso2.micro.integrator.security.user.core.UserStoreConfigConstants;
import org.wso2.micro.integrator.security.user.core.UserStoreException;
import org.wso2.micro.integrator.security.user.core.claim.ClaimManager;
import org.wso2.micro.integrator.security.user.core.common.AbstractUserStoreManager;
import org.wso2.micro.integrator.security.user.core.common.PaginatedSearchResult;
import org.wso2.micro.integrator.security.user.core.common.RoleContext;
import org.wso2.micro.integrator.security.user.core.hybrid.HybridRoleManager;
import org.wso2.micro.integrator.security.user.core.internal.UserStoreMgtDSComponent;
import org.wso2.micro.integrator.security.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.micro.integrator.security.user.core.model.Condition;
import org.wso2.micro.integrator.security.user.core.model.ExpressionAttribute;
import org.wso2.micro.integrator.security.user.core.model.ExpressionCondition;
import org.wso2.micro.integrator.security.user.core.model.ExpressionOperation;
import org.wso2.micro.integrator.security.user.core.model.OperationalCondition;
import org.wso2.micro.integrator.security.user.core.profile.ProfileConfigurationManager;
import org.wso2.micro.integrator.security.user.core.service.RealmService;
import org.wso2.micro.integrator.security.user.core.tenant.Tenant;
import org.wso2.micro.integrator.security.user.core.util.DatabaseUtil;
import org.wso2.micro.integrator.security.user.core.util.JNDIUtil;
import org.wso2.micro.integrator.security.user.core.util.LDAPUtil;
import org.wso2.micro.integrator.security.user.core.util.UserCoreUtil;
import org.wso2.micro.integrator.security.util.Secret;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.CacheBuilder;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.naming.AuthenticationException;
import javax.naming.CompositeName;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.Rdn;
import javax.naming.ldap.SortControl;
import javax.sql.DataSource;

import static org.wso2.micro.integrator.security.user.core.ldap.ActiveDirectoryUserStoreConstants.TRANSFORM_OBJECTGUID_TO_UUID;

public class ReadOnlyLDAPUserStoreManager extends AbstractUserStoreManager {

    public static final String MEMBER_UID = "memberUid";
    private static final String OBJECT_GUID = "objectGUID";
    protected static final String MEMBERSHIP_ATTRIBUTE_RANGE = "MembershipAttributeRange";
    protected static final String MEMBERSHIP_ATTRIBUTE_RANGE_DISPLAY_NAME = "Membership Attribute Range";
    private static final String USER_CACHE_NAME_PREFIX = UserCoreConstants.CachingConstants.LOCAL_CACHE_PREFIX + "UserCache-";
    private static final String USER_CACHE_MANAGER = "UserCacheManager";
    private static Log log = LogFactory.getLog(ReadOnlyLDAPUserStoreManager.class);
    protected static final int MAX_USER_CACHE = 200;

    private static final String MULTI_ATTRIBUTE_SEPARATOR_DESCRIPTION = "This is the separator for multiple claim values";
    private static final String MULTI_ATTRIBUTE_SEPARATOR = "MultiAttributeSeparator";
    private static final ArrayList<Property> RO_LDAP_UM_ADVANCED_PROPERTIES = new ArrayList<Property>();
    private static final String PROPERTY_REFERRAL_IGNORE ="ignore";
    private static final String LDAPConnectionTimeout = "LDAPConnectionTimeout";
    private static final String LDAPConnectionTimeoutDescription = "LDAP Connection Timeout";
    private static final String readTimeout = "ReadTimeout";
    private static final String readTimeoutDescription = "Configure this to define the read timeout for LDAP operations";
    private static final String RETRY_ATTEMPTS = "RetryAttempts";
    private static final String LDAPBinaryAttributesDescription = "Configure this to define the LDAP binary attributes " +
            "seperated by a space. Ex:mpegVideo mySpecialKey";
    protected static final String USER_CACHE_EXPIRY_TIME_ATTRIBUTE_NAME = "User Cache Expiry milliseconds";
    protected static final String USER_DN_CACHE_ENABLED_ATTRIBUTE_NAME = "Enable User DN Cache";
    protected static final String USER_CACHE_EXPIRY_TIME_ATTRIBUTE_DESCRIPTION =
            "Configure the user cache expiry in milliseconds. "
                    + "Values  {0: expire immediately, -1: never expire, '': i.e. empty, system default}.";
    protected static final String USER_DN_CACHE_ENABLED_ATTRIBUTE_DESCRIPTION = "Enables the user cache. Default true,"
            + " Unless set to false. Empty value is interpreted as true.";
    //Authenticating to LDAP via Anonymous Bind
    private static final String USE_ANONYMOUS_BIND = "AnonymousBind";
    protected static final int MEMBERSHIP_ATTRIBUTE_RANGE_VALUE = 0;

    private String cacheExpiryTimeAttribute = ""; //Default: expire with default system wide cache expiry
    private long userDnCacheExpiryTime = 0; //Default: No cache
    private CacheBuilder userDnCacheBuilder = null; //Use cache manager if not null to get cache
    private String userDnCacheName;
    private boolean userDnCacheEnabled = true;
    protected CacheManager cacheManager;
    protected String tenantDomain;

    /**
     * The use of this Map is Deprecated. Please use userDnCache.
     * Retained so that any extended class will function as it used to be.
     */
    @Deprecated
    Map<String, Object> userCache = new ConcurrentHashMap<>(MAX_USER_CACHE);
    protected LDAPConnectionContext connectionSource = null;
    protected String userSearchBase = null;
    protected String groupSearchBase = null;

    /*
     * following is by default true since embedded-ldap allows it. If connected
     * to an external ldap
     * where empty roles not allowed, then following property should be set
     * accordingly in
     * user-mgt.xml
     */
    protected boolean emptyRolesAllowed = false;

    private boolean fileBasedUserStoreMode = false;

    static {
        setAdvancedProperties();
    }

    public ReadOnlyLDAPUserStoreManager() {
    }

    public ReadOnlyLDAPUserStoreManager(RealmConfiguration realmConfig,
                                        Map<String, Object> properties, ClaimManager claimManager,
                                        ProfileConfigurationManager profileManager,
                                        UserRealm realm, Integer tenantId)
            throws UserStoreException {
        this(realmConfig, properties, claimManager, profileManager, realm, tenantId, false);
    }

    /**
     * Constructor with Hybrid Role Manager
     *
     * @param realmConfig
     * @param properties
     * @param claimManager
     * @param profileManager
     * @param realm
     * @param tenantId
     * @throws UserStoreException
     */
    public ReadOnlyLDAPUserStoreManager(RealmConfiguration realmConfig,
                                        Map<String, Object> properties, ClaimManager claimManager,
                                        ProfileConfigurationManager profileManager,
                                        UserRealm realm, Integer tenantId, boolean skipInitData)
            throws UserStoreException {
        if (log.isDebugEnabled()) {
            log.debug("Initialization Started " + System.currentTimeMillis());
        }

        this.realmConfig = realmConfig;
        this.claimManager = claimManager;
        this.userRealm = realm;
        this.tenantId = tenantId;

//		if (isReadOnly() && realmConfig.isPrimary()) {
//			String adminRoleName =
//			                       UserCoreUtil.removeDomainFromName(realmConfig.getAdminRoleName());
//			realmConfig.setAdminRoleName(UserCoreUtil.addInternalDomainName(adminRoleName));
//		}

        // check if required configurations are in the user-mgt.xml
        checkRequiredUserStoreConfigurations();
        Object fileUserStore = ConfigParser.getParsedConfigs().get(UserCoreConstants.FILE_BASED_USER_STORE_AS_PRIMARY);
        if (fileUserStore != null && Boolean.parseBoolean(fileUserStore.toString())) {
            fileBasedUserStoreMode = true;
        }
        if(!fileBasedUserStoreMode) {
            dataSource = (DataSource) properties.get(UserCoreConstants.DATA_SOURCE);
            if (dataSource == null) {
                // avoid returning null
                dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
            }
            if (dataSource == null) {
                throw new UserStoreException("Data Source is null");
            }
            properties.put(UserCoreConstants.DATA_SOURCE, dataSource);

        }
		/*
         * obtain the ldap connection source that was created in
		 * DefaultRealmService.
		 */

        connectionSource = new LDAPConnectionContext(realmConfig);
        DirContext dirContext = null;
        try {
            dirContext = connectionSource.getContext();
            if (this.isReadOnly()) {
                log.info("LDAP connection created successfully in read-only mode");
            }
        } catch (Exception e) {
            // Skipped to throw a UserStoreException and log the error message in-order to successfully initiate and
            // create the user-store object.
            log.error("Cannot create connection to LDAP server. Connection URL: " + realmConfig
                    .getUserStoreProperty(LDAPConstants.CONNECTION_URL) + " Error message: " + e.getMessage());
        } finally {
            JNDIUtil.closeContext(dirContext);
        }
        this.userRealm = realm;

        if (!fileBasedUserStoreMode) {
            this.persistDomain();
        }
        doInitialSetup(fileBasedUserStoreMode);
        if (realmConfig.isPrimary()) {
            addInitialAdminData(Boolean.parseBoolean(realmConfig.getAddAdmin()),
                    !isInitSetupDone());
        }
        /*
         * Initialize user roles cache as implemented in
         * AbstractUserStoreManager
         */
        initUserRolesCache();

        initUserCache();

        if (log.isDebugEnabled()) {
            log.debug("Initialization Ended " + System.currentTimeMillis());
        }
    }

    /**
     * This operates in the pure read-only mode without a connection to a
     * database. No handling of
     * Internal roles.
     */
    public ReadOnlyLDAPUserStoreManager(RealmConfiguration realmConfig, ClaimManager claimManager,
                                        ProfileConfigurationManager profileManager)
            throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Started " + System.currentTimeMillis());
        }
        this.realmConfig = realmConfig;
        this.claimManager = claimManager;

        // check if required configurations are in the user-mgt.xml
        checkRequiredUserStoreConfigurations();

        this.connectionSource = new LDAPConnectionContext(realmConfig);

        try {
            this.dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
        } catch (Exception ex) {
            // datasource is not configured
            if (log.isDebugEnabled()) {
                log.debug("Datasource is not configured for LDAP user store", ex);
            }
            if (dataSource == null) {
                log.warn("Datasource is not configured for LDAP user store. " +
                        "Hybrid role support will not be available.");
            }
        }
        hybridRoleManager =
                new HybridRoleManager(dataSource, tenantId, realmConfig, userRealm);
    }

    /**
     * @throws UserStoreException
     */
    protected void checkRequiredUserStoreConfigurations() throws UserStoreException {

        log.debug("Checking LDAP configurations ");

        String connectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
        String DNSURL = realmConfig.getUserStoreProperty(LDAPConstants.DNS_URL);
        String AnonymousBind = realmConfig.getUserStoreProperty(USE_ANONYMOUS_BIND);

        if ((connectionURL == null || connectionURL.trim().length() == 0) &&
                ((DNSURL == null || DNSURL.trim().length() == 0))) {
            throw new UserStoreException(
                    "Required ConnectionURL property is not set at the LDAP configurations");
        }
        if (!Boolean.parseBoolean(AnonymousBind)) {
            String connectionName = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_NAME);
            if (StringUtils.isEmpty(connectionName)) {
                throw new UserStoreException(
                        "Required ConnectionNme property is not set at the LDAP configurations");
            }
            String connectionPassword =
                    realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_PASSWORD);
            if (StringUtils.isEmpty(connectionPassword)) {
                throw new UserStoreException(
                        "Required ConnectionPassword property is not set at the LDAP configurations");
            }
        }
        userSearchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        if (userSearchBase == null || userSearchBase.trim().length() == 0) {
            throw new UserStoreException(
                    "Required UserSearchBase property is not set at the LDAP configurations");
        }
        String usernameListFilter =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER);
        if (usernameListFilter == null || usernameListFilter.trim().length() == 0) {
            throw new UserStoreException(
                    "Required UserNameListFilter property is not set at the LDAP configurations");
        }

        String usernameSearchFilter =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        if (usernameSearchFilter == null || usernameSearchFilter.trim().length() == 0) {
            throw new UserStoreException(
                    "Required UserNameSearchFilter property is not set at the LDAP configurations");
        }

        String usernameAttribute =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
        if (usernameAttribute == null || usernameAttribute.trim().length() == 0) {
            throw new UserStoreException(
                    "Required UserNameAttribute property is not set at the LDAP configurations");
        }

        writeGroupsEnabled = false;

        // Groups properties
        if (realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.READ_GROUPS_ENABLED) != null) {
            readGroupsEnabled = Boolean.parseBoolean(realmConfig.
                    getUserStoreProperty(UserCoreConstants.RealmConfig.READ_GROUPS_ENABLED));
        }

        if (log.isDebugEnabled()) {
            if (readGroupsEnabled) {
                log.debug("ReadGroups is enabled for " + getMyDomainName());
            } else {
                log.debug("ReadGroups is disabled for " + getMyDomainName());
            }
        }

        if (readGroupsEnabled) {
            groupSearchBase = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);
            if (groupSearchBase == null || groupSearchBase.trim().length() == 0) {
                throw new UserStoreException(
                        "Required GroupSearchBase property is not set at the LDAP configurations");
            }
            String groupNameListFilter =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER);
            if (groupNameListFilter == null || groupNameListFilter.trim().length() == 0) {
                throw new UserStoreException(
                        "Required GroupNameListFilter property is not set at the LDAP configurations");
            }

            String groupNameSearchFilter =
                    realmConfig.getUserStoreProperty(LDAPConstants.ROLE_NAME_FILTER);
            if (groupNameSearchFilter == null || groupNameSearchFilter.trim().length() == 0) {
                throw new UserStoreException(
                        "Required GroupNameSearchFilter property is not set at the LDAP configurations");
            }

            String groupNameAttribute =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
            if (groupNameAttribute == null || groupNameAttribute.trim().length() == 0) {
                throw new UserStoreException(
                        "Required GroupNameAttribute property is not set at the LDAP configurations");
            }
            String memebershipAttribute =
                    realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
            if (memebershipAttribute == null || memebershipAttribute.trim().length() == 0) {
                throw new UserStoreException(
                        "Required MembershipAttribute property is not set at the LDAP configurations");
            }
        }

        // User DN cache properties
        cacheExpiryTimeAttribute = realmConfig.getUserStoreProperty(LDAPConstants.USER_CACHE_EXPIRY_MILLISECONDS);
        String userDnCacheEnabledAttribute = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_CACHE_ENABLED);
        if (StringUtils.isNotEmpty(userDnCacheEnabledAttribute)) {
            userDnCacheEnabled = Boolean.parseBoolean(userDnCacheEnabledAttribute);
        }
    }

    /**
     *
     */
    public boolean doAuthenticate(String userName, Object credential) throws UserStoreException {

        boolean debug = log.isDebugEnabled();


        String failedUserDN = null;

        if (userName == null || credential == null) {
            return false;
        }

        String leadingOrTrailingSpaceAllowedInUserName = realmConfig.getUserStoreProperty(UserCoreConstants
                .RealmConfig.LEADING_OR_TRAILING_SPACE_ALLOWED_IN_USERNAME);
        if (StringUtils.isNotEmpty(leadingOrTrailingSpaceAllowedInUserName)) {
            boolean isSpaceAllowedInUserName = Boolean.parseBoolean(leadingOrTrailingSpaceAllowedInUserName);
            if (log.isDebugEnabled()) {
                log.debug("'LeadingOrTrailingSpaceAllowedInUserName' property is set to : " +
                        isSpaceAllowedInUserName);
            }
            if (!isSpaceAllowedInUserName) {
                if (log.isDebugEnabled()) {
                    log.debug("Leading or trailing spaces are not allowed in username. Hence validating the username" +
                            " against the regex for the user : " + userName);
                }
                // Need to validate the username against the regex.
                if (!checkUserNameValid(userName)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Username validation failed for the user : " + userName);
                    }
                    return false;
                }
            }
        } else {
            // Keeping old behavior for backward-compatibility.
            userName = userName.trim();
        }

        Secret credentialObj;
        try {
            credentialObj = Secret.getSecret(credential);
        } catch (UnsupportedSecretTypeException e) {
            throw new UserStoreException("Unsupported credential type", e);
        }

        if (userName.equals("") || credentialObj.isEmpty()) {
            return false;
        }

        if (debug) {
            log.debug("Authenticating user " + userName);
        }

        try {
            boolean bValue = false;
            // check cached user DN first.
            String name = null;
            LdapName ldn = getFromUserCache(userName);
            if (ldn != null) {
                name = ldn.toString();
                try {
                    if (debug) {
                        log.debug("Cache hit. Using DN " + name);
                    }
                    bValue = this.bindAsUser(userName, name, credentialObj);
                } catch (NamingException e) {
                    // do nothing if bind fails since we check for other DN
                    // patterns as well.
                    if (log.isDebugEnabled()) {
                        log.debug("Checking authentication with UserDN " + name + "failed " + e.getMessage(), e);
                    }
                }

                if (bValue) {
                    return bValue;
                }
                // we need not check binding for this name again, so store this and check
                failedUserDN = name;

            }
            // read DN patterns from user-mgt.xml
            String patterns = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);

            if (patterns != null && !patterns.isEmpty()) {

            if (debug) {
                log.debug("Using UserDNPatterns " + patterns);
            }

            // if the property is present, split it using # to see if there are
            // multiple patterns specified.
            String[] userDNPatternList = patterns.split("#");
            if (userDNPatternList.length > 0) {
                for (String userDNPattern : userDNPatternList) {
                    name = MessageFormat.format(userDNPattern, escapeSpecialCharactersForDN(userName));
                    // check if the same name is found and checked from cache
                    if (failedUserDN != null && failedUserDN.equalsIgnoreCase(name)) {
                        continue;
                    }

                        if (debug) {
                            log.debug("Authenticating with " + name);
                        }
                        try {
                            if (name != null) {
                                bValue = this.bindAsUser(userName, name, credentialObj);
                                if (bValue) {
                                    LdapName ldapName = new LdapName(name);
                                    putToUserCache(userName, ldapName);
                                    break;
                                }
                            }
                        } catch (NamingException e) {
                            // do nothing if bind fails since we check for other DN
                            // patterns as well.
                            if (log.isDebugEnabled()) {
                                log.debug("Checking authentication with UserDN " + userDNPattern +
                                          "failed " + e.getMessage(), e);
                            }
                        }
                    }
                }
            } else {
                name = getNameInSpaceForUsernameFromLDAP(userName);
                try {
                    if (name != null) {
                        // if it is the same user DN found in the cache no need of futher authentication required.
                        if (failedUserDN == null || !failedUserDN.equalsIgnoreCase(name)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Authenticating with " + name);
                            }
                            bValue = this.bindAsUser(userName, name, credentialObj);
                            if (bValue) {
                                LdapName ldapName = new LdapName(name);
                                putToUserCache(userName, ldapName);
                            }
                        }
                    }
                } catch (NamingException e) {
                    String errorMessage = "Cannot bind user : " + userName;
                    if (log.isDebugEnabled()) {
                        log.debug(errorMessage, e);
                    }
                    throw new UserStoreException(errorMessage, e);
                }
            }

            return bValue;
        } finally {
            credentialObj.clear();
        }
    }

    /**
     * We do not have multiple profile support with LDAP.
     */
    public String[] getAllProfileNames() throws UserStoreException {
        return new String[]{UserCoreConstants.DEFAULT_PROFILE};
    }

    /**
     * We do not have multiple profile support with LDAP.
     */
    public String[] getProfileNames(String userName) throws UserStoreException {
        return new String[]{UserCoreConstants.DEFAULT_PROFILE};
    }

    /**
     *
     */
    public Map<String, String> getUserPropertyValues(String userName, String[] propertyNames,
                                                     String profileName) throws UserStoreException {
        if (userName == null) {
            throw new UserStoreException("userName value is null.");
        }
        String userAttributeSeparator = ",";
        String userDN = null;
        LdapName ldn = getFromUserCache(userName);

        if (ldn == null) {
            // read list of patterns from user-mgt.xml
            String patterns = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);

            if (patterns != null && !patterns.isEmpty()) {

                if (log.isDebugEnabled()) {
                    log.debug("Using User DN Patterns " + patterns);
                }

                if (patterns.contains("#")) {
                    userDN = getNameInSpaceForUserName(userName);
                } else {
                    userDN = MessageFormat.format(patterns, escapeSpecialCharactersForDN(userName));
                }
            }
        } else {
            userDN = ldn.toString();
        }

        Map<String, String> values = new HashMap<String, String>();
        // if user name contains domain name, remove domain name
        String[] userNames = userName.split(UserCoreConstants.DOMAIN_SEPARATOR);
        if (userNames.length > 1) {
            userName = userNames[1];
        }

        DirContext dirContext = this.connectionSource.getContext();
        String userSearchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        String searchFilter = userSearchFilter.replace("?", escapeSpecialCharactersForFilter(userName));

        NamingEnumeration<?> answer = null;
        NamingEnumeration<?> attrs = null;
        try {
            if (userDN != null) {
                SearchControls searchCtls = new SearchControls();
                searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
                if (propertyNames != null && propertyNames.length > 0) {
                    searchCtls.setReturningAttributes(propertyNames);
                }
                if (log.isDebugEnabled()) {
                    try {
                        log.debug("Searching for user with SearchFilter: " + searchFilter + " in SearchBase: " + dirContext.getNameInNamespace());
                    } catch (NamingException e) {
                        log.debug("Error while getting DN of search base", e);
                    }
                    if (propertyNames == null) {
                        log.debug("No attributes requested");
                    } else {
                        for (String attribute : propertyNames) {
                            log.debug("Requesting attribute :" + attribute);
                        }
                    }
                }
                try {
                    answer = dirContext.search(escapeDNForSearch(userDN), searchFilter, searchCtls);
                } catch (PartialResultException e) {
                    // can be due to referrals in AD. so just ignore error
                    String errorMessage = "Error occurred while searching directory context for user : " + userDN + " searchFilter : " + searchFilter;
                    if (isIgnorePartialResultException()) {
                        if (log.isDebugEnabled()) {
                            log.debug(errorMessage, e);
                        }
                    } else {
                        throw new UserStoreException(errorMessage, e);
                    }
                } catch (NamingException e) {
                    String errorMessage = "Error occurred while searching directory context for user : " + userDN + " searchFilter : " + searchFilter;
                    if (log.isDebugEnabled()) {
                        log.debug(errorMessage, e);
                    }
                    throw new UserStoreException(errorMessage, e);
                }
            } else {
                answer = this.searchForUser(searchFilter, propertyNames, dirContext);
            }
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                Attributes attributes = sr.getAttributes();
                if (attributes != null) {
                    for (String name : propertyNames) {
                        if (name != null) {
                            Attribute attribute = attributes.get(name);
                            if (attribute != null) {
                                StringBuffer attrBuffer = new StringBuffer();
                                for (attrs = attribute.getAll(); attrs.hasMore(); ) {
                                    Object attObject = attrs.next();
                                    String attr = null;
                                    if (attObject instanceof String) {
                                        attr = (String) attObject;
                                    } else if (attObject instanceof byte[]) {
                                        // return canonical representation of UUIDs or base64 encoded string of other binary data
                                        // Active Directory attribute: objectGUID
                                        // RFC 4530 attribute: entryUUID
                                        final byte[] bytes = (byte[]) attObject;
                                        if (bytes.length == 16 && name.endsWith("UID")) {
                                            // objectGUID byte order is not big-endian
                                            // https://msdn.microsoft.com/en-us/library/aa373931%28v=vs.85%29.aspx
                                            // https://community.oracle.com/thread/1157698
                                            if (name.equals(OBJECT_GUID)) {
                                                // check the property for objectGUID transformation
                                                String property =
                                                        realmConfig.getUserStoreProperty(TRANSFORM_OBJECTGUID_TO_UUID);

                                                boolean transformObjectGuidToUuid = StringUtils.isEmpty(property) ||
                                                        Boolean.parseBoolean(property);

                                                if (transformObjectGuidToUuid) {
                                                    final ByteBuffer bb = ByteBuffer.wrap(swapBytes(bytes));
                                                    attr = new java.util.UUID(bb.getLong(), bb.getLong()).toString();
                                                } else {
                                                    // Ignore transforming objectGUID to UUID canonical format
                                                    attr = new String(Base64.encodeBase64((byte[]) attObject));
                                                }
                                            }
                                        } else {
                                            attr = new String(Base64.encodeBase64((byte[]) attObject));
                                        }
                                    }

                                    if (attr != null && attr.trim().length() > 0) {
                                        String attrSeparator = realmConfig.getUserStoreProperty(MULTI_ATTRIBUTE_SEPARATOR);
                                        if (attrSeparator != null && !attrSeparator.trim().isEmpty()) {
                                            userAttributeSeparator = attrSeparator;
                                        }
                                        attrBuffer.append(attr + userAttributeSeparator);
                                    }
                                    String value = attrBuffer.toString();

                                /*
                                 * Length needs to be more than userAttributeSeparator.length() for a valid
                                 * attribute, since we
                                 * attach userAttributeSeparator
                                 */
                                    if (value != null && value.trim().length() > userAttributeSeparator.length()) {
                                        value = value.substring(0, value.length() - userAttributeSeparator.length());
                                        values.put(name, value);
                                    }

                                }
                            }
                        }
                    }
                }
            }

        } catch (NamingException e) {
            String errorMessage = "Error occurred while getting user property values for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            // close the naming enumeration and free up resources
            JNDIUtil.closeNamingEnumeration(attrs);
            JNDIUtil.closeNamingEnumeration(answer);
            // close directory context
            JNDIUtil.closeContext(dirContext);
        }
        return values;
    }

    /**
     *
     */
    public boolean doCheckExistingRole(String roleName) throws UserStoreException {

        RoleContext roleContext = createRoleContext(roleName);  // TODO if role Name with Shared Role?
        return isExistingLDAPRole(roleContext);

    }

    protected boolean isExistingLDAPRole(RoleContext context) throws UserStoreException {

        boolean debug = log.isDebugEnabled();
        boolean isExisting = false;
        String roleName = context.getRoleName();

        if (debug) {
            log.debug("Searching for role: " + roleName);
        }
        String searchFilter = ((LDAPRoleContext) context).getListFilter();
        String roleNameProperty = ((LDAPRoleContext) context).getRoleNameProperty();
        searchFilter = "(&" + searchFilter + "(" + roleNameProperty + "=" + escapeSpecialCharactersForFilter(roleName) + "))";
        String searchBases = ((LDAPRoleContext) context).getSearchBase();

        if (debug) {
            log.debug("Using search filter: " + searchFilter);
        }
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        searchCtls.setReturningAttributes(new String[]{roleNameProperty});
        NamingEnumeration<SearchResult> answer = null;
        DirContext dirContext = null;

        try {
            dirContext = connectionSource.getContext();
            // with DN patterns
            if (((LDAPRoleContext) context).getRoleDNPatterns().size() > 0) {
                for (String pattern : ((LDAPRoleContext) context).getRoleDNPatterns()) {
                    if (debug) {
                        log.debug("Using pattern: " + pattern);
                    }
                    pattern = MessageFormat.format(pattern.trim(), escapeSpecialCharactersForDN(roleName));
                    try {
                        answer = dirContext.search(escapeDNForSearch(pattern), searchFilter, searchCtls);
                    } catch (NamingException e) {
                        if (log.isDebugEnabled()) {
                            log.debug(e);
                        }
                        // ignore
                    }
                    if (answer != null && answer.hasMoreElements()) {
                        return true;
                    }
                }
            }
            //try out with handle multiple search bases
            String[] roleSearchBaseArray = searchBases.split("#");
            for (String searchBase : roleSearchBaseArray) {
                // no DN Patterns found
                if (debug) {
                    log.debug("Searching in " + searchBase);
                }
                try {
                    answer = dirContext.search(escapeDNForSearch(searchBase), searchFilter, searchCtls);
                    if (answer.hasMoreElements()) {
                        isExisting = true;
                        break;
                    }
                } catch (NamingException e) {
                    if (log.isDebugEnabled()) {
                        log.debug(e);
                    }
                    // ignore
                }
            }
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }
        if (debug) {
            log.debug("Is role: " + roleName + " exist: " + isExisting);
        }
        return isExisting;
    }

    public boolean doCheckExistingUser(String userName) throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Searching for user " + userName);
        }

        if (userName == null) {
            return false;
        }

        boolean bFound = false;
        String userSearchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        userSearchFilter = userSearchFilter.replace("?", escapeSpecialCharactersForFilter(userName));
        try {
            String searchBase = null;
            String userDN = null;
            LdapName ldn = getFromUserCache(userName);
            if(ldn == null){
                String userDNPattern = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);
                if (userDNPattern != null && userDNPattern.trim().length() > 0) {
                    String[] patterns = userDNPattern.split("#");
                    for (String pattern : patterns) {
                        searchBase = MessageFormat.format(pattern, escapeSpecialCharactersForDN(userName));
                        userDN = getNameInSpaceForUserName(userName, searchBase, userSearchFilter);
                        if (userDN != null && userDN.length() > 0) {
                            bFound = true;
                            LdapName ldapName = new LdapName(userDN);
                            putToUserCache(userName, ldapName);
                            break;
                        }
                    }
                }
            } else {
                userDN = ldn.toString();
                searchBase = MessageFormat.format(userDN, escapeSpecialCharactersForDN(userName));
                userDN = getNameInSpaceForUserName(userName, searchBase, userSearchFilter);
                if (userDN != null && userDN.length() > 0) {
                    bFound = true;
                } else {
                    removeFromUserCache(userName);
                }
            }
            if(!bFound){
                searchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
                userDN = getNameInSpaceForUserName(userName, searchBase, userSearchFilter);
                if(userDN != null && userDN.length() > 0){
                    bFound = true;
                }
            }
        } catch (Exception e) {
            String errorMessage = "Error occurred while checking existence of user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        }
        if (log.isDebugEnabled()) {
            log.debug("User: " + userName + " exist: " + bFound);
        }
        return bFound;
    }

    /**
     *
     */
    public String[] doListUsers(String filter, int maxItemLimit) throws UserStoreException {
        boolean debug = log.isDebugEnabled();
        String[] userNames = new String[0];

        if (maxItemLimit == 0) {
            return userNames;
        }

        int givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        int searchTime = UserCoreConstants.MAX_SEARCH_TIME;

        try {
            givenMax =
                    Integer.parseInt(realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST));
        } catch (Exception e) {
            givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        }

        try {
            searchTime =
                    Integer.parseInt(realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_SEARCH_TIME));
        } catch (Exception e) {
            searchTime = UserCoreConstants.MAX_SEARCH_TIME;
        }

        if (maxItemLimit < 0 || maxItemLimit > givenMax) {
            maxItemLimit = givenMax;
        }

        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        searchCtls.setCountLimit(maxItemLimit);
        searchCtls.setTimeLimit(searchTime);

        if (filter.contains("?") || filter.contains("**")) {
            throw new UserStoreException(
                    "Invalid character sequence entered for user serch. Please enter valid sequence.");
        }

        StringBuffer searchFilter =
                new StringBuffer(
                        realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER));
        String searchBases = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);

        String userNameProperty =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);

        String serviceNameAttribute = "sn";

        StringBuffer finalFilter = new StringBuffer();

        // read the display name attribute - if provided
        String displayNameAttribute =
                realmConfig.getUserStoreProperty(LDAPConstants.DISPLAY_NAME_ATTRIBUTE);

        String[] returnedAtts = null;

        if (StringUtils.isNotEmpty(displayNameAttribute)) {
            returnedAtts =
                    new String[]{userNameProperty, serviceNameAttribute,
                            displayNameAttribute};
            finalFilter.append("(&").append(searchFilter).append("(").append(displayNameAttribute)
                    .append("=").append(escapeSpecialCharactersForFilterWithStarAsRegex(filter)).append("))");
        } else {
            returnedAtts = new String[]{userNameProperty, serviceNameAttribute};
            finalFilter.append("(&").append(searchFilter).append("(").append(userNameProperty).append("=")
                    .append(escapeSpecialCharactersForFilterWithStarAsRegex(filter)).append("))");
        }

        if (debug) {
            log.debug("Listing users. SearchBase: " + searchBases + " Constructed-Filter: " + finalFilter.toString());
            log.debug("Search controls. Max Limit: " + maxItemLimit + " Max Time: " + searchTime);
        }

        searchCtls.setReturningAttributes(returnedAtts);
        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;
        List<String> list = new ArrayList<String>();

        try {
            dirContext = connectionSource.getContext();
            // handle multiple search bases
            String[] searchBaseArray = searchBases.split("#");

            for (String searchBase : searchBaseArray) {

                answer = dirContext.search(escapeDNForSearch(searchBase), finalFilter.toString(), searchCtls);

                while (answer.hasMoreElements()) {
                    SearchResult sr = (SearchResult) answer.next();
                    if (sr.getAttributes() != null) {
                        log.debug("Result found ..");
                        Attribute attr = sr.getAttributes().get(userNameProperty);

						/*
						 * If this is a service principle, just ignore and
						 * iterate rest of the array. The entity is a service if
						 * value of surname is Service
						 */
                        Attribute attrSurname = sr.getAttributes().get(serviceNameAttribute);

                        if (attrSurname != null) {
                            if (debug) {
                                log.debug(serviceNameAttribute + " : " + attrSurname);
                            }
                            String serviceName = (String) attrSurname.get();
                            if (serviceName != null
                                    && serviceName
                                    .equals(LDAPConstants.SERVER_PRINCIPAL_ATTRIBUTE_VALUE)) {
                                continue;
                            }
                        }

						/*
						 * if display name is provided, read that attribute
						 */
                        Attribute displayName = null;
                        if (StringUtils.isNotEmpty(displayNameAttribute)) {
                            displayName = sr.getAttributes().get(displayNameAttribute);
                            if (debug) {
                                log.debug(displayNameAttribute + " : " + displayName);
                            }
                        }

                        if (attr != null) {
                            String name = (String) attr.get();
                            String display = null;
                            if (displayName != null) {
                                display = (String) displayName.get();
                            }
                            // append the domain if exist
                            String domain = this.getRealmConfiguration().getUserStoreProperty(
                                    UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                            // get the name in the format of
                            // domainName/userName|domainName/displayName
                            name = UserCoreUtil.getCombinedName(domain, name, display);
                            list.add(name);
                        }
                    }
                }
            }
            userNames = list.toArray(new String[list.size()]);
            Arrays.sort(userNames);

            if (debug) {
                for (String username : userNames) {
                    log.debug("result: " + username);
                }
            }
        } catch (PartialResultException e) {
            // can be due to referrals in AD. so just ignore error
            String errorMessage =
                    "Error occurred while getting user list for filter : " + filter + "max limit : " + maxItemLimit;
            if (isIgnorePartialResultException()) {
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e);
                }
            } else {
                throw new UserStoreException(errorMessage, e);
            }
        } catch (NamingException e) {
            String errorMessage =
                    "Error occurred while getting user list for filter : " + filter + "max limit : " + maxItemLimit;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }
        return userNames;
    }

    @Override
    protected String[] doGetDisplayNamesForInternalRole(String[] userNames)
            throws UserStoreException {
        // search the user with UserNameAttribute, retrieve their
        // DisplayNameAttribute combine and return
        String displayNameAttribute =
                this.realmConfig.getUserStoreProperty(LDAPConstants.DISPLAY_NAME_ATTRIBUTE);
        if (StringUtils.isNotEmpty(displayNameAttribute)) {
            String userNameAttribute =
                    this.realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
            String userSearchBase =
                    this.realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
            String userNameListFilter =
                    this.realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER);

            String[] returningAttributes = {displayNameAttribute};
            SearchControls searchControls = new SearchControls();
            searchControls.setReturningAttributes(returningAttributes);

            List<String> combinedNames = new ArrayList<String>();
            if (userNames != null && userNames.length > 0) {
                for (String userName : userNames) {
                    String searchFilter =
                            "(&" + userNameListFilter + "(" + userNameAttribute +
                                    "=" + escapeSpecialCharactersForFilter(userName) + "))";
                    List<String> displayNames =
                            this.getListOfNames(userSearchBase, searchFilter,
                                    searchControls,
                                    displayNameAttribute, false);
                    // we expect only one display name
                    if (displayNames != null && !displayNames.isEmpty()) {
                        String name = UserCoreUtil.getCombinedName(this.realmConfig.getUserStoreProperty(
                                UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME), userName, displayNames.get(0));
                        combinedNames.add(name);
                    }
                }
                return combinedNames.toArray(new String[combinedNames.size()]);
            } else {
                return userNames;
            }
        } else {
            return userNames;
        }
    }

    /**
     * @param dn
     * @param credentials
     * @return
     * @throws NamingException
     * @throws UserStoreException
     */
    protected boolean bindAsUser(String dn, String credentials) throws NamingException,
            UserStoreException {
        boolean isAuthed = false;
        boolean debug = log.isDebugEnabled();

		/*
		 * Hashtable<String, String> env = new Hashtable<String, String>();
		 * env.put(Context.INITIAL_CONTEXT_FACTORY, LDAPConstants.DRIVER_NAME);
		 * env.put(Context.SECURITY_PRINCIPAL, dn);
		 * env.put(Context.SECURITY_CREDENTIALS, credentials);
		 * env.put("com.sun.jndi.ldap.connect.pool", "true");
		 */
        /**
         * In carbon JNDI context we need to by pass specific tenant context and
         * we need the base
         * context for LDAP operations.
         */
        // env.put(CarbonConstants.REQUEST_BASE_CONTEXT, "true");

		/*
		 * String rawConnectionURL =
		 * realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
		 * String portInfo = rawConnectionURL.split(":")[2];
		 * 
		 * String connectionURL = null;
		 * String port = null;
		 * // if the port contains a template string that refers to carbon.xml
		 * if ((portInfo.contains("${")) && (portInfo.contains("}"))) {
		 * port =
		 * Integer.toString(CarbonUtils.getPortFromServerConfig(portInfo));
		 * connectionURL = rawConnectionURL.replace(portInfo, port);
		 * }
		 * if (port == null) { // if not enabled, read LDAP url from
		 * user.mgt.xml
		 * connectionURL =
		 * realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
		 * }
		 */
		/*
		 * env.put(Context.PROVIDER_URL, connectionURL);
		 * env.put(Context.SECURITY_AUTHENTICATION, "simple");
		 */

        LdapContext cxt = null;
        try {
            // cxt = new InitialLdapContext(env, null);
            int retries;
            boolean retry;
            try {
                retries = Integer.parseInt(realmConfig.getUserStoreProperty("RetryAttempts"));
            } catch (NumberFormatException | NullPointerException e) {
                retries = 0;
            }
            do {
                retries--;
                retry = false;
                try {
                    cxt = this.connectionSource.getContextWithCredentials(dn, credentials);
                    isAuthed = true;
                } catch (UserStoreException e) {
                    if (e.getMessage().contains("TimeLimitExceeded")) {
                       retry = true;
                    }
                }
            } while (retry && (retries >= 0));
        } catch (AuthenticationException e) {
			/*
			 * StringBuilder stringBuilder = new
			 * StringBuilder("Authentication failed for user ");
			 * stringBuilder.append(dn).append(" ").append(e.getMessage());
			 */

            // we avoid throwing an exception here since we throw that exception
            // in a one level above this.
            if (debug) {
                log.debug("Authentication failed " + e);
            }

        } finally {
            JNDIUtil.closeContext(cxt);
        }

        if (debug) {
            log.debug("User: " + dn + " is authenticated: " + isAuthed);
        }
        return isAuthed;
    }

    /**
     * @param userName
     * @param dn
     * @param credentials
     * @return
     * @throws NamingException
     * @throws UserStoreException
     */
    private boolean bindAsUser(String userName, String dn, Object credentials) throws NamingException,
            UserStoreException {
        boolean isAuthed = false;
        boolean debug = log.isDebugEnabled();

		/*
		 * Hashtable<String, String> env = new Hashtable<String, String>();
		 * env.put(Context.INITIAL_CONTEXT_FACTORY, LDAPConstants.DRIVER_NAME);
		 * env.put(Context.SECURITY_PRINCIPAL, dn);
		 * env.put(Context.SECURITY_CREDENTIALS, credentials);
		 * env.put("com.sun.jndi.ldap.connect.pool", "true");
		 */
        /**
         * In carbon JNDI context we need to by pass specific tenant context and
         * we need the base
         * context for LDAP operations.
         */
        // env.put(CarbonConstants.REQUEST_BASE_CONTEXT, "true");

		/*
		 * String rawConnectionURL =
		 * realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
		 * String portInfo = rawConnectionURL.split(":")[2];
		 *
		 * String connectionURL = null;
		 * String port = null;
		 * // if the port contains a template string that refers to carbon.xml
		 * if ((portInfo.contains("${")) && (portInfo.contains("}"))) {
		 * port =
		 * Integer.toString(CarbonUtils.getPortFromServerConfig(portInfo));
		 * connectionURL = rawConnectionURL.replace(portInfo, port);
		 * }
		 * if (port == null) { // if not enabled, read LDAP url from
		 * user.mgt.xml
		 * connectionURL =
		 * realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
		 * }
		 */
		/*
		 * env.put(Context.PROVIDER_URL, connectionURL);
		 * env.put(Context.SECURITY_AUTHENTICATION, "simple");
		 */

        LdapContext cxt = null;
        try {
            // cxt = new InitialLdapContext(env, null);
            cxt = this.connectionSource.getContextWithCredentials(dn, credentials);
            isAuthed = true;
        } catch (AuthenticationException e) {
			/*
			 * StringBuilder stringBuilder = new
			 * StringBuilder("Authentication failed for user ");
			 * stringBuilder.append(dn).append(" ").append(e.getMessage());
			 */

            // we avoid throwing an exception here since we throw that exception
            // in a one level above this.
            if (debug) {
                log.debug("Authentication failed " + e);
                log.debug("Clearing cache for DN: " + dn);
            }
            if (userName != null) {
                removeFromUserCache(userName);
            }

        } finally {
            JNDIUtil.closeContext(cxt);
        }

        if (debug) {
            log.debug("User: " + dn + " is authnticated: " + isAuthed);
        }
        return isAuthed;
    }


    /**
     * @param searchFilter
     * @param returnedAtts
     * @param dirContext
     * @return
     * @throws UserStoreException
     */
    protected NamingEnumeration<SearchResult> searchForUser(String searchFilter,
                                                            String[] returnedAtts,
                                                            DirContext dirContext)
            throws UserStoreException {
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        String searchBases = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        if (returnedAtts != null && returnedAtts.length > 0) {
            searchCtls.setReturningAttributes(returnedAtts);
        }

        if (log.isDebugEnabled()) {
            try {
                log.debug("Searching for user with SearchFilter: " + searchFilter + " in SearchBase: " + dirContext.getNameInNamespace());
            } catch (NamingException e) {
                log.debug("Error while getting DN of search base", e);
            }
            if (returnedAtts == null) {
                log.debug("No attributes requested");
            } else {
                for (String attribute : returnedAtts) {
                    log.debug("Requesting attribute :" + attribute);
                }
            }
        }

        String[] searchBaseAraay = searchBases.split("#");
        NamingEnumeration<SearchResult> answer = null;

        try {
            for (String searchBase : searchBaseAraay) {
                answer = dirContext.search(escapeDNForSearch(searchBase), searchFilter, searchCtls);
                if (answer.hasMore()) {
                    return answer;
                }
            }
        } catch (PartialResultException e) {
            // can be due to referrals in AD. so just ignore error
            String errorMessage ="Error occurred while search user for filter : " + searchFilter;
            if (isIgnorePartialResultException()) {
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e);
                }
            } else {
                throw new UserStoreException(errorMessage, e);
            }
        } catch (NamingException e) {
            String errorMessage ="Error occurred while search user for filter : " + searchFilter;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        }
        return answer;
    }


    /**
     *
     */
    public void doAddRole(String roleName, String[] userList, boolean shared)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     *
     */
    public void doUpdateRoleName(String roleName, String newRoleName) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

     /**
     * This method is to check whether multiple profiles are allowed with a
     * particular user-store.
     * For an example, currently, JDBC user store supports multiple profiles and
     * where as ApacheDS
     * does not allow. LDAP currently does not allow multiple profiles.
     *
     * @return boolean
     */
    public boolean isMultipleProfilesAllowed() {
        return false;
    }

    /**
     *
     */
    public void doDeleteRole(String roleName) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     * Returns the list of role names for the given search base and other
     * parameters
     *
     * @param searchTime
     * @param filter
     * @param maxItemLimit
     * @param searchFilter
     * @param roleNameProperty
     * @param searchBase
     * @param appendTenantDomain
     * @return
     * @throws UserStoreException
     */
    protected List<String> getLDAPRoleNames(int searchTime, String filter, int maxItemLimit,
                                            String searchFilter, String roleNameProperty,
                                            String searchBase, boolean appendTenantDomain)
            throws UserStoreException {
        boolean debug = log.isDebugEnabled();
        List<String> roles = new ArrayList<String>();

        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        searchCtls.setCountLimit(maxItemLimit);
        searchCtls.setTimeLimit(searchTime);

        String returnedAtts[] = {roleNameProperty};
        searchCtls.setReturningAttributes(returnedAtts);

        // / search filter TODO
        StringBuffer finalFilter = new StringBuffer();
        finalFilter.append("(&").append(searchFilter).append("(").append(roleNameProperty).append("=")
                .append(escapeSpecialCharactersForFilterWithStarAsRegex(filter)).append("))");

        if (debug) {
            log.debug("Listing roles. SearchBase: " + searchBase + " ConstructedFilter: " +
                    finalFilter.toString());
        }

        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;

        try {
            dirContext = connectionSource.getContext();
            answer = dirContext.search(escapeDNForSearch(searchBase), finalFilter.toString(), searchCtls);
            // append the domain if exist
            String domain =
                    this.getRealmConfiguration()
                            .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);

            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                if (sr.getAttributes() != null) {
                    Attribute attr = sr.getAttributes().get(roleNameProperty);
                    if (attr != null) {
                        String name = (String) attr.get();
                        name = UserCoreUtil.addDomainToName(name, domain);
                        if (appendTenantDomain) {
                            String dn = sr.getNameInNamespace();
                            name = UserCoreUtil.addTenantDomainToEntry(name,
                                    getTenantDomainFromRoleDN(dn, name));
                        }
                        roles.add(name);
                    }
                }
            }
        } catch (PartialResultException e) {
            // can be due to referrals in AD. so just ignore error
            String errorMessage = "Error occurred while getting LDAP role names. SearchBase: " + searchBase + " ConstructedFilter: " +
            finalFilter.toString();
            if (isIgnorePartialResultException()) {
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e);
                }
            } else {
                throw new UserStoreException(errorMessage, e);
            }
        } catch (NamingException e) {
            String errorMessage = "Error occurred while getting LDAP role names. SearchBase: " + searchBase + " ConstructedFilter: " +
                                  finalFilter.toString();
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }

        if (debug) {
            Iterator<String> rolesIte = roles.iterator();
            while (rolesIte.hasNext()) {
                log.debug("result: " + rolesIte.next());
            }
        }

        return roles;
    }

    /**
     * Get the tenant domain for the provided distinguished name. If the role is
     * not a shared role returns the super tenant domain
     *
     * @param dn
     * @param roleName
     * @return
     */
    private String getTenantDomainFromRoleDN(String dn, String roleName) {

        dn = dn.toLowerCase();
        roleName = roleName.toLowerCase();
        String sharedSearchBase = realmConfig.getUserStoreProperties().
                get(LDAPConstants.SHARED_GROUP_SEARCH_BASE);

        sharedSearchBase = sharedSearchBase.toLowerCase();
        if (dn.indexOf(sharedSearchBase) > -1) {
            dn = dn.replaceAll(sharedSearchBase, "");
            dn = dn.replace(realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_ATTRIBUTE).
                    toLowerCase() + "=" + roleName, "");
            if (dn.indexOf(",") == 0) {
                dn = dn.substring(1);
            }
            int lastIndex = dn.indexOf(",");
            if (lastIndex > -1 && lastIndex == dn.length() - 1) {
                dn = dn.substring(0, dn.length() - 1);
            }

            String groupNameAttributeName = realmConfig.
                    getUserStoreProperty(LDAPConstants.SHARED_TENANT_NAME_ATTRIBUTE).toLowerCase();
            dn = dn.replaceAll(groupNameAttributeName + "=", "");
            if (dn == null || dn.isEmpty()) {
                dn = Constants.SUPER_TENANT_DOMAIN_NAME;
            }
            return dn;
        } else {
            return Constants.SUPER_TENANT_DOMAIN_NAME;
        }
    }

    /**
     * Removes the shared roles relevant to the provided tenant domain
     *
     * @param sharedRoles
     * @param tenantDomain
     */
    protected void filterSharedRoles(List<String> sharedRoles, String tenantDomain) {
        tenantDomain = tenantDomain.toLowerCase();
        if (tenantDomain != null) {
            for (Iterator<String> i = sharedRoles.iterator(); i.hasNext(); ) {
                String role = i.next();
                if (role.toLowerCase().indexOf(tenantDomain) > -1) {
                    i.remove();
                }
            }
        }
    }


    /**
     *
     */
    public String[] doGetRoleNames(String filter, int maxItemLimit) throws UserStoreException {

        if (maxItemLimit == 0) {
            return new String[0];
        }

        int givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;

        int searchTime = UserCoreConstants.MAX_SEARCH_TIME;

        try {
            givenMax = Integer.parseInt(realmConfig.
                    getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_ROLE_LIST));
        } catch (Exception e) {
            givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        }

        try {
            searchTime = Integer.parseInt(realmConfig.
                    getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_SEARCH_TIME));
        } catch (Exception e) {
            searchTime = UserCoreConstants.MAX_SEARCH_TIME;
        }

        if (maxItemLimit < 0 || maxItemLimit > givenMax) {
            maxItemLimit = givenMax;
        }

        List<String> externalRoles = new ArrayList<String>();

        if (readGroupsEnabled) {

            // handling multiple search bases
            String searchBases = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);
            String[] searchBaseArray = searchBases.split("#");
            for (String searchBase : searchBaseArray) {
                // get the role list from the group search base
                externalRoles.addAll(getLDAPRoleNames(searchTime, filter, maxItemLimit,
                        realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER),
                        realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE),
                        searchBase, false));
            }

            // get the role list from the shared group search base

//			if (isSharedGroupEnabled()) {
//				List<String> sharedRoleNames = new ArrayList<String>();
//				sharedRoleNames.addAll(Arrays.asList(doGetSharedRoleNames(null,filter, maxItemLimit)));
//
//				filterSharedRoles(sharedRoleNames, CarbonContext.getCurrentContext()
//				                                                .getTenantDomain());
//				externalRoles.addAll(sharedRoleNames);
//			}
        }

        return externalRoles.toArray(new String[externalRoles.size()]);
    }


    @Override
    protected String[] doGetSharedRoleNames(String tenantDomain, String filter, int maxItemLimit)
            throws UserStoreException {

        if (!isSharedGroupEnabled()) {
            return new String[0];
        }

        if (maxItemLimit == 0) {
            return new String[0];
        }

        int givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;

        int searchTime = UserCoreConstants.MAX_SEARCH_TIME;

        try {
            givenMax = Integer.parseInt(realmConfig.
                    getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_ROLE_LIST));
        } catch (Exception e) {
            givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        }

        try {
            searchTime = Integer.parseInt(realmConfig.
                    getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_SEARCH_TIME));
        } catch (Exception e) {
            searchTime = UserCoreConstants.MAX_SEARCH_TIME;
        }

        if (maxItemLimit < 0 || maxItemLimit > givenMax) {
            maxItemLimit = givenMax;
        }

        String searchBase = null;

        if (Constants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(tenantDomain)) {
            searchBase = realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_SEARCH_BASE);
        } else {
            String groupNameAttributeName =
                    realmConfig.getUserStoreProperty(LDAPConstants.SHARED_TENANT_NAME_ATTRIBUTE);
            if (groupNameAttributeName == null || groupNameAttributeName.trim().length() == 0) {
                groupNameAttributeName = "ou";
            }
            searchBase = groupNameAttributeName + "=" + tenantDomain + "," +
                    realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_SEARCH_BASE);
        }

        List<String> sharedRoleNames = getLDAPRoleNames(searchTime, filter, maxItemLimit,
                realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_LIST_FILTER),
                realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_ATTRIBUTE),
                searchBase, true);

        filterSharedRoles(sharedRoleNames, Constants.SUPER_TENANT_DOMAIN_NAME);
        return sharedRoleNames.toArray(new String[sharedRoleNames.size()]);

    }

    /**
     *
     */
    public RealmConfiguration getRealmConfiguration() {
        return this.realmConfig;
    }


    /**
     *
     */
    public String[] doGetUserListOfRole(String roleName, String filter) throws UserStoreException {

        RoleContext roleContext = createRoleContext(roleName);
        return getUserListOfLDAPRole(roleContext, filter);
    }

    /**
     *
     */
    public String[] getUserListOfLDAPRole(RoleContext context, String filter) throws UserStoreException {

        boolean debug = log.isDebugEnabled();

        if (debug) {
            log.debug("Getting user list of role: " + context.getRoleName() + " with filter: " + filter);
        }

        List<String> userList = new ArrayList<String>();
        String[] names = new String[0];
        int givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        int searchTime = UserCoreConstants.MAX_SEARCH_TIME;

        try {
            givenMax =
                    Integer.parseInt(realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST));
        } catch (Exception e) {
            givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        }

        try {
            searchTime =
                    Integer.parseInt(realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_SEARCH_TIME));
        } catch (Exception e) {
            searchTime = UserCoreConstants.MAX_SEARCH_TIME;
        }

        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;
        try {
            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchCtls.setTimeLimit(searchTime);
            searchCtls.setCountLimit(givenMax);

            String searchFilter = ((LDAPRoleContext) context).getListFilter();
            String roleNameProperty = ((LDAPRoleContext) context).getRoleNameProperty();
            searchFilter = "(&" + searchFilter + "(" + roleNameProperty + "=" + escapeSpecialCharactersForFilter(
                    context.getRoleName()) + "))";

            // Iterate the by intervals of range defined (if range > 0) and get the complete list of users
            int offset = 0;
            int lastRecord = 0;
            int attributeValuesRange = 0;
            boolean isEndOfAttributes = false;

            String roleListRange = realmConfig.getUserStoreProperty(MEMBERSHIP_ATTRIBUTE_RANGE);
            if (StringUtils.isNotEmpty(roleListRange)) {
                attributeValuesRange = Integer.parseInt(roleListRange);
            }
            if (attributeValuesRange > 0) {
                lastRecord = attributeValuesRange - 1;
            }
            String membershipProperty = realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
            List<String> userDNList = new ArrayList<>();
            String rangedMembershipProperty = membershipProperty;


            while (!isEndOfAttributes) {
                if (lastRecord > 0 && StringUtils.isNotEmpty(membershipProperty)) {
                    rangedMembershipProperty =
                            membershipProperty + String.format(";range=%1$d-%2$d", offset, lastRecord);
                }
                String returnedAtts[] = {rangedMembershipProperty};
                searchCtls.setReturningAttributes(returnedAtts);

                SearchResult sr = null;
                dirContext = connectionSource.getContext();

                // with DN patterns
                if (!((LDAPRoleContext) context).getRoleDNPatterns().isEmpty()) {
                    for (String pattern : ((LDAPRoleContext) context).getRoleDNPatterns()) {
                        if (debug) {
                            log.debug("Using pattern: " + pattern);
                        }
                        pattern = MessageFormat.format(pattern.trim(), escapeSpecialCharactersForDN(
                                context.getRoleName()));
                        try {
                            answer = dirContext.search(escapeDNForSearch(pattern), searchFilter, searchCtls);
                            if (answer.hasMore()) {
                                sr = answer.next();
                                break;
                            }
                        } catch (NamingException e) {
                            // ignore
                            if (log.isDebugEnabled()) {
                                log.debug(e);
                            }
                        }
                    }
                }

                if (sr == null) {
                    // handling multiple search bases
                    String searchBases = ((LDAPRoleContext) context).getSearchBase();
                    String[] roleSearchBaseArray = searchBases.split("#");
                    for (String searchBase : roleSearchBaseArray) {
                        if (debug) {
                            log.debug("Searching role: " + context.getRoleName() + " SearchBase: "
                                    + searchBase + " SearchFilter: " + searchFilter);
                        }

                        try {
                            // read the DN of users who are members of the group
                            answer = dirContext.search(escapeDNForSearch(searchBase), searchFilter, searchCtls);
                            int count = 0;
                            if (answer.hasMore()) { // to check if there is a result
                                while (answer.hasMore()) { // to check if there are more than one group
                                    if (count > 0) {
                                        throw new UserStoreException("More than one group exist with name");
                                    }
                                    sr = answer.next();
                                    count++;
                                }
                                break;
                            }
                        } catch (NamingException e) {
                            // ignore
                            if (log.isDebugEnabled()) {
                                log.debug(e);
                            }
                        }
                    }
                }

                if (debug) {
                    log.debug("Found role: " + sr.getNameInNamespace());
                }

                // read the member attribute and get DNs of the users
                Attributes attributes = sr.getAttributes();
                if (attributes != null) {
                    NamingEnumeration attributeEntry = null;
                    int recordCount = 0;
                    for (attributeEntry = attributes.getAll(); attributeEntry.hasMore(); ) {
                        Attribute valAttribute = (Attribute) attributeEntry.next();
                        if (membershipProperty == null ||
                                isAttributeEqualsProperty(membershipProperty, valAttribute.getID())) {
                            NamingEnumeration values = null;
                            for (values = valAttribute.getAll(); values.hasMore(); ) {
                                String value = values.next().toString();
                                userDNList.add(value);
                                recordCount++;

                                if (debug) {
                                    log.debug("Found attribute: " + membershipProperty + " value: " + value);
                                }
                            }
                        }
                    }
                    if (attributeValuesRange == 0 || recordCount < attributeValuesRange) {
                        isEndOfAttributes = true;
                    } else {
                        offset += attributeValuesRange;
                        lastRecord += attributeValuesRange;
                    }
                }
            }

            if (MEMBER_UID.equals(realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE))) {
                /* when the GroupEntryObjectClass is posixGroup, membership attribute is memberUid. We have to
                   retrieve the DN using the memberUid.
                   This procedure has to make an extra call to ldap. alternatively this can be done with a single ldap
                   search using the memberUid and retrieving the display name and username. */
                List<String> userDNListNew = new ArrayList<>();

                for (String user : userDNList) {
                    String userDN = getNameInSpaceForUserName(user);
                    userDNListNew.add(userDN);
                }

                userDNList = userDNListNew;
            }

            // iterate over users' DN list and get userName and display name
            // attribute values

            String userNameProperty = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
            String displayNameAttribute = realmConfig
                    .getUserStoreProperty(LDAPConstants.DISPLAY_NAME_ATTRIBUTE);
            String[] returnedAttributes = {userNameProperty, displayNameAttribute};

            for (String user : userDNList) {
                if (debug) {
                    log.debug("Getting name attributes of: " + user);
                }

                Attributes userAttributes;
                try {
                    // '\' and '"' characters need another level of escaping before searching
                    userAttributes = dirContext.getAttributes(escapeDNForSearch(user), returnedAttributes);

                    String displayName = null;
                    String userName = null;
                    if (userAttributes != null) {
                        Attribute userNameAttribute = userAttributes.get(userNameProperty);
                        if (userNameAttribute != null) {
                            userName = (String) userNameAttribute.get();
                            if (debug) {
                                log.debug("UserName: " + userName);
                            }
                        }
                        if (StringUtils.isNotEmpty(displayNameAttribute)) {
                            Attribute displayAttribute = userAttributes.get(displayNameAttribute);
                            if (displayAttribute != null) {
                                displayName = (String) displayAttribute.get();
                            }
                            if (debug) {
                                log.debug("DisplayName: " + displayName);
                            }
                        }
                    }
                    String domainName =
                            realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);

                    // Username will be null in the special case where the
                    // username attribute has changed to another
                    // and having different userNameProperty than the current
                    // user-mgt.xml
                    if (userName != null) {
                        user = UserCoreUtil.getCombinedName(domainName, userName, displayName);
                        userList.add(user);
                        if (debug) {
                            log.debug(user + " is added to the result list");
                        }
                    }
                    // Skip listing users which are not applicable to current
                    // user-mgt.xml
                    else {
                        if (log.isDebugEnabled()) {
                            log.debug("User " + user + " doesn't have the user name property : " +
                                    userNameProperty);
                        }
                    }

                } catch (NamingException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Error in reading user information in the user store for the user " +
                                user + e.getMessage(), e);
                    }
                }

            }
            names = userList.toArray(new String[userList.size()]);

        } catch (PartialResultException e) {
            // can be due to referrals in AD. so just ignore error
            String errorMessage = "Error in reading user information in the user store for filter : " + filter;
            if (isIgnorePartialResultException()) {
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e);
                }
            } else {
                throw new UserStoreException(errorMessage, e);
            }
        } catch (NamingException e) {
            String errorMessage = "Error in reading user information in the user store for filter : " + filter;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }

        return names;
    }

    private boolean isAttributeEqualsProperty(String property, String attribute) {

        if (StringUtils.isEmpty(property) || StringUtils.isEmpty(attribute)) {
            return false;
        }
        return property.equals(attribute) || property.equals(attribute.substring(0, attribute.indexOf(";")));
    }

    /**
     * This method will check whether back link support is enabled and will
     * return the effective
     * search base. Read http://www.frickelsoft.net/blog/?p=130 for more
     * details.
     *
     * @param shared whether share search based or not
     * @return The search base based on back link support. If back link support
     * is enabled this will
     * return user search base, else group search base.
     */
    protected String getEffectiveSearchBase(boolean shared) {

        String backLinksEnabled =
                realmConfig.getUserStoreProperty(LDAPConstants.BACK_LINKS_ENABLED);
        boolean isBackLinkEnabled = false;

        if (backLinksEnabled != null && !backLinksEnabled.equals("")) {
            isBackLinkEnabled = Boolean.parseBoolean(backLinksEnabled);
        }

        if (isBackLinkEnabled) {
            return realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        } else {
            if (shared) {
                return realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_SEARCH_BASE);
            } else {
                return realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);
            }
        }

    }

    /**
     * {@inheritDoc}
     */
    protected String[] getLDAPRoleListOfUser(String userName, String filter, String searchBase,
                                             boolean shared) throws UserStoreException {
        if (userName == null) {
            throw new UserStoreException("userName value is null.");
        }
        boolean debug = log.isDebugEnabled();
        List<String> list = new ArrayList<String>();
        /*
		 * do not search REGISTRY_ANONNYMOUS_USERNAME or
		 * REGISTRY_SYSTEM_USERNAME in LDAP because it
		 * causes warn logs printed from embedded-ldap.
		 */
        if (readGroupsEnabled && (!UserCoreUtil.isRegistryAnnonymousUser(userName)) &&
                (!UserCoreUtil.isRegistrySystemUser(userName))) {

            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            String memberOfProperty =
                    realmConfig.getUserStoreProperty(LDAPConstants.MEMBEROF_ATTRIBUTE);
            if (memberOfProperty != null && memberOfProperty.length() > 0) {
                // TODO Handle active directory shared roles logics here

                String userNameProperty =
                        realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
                String userSearchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
                String searchFilter = userSearchFilter.replace("?", escapeSpecialCharactersForFilter(userName));

                String binaryAttribute =
                        realmConfig.getUserStoreProperty(LDAPConstants.LDAP_ATTRIBUTES_BINARY);
                String primaryGroupId =
                        realmConfig.getUserStoreProperty(LDAPConstants.PRIMARY_GROUP_ID);

                String returnedAtts[] = {memberOfProperty};

                if (binaryAttribute != null && primaryGroupId != null) {
                    returnedAtts =
                            new String[]{memberOfProperty, binaryAttribute, primaryGroupId};
                }

                searchCtls.setReturningAttributes(returnedAtts);

                if (debug) {
                    log.debug("Reading roles with the memberOfProperty Property: " + memberOfProperty);
                }

                if (binaryAttribute != null && primaryGroupId != null) {
                    list =
                            this.getAttributeListOfOneElementWithPrimarGroup(
                                    searchBase,
                                    searchFilter,
                                    searchCtls,
                                    binaryAttribute,
                                    primaryGroupId,
                                    userNameProperty,
                                    memberOfProperty);
                } else {
                    // use cache
                    LdapName ldn = getFromUserCache(userName);
                    if (ldn != null) {
                        searchBase = ldn.toString();
                    } else {
                        // create DN directly   but there is no way when multiple DNs are used. Need to improve letter
                        String userDNPattern = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);
                        if (userDNPattern != null && userDNPattern.trim().length() > 0
                                && !userDNPattern.contains("#")) {
                            searchBase = MessageFormat.format(userDNPattern, escapeSpecialCharactersForDN(userName));
                        }
                    }

                    // get DNs of the groups to which this user belongs
                    List<String> groupDNs = this.getListOfNames(searchBase, searchFilter,
                            searchCtls, memberOfProperty, false);
                    List<LdapName> groups = new ArrayList<>();
                    for (String groupDN : groupDNs) {
                        try {
                            groups.add(new LdapName(groupDN));
                        } catch (InvalidNameException e) {
                           if (log.isDebugEnabled()) {
                                log.debug("LDAP Name error :", e);
                           }
                        }
                    }
					/*
					 * to be compatible with AD as well, we need to do a search
					 * over the groups and
					 * find those groups' attribute value defined for group name
					 * attribute and
					 * return
					 */
                    list = this.getGroupNameAttributeValuesOfGroups(groups);
                }
            } else {

                // Load normal roles with the user
                String searchFilter;
                String roleNameProperty;

                if (shared) {
                    searchFilter = realmConfig.
                            getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_LIST_FILTER);
                    roleNameProperty =
                            realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_ATTRIBUTE);
                } else {
                    searchFilter = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER);
                    roleNameProperty =
                            realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
                }

                String membershipProperty =
                        realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
                String userDNPattern = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);
                String nameInSpace;
                if (userDNPattern != null && userDNPattern.trim().length() > 0 && !userDNPattern.contains("#")) {

                    nameInSpace = MessageFormat.format(userDNPattern, escapeSpecialCharactersForDN(userName));
                } else {
                    nameInSpace = this.getNameInSpaceForUserName(userName);
                }
                // read the roles with this membership property

                if (membershipProperty == null || membershipProperty.length() < 1) {
                    throw new UserStoreException(
                            "Please set member of attribute or membership attribute");
                }

                String membershipValue;
                if (nameInSpace != null) {
                    try {
                        LdapName ldn = new LdapName(nameInSpace);
                        if (MEMBER_UID.equals(realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE))) {
                            // membership value of posixGroup is not DN of the user
                            List rdns = ldn.getRdns();
                            membershipValue = ((Rdn) rdns.get(rdns.size() - 1)).getValue().toString();
                        } else {
                            membershipValue = escapeLdapNameForFilter(ldn);
                        }
                    } catch (InvalidNameException e) {
                        log.error("Error while creating LDAP name from: " + nameInSpace);
                        throw new UserStoreException("Invalid naming exception for : " + nameInSpace, e);
                    }
                } else {
                    return new String[0];
                }

                searchFilter =
                        "(&" + searchFilter + "(" + membershipProperty + "=" + membershipValue + "))";
                String returnedAtts[] = {roleNameProperty};
                searchCtls.setReturningAttributes(returnedAtts);

                if (debug) {
                    log.debug("Reading roles with the membershipProperty Property: " + membershipProperty);
                }

                list = this.getListOfNames(searchBase, searchFilter, searchCtls, roleNameProperty, false);
            }
        } else if (UserCoreUtil.isRegistryAnnonymousUser(userName)) {
            // returning a REGISTRY_ANONNYMOUS_ROLE_NAME for
            // REGISTRY_ANONNYMOUS_USERNAME
            list.add(UserCoreConstants.REGISTRY_ANONNYMOUS_ROLE_NAME);
        }

        String[] result = list.toArray(new String[list.size()]);

        if (result != null) {
            for (String rolename : result) {
                log.debug("Found role: " + rolename);
            }
        }
        return result;
    }

    @Override
    protected String[] doGetExternalRoleListOfUser(String userName, String filter) throws UserStoreException {

        // Get the effective search base
        String searchBase = this.getEffectiveSearchBase(false);
        return getLDAPRoleListOfUser(userName, filter, searchBase, false);
    }


    @Override
    protected String[] doGetSharedRoleListOfUser(String userName,
                                                 String tenantDomain, String filter) throws UserStoreException {
        // Get the effective search base
        String searchBase = this.getEffectiveSearchBase(true);
        if (tenantDomain != null && tenantDomain.trim().length() > 0) {
            if (!Constants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(tenantDomain.trim())) {
                String groupNameAttributeName =
                        realmConfig.getUserStoreProperty(LDAPConstants.SHARED_TENANT_NAME_ATTRIBUTE);
                if (groupNameAttributeName == null || groupNameAttributeName.trim().length() == 0) {
                    groupNameAttributeName = "ou";
                }
                searchBase = groupNameAttributeName + "=" + tenantDomain + "," + searchBase;
            }
        }
        return getLDAPRoleListOfUser(userName, filter, searchBase, true);
    }

    /**
     * {@inheritDoc}
     */

    public boolean isReadOnly() throws UserStoreException {
        return true;
    }

    /**
     * @param userName
     * @return
     * @throws UserStoreException
     */
    protected String getNameInSpaceForUserName(String userName) throws UserStoreException {

        // check the cache first
        LdapName ldn = null;
        if (userName != null) {
            ldn = getFromUserCache(userName);
        } else {
            throw new UserStoreException("userName value is null.");
        }
        if (ldn != null) {
            return ldn.toString();
        }

        return getNameInSpaceForUsernameFromLDAP(userName);
    }

    /**
     * This is to search user and retrieve ldap name directly from ldap
     * @param userName
     * @return
     * @throws UserStoreException
     */
    protected String getNameInSpaceForUsernameFromLDAP(String userName) throws UserStoreException {

        String searchBase = null;
        String userSearchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        userSearchFilter = userSearchFilter.replace("?", escapeSpecialCharactersForFilter(userName));
        String userDNPattern = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);
        if (userDNPattern != null && userDNPattern.trim().length() > 0) {
            String[] patterns = userDNPattern.split("#");
            for (String pattern : patterns) {
                searchBase = MessageFormat.format(pattern, escapeSpecialCharactersForDN(userName));
                String userDN = getNameInSpaceForUserName(userName, searchBase, userSearchFilter);
                // check in another DN pattern
                if (userDN != null) {
                    return userDN;
                }
            }
        }

        searchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);

        return getNameInSpaceForUserName(userName, searchBase, userSearchFilter);
    }

    /**
     * @param userName
     * @param searchBase
     * @param searchFilter
     * @return
     * @throws UserStoreException
     */
    protected String getNameInSpaceForUserName(String userName, String searchBase, String searchFilter) throws UserStoreException {
        boolean debug = log.isDebugEnabled();

        if (userName == null) {
            throw new UserStoreException("userName value is null.");
        }
        Object cachedDn = getFromUserCache(userName);
        if ( cachedDn != null) {
            return cachedDn.toString();
        }

        String userDN = null;

        DirContext dirContext = this.connectionSource.getContext();
        NamingEnumeration<SearchResult> answer = null;
        try {
            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            if (log.isDebugEnabled()) {
                try {
                    log.debug("Searching for user with SearchFilter: " + searchFilter + " in SearchBase: " + dirContext.getNameInNamespace());
                } catch (NamingException e) {
                    log.debug("Error while getting DN of search base", e);
                }
            }
            SearchResult userObj = null;
            String[] searchBases = searchBase.split("#");
            for (String base : searchBases) {
                answer = dirContext.search(escapeDNForSearch(base), searchFilter, searchCtls);
                if (answer.hasMore()) {
                    userObj = (SearchResult) answer.next();
                    if (userObj != null) {
                        //no need to decode since , if decoded the whole string, can't be encoded again
                        //eg CN=Hello\,Ok=test\,test, OU=Industry
                        userDN = userObj.getNameInNamespace();
                        break;
                    }
                }
            }
            if (userDN != null) {
                LdapName ldn = new LdapName(userDN);
                putToUserCache(userName, ldn);
            }
            if (debug) {
                log.debug("Name in space for " + userName + " is " + userDN);
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }
        return userDN;
    }

    /**
     * @param sr
     * @param groupAttributeName
     * @return
     */
    private List<String> parseSearchResult(SearchResult sr, String groupAttributeName) {
        List<String> list = new ArrayList<String>();
        Attributes attrs = sr.getAttributes();

        if (attrs != null) {
            try {
                NamingEnumeration ae = null;
                for (ae = attrs.getAll(); ae.hasMore(); ) {
                    Attribute attr = (Attribute) ae.next();
                    if (groupAttributeName == null || groupAttributeName.equals(attr.getID())) {
                        NamingEnumeration e = null;
                        for (e = attr.getAll(); e.hasMore(); ) {
                            String value = e.next().toString();
                            int begin = value.indexOf("=") + 1;
                            int end = value.indexOf(",");
                            if (begin > -1 && end > -1) {
                                value = value.substring(begin, end);
                            }
                            list.add(value);
                        }
                        JNDIUtil.closeNamingEnumeration(e);
                    }
                }
                JNDIUtil.closeNamingEnumeration(ae);
            } catch (NamingException e) {
                log.debug(e.getMessage(), e);
            }
        }
        return list;
    }

    /**
     * @param searchBase
     * @param searchFilter
     * @param searchCtls
     * @param objectSid
     * @param primaryGroupID
     * @param userAttributeId
     * @param groupAttributeName
     * @return
     * @throws UserStoreException
     */
    private List<String> getAttributeListOfOneElementWithPrimarGroup(String searchBase,
                                                                     String searchFilter,
                                                                     SearchControls searchCtls,
                                                                     String objectSid,
                                                                     String primaryGroupID,
                                                                     String userAttributeId,
                                                                     String groupAttributeName)
            throws UserStoreException {
        boolean debug = log.isDebugEnabled();

        List<String> list = new ArrayList<String>();
        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;

        if (debug) {
            log.debug("GetAttributeListOfOneElementWithPrimarGroup. SearchBase: " + searchBase + " SearchFilter: " + searchFilter);
        }
        try {
            dirContext = connectionSource.getContext();
            answer = dirContext.search(escapeDNForSearch(searchBase), searchFilter, searchCtls);
            int count = 0;
            while (answer.hasMore()) {
                if (count > 0) {
                    log.error("More than element user exist with name");
                    throw new UserStoreException("More than element user exist with name");
                }
                SearchResult sr = (SearchResult) answer.next();
                count++;

                list = parseSearchResult(sr, groupAttributeName);

                String primaryGroupSID = LDAPUtil.getPrimaryGroupSID(sr, objectSid, primaryGroupID);
                String primaryGroupName =
                        LDAPUtil.findGroupBySID(dirContext, searchBase,
                                primaryGroupSID, userAttributeId);
                if (primaryGroupName != null) {
                    list.add(primaryGroupName);
                }
            }

        } catch (PartialResultException e) {
            // can be due to referrals in AD. so just ignore error
            String errorMessage = "Error occurred while GetAttributeListOfOneElementWithPrimarGroup. SearchBase: " +
                                  searchBase + " SearchFilter: " + searchFilter;
            if (isIgnorePartialResultException()) {
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e);
                }
            } else {
                throw new UserStoreException(errorMessage, e);
            }
        } catch (NamingException e) {
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage(), e);
            }
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }

        if (debug) {
            log.debug("GetAttributeListOfOneElementWithPrimarGroup. SearchBase: " + searchBase + " SearchFilter: " + searchFilter);
            Iterator<String> ite = list.iterator();
            while (ite.hasNext()) {
                log.debug("result: " + ite.next());
            }
        }
        return list;
    }

    // ****************************************************

    @SuppressWarnings("rawtypes")
    protected List<String> getAttributeListOfOneElement(String searchBases, String searchFilter,
                                                        SearchControls searchCtls)
            throws UserStoreException {
        List<String> list = new ArrayList<String>();
        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;
        try {
            dirContext = connectionSource.getContext();
            // handle multiple search bases
            String[] searchBaseArray = searchBases.split("#");
            for (String searchBase : searchBaseArray) {
                try {
                    answer = dirContext.search(escapeDNForSearch(searchBase), searchFilter, searchCtls);
                    int count = 0;
                    if (answer.hasMore()) {
                        while (answer.hasMore()) {
                            if (count > 0) {
                                log.error("More than element user exist with name");
                                throw new UserStoreException("More than element user exist with name");
                            }
                            SearchResult sr = (SearchResult) answer.next();
                            count++;
                            list = parseSearchResult(sr, null);
                        }
                        break;
                    }
                } catch (NamingException e) {
                    //ignore
                    if (log.isDebugEnabled()) {
                        log.debug(e);
                    }
                }
            }
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }
        return list;
    }

    /**
     * @param searchBases
     * @param searchFilter
     * @param searchCtls
     * @param property
     * @return
     * @throws UserStoreException
     */
    private List<String> getListOfNames(String searchBases, String searchFilter,
                                        SearchControls searchCtls, String property, boolean appendDn)
            throws UserStoreException {
        boolean debug = log.isDebugEnabled();
        List<String> names = new ArrayList<String>();
        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;

        if (debug) {
            log.debug("Result for searchBase: " + searchBases + " searchFilter: " + searchFilter +
                    " property:" + property + " appendDN: " + appendDn);
        }

        try {
            dirContext = connectionSource.getContext();

            // handle multiple search bases
            String[] searchBaseArray = searchBases.split("#");
            for (String searchBase : searchBaseArray) {

                try {
                    answer = dirContext.search(escapeDNForSearch(searchBase), searchFilter, searchCtls);
                    String domain = this.getRealmConfiguration().getUserStoreProperty(
                            UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);

                    while (answer.hasMoreElements()) {
                        SearchResult sr = (SearchResult) answer.next();
                        if (sr.getAttributes() != null) {
                            Attribute attr = sr.getAttributes().get(property);
                            if (attr != null) {
                                for (Enumeration vals = attr.getAll(); vals.hasMoreElements(); ) {
                                    String name = (String) vals.nextElement();
                                    if (debug) {
                                        log.debug("Found user: " + name);
                                    }
                                    domain = UserCoreUtil.addDomainToName(name,
                                            domain);
                                    names.add(name);
                                }
                            }
                        }
                    }
                } catch (NamingException e) {
                    // ignore
                    if (log.isDebugEnabled()) {
                        log.debug(e);
                    }
                }

                if (debug) {
                    for (String name : names) {
                        log.debug("Result  :  " + name);
                    }
                }

            }

            return names;
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }
    }

    /**
     *
     */
    public Map<String, String> getProperties(org.wso2.micro.integrator.security.user.api.Tenant tenant)
            throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        return getProperties((Tenant) tenant);
    }

    /**
     *
     */
    public int getTenantId() throws UserStoreException {
        return this.tenantId;
    }

    /* TODO: support for multiple user stores */
    public String[] getUserListFromProperties(String property, String value, String profileName)
            throws UserStoreException {

        if (value == null) {
            return new String[0];
        }
        boolean debug = log.isDebugEnabled();
        String userAttributeSeparator = ",";
        String serviceNameAttribute = "sn";
        List<String> values = new ArrayList<String>();
        String searchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER);
        String userPropertyName =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);

        if (OBJECT_GUID.equals(property)) {
            String transformObjectGuidToUuidProperty =
                    realmConfig.getUserStoreProperty(TRANSFORM_OBJECTGUID_TO_UUID);

            boolean transformObjectGuidToUuid = StringUtils.isEmpty(transformObjectGuidToUuidProperty) ||
                    Boolean.parseBoolean(transformObjectGuidToUuidProperty);

            String convertedValue;
            if (transformObjectGuidToUuid) {
                convertedValue = transformUUIDToObjectGUID(value);
            } else {
                byte[] bytes = Base64.decodeBase64(value.getBytes());
                convertedValue = convertBytesToHexString(bytes);
            }
            searchFilter = "(&" + searchFilter + "(" + property + "=" + convertedValue + "))";
        } else {
            searchFilter = "(&" + searchFilter + "(" + property + "=" + escapeSpecialCharactersForFilterWithStarAsRegex(
                    value) + "))";
        }

        DirContext dirContext = this.connectionSource.getContext();
        NamingEnumeration<?> answer = null;
        NamingEnumeration<?> attrs = null;

        if (debug) {
            log.debug("Listing users with Property: " + property + " SearchFilter: " + searchFilter);
        }
        String[] returnedAttributes = new String[]{ userPropertyName, serviceNameAttribute };
        try {
            answer = this.searchForUser(searchFilter, returnedAttributes, dirContext);
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                Attributes attributes = sr.getAttributes();
                if (attributes != null) {
                    Attribute attribute = attributes.get(userPropertyName);
                    if (attribute != null) {
                        StringBuffer attrBuffer = new StringBuffer();
                        for (attrs = attribute.getAll(); attrs.hasMore(); ) {
                            String attr = (String) attrs.next();
                            if (attr != null && attr.trim().length() > 0) {

                                String attrSeparator = realmConfig.getUserStoreProperty(MULTI_ATTRIBUTE_SEPARATOR);
                                if (attrSeparator != null && !attrSeparator.trim().isEmpty()) {
                                    userAttributeSeparator = attrSeparator;
                                }
                                attrBuffer.append(attr + userAttributeSeparator);
                                if (debug) {
                                    log.debug(userPropertyName + " : " + attr);
                                }
                            }
                        }
                        String propertyValue = attrBuffer.toString();
                        Attribute serviceNameObject = attributes.get(serviceNameAttribute);
                        String serviceNameAttributeValue = null;
                        if (serviceNameObject != null) {
                            serviceNameAttributeValue = (String) serviceNameObject.get();
                        }
                        // Length needs to be more than userAttributeSeparator.length() for a valid
                        // attribute, since we
                        // attach userAttributeSeparator.
                        if (propertyValue != null && propertyValue.trim().length() > userAttributeSeparator.length()) {
                            if (LDAPConstants.SERVER_PRINCIPAL_ATTRIBUTE_VALUE.equals(serviceNameAttributeValue)) {
                                continue;
                            }
                            propertyValue = propertyValue.substring(0, propertyValue.length() -
                                    userAttributeSeparator.length());
                            values.add(propertyValue);
                        }
                    }
                }
            }

		} catch (NamingException e) {
            String errorMessage =
                    "Error occurred while getting user list from property : " + property + " & value : " + value +
                    " & profile name : " + profileName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            // close the naming enumeration and free up resources
            JNDIUtil.closeNamingEnumeration(attrs);
            JNDIUtil.closeNamingEnumeration(answer);
            // close directory context
            JNDIUtil.closeContext(dirContext);
        }

        if (debug) {
            String[] results = values.toArray(new String[values.size()]);
            for (String result : results) {
                log.debug("result: " + result);
            }
        }

        return values.toArray(new String[values.size()]);
    }

    /**
     * This method support multi-attribute filters with paginated search for user(s).
     *
     * @param condition   Validated Condition tree
     * @param profileName Default profile name
     * @param limit       The number of entries to return in a page
     * @param offset      Start index
     * @param sortBy      Sort according to the given attribute name
     * @param sortOrder   Sorting order
     * @return A non-null PaginatedSearchResult instance. Typically contains user names with pagination
     * @throws UserStoreException If an UserStoreException is encountered
     *                            while searching for users in a given condition
     */
    protected PaginatedSearchResult doGetUserList(Condition condition, String profileName, int limit, int offset,
                                                  String sortBy, String sortOrder) throws UserStoreException {

        PaginatedSearchResult result = new PaginatedSearchResult();
        // Since we support only AND operation get expressions as a list.
        List<ExpressionCondition> expressionConditions = getExpressionConditions(condition);
        LDAPSearchSpecification ldapSearchSpecification = new LDAPSearchSpecification(realmConfig,
                expressionConditions);
        boolean isMemberShipPropertyFound = ldapSearchSpecification.isMemberShipPropertyFound();
        limit = getLimit(limit, isMemberShipPropertyFound);
        offset = getOffset(offset);

        if (limit == 0) {
            return result;
        }

        int pageSize = limit;
        DirContext dirContext = this.connectionSource.getContext();
        LdapContext ldapContext = (LdapContext) dirContext;
        List<String> users;
        String userNameAttribute = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
        try {
            ldapContext.setRequestControls(new Control[]{new PagedResultsControl(pageSize, Control.CRITICAL),
                    new SortControl(userNameAttribute, Control.NONCRITICAL)});
            users = performLDAPSearch(ldapContext, ldapSearchSpecification, pageSize, offset, expressionConditions);
            result.setUsers(users.toArray(new String[0]));
            return result;
        } catch (NamingException e) {
            log.error(String.format("Error occurred while performing paginated search, %s", e.getMessage()));
            throw new UserStoreException(e.getMessage(), e);
        } catch (IOException e) {
            log.error(String.format("Error occurred while setting paged results controls for paginated search, %s",
                    e.getMessage()));
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeContext(dirContext);
            JNDIUtil.closeContext(ldapContext);
        }
    }

    /**
     * Get offset, that is start index.
     *
     * @param offset
     * @return
     */
    private int getOffset(int offset) {

        if (offset <= 0) {
            offset = 0;
        } else {
            offset = offset - 1;
        }
        return offset;
    }

    /**
     * Get page size limit to do paginated search.
     *
     * @param limit
     * @param isMemberShipPropertyFound
     * @return
     */
    private int getLimit(int limit, boolean isMemberShipPropertyFound) {

        int givenMax;

        try {
            givenMax = Integer.parseInt(realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig
                    .PROPERTY_MAX_USER_LIST));
        } catch (Exception e) {
            givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        }
        /*
        For group filtering can't apply pagination. We don't know how many group details will be return.
        So set to max value.
         */
        if (isMemberShipPropertyFound || limit > givenMax) {
            limit = givenMax;
        }
        return limit;
    }

    /**
     * Parse the controls to navigate to next page.
     *
     * @param controls
     * @return
     */
    private static byte[] parseControls(Control[] controls) {

        byte[] cookie = null;
        // Handle the paged results control response
        if (controls != null) {
            for (int i = 0; i < controls.length; i++) {
                if (controls[i] instanceof PagedResultsResponseControl) {
                    PagedResultsResponseControl prrc = (PagedResultsResponseControl) controls[i];
                    cookie = prrc.getCookie();
                }
            }
        }
        return cookie;
    }

    /**
     * Parse the condition tree and get as list of expressions. Since we only support for 'AND' operation.
     *
     * @param condition
     * @return
     */
    private List<ExpressionCondition> getExpressionConditions(Condition condition) {

        List<ExpressionCondition> expressionConditions = new ArrayList<>();
        getExpressionConditionsAsList(condition, expressionConditions);
        return expressionConditions;
    }

    /**
     * Traversing through all nodes of condition tree and generate expression list.
     *
     * @param condition
     * @param expressionConditions
     */
    private void getExpressionConditionsAsList(Condition condition, List<ExpressionCondition> expressionConditions) {

        if (condition instanceof ExpressionCondition) {
            ExpressionCondition expressionCondition = (ExpressionCondition) condition;
            expressionCondition.setAttributeValue(
                    escapeSpecialCharactersForFilterWithStarAsRegex(expressionCondition.getAttributeValue()));
            expressionConditions.add(expressionCondition);
        } else if (condition instanceof OperationalCondition) {
            Condition leftCondition = ((OperationalCondition) condition).getLeftCondition();
            getExpressionConditionsAsList(leftCondition, expressionConditions);
            Condition rightCondition = ((OperationalCondition) condition).getRightCondition();
            getExpressionConditionsAsList(rightCondition, expressionConditions);
        }
    }

    /**
     * Do LDAP paginated search and return user names as a list.
     *
     * @param ldapContext             LDAP connection context
     * @param ldapSearchSpecification Contains LDAP context search parameters
     * @param pageSize                Number of results per page
     * @param offset                  Start index
     * @param expressionConditions    List of input expressions
     * @return List of user name
     * @throws UserStoreException
     */
    private List<String> performLDAPSearch(LdapContext ldapContext, LDAPSearchSpecification ldapSearchSpecification,
                                           int pageSize, int offset, List<ExpressionCondition> expressionConditions)
            throws UserStoreException {

        byte[] cookie;
        int pageIndex = -1;
        boolean isGroupFiltering = ldapSearchSpecification.isGroupFiltering();
        boolean isUsernameFiltering = ldapSearchSpecification.isUsernameFiltering();
        boolean isClaimFiltering = ldapSearchSpecification.isClaimFiltering();
        boolean isMemberShipPropertyFound = ldapSearchSpecification.isMemberShipPropertyFound();

        String searchBases = ldapSearchSpecification.getSearchBases();
        String[] searchBaseAraay = searchBases.split("#");
        String searchFilter = ldapSearchSpecification.getSearchFilterQuery();
        SearchControls searchControls = ldapSearchSpecification.getSearchControls();
        List<String> returnedAttributes = Arrays.asList(searchControls.getReturningAttributes());
        NamingEnumeration<SearchResult> answer = null;
        List<String> users = new ArrayList<>();

        if (log.isDebugEnabled()) {
            log.debug(String.format("Searching for user(s) with SearchFilter: %s and page size %d", searchFilter,
                    pageSize));
        }
        try {
            for (String searchBase : searchBaseAraay) {
                do {
                    List<String> tempUserList = new ArrayList<>();
                    answer = ldapContext.search(escapeDNForSearch(searchBase), searchFilter, searchControls);
                    if (answer.hasMore()) {
                        tempUserList = getUserListFromSearch(isGroupFiltering, returnedAttributes, answer,
                                isSingleAttributeFilterOperation(expressionConditions));
                        pageIndex++;
                    }
                    if (CollectionUtils.isNotEmpty(tempUserList)) {
                        if (isMemberShipPropertyFound) {
                            /*
                            Pagination is not supported for 'member' attribute group filtering. Also,
                            we need do post-processing if we found username filtering or claim filtering,
                            because can't apply claim filtering with memberShip group filtering and
                            can't apply username filtering with 'CO', 'EW' filter operations.
                             */
                            users = membershipGroupFilterPostProcessing(isUsernameFiltering, isClaimFiltering,
                                    expressionConditions, tempUserList);
                            break;
                        } else {
                            // Handle pagination depends on given offset, i.e. start index.
                            generatePaginatedUserList(pageIndex, offset, pageSize, tempUserList, users);
                            int needMore = pageSize - users.size();
                            if (needMore == 0) {
                                break;
                            }
                        }
                    }
                    cookie = parseControls(ldapContext.getResponseControls());
                    String userNameAttribute = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
                    ldapContext.setRequestControls(new Control[]{new PagedResultsControl(pageSize, cookie,
                            Control.CRITICAL), new SortControl(userNameAttribute, Control.NONCRITICAL)});
                } while ((cookie != null) && (cookie.length != 0));
            }
        } catch (PartialResultException e) {
            // Can be due to referrals in AD. So just ignore error.
            if (isIgnorePartialResultException()) {
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Error occurred while searching for user(s) for filter: %s", searchFilter));
                }
            } else {
                log.error(String.format("Error occurred while searching for user(s) for filter: %s", searchFilter));
                throw new UserStoreException(e.getMessage(), e);
            }
        } catch (NamingException e) {
            log.error(String.format("Error occurred while searching for user(s) for filter: %s, %s",
                    searchFilter, e.getMessage()));
            throw new UserStoreException(e.getMessage(), e);
        } catch (IOException e) {
            log.error(String.format("Error occurred while doing paginated search, %s", e.getMessage()));
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
        }
        return users;
    }

    /**
     * Method to verify whether the filter operation is a single attribute filter scenario or multi attribute filter scenario
     *
     * @param expressionConditions Expression conditions
     * @return True if the operation is a single attribute filter.
     */
    private boolean isSingleAttributeFilterOperation(List<ExpressionCondition> expressionConditions) {

        /*
        The size of the expression condition is used to verify the type of filter operation since the up
        coming steps needs to verify whether this is a multi attribute scenario or single attribute scenario.
        (value will equal to 1 for a single attribute filter)
        */
        return (expressionConditions.size() == 1);
    }

    /**
     * Get user list from multi attribute search filter.
     *
     * @param isGroupFiltering        Whether the filtering has the group attribute name.
     * @param returnedAttributes      Returned Attributes
     * @param answer                  Answer
     * @param isSingleAttributeFilter Whether the original request is from a single attribute filter or a multi
     *                                attribute filter, so that AND operation can be omitted during the filtering
     *                                process.
     * @return A users list
     * @throws UserStoreException
     * @throws NamingException
     */
    private List<String> getUserListFromSearch(boolean isGroupFiltering, List<String> returnedAttributes,
            NamingEnumeration<SearchResult> answer, boolean isSingleAttributeFilter) throws UserStoreException {

        List<String> tempUserList;
        if (isGroupFiltering) {
            tempUserList = getUserListFromGroupFilterResult(answer, returnedAttributes, isSingleAttributeFilter);
        } else {
            tempUserList = getUserListFromNonGroupFilterResult(answer, returnedAttributes);
        }
        return tempUserList;
    }

    /**
     * Parse the search result of group filtering and get the user list.
     * If it's membership group filtering, we retrieve all members of the requested group(s) and then
     * get the mutual members' out of it as a DN list.
     * If it's memberOf group filtering, directly get the user name list from search result.
     *
     * @param answer                  Answer
     * @param returnedAttributes      Returned Attributes
     * @param isSingleAttributeFilter Whether the original request is from a single attribute filter or a multi
     *                                attribute filter, so that AND operation can be omitted during the filtering
     *                                process.
     * @return A users list
     * @throws UserStoreException
     */
    private List<String> getUserListFromGroupFilterResult(NamingEnumeration<SearchResult> answer,
            List<String> returnedAttributes, boolean isSingleAttributeFilter) throws UserStoreException {

        // Can be user DN list or username list
        List<String> userListFromSearch = new ArrayList<>();
        // Multi group retrieval
        int count = 0;
        NamingEnumeration<?> attrs = null;
        List<String> finalUserList;

        try {
            while (answer.hasMoreElements()) {
                count++;
                List<String> tempUserList = new ArrayList<>();
                SearchResult searchResult = answer.next();
                Attributes attributes = searchResult.getAttributes();
                if (attributes == null)
                    continue;
                NamingEnumeration attributeEntry;
                for (attributeEntry = attributes.getAll(); attributeEntry.hasMore(); ) {
                    Attribute valAttribute = (Attribute) attributeEntry.next();
                    if (isAttributeEqualsProperty(returnedAttributes.get(0), valAttribute.getID())) {
                        NamingEnumeration values;
                        for (values = valAttribute.getAll(); values.hasMore(); ) {
                            tempUserList.add(values.next().toString());
                        }
                    }
                }
                /*
                 When singleAttributeFilter is true, that implies that the request is a single attribute filter. In
                 this case, the intersection (AND operation) should not be performed on the filtered results.
                 Following IF block handles the single attribute filter.
                 */
                if (isSingleAttributeFilter) {
                    userListFromSearch.addAll(tempUserList);
                } else {
                    /*
                     * If returnedAttributes doesn't contain 'member' attribute, then it's memberOf group filter.
                     * If so we  don't need to do post processing.
                     */
                    if (!returnedAttributes
                            .contains(realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE))
                            || count == 1) {
                        userListFromSearch.addAll(tempUserList);
                    } else {
                        userListFromSearch.retainAll(tempUserList);
                    }
                }
            }
        } catch (NamingException e) {
            log.error(String.format("Error occurred while getting user list from group filter %s", e.getMessage()));
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeNamingEnumeration(attrs);
        }

        // If 'member' attribute found, we need iterate over users' DN list and get userName.
        if (returnedAttributes.contains(realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE))) {
            finalUserList = getUserNamesFromDNList(userListFromSearch);
        } else {
            finalUserList = userListFromSearch;
        }
        return finalUserList;
    }

    /**
     * Get user name list from DN list.
     *
     * @param userListFromSearch
     * @return
     * @throws UserStoreException
     */
    private List<String> getUserNamesFromDNList(List<String> userListFromSearch) throws UserStoreException {

        List<String> userNameList = new ArrayList<>();
        DirContext dirContext = this.connectionSource.getContext();
        String userNameProperty = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
        String displayNameAttribute = realmConfig.getUserStoreProperty(LDAPConstants.DISPLAY_NAME_ATTRIBUTE);
        String[] requiredAttributes = {userNameProperty, displayNameAttribute};

        for (String user : userListFromSearch) {
            try {
                String displayName = null;
                String userName = null;
                Attributes userAttributes = dirContext.getAttributes(escapeDNForSearch(user), requiredAttributes);

                if (userAttributes != null) {
                    Attribute userNameAttribute = userAttributes.get(userNameProperty);
                    if (userNameAttribute != null) {
                        userName = (String) userNameAttribute.get();
                    }
                    if (StringUtils.isNotEmpty(displayNameAttribute)) {
                        Attribute displayAttribute = userAttributes.get(displayNameAttribute);
                        if (displayAttribute != null) {
                            displayName = (String) displayAttribute.get();
                        }
                    }
                }
                String domainName =
                        realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                /* Username will be null in the special case where the username attribute has changed to another
                and having different userNameProperty than the current user-mgt.xml. */
                if (userName != null) {
                    user = UserCoreUtil.getCombinedName(domainName, userName, displayName);
                    userNameList.add(user);
                } else {
                    // Skip listing users which are not applicable to current user-mgt.xml
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("User %s doesn't have the user name property %s", user,
                                userNameProperty));
                    }
                }
            } catch (NamingException e) {
                log.error(String.format("Error in reading user information in the user store for the user %s, %s",
                        user, e.getMessage()));
                throw new UserStoreException(e.getMessage(), e);
            }
        }
        return userNameList;
    }

    /**
     * Parse the search result of non group filtering and get the user list.
     *
     * @param answer
     * @param returnedAttributes
     * @return
     * @throws UserStoreException
     */
    private List<String> getUserListFromNonGroupFilterResult(NamingEnumeration<SearchResult> answer,
                                                             List<String> returnedAttributes)
            throws UserStoreException {

        List<String> finalUserList = new ArrayList<>();
        String userAttributeSeparator = ",";
        NamingEnumeration<?> attrs = null;

        try {
            while (answer.hasMoreElements()) {
                SearchResult searchResult = answer.next();
                Attributes attributes = searchResult.getAttributes();
                if (attributes == null) {
                    continue;
                }
                Attribute attribute = attributes.get(returnedAttributes.get(0));
                if (attribute == null) {
                    continue;
                }
                StringBuffer attrBuffer = new StringBuffer();
                for (attrs = attribute.getAll(); attrs.hasMore(); ) {
                    String attr = (String) attrs.next();
                    if (StringUtils.isNotEmpty(attr.trim())) {
                        String attrSeparator = realmConfig.getUserStoreProperty(MULTI_ATTRIBUTE_SEPARATOR);
                        if (StringUtils.isNotEmpty(attrSeparator.trim())) {
                            userAttributeSeparator = attrSeparator;
                        }
                        attrBuffer.append(attr + userAttributeSeparator);
                        if (log.isDebugEnabled()) {
                            log.debug(returnedAttributes.get(0) + " : " + attr);
                        }
                    }
                }
                String propertyValue = attrBuffer.toString();
                Attribute serviceNameObject = attributes.get(returnedAttributes.get(1));
                String serviceNameAttributeValue = null;
                if (serviceNameObject != null) {
                    serviceNameAttributeValue = (String) serviceNameObject.get();
                }
                /* Length needs to be more than userAttributeSeparator.length() for a valid attribute,
                since we attach userAttributeSeparator. */
                if (propertyValue.trim().length() > userAttributeSeparator.length()) {
                    if (LDAPConstants.SERVER_PRINCIPAL_ATTRIBUTE_VALUE.equals(serviceNameAttributeValue)) {
                        continue;
                    }
                    propertyValue = propertyValue.substring(0, propertyValue.length() -
                            userAttributeSeparator.length());
                    finalUserList.add(propertyValue);
                }
            }
        } catch (NamingException e) {
            log.error(String.format("Error occurred while getting user list from non group filter %s", e.getMessage()));
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            // Close the naming enumeration and free up resources
            JNDIUtil.closeNamingEnumeration(attrs);
        }
        return finalUserList;
    }

    /**
     * Generate paginated user list. Since LDAP doesn't support pagination with start index.
     * So we need to process the page results according to the requested start index.
     *
     * @param pageIndex    index of the paginated page.
     * @param offset       start index.
     * @param pageSize     number of results per page which is equal to count/limit.
     * @param tempUserList users in the particular indexed page.
     * @param users        final paginated user list.
     */
    private void generatePaginatedUserList(int pageIndex, int offset, int pageSize, List<String> tempUserList,
                                           List<String> users) {

        int needMore;
        // Handle pagination depends on given offset, i.e. start index.
        if (pageIndex == (offset / pageSize)) {
            int startPosition = (offset % pageSize);
            if (startPosition < tempUserList.size() - 1) {
                users.addAll(tempUserList.subList(startPosition, tempUserList.size()));
            } else if (startPosition == tempUserList.size() - 1) {
                users.add(tempUserList.get(tempUserList.size() - 1));
            }
        } else if (pageIndex == (offset / pageSize) + 1) {
            needMore = pageSize - users.size();
            if (tempUserList.size() >= needMore) {
                users.addAll(tempUserList.subList(0, needMore));
            } else {
                users.addAll(tempUserList);
            }
        }
    }

    /**
     * Post processing the user list, when found membership group filter with user name filtering.
     * Get match users from member list. When found username filtering.
     *
     * @param expressionConditions
     * @param userNames
     * @return
     */
    private List<String> getMatchUsersFromMemberList(List<ExpressionCondition> expressionConditions,
                                                     List<String> userNames) {
        /*
        If group filtering and username filtering found, we need to get match users names only.
        'member' filtering retrieve all the members once the conditions matched because 'member' is a
        multi valued attribute.
        */
        List<String> derivedUserList = new ArrayList<>();

        for (ExpressionCondition expressionCondition : expressionConditions) {
            if (ExpressionAttribute.USERNAME.toString().equals(expressionCondition.getAttributeName())) {
                derivedUserList.addAll(getMatchUserNames(expressionCondition, userNames));
            }
        }
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
        linkedHashSet.addAll(derivedUserList);
        derivedUserList.clear();
        derivedUserList.addAll(linkedHashSet);
        return derivedUserList;
    }

    /**
     * Get match user names from given expression condition.
     *
     * @param expressionCondition
     * @param users
     * @return
     */
    private List<String> getMatchUserNames(ExpressionCondition expressionCondition, List<String> users) {

        List<String> newUserNameList = new ArrayList<>();

        for (String user : users) {
            if (ExpressionOperation.SW.toString().equals(expressionCondition.getOperation())
                    && user.startsWith(expressionCondition.getAttributeValue()) && !newUserNameList.contains(user)) {
                newUserNameList.add(user);
            } else if (ExpressionOperation.EQ.toString().equals(expressionCondition.getOperation())
                    && user.equals(expressionCondition.getAttributeValue()) && !newUserNameList.contains(user)) {
                newUserNameList.add(user);
            } else if (ExpressionOperation.CO.toString().equals(expressionCondition.getOperation())
                    && user.contains(expressionCondition.getAttributeValue()) && !newUserNameList.contains(user)) {
                newUserNameList.add(user);
            } else if (ExpressionOperation.EW.toString().equals(expressionCondition.getOperation())
                    && user.endsWith(expressionCondition.getAttributeValue()) && !newUserNameList.contains(user)) {
                newUserNameList.add(user);
            }
        }
        return newUserNameList;
    }

    /**
     * Post processing the user list, when found membership group filtering.
     *
     * @param isUsernameFiltering
     * @param isClaimFiltering
     * @param expressionConditions
     * @param tempUserList
     * @return
     * @throws UserStoreException
     */
    private List<String> membershipGroupFilterPostProcessing(boolean isUsernameFiltering, boolean isClaimFiltering,
                                                             List<ExpressionCondition> expressionConditions,
                                                             List<String> tempUserList) throws UserStoreException {

        List<String> users;
        if (isUsernameFiltering) {
            tempUserList = getMatchUsersFromMemberList(expressionConditions, tempUserList);
        }

        if (isClaimFiltering) {
            users = getUserListFromClaimFiltering(expressionConditions, tempUserList);
        } else {
            users = tempUserList;
        }
        return users;
    }

    /**
     * Post processing the user list, when found membership group filter with claim filtering.
     *
     * @param expressionConditions
     * @param tempUserList
     * @return
     * @throws UserStoreException
     */
    private List<String> getUserListFromClaimFiltering(List<ExpressionCondition> expressionConditions,
                                                       List<String> tempUserList) throws UserStoreException {

        List<String> claimSearchUserList = new ArrayList<>();
        List<ExpressionCondition> derivedConditionList = expressionConditions;
        Iterator<ExpressionCondition> iterator = derivedConditionList.iterator();

        while (iterator.hasNext()) {
            ExpressionCondition expressionCondition = iterator.next();
            if (ExpressionAttribute.ROLE.toString().equals(
                    expressionCondition.getAttributeName())) {
                iterator.remove();
            }
        }

        LDAPSearchSpecification claimSearch = new LDAPSearchSpecification(realmConfig, derivedConditionList);
        SearchControls claimSearchControls = claimSearch.getSearchControls();
        DirContext claimSearchDirContext = this.connectionSource.getContext();
        NamingEnumeration<SearchResult> tempAnswer = null;

        try {
            tempAnswer = claimSearchDirContext.search(claimSearch.getSearchBases(),
                    claimSearch.getSearchFilterQuery(), claimSearchControls);
            if (tempAnswer.hasMore()) {
                claimSearchUserList = getUserListFromNonGroupFilterResult(tempAnswer,
                        Arrays.asList(claimSearchControls.getReturningAttributes()));
            }
        } catch (NamingException e) {
            log.error(String.format("Error occurred while doing claim filtering for user(s) with filter: %s, %s",
                    claimSearch.getSearchFilterQuery(), e.getMessage()));
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeContext(claimSearchDirContext);
            JNDIUtil.closeNamingEnumeration(tempAnswer);
        }
        tempUserList.retainAll(claimSearchUserList);
        return tempUserList;
    }

    protected String convertBytesToHexString(byte[] bytes) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append("\\").append(String.format("%02x", b));
        }
        return builder.toString();
    }

    protected String transformUUIDToObjectGUID(String value) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(java.util.UUID.fromString(value).getMostSignificantBits());
        bb.putLong(java.util.UUID.fromString(value).getLeastSignificantBits());
        final byte[] bytes = swapBytes(bb.array());
        return convertBytesToHexString(bytes);
    }

    protected byte[] swapBytes(byte[] bytes) {
        // bytes[0] <-> bytes[3]
        byte swap = bytes[3];
        bytes[3] = bytes[0];
        bytes[0] = swap;
        // bytes[1] <-> bytes[2]
        swap = bytes[2];
        bytes[2] = bytes[1];
        bytes[1] = swap;
        // bytes[4] <-> bytes[5]
        swap = bytes[5];
        bytes[5] = bytes[4];
        bytes[4] = swap;
        // bytes[6] <-> bytes[7]
        swap = bytes[7];
        bytes[7] = bytes[6];
        bytes[6] = swap;
        return bytes;
    }

    @Override
    public boolean doCheckIsUserInRole(String userName, String roleName) throws UserStoreException {

        boolean debug = log.isDebugEnabled();
        if (userName == null) {
            return false;
        }

        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        LDAPRoleContext context = (LDAPRoleContext) createRoleContext(roleName);
        // Get the effective search base
        String searchBases = this.getEffectiveSearchBase(context.isShared());
        String memberOfProperty = realmConfig.getUserStoreProperty(LDAPConstants.MEMBEROF_ATTRIBUTE);

        if (memberOfProperty != null && memberOfProperty.length() > 0) {
            List<String> list;

            String userNameProperty = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
            String userSearchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
            String searchFilter = userSearchFilter.replace("?", escapeSpecialCharactersForFilter(userName));
            String binaryAttribute =
                    realmConfig.getUserStoreProperty(LDAPConstants.LDAP_ATTRIBUTES_BINARY);
            String primaryGroupId = realmConfig.getUserStoreProperty(LDAPConstants.PRIMARY_GROUP_ID);

            String returnedAtts[] = {memberOfProperty};

            if (binaryAttribute != null && primaryGroupId != null) {
                returnedAtts = new String[]{memberOfProperty, binaryAttribute, primaryGroupId};
            }
            searchCtls.setReturningAttributes(returnedAtts);

            if (debug) {
                log.debug("Do check whether the user: " + userName + " is in role: " + roleName);
                log.debug("Search filter: " + searchFilter);
                for (String retAttrib : returnedAtts) {
                    log.debug("Requesting attribute: " + retAttrib);
                }
            }


            if (binaryAttribute != null && primaryGroupId != null) {
                list =
                        this.getAttributeListOfOneElementWithPrimarGroup(searchBases, searchFilter,
                                searchCtls, binaryAttribute,
                                primaryGroupId, userNameProperty,
                                memberOfProperty);
            } else {
                // use cache
                LdapName ldn = getFromUserCache(userName);
                if (ldn != null) {
                    searchBases = ldn.toString();
                } else {
                    // create DN directly   but there is no way when multiple DNs are used. Need to improve letter
                    String userDNPattern = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);
                    if (StringUtils.isNotEmpty(userDNPattern) && !userDNPattern.contains("#")) {
                        searchBases = MessageFormat.format(userDNPattern, escapeSpecialCharactersForDN(userName));
                    }
                }


                // get DNs of the groups to which this user belongs
                List<String> groupDNs = this.getListOfNames(searchBases, searchFilter,
                        searchCtls, memberOfProperty, false);

                list = this.getAttributeListOfOneElement(searchBases, searchFilter, searchCtls);
            }

            if (debug) {
                if (list != null) {
                    boolean isUserInRole = false;
                    for (String item : list) {
                        log.debug("Result: " + item);
                        if (item.equalsIgnoreCase(roleName)) {
                            isUserInRole = true;
                        }
                    }
                    log.debug("Is user: " + userName + " in role: " + roleName + " ? " +
                            isUserInRole);
                } else {
                    log.debug("No results found !");
                }
            }

            // adding roles list in to the cache
            if (list != null) {
               //avoid adding roles to cache if the cached user realm is not defined yet. otherwise, it will go into an
               //infinite loop, if this method is called while creating a realm.
                RealmService defaultRealmService = UserStoreMgtDSComponent.getRealmService();
                if (defaultRealmService != null && defaultRealmService.getCachedUserRealm(tenantId) != null) {
                    addAllRolesToUserRolesCache(userName, list);
                }
                for (String role : list) {
                    if (role.equalsIgnoreCase(roleName)) {
                        return true;
                    }
                }
            }

        } else {
            // read the roles with this membership property
            String searchFilter = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER);
            String membershipProperty =
                    realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);

            if (membershipProperty == null || membershipProperty.length() < 1) {
                throw new UserStoreException("Please set member of attribute or membership attribute");
            }

            String roleNameProperty =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
            String userDNPattern = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);
            String nameInSpace;
            if (StringUtils.isNotEmpty(userDNPattern) && !userDNPattern.contains("#")) {
                nameInSpace = MessageFormat.format(userDNPattern, escapeSpecialCharactersForDN(userName));
            } else {
                nameInSpace = this.getNameInSpaceForUserName(userName);
            }

            String membershipValue;
            if (nameInSpace != null) {
                try {
                    LdapName ldn = new LdapName(nameInSpace);
                    membershipValue = escapeLdapNameForFilter(ldn);
                } catch (InvalidNameException e) {
                    log.error("Error while creating LDAP name from: " + nameInSpace);
                    throw new UserStoreException("Invalid naming exception for : " + nameInSpace, e);
                }
            } else {
                return false;
            }

            searchFilter = "(&" + searchFilter + "(" + membershipProperty + "=" + membershipValue + "))";
            String returnedAtts[] = {roleNameProperty};
            searchCtls.setReturningAttributes(returnedAtts);

            if (debug) {
                log.debug("Do check whether the user : " + userName + " is in role: " + roleName);
                log.debug("Search filter : " + searchFilter);
                for (String retAttrib : returnedAtts) {
                    log.debug("Requesting attribute: " + retAttrib);
                }
            }

            DirContext dirContext = null;
            NamingEnumeration<SearchResult> answer = null;
            try {
                dirContext = connectionSource.getContext();
                if (context.getRoleDNPatterns().size() > 0) {
                    for (String pattern : context.getRoleDNPatterns()) {

                        if (debug) {
                            log.debug("Using pattern: " + pattern);
                        }
                        searchBases = MessageFormat.format(pattern.trim(), escapeSpecialCharactersForDN(roleName));
                        try {
                            answer = dirContext.search(escapeDNForSearch(searchBases), searchFilter, searchCtls);
                        } catch (NamingException e) {
                            if (log.isDebugEnabled()) {
                                log.debug(e);
                            }
                            //ignore
                        }

                        if (answer != null && answer.hasMoreElements()) {
                            if (debug) {
                                log.debug("User: " + userName + " in role: " + roleName);
                            }
                            return true;
                        }
                        if (debug) {
                            log.debug("User: " + userName + " NOT in role: " + roleName);
                        }
                    }
                } else {

                    if (debug) {
                        log.debug("Do check whether the user: " + userName + " is in role: " + roleName);
                        log.debug("Search filter: " + searchFilter);
                        for (String retAttrib : returnedAtts) {
                            log.debug("Requesting attribute: " + retAttrib);
                        }
                    }

                    searchFilter =
                            "(&" + searchFilter + "(" + membershipProperty + "=" + membershipValue +
                                    ") (" + roleNameProperty + "=" + escapeSpecialCharactersForFilter(roleName) + "))";

                    // handle multiple search bases 
                    String[] searchBaseArray = searchBases.split("#");

                    for (String searchBase : searchBaseArray) {
                        answer = dirContext.search(escapeDNForSearch(searchBase), searchFilter, searchCtls);

                        if (answer.hasMoreElements()) {
                            if (debug) {
                                log.debug("User: " + userName + " in role: " + roleName);
                            }
                            return true;
                        }

                        if (debug) {
                            log.debug("User: " + userName + " NOT in role: " + roleName);
                        }
                    }
                }
            } catch (NamingException e) {
                if (log.isDebugEnabled()) {
                    log.debug(e.getMessage(), e);
                }
            } finally {
                JNDIUtil.closeNamingEnumeration(answer);
                JNDIUtil.closeContext(dirContext);
            }
        }

        return false;
    }

    private void addAllRolesToUserRolesCache(String userName, List<String> roleList) throws UserStoreException {
        String[] internalRoleList = doGetInternalRoleListOfUser(userName, "*");
        String[] combinedRoleList = UserCoreUtil.combineArrays((roleList.toArray(new String[roleList.size()])), internalRoleList);
        addToUserRolesCache(getTenantId(), userName, combinedRoleList);
    }

    // ************** NOT GOING TO IMPLEMENT ***************

    /**
     *
     */
    public Date getPasswordExpirationTime(String username) throws UserStoreException {

        if (username != null && username.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
            return super.getPasswordExpirationTime(username);
        }

        return null;
    }

    /**
     *
     */
    public int getTenantId(String username) throws UserStoreException {
        throw new UserStoreException("Invalid operation");
    }

    /**
     * //TODO:remove this method
     *
     * @param username
     * @return
     * @throws UserStoreException
     * @deprecated
     */
    public int getUserId(String username) throws UserStoreException {
        throw new UserStoreException("Invalid operation");
    }

    /**
     *
     */
    public void doDeleteUserClaimValue(String userName, String claimURI, String profileName)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");

    }

    /**
     *
     */
    public void doDeleteUserClaimValues(String userName, String[] claims, String profileName)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");

    }

    /**
     * @param userName
     * @param credential
     * @param roleList
     * @param claims
     * @param profileName
     * @throws UserStoreException
     */
    public void doAddUser(String userName, Object credential, String[] roleList,
                          Map<String, String> claims, String profileName) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     *
     */
    public void doAddUser(String userName, Object credential, String[] roleList,
                          Map<String, String> claims, String profileName,
                          boolean requirePasswordChange) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     *
     */
    public void doDeleteUser(String userName) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     *
     */
    public void doSetUserClaimValue(String userName, String claimURI, String claimValue,
                                    String profileName) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     *
     */
    public void doSetUserClaimValues(String userName, Map<String, String> claims, String profileName)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");

    }

    /**
     *
     */
    public void doUpdateCredential(String userName, Object newCredential, Object oldCredential)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     *
     */
    public void doUpdateCredentialByAdmin(String userName, Object newCredential)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");

    }

	/*
	 * ****************Unsupported methods list
	 * over***********************************************
	 */

    /**
     *
     */
    public void doUpdateRoleListOfUser(String userName, String[] deletedRoles, String[] newRoles)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     *
     */
    public void doUpdateUserListOfRole(String roleName, String[] deletedUsers, String[] newUsers)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    /**
     *
     */
    public Map<String, String> getProperties(Tenant tenant) throws UserStoreException {
        return this.realmConfig.getUserStoreProperties();
    }

    @Override
    public boolean isBulkImportSupported() throws UserStoreException {
        return new Boolean(realmConfig.getUserStoreProperty("IsBulkImportSupported"));
    }

    /**
     *
     */
    public void addRememberMe(String userName, String token)
            throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        JDBCUserStoreManager jdbcUserStore =
                new JDBCUserStoreManager(dataSource, realmConfig,
                        realmConfig.getTenantId(),
                        false);
        jdbcUserStore.addRememberMe(userName, token);
    }

    /**
     *
     */
    public boolean isValidRememberMeToken(String userName, String token)
            throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        try {
            if (this.isExistingUser(userName)) {
                JDBCUserStoreManager jdbcUserStore =
                        new JDBCUserStoreManager(
                                dataSource,
                                realmConfig,
                                realmConfig.getTenantId(),
                                false);
                return jdbcUserStore.isExistingRememberMeToken(userName, token);
            }
        } catch (Exception e) {
            log.error("Validating remember me token failed for" + userName);
                       /*
                        * not throwing exception. because we need to seamlessly direct them
                        * to login uis
                        */
        }
        return false;
    }

    private boolean isInSearchBase(LdapName name, LdapName searchBase) {
        List<Rdn> baseRdns = searchBase.getRdns();
        return name.startsWith(baseRdns);
    }

    /**
     * @param groupDNs
     * @return
     * @throws UserStoreException
     */
    private List<String> getGroupNameAttributeValuesOfGroups(List<LdapName> groupDNs)
            throws UserStoreException {
        log.debug("GetGroupNameAttributeValuesOfGroups with DN");
        boolean debug = log.isDebugEnabled();
        // get the DNs of the groups to which user belongs to, as per the search
        // parameters
        String groupNameAttribute =
                realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
        String[] returnedAttributes = {groupNameAttribute};
        List<String> groupNameAttributeValues = new ArrayList<String>();
        DirContext dirContext = null;
        try {
            dirContext = this.connectionSource.getContext();

            for (LdapName group : groupDNs) {
                if (!isInSearchBase(group, new LdapName(groupSearchBase))) {
                    continue;
                }
                if (debug) {
                    log.debug("Using DN: " + group);
                }
                /* check to see if the required attribute can be retrieved by the DN itself */
                Rdn rdn = group.getRdn(group.getRdns().size() - 1);
                if (rdn.getType().equalsIgnoreCase(groupNameAttribute)) {
                    groupNameAttributeValues.add(rdn.getValue().toString());
                    continue;
                }
                Attributes groupAttributes = dirContext.getAttributes(group, returnedAttributes);
                if (groupAttributes != null) {
                    Attribute groupAttribute = groupAttributes.get(groupNameAttribute);
                    if (groupAttribute != null) {
                        String groupNameAttributeValue = (String) groupAttribute.get();
                        if (debug) {
                            log.debug(groupNameAttribute + " : " + groupNameAttributeValue);
                        }
                        groupNameAttributeValues.add(groupNameAttributeValue);
                    }
                }
            }
        } catch (UserStoreException e) {
            String errorMessage = "Error in getting group name attribute values of groups";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (NamingException e) {
            String errorMessage = "Error in getting group name attribute values of groups";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeContext(dirContext);
        }
        return groupNameAttributeValues;
    }

    @Override
    public Properties getDefaultUserStoreProperties() {
        Properties properties = new Properties();
        properties.setMandatoryProperties(ReadOnlyLDAPUserStoreConstants.ROLDAP_USERSTORE_PROPERTIES.toArray
                (new Property[ReadOnlyLDAPUserStoreConstants.ROLDAP_USERSTORE_PROPERTIES.size()]));
        properties.setOptionalProperties(ReadOnlyLDAPUserStoreConstants.OPTIONAL_ROLDAP_USERSTORE_PROPERTIES.toArray
                (new Property[ReadOnlyLDAPUserStoreConstants.OPTIONAL_ROLDAP_USERSTORE_PROPERTIES.size()]));
        properties.setAdvancedProperties(RO_LDAP_UM_ADVANCED_PROPERTIES.toArray
                (new Property[RO_LDAP_UM_ADVANCED_PROPERTIES.size()]));
        return properties;
    }


    @Override
    public boolean isSharedRole(String roleName, String roleNameBase) {
        if (super.isSharedRole(roleName, roleNameBase) && roleNameBase != null) {
            String sharedRoleBase =
                    realmConfig.getUserStoreProperties()
                            .get(LDAPConstants.SHARED_GROUP_SEARCH_BASE);
            if (roleNameBase.contains(sharedRoleBase)) {
                return true;
            }
        }
        return false;
    }


    @Override
    protected boolean isOwnRole(String roleName) {
        String[] nameArray = roleName.split(UserCoreConstants.TENANT_DOMAIN_COMBINER);
        if (nameArray.length > 1) {
            return (Constants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(nameArray[1]));
        }
        return super.isOwnRole(roleName);
    }

    protected RoleContext createRoleContext(String roleName) { // TODO check whether shared roles enable

        LDAPRoleContext roleContext = new LDAPRoleContext();

        String[] rolePortions;

        if (isSharedGroupEnabled()) {
            rolePortions = roleName.split(UserCoreConstants.TENANT_DOMAIN_COMBINER);
            if (rolePortions.length > 1 && (rolePortions[1] == null || rolePortions[1].equals("null"))) {
                rolePortions = new String[]{rolePortions[0]};
            }
        } else {
            rolePortions = new String[]{roleName};
        }

        boolean shared = false;
        if (rolePortions.length == 1) {
            roleContext.setSearchBase(realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE));
            roleContext.setTenantDomain(Constants.SUPER_TENANT_DOMAIN_NAME);
        } else if (rolePortions.length > 1) {
            roleContext.setTenantDomain(rolePortions[1]);
//            if (tenantDomain.equalsIgnoreCase(CarbonContext.getCurrentContext().getTenantDomain())) {
//                // Role which is created by the logged in tenant. Tenant can be
//                // either super tenant or other sub tenant.
//                roleContext.setSearchBase(realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE));
//            } else {
            String base =
                    realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_SEARCH_BASE);

            if (!rolePortions[1].equalsIgnoreCase(Constants.SUPER_TENANT_DOMAIN_NAME)) {
                String groupNameAttributeName =
                        realmConfig.getUserStoreProperty(LDAPConstants.SHARED_TENANT_NAME_ATTRIBUTE);

                base = groupNameAttributeName + "=" + escapeSpecialCharactersForDN(rolePortions[1]) + "," + base;
            }

            String roleDNPattern = realmConfig.
                    getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_ATTRIBUTE) + "={0}," + base;
            roleContext.setSearchBase(base);
            roleContext.addRoleDNPatterns(roleDNPattern);
            shared = true;

        }
        if (shared) {
            roleContext.setSearchFilter(realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_SEARCH_FILTER));
            roleContext.setRoleNameProperty(realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_ATTRIBUTE));
            roleContext.setListFilter(realmConfig.getUserStoreProperty(LDAPConstants.SHARED_GROUP_NAME_LIST_FILTER));
            roleContext.setGroupEntryObjectClass(realmConfig.getUserStoreProperty(LDAPConstants.GROUP_ENTRY_OBJECT_CLASS));
        } else {
            roleContext.setSearchFilter(realmConfig.getUserStoreProperty(LDAPConstants.ROLE_NAME_FILTER));
            roleContext.setRoleNameProperty(realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE));
            roleContext.setListFilter(realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER));
            roleContext.setGroupEntryObjectClass(realmConfig.getUserStoreProperty(LDAPConstants.GROUP_ENTRY_OBJECT_CLASS));
            String roleDNPattern = realmConfig.getUserStoreProperty(LDAPConstants.ROLE_DN_PATTERN);
            if (roleDNPattern != null && roleDNPattern.trim().length() > 0) {
                if (roleDNPattern.contains("#")) {
                    String[] patterns = roleDNPattern.split("#");
                    for (String pattern : patterns) {
                        roleContext.addRoleDNPatterns(pattern);
                    }
                } else {
                    roleContext.addRoleDNPatterns(roleDNPattern);
                }
            }
        }
        roleContext.setRoleName(rolePortions[0]);
        roleContext.setShared(shared);
        return roleContext;
    }

    /**
     * This method escapes the special characters in a LdapName
     * according to the ldap filter escaping standards
     * @param ldn
     * @return
     */
    private String escapeLdapNameForFilter(LdapName ldn){

        if (ldn == null) {
            if (log.isDebugEnabled()) {
                log.debug("Received null value to escape special characters. Returning null");
            }
            return null;
        }

        boolean replaceEscapeCharacters = true;

        String replaceEscapeCharactersAtUserLoginString = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_REPLACE_ESCAPE_CHARACTERS_AT_USER_LOGIN);

        if (replaceEscapeCharactersAtUserLoginString != null) {
            replaceEscapeCharacters = Boolean
                    .parseBoolean(replaceEscapeCharactersAtUserLoginString);
            if (log.isDebugEnabled()) {
                log.debug("Replace escape characters configured to: "
                        + replaceEscapeCharactersAtUserLoginString);
            }
        }

        if (replaceEscapeCharacters) {
            String escapedDN = "";
            for (int i = ldn.size()-1; i > -1; i--) { //escaping the rdns separately and re-constructing the DN
                escapedDN = escapedDN + escapeSpecialCharactersForFilterWithStarAsRegex(ldn.get(i));
                if (i != 0) {
                    escapedDN += ",";
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("Escaped DN value for filter : " + escapedDN);
            }
            return escapedDN;
        } else {
            return ldn.toString();
        }
    }

    /**
     * Escaping ldap search filter special characters in a string
     * @param dnPartial
     * @return
     */
    private String escapeSpecialCharactersForFilterWithStarAsRegex(String dnPartial){
        boolean replaceEscapeCharacters = true;

        String replaceEscapeCharactersAtUserLoginString = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_REPLACE_ESCAPE_CHARACTERS_AT_USER_LOGIN);

        if (replaceEscapeCharactersAtUserLoginString != null) {
            replaceEscapeCharacters = Boolean
                    .parseBoolean(replaceEscapeCharactersAtUserLoginString);
            if (log.isDebugEnabled()) {
                log.debug("Replace escape characters configured to: "
                        + replaceEscapeCharactersAtUserLoginString);
            }
        }
        //TODO: implement character escaping for *

        if (replaceEscapeCharacters) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dnPartial.length(); i++) {
                char currentChar = dnPartial.charAt(i);
                switch (currentChar) {
                    case '\\':
                        if(dnPartial.charAt(i+1) == '*'){
                            sb.append("\\2a");
                            i++;
                            break;
                        }
                        sb.append("\\5c");
                        break;
                    case '(':
                        sb.append("\\28");
                        break;
                    case ')':
                        sb.append("\\29");
                        break;
                    case '\u0000':
                        sb.append("\\00");
                        break;
                    default:
                        sb.append(currentChar);
                }
            }
            return sb.toString();
        } else {
            return dnPartial;
        }
    }

    /**
     * Escaping ldap search filter special characters in a string
     *
     * @param dnPartial String to replace special characters of
     * @return
     */
    private String escapeSpecialCharactersForFilter(String dnPartial) {
        boolean replaceEscapeCharacters = true;
        dnPartial.replace("\\*", "*");

        String replaceEscapeCharactersAtUserLoginString = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_REPLACE_ESCAPE_CHARACTERS_AT_USER_LOGIN);

        if (replaceEscapeCharactersAtUserLoginString != null) {
            replaceEscapeCharacters = Boolean
                    .parseBoolean(replaceEscapeCharactersAtUserLoginString);
            if (log.isDebugEnabled()) {
                log.debug("Replace escape characters configured to: "
                        + replaceEscapeCharactersAtUserLoginString);
            }
        }
        //TODO: implement character escaping for *

        if (replaceEscapeCharacters) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dnPartial.length(); i++) {
                char currentChar = dnPartial.charAt(i);
                switch (currentChar) {
                    case '\\':
                        sb.append("\\5c");
                        break;
                    case '*':
                        sb.append("\\2a");
                        break;
                    case '(':
                        sb.append("\\28");
                        break;
                    case ')':
                        sb.append("\\29");
                        break;
                    case '\u0000':
                        sb.append("\\00");
                        break;
                    default:
                        sb.append(currentChar);
                }
            }
            return sb.toString();
        } else {
            return dnPartial;
        }
    }

    /**
     * Escaping ldap DN special characters in a String value
     *
     * @param text String to replace special characters of
     * @return
     */
    private String escapeSpecialCharactersForDN(String text) {
        boolean replaceEscapeCharacters = true;
        text.replace("\\*", "*");

        String replaceEscapeCharactersAtUserLoginString = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_REPLACE_ESCAPE_CHARACTERS_AT_USER_LOGIN);

        if (replaceEscapeCharactersAtUserLoginString != null) {
            replaceEscapeCharacters = Boolean
                    .parseBoolean(replaceEscapeCharactersAtUserLoginString);
            if (log.isDebugEnabled()) {
                log.debug("Replace escape characters configured to: "
                        + replaceEscapeCharactersAtUserLoginString);
            }
        }

        if (replaceEscapeCharacters) {
            StringBuilder sb = new StringBuilder();
            if ((text.length() > 0) && ((text.charAt(0) == ' ') || (text.charAt(0) == '#'))) {
                sb.append('\\'); // add the leading backslash if needed
            }
            for (int i = 0; i < text.length(); i++) {
                char currentChar = text.charAt(i);
                switch (currentChar) {
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case ',':
                        sb.append("\\,");
                        break;
                    case '+':
                        sb.append("\\+");
                        break;
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '<':
                        sb.append("\\<");
                        break;
                    case '>':
                        sb.append("\\>");
                        break;
                    case ';':
                        sb.append("\\;");
                        break;
                    case '*':
                        sb.append("\\2a");
                        break;
                    default:
                        sb.append(currentChar);
                }
            }
            if ((text.length() > 1) && (text.charAt(text.length() - 1) == ' ')) {
                sb.insert(sb.length() - 1, '\\'); // add the trailing backslash if needed
            }
            if (log.isDebugEnabled()) {
                log.debug("value after escaping special characters in " + text + " : " + sb.toString());
            }
            return sb.toString();
        } else {
            return text;
        }

    }

    /**
     * This method performs the additional level escaping for ldap search. In ldap search / and " characters
     * have to be escaped again
     * @param dn DN
     * @return composite name
     * @throws InvalidNameException failed to build composite name
     */
    protected Name escapeDNForSearch(String dn) throws InvalidNameException {
        // This is done to escape '/' which is not a LDAP special character but a JNDI special character.
        // Refer: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4307193
        return new CompositeName().add(dn);
    }

    private boolean isIgnorePartialResultException() {

        if (PROPERTY_REFERRAL_IGNORE.equals(realmConfig.getUserStoreProperty(LDAPConstants.PROPERTY_REFERRAL))) {
            return true;
        }
        return false;
    }


    private static void setAdvancedProperties() {
        //Set Advanced Properties

        RO_LDAP_UM_ADVANCED_PROPERTIES.clear();
        setAdvancedProperty(UserStoreConfigConstants.SCIMEnabled, "Enable SCIM", "false", UserStoreConfigConstants
                .SCIMEnabledDescription);

        setAdvancedProperty(UserStoreConfigConstants.passwordHashMethod, "Password Hashing Algorithm", "PLAIN_TEXT",
                UserStoreConfigConstants.passwordHashMethodDescription);
        setAdvancedProperty(MULTI_ATTRIBUTE_SEPARATOR, "Multiple Attribute Separator", ",", MULTI_ATTRIBUTE_SEPARATOR_DESCRIPTION);

        setAdvancedProperty(UserStoreConfigConstants.maxUserNameListLength, "Maximum User List Length", "100", UserStoreConfigConstants
                .maxUserNameListLengthDescription);
        setAdvancedProperty(UserStoreConfigConstants.maxRoleNameListLength, "Maximum Role List Length", "100", UserStoreConfigConstants
                .maxRoleNameListLengthDescription);

        setAdvancedProperty(UserStoreConfigConstants.userRolesCacheEnabled, "Enable User Role Cache", "true", UserStoreConfigConstants
                .userRolesCacheEnabledDescription);

        setAdvancedProperty(UserStoreConfigConstants.connectionPoolingEnabled, "Enable LDAP Connection Pooling", "false",
                UserStoreConfigConstants.connectionPoolingEnabledDescription);
        setAdvancedProperty(LDAPConnectionTimeout, "LDAP Connection Timeout", "5000", LDAPConnectionTimeoutDescription);

        setAdvancedProperty(readTimeout, "LDAP Read Timeout", "5000", readTimeoutDescription);
        setAdvancedProperty(RETRY_ATTEMPTS, "Retry Attempts", "0", "Number of retries for" +
                " authentication in case ldap read timed out.");
        setAdvancedProperty("CountRetrieverClass", "Count Implementation", "",
                "Name of the class that implements the count functionality");
        setAdvancedProperty(LDAPConstants.LDAP_ATTRIBUTES_BINARY, "LDAP binary attributes", " ",
                LDAPBinaryAttributesDescription);
        setAdvancedProperty(UserStoreConfigConstants.claimOperationsSupported, UserStoreConfigConstants
                .getClaimOperationsSupportedDisplayName, "false", UserStoreConfigConstants.claimOperationsSupportedDescription);
        setAdvancedProperty(MEMBERSHIP_ATTRIBUTE_RANGE, MEMBERSHIP_ATTRIBUTE_RANGE_DISPLAY_NAME,
                String.valueOf(MEMBERSHIP_ATTRIBUTE_RANGE_VALUE), "Number of maximum users of role returned by the LDAP");
        setAdvancedProperty(LDAPConstants.USER_CACHE_EXPIRY_MILLISECONDS, USER_CACHE_EXPIRY_TIME_ATTRIBUTE_NAME, "",
                USER_CACHE_EXPIRY_TIME_ATTRIBUTE_DESCRIPTION);
        setAdvancedProperty(LDAPConstants.USER_DN_CACHE_ENABLED, USER_DN_CACHE_ENABLED_ATTRIBUTE_NAME, "true",
                USER_DN_CACHE_ENABLED_ATTRIBUTE_DESCRIPTION);
        setAdvancedProperty(UserStoreConfigConstants.STARTTLS_ENABLED,
                UserStoreConfigConstants.STARTTLS_ENABLED_DISPLAY_NAME, "false",
                UserStoreConfigConstants.STARTTLS_ENABLED_DESCRIPTION);
        setAdvancedProperty(UserStoreConfigConstants.CONNECTION_RETRY_DELAY,
                UserStoreConfigConstants.CONNECTION_RETRY_DELAY_DISPLAY_NAME, "120000",
                UserStoreConfigConstants.CONNECTION_RETRY_DELAY_DESCRIPTION);
    }

    private static void setAdvancedProperty(String name, String displayName, String value,
                                            String description) {
        Property property = new Property(name, value, displayName + "#" + description, null);
        RO_LDAP_UM_ADVANCED_PROPERTIES.add(property);

    }

    /**
     * Initialize the user cache.
     * Uses Javax cache. Any existing cache with the same name will be removed and re-attach an new one.
     */
    protected void initUserCache() throws UserStoreException {
        if (!userDnCacheEnabled) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "User DN cache is disabled in configuration on UserStore having SearchBase: " + userSearchBase);
            }
            return;
        }
        boolean isUserDnCacheCustomExpiryValuePresent = false;

        if (StringUtils.isNotEmpty(cacheExpiryTimeAttribute)) {
            if (log.isDebugEnabled()) {
                log.debug("Cache expiry time : " + cacheExpiryTimeAttribute
                        + " configured for the user DN cache having search base: " + userSearchBase);
            }
            try {
                userDnCacheExpiryTime = Long.parseLong(cacheExpiryTimeAttribute);
                isUserDnCacheCustomExpiryValuePresent = true;
            } catch (NumberFormatException nfe) {
                log.error("Could not convert the cache expiry time to Number (long) : " + cacheExpiryTimeAttribute
                        + " . Will default to system wide expiry settings.", nfe);
            }
        }

        RealmService realmService = UserStoreMgtDSComponent.getRealmService();
        if (realmService != null && realmService.getTenantManager() != null) {
            try {
                tenantDomain = realmService.getTenantManager().getDomain(tenantId);
                if (log.isDebugEnabled()) {
                    log.debug("Tenant domain : " + tenantDomain + " found for the tenant ID : " + tenantId);
                }
            } catch (org.wso2.micro.integrator.security.user.api.UserStoreException e) {
                throw new UserStoreException("Could not get the tenant domain for tenant id : " + tenantId, e);
            }
        }

        if (tenantDomain == null && tenantId == Constants.SUPER_TENANT_ID) {
            // Assign super-tenant domain, If this is super tenant and the tenant domain is not yet known.
            tenantDomain = Constants.SUPER_TENANT_DOMAIN_NAME;
        }

        if (tenantDomain == null) {
            // Do not create the cache if there is no tenant domain, which means the given tenant ID is invalid.
            // Any cache access i.e. getX, putX or deleteX will simply behave as no-op in this case.
            if (log.isDebugEnabled()) {
                log.debug("Could not find a tenant domain for the tenant ID : " + tenantId
                        + ". Not initializing the User DN cache.");
            }
            return;
        }

        userDnCacheName = USER_CACHE_NAME_PREFIX + this.hashCode();
        cacheManager = Caching.getCacheManagerFactory().getCacheManager(USER_CACHE_MANAGER);

        // Unconditionally remove the cache, so that it can be reconfigured.
        cacheManager.removeCache(userDnCacheName);

        if (isUserDnCacheCustomExpiryValuePresent) {
            // We use cache builder to create the cache with custom expiry values.
            if (log.isDebugEnabled()) {
                log.debug("Using cache expiry time : " + userDnCacheExpiryTime
                        + " configured for the user DN cache having search base: " + userSearchBase);
            }
            userDnCacheBuilder = cacheManager.createCacheBuilder(userDnCacheName);
            userDnCacheBuilder.setExpiry(CacheConfiguration.ExpiryType.ACCESSED,
                    new CacheConfiguration.Duration(TimeUnit.MILLISECONDS, userDnCacheExpiryTime)).
                    setExpiry(CacheConfiguration.ExpiryType.MODIFIED,
                            new CacheConfiguration.Duration(TimeUnit.MILLISECONDS, userDnCacheExpiryTime)).
                    setStoreByValue(false);
        }
    }


    /**
     * Puts the DN into the cache.
     *
     * @param name  the user name.
     * @param value the LDAP name (DN)
     */
    protected void putToUserCache(String name, LdapName value) {
        try {
            Cache<String, LdapName> userDnCache = createOrGetUserDnCache();
            if (userDnCache == null) {
                // User cache may be null while initializing.
                return;
            }
            userDnCache.put(name, value);
        } catch (IllegalStateException e) {
            // There is no harm ignoring the put, as the cache(local) is already is of no use. Mis-penalty is low.
            log.error("Error occurred while putting User DN to the cache having search base : " + userSearchBase, e);
        }
    }

    /**
     * Returns the LDAP Name (DN) for the given user name, if it exists in the cache.
     *
     * @param userName
     * @return cached DN, if exists. null if the cache does not contain the DN for the userName.
     */
    protected LdapName getFromUserCache(String userName) {
        try {
            Cache<String, LdapName> userDnCache = createOrGetUserDnCache();
            if (userDnCache == null) {
                // User cache may be null while initializing.
                return null;
            }
            return userDnCache.get(userName);
        } catch (IllegalStateException e) {
            log.error("Error occurred while getting User DN from cache having search base : " + userSearchBase, e);
            return null;
        }
    }

    /**
     * Removes the cache entry given the user name.
     *
     * @param userName the User name to remove.
     * @return true if removal was successful.
     */
    protected boolean removeFromUserCache(String userName) {
        try {
            Cache<String, LdapName> userDnCache = createOrGetUserDnCache();
            if (userDnCache == null) {
                // User cache may be null while initializing.
                // Return true as removal result is successful when there is no cache. Nothing was held.
                return true;
            }
            return userDnCache.remove(userName);
        } catch (IllegalStateException e) {
            // There is no harm ignoring the removal, as the cache(local) is already is of no use.
            log.error("Error occurred while removing User DN from cache having search base : " + userSearchBase, e);
            return true;
        }
    }


    /**
     * Returns the User DN Cache. Creates one if not exists in the cache manager.
     * Cache manager removes the cache if it is idle and empty for some time. Hence we need to create,
     * with our owen settings if needed.
     * @return
     */
    private Cache<String, LdapName> createOrGetUserDnCache() {
        if (cacheManager == null || !userDnCacheEnabled) {
            if (log.isDebugEnabled()) {
                log.debug("Not using the cache on UserDN. cacheManager: " + cacheManager + " , Enabled : "
                        + userDnCacheEnabled);
            }
            return null;
        }

        Cache<String, LdapName> userDnCache;

        if (userDnCacheBuilder != null) {
            // We use cache builder to create the cache with custom expiry values.
            if (log.isDebugEnabled()) {
                log.debug("Using cache bulder to get the cache, for UserSearchBase: " + userSearchBase);
            }
            userDnCache = userDnCacheBuilder.build();
        } else {
            // We use system-wide settings to build the cache.
            if (log.isDebugEnabled()) {
                log.debug("Using default configurations for the user DN cache, having search base : " + userSearchBase);
            }
            userDnCache = cacheManager.getCache(userDnCacheName);
        }

        return userDnCache;
    }

    /**
     * Removes
     *  1. Current User cache from the respective cache manager.
     *
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        if (cacheManager != null && userDnCacheName != null) {
            // Remove the userDN cache, as we created a DN cache per an instance of this class.
            // Any change in LDAP User Store config, too should invalidate the cache and remove it from memory.
            cacheManager.removeCache(userDnCacheName);

        }
        super.finalize();
    }
}
