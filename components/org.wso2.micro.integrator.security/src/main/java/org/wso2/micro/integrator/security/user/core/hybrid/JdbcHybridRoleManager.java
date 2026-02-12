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
package org.wso2.micro.integrator.security.user.core.hybrid;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.core.util.DatabaseCreator;
import org.wso2.micro.core.util.StringUtils;
import org.wso2.micro.integrator.security.user.api.RealmConfiguration;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.core.UserRealm;
import org.wso2.micro.integrator.security.user.core.UserStoreException;
import org.wso2.micro.integrator.security.user.core.authorization.AuthorizationCache;
import org.wso2.micro.integrator.security.user.core.common.UserRolesCache;
import org.wso2.micro.integrator.security.user.core.constants.UserCoreDBConstants;
import org.wso2.micro.integrator.security.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.micro.integrator.security.user.core.jdbc.caseinsensitive.JDBCCaseInsensitiveConstants;
import org.wso2.micro.integrator.security.user.core.util.DatabaseUtil;
import org.wso2.micro.integrator.security.user.core.util.UserCoreUtil;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.SQLTimeoutException;
import java.util.*;

import static org.wso2.micro.integrator.security.SecurityConstants.CASE_INSENSITIVE_USERNAME;
import static org.wso2.micro.integrator.security.user.core.constants.UserCoreErrorConstants.ErrorMessages.ERROR_CODE_DUPLICATE_WHILE_ADDING_A_HYBRID_ROLE;
import static org.wso2.micro.integrator.security.user.core.constants.UserCoreErrorConstants.ErrorMessages.ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE;

public class JdbcHybridRoleManager extends HybridRoleManager {

    private static Log log = LogFactory.getLog(JDBCUserStoreManager.class);
    private final int DEFAULT_MAX_ROLE_LIST_SIZE = 1000;
    private final int DEFAULT_MAX_SEARCH_TIME = 1000;
    private DataSource dataSource;
    private String isCascadeDeleteEnabled;
    private static final String APPLICATION_DOMAIN = "Application";
    private static final String WORKFLOW_DOMAIN = "Workflow";

    public JdbcHybridRoleManager(DataSource dataSource, int tenantId, RealmConfiguration realmConfig,
                                 UserRealm realm) throws UserStoreException {
        super(dataSource, tenantId, realmConfig, realm);
        this.dataSource = dataSource;
        this.isCascadeDeleteEnabled = realmConfig.getRealmProperty(UserCoreDBConstants.CASCADE_DELETE_ENABLED);
        //persist internal domain
        UserCoreUtil.persistDomain(UserCoreConstants.INTERNAL_DOMAIN, tenantId, dataSource);
        UserCoreUtil.persistDomain(APPLICATION_DOMAIN, tenantId, dataSource);
        UserCoreUtil.persistDomain(WORKFLOW_DOMAIN, tenantId, dataSource);

    }

