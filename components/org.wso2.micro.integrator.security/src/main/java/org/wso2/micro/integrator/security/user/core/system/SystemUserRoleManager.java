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
package org.wso2.micro.integrator.security.user.core.system;

import org.apache.axis2.util.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.security.UnsupportedSecretTypeException;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.core.UserStoreException;
import org.wso2.micro.integrator.security.user.core.util.DatabaseUtil;
import org.wso2.micro.integrator.security.user.core.util.UserCoreUtil;
import org.wso2.micro.integrator.security.util.Secret;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import javax.sql.DataSource;

import static org.wso2.micro.integrator.security.user.core.constants.UserCoreErrorConstants.ErrorMessages.ERROR_CODE_DUPLICATE_WHILE_ADDING_A_SYSTEM_ROLE;
import static org.wso2.micro.integrator.security.user.core.constants.UserCoreErrorConstants.ErrorMessages.ERROR_CODE_DUPLICATE_WHILE_ADDING_A_SYSTEM_USER;
import static org.wso2.micro.integrator.security.user.core.constants.UserCoreErrorConstants.ErrorMessages.ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE;

public class SystemUserRoleManager {

    private static Log log = LogFactory.getLog(SystemUserRoleManager.class);
    int tenantId;
    private DataSource dataSource;
    private static final String SHA_1_PRNG = "SHA1PRNG";
    private static final String DRBG = "DRBG";
    private static final String JCE_PROVIDER = "security.jce.provider";

    public SystemUserRoleManager(DataSource dataSource, int tenantId) throws UserStoreException {
        super();
        this.dataSource = dataSource;
        this.tenantId = tenantId;
        //persist system domain
        UserCoreUtil.persistDomain(UserCoreConstants.SYSTEM_DOMAIN_NAME, this.tenantId,
                this.dataSource);
    }

    public void addSystemRole(String roleName, String[] userList) throws UserStoreException {

        UserCoreUtil.logUnsupportedOperation();
        // TODO Revisit and decide whether we need this for MI
        /*Connection dbConnection = null;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            if (!this.isExistingRole(roleName)) {
                DatabaseUtil.updateDatabase(dbConnection, SystemJDBCConstants.ADD_ROLE_SQL,
                        roleName, tenantId);
            }
            if (userList != null) {
                String sql = SystemJDBCConstants.ADD_USER_TO_ROLE_SQL;
                String type = DatabaseCreator.getDatabaseType(dbConnection);
                if (UserCoreConstants.MSSQL_TYPE.equals(type)) {
                    sql = SystemJDBCConstants.ADD_USER_TO_ROLE_SQL_MSSQL;
                }
                if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                    sql = SystemJDBCConstants.ADD_USER_TO_ROLE_SQL_OPENEDGE;
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sql, userList,
                            tenantId, roleName, tenantId);
                } else {
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sql, userList,
                            roleName, tenantId, tenantId);
                }
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String errorMessage = "Error occurred while adding system role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            if (e instanceof SQLIntegrityConstraintViolationException) {
                // Duplicate entry
                throw new UserStoreException(e.getMessage(), ERROR_CODE_DUPLICATE_WHILE_ADDING_A_SYSTEM_ROLE.getCode(), e);
            } else {
                // Other SQL Exception
                throw new UserStoreException(e.getMessage(), e);
            }
        } catch (Exception e) {
            String errorMessage = "Error occurred while getting database type from DB connection";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            if (e instanceof UserStoreException && ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE.getCode().equals(((UserStoreException) e)
                    .getErrorCode())) {
                // Duplicate entry
                throw new UserStoreException(e.getMessage(), ERROR_CODE_DUPLICATE_WHILE_ADDING_A_SYSTEM_ROLE.getCode(), e);
            } else {
                // Other SQL Exception
                throw new UserStoreException(e.getMessage(), e);
            }
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }*/
    }

