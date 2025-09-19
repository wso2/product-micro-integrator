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

package org.wso2.micro.integrator.security.user.core.jdbc;

import org.apache.axiom.om.util.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.User;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.core.Constants;
import org.wso2.micro.core.util.DatabaseCreator;
import org.wso2.micro.integrator.security.UnsupportedSecretTypeException;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.api.Properties;
import org.wso2.micro.integrator.security.user.api.Property;
import org.wso2.micro.integrator.security.user.api.RealmConfiguration;
import org.wso2.micro.integrator.security.user.core.UserRealm;
import org.wso2.micro.integrator.security.user.core.UserStoreException;
import org.wso2.micro.integrator.security.user.core.claim.ClaimManager;
import org.wso2.micro.integrator.security.user.core.common.AbstractUserStoreManager;
import org.wso2.micro.integrator.security.user.core.common.PaginatedSearchResult;
import org.wso2.micro.integrator.security.user.core.common.RoleContext;
import org.wso2.micro.integrator.security.user.core.dto.RoleDTO;
import org.wso2.micro.integrator.security.user.core.hybrid.FileBasedHybridRoleManager;
import org.wso2.micro.integrator.security.user.core.hybrid.HybridJDBCConstants;
import org.wso2.micro.integrator.security.user.core.jdbc.caseinsensitive.JDBCCaseInsensitiveConstants;
import org.wso2.micro.integrator.security.user.core.model.Condition;
import org.wso2.micro.integrator.security.user.core.model.ExpressionAttribute;
import org.wso2.micro.integrator.security.user.core.model.ExpressionCondition;
import org.wso2.micro.integrator.security.user.core.model.ExpressionOperation;
import org.wso2.micro.integrator.security.user.core.model.OperationalCondition;
import org.wso2.micro.integrator.security.user.core.model.SqlBuilder;
import org.wso2.micro.integrator.security.user.core.profile.ProfileConfigurationManager;
import org.wso2.micro.integrator.security.user.core.tenant.Tenant;
import org.wso2.micro.integrator.security.user.core.util.DatabaseUtil;
import org.wso2.micro.integrator.security.user.core.util.JDBCRealmUtil;
import org.wso2.micro.integrator.security.user.core.util.UserCoreUtil;
import org.wso2.micro.integrator.security.util.Secret;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTimeoutException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.sql.DataSource;

import static org.wso2.micro.integrator.security.user.core.constants.UserCoreErrorConstants.ErrorMessages.ERROR_CODE_DUPLICATE_WHILE_ADDING_A_USER;
import static org.wso2.micro.integrator.security.user.core.constants.UserCoreErrorConstants.ErrorMessages.ERROR_CODE_DUPLICATE_WHILE_ADDING_ROLE;
import static org.wso2.micro.integrator.security.user.core.constants.UserCoreErrorConstants.ErrorMessages.ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE;

public class JDBCUserStoreManager extends AbstractUserStoreManager {

    // private boolean useOnlyInternalRoles;
    private static Log log = LogFactory.getLog(JDBCUserStoreManager.class);

    private static final String QUERY_FILTER_STRING_ANY = "*";
    private static final String SQL_FILTER_STRING_ANY = "%";
    private static final char SQL_FILTER_CHAR_ESCAPE = '\\';
    private static final String CASE_INSENSITIVE_USERNAME = "CaseInsensitiveUsername";
    private static final String SHA_1_PRNG = "SHA1PRNG";

    protected DataSource jdbcds = null;
    protected Random random = new Random();

    private static final String DB2 = "db2";
    private static final String MSSQL = "mssql";
    private static final String ORACLE = "oracle";
    private static final String MYSQL = "mysql";
    private boolean fileBasedUserStoreMode = false;
    private static final String BOUNCY_CASTLE_PROVIDER = "BC";
    private static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
    private static final String SECURITY_JCE_PROVIDER = "security.jce.provider";
    public JDBCUserStoreManager() {

    }