    /**
     * @param roleName Domain-less role
     * @param userList Domain-aware user list
     * @throws UserStoreException
     */
    public void addHybridRole(String roleName, String[] userList) throws UserStoreException {
        UserCoreUtil.logUnsupportedOperation();
        // TODO Revisit and decide whether we need this for MI
        Connection dbConnection = null;
        try {

            // ########### Domain-less Roles and Domain-aware Users from here onwards #############

            // This method is always invoked by the primary user store manager.
            String primaryDomainName = getMyDomainName();

            if (primaryDomainName != null) {
                primaryDomainName = primaryDomainName.toUpperCase();
            }

            dbConnection = DatabaseUtil.getDBConnection(dataSource);

            if (!this.isExistingRole(roleName)) {
                DatabaseUtil.updateDatabase(dbConnection, HybridJDBCConstants.ADD_ROLE_SQL,
                        roleName, tenantId);
                dbConnection.commit();
            } else {
                throw new UserStoreException("Role name: " + roleName
                        + " in the system. Please pick another role name.");
            }
            if (userList != null) {
                String sql = HybridJDBCConstants.ADD_USER_TO_ROLE_SQL;
                String type = DatabaseCreator.getDatabaseType(dbConnection);
                if (UserCoreConstants.MSSQL_TYPE.equals(type)) {
                    sql = HybridJDBCConstants.ADD_USER_TO_ROLE_SQL_MSSQL;
                }
                if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                    sql = HybridJDBCConstants.ADD_USER_TO_ROLE_SQL_OPENEDGE;
                    DatabaseUtil.udpateUserRoleMappingInBatchModeForInternalRoles(dbConnection,
                            sql, primaryDomainName, userList, tenantId, roleName, tenantId);
                } else {
                    DatabaseUtil.udpateUserRoleMappingInBatchModeForInternalRoles(dbConnection,
                            sql, primaryDomainName, userList, roleName, tenantId, tenantId, tenantId);
                }
            }
            dbConnection.commit();
        } catch (SQLException | UserStoreException e) {
            String errorMessage = "Error occurred while adding hybrid role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            if (e instanceof UserStoreException && ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE.getCode().equals(((UserStoreException) e)
                    .getErrorCode())) {
                // Duplicate entry
                throw new UserStoreException(e.getMessage(), ERROR_CODE_DUPLICATE_WHILE_ADDING_A_HYBRID_ROLE.getCode(), e);
            } else {
                // Other SQL Exception
                throw new UserStoreException(e.getMessage(), e);
            }
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
     * @param tenantID
     */
    protected void clearUserRolesCacheByTenant(int tenantID) {
        if (userRolesCache != null) {
            userRolesCache.clearCacheByTenant(tenantID);
            AuthorizationCache authorizationCache = AuthorizationCache.getInstance();
            authorizationCache.clearCacheByTenant(tenantID);
        }
    }

    /**
     * @param roleName
     * @return
     * @throws UserStoreException
     */
    public boolean isExistingRole(String roleName) throws UserStoreException {

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isExisting = false;

        try {

            // ########### Domain-less Roles and Domain-aware Users from here onwards #############

            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            prepStmt = dbConnection.prepareStatement(HybridJDBCConstants.GET_ROLE_ID);
            prepStmt.setString(1, roleName);
            prepStmt.setInt(2, tenantId);
            rs = prepStmt.executeQuery();
            if (rs.next()) {
                int value = rs.getInt(1);
                if (value > -1) {
                    isExisting = true;
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("Is roleName: " + roleName + " Exist: " + isExisting + " TenantId: " + tenantId);
            }
            return isExisting;
        } catch (SQLException e) {
            String errorMessage = "Error occurred while checking is existing role for role name : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    /**
     * @param filter
     * @return
     * @throws UserStoreException
     */
    public String[] getHybridRoles(String filter) throws UserStoreException {

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        String sqlStmt = HybridJDBCConstants.GET_ROLES;
        int maxItemLimit = UserCoreConstants.MAX_USER_ROLE_LIST;
        int searchTime = UserCoreConstants.MAX_SEARCH_TIME;

        try {
            maxItemLimit = Integer.parseInt(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_ROLE_LIST));
        } catch (Exception e) {
            maxItemLimit = DEFAULT_MAX_ROLE_LIST_SIZE;
        }

        try {
            searchTime = Integer.parseInt(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_SEARCH_TIME));
        } catch (Exception e) {
            searchTime = DEFAULT_MAX_SEARCH_TIME;
        }

        try {
            if (filter != null && filter.trim().length() != 0) {
                filter = filter.trim();
                filter = filter.replace("*", "%");
                filter = filter.replace("?", "_");
            } else {
                filter = "%";
            }

            dbConnection = DatabaseUtil.getDBConnection(dataSource);

            if (dbConnection == null) {
                throw new UserStoreException("null connection");
            }

            dbConnection.setAutoCommit(false);
            if (dbConnection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                dbConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
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
            List<String> filteredRoles = new ArrayList<String>();

            try {
                rs = prepStmt.executeQuery();
            } catch (SQLException e) {
                log.error("Error while retrieving roles from Internal JDBC role store", e);
                // May be due time out, therefore ignore this exception
            }

            if (rs != null) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    // Append the domain
                    if (!name.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
                        name = UserCoreConstants.INTERNAL_DOMAIN + UserCoreConstants.DOMAIN_SEPARATOR
                               + name;
                    }
                    filteredRoles.add(name);
                }
            }
            return filteredRoles.toArray(new String[filteredRoles.size()]);
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting hybrid roles from filter : " + filter;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    /**
     * @param roleName
     * @return
     * @throws UserStoreException
     */
    public String[] getUserListOfHybridRole(String roleName) throws UserStoreException {

        if (UserCoreUtil.isEveryoneRole(roleName, realmConfig)) {
            if (Boolean.parseBoolean(System.getProperty(HybridJDBCConstants.NON_USER_CORE_MODE))) {
                // Primary User Store
                return getPrimaryUsers();
            } else {
                return userRealm.getUserStoreManager().listUsers("*", -1);
            }
        }

        // ########### Domain-less Roles and Domain-aware Users from here onwards #############

        String sqlStmt = HybridJDBCConstants.GET_USER_LIST_OF_ROLE_SQL;
        Connection dbConnection = null;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String[] names = DatabaseUtil.getStringValuesFromDatabaseForInternalRoles(dbConnection, sqlStmt,
                    roleName, tenantId, tenantId);
            return names;
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting user list from hybrid role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    private String[] getPrimaryUsers() throws UserStoreException {
        String filter = HybridJDBCConstants.ALL_FILTER;
        List<String> lst = new LinkedList<String>();
        String sqlStmt = HybridJDBCConstants.GET_PRIMARY_USERS;
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String[] users = new String[0];

        try {
            // get DB connection
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            dbConnection.setAutoCommit(false);
            if (dbConnection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                dbConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            }

            // prepare statement
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, filter);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }
            prepStmt.setMaxRows(DEFAULT_MAX_ROLE_LIST_SIZE);
            prepStmt.setQueryTimeout(DEFAULT_MAX_SEARCH_TIME);

            try {
                // execute query
                rs = prepStmt.executeQuery();

            } catch (SQLException e) {
                if (e instanceof SQLTimeoutException) {
                    log.error("The cause might be a time out. Hence ignored", e);
                    return users;
                }
                String errorMessage =
                        "Error while fetching users according to filter : * & max Item limit " +
                                ": " + DEFAULT_MAX_ROLE_LIST_SIZE;
                if (log.isDebugEnabled()) {
                    log.debug(errorMessage, e);
                }
                throw new UserStoreException(errorMessage, e);
            }

            // get name list
            while (rs.next()) {
                String name = rs.getString(1);
                if (UserCoreConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(name)) {
                    continue;
                }
                // append the "PRIMARY" domain
                String domain = "PRIMARY";
                name = UserCoreUtil.addDomainToName(name, domain);
                lst.add(name);
            }

            rs.close();
            if (lst.size() > 0) {
                users = lst.toArray(new String[lst.size()]);
            }
            Arrays.sort(users);

        } catch (SQLException e) {
            String msg = "Error occurred while retrieving users for filter : * & max Item limit : " +
                    DEFAULT_MAX_ROLE_LIST_SIZE;
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new UserStoreException(msg, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return users;
    }

    /**
     * @param roleName
     * @param deletedUsers
     * @param newUsers
     * @throws UserStoreException
     */
    public void updateUserListOfHybridRole(String roleName, String[] deletedUsers, String[] newUsers)
            throws UserStoreException {

        UserCoreUtil.logUnsupportedOperation();
        // TODO Revisit and decide whether we need this for MI
        /*String sqlStmt1 = HybridJDBCConstants.REMOVE_USER_FROM_ROLE_SQL;
        String sqlStmt2 = HybridJDBCConstants.ADD_USER_TO_ROLE_SQL;
        if (!isCaseSensitiveUsername()) {
            sqlStmt1 = HybridJDBCConstants.REMOVE_USER_FROM_ROLE_SQL_CASE_INSENSITIVE;
        }
        Connection dbConnection = null;

        try {

            // ########### Domain-less Roles and Domain-aware Users from here onwards #############
            String primaryDomainName = getMyDomainName();

            if (primaryDomainName != null) {
                primaryDomainName = primaryDomainName.toUpperCase();
            }

            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String type = DatabaseCreator.getDatabaseType(dbConnection);

            if (UserCoreConstants.MSSQL_TYPE.equals(type)) {
                sqlStmt2 = HybridJDBCConstants.ADD_USER_TO_ROLE_SQL_MSSQL;
            }

            if (deletedUsers != null && deletedUsers.length > 0) {
                DatabaseUtil.udpateUserRoleMappingInBatchModeForInternalRoles(
                        dbConnection, sqlStmt1, primaryDomainName, deletedUsers,
                        roleName, tenantId, tenantId, tenantId);
                // authz cache of deleted users from role, needs to be updated
                for (String deletedUser : deletedUsers) {
                    userRealm.getAuthorizationManager().clearUserAuthorization(deletedUser);
                }
            }

            if (newUsers != null && newUsers.length > 0) {
                if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                    sqlStmt2 = HybridJDBCConstants.ADD_USER_TO_ROLE_SQL_OPENEDGE;
                    DatabaseUtil.udpateUserRoleMappingInBatchModeForInternalRoles(dbConnection,
                            sqlStmt2, primaryDomainName, newUsers, tenantId, roleName, tenantId);
                } else {
                    DatabaseUtil.udpateUserRoleMappingInBatchModeForInternalRoles(dbConnection,
                            sqlStmt2, primaryDomainName, newUsers, roleName, tenantId, tenantId, tenantId);
                }
            }

            dbConnection.commit();
        } catch (SQLException | UserStoreException e) {
            String errorMessage = "Error occurred while updating user list of hybrid role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage = "Error occurred while getting database type from DB connection";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }*/
    }

    /**
     * @param userName
     * @return
     * @throws UserStoreException
     */
    public String[] getHybridRoleListOfUser(String userName, String filter) throws UserStoreException {

        String sqlStmt;
        Connection dbConnection = null;
        String[] roles;
        userName = UserCoreUtil.addDomainToName(userName, getMyDomainName());
        String domain = UserCoreUtil.extractDomainFromName(userName);
        // ########### Domain-less Roles and Domain-aware Users from here onwards #############
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);

            if (domain != null) {
                domain = domain.toUpperCase();
            }
            if (filter.equals("*") || StringUtils.isEmpty(filter)) {
                sqlStmt = getHybridRoleListSqlStatement(
                        realmConfig.getRealmProperty(HybridJDBCConstants.GET_ROLE_LIST_OF_USER),
                        HybridJDBCConstants.GET_ROLE_LIST_OF_USER_SQL,
                        JDBCCaseInsensitiveConstants.GET_ROLE_LIST_OF_USER_SQL_CASE_INSENSITIVE);
                roles = DatabaseUtil
                        .getStringValuesFromDatabase(dbConnection, sqlStmt, UserCoreUtil.removeDomainFromName(userName),
                                tenantId, tenantId, tenantId, domain);
            } else if (filter.contains("*") || filter.contains("?")) {
                filter = filter.trim();
                filter = filter.replace("*", "%");
                filter = filter.replace("?", "_");
                sqlStmt = getHybridRoleListSqlStatement(
                        realmConfig.getRealmProperty(HybridJDBCConstants.GET_IS_ROLE_EXIST_LIST_OF_USER),
                        HybridJDBCConstants.GET_ROLE_OF_USER_SQL,
                        JDBCCaseInsensitiveConstants.GET_IS_USER_ROLE_SQL_CASE_INSENSITIVE);

                // If the filter contains the internal domain, then here we remove the internal domain from the filter
                // as the database only has the role name without the internal domain.
                if (filter.toLowerCase().contains(UserCoreConstants.INTERNAL_DOMAIN.toLowerCase())) {
                    int index;
                    if ((index = filter.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) >= 0) {
                        filter = filter.substring(index + 1);
                    }
                }
                roles = DatabaseUtil
                        .getStringValuesFromDatabase(dbConnection, sqlStmt, UserCoreUtil.removeDomainFromName(userName),
                                tenantId, tenantId, tenantId, domain, filter);
            } else {
                sqlStmt = getHybridRoleListSqlStatement(
                        realmConfig.getRealmProperty(HybridJDBCConstants.GET_IS_ROLE_EXIST_LIST_OF_USER),
                        HybridJDBCConstants.GET_USER_ROLE_NAME_SQL,
                        JDBCCaseInsensitiveConstants.GET_IS_USER_ROLE_SQL_CASE_INSENSITIVE);

                if (filter.toLowerCase().contains(UserCoreConstants.INTERNAL_DOMAIN.toLowerCase())) {
                    int index;
                    if ((index = filter.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) >= 0) {
                        filter = filter.substring(index + 1);
                    }
                }
                roles = DatabaseUtil
                        .getStringValuesFromDatabase(dbConnection, sqlStmt, UserCoreUtil.removeDomainFromName(userName),
                                tenantId, tenantId, tenantId, domain, filter);
            }

            if (!UserCoreConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
                // Adding everyone role
                if (roles == null || roles.length == 0) {
                    return new String[]{realmConfig.getEveryOneRoleName()};
                }
                List<String> allRoles = new ArrayList<String>();
                boolean isEveryone = false;
                for (String role : roles) {
                    if (!role.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
                        role = UserCoreConstants.INTERNAL_DOMAIN + UserCoreConstants.DOMAIN_SEPARATOR + role;
                    }
                    if (role.equals(realmConfig.getEveryOneRoleName())) {
                        isEveryone = true;
                    }
                    allRoles.add(role);
                }
                if (!isEveryone) {
                    allRoles.add(realmConfig.getEveryOneRoleName());
                }
                return allRoles.toArray(new String[allRoles.size()]);
            } else {
                return roles;
            }
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting hybrid role list of user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     * Get hybrid role list of users
     *
     * @param userNames user name list
     * @return map of hybrid role list of users
     * @throws UserStoreException userStoreException
     */
    public Map<String, List<String>> getHybridRoleListOfUsers(List<String> userNames, String domainName) throws
            UserStoreException {

        Map<String, List<String>> hybridRoleListOfUsers = new HashMap<>();
        String sqlStmt = realmConfig.getRealmProperty(HybridJDBCConstants.GET_ROLE_LIST_OF_USERS);
        StringBuilder usernameParameter = new StringBuilder();
        if (isCaseSensitiveUsername()) {
            if (StringUtils.isEmpty(sqlStmt)) {
                sqlStmt = HybridJDBCConstants.GET_INTERNAL_ROLE_LIST_OF_USERS_SQL;
            }
            for (int i = 0; i < userNames.size(); i++) {

                usernameParameter.append("'").append(userNames.get(i)).append("'");

                if (i != userNames.size() - 1) {
                    usernameParameter.append(",");
                }
            }
        } else {
            if (sqlStmt == null) {
                sqlStmt = JDBCCaseInsensitiveConstants.GET_INTERNAL_ROLE_LIST_OF_USERS_SQL_CASE_INSENSITIVE;
            }
            for (int i = 0; i < userNames.size(); i++) {

                usernameParameter.append("LOWER('").append(userNames.get(i)).append("')");

                if (i != userNames.size() - 1) {
                    usernameParameter.append(",");
                }
            }
        }

        sqlStmt = sqlStmt.replaceFirst("\\?", usernameParameter.toString());
        try (Connection connection = DatabaseUtil.getDBConnection(dataSource);
                PreparedStatement prepStmt = connection.prepareStatement(sqlStmt)) {
            prepStmt.setInt(1, tenantId);
            prepStmt.setInt(2, tenantId);
            prepStmt.setInt(3, tenantId);
            prepStmt.setString(4, domainName);
            try (ResultSet resultSet = prepStmt.executeQuery()) {
                while (resultSet.next()) {
                    String userName = resultSet.getString(1);
                    if (!userNames.contains(userName)) {
                        continue;
                    }

                    String roleName = resultSet.getString(2);
                    List<String> userRoles = hybridRoleListOfUsers.get(userName);
                    if (userRoles == null) {
                        userRoles = new ArrayList<>();
                        hybridRoleListOfUsers.put(userName, userRoles);
                    }

                    if (!roleName.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
                        roleName = UserCoreConstants.INTERNAL_DOMAIN + UserCoreConstants.DOMAIN_SEPARATOR + roleName;
                    }
                    userRoles.add(roleName);
                }
            }

            for (String userName : userNames) {
                List<String> hybridRoles = hybridRoleListOfUsers.get(userName);
                if (hybridRoles == null) {
                    hybridRoles = new ArrayList<>();
                    hybridRoleListOfUsers.put(userName, hybridRoles);
                }
                if (!hybridRoles.contains(realmConfig.getEveryOneRoleName())
                        && !UserCoreConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
                    hybridRoles.add(realmConfig.getEveryOneRoleName());
                }
            }
        } catch (SQLException e) {
            String errorMessage =
                    "Error occurred while getting hybrid role list of users : " + Arrays.toString(userNames.toArray())
                            + " in domain: " + domainName;
            throw new UserStoreException(errorMessage, e);
        }

        return hybridRoleListOfUsers;
    }

    /**
     * @param user
     * @param deletedRoles
     * @param addRoles
     * @throws UserStoreException
     */
    public void updateHybridRoleListOfUser(String user, String[] deletedRoles, String[] addRoles)
            throws UserStoreException {
        UserCoreUtil.logUnsupportedOperation();
        // TODO Revisit and decide whether we need this for MI
        String sqlStmt1 = HybridJDBCConstants.REMOVE_ROLE_FROM_USER_SQL;
        String sqlStmt2 = HybridJDBCConstants.ADD_ROLE_TO_USER_SQL;
        if(!isCaseSensitiveUsername()){
            sqlStmt1 = HybridJDBCConstants.REMOVE_ROLE_FROM_USER_SQL_CASE_INSENSITIVE;
        }
        Connection dbConnection = null;

        try {

            user = UserCoreUtil.addDomainToName(user, getMyDomainName());
            String domain = UserCoreUtil.extractDomainFromName(user);
            // ########### Domain-less Roles and Domain-aware Users from here onwards #############

            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String type = DatabaseCreator.getDatabaseType(dbConnection);
            if (UserCoreConstants.MSSQL_TYPE.equals(type)) {
                sqlStmt2 = HybridJDBCConstants.ADD_ROLE_TO_USER_SQL_MSSQL;
            }

            if (domain != null) {
                domain = domain.toUpperCase();
            }

            if (deletedRoles != null && deletedRoles.length > 0) {
                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1, deletedRoles,
                        tenantId, UserCoreUtil.removeDomainFromName(user), tenantId, tenantId, domain);
            }
            if (addRoles != null && addRoles.length > 0) {
                ArrayList<String> newRoleList = new ArrayList<>();
                for (String role : addRoles) {
                    if(!isExistingRole(role)){
                        String errorMessage = "The role: " + role + " does not exist.";
                        throw new UserStoreException(errorMessage);
                    }
                    if (!isUserInRole(user, role)) {
                        newRoleList.add(role);
                    }
                }

                String[] rolesToAdd = newRoleList.toArray(new String[newRoleList.size()]);

                if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                    sqlStmt2 = HybridJDBCConstants.ADD_ROLE_TO_USER_SQL_OPENEDGE;
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, user,
                            tenantId, rolesToAdd, tenantId);
                } else {
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, rolesToAdd,
                            tenantId, UserCoreUtil.removeDomainFromName(user), tenantId, tenantId, domain);
                }
            }
            dbConnection.commit();
        } catch (SQLException | UserStoreException e) {
            String errorMessage = "Error occurred while updating hybrid role list of user : " + user;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage = "Error occurred while getting database type from DB connection";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        // Authorization cache of user should also be updated if deleted roles are involved
//        if (deletedRoles != null && deletedRoles.length > 0) {
//            userRealm.getAuthorizationManager().clearUserAuthorization(user);
//        }
    }

    /**
     * @param roleName
     * @throws UserStoreException
     */
    public void deleteHybridRole(String roleName) throws UserStoreException {

        // ########### Domain-less Roles and Domain-aware Users from here onwards #############

        if (UserCoreUtil.isEveryoneRole(roleName, realmConfig)) {
            throw new UserStoreException("Invalid operation");
        }

        Connection dbConnection = null;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            if(isCascadeDeleteEnabled == null || !Boolean.parseBoolean(isCascadeDeleteEnabled)) {
                DatabaseUtil.updateDatabase(dbConnection,
                        HybridJDBCConstants.ON_DELETE_ROLE_REMOVE_USER_ROLE_SQL, roleName, tenantId, tenantId);
            }
            DatabaseUtil.updateDatabase(dbConnection, HybridJDBCConstants.DELETE_ROLE_SQL,
                    roleName, tenantId);
            dbConnection.commit();
        } catch (SQLException e) {
            String errorMessage = "Error occurred while deleting hybrid role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }

        // UM_ROLE_PERMISSION Table, roles are associated with Domain ID.
        // At this moment Role name doesn't contain the Domain prefix.
        // clearRoleAuthorization() expects domain qualified name.
        // Hence we add the "Internal" Domain name explicitly here.
        if (!roleName.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
            roleName = UserCoreUtil.addDomainToName(roleName, UserCoreConstants.INTERNAL_DOMAIN);
        }
        // also need to clear role authorization
        // userRealm.getAuthorizationManager().clearRoleAuthorization(roleName);
    }

    /**
     * @param roleName
     * @param newRoleName
     * @throws UserStoreException
     */
    public void updateHybridRoleName(String roleName, String newRoleName) throws UserStoreException {

        // ########### Domain-less Roles and Domain-aware Users from here onwards #############

        if (this.isExistingRole(newRoleName)) {
            throw new UserStoreException("Role name: " + newRoleName
                    + " in the system. Please pick another role name.");
        }

        String sqlStmt = HybridJDBCConstants.UPDATE_ROLE_NAME_SQL;
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for update hybrid role name is null");
        }

        Connection dbConnection = null;
        try {

            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                DatabaseUtil.updateDatabase(dbConnection, sqlStmt, newRoleName, roleName, tenantId);
            } else {
                DatabaseUtil.updateDatabase(dbConnection, sqlStmt, newRoleName, roleName);
            }
            dbConnection.commit();
            this.userRealm.getAuthorizationManager().resetPermissionOnUpdateRole(roleName,
                    newRoleName);
        } catch (SQLException e) {
            String errorMessage =
                    "Error occurred while updating hybrid role : " + roleName + " to new role : " + newRoleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    /**
     * ##### This method is not used anywhere
     *
     * @param userName
     * @param roleName
     * @return
     * @throws UserStoreException
     */
    public boolean isUserInRole(String userName, String roleName) throws UserStoreException {
        // TODO
        String[] roles = getHybridRoleListOfUser(userName, "*");
        if (roles != null && roleName != null) {
            for (String role : roles) {
                if (roleName.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
                    if (role.equalsIgnoreCase(roleName)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Role: " + roleName + " is already assigned to the user: " + userName);
                        }
                        return true;
                    }
                } else {
                    if (UserCoreUtil.removeDomainFromName(role).equalsIgnoreCase(roleName)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Role: " + roleName + " is already assigned to the user: " + userName);
                        }
                        return true;
                    }
                }
            }
        }

        return false;