    public boolean isExistingRole(String roleName) throws UserStoreException {

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isExisting = false;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            prepStmt = dbConnection.prepareStatement(SystemJDBCConstants.GET_ROLE_ID);
            prepStmt.setString(1, roleName);
            prepStmt.setInt(2, tenantId);
            rs = prepStmt.executeQuery();
            if (rs.next()) {
                int value = rs.getInt(1);
                if (value > -1) {
                    isExisting = true;
                }
            }
            return isExisting;
        } catch (SQLException e) {
            String errorMessage = "Error occurred while checking is existing role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    public String[] getSystemRoles() throws UserStoreException {
        String sqlStmt = SystemJDBCConstants.GET_ROLES;
        Connection dbConnection = null;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String[] roles = DatabaseUtil.getStringValuesFromDatabase(dbConnection, sqlStmt,
                    tenantId);
            return UserCoreUtil.addDomainToNames(roles, UserCoreConstants.SYSTEM_DOMAIN_NAME);
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting system roles";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public String[] getUserListOfSystemRole(String roleName) throws UserStoreException {

        String sqlStmt = SystemJDBCConstants.GET_USER_LIST_OF_ROLE_SQL;
        Connection dbConnection = null;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String[] users = DatabaseUtil.getStringValuesFromDatabase(dbConnection, sqlStmt,
                    roleName, tenantId, tenantId);
            return UserCoreUtil.addDomainToNames(users, UserCoreConstants.SYSTEM_DOMAIN_NAME);
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting user list of system role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void updateUserListOfSystemRole(String roleName, String[] deletedUsers, String[] newUsers)
            throws UserStoreException {

        UserCoreUtil.logUnsupportedOperation();
        // TODO Revisit and decide whether we need this for MI
        /*String sqlStmt1 = SystemJDBCConstants.REMOVE_USER_FROM_ROLE_SQL;
        String sqlStmt2 = SystemJDBCConstants.ADD_USER_TO_ROLE_SQL;
        Connection dbConnection = null;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String type = DatabaseCreator.getDatabaseType(dbConnection);
            if (UserCoreConstants.MSSQL_TYPE.equals(type)) {
                sqlStmt2 = SystemJDBCConstants.ADD_USER_TO_ROLE_SQL_MSSQL;
            }
            if (deletedUsers != null && deletedUsers.length > 0) {
                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1, deletedUsers,
                        roleName, tenantId, tenantId);
            }
            if (newUsers != null && newUsers.length > 0) {
                if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                    sqlStmt2 = SystemJDBCConstants.ADD_USER_TO_ROLE_SQL_OPENEDGE;
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, newUsers,
                            tenantId, roleName, tenantId);
                } else {
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, newUsers,
                            roleName, tenantId, tenantId);
                }
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String errorMessage = "Error occurred while updating user list of system role : " + roleName;
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

    public String[] getSystemRoleListOfUser(String userName) throws UserStoreException {
        String sqlStmt = SystemJDBCConstants.GET_ROLE_LIST_OF_USER_SQL;
        Connection dbConnection = null;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String[] roles = DatabaseUtil.getStringValuesFromDatabase(dbConnection, sqlStmt,
                    userName, tenantId, tenantId);
            return UserCoreUtil.addDomainToNames(roles, UserCoreConstants.SYSTEM_DOMAIN_NAME);
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting system role list of user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void updateSystemRoleListOfUser(String user, String[] deletedRoles, String[] addRoles)
            throws UserStoreException {
        UserCoreUtil.logUnsupportedOperation();
        // TODO Revisit and decide whether we need this for MI

        /*String sqlStmt1 = SystemJDBCConstants.REMOVE_ROLE_FROM_USER_SQL;
        String sqlStmt2 = SystemJDBCConstants.ADD_ROLE_TO_USER_SQL;
        Connection dbConnection = null;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String type = DatabaseCreator.getDatabaseType(dbConnection);
            if (UserCoreConstants.MSSQL_TYPE.equals(type)) {
                sqlStmt2 = SystemJDBCConstants.ADD_ROLE_TO_USER_SQL_MSSQL;
            }
            if (deletedRoles != null && deletedRoles.length > 0) {
                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1, deletedRoles,
                        tenantId, user, tenantId);
            }
            if (addRoles != null && addRoles.length > 0) {
                if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                    sqlStmt2 = SystemJDBCConstants.ADD_ROLE_TO_USER_SQL_OPENEDGE;
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, user,
                            tenantId, addRoles, tenantId);
                } else {
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, addRoles,
                            tenantId, user, tenantId);
                }
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting system role list of user : " + user;
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

    public boolean isUserInRole(String userName, String roleName) throws UserStoreException {

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isUserInRole = false;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            prepStmt = dbConnection.prepareStatement(SystemJDBCConstants.IS_USER_IN_ROLE_SQL);
            prepStmt.setString(1, userName);
            prepStmt.setString(2, roleName);
            prepStmt.setInt(3, tenantId);
            prepStmt.setInt(4, tenantId);
            rs = prepStmt.executeQuery();
            if (rs.next()) {
                int value = rs.getInt(1);
                if (value != -1) {
                    isUserInRole = true;
                }
            }
            dbConnection.commit();
        } catch (SQLException e) {
            String errorMessage = "Error occurred while checking is user : " + userName + " & in role : " + roleName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return isUserInRole;
    }

    public boolean isExistingSystemUser(String userName) throws UserStoreException {

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isExisting = false;
        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            prepStmt = dbConnection.prepareStatement(SystemJDBCConstants.GET_USER_ID_SQL);
            prepStmt.setString(1, userName);
            prepStmt.setInt(2, tenantId);
            rs = prepStmt.executeQuery();
            if (rs.next()) {
                int value = rs.getInt(1);
                if (value > -1) {
                    isExisting = true;
                }
            }
            return isExisting;
        } catch (SQLException e) {
            String errorMessage = "Error occurred while checking is existing system user : " + userName;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    public void addSystemUser(String userName, Object credential,
                              String[] roleList) throws UserStoreException {

        Connection dbConnection = null;
        Secret credentialObj = null;
        try {
            credentialObj = Secret.getSecret(credential);
        } catch (UnsupportedSecretTypeException e) {
            throw new UserStoreException("Unsupported credential type", e);
        }

        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            String sqlStmt1 = SystemJDBCConstants.ADD_USER_SQL;

            String saltValue = null;
            try {
                SecureRandom secureRandom = SecureRandom.getInstance(getAlgorithm());
                byte[] bytes = new byte[16];
                //secureRandom is automatically seeded by calling nextBytes
                secureRandom.nextBytes(bytes);
                saltValue = Base64.encode(bytes);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA1PRNG algorithm could not be found.");
            }

            String password = this.preparePassword(credentialObj, saltValue);

            this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName, password,
                    saltValue, false, new Date(), tenantId);

            // add user to role.
            updateSystemRoleListOfUser(userName, null, roleList);

            dbConnection.commit();
        } catch (Throwable e) {
            try {
                if (dbConnection != null) {
                    dbConnection.rollback();
                }
            } catch (SQLException e1) {
                log.error("Error while rollbacking add system user operation", e1);
            }
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage(), e);
            }
            if (e instanceof UserStoreException && ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE.getCode().equals(((UserStoreException) e)
                    .getErrorCode())) {
                // Duplicate entry
                throw new UserStoreException(e.getMessage(), ERROR_CODE_DUPLICATE_WHILE_ADDING_A_SYSTEM_USER.getCode(), e);
            } else {
                // Other SQL Exception
                throw new UserStoreException(e.getMessage(), e);
            }
        } finally {
            credentialObj.clear();
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public String[] getSystemUsers() throws UserStoreException {

        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String filter = "*";
        int maxItemLimit = 100;

        String[] systemsUsers = new String[0];
        try {

            if (filter != null && filter.trim().length() != 0) {
                filter = filter.trim();
                filter = filter.replace("*", "%");
                filter = filter.replace("?", "_");
            } else {
                filter = "%";
            }

            List<String> lst = new LinkedList<String>();

            dbConnection = DatabaseUtil.getDBConnection(dataSource);

            if (dbConnection == null) {
                throw new UserStoreException("null connection");
            }
            dbConnection.setAutoCommit(false);
            dbConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            sqlStmt = SystemJDBCConstants.GET_SYSTEM_USER_FILTER_SQL;

            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, filter);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }

            rs = prepStmt.executeQuery();

            int i = 0;
            while (rs.next()) {
                if (i < maxItemLimit) {
                    String name = rs.getString(1);
                    lst.add(name);
                } else {
                    break;
                }
                i++;
            }
            rs.close();

            if (lst.size() > 0) {
                systemsUsers = lst.toArray(new String[lst.size()]);
            }
            Arrays.sort(systemsUsers);
            systemsUsers = UserCoreUtil.addDomainToNames(systemsUsers, UserCoreConstants.SYSTEM_DOMAIN_NAME);
        } catch (SQLException e) {
            String errorMessage = "Error occurred while getting system users";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return systemsUsers;

    }

//    public void deleteUser(String userName) throws UserStoreException {
//
//        Connection dbConnection = null;
//        PreparedStatement preparedStatement = null;
//
//        try {
//            dbConnection = DatabaseUtil.getDBConnection(dataSource);
//            preparedStatement = dbConnection.prepareStatement(SystemJDBCConstants.REMOVE_USER_SQL);
//            preparedStatement.setString(1, userName);
//            preparedStatement.execute();
//            //needs to clear authz cache of user
//            this.userRealm.getAuthorizationManager().clearUserAuthorization(userName);
//            dbConnection.commit();
//        } catch (SQLException e) {
//            log.error(e.getMessage(), e);
//            throw new UserStoreException(e.getMessage(), e);
//        } finally {
//            DatabaseUtil.closeAllConnections(dbConnection, preparedStatement);
//        }
//    }

//    public void deleteSystemRole(String roleName) throws UserStoreException {
//
//        Connection dbConnection = null;
//        try {
//            dbConnection = getDBConnection();
//            DatabaseUtil.updateDatabase(dbConnection,
//                    SystemJDBCConstants.ON_DELETE_ROLE_REMOVE_USER_ROLE_SQL, roleName, tenantId,
//                    tenantId);
//            DatabaseUtil.updateDatabase(dbConnection, SystemJDBCConstants.DELETE_ROLE_SQL,
//                    roleName, tenantId);
//            dbConnection.commit();
//        } catch (SQLException e) {
//            log.error(e.getMessage(), e);
//            throw new UserStoreException(e.getMessage(), e);
//        } finally {
//            DatabaseUtil.closeAllConnections(dbConnection);
//        }
//        //also need to clear role authorization
//        userRealm.getAuthorizationManager().clearRoleAuthorization(roleName);
//    }
//
//    public void updateSystemRoleName(String roleName, String newRoleName) throws UserStoreException {
//
//        String sqlStmt = SystemJDBCConstants.UPDATE_ROLE_NAME_SQL;
//        Connection dbConnection = null;
//        try {
//            dbConnection = DatabaseUtil.getDBConnection(dataSource);
//            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
//                DatabaseUtil.updateDatabase(dbConnection, sqlStmt, newRoleName, roleName, tenantId);
//            } else {
//                DatabaseUtil.updateDatabase(dbConnection, sqlStmt, newRoleName, roleName);
//            }
//            dbConnection.commit();
//            this.userRealm.getAuthorizationManager().resetPermissionOnUpdateRole(roleName,
//                    newRoleName);
//        } catch (SQLException e) {
//            log.error(e.getMessage(), e);
//            log.error("Using sql : " + sqlStmt);
//            throw new UserStoreException(e.getMessage(), e);
//        } finally {
//            DatabaseUtil.closeAllConnections(dbConnection);
//        }
//    }

    private String preparePassword(Secret password, String saltValue) throws UserStoreException {
        try {
            if (saltValue != null) {
                password.addChars(saltValue.toCharArray());
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteValue = digest.digest(password.getBytes());
            return Base64.encode(byteValue);
        } catch (NoSuchAlgorithmException e) {
            String errorMessage = "Error occurred while preparing password";
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        }
    }

    private void updateStringValuesToDatabase(Connection dbConnection, String sqlStmt,
                                              Object... params) throws UserStoreException {
        PreparedStatement prepStmt = null;
        boolean localConnection = false;
        try {
            if (dbConnection == null) {
                localConnection = true;
                dbConnection = DatabaseUtil.getDBConnection(dataSource);
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
                        //Timestamp timestamp = new Timestamp(((Date) param).getTime());
                        //prepStmt.setTimestamp(i + 1, timestamp);
                        prepStmt.setTimestamp(i + 1, new Timestamp(System.currentTimeMillis()));
                    } else if (param instanceof Boolean) {
                        prepStmt.setBoolean(i + 1, (Boolean) param);
                    }
                }
            }
            int count = prepStmt.executeUpdate();
            if (count == 0) {
                log.info("No rows were updated");
            }
            if (log.isDebugEnabled()) {
                log.debug("Executed querry is " + sqlStmt + " and number of updated rows :: "
                        + count);
            }

            if (localConnection) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage(), e);
            }

            if (e instanceof SQLIntegrityConstraintViolationException) {
                // Duplicate entry
                throw new UserStoreException(e.getMessage(), ERROR_CODE_DUPLICATE_WHILE_WRITING_TO_DATABASE.getCode(), e);
            } else {
                // Other SQL Exception
                throw new UserStoreException(e.getMessage(), e);
            }
        } finally {
            if (localConnection) {
                DatabaseUtil.closeAllConnections(dbConnection);
            }
            DatabaseUtil.closeAllConnections(null, prepStmt);
        }
    }

    private static String getAlgorithm() {
        String provider = System.getProperty(JCE_PROVIDER);
        if (org.apache.commons.lang.StringUtils.isNotEmpty(provider)) {
            return DRBG;
        }
        return SHA_1_PRNG;
    }
}