    private void checkFileBasedUserStoreEnabled() throws UserStoreException {
        Object fileUserStore = ConfigParser.getParsedConfigs().get(UserCoreConstants.FILE_BASED_USER_STORE_AS_PRIMARY);
        if (fileUserStore != null && Boolean.parseBoolean(fileUserStore.toString())) {
            fileBasedUserStoreMode = true;
        }
    }
    /**
     * @param realmConfig
     * @param tenantId
     * @throws UserStoreException
     */
    public JDBCUserStoreManager(RealmConfiguration realmConfig, int tenantId) throws UserStoreException {
        this.realmConfig = realmConfig;
        this.tenantId = tenantId;
        realmConfig.setUserStoreProperties(JDBCRealmUtil.getSQL(realmConfig
                .getUserStoreProperties()));

//		if (isReadOnly() && realmConfig.isPrimary()) {
//			String adminRoleName = UserCoreUtil.removeDomainFromName(realmConfig.getAdminRoleName());
//			realmConfig.setAdminRoleName(UserCoreUtil.addInternalDomainName(adminRoleName));
//		}

        // new properties after carbon core 4.0.7 release.
        if (realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.READ_GROUPS_ENABLED) != null) {
            readGroupsEnabled = Boolean.parseBoolean(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.READ_GROUPS_ENABLED));
        }

        if (log.isDebugEnabled()) {
            if (readGroupsEnabled) {
                log.debug("ReadGroups is enabled for " + getMyDomainName());
            } else {
                log.debug("ReadGroups is disabled for " + getMyDomainName());
            }
        }

        if (realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.WRITE_GROUPS_ENABLED) != null) {
            writeGroupsEnabled = Boolean.parseBoolean(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.WRITE_GROUPS_ENABLED));
        } else {
            if (!isReadOnly()) {
                writeGroupsEnabled = true;
            }
        }

        if (log.isDebugEnabled()) {
            if (writeGroupsEnabled) {
                log.debug("WriteGroups is enabled for " + getMyDomainName());
            } else {
                log.debug("WriteGroups is disabled for " + getMyDomainName());
            }
        }

        // This property is now deprecated
        if (realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_INTERNAL_ROLES_ONLY) != null) {
            boolean internalRolesOnly = Boolean
                    .parseBoolean(realmConfig
                            .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_INTERNAL_ROLES_ONLY));
            if (internalRolesOnly) {
                readGroupsEnabled = false;
                writeGroupsEnabled = false;
            } else {
                readGroupsEnabled = true;
                writeGroupsEnabled = true;
            }
        }


        if (writeGroupsEnabled) {
            readGroupsEnabled = true;
        }

    }

    /**
     * This constructor is used by the support IS
     *
     * @param ds
     * @param realmConfig
     * @param tenantId
     * @param addInitData
     * @param tenantId
     */
    public JDBCUserStoreManager(DataSource ds, RealmConfiguration realmConfig, int tenantId,
                                boolean addInitData) throws UserStoreException {

        this(realmConfig, tenantId);
        if (log.isDebugEnabled()) {
            log.debug("Started " + System.currentTimeMillis());
        }
        realmConfig.setUserStoreProperties(JDBCRealmUtil.getSQL(realmConfig
                .getUserStoreProperties()));
        this.jdbcds = ds;
        this.dataSource = ds;

        checkFileBasedUserStoreEnabled();
        if (!fileBasedUserStoreMode) {
            if (dataSource == null) {
                dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
            }
            if (dataSource == null) {
                throw new UserStoreException("User Management Data Source is null");
            }
            this.persistDomain();
        }
        doInitialSetup(fileBasedUserStoreMode);
        if (addInitData && realmConfig.isPrimary()) {
            addInitialAdminData(Boolean.parseBoolean(realmConfig.getAddAdmin()),
                    !isInitSetupDone());
        }

        if (log.isDebugEnabled()) {
            log.debug("Ended " + System.currentTimeMillis());
        }
    }

    /**
     * This constructor to accommodate PasswordUpdater called from chpasswd script
     *
     * @param ds
     * @param realmConfig
     * @throws UserStoreException
     */
    public JDBCUserStoreManager(DataSource ds, RealmConfiguration realmConfig)
            throws UserStoreException {

        this(realmConfig, Constants.SUPER_TENANT_ID);
        realmConfig.setUserStoreProperties(JDBCRealmUtil.getSQL(realmConfig
                .getUserStoreProperties()));
        this.jdbcds = ds;
    }

    /**
     * @param realmConfig
     * @param properties
     * @param claimManager
     * @param profileManager
     * @param realm
     * @param tenantId
     * @throws UserStoreException
     */
    public JDBCUserStoreManager(RealmConfiguration realmConfig, Map<String, Object> properties,
                                ClaimManager claimManager, ProfileConfigurationManager profileManager, UserRealm realm,
                                Integer tenantId) throws UserStoreException {
        this(realmConfig, properties, claimManager, profileManager, realm, tenantId, false);
    }

    /**
     * @param realmConfig
     * @param properties
     * @param claimManager
     * @param profileManager
     * @param realm
     * @param tenantId
     * @param skipInitData
     * @throws UserStoreException
     */
    public JDBCUserStoreManager(RealmConfiguration realmConfig, Map<String, Object> properties,
                                ClaimManager claimManager, ProfileConfigurationManager profileManager, UserRealm realm,
                                Integer tenantId, boolean skipInitData) throws UserStoreException {
        this(realmConfig, tenantId);
        if (log.isDebugEnabled()) {
            log.debug("Started " + System.currentTimeMillis());
        }
        this.claimManager = claimManager;
        this.userRealm = realm;

        try {
            jdbcds = loadUserStoreSpacificDataSoruce();

            if (jdbcds == null) {
                jdbcds = (DataSource) properties.get(UserCoreConstants.DATA_SOURCE);
            }
            if (jdbcds == null) {
                jdbcds = DatabaseUtil.getRealmDataSource(realmConfig);
                properties.put(UserCoreConstants.DATA_SOURCE, jdbcds);
            }

            if (log.isDebugEnabled()) {
                log.debug("The jdbcDataSource being used by JDBCUserStoreManager :: "
                        + jdbcds.hashCode());
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Loading JDBC datasource failed", e);
            }
        }

        checkFileBasedUserStoreEnabled();
        if (!fileBasedUserStoreMode) {
            dataSource = (DataSource) properties.get(UserCoreConstants.DATA_SOURCE);
            if (dataSource == null) {
                dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
            }
            if (dataSource == null) {
                throw new UserStoreException("User Management Data Source is null");
            }
            properties.put(UserCoreConstants.DATA_SOURCE, dataSource);
        }

        realmConfig.setUserStoreProperties(JDBCRealmUtil.getSQL(realmConfig
                .getUserStoreProperties()));

        if (!fileBasedUserStoreMode) {
            this.persistDomain();
        }
        doInitialSetup(fileBasedUserStoreMode);
        if (!skipInitData && realmConfig.isPrimary()) {
            addInitialAdminData(Boolean.parseBoolean(realmConfig.getAddAdmin()),
                    !isInitSetupDone());
        }

        if (log.isDebugEnabled()) {
            log.debug("Ended " + System.currentTimeMillis());
        }

    }

    // Loading JDBC data store on demand.
    private DataSource getJDBCDataSource() throws UserStoreException {
        if (jdbcds == null) {
            jdbcds = loadUserStoreSpacificDataSoruce();
        }
        return jdbcds;
    }

    /**
     *
     */
    public String[] doListUsers(String filter, int maxItemLimit) throws UserStoreException {

        String[] users = new String[0];
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        if (maxItemLimit == 0) {
            return new String[0];
        }

        int givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;

        int searchTime = UserCoreConstants.MAX_SEARCH_TIME;

        try {
            givenMax = Integer.parseInt(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST));
        } catch (Exception e) {
            givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        }

        try {
            searchTime = Integer.parseInt(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_SEARCH_TIME));
        } catch (Exception e) {
            searchTime = UserCoreConstants.MAX_SEARCH_TIME;
        }

        if (maxItemLimit < 0 || maxItemLimit > givenMax) {
            maxItemLimit = givenMax;
        }

        try {

            if (filter != null && filter.trim().length() != 0) {
                filter = filter.trim();
                filter = filter.replace("*", "%");
                filter = filter.replace("?", "_");
            } else {
                filter = "%";
            }

            List<String> lst = new LinkedList<String>();

            dbConnection = getDBConnection();

            if (dbConnection == null) {
                throw new UserStoreException("null connection");
            }

            if (isCaseSensitiveUsername()) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USER_FILTER);
            } else {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_USER_FILTER_CASE_INSENSITIVE);
            }

            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, filter);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }
            prepStmt.setMaxRows(maxItemLimit);
            try {
                prepStmt.setQueryTimeout(searchTime);
            } catch (Exception e) {
                // this can be ignored since timeout method is not implemented
                log.debug(e);
            }

            try {
                rs = prepStmt.executeQuery();
            } catch (SQLException e) {
                if (e instanceof SQLTimeoutException) {
                    log.error("The cause might be a time out. Hence ignored", e);
                    return users;
                }
                String errorMessage =
                        "Error while fetching users according to filter : " + filter + " & max Item limit " +
                        ": " + maxItemLimit;
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e);
                }
                throw new UserStoreException(errorMessage, e);
            }

            while (rs.next()) {

                String name = rs.getString(1);
                if (UserCoreConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(name)) {
                    continue;
                }
                // append the domain if exist
                String domain = realmConfig
                        .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                name = UserCoreUtil.addDomainToName(name, domain);
                lst.add(name);
            }
            rs.close();

            if (lst.size() > 0) {
                users = lst.toArray(new String[lst.size()]);
            }

            Arrays.sort(users);

        } catch (SQLException e) {
            String msg = "Error occurred while retrieving users for filter : " + filter + " & max Item limit : " +
                         maxItemLimit;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return users;

    }


    @Override
    public boolean doCheckIsUserInRole(String userName, String roleName) throws UserStoreException {

        String[] roles = doGetExternalRoleListOfUser(userName, roleName);
        if (roles != null) {
            for (String role : roles) {
                if (role.equalsIgnoreCase(roleName)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected String[] doGetDisplayNamesForInternalRole(String[] userNames) throws UserStoreException {
        return userNames;
    }

    /**
     *
     */
    public String[] doGetRoleNames(String filter, int maxItemLimit) throws UserStoreException {

        String[] roles = new String[0];
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        if (maxItemLimit == 0) {
            return roles;
        }

        try {

            if (filter != null && filter.trim().length() != 0) {
                filter = filter.trim();
                filter = filter.replace("*", "%");
                filter = filter.replace("?", "_");
            } else {
                filter = "%";
            }

            List<String> lst = new LinkedList<String>();

            dbConnection = getDBConnection();

            if (dbConnection == null) {
                throw new UserStoreException("null connection");
            }

            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_ROLE_LIST); // TODO

            prepStmt = dbConnection.prepareStatement(sqlStmt);
            //prepStmt.setString(1, filter);
            byte count = 0;
            prepStmt.setString(++count, filter);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(++count, tenantId);
            }
            setPSRestrictions(prepStmt, maxItemLimit);
            try {
                rs = prepStmt.executeQuery();
            } catch (SQLException e) {
                if (e instanceof SQLTimeoutException) {
                    log.error("The cause might be a time out. Hence ignored", e);
                } else {
                    String errorMessage =
                            "Error while fetching roles from JDBC user store according to filter : " + filter +
                            " & max item limit : " + maxItemLimit;
                    if (log.isDebugEnabled()) {
                        log.debug(errorMessage, e);
                    }
                    throw new UserStoreException(errorMessage, e);
                }
            }

            //Expected columns UM_ROLE_NAME, UM_TENANT_ID, UM_SHARED_ROLE
            if (rs != null) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    // append the domain if exist
                    String domain =
                            realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                    name = UserCoreUtil.addDomainToName(name, domain);
                    lst.add(name);
                }
            }
//
//			if (isSharedGroupEnabled()) {
//				lst.addAll(Arrays.asList(doGetSharedRoleNames(null, filter, maxItemLimit)));
//			}
//
            if (lst.size() > 0) {
                roles = lst.toArray(new String[lst.size()]);
            }

        } catch (SQLException e) {
            String msg = "Error occurred while retrieving role names for filter : " + filter + " & max item limit : " +
                         maxItemLimit;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return roles;

    }

    private void setPSRestrictions(PreparedStatement ps, int maxItemLimit) throws SQLException {

        int givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;

        int searchTime = UserCoreConstants.MAX_SEARCH_TIME;

        try {
            givenMax =
                    Integer.parseInt(realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_ROLE_LIST));
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

        ps.setMaxRows(maxItemLimit);
        try {
            ps.setQueryTimeout(searchTime);
        } catch (Exception e) {
            // this can be ignored since timeout method is not implemented
            log.debug(e);
        }
    }


    @Override
    protected String[] doGetSharedRoleNames(String tenantDomain, String filter, int maxItemLimit)
            throws UserStoreException {
        String[] roles = new String[0];
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        if (maxItemLimit == 0) {
            return roles;
        }

        try {

            if (!isSharedGroupEnabled()) {
                return roles;
            }

            if (filter != null && filter.trim().length() != 0) {
                filter = filter.trim();
                filter = filter.replace("*", "%");
                filter = filter.replace("?", "_");
            } else {
                filter = "%";
            }

            List<String> lst = new LinkedList<String>();

            dbConnection = getDBConnection();

            if (dbConnection == null) {
                throw new UserStoreException("null connection");
            }

            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_SHARED_ROLE_LIST);

            prepStmt = dbConnection.prepareStatement(sqlStmt);
            byte count = 0;
            prepStmt.setString(++count, filter);
            setPSRestrictions(prepStmt, maxItemLimit);
            try {
                rs = prepStmt.executeQuery();
            } catch (SQLException e) {
                if (e instanceof SQLTimeoutException) {
                    // may be due time out, therefore ignore this exception
                    log.error("The cause might be a time out. Hence ignored", e);
                } else {
                    String errorMessage =
                            "Error while fetching roles from JDBC user store for tenant domain : " + tenantDomain +
                            " & filter : " + filter + "& max item limit : " + maxItemLimit;
                    if (log.isDebugEnabled()) {
                        log.debug(errorMessage, e);
                    }
                    throw new UserStoreException(errorMessage, e);
                }
            }

            // Expected columns UM_ROLE_NAME, UM_TENANT_ID, UM_SHARED_ROLE
            if (rs != null) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    int roleTenantId = rs.getInt(2);
                    // append the domain if exist
                    String domain =
                            realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                    name = UserCoreUtil.addDomainToName(name, domain);
                    name = UserCoreUtil.addTenantDomainToEntry(name, String.valueOf(roleTenantId));
                    lst.add(name);
                }
            }

            if (lst.size() > 0) {
                roles = lst.toArray(new String[lst.size()]);
            }
        } catch (SQLException e) {
            String errorMessage =
                    "Error while retrieving roles from JDBC user store for tenant domain : " + tenantDomain +
                    " & filter : " + filter + "& max item limit : " + maxItemLimit;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return roles;
    }


    public String[] doGetUserListOfRole(String roleName, String filter) throws UserStoreException {

        RoleContext roleContext = createRoleContext(roleName);
        return getUserListOfJDBCRole(roleContext, filter);
    }

    /**
     *
     */
    public String[] getUserListOfJDBCRole(RoleContext ctx, String filter) throws UserStoreException {

        String roleName = ctx.getRoleName();
        String[] names = null;
        String sqlStmt = null;
        if (!ctx.isShared()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USERS_IN_ROLE);
            if (sqlStmt == null) {
                throw new UserStoreException("The sql statement for retrieving user roles is null");
            }
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                names =
                        getStringValuesFromDatabase(sqlStmt, roleName, tenantId, tenantId, tenantId);
            } else {
                names = getStringValuesFromDatabase(sqlStmt, roleName);
            }
        } else if (ctx.isShared()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USERS_IN_SHARED_ROLE);
            names = getStringValuesFromDatabase(sqlStmt, roleName);
        }

        List<String> userList = new ArrayList<String>();

        String domainName =
                realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);

        if (names != null) {
            for (String user : names) {
                user = UserCoreUtil.addDomainToName(user, domainName);
                userList.add(user);
            }

            names = userList.toArray(new String[userList.size()]);
        }
        log.debug("Roles are not defined for the role name " + roleName);

        return names;
    }

    /**
     *
     */
    public boolean doCheckExistingRole(String roleName) throws UserStoreException {

        RoleContext roleContext = createRoleContext(roleName);  // TODO if role Name with Shared Role?
        return isExistingJDBCRole(roleContext);
    }

    protected boolean isExistingJDBCRole(RoleContext context) throws UserStoreException {

        boolean isExisting;
        String roleName = context.getRoleName();

        String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_IS_ROLE_EXISTING);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for is role existing role null");
        }

        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
            isExisting =
                    isValueExisting(sqlStmt, null, roleName, ((JDBCRoleContext) context).getTenantId());
        } else {
            isExisting = isValueExisting(sqlStmt, null, roleName);
        }

        return isExisting;
    }

    /**
     *
     */
    public String[] getAllProfileNames() throws UserStoreException {
        String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_PROFILE_NAMES);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving profile names is null");
        }
        String[] names;
        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
            names = getStringValuesFromDatabase(sqlStmt, tenantId);
        } else {
            names = getStringValuesFromDatabase(sqlStmt);
        }

        return names;
    }

    /**
     *
     */
    public String[] getProfileNames(String userName) throws UserStoreException {

        userName = UserCoreUtil.removeDomainFromName(userName);
        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_PROFILE_NAMES_FOR_USER);
        } else {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_PROFILE_NAMES_FOR_USER_CASE_INSENSITIVE);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving  is null");
        }
        String[] names;
        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
            names = getStringValuesFromDatabase(sqlStmt, userName, tenantId, tenantId);
        } else {
            names = getStringValuesFromDatabase(sqlStmt, userName);
        }
        if (names.length == 0) {
            names = new String[]{UserCoreConstants.DEFAULT_PROFILE};
        } else {
            Arrays.sort(names);
            if (Arrays.binarySearch(names, UserCoreConstants.DEFAULT_PROFILE) < 0) {
                // we have to add the default profile
                String[] newNames = new String[names.length + 1];
                int i = 0;
                for (i = 0; i < names.length; i++) {
                    newNames[i] = names[i];
                }
                newNames[i] = UserCoreConstants.DEFAULT_PROFILE;
                names = newNames;
            }
        }

        return names;
    }

    /**
     *
     */
    public int getUserId(String username) throws UserStoreException {
        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USERID_FROM_USERNAME);
        } else {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_USERID_FROM_USERNAME_CASE_INSENSITIVE);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving ID is null");
        }
        int id = -1;
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                id = DatabaseUtil.getIntegerValueFromDatabase(dbConnection, sqlStmt, username,
                        tenantId);
            } else {
                id = DatabaseUtil.getIntegerValueFromDatabase(dbConnection, sqlStmt, username);
            }
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting user id from username : " + username;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        return id;
    }

    /**
     * @param tenantId tenant id
     * @return array of users of the tenant.
     * @throws UserStoreException throws user store exception
     */
    public String[] getUserNames(int tenantId) throws UserStoreException {
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.GET_USERNAME_FROM_TENANT_ID);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving user names is null");
        }
        String[] userNames;
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            userNames = DatabaseUtil.getStringValuesFromDatabase(dbConnection, sqlStmt, tenantId);
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting username from tenant ID : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        return userNames;
    }

    /**
     * @return the admin user. // TODO remove this method
     * @throws org.wso2.micro.integrator.security.user.core.UserStoreException from the getUserNames()
     * @deprecated Returns the admin users for the given tenant.
     */
    public String getAdminUser() throws UserStoreException {
        String[] users = getUserListOfRole(this.realmConfig.getAdminRoleName());
        if (users != null && users.length > 0) {
            return users[0];
        }
        return null;
    }

    /**
     *
     */
    public int getTenantId() throws UserStoreException {
        return this.tenantId;
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
    public int getTenantId(String username) throws UserStoreException {
        if (this.tenantId != Constants.SUPER_TENANT_ID) {
            throw new UserStoreException("Not allowed to perform this operation");
        }
        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_TENANT_ID_FROM_USERNAME);
        } else {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_TENANT_ID_FROM_USERNAME_CASE_INSENSITIVE);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving ID is null");
        }
        int id = -1;
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            id = DatabaseUtil.getIntegerValueFromDatabase(dbConnection, sqlStmt, username);
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting tenant ID from username : " + username;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        return id;
    }

    /**
     *
     */
    public Map<String, String> getUserPropertyValues(String userName, String[] propertyNames,
                                                     String profileName) throws UserStoreException {

        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String[] propertyNamesSorted = propertyNames.clone();
        Arrays.sort(propertyNamesSorted);
        Map<String, String> map = new HashMap<String, String>();
        try {
            dbConnection = getDBConnection();
            if (isCaseSensitiveUsername()) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_PROPS_FOR_PROFILE);
            } else {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_PROPS_FOR_PROFILE_CASE_INSENSITIVE);
            }
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, userName);
            prepStmt.setString(2, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(3, tenantId);
                prepStmt.setInt(4, tenantId);
            }
            rs = prepStmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                if (Arrays.binarySearch(propertyNamesSorted, name) < 0) {
                    continue;
                }
                map.put(name, value);
            }

            return map;
        } catch (SQLException e) {
            String errorMessage =
                    "Error Occurred while getting property values for user : " + userName + " & profile name : " +
                    profileName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    /**
     * @param sqlStmt
     * @param params
     * @return
     * @throws UserStoreException
     */
    private String[] getStringValuesFromDatabase(String sqlStmt, Object... params)
            throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Executing Query: " + sqlStmt);
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                log.debug("Input value: " + param);
            }
        }

        String[] values = new String[0];
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        try {
            dbConnection = getDBConnection();
            values = DatabaseUtil.getStringValuesFromDatabase(dbConnection, sqlStmt, params);
        } catch (SQLException e) {
            String msg = "Error occurred while retrieving string values.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return values;
    }

    private String[] getRoleNamesWithDomain(String sqlStmt, String userName, int tenantId,
                                            boolean appendDn) throws UserStoreException {

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        List<String> roles = new ArrayList<String>();
        try {
            dbConnection = getDBConnection();
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            byte count = 0;
            prepStmt.setString(++count, userName);
            prepStmt.setInt(++count, tenantId);

            rs = prepStmt.executeQuery();
            // append the domain if exist
            String domain =
                    realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);

            while (rs.next()) {
                String name = rs.getString(1);
                int tenant = rs.getInt(2);
                // boolean shared = rs.getBoolean(3);

                String role = name;
                if (appendDn) {
                    name = UserCoreUtil.addTenantDomainToEntry(name, String.valueOf(tenant));
                }
                roles.add(role);
            }

        } catch (SQLException e) {
            String msg =
                    "Error occurred while retrieving role name with tenant id : " + tenantId + " & user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return roles.toArray(new String[roles.size()]);
    }


    /**
     * @return
     * @throws SQLException
     * @throws UserStoreException
     */
    protected Connection getDBConnection() throws SQLException, UserStoreException {
        Connection dbConnection = getJDBCDataSource().getConnection();
        dbConnection.setAutoCommit(false);
        if (dbConnection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
            dbConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
        return dbConnection;
    }

    /**
     * @param sqlStmt
     * @param dbConnection
     * @param params
     * @return
     * @throws UserStoreException
     */
    protected boolean isValueExisting(String sqlStmt, Connection dbConnection, Object... params)
            throws UserStoreException {
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isExisting = false;
        boolean doClose = false;
        try {
            if (dbConnection == null) {
                dbConnection = getDBConnection();
                doClose = true; // because we created it
            }
            if (DatabaseUtil.getIntegerValueFromDatabase(dbConnection, sqlStmt, params) > -1) {
                isExisting = true;
            }
            return isExisting;
        } catch (SQLException e) {
            String msg = "Error occurred while checking existence of values.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            if (doClose) {
                DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
            }
        }
    }

    /**
     *
     */
    public boolean doCheckExistingUser(String userName) throws UserStoreException {

        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_IS_USER_EXISTING);
        } else {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_IS_USER_EXISTING_CASE_INSENSITIVE);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for is user existing null");
        }
        boolean isExisting = false;

        String isUnique = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USERNAME_UNIQUE);
        if (Boolean.parseBoolean(isUnique) && !UserCoreConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
            String uniquenesSql;
            if (isCaseSensitiveUsername()) {
                uniquenesSql = realmConfig.getUserStoreProperty(JDBCRealmConstants.USER_NAME_UNIQUE);
            } else {
                uniquenesSql = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.USER_NAME_UNIQUE_CASE_INSENSITIVE);
            }
            isExisting = isValueExisting(uniquenesSql, null, userName);
            if (log.isDebugEnabled()) {
                log.debug("The username should be unique across tenants.");
            }
        } else {
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                isExisting = isValueExisting(sqlStmt, null, userName, tenantId);
            } else {
                isExisting = isValueExisting(sqlStmt, null, userName);
            }
        }

        return isExisting;
    }

    /**
     *
     */
    public boolean doAuthenticate(String userName, Object credential) throws UserStoreException {

        if (!checkUserNameValid(userName)) {
            if (log.isDebugEnabled()) {
                log.debug("Username validation failed");
            }
            return false;
        }

        if (!checkUserPasswordValid(credential)) {
            if (log.isDebugEnabled()) {
                log.debug("Password validation failed");
            }
            return false;
        }

        if (UserCoreUtil.isRegistryAnnonymousUser(userName)) {
            log.error("Anonnymous user trying to login");
            return false;
        }

        Connection dbConnection = null;
        ResultSet rs = null;
        PreparedStatement prepStmt = null;
        String sqlstmt = null;
        String password = null;
        boolean isAuthed = false;

        try {
            dbConnection = getDBConnection();
            dbConnection.setAutoCommit(false);

            if (isCaseSensitiveUsername()) {
                sqlstmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.SELECT_USER);
            } else {
                sqlstmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.SELECT_USER_CASE_INSENSITIVE);
            }

            if (log.isDebugEnabled()) {
                log.debug(sqlstmt);
            }

            prepStmt = dbConnection.prepareStatement(sqlstmt);
            prepStmt.setString(1, userName);
            if (sqlstmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }

            rs = prepStmt.executeQuery();

            if (rs.next() == true) {
                String storedPassword = rs.getString(3);
                String saltValue = null;
                if ("true".equalsIgnoreCase(realmConfig
                        .getUserStoreProperty(JDBCRealmConstants.STORE_SALTED_PASSWORDS))) {
                    saltValue = rs.getString(4);
                }

                boolean requireChange = rs.getBoolean(5);
                Timestamp changedTime = rs.getTimestamp(6);

                GregorianCalendar gc = new GregorianCalendar();
                gc.add(GregorianCalendar.HOUR, -24);
                Date date = gc.getTime();

                if (requireChange == true && changedTime.before(date)) {
                    isAuthed = false;
                } else {
                    password = this.preparePassword(credential, saltValue);
                    if ((storedPassword != null) && (storedPassword.equals(password))) {
                        isAuthed = true;
                    }
                }
            }
        } catch (SQLException e) {
            String msg = "Error occurred while retrieving user authentication info for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException("Authentication Failure", e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }

        if (log.isDebugEnabled()) {
            log.debug("User " + userName + " login attempt. Login success :: " + isAuthed);
        }

        return isAuthed;
    }

    /**
     *
     */
    public boolean isReadOnly() throws UserStoreException {
        if ("true".equalsIgnoreCase(realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY))) {
            return true;
        }
        return false;
    }

    /**
     *
     */
    public void doAddUser(String userName, Object credential, String[] roleList,
                          Map<String, String> claims, String profileName, boolean requirePasswordChange)
            throws UserStoreException {

        String userID = UUID.randomUUID().toString();

        // persist the user info. in the database.
        persistUser(userID, userName, credential, roleList, claims, profileName, requirePasswordChange);

    }

    /*
     * This method persists the user information in the database.
     */
    protected void persistUser(String userID, String userName, Object credential, String[] roleList,
                               Map<String, String> claims, String profileName, boolean requirePasswordChange)
            throws UserStoreException {

        Connection dbConnection = null;
        try{
            dbConnection = getDBConnection();
        }catch (SQLException e){
            String errorMessage = "Error occurred while getting DB connection";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        }

        Secret credentialObj;
        try {
            credentialObj = Secret.getSecret(credential);
        } catch (UnsupportedSecretTypeException e) {
            throw new UserStoreException("Unsupported credential type", e);
        }

        try {
            String sqlStmt1 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_WITH_ID);

            String saltValue = null;

            if ("true".equalsIgnoreCase(realmConfig.getUserStoreProperties()
                    .get(JDBCRealmConstants.STORE_SALTED_PASSWORDS))) {
                saltValue = generateSaltValue();
            }

            String password = this.preparePassword(credentialObj, saltValue);

            // do all 4 possibilities
            if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN) && (saltValue == null)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userID, userName, password, "",
                        requirePasswordChange, new Date(), tenantId);
            } else if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN) && (saltValue != null)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userID, userName, password,
                        saltValue, requirePasswordChange, new Date(),
                        tenantId);
            } else if (!sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN) &&
                    (saltValue == null)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userID, userName, password, "",
                        requirePasswordChange, new Date());
            } else {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userID, userName, password, saltValue,
                        requirePasswordChange, new Date());
            }

            if (roleList != null && roleList.length > 0) {

                RoleBreakdown breakdown = getSharedRoleBreakdown(roleList);
                String[] roles = breakdown.getRoles();
                // Integer[] tenantIds = breakdown.getTenantIds();

                String[] sharedRoles = breakdown.getSharedRoles();
                Integer[] sharedTenantIds = breakdown.getSharedTenantids();

                String sqlStmt2 = null;
                String type = DatabaseCreator.getDatabaseType(dbConnection);
                if (roles.length > 0) {
                    // Adding user to the non shared roles
                    if (isCaseSensitiveUsername()) {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_ROLE_TO_USER + "-" + type);
                    } else {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                .ADD_ROLE_TO_USER_CASE_INSENSITIVE + "-" + type);
                    }
                    if (sqlStmt2 == null) {
                        if (isCaseSensitiveUsername()) {
                            sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_ROLE_TO_USER);
                        } else {
                            sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                    .ADD_ROLE_TO_USER_CASE_INSENSITIVE);
                        }
                    }

                    if (sqlStmt2.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                        if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    tenantId, roles,
                                    tenantId, userName,
                                    tenantId);
                        } else {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    roles, tenantId,
                                    userName, tenantId,
                                    tenantId);
                        }
                    } else {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, roleList, userName);
                    }

                }
                if (sharedRoles.length > 0) {
                    // Adding user to the shared roles
                    if (isCaseSensitiveUsername()) {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_SHARED_ROLE_TO_USER);
                    } else {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                .ADD_SHARED_ROLE_TO_USER_CASE_INSENSITIVE);
                    }
                    DatabaseUtil.udpateUserRoleMappingWithExactParams(dbConnection, sqlStmt2,
                            sharedRoles, userName,
                            sharedTenantIds, tenantId);
                }

            }

            if (claims != null) {
                // add the properties
                if (profileName == null) {
                    profileName = UserCoreConstants.DEFAULT_PROFILE;
                }

                addProperties(dbConnection, userName, claims, profileName);
            }

            dbConnection.commit();
        } catch (Exception e) {
            try {
                dbConnection.rollback();
            } catch (SQLException e1) {
                String errorMessage = "Error rollbacking add user operation for user : " + userName;
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e1);
                }
                throw new UserStoreException(errorMessage, e1);
            }
            String errorMessage = "Error while persisting user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            if (e instanceof UserStoreException && ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE.getCode().equals((
                    (UserStoreException) e).getErrorCode())) {
                // Duplicate entry
                throw new UserStoreException(errorMessage, ERROR_CODE_DUPLICATE_WHILE_ADDING_A_USER.getCode(), e);
            } else {
                // Other SQL Exception
                throw new UserStoreException(errorMessage, e);
            }
        } finally {
            credentialObj.clear();
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     *
     */
    public void doAddRole(String roleName, String[] userList, boolean shared) throws UserStoreException {


        if (shared && isSharedGroupEnabled()) {
            doAddSharedRole(roleName, userList);
        }

        Connection dbConnection = null;

        try {
            dbConnection = getDBConnection();
            String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_ROLE);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt, roleName, tenantId);
            } else {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt, roleName);
            }
            if (userList != null) {
                // add role to user
                String type = DatabaseCreator.getDatabaseType(dbConnection);
                String sqlStmt2;
                if (isCaseSensitiveUsername()) {
                    sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_TO_ROLE + "-" + type);
                } else {
                    sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                    .ADD_USER_TO_ROLE_CASE_INSENSITIVE + "-" + type);
                }
                if (sqlStmt2 == null) {
                    if (isCaseSensitiveUsername()) {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_TO_ROLE);
                    } else {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                .ADD_USER_TO_ROLE_CASE_INSENSITIVE);
                    }
                }
                if (sqlStmt2.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                    if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                tenantId, userList, tenantId, roleName, tenantId);
                    } else {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                userList, tenantId, roleName, tenantId, tenantId);
                    }
                } else {
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, userList, roleName);
                }

            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Error occurred while adding role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (Exception e) {
            String errorMessage = "Error occurred while getting database type from DB connection";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            if (e instanceof UserStoreException && ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE.getCode().equals((
                    (UserStoreException) e).getErrorCode())) {
                // Duplicate entry
                throw new UserStoreException(errorMessage, ERROR_CODE_DUPLICATE_WHILE_ADDING_ROLE.getCode(), e);
            } else {
                // Other SQL Exception
                throw new UserStoreException(errorMessage, e);
            }
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     *
     */
    public void doUpdateRoleName(String roleName, String newRoleName) throws UserStoreException {

        JDBCRoleContext ctx = (JDBCRoleContext) createRoleContext(roleName);

        if (isExistingRole(newRoleName)) {
            throw new UserStoreException("Role name: " + newRoleName
                    + " in the system. Please pick another role name.");
        }
        String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.UPDATE_ROLE_NAME);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for update role name is null");
        }
        Connection dbConnection = null;
        try {

            roleName = ctx.getRoleName();
            dbConnection = getDBConnection();
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt, newRoleName, roleName,
                        tenantId);
            } else {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt, newRoleName, roleName);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Error occurred while updating role name : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }


    @Override
    public boolean isSharedRole(String roleName, String roleNameBase) {
        if (roleNameBase != null && roleNameBase.indexOf(TRUE_VALUE) > -1) {
            return true;
        }
        return false;
    }

    private int getTenantIdFromRole(String roleBase) {
        int tenantId = Constants.SUPER_TENANT_ID;
        String[] postfix = roleBase.split(UserCoreConstants.SHARED_ROLE_TENANT_COMBINER);
        if (postfix.length > 1) {
            try {
                tenantId = Integer.parseInt(postfix[1]);
            } catch (NumberFormatException e) {
                log.error(e);
                tenantId = Constants.SUPER_TENANT_ID;
            }
        }

        return tenantId;
    }

    /**
     * JDBC User store supports bulk import.
     *
     * @return Always <code>true<code>.
     */
    public boolean isBulkImportSupported() {
        return new Boolean(realmConfig.getUserStoreProperty("IsBulkImportSupported"));
    }

    public RealmConfiguration getRealmConfiguration() {
        return this.realmConfig;
    }

    /**
     * User of this? TODO remove
     *
     * @param noHybridRoles
     * @return
     * @throws UserStoreException
     */
    public RoleDTO[] getRoleNamesWithDomain(boolean noHybridRoles) throws UserStoreException {

        String[] names = null;
        String domain = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);

        String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_ROLE_LIST);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving role name is null");
        }
        names = getStringValuesFromDatabase(sqlStmt, tenantId);
        if (isReadOnly() && !noHybridRoles) {
            String[] hybrids = hybridRoleManager.getHybridRoles("*");
            names = UserCoreUtil.combineArrays(names, hybrids);
        }

        List<RoleDTO> roleDTOs = new ArrayList<RoleDTO>();
        if (names != null && names.length != 0) {
            roleDTOs.addAll(Arrays.asList(UserCoreUtil.convertRoleNamesToRoleDTO(names, domain)));
        }

        RoleDTO[] secondaryRoleDTOs = getAllSecondaryRoleDTOs();
        if (secondaryRoleDTOs != null && secondaryRoleDTOs.length != 0) {
            roleDTOs.addAll(Arrays.asList(secondaryRoleDTOs));
        }

        return roleDTOs.toArray(new RoleDTO[roleDTOs.size()]);
    }

    /**
     * This method is to check whether multiple profiles are allowed with a particular user-store.
     * For an example, currently, JDBC user store supports multiple profiles and where as ApacheDS
     * does not allow. Currently, JDBC user store allows multiple profiles. Hence return true.
     *
     * @return boolean
     */
    public boolean isMultipleProfilesAllowed() {
        return true;
    }

    /**
     *
     */
    public void doDeleteRole(String roleName) throws UserStoreException {

        String sqlStmt1 = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.ON_DELETE_ROLE_REMOVE_USER_ROLE);
        if (sqlStmt1 == null) {
            throw new UserStoreException("The sql statement for delete user-role mapping is null");
        }

        String sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.DELETE_ROLE);
        if (sqlStmt2 == null) {
            throw new UserStoreException("The sql statement for delete role is null");
        }

        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, roleName, tenantId,
                        tenantId);
                this.updateStringValuesToDatabase(dbConnection, sqlStmt2, roleName, tenantId);
            } else {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, roleName);
                this.updateStringValuesToDatabase(dbConnection, sqlStmt2, roleName);
            }
            //this.userRealm.getAuthorizationManager().clearRoleAuthorization(roleName);
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Error occurred while deleting role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     *
     */
    public void doDeleteUser(String userName) throws UserStoreException {

        String sqlStmt1;
        if (isCaseSensitiveUsername()) {
            sqlStmt1 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ON_DELETE_USER_REMOVE_USER_ROLE);
        } else {
            sqlStmt1 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                    .ON_DELETE_USER_REMOVE_USER_ROLE_CASE_INSENSITIVE);
        }
        if (sqlStmt1 == null) {
            throw new UserStoreException("The sql statement for delete user-role mapping is null");
        }

        String sqlStmt2;
        if (isCaseSensitiveUsername()) {
            sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ON_DELETE_USER_REMOVE_ATTRIBUTE);
        } else {
            sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                    .ON_DELETE_USER_REMOVE_ATTRIBUTE_CASE_INSENSITIVE);
        }
        if (sqlStmt2 == null) {
            throw new UserStoreException("The sql statement for delete user attribute is null");
        }

        String sqlStmt3;
        if (isCaseSensitiveUsername()) {
            sqlStmt3 = realmConfig.getUserStoreProperty(JDBCRealmConstants.DELETE_USER);
        } else {
            sqlStmt3 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.DELETE_USER_CASE_INSENSITIVE);
        }
        if (sqlStmt3 == null) {
            throw new UserStoreException("The sql statement for delete user is null");
        }

        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName, tenantId,
                        tenantId);
                this.updateStringValuesToDatabase(dbConnection, sqlStmt2, userName, tenantId,
                        tenantId);
                this.updateStringValuesToDatabase(dbConnection, sqlStmt3, userName, tenantId);
            } else {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName);
                this.updateStringValuesToDatabase(dbConnection, sqlStmt2, userName);
                this.updateStringValuesToDatabase(dbConnection, sqlStmt3, userName);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Error occurred while deleting user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     *
     */
    public void doUpdateUserListOfRole(String roleName, String deletedUsers[], String[] newUsers)
            throws UserStoreException {

        JDBCRoleContext ctx = (JDBCRoleContext) createRoleContext(roleName);
        roleName = ctx.getRoleName();
        int roleTenantId = ctx.getTenantId();
        boolean isShared = ctx.isShared();

        String sqlStmt1;
        if (isCaseSensitiveUsername()) {
            sqlStmt1 = realmConfig.getUserStoreProperty(isShared ? JDBCRealmConstants.REMOVE_USER_FROM_SHARED_ROLE :
                    JDBCRealmConstants.REMOVE_USER_FROM_ROLE);
        } else {
            sqlStmt1 = realmConfig
                    .getUserStoreProperty(isShared ? JDBCCaseInsensitiveConstants.REMOVE_USER_FROM_SHARED_ROLE_CASE_INSENSITIVE :
                            JDBCCaseInsensitiveConstants.REMOVE_USER_FROM_ROLE_CASE_INSENSITIVE);
        }
        if (sqlStmt1 == null) {
            throw new UserStoreException("The sql statement for remove user from role is null");
        }

        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            String type = DatabaseCreator.getDatabaseType(dbConnection);
            String sqlStmt2 = null;
            if (!isShared) {
                if (isCaseSensitiveUsername()) {
                    sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_TO_ROLE + "-" + type);
                } else {
                    sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.ADD_USER_TO_ROLE_CASE_INSENSITIVE
                            + "-" + type);
                }
                if (sqlStmt2 == null) {
                    if (isCaseSensitiveUsername()) {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_TO_ROLE);
                    } else {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                .ADD_USER_TO_ROLE_CASE_INSENSITIVE);
                    }
                }
            } else {
                if (isCaseSensitiveUsername()) {
                    sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_SHARED_ROLE_TO_USER);
                } else {
                    sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                            .ADD_SHARED_ROLE_TO_USER_CASE_INSENSITIVE);
                }
            }
            if (sqlStmt2 == null) {
                throw new UserStoreException("The sql statement for add user to role is null");
            }
            if (deletedUsers != null) {
                if (isShared) {
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1,
                            roleName, tenantId,
                            deletedUsers, tenantId, tenantId, roleTenantId);
                } else {
                    if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1,
                                deletedUsers, tenantId,
                                roleName, tenantId, tenantId);
                    } else {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1,
                                deletedUsers, roleName);
                    }
                }
            }
            if (newUsers != null) {
                if (isShared) {
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, roleName,
                            roleTenantId, newUsers, tenantId,
                            tenantId, roleTenantId);

                } else {
                    if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                        if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    tenantId, newUsers,
                                    tenantId, roleName,
                                    tenantId);
                        } else {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    newUsers, tenantId,
                                    roleName, tenantId,
                                    tenantId);
                        }
                    } else {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                newUsers, roleName);
                    }
                }
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Database error occurred while updating user list of role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (Exception e) {
            String errorMessage = "Error occurred while getting database type from DB connection";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }

    }

    /**
     * Break the provided role list based on whether roles are shared or not
     *
     * @param rolesList
     * @return
     */
    private RoleBreakdown getSharedRoleBreakdown(String[] rolesList) {
        List<String> roles = new ArrayList<String>();
        List<Integer> tenantIds = new ArrayList<Integer>();

        List<String> sharedRoles = new ArrayList<String>();
        List<Integer> sharedTenantIds = new ArrayList<Integer>();

        for (String role : rolesList) {

            if (StringUtils.isNotEmpty(role)) {
                String[] deletedRoleNames = role.split(UserCoreConstants.DOMAIN_SEPARATOR);
                if (deletedRoleNames.length > 1) {
                    role = deletedRoleNames[1];
                }

                JDBCRoleContext ctx = (JDBCRoleContext) createRoleContext(role);
                role = ctx.getRoleName();
                int roleTenantId = ctx.getTenantId();
                boolean isShared = ctx.isShared();

                if (isShared) {
                    sharedRoles.add(role);
                    sharedTenantIds.add(roleTenantId);
                } else {
                    roles.add(role);
                    tenantIds.add(roleTenantId);
                }
            }

        }

        RoleBreakdown breakdown = new RoleBreakdown();

        // Non shared roles and tenant ids
        breakdown.setRoles(roles.toArray(new String[roles.size()]));
        breakdown.setTenantIds(tenantIds.toArray(new Integer[tenantIds.size()]));

        // Shared roles and tenant ids
        breakdown.setSharedRoles(sharedRoles.toArray(new String[sharedRoles.size()]));
        breakdown.setSharedTenantids(sharedTenantIds.toArray(new Integer[sharedTenantIds.size()]));

        return breakdown;

    }

    /**
     *
     */
    public void doUpdateRoleListOfUser(String userName, String[] deletedRoles, String[] newRoles)
            throws UserStoreException {

        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            String type = DatabaseCreator.getDatabaseType(dbConnection);
            String sqlStmt2 = null;
            // if user name and role names are prefixed with domain name, remove the domain name
            String[] userNames = userName.split(UserCoreConstants.DOMAIN_SEPARATOR);
            if (userNames.length > 1) {
                userName = userNames[1];
            }
            if (deletedRoles != null && deletedRoles.length > 0) {
                // Break the provided role list based on whether roles are shared or not
                RoleBreakdown breakdown = getSharedRoleBreakdown(deletedRoles);
                String[] roles = breakdown.getRoles();
                // Integer[] tenantIds = breakdown.getTenantIds();

                String[] sharedRoles = breakdown.getSharedRoles();
                Integer[] sharedTenantIds = breakdown.getSharedTenantids();

                String sqlStmt1 = null;

                if (roles.length > 0) {
                    if (isCaseSensitiveUsername()) {
                        sqlStmt1 = realmConfig.getUserStoreProperty(JDBCRealmConstants.REMOVE_ROLE_FROM_USER);
                    } else {
                        sqlStmt1 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                .REMOVE_ROLE_FROM_USER_CASE_INSENSITIVE);
                    }
                    if (sqlStmt1 == null) {
                        throw new UserStoreException(
                                "The sql statement for remove user from role is null");
                    }
                    if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1,
                                roles, tenantId, userName,
                                tenantId, tenantId);
                    } else {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1, roles, userName);
                    }
                }
                if (sharedRoles.length > 0) {
                    if (isCaseSensitiveUsername()) {
                        sqlStmt1 = realmConfig.getUserStoreProperty(JDBCRealmConstants.REMOVE_USER_FROM_SHARED_ROLE);
                    } else {
                        sqlStmt1 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                .REMOVE_USER_FROM_SHARED_ROLE_CASE_INSENSITIVE);
                    }
                    if (sqlStmt1 == null) {
                        throw new UserStoreException(
                                "The sql statement for remove user from role is null");
                    }

                    DatabaseUtil.udpateUserRoleMappingWithExactParams(dbConnection, sqlStmt1,
                            sharedRoles, userName,
                            sharedTenantIds, tenantId);
                }
            }

            if (newRoles != null && newRoles.length > 0) {

                ArrayList<String> newRoleList = new ArrayList<>();
                for (String role : newRoles) {
                    if(!isExistingRole(role)){
                        String errorMessage = "The role: " + role + " does not exist.";
                        throw new UserStoreException(errorMessage);
                    }
                    if (!isUserInRole(userName, role)) {
                        newRoleList.add(role);
                    }
                }

                String[] rolesToAdd = newRoleList.toArray(new String[newRoleList.size()]);
                // if user name and role names are prefixed with domain name,
                // remove the domain name
                RoleBreakdown breakdown = getSharedRoleBreakdown(rolesToAdd);

                String[] roles = breakdown.getRoles();

                // Integer[] tenantIds = breakdown.getTenantIds();

                String[] sharedRoles = breakdown.getSharedRoles();
                Integer[] sharedTenantIds = breakdown.getSharedTenantids();

                if (roles.length > 0) {

                    if (isCaseSensitiveUsername()) {
                        realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_ROLE_TO_USER + "-" + type);
                    } else {
                        realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.ADD_ROLE_TO_USER_CASE_INSENSITIVE + "-" +
                                type);
                    }
                    if (sqlStmt2 == null) {
                        if (isCaseSensitiveUsername()) {
                                                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_ROLE_TO_USER);
                                                    } else {
                                                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                                                        .ADD_ROLE_TO_USER_CASE_INSENSITIVE);
                                                    }
                    }
                    if (sqlStmt2 == null) {
                        throw new UserStoreException(
                                "The sql statement for add user to role is null");
                    }
                    if (sqlStmt2.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                        if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    tenantId, roles,
                                    tenantId, userName,
                                    tenantId);
                        } else {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    roles, tenantId,
                                    userName, tenantId,
                                    tenantId);
                        }
                    } else {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, newRoles, userName);
                    }
                }
                if (sharedRoles.length > 0) {
                    if (isCaseSensitiveUsername()) {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_SHARED_ROLE_TO_USER);
                    } else {
                        sqlStmt2 = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                                .ADD_SHARED_ROLE_TO_USER_CASE_INSENSITIVE);
                    }
                    if (sqlStmt2 == null) {
                        throw new UserStoreException(
                                "The sql statement for remove user from role is null");
                    }

                    DatabaseUtil.udpateUserRoleMappingWithExactParams(dbConnection, sqlStmt2,
                            sharedRoles, userName,
                            sharedTenantIds, tenantId);

                }
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Database error occurred while updating role list of user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (UserStoreException e) {
            String errorMessage = "Error occurred while updating role list of user:" + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(e.getMessage(), e);
        } catch (Exception e) {
            String errorMessage = "Error occurred while getting database type from DB connection";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }

    }

    /**
     *
     */
    public void doSetUserClaimValue(String userName, String claimURI, String claimValue,
                                    String profileName) throws UserStoreException {
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }
        if (claimValue == null) {
            throw new UserStoreException("Cannot set null values.");
        }
        Connection dbConnection = null;
        String property = null;
        try {
            dbConnection = getDBConnection();
            property = getClaimAtrribute(claimURI, userName, null);
            String value = getProperty(dbConnection, userName, property, profileName);
            if (value == null) {
                addProperty(dbConnection, userName, property, claimValue, profileName);
            } else {
                updateProperty(dbConnection, userName, property, claimValue, profileName);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg =
                    "Database error occurred while saving user claim value for user : " + userName + " & claim URI : " +
                    claimURI + " claim value : " + claimValue;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (UserStoreException e) {
            String errorMessage =
                    "Error occurred while adding or updating claim value for user : " + userName + " & claim URI : " +
                    claimURI + " attribute : " + property + " profile : " + profileName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (org.wso2.micro.integrator.security.user.api.UserStoreException e) {
            String errorMessage =
                    "Error occurred while getting claim attribute for user : " + userName + " & claim URI : " +
                    claimURI;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     *
     */
    public void doSetUserClaimValues(String userName, Map<String, String> claims, String profileName)
            throws UserStoreException {
        Connection dbConnection = null;
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        if (claims.get(UserCoreConstants.PROFILE_CONFIGURATION) == null) {
            claims.put(UserCoreConstants.PROFILE_CONFIGURATION,
                    UserCoreConstants.DEFAULT_PROFILE_CONFIGURATION);
        }

        try {

            ArrayList<String> propertyListToUpdate = new ArrayList<>();
            Map<String, String> claimPropertyMap = new HashMap<>();
            Iterator<Map.Entry<String, String>> ite = claims.entrySet().iterator();

            // Get the property names fo the claims
            while (ite.hasNext()) {
                Map.Entry<String, String> entry = ite.next();
                String claimURI = entry.getKey();

                String property = getClaimAtrribute(claimURI, userName, null);
                propertyListToUpdate.add(property);
                claimPropertyMap.put(claimURI, property);
            }

            String[] propertyArr = new String[propertyListToUpdate.size()];
            propertyArr = propertyListToUpdate.toArray(propertyArr);

            // Get available properties
            Map<String, String> availableProperties = getUserPropertyValues(userName, propertyArr, profileName);
            Map<String, String> newClaims = new HashMap<>();
            Map<String, String> availableClaims = new HashMap<>();

            // Divide claim list to already available claims (need to update those) and new claims (need to add those)
            Iterator<Map.Entry<String, String>> ite2 = claims.entrySet().iterator();
            while (ite2.hasNext()) {
                Map.Entry<String, String> entry = ite2.next();
                String claimURI = entry.getKey();
                String claimValue = claimPropertyMap.get(claimURI);
                if (claimValue != null && availableProperties.containsKey(claimValue)) {
                    String availableValue = availableProperties.get(claimValue);
                    if (availableValue != null && availableValue.equals(entry.getValue())) {
                        continue;
                    } else {
                        availableClaims.put(claimURI, entry.getValue());
                    }
                } else {
                    newClaims.put(claimURI, entry.getValue());
                }
            }

            dbConnection = getDBConnection();
            addProperties(dbConnection, userName, newClaims, profileName);
            updateProperties(dbConnection, userName, availableClaims, profileName);
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Database error occurred while setting user claim values for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (org.wso2.micro.integrator.security.user.api.UserStoreException e) {
            String errorMessage = "Error occurred while getting claim attribute for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     *
     */
    public void doDeleteUserClaimValue(String userName, String claimURI, String profileName)
            throws UserStoreException {
        Connection dbConnection = null;
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }
        try {
            String property = null;
            if (UserCoreConstants.PROFILE_CONFIGURATION.equals(claimURI)) {
                property = UserCoreConstants.PROFILE_CONFIGURATION;
            } else {
                property = getClaimAtrribute(claimURI, userName, null);
            }

            dbConnection = getDBConnection();
            this.deleteProperty(dbConnection, userName, property, profileName);
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Database error occurred while deleting user claim value for user : " + userName +
                         " & claim URI : " + claimURI;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (org.wso2.micro.integrator.security.user.api.UserStoreException e) {
            String errorMessage =
                    "Error occurred while getting claim attribute for user : " + userName + " & claim URI : " +
                    claimURI;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     *
     */
    public void doDeleteUserClaimValues(String userName, String[] claims, String profileName)
            throws UserStoreException {
        Connection dbConnection = null;
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }
        try {
            dbConnection = getDBConnection();
            for (String claimURI : claims) {
                String property = getClaimAtrribute(claimURI, userName, null);
                this.deleteProperty(dbConnection, userName, property, profileName);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Database error occurred while deleting user claim values for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (org.wso2.micro.integrator.security.user.api.UserStoreException e) {
            String errorMessage = "Error occurred while getting claim attribute for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     *
     */
    public void doUpdateCredential(String userName, Object newCredential, Object oldCredential)
            throws UserStoreException {
        // no need to check old password here because we already authenticate in super class
        // if (this.authenticate(userName, oldCredential)) {
        this.doUpdateCredentialByAdmin(userName, newCredential);
		/*
		 * } else { log.error("Wrong username/password provided"); throw new
		 * UserStoreException("Wrong username/password provided"); }
		 */
    }

    /**
     *
     */
    public void doUpdateCredentialByAdmin(String userName, Object newCredential)
            throws UserStoreException {

        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.UPDATE_USER_PASSWORD);
        } else {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.UPDATE_USER_PASSWORD_CASE_INSENSITIVE);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for delete user claim value is null");
        }
        String saltValue = null;
        if ("true".equalsIgnoreCase(realmConfig.getUserStoreProperties().get(
                JDBCRealmConstants.STORE_SALTED_PASSWORDS))) {
            saltValue = generateSaltValue();
        }

        String password = this.preparePassword(newCredential, saltValue);

        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN) && saltValue == null) {
            updateStringValuesToDatabase(null, sqlStmt, password, "", false, new Date(), userName,
                    tenantId);
        } else if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN) && saltValue != null) {
            updateStringValuesToDatabase(null, sqlStmt, password, saltValue, false, new Date(),
                    userName, tenantId);
        } else if (!sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN) && saltValue == null) {
            updateStringValuesToDatabase(null, sqlStmt, password, "", false, new Date(), userName);
        } else {
            updateStringValuesToDatabase(null, sqlStmt, password, saltValue, false, new Date(),
                    userName);
        }
    }

    /**
     *
     */
    public Date getPasswordExpirationTime(String userName) throws UserStoreException {

        if (userName != null && userName.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
            return super.getPasswordExpirationTime(userName);
        }

        Connection dbConnection = null;
        ResultSet rs = null;
        PreparedStatement prepStmt = null;
        String sqlstmt = null;
        Date date = null;

        try {
            dbConnection = getDBConnection();
            dbConnection.setAutoCommit(false);

            if (isCaseSensitiveUsername()) {
                sqlstmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.SELECT_USER);
            } else {
                sqlstmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.SELECT_USER_CASE_INSENSITIVE);
            }

            if (log.isDebugEnabled()) {
                log.debug(sqlstmt);
            }

            prepStmt = dbConnection.prepareStatement(sqlstmt);
            prepStmt.setString(1, userName);
            if (sqlstmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }

            rs = prepStmt.executeQuery();

            if (rs.next() == true) {
                boolean requireChange = rs.getBoolean(5);
                Timestamp changedTime = rs.getTimestamp(6);
                if (requireChange) {
                    GregorianCalendar gc = new GregorianCalendar();
                    gc.setTime(changedTime);
                    gc.add(GregorianCalendar.HOUR, 24);
                    date = gc.getTime();
                }
            }
        } catch (SQLException e) {
            String msg = "Error occurred while retrieving password expiration time for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return date;
    }

    /**
     * This private method returns a saltValue using SecureRandom.
     *
     * @return saltValue
     */
    private String generateSaltValue() {
        String saltValue = null;
        try {
            SecureRandom secureRandom;
            String provider = getJceProvider();
            if (provider != null) {
                secureRandom = SecureRandom.getInstance("DEFAULT", provider);
            } else {
                secureRandom = SecureRandom.getInstance(SHA_1_PRNG);
            }
            byte[] bytes = new byte[16];
            //secureRandom is automatically seeded by calling nextBytes
            secureRandom.nextBytes(bytes);
            saltValue = Base64.encode(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA1PRNG algorithm could not be found.");
        } catch (NoSuchProviderException e) {
            throw new RuntimeException("Specified security provider is not available in this environment: ", e);
        }
        return saltValue;
    }
    /**
     * @param dbConnection
     * @param sqlStmt
     * @param params
     * @throws UserStoreException
     */
    private void updateStringValuesToDatabase(Connection dbConnection, String sqlStmt,
                                              Object... params) throws UserStoreException {
        PreparedStatement prepStmt = null;
        boolean localConnection = false;
        try {
            if (dbConnection == null) {
                localConnection = true;
                dbConnection = getDBConnection();
            }
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param == null) {
                        throw new UserStoreException("Invalid data provided");
                    } else if (param instanceof String) {
                        prepStmt.setString(i + 1, (String) param);
                    } else if (param instanceof Integer) {
                        prepStmt.setInt(i + 1, (Integer) param);
                    } else if (param instanceof Date) {
                        // Timestamp timestamp = new Timestamp(((Date) param).getTime());
                        // prepStmt.setTimestamp(i + 1, timestamp);
                        prepStmt.setTimestamp(i + 1, new Timestamp(System.currentTimeMillis()));
                    } else if (param instanceof Boolean) {
                        prepStmt.setBoolean(i + 1, (Boolean) param);
                    }
                }
            }
            int count = prepStmt.executeUpdate();

            if (log.isDebugEnabled()) {
                if (count == 0) {
                    log.debug("No rows were updated");
                }
                log.debug("Executed query is " + sqlStmt + " and number of updated rows :: "
                        + count);
            }

            if (localConnection) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            String msg = "Error occurred while updating string values to database.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            if (e instanceof SQLIntegrityConstraintViolationException) {
                // Duplicate entry
                throw new UserStoreException(msg, ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE.getCode(), e);
            } else {
                // Other SQL Exception
                throw new UserStoreException(msg, e);
            }
        } finally {
            if (localConnection) {
                DatabaseUtil.closeAllConnections(dbConnection);
            }
            DatabaseUtil.closeAllConnections(null, prepStmt);
        }
    }

    /**
     * @param dbConnection
     * @param userName
     * @param propertyName
     * @param value
     * @param profileName
     * @throws UserStoreException
     */
    public void addProperty(Connection dbConnection, String userName, String propertyName,
                            String value, String profileName) throws UserStoreException {
        try {
            String type = DatabaseCreator.getDatabaseType(dbConnection);
            String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_PROPERTY
                    + "-" + type);
            if (sqlStmt == null) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_PROPERTY);
            }
            if (sqlStmt == null) {
                throw new UserStoreException("The sql statement for add user property sql is null");
            }

            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                    updateStringValuesToDatabase(dbConnection, sqlStmt, propertyName, value,
                            profileName, tenantId, userName, tenantId);
                } else {
                    updateStringValuesToDatabase(dbConnection, sqlStmt, userName, tenantId,
                            propertyName, value, profileName, tenantId);
                }
            } else {
                updateStringValuesToDatabase(dbConnection, sqlStmt, userName, propertyName, value, profileName);
            }
        } catch (Exception e) {
            String msg = "Error occurred while adding user property for user : " + userName + " & property name : " +
                         propertyName + " & value : " + value;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        }
    }

    /**
     * @param dbConnection
     * @param userName
     * @param propertyName
     * @param value
     * @param profileName
     * @throws UserStoreException
     */
    protected void updateProperty(Connection dbConnection, String userName, String propertyName,
                                  String value, String profileName) throws UserStoreException {
        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.UPDATE_USER_PROPERTY);
        } else {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.UPDATE_USER_PROPERTY_CASE_INSENSITIVE);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for add user property sql is null");
        }

        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
            updateStringValuesToDatabase(dbConnection, sqlStmt, value, userName, tenantId,
                    propertyName, profileName, tenantId);
        } else {
            updateStringValuesToDatabase(dbConnection, sqlStmt, value, userName, propertyName, profileName);
        }

    }

    /**
     * @param dbConnection
     * @param userName
     * @param propertyName
     * @param profileName
     * @throws UserStoreException
     */
    protected void deleteProperty(Connection dbConnection, String userName, String propertyName,
                                  String profileName) throws UserStoreException {
        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.DELETE_USER_PROPERTY);
        } else {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.DELETE_USER_PROPERTY_CASE_INSENSITIVE);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for add user property sql is null");
        }

        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
            updateStringValuesToDatabase(dbConnection, sqlStmt, userName, tenantId, propertyName,
                    profileName, tenantId);
        } else {
            updateStringValuesToDatabase(dbConnection, sqlStmt, userName, propertyName, profileName);
        }
    }

    /**
     * @param dbConnection
     * @param userName
     * @param propertyName
     * @param profileName
     * @return
     * @throws UserStoreException
     */
    protected String getProperty(Connection dbConnection, String userName, String propertyName,
                                 String profileName) throws UserStoreException {

        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_PROP_FOR_PROFILE);
        } else {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_PROP_FOR_PROFILE_CASE_INSENSITIVE);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for add user property sql is null");
        }
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String value = null;
        try {
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, userName);
            prepStmt.setString(2, propertyName);
            prepStmt.setString(3, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(4, tenantId);
                prepStmt.setInt(5, tenantId);
            }

            rs = prepStmt.executeQuery();
            while (rs.next()) {
                value = rs.getString(1);
            }
            return value;
        } catch (SQLException e) {
            String msg = "Error occurred while retrieving user profile property for user : " + userName +
                         " & property name : " + propertyName + " & profile name : " + profileName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(null, rs, prepStmt);
        }
    }

    /**
     * @param password
     * @param saltValue
     * @return
     * @throws UserStoreException
     */
    @Deprecated
    protected String preparePassword(String password, String saltValue) throws UserStoreException {
        try {
            String digestInput = password;
            if (saltValue != null) {
                digestInput = password + saltValue;
            }
            String digsestFunction = realmConfig.getUserStoreProperties().get(
                    JDBCRealmConstants.DIGEST_FUNCTION);
            if (digsestFunction != null) {

                if (digsestFunction
                        .equals(UserCoreConstants.RealmConfig.PASSWORD_HASH_METHOD_PLAIN_TEXT)) {
                    return password;
                }
                MessageDigest dgst;
                String provider = getJceProvider();
                if (provider != null) {
                    dgst = MessageDigest.getInstance(digsestFunction, provider);
                } else {
                    dgst = MessageDigest.getInstance(digsestFunction);
                }
                byte[] byteValue = dgst.digest(digestInput.getBytes());
                password = Base64.encode(byteValue);
            }
            return password;
        } catch (NoSuchAlgorithmException e) {
            String msg = "Error occurred while preparing password.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (NoSuchProviderException e) {
            throw new UserStoreException("Specified security provider is not available in this environment: ", e);
        }
    }

    /**
     * Prepare the password including the salt, and hashes if hash algorithm is provided
     *
     * @param password original password value
     * @param saltValue salt value
     * @return  hashed password or plain text password as a String
     * @throws UserStoreException
     */
    protected String preparePassword(Object password, String saltValue) throws UserStoreException {

        Secret credentialObj;
        try {
            credentialObj = Secret.getSecret(password);
        } catch (UnsupportedSecretTypeException e) {
            throw new UserStoreException("Unsupported credential type", e);
        }

        try {
            String passwordString;
            if (saltValue != null) {
                credentialObj.addChars(saltValue.toCharArray());
            }

            String digestFunction = realmConfig.getUserStoreProperties().get(JDBCRealmConstants.DIGEST_FUNCTION);
            if (digestFunction != null) {
                if (digestFunction.equals(UserCoreConstants.RealmConfig.PASSWORD_HASH_METHOD_PLAIN_TEXT)) {
                    passwordString = new String(credentialObj.getChars());
                    return passwordString;
                }
                MessageDigest digest;
                String provider = getJceProvider();
                if (provider != null) {
                    digest = MessageDigest.getInstance(digestFunction, provider);
                } else {
                    digest = MessageDigest.getInstance(digestFunction);
                }
                byte[] byteValue = digest.digest(credentialObj.getBytes());
                passwordString = Base64.encode(byteValue);
            } else {
                passwordString = new String(credentialObj.getChars());
            }

            return passwordString;
        } catch (NoSuchAlgorithmException e) {
            String msg = "Error occurred while preparing password.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException("Specified security provider is not available in this environment: ", e);
        } finally {
            credentialObj.clear();
        }
    }

    /**
     * @return
     * @throws UserStoreException
     */
    private DataSource loadUserStoreSpacificDataSoruce() throws UserStoreException {
        return DatabaseUtil.createUserStoreDataSource(realmConfig);
    }

    /**
     *
     */
    public Map<String, String> getProperties(Tenant tenant) throws UserStoreException {
        return this.realmConfig.getUserStoreProperties();
    }

    /**
     *
     */
    public void addRememberMe(String userName, String token)
            throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            String[] values = DatabaseUtil.getStringValuesFromDatabase(dbConnection,
                    HybridJDBCConstants.GET_REMEMBERME_VALUE_SQL, userName, tenantId);
            Date createdTime = Calendar.getInstance().getTime();
            if (values != null && values.length > 0 && values[0].length() > 0) {
                // udpate
                DatabaseUtil.updateDatabase(dbConnection,
                        HybridJDBCConstants.UPDATE_REMEMBERME_VALUE_SQL, token, createdTime,
                        userName, tenantId);
            } else {
                // add
                DatabaseUtil.updateDatabase(dbConnection,
                        HybridJDBCConstants.ADD_REMEMBERME_VALUE_SQL, userName, token, createdTime,
                        tenantId);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Database error occurred while saving remember me token for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (Exception e) {
            String errorMessage = "Error occurred while saving remember me token";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     * Checks whether the token is existing or not.
     *
     * @param userName
     * @param token
     * @return
     * @throws org.wso2.micro.integrator.security.user.api.UserStoreException
     */
    public boolean isExistingRememberMeToken(String userName, String token)
            throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        boolean isValid = false;
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String value = null;
        Date createdTime = null;
        try {
            dbConnection = getDBConnection();
            prepStmt = dbConnection.prepareStatement(HybridJDBCConstants.GET_REMEMBERME_VALUE_SQL);
            prepStmt.setString(1, userName);
            prepStmt.setInt(2, tenantId);
            rs = prepStmt.executeQuery();
            while (rs.next()) {
                value = rs.getString(1);
                createdTime = rs.getTimestamp(2);
            }
        } catch (SQLException e) {
            String errorMessage = "Error occurred while checking is existing remember me token for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }

        if (value != null && createdTime != null) {
            Calendar calendar = Calendar.getInstance();
            Date nowDate = calendar.getTime();
            calendar.setTime(createdTime);
            calendar.add(Calendar.SECOND, UserCoreConstants.REMEMBER_ME_COOKIE_TTL);
            Date expDate = calendar.getTime();
            if (expDate.before(nowDate)) {
                // Do nothing remember me expired.
                // Return the user gracefully
                log.debug("Remember me token has expired !!");
            } else {

                // We also need to compare the token
                if (value.equals(token)) {
                    isValid = true;
                } else {
                    log.debug("Remember me token in DB and token in request are different !!");
                    isValid = false;
                }
            }
        }

        return isValid;
    }

    /**
     *
     */
    public boolean isValidRememberMeToken(String userName, String token)
            throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        try {
            if (isExistingUser(userName)) {
                return isExistingRememberMeToken(userName, token);
            }
        } catch (Exception e) {
            log.error("Validating remember me token failed for" + userName);
            // not throwing exception.
            // because we need to seamlessly direct them to login uis
        }

        return false;
    }

    @Override
    public String[] getUserListFromProperties(String property, String value, String profileName)
            throws UserStoreException {

        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        if(value == null){
            throw new IllegalArgumentException("Filter value cannot be null");
        }
        if (value.contains(QUERY_FILTER_STRING_ANY)) {
            // This is to support LDAP like queries. Value having only * is restricted except one *.
            if (!value.matches("(\\*)\\1+")) {
                // Convert all the * to % except \*.
                value = value.replaceAll("(?<!\\\\)\\*", SQL_FILTER_STRING_ANY);
            }
        }

        String[] users = new String[0];
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        List<String> list = new ArrayList<String>();
        try {
            dbConnection = getDBConnection();
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USERS_FOR_PROP);
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, property);
            prepStmt.setString(2, value);
            prepStmt.setString(3, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(4, tenantId);
                prepStmt.setInt(5, tenantId);
            }
            rs = prepStmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                list.add(name);
            }

            if (list.size() > 0) {
                users = list.toArray(new String[list.size()]);
            }

        } catch (SQLException e) {
            String msg =
                    "Database error occurred while listing users for a property : " + property + " & value : " + value +
                    " & profile name : " + profileName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }

        return users;
    }

    @Override
    public String[] doGetExternalRoleListOfUser(String userName, String filter) throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Getting roles of user: " + userName + " with filter: " + filter);
        }

        String sqlStmt;
        String[] names;
        if (filter.equals("*") || StringUtils.isEmpty(filter)) {

            sqlStmt = getExternalRoleListSqlStatement(
                    realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USER_ROLE),
                    realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_USER_ROLE_CASE_INSENSITIVE));
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                names = getStringValuesFromDatabase(sqlStmt, userName, tenantId, tenantId, tenantId);
            } else {
                names = getStringValuesFromDatabase(sqlStmt, userName);
            }
        } else {
            filter = filter.trim();
            filter = filter.replace("*", "%");
            filter = filter.replace("?", "_");
            sqlStmt = getExternalRoleListSqlStatement(
                    realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_IS_USER_ROLE_EXIST), realmConfig
                            .getUserStoreProperty(
                                    JDBCCaseInsensitiveConstants.GET_IS_USER_ROLE_EXIST_CASE_INSENSITIVE));

            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                names = getStringValuesFromDatabase(sqlStmt, userName, tenantId, tenantId, tenantId, filter);
            } else {
                names = getStringValuesFromDatabase(sqlStmt, userName, filter);
            }
        }
        List<String> roles = new ArrayList<String>();
        if (log.isDebugEnabled()) {
            if (names != null) {
                for (String name : names) {
                    log.debug("Found role: " + name);
                }
            } else {
                log.debug("No external role found for the user: " + userName);
            }
        }

        Collections.addAll(roles, names);
        return roles.toArray(new String[roles.size()]);
    }

    @Override
    public org.wso2.micro.integrator.security.user.api.Properties getDefaultUserStoreProperties() {
        Properties properties = new Properties();
        properties.setMandatoryProperties(JDBCUserStoreConstants.JDBC_UM_MANDATORY_PROPERTIES.toArray
                (new Property[JDBCUserStoreConstants.JDBC_UM_MANDATORY_PROPERTIES.size()]));
        properties.setOptionalProperties(JDBCUserStoreConstants.JDBC_UM_OPTIONAL_PROPERTIES.toArray
                (new Property[JDBCUserStoreConstants.JDBC_UM_OPTIONAL_PROPERTIES.size()]));
        properties.setAdvancedProperties(JDBCUserStoreConstants.JDBC_UM_ADVANCED_PROPERTIES.toArray
                (new Property[JDBCUserStoreConstants.JDBC_UM_ADVANCED_PROPERTIES.size()]));
        return properties;
    }

    protected Map<String, Map<String, String>> getUsersPropertyValues(List<String> users, String[] propertyNames,
                                                                      String profileName) throws UserStoreException {

        Connection dbConnection = null;
        String sqlStmt;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String[] propertyNamesSorted = propertyNames.clone();
        Arrays.sort(propertyNamesSorted);

        Map<String, Map<String, String>> usersPropertyValuesMap = new HashMap<>();
        try {
            dbConnection = getDBConnection();
            StringBuilder usernameParameter = new StringBuilder();
            if (isCaseSensitiveUsername()) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USERS_PROPS_FOR_PROFILE);
                for (int i = 0; i < users.size(); i++) {

                    usernameParameter.append("'").append(users.get(i)).append("'");

                    if (i != users.size() - 1) {
                        usernameParameter.append(",");
                    }
                }
            } else {
                sqlStmt = realmConfig.getUserStoreProperty(
                        JDBCCaseInsensitiveConstants.GET_USERS_PROPS_FOR_PROFILE_CASE_INSENSITIVE);
                for (int i = 0; i < users.size(); i++) {

                    usernameParameter.append("LOWER('").append(users.get(i)).append("')");

                    if (i != users.size() - 1) {
                        usernameParameter.append(",");
                    }
                }
            }

            sqlStmt = sqlStmt.replaceFirst("\\?", usernameParameter.toString());
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
                prepStmt.setInt(3, tenantId);
            }

            rs = prepStmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(2);
                if (Arrays.binarySearch(propertyNamesSorted, name) < 0) {
                    continue;
                }
                String username = rs.getString(1);
                String value = rs.getString(3);

                if (usersPropertyValuesMap.get(username) != null) {
                    usersPropertyValuesMap.get(username).put(name, value);
                } else {
                    Map<String, String> attributes = new HashMap<>();
                    attributes.put(name, value);
                    usersPropertyValuesMap.put(username, attributes);
                }
            }
            return usersPropertyValuesMap;
        } catch (SQLException e) {
            String errorMessage = "Error Occurred while getting property values";
            if (log.isDebugEnabled()) {
                errorMessage = errorMessage + ": " + users;
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    protected Map<String, List<String>> doGetExternalRoleListOfUsers(List<String> userNames) throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Getting roles of users: " + userNames);
        }

        String sqlStmt;
        Map<String, List<String>> rolesListOfUsersMap = new HashMap<>();
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        try {
            dbConnection = getDBConnection();
            StringBuilder usernameParameter = new StringBuilder();
            if (isCaseSensitiveUsername()) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USERS_ROLE);
                if (sqlStmt == null) {
                    throw new UserStoreException("The sql statement for retrieving users roles is null");
                }
                for (int i = 0; i < userNames.size(); i++) {

                    usernameParameter.append("'").append(userNames.get(i)).append("'");

                    if (i != userNames.size() - 1) {
                        usernameParameter.append(",");
                    }
                }
            } else {
                sqlStmt = realmConfig
                        .getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_USERS_ROLE_CASE_INSENSITIVE);
                if (sqlStmt == null) {
                    throw new UserStoreException("The sql statement for retrieving users roles is null");
                }
                for (int i = 0; i < userNames.size(); i++) {

                    usernameParameter.append("LOWER('").append(userNames.get(i)).append("')");

                    if (i != userNames.size() - 1) {
                        usernameParameter.append(",");
                    }
                }
            }

            sqlStmt = sqlStmt.replaceFirst("\\?", usernameParameter.toString());
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(1, tenantId);
                prepStmt.setInt(2, tenantId);
                prepStmt.setInt(3, tenantId);
            }
            rs = prepStmt.executeQuery();
            String domainName = getMyDomainName();
            while (rs.next()) {
                String username = UserCoreUtil.addDomainToName(rs.getString(1), domainName);
                String roleName = UserCoreUtil.addDomainToName(rs.getString(2), domainName);
                if (rolesListOfUsersMap.get(username) != null) {
                    rolesListOfUsersMap.get(username).add(roleName);
                } else {
                    List<String> roleNames = new ArrayList<>();
                    roleNames.add(roleName);
                    rolesListOfUsersMap.put(username, roleNames);
                }
            }
            return rolesListOfUsersMap;
        } catch (SQLException e) {
            String errorMessage = "Error Occurred while getting role lists of users";
            if (log.isDebugEnabled()) {
                errorMessage = errorMessage + ": " + userNames;
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    protected void doAddSharedRole(String roleName, String[] userList) throws UserStoreException {

        Connection dbConnection = null;

        try {
            dbConnection = getDBConnection();
            String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_SHARED_ROLE);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt, true, roleName, tenantId);
            } else {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt, true, roleName);
            }
            if (userList != null) {
                // add role to user
                int roleTenantId = Constants.SUPER_TENANT_ID;
                if (isCaseSensitiveUsername()) {
                    sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_SHARED_ROLE_TO_USER);
                } else {
                    sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                            .ADD_SHARED_ROLE_TO_USER_CASE_INSENSITIVE);
                }
                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt, roleName,
                        roleTenantId, userList, tenantId,
                        tenantId, roleTenantId);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String msg = "Database error occurred while adding shared role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } catch (Exception e) {
            String msg = "Error occurred while adding shared role.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    @Override
    protected String[] doGetSharedRoleListOfUser(String userName,
                                                 String tenantDomain, String filter) throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Looking for shared roles for user: " + userName + " for tenant: " + tenantDomain);
        }

        if (isSharedGroupEnabled()) {
            // shared roles
            String sqlStmt;
            if (isCaseSensitiveUsername()) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_SHARED_ROLES_FOR_USER);
            } else {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                        .GET_SHARED_ROLES_FOR_USER_CASE_INSENSITIVE);
            }
            String[] sharedNames = getRoleNamesWithDomain(sqlStmt, userName, tenantId, true);

            return sharedNames;
        }
        return new String[0];
    }

    /**
     * Add properties as a batch
     *
     * @param dbConnection
     * @param userName
     * @param properties
     * @param profileName
     * @throws org.wso2.micro.integrator.security.user.api.UserStoreException
     */
    private void addProperties(Connection dbConnection, String userName, Map<String, String> properties,
                               String profileName) throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        String type;
        try {
            type = DatabaseCreator.getDatabaseType(dbConnection);
        } catch (Exception e) {
            String msg = "Error occurred while adding user properties for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        }

        String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_PROPERTY + "-" + type);
        if (sqlStmt == null) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER_PROPERTY);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for add user property sql is null");
        }

        PreparedStatement prepStmt = null;
        boolean localConnection = false;

        try {
            if (dbConnection == null) {
                localConnection = true;
                dbConnection = getDBConnection();
            }
            prepStmt = dbConnection.prepareStatement(sqlStmt);

            Map<String, String> userAttributes = new HashMap<>();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String attributeName = getClaimAtrribute(entry.getKey(), userName, null);
                String attributeValue = entry.getValue();
                userAttributes.put(attributeName, attributeValue);
            }

            for (Map.Entry<String, String> entry : userAttributes.entrySet()) {
                String propertyName = entry.getKey();
                String propertyValue = entry.getValue();
                if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                    if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                        batchUpdateStringValuesToDatabase(prepStmt, propertyName, propertyValue, profileName,
                                tenantId, userName, tenantId);
                    } else {
                        batchUpdateStringValuesToDatabase(prepStmt, userName, tenantId, propertyName, propertyValue,
                                profileName, tenantId);
                    }
                } else {
                    batchUpdateStringValuesToDatabase(prepStmt, userName, propertyName, propertyValue, profileName);
                }
            }

            int[] counts = prepStmt.executeBatch();
            if (log.isDebugEnabled()) {
                int totalUpdated = 0;
                if (counts != null) {
                    for (int i : counts) {
                        totalUpdated += i;
                    }
                }

                if (totalUpdated == 0) {
                    log.debug("No rows were updated");
                }
                log.debug("Executed query is " + sqlStmt + " and number of updated rows :: " + totalUpdated);
            }

            if (localConnection) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            String msg = "Error occurred while updating string values to database.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            if (localConnection) {
                DatabaseUtil.closeAllConnections(dbConnection);
            }
            DatabaseUtil.closeAllConnections(null, prepStmt);
        }
    }

    /**
     * Update properties as a batch
     *
     * @param dbConnection
     * @param userName
     * @param properties
     * @param profileName
     * @throws org.wso2.micro.integrator.security.user.api.UserStoreException
     */
    private void updateProperties(Connection dbConnection, String userName, Map<String, String> properties,
                                 String profileName) throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        String type;
        try {
            type = DatabaseCreator.getDatabaseType(dbConnection);
        } catch (Exception e) {
            String msg = "Error occurred while updating user properties for user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        }

        String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.UPDATE_USER_PROPERTY + "-" + type);
        if (sqlStmt == null) {
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.UPDATE_USER_PROPERTY);
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for update user property sql is null");
        }

        PreparedStatement prepStmt = null;
        boolean localConnection = false;

        try {
            if (dbConnection == null) {
                localConnection = true;
                dbConnection = getDBConnection();
            }
            prepStmt = dbConnection.prepareStatement(sqlStmt);

            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String propertyName = getClaimAtrribute(entry.getKey(), userName, null);
                String propertyValue = entry.getValue();
                if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                    if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                        batchUpdateStringValuesToDatabase(prepStmt, propertyName, propertyValue, profileName,
                                tenantId, userName, tenantId);
                    } else {
                        batchUpdateStringValuesToDatabase(prepStmt, propertyValue, userName, tenantId, propertyName,
                                profileName, tenantId);
                    }
                } else {
                    batchUpdateStringValuesToDatabase(prepStmt, propertyValue, userName, propertyName, profileName);
                }
            }

            int[] counts = prepStmt.executeBatch();
            if (log.isDebugEnabled()) {
                int totalUpdated = 0;
                if (counts != null) {
                    for (int i : counts) {
                        totalUpdated += i;
                    }
                }

                if (totalUpdated == 0) {
                    log.debug("No rows were updated");
                }
                log.debug("Executed query is " + sqlStmt + " and number of updated rows :: " + totalUpdated);
            }

            if (localConnection) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            String msg = "Error occurred while updating string values to database.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            if (localConnection) {
                DatabaseUtil.closeAllConnections(dbConnection);
            }
            DatabaseUtil.closeAllConnections(null, prepStmt);
        }
    }

    /**
     * Prepare the batch
     *
     * @param prepStmt
     * @param params
     * @throws UserStoreException
     */
    private void batchUpdateStringValuesToDatabase(PreparedStatement prepStmt, Object... params) throws
            UserStoreException {
        try {
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param == null) {
                        throw new UserStoreException("Invalid data provided");
                    } else if (param instanceof String) {
                        prepStmt.setString(i + 1, (String) param);
                    } else if (param instanceof Integer) {
                        prepStmt.setInt(i + 1, (Integer) param);
                    } else if (param instanceof Date) {
                        prepStmt.setTimestamp(i + 1, new Timestamp(System.currentTimeMillis()));
                    } else if (param instanceof Boolean) {
                        prepStmt.setBoolean(i + 1, (Boolean) param);
                    }
                }
            }
            prepStmt.addBatch();
        } catch (SQLException e) {
            String msg = "Error occurred while updating property values to database.";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        }
    }

    @Override
    protected RoleContext createRoleContext(String roleName) {

        JDBCRoleContext searchCtx = new JDBCRoleContext();
        String[] roleNameParts;

        if (isSharedGroupEnabled()) {
            roleNameParts = roleName.split(UserCoreConstants.TENANT_DOMAIN_COMBINER);
            if (roleNameParts.length > 1 && (roleNameParts[1] == null || roleNameParts[1].equals("null"))) {
                roleNameParts = new String[]{roleNameParts[0]};
            }
        } else {
            roleNameParts = new String[]{roleName};
        }

        int tenantId = -1;
        if (roleNameParts.length > 1) {
            tenantId = Integer.parseInt(roleNameParts[1]);
            searchCtx.setTenantId(tenantId);
        } else {
            tenantId = this.tenantId;
            searchCtx.setTenantId(tenantId);
        }

        if (tenantId != this.tenantId) {
            searchCtx.setShared(true);
        }

        searchCtx.setRoleName(roleNameParts[0]);
        return searchCtx;
    }

    @Override
    protected PaginatedSearchResult doListUsers(String filter, int limit, int offset) throws UserStoreException {

        String[] users = new String[0];
        Connection dbConnection = null;
        String sqlStmt;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        int givenMax;
        int searchTime;

        PaginatedSearchResult result = new PaginatedSearchResult();

        if (limit == 0) {
            return result;
        }

        try {
            givenMax = Integer.parseInt(realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig
                    .PROPERTY_MAX_USER_LIST));
        } catch (Exception e) {
            givenMax = UserCoreConstants.MAX_USER_ROLE_LIST;
        }

        try {
            searchTime = Integer.parseInt(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_SEARCH_TIME));
        } catch (Exception e) {
            searchTime = UserCoreConstants.MAX_SEARCH_TIME;
        }

        if (limit < 0 || limit > givenMax) {
            limit = givenMax;
        }

        try {

            if (filter != null && filter.trim().length() != 0) {
                filter = filter.trim();
                filter = filter.replace("*", "%");
                filter = filter.replace("?", "_");
            } else {
                filter = "%";
            }

            List<String> list = new LinkedList<>();

            dbConnection = getDBConnection();

            if (dbConnection == null) {
                throw new UserStoreException("null connection");
            }

            String type = DatabaseCreator.getDatabaseType(dbConnection);

            if (offset <= 0) {
                offset = 0;
            } else {
                offset = offset - 1;
            }

            if (DB2.equalsIgnoreCase(type)) {
                int initialOffset = offset;
                offset = offset + limit;
                limit = initialOffset + 1;
            } else if (MSSQL.equalsIgnoreCase(type)) {
                int initialOffset = offset;
                offset = limit + offset;
                limit = initialOffset + 1;
            } else if (ORACLE.equalsIgnoreCase(type)) {
                limit = offset + limit;
            }

            if (isCaseSensitiveUsername()) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USER_FILTER_PAGINATED + "-" + type);
                if (sqlStmt == null) {
                    sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USER_FILTER_PAGINATED);
                }
            } else {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                        .GET_USER_FILTER_CASE_INSENSITIVE_PAGINATED + "-" + type);
                if (sqlStmt == null) {
                    sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants.GET_USER_FILTER_CASE_INSENSITIVE_PAGINATED);
                }
            }

            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, filter);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
                prepStmt.setLong(3, limit);
                prepStmt.setLong(4, offset);
            } else {
                prepStmt.setLong(2, limit);
                prepStmt.setLong(3, offset);
            }

            try {
                prepStmt.setQueryTimeout(searchTime);
            } catch (Exception e) {
                // this can be ignored since timeout method is not implemented
                log.debug(e);
            }

            try {
                rs = prepStmt.executeQuery();
            } catch (SQLException e) {
                if (e instanceof SQLTimeoutException) {
                    log.error("The cause might be a time out. Hence ignored", e);
                    return result;
                }
                String errorMessage =
                        "Error while fetching users according to filter : " + filter + " & limit " +
                                ": " + limit;
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e);
                }
                throw new UserStoreException(errorMessage, e);
            }

            while (rs.next()) {

                String name = rs.getString(1);
                if (UserCoreConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(name)) {
                    continue;
                }
                // append the domain if exist
                String domain = realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                name = UserCoreUtil.addDomainToName(name, domain);
                list.add(name);
            }
            rs.close();

            if (list.size() > 0) {
                users = list.toArray(new String[list.size()]);
            }

            Arrays.sort(users);
        } catch (Exception e) {
            String msg = "Error occurred while retrieving users for filter : " + filter + " & limit : " + limit;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        result.setUsers(users);

        if (users.length == 0) {
            result.setSkippedUserCount(doGetListUsersCount(filter));
        }
        return result;
    }

    protected int doGetListUsersCount(String filter) throws UserStoreException {

        Connection dbConnection = null;
        String sqlStmt;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        int count = 0;

        try {

            if (filter != null && StringUtils.isNotEmpty(filter.trim())) {
                filter = filter.trim().replace("*", "%");
                filter = filter.replace("?", "_");
            } else {
                filter = "%";
            }

            dbConnection = getDBConnection();

            if (dbConnection == null) {
                throw new UserStoreException("null connection");
            }

            if (isCaseSensitiveUsername()) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USER_FILTER_PAGINATED_COUNT);
            } else {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCCaseInsensitiveConstants
                        .GET_USER_FILTER_CASE_INSENSITIVE_PAGINATED_COUNT);
            }

            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, filter);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }

            rs = prepStmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }

        } catch (SQLException e) {
            String msg = "Error occurred while retrieving users count for filter : " + filter;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return count;

    }

    @Override
    public PaginatedSearchResult getUserListFromProperties(String property, String value, String profileName, int limit, int offset)
            throws UserStoreException {

        PaginatedSearchResult result = new PaginatedSearchResult();

        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        if (limit == 0) {
            return result;
        }

        if(value == null){
            throw new IllegalArgumentException("Filter value cannot be null");
        }
        if (value.contains(QUERY_FILTER_STRING_ANY)) {
            // This is to support LDAP like queries. Value having only * is restricted except one *.
            if (!value.matches("(\\*)\\1+")) {
                // Convert all the * to % except \*.
                value = value.replaceAll("(?<!\\\\)\\*", SQL_FILTER_STRING_ANY);
            }
        }

        String[] users = new String[0];
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        List<String> list = new ArrayList<String>();
        try {
            dbConnection = getDBConnection();
            String type = DatabaseCreator.getDatabaseType(dbConnection);

            if (offset <= 0) {
                offset = 0;
            } else {
                offset = offset - 1;
            }

            if (ORACLE.equalsIgnoreCase(type)) {
                limit = offset + limit;
            } else if (MSSQL.equalsIgnoreCase(type)) {
                int initialOffset = offset;
                offset = limit + offset;
                limit = initialOffset + 1;
            } else if (DB2.equalsIgnoreCase(type)) {
                int initialOffset = offset;
                offset = offset + limit;
                limit = initialOffset + 1;
            }

            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_PAGINATED_USERS_FOR_PROP + "-" + type);
            if (sqlStmt == null) {
                sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_PAGINATED_USERS_FOR_PROP);
            }
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, property);
            prepStmt.setString(2, value);
            prepStmt.setString(3, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(4, tenantId);
                prepStmt.setInt(5, tenantId);
                prepStmt.setInt(6, limit);
                prepStmt.setInt(7, offset);
            } else {
                prepStmt.setInt(4, limit);
                prepStmt.setInt(5, offset);
            }
            rs = prepStmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                list.add(name);
            }

            if (list.size() > 0) {
                users = list.toArray(new String[list.size()]);
            }
            result.setUsers(users);
        } catch (Exception e) {
            String msg = "Database error occurred while paginating users for a property : " + property + " & value : " +
                            value + "& profile name : " + profileName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }

        if (users.length == 0) {
            result.setSkippedUserCount(getUserListFromPropertiesCount(property, value, profileName));
        }
        return result;
    }

    protected PaginatedSearchResult doGetUserList(Condition condition, String profileName, int limit, int offset,
                                                  String sortBy, String sortOrder) throws UserStoreException {

        boolean isGroupFiltering = false;
        boolean isUsernameFiltering = false;
        boolean isClaimFiltering = false;
        // To identify Mysql multi group filter and multi claim filter.
        int totalMultiGroupFilters = 0;
        int totalMulitClaimFitlers = 0;

        PaginatedSearchResult result = new PaginatedSearchResult();

        if (limit == 0) {
            return result;
        }

        //Since we support only AND operation get expressions as a list.
        List<ExpressionCondition> expressionConditions = new ArrayList<>();
        getExpressionConditions(condition, expressionConditions);

        for (ExpressionCondition expressionCondition : expressionConditions) {
            if (ExpressionAttribute.ROLE.toString().equals(expressionCondition.getAttributeName())) {
                isGroupFiltering = true;
                totalMultiGroupFilters++;
            } else if (ExpressionAttribute.USERNAME.toString().equals(expressionCondition.getAttributeName())) {
                isUsernameFiltering = true;
            } else {
                isClaimFiltering = true;
                totalMulitClaimFitlers++;
            }
        }


        String[] users = new String[0];
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        List<String> list = new ArrayList<>();
        try {
            dbConnection = getDBConnection();
            String type = DatabaseCreator.getDatabaseType(dbConnection);

            if (offset <= 0) {
                offset = 0;
            } else {
                offset = offset - 1;
            }

            if (DB2.equalsIgnoreCase(type)) {
                int initialOffset = offset;
                offset = offset + limit;
                limit = initialOffset + 1;
            } else if (ORACLE.equalsIgnoreCase(type)) {
                limit = offset + limit;
            } else if (MSSQL.equalsIgnoreCase(type)) {
                int initialOffset = offset;
                offset = limit + offset;
                limit = initialOffset + 1;
            }

            SqlBuilder sqlBuilder = getQueryString(isGroupFiltering, isUsernameFiltering, isClaimFiltering,
                    expressionConditions, limit, offset, sortBy, sortOrder, profileName, type, totalMultiGroupFilters,
                    totalMulitClaimFitlers);

            if (MYSQL.equals(type) && totalMultiGroupFilters > 1 && totalMulitClaimFitlers > 1) {
                String fullQuery = sqlBuilder.getQuery();
                String[] splits = fullQuery.split("INTERSECT ");
                int startIndex = 0;
                int endIndex = 0;
                for (String query : splits) {
                    List<String> tempUserList = new ArrayList<>();
                    int occurance = StringUtils.countMatches(query, "?");
                    endIndex = endIndex + occurance;
                    prepStmt = dbConnection.prepareStatement(query);
                    populatePrepareStatement(sqlBuilder, prepStmt, startIndex, endIndex);
                    rs = prepStmt.executeQuery();
                    while (rs.next()) {
                        String name = rs.getString(1);
                        tempUserList.add(UserCoreUtil.addDomainToName(name, getMyDomainName()));
                    }

                    if (startIndex == 0) {
                        list = tempUserList;
                    } else {
                        list.retainAll(tempUserList);
                    }
                    startIndex += occurance;
                }
            } else {
                prepStmt = dbConnection.prepareStatement(sqlBuilder.getQuery());
                int occurance = StringUtils.countMatches(sqlBuilder.getQuery(), "?");
                populatePrepareStatement(sqlBuilder, prepStmt, 0, occurance);
                rs = prepStmt.executeQuery();
                while (rs.next()) {
                    String name = rs.getString(1);
                    list.add(UserCoreUtil.addDomainToName(name, getMyDomainName()));
                }
            }

            if (list.size() > 0) {
                users = list.toArray(new String[list.size()]);
            }
            result.setUsers(users);

        } catch (Exception e) {
            String msg = "Error occur while doGetUserList for multi attribute searching";
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }

        return result;
    }

    private void populatePrepareStatement(SqlBuilder sqlBuilder, PreparedStatement prepStmt, int startIndex,
                                          int endIndex) throws SQLException {

        Map<Integer, Integer> integerParameters = sqlBuilder.getIntegerParameters();
        Map<Integer, String> stringParameters = sqlBuilder.getStringParameters();
        Map<Integer, Long> longParameters = sqlBuilder.getLongParameters();

        for (Map.Entry<Integer, Integer> entry : integerParameters.entrySet()) {
            if (entry.getKey() > startIndex && entry.getKey() <= endIndex) {
                prepStmt.setInt(entry.getKey() - startIndex, entry.getValue());
            }
        }

        for (Map.Entry<Integer, String> entry : stringParameters.entrySet()) {
            if (entry.getKey() > startIndex && entry.getKey() <= endIndex) {
                prepStmt.setString(entry.getKey() - startIndex, entry.getValue());
            }
        }

        for (Map.Entry<Integer, Long> entry : longParameters.entrySet()) {
            if (entry.getKey() > startIndex && entry.getKey() <= endIndex) {
                prepStmt.setLong(entry.getKey() - startIndex, entry.getValue());
            }
        }
    }

    protected SqlBuilder getQueryString(boolean isGroupFiltering, boolean isUsernameFiltering, boolean
            isClaimFiltering, List<ExpressionCondition> expressionConditions, int limit, int offset, String sortBy,
                                        String sortOrder, String profileName, String dbType, int totalMultiGroupFilters,
                                        int totalMulitClaimFitlers) throws UserStoreException {

        StringBuilder sqlStatement;
        SqlBuilder sqlBuilder;
        boolean hitGroupFilter = false;
        boolean hitClaimFilter = false;
        int groupFilterCount = 0;
        int claimFilterCount = 0;

        if (isGroupFiltering && isUsernameFiltering && isClaimFiltering || isGroupFiltering && isClaimFiltering) {

            if (DB2.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT ROW_NUMBER() OVER (ORDER BY " +
                        "UM_USER_NAME) AS rn, p.*  FROM (SELECT DISTINCT UM_USER_NAME  FROM UM_ROLE R INNER JOIN " +
                        "UM_USER_ROLE UR ON R.UM_ID = UR.UM_ROLE_ID INNER JOIN UM_USER U ON UR.UM_USER_ID =U.UM_ID " +
                        "INNER JOIN UM_USER_ATTRIBUTE UA ON U.UM_ID = UA.UM_USER_ID");
            } else if (MSSQL.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT UM_USER_NAME, ROW_NUMBER() OVER " +
                        "(ORDER BY UM_USER_NAME) AS RowNum FROM (SELECT DISTINCT UM_USER_NAME FROM UM_ROLE R INNER " +
                        "JOIN UM_USER_ROLE UR ON R" +
                        ".UM_ID = UR.UM_ROLE_ID INNER JOIN UM_USER U ON UR.UM_USER_ID =U.UM_ID INNER JOIN " +
                        "UM_USER_ATTRIBUTE UA ON U.UM_ID = UA.UM_USER_ID");
            } else if (ORACLE.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT UM_USER_NAME, rownum AS rnum FROM " +
                        "(SELECT  UM_USER_NAME FROM UM_ROLE R INNER JOIN UM_USER_ROLE UR ON R.UM_ID = UR.UM_ROLE_ID " +
                        "INNER JOIN UM_USER U ON UR.UM_USER_ID =U.UM_ID INNER JOIN UM_USER_ATTRIBUTE UA ON U.UM_ID = " +
                        "UA.UM_USER_ID");
            } else {
                sqlStatement = new StringBuilder("SELECT DISTINCT UM_USER_NAME FROM UM_ROLE R INNER JOIN UM_USER_ROLE UR" +
                        " INNER JOIN UM_USER U INNER JOIN UM_USER_ATTRIBUTE UA ON R.UM_ID = UR.UM_ROLE_ID AND UR.UM_USER_ID =" +
                        " U.UM_ID AND U.UM_ID = UA.UM_USER_ID");
            }
            sqlBuilder = new SqlBuilder(sqlStatement)
                    .where("R.UM_TENANT_ID = ?", tenantId)
                    .where("U.UM_TENANT_ID = ?", tenantId)
                    .where("UR.UM_TENANT_ID = ?", tenantId)
                    .where("UA.UM_TENANT_ID = ?", tenantId)
                    .where("UA.UM_PROFILE_ID = ?", profileName);
        } else if (isGroupFiltering && isUsernameFiltering || isGroupFiltering) {
            if (DB2.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT ROW_NUMBER() OVER (ORDER BY " +
                        "UM_USER_NAME) AS rn, p.*  FROM (SELECT DISTINCT UM_USER_NAME  FROM UM_ROLE R INNER JOIN " +
                        "UM_USER_ROLE UR ON R.UM_ID = UR.UM_ROLE_ID INNER JOIN UM_USER U ON UR.UM_USER_ID =U.UM_ID ");
            } else if (MSSQL.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT UM_USER_NAME, ROW_NUMBER() OVER " +
                        "(ORDER BY UM_USER_NAME) AS RowNum FROM (SELECT DISTINCT UM_USER_NAME FROM UM_ROLE R INNER " +
                        "JOIN UM_USER_ROLE UR ON R.UM_ID = UR.UM_ROLE_ID INNER JOIN UM_USER U ON UR.UM_USER_ID =U" +
                        ".UM_ID");
            } else if (ORACLE.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT UM_USER_NAME, rownum AS rnum FROM " +
                        "(SELECT  UM_USER_NAME FROM UM_ROLE R INNER JOIN UM_USER_ROLE UR ON R.UM_ID = UR.UM_ROLE_ID " +
                        "INNER JOIN UM_USER U ON UR.UM_USER_ID =U.UM_ID");
            } else {
                sqlStatement = new StringBuilder("SELECT DISTINCT UM_USER_NAME FROM UM_ROLE R INNER JOIN UM_USER_ROLE UR" +
                        " INNER JOIN UM_USER U ON R.UM_ID = UR.UM_ROLE_ID AND UR.UM_USER_ID =U.UM_ID");
            }

            sqlBuilder = new SqlBuilder(sqlStatement)
                    .where("R.UM_TENANT_ID = ?", tenantId)
                    .where("U.UM_TENANT_ID = ?", tenantId)
                    .where("UR.UM_TENANT_ID = ?", tenantId);
        } else if (isUsernameFiltering && isClaimFiltering || isClaimFiltering) {
            if (DB2.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT ROW_NUMBER() OVER (ORDER BY " +
                        "UM_USER_NAME) AS rn, p.*  FROM (SELECT DISTINCT UM_USER_NAME  FROM  UM_USER U INNER JOIN " +
                        "UM_USER_ATTRIBUTE UA ON U.UM_ID = UA.UM_USER_ID");
            } else if (MSSQL.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT UM_USER_NAME, ROW_NUMBER() OVER " +
                        "(ORDER BY UM_USER_NAME) AS RowNum FROM (SELECT DISTINCT UM_USER_NAME FROM UM_USER U INNER JOIN " +
                        "UM_USER_ATTRIBUTE UA ON U.UM_ID = UA.UM_USER_ID");
            } else if (ORACLE.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT UM_USER_NAME, rownum AS rnum FROM " +
                        "(SELECT UM_USER_NAME FROM UM_USER U INNER JOIN UM_USER_ATTRIBUTE UA ON U.UM_ID = " +
                        "UA.UM_USER_ID");
            } else {
                sqlStatement = new StringBuilder("SELECT DISTINCT UM_USER_NAME FROM UM_USER U INNER JOIN " +
                        "UM_USER_ATTRIBUTE UA ON U.UM_ID = UA.UM_USER_ID");
            }
            sqlBuilder = new SqlBuilder(sqlStatement)
                    .where("U.UM_TENANT_ID = ?", tenantId)
                    .where("UA.UM_TENANT_ID = ?", tenantId)
                    .where("UA.UM_PROFILE_ID = ?", profileName);
        } else if (isUsernameFiltering) {
            if (DB2.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT ROW_NUMBER() OVER (ORDER BY " +
                        "UM_USER_NAME) AS rn, p.*  FROM (SELECT DISTINCT UM_USER_NAME  FROM UM_USER U");
            } else if (MSSQL.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT UM_USER_NAME, ROW_NUMBER() OVER " +
                        "(ORDER BY UM_USER_NAME) AS RowNum FROM (SELECT DISTINCT UM_USER_NAME FROM UM_USER U");
            } else if (ORACLE.equals(dbType)) {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM (SELECT UM_USER_NAME, rownum AS rnum FROM " +
                        "(SELECT UM_USER_NAME FROM UM_USER U");
            } else {
                sqlStatement = new StringBuilder("SELECT UM_USER_NAME FROM UM_USER U");
            }

            sqlBuilder = new SqlBuilder(sqlStatement)
                    .where("U.UM_TENANT_ID = ?", tenantId);
        } else {
            throw new UserStoreException("Condition is not valid.");
        }

        SqlBuilder header = new SqlBuilder(new StringBuilder(sqlBuilder.getSql()));
        addingWheres(sqlBuilder, header);

        for (ExpressionCondition expressionCondition : expressionConditions) {
            if (ExpressionAttribute.ROLE.toString().equals(expressionCondition.getAttributeName())) {
                if (!MYSQL.equals(dbType) || (MYSQL.equals(dbType) && totalMultiGroupFilters > 1 &&
                        totalMulitClaimFitlers > 1)) {
                    multiGroupQueryBuilder(sqlBuilder, header, hitGroupFilter, expressionCondition);
                    hitGroupFilter = true;
                } else {
                    multiGroupMySqlQueryBuilder(sqlBuilder, groupFilterCount, expressionCondition);
                    groupFilterCount++;
                }
            } else if (ExpressionOperation.EQ.toString().equals(expressionCondition.getOperation()) &&
                    ExpressionAttribute.USERNAME.toString().equals(expressionCondition.getAttributeName())) {
                if (isCaseSensitiveUsername()) {
                    sqlBuilder.where("U.UM_USER_NAME = ?", expressionCondition.getAttributeValue());
                } else {
                    sqlBuilder.where("U.UM_USER_NAME = LOWER(?)", expressionCondition.getAttributeValue());
                }
            } else if (ExpressionOperation.CO.toString().equals(expressionCondition.getOperation()) &&
                    ExpressionAttribute.USERNAME.toString().equals(expressionCondition.getAttributeName())) {
                if (isCaseSensitiveUsername()) {
                    sqlBuilder.where("U.UM_USER_NAME LIKE ?", "%" + expressionCondition.getAttributeValue()
                            + "%");
                } else {
                    sqlBuilder.where("U.UM_USER_NAME LIKE LOWER(?)", "%" +
                            expressionCondition.getAttributeValue() + "%");
                }
            } else if (ExpressionOperation.EW.toString().equals(expressionCondition.getOperation()) &&
                    ExpressionAttribute.USERNAME.toString().equals(expressionCondition.getAttributeName())) {
                if (isCaseSensitiveUsername()) {
                    sqlBuilder.where("U.UM_USER_NAME LIKE ?", "%" + expressionCondition.getAttributeValue());
                } else {
                    sqlBuilder.where("U.UM_USER_NAME LIKE LOWER(?)", "%" +
                            expressionCondition.getAttributeValue());
                }
            } else if (ExpressionOperation.SW.toString().equals(expressionCondition.getOperation()) &&
                    ExpressionAttribute.USERNAME.toString().equals(expressionCondition.getAttributeName())) {
                if (isCaseSensitiveUsername()) {
                    sqlBuilder.where("U.UM_USER_NAME LIKE ?", expressionCondition.getAttributeValue() + "%");
                } else {
                    sqlBuilder.where("U.UM_USER_NAME LIKE LOWER(?)", expressionCondition.getAttributeValue()
                            + "%");
                }
            } else {
                // Claim filtering
                if (!MYSQL.equals(dbType) || (MYSQL.equals(dbType) && totalMultiGroupFilters > 1 &&
                        totalMulitClaimFitlers > 1)) {
                    multiClaimQueryBuilder(sqlBuilder, header, hitClaimFilter, expressionCondition);
                    hitClaimFilter = true;
                } else {
                    multiClaimMySqlQueryBuilder(sqlBuilder, claimFilterCount, expressionCondition);
                    claimFilterCount++;
                }
            }
        }

        if (MYSQL.equals(dbType)) {
            sqlBuilder.updateSql(" GROUP BY U.UM_USER_NAME ");
            if (groupFilterCount > 0) {
                sqlBuilder.updateSql(" HAVING COUNT(DISTINCT R.UM_ROLE_NAME) = " + groupFilterCount);
            }
            if (claimFilterCount > 0) {
                sqlBuilder.updateSql(" HAVING COUNT(DISTINCT UA.UM_ATTR_VALUE) = " + claimFilterCount);
            }
        }

        if (!(MYSQL.equals(dbType) && totalMultiGroupFilters > 1 && totalMulitClaimFitlers > 1)) {
            if (DB2.equals(dbType)) {
                sqlBuilder.setTail(") AS p) WHERE rn BETWEEN ? AND ?", limit, offset);
            } else if (MSSQL.equals(dbType)) {
                sqlBuilder.setTail(") AS R) AS P WHERE P.RowNum BETWEEN ? AND ?", limit, offset);
            } else if (ORACLE.equals(dbType)) {
                sqlBuilder.setTail(" ORDER BY UM_USER_NAME) where rownum <= ?) WHERE  rnum > ?", limit, offset);
            } else {
                sqlBuilder.setTail(" ORDER BY UM_USER_NAME ASC LIMIT ? OFFSET ?", limit, offset);
            }
        }
        return sqlBuilder;
    }

    private void multiGroupQueryBuilder(SqlBuilder sqlBuilder, SqlBuilder header, boolean hitFirstRound,
                                        ExpressionCondition expressionCondition) {

        if (hitFirstRound) {
            sqlBuilder.updateSql(" INTERSECT " + header.getSql());
            addingWheres(header, sqlBuilder);
            buildGroupWhereConditions(sqlBuilder, expressionCondition.getOperation(),
                    expressionCondition.getAttributeValue());
        } else {
            buildGroupWhereConditions(sqlBuilder, expressionCondition.getOperation(),
                    expressionCondition.getAttributeValue());
        }
    }

    private void buildGroupWhereConditions(SqlBuilder sqlBuilder, String operation, String value) {

        if (ExpressionOperation.EQ.toString().equals(operation)) {
            sqlBuilder.where("R.UM_ROLE_NAME = ?", value);
        } else if (ExpressionOperation.EW.toString().equals(operation)) {
            sqlBuilder.where("R.UM_ROLE_NAME LIKE ?", "%" + value);
        } else if (ExpressionOperation.CO.toString().equals(operation)) {
            sqlBuilder.where("R.UM_ROLE_NAME LIKE ?", "%" + value + "%");
        } else if (ExpressionOperation.SW.toString().equals(operation)) {
            sqlBuilder.where("R.UM_ROLE_NAME LIKE ?", value + "%");
        }
    }

    private void multiGroupMySqlQueryBuilder(SqlBuilder sqlBuilder, int groupFilterCount,
                                             ExpressionCondition expressionCondition) {

        if (groupFilterCount == 0) {
            buildGroupWhereConditions(sqlBuilder, expressionCondition.getOperation(),
                    expressionCondition.getAttributeValue());
        } else {
            buildGroupConditionWithOROperator(sqlBuilder, expressionCondition.getOperation(),
                    expressionCondition.getAttributeValue());
        }
    }

    private void buildGroupConditionWithOROperator(SqlBuilder sqlBuilder, String operation, String value) {

        if (ExpressionOperation.EQ.toString().equals(operation)) {
            sqlBuilder.updateSqlWithOROperation("R.UM_ROLE_NAME = ?", value);
        } else if (ExpressionOperation.EW.toString().equals(operation)) {
            sqlBuilder.updateSqlWithOROperation("R.UM_ROLE_NAME LIKE ?", "%" + value);
        } else if (ExpressionOperation.CO.toString().equals(operation)) {
            sqlBuilder.updateSqlWithOROperation("R.UM_ROLE_NAME LIKE ?", "%" + value + "%");
        } else if (ExpressionOperation.SW.toString().equals(operation)) {
            sqlBuilder.updateSqlWithOROperation("R.UM_ROLE_NAME LIKE ?", value + "%");
        }
    }

    private void multiClaimQueryBuilder(SqlBuilder sqlBuilder, SqlBuilder header, boolean hitFirstRound,
                                        ExpressionCondition expressionCondition) {

        if (hitFirstRound) {
            sqlBuilder.updateSql(" INTERSECT " + header.getSql());
            addingWheres(header, sqlBuilder);
            buildClaimWhereConditions(sqlBuilder, expressionCondition.getAttributeName(),
                    expressionCondition.getOperation(), expressionCondition.getAttributeValue());
        } else {
            buildClaimWhereConditions(sqlBuilder, expressionCondition.getAttributeName(),
                    expressionCondition.getOperation(), expressionCondition.getAttributeValue());
        }
    }

    private void buildClaimWhereConditions(SqlBuilder sqlBuilder, String attributeName, String operation,
                                           String attributeValue) {

        sqlBuilder.where("UA.UM_ATTR_NAME = ?", attributeName);

        if (ExpressionOperation.EQ.toString().equals(operation)) {
            sqlBuilder.where("UA.UM_ATTR_VALUE = ?", attributeValue);
        } else if (ExpressionOperation.EW.toString().equals(operation)) {
            sqlBuilder.where("UA.UM_ATTR_VALUE LIKE ?", "%" + attributeValue);
        } else if (ExpressionOperation.CO.toString().equals(operation)) {
            sqlBuilder.where("UA.UM_ATTR_VALUE LIKE ?", "%" + attributeValue + "%");
        } else if (ExpressionOperation.SW.toString().equals(operation)) {
            sqlBuilder.where("UA.UM_ATTR_VALUE LIKE ?", attributeValue + "%");
        }
    }

    private void multiClaimMySqlQueryBuilder(SqlBuilder sqlBuilder, int claimFilterCount,
                                             ExpressionCondition expressionCondition) {

        if (claimFilterCount == 0) {
            buildClaimWhereConditions(sqlBuilder, expressionCondition.getAttributeName()
                    , expressionCondition.getOperation(), expressionCondition.getAttributeValue());
        } else {
            buildClaimConditionWithOROperator(sqlBuilder, expressionCondition.getAttributeName(),
                    expressionCondition.getOperation(), expressionCondition.getAttributeValue());
        }
    }

    private void buildClaimConditionWithOROperator(SqlBuilder sqlBuilder, String attributeName, String operation,
                                                   String attributeValue) {

        sqlBuilder.updateSqlWithOROperation("UA.UM_ATTR_NAME = ?", attributeName);

        if (ExpressionOperation.EQ.toString().equals(operation)) {
            sqlBuilder.updateSqlWithOROperation("UA.UM_ATTR_VALUE = ?", attributeValue);
        } else if (ExpressionOperation.EW.toString().equals(operation)) {
            sqlBuilder.updateSqlWithOROperation("UA.UM_ATTR_VALUE LIKE ?", "%" + attributeValue);
        } else if (ExpressionOperation.CO.toString().equals(operation)) {
            sqlBuilder.updateSqlWithOROperation("UA.UM_ATTR_VALUE LIKE ?", "%" + attributeValue + "%");
        } else if (ExpressionOperation.SW.toString().equals(operation)) {
            sqlBuilder.updateSqlWithOROperation("UA.UM_ATTR_VALUE LIKE ?", attributeValue + "%");
        }
    }

    private void addingWheres(SqlBuilder baseSqlBuilder, SqlBuilder newSqlBuilder) {

        for (int i = 0; i < baseSqlBuilder.getWheres().size(); i++) {

            if (baseSqlBuilder.getIntegerParameters().containsKey(i + 1)) {
                newSqlBuilder.where(baseSqlBuilder.getWheres().get(i), baseSqlBuilder.getIntegerParameters().get(i + 1));

            } else if (baseSqlBuilder.getStringParameters().containsKey(i + 1)) {
                newSqlBuilder.where(baseSqlBuilder.getWheres().get(i), baseSqlBuilder.getStringParameters().get(i + 1));

            } else if (baseSqlBuilder.getIntegerParameters().containsKey(i + 1)) {
                newSqlBuilder.where(baseSqlBuilder.getWheres().get(i), baseSqlBuilder.getLongParameters().get(i + 1));
            }
        }
    }

    private void getExpressionConditions(Condition condition, List<ExpressionCondition> expressionConditions) {

        if (condition instanceof ExpressionCondition) {
            expressionConditions.add((ExpressionCondition) condition);
        } else if (condition instanceof OperationalCondition) {
            Condition leftCondition = ((OperationalCondition) condition).getLeftCondition();
            getExpressionConditions(leftCondition, expressionConditions);
            Condition rightCondition = ((OperationalCondition) condition).getRightCondition();
            getExpressionConditions(rightCondition, expressionConditions);
        }
    }

    protected int getUserListFromPropertiesCount(String property, String value, String profileName)
            throws UserStoreException {

        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        if (value == null) {
            throw new IllegalArgumentException("Filter value cannot be null");
        }
        if (value.contains(QUERY_FILTER_STRING_ANY)) {
            // This is to support LDAP like queries. Value having only * is restricted except one *.
            if (!value.matches("(\\*)\\1+")) {
                // Convert all the * to % except \*.
                value = value.replaceAll("(?<!\\\\)\\*", SQL_FILTER_STRING_ANY);
            }
        }

        int count = 0;
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        try {
            dbConnection = getDBConnection();
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_PAGINATED_USERS_COUNT_FOR_PROP);
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, property);
            prepStmt.setString(2, value);
            prepStmt.setString(3, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(4, tenantId);
                prepStmt.setInt(5, tenantId);
            }
            rs = prepStmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }

        } catch (SQLException e) {
            String msg = "Database error occurred while paginating users count for a property : " + property + " & " +
                    "value :" + " " + value + "& profile name : " + profileName;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }

        return count;
    }

    public class RoleBreakdown {
        private String[] roles;
        private Integer[] tenantIds;

        private String[] sharedRoles;
        private Integer[] sharedTenantids;

        public String[] getRoles() {
            return roles;
        }

        public void setRoles(String[] roles) {
            this.roles = roles;
        }

        public Integer[] getTenantIds() {
            return tenantIds;
        }

        public void setTenantIds(Integer[] tenantIds) {
            this.tenantIds = tenantIds;
        }

        public String[] getSharedRoles() {
            return sharedRoles;
        }

        public void setSharedRoles(String[] sharedRoles) {
            this.sharedRoles = sharedRoles;
        }

        public Integer[] getSharedTenantids() {
            return sharedTenantids;
        }

        public void setSharedTenantids(Integer[] sharedTenantids) {
            this.sharedTenantids = sharedTenantids;
        }

    }

    private boolean isCaseSensitiveUsername() {
        String isUsernameCaseInsensitiveString = realmConfig.getUserStoreProperty(CASE_INSENSITIVE_USERNAME);
        return !Boolean.parseBoolean(isUsernameCaseInsensitiveString);
    }

    /**
     * Get the SQL statement for ExternalRoles.
     *
     * @param caseSensitiveUsernameQuery    query for getting role with case sensitive username.
     * @param nonCaseSensitiveUsernameQuery query for getting role with non-case sensitive username.
     * @return sql statement.
     * @throws UserStoreException
     */
    private String getExternalRoleListSqlStatement(String caseSensitiveUsernameQuery,
            String nonCaseSensitiveUsernameQuery) throws UserStoreException {
        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = caseSensitiveUsernameQuery;
        } else {
            sqlStmt = nonCaseSensitiveUsernameQuery;
        }
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving user roles is null");
        }
        return sqlStmt;
    }

    /**
     * Get the JCE provider to be used for encryption/decryption
     *
     * @return
     */
    private static String getJceProvider() {
        String provider = System.getProperty(SECURITY_JCE_PROVIDER);
        if (provider != null && (provider.equalsIgnoreCase(BOUNCY_CASTLE_FIPS_PROVIDER) ||
                provider.equalsIgnoreCase(BOUNCY_CASTLE_PROVIDER))) {
            return provider;
        }
        return null;
    }
}