//		if (UserCoreUtil.isEveryoneRole(roleName, realmConfig)) {
//			return true;
//		}
//
//		userName = UserCoreUtil.addDomainToName(userName, getMyDomainName());
//        String domain = UserCoreUtil.extractDomainFromName(userName);
//		// ########### Domain-less Roles and Domain-aware Users from here onwards #############
//
//		Connection dbConnection = null;
//		PreparedStatement prepStmt = null;
//		ResultSet rs = null;
//		boolean isUserInRole = false;
//		try {
//			dbConnection = DatabaseUtil.getDBConnection(dataSource);
//			prepStmt = dbConnection.prepareStatement(HybridJDBCConstants.IS_USER_IN_ROLE_SQL);
//			prepStmt.setString(1, userName);
//			prepStmt.setString(2, roleName);
//			prepStmt.setInt(3, tenantId);
//            prepStmt.setInt(4, tenantId);
//            prepStmt.setInt(5, tenantId);
//			prepStmt.setString(6, domain);
//			rs = prepStmt.executeQuery();
//			if (rs.next()) {
//				int value = rs.getInt(1);
//				if (value != -1) {
//					isUserInRole = true;
//				}
//			}
//			dbConnection.commit();
//		} catch (SQLException e) {
//			throw new UserStoreException(e.getMessage(), e);
//		} finally {
//			DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
//		}
//		return isUserInRole;
    }

    /**
     * If a user is added to a hybrid role, that entry should be deleted upon deletion of the user.
     *
     * @param userName
     * @throws UserStoreException
     */
    public void deleteUser(String userName) throws UserStoreException {

        Connection dbConnection = null;
        PreparedStatement preparedStatement = null;

        userName = UserCoreUtil.addDomainToName(userName, getMyDomainName());
        String domain = UserCoreUtil.extractDomainFromName(userName);
        // ########### Domain-less Roles and Domain-aware Users from here onwards #############

        if (domain != null) {
            domain = domain.toUpperCase();
        }

        String sqlStmt = HybridJDBCConstants.REMOVE_USER_SQL;
        if (!isCaseSensitiveUsername()) {
            sqlStmt = HybridJDBCConstants.REMOVE_USER_SQL_CASE_INSENSITIVE;
        }

        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            preparedStatement = dbConnection.prepareStatement(sqlStmt);
            preparedStatement.setString(1, UserCoreUtil.removeDomainFromName(userName));
            preparedStatement.setInt(2, tenantId);
            preparedStatement.setInt(3, tenantId);
            preparedStatement.setString(4, domain);
            preparedStatement.execute();
            dbConnection.commit();
        } catch (SQLException e) {
            String errorMessage = "Error occurred while deleting user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, preparedStatement);
        }
    }

    /**
     *
     */
    protected void initUserRolesCache() {

        String userRolesCacheEnabledString = (realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_ROLES_CACHE_ENABLED));

        if (userRolesCacheEnabledString != null && !userRolesCacheEnabledString.equals("")) {
            userRolesCacheEnabled = Boolean.parseBoolean(userRolesCacheEnabledString);
            if (log.isDebugEnabled()) {
                log.debug("User Roles Cache is configured to:" + userRolesCacheEnabledString);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.info("User Roles Cache is not configured. Default value: "
                        + userRolesCacheEnabled + " is taken.");
            }
        }

        if (userRolesCacheEnabled) {
            int timeOut = UserCoreConstants.USER_ROLE_CACHE_DEFAULT_TIME_OUT;
            String timeOutString = realmConfig.
                    getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USER_ROLE_CACHE_TIME_OUT);
            if (timeOutString != null) {
                timeOut = Integer.parseInt(timeOutString);
            }
            userRolesCache = UserRolesCache.getInstance();
            userRolesCache.setTimeOut(timeOut);
        }
    }

    /**
     * @return
     */
    protected String getMyDomainName() {
        return UserCoreUtil.getDomainName(realmConfig);
    }

    private boolean isCaseSensitiveUsername() throws UserStoreException{

        String isUsernameCaseInsensitiveString = realmConfig.getUserStoreProperty(CASE_INSENSITIVE_USERNAME);
        return !Boolean.parseBoolean(isUsernameCaseInsensitiveString);
    }

    /**
     * Get the SQL statement for HybridRole.
     *
     * @param getRoleListOfUserSQLConfig    query for getting role set from resource property.
     * @param caseSensitiveUsernameQuery    query for getting role with case sensitive username.
     * @param nonCaseSensitiveUsernameQuery query for getting role with non-case sensitive username.
     * @return sql statement.
     * @throws UserStoreException
     */
    private String getHybridRoleListSqlStatement(String getRoleListOfUserSQLConfig, String caseSensitiveUsernameQuery,
            String nonCaseSensitiveUsernameQuery) throws UserStoreException {

        String sqlStmt;
        if (isCaseSensitiveUsername()) {
            sqlStmt = caseSensitiveUsernameQuery;
        } else {
            sqlStmt = nonCaseSensitiveUsernameQuery;
        }
        if (!StringUtils.isEmpty(getRoleListOfUserSQLConfig)) {
            sqlStmt = getRoleListOfUserSQLConfig;
        }
        return sqlStmt;
    }
}
