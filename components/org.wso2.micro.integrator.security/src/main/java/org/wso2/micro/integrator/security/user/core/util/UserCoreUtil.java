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
package org.wso2.micro.integrator.security.user.core.util;

import org.apache.axiom.om.util.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.core.Constants;
import org.wso2.micro.core.util.StringUtils;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.security.UnsupportedSecretTypeException;
import org.wso2.micro.integrator.security.user.api.RealmConfiguration;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.core.UserStoreException;
import org.wso2.micro.integrator.security.user.core.authorization.DBConstants;
import org.wso2.micro.integrator.security.user.core.common.UserStore;
import org.wso2.micro.integrator.security.user.core.dto.RoleDTO;
import org.wso2.micro.integrator.security.user.core.internal.UserStoreMgtDSComponent;
import org.wso2.micro.integrator.security.user.core.jdbc.JDBCRealmConstants;
import org.wso2.micro.integrator.security.user.core.model.UserMgtContext;
import org.wso2.micro.integrator.security.user.core.service.RealmService;
import org.wso2.micro.integrator.security.util.Secret;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to handle user kernel utilities.
 */
public final class UserCoreUtil {

    private static final String DUMMY_VALUE = "dummy";
    private static final String APPLICATION_DOMAIN = "Application";
    private static final String WORKFLOW_DOMAIN = "Workflow";
    private static Log log = LogFactory.getLog(UserCoreUtil.class);
    private static Boolean isEmailUserName;
    private static Boolean isCrossTenantUniqueUserName;
    private static RealmService realmService = null;
    public static final String BOUNCY_CASTLE_PROVIDER = "BC";
    public static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
    public static final String SECURITY_JCE_PROVIDER = "security.jce.provider";
    /*
     * When user authenticates with out domain, need to set the domain of the user store that he
     * belongs to, as a thread local variable.
     */
    private static ThreadLocal<String> threadLocalToSetDomain = new ThreadLocal<String>();
    private static ThreadLocal<UserMgtContext> threadLocalToSetUserMgtContext = new
            ThreadLocal<UserMgtContext>();

    /**
     * @param arr1
     * @param arr2
     * @return
     * @throws UserStoreException
     */
    public static String[] combineArrays(String[] arr1, String[] arr2) throws UserStoreException {
        if (arr1 == null || arr1.length == 0) {
            return arr2;
        }
        if (arr2 == null || arr2.length == 0) {
            return arr1;
        }
        String[] newArray = new String[arr1.length + arr2.length];
        for (int i = 0; i < arr1.length; i++) {
            newArray[i] = arr1[i];
        }

        int j = 0;
        for (int i = arr1.length; i < newArray.length; i++) {
            newArray[i] = arr2[j];
            j++;
        }
        return newArray;
    }

    /**
     * @param array
     * @param list
     * @return
     * @throws UserStoreException
     */
    public static String[] combine(String[] array, List<String> list) throws UserStoreException {

        if(array == null || list == null){
            throw new IllegalArgumentException("Invalid parameters; array : " + array + ", list : " + list);
        }
        Set h = new HashSet(list);
        h.addAll(Arrays.asList(array));
        return (String[]) h.toArray(new String[h.size()]);
    }

    /**
     * @param rawResourcePath
     * @return
     */
    public static String[] optimizePermissions(String[] rawResourcePath) {
        Arrays.sort(rawResourcePath);
        int index = 0;
        List<String> lst = new ArrayList<String>();
        while (index < rawResourcePath.length) {
            String shortestString = rawResourcePath[index];
            lst.add(shortestString);
            index++;
            Pattern p = Pattern.compile("(.*)/.*$");
            while (index < rawResourcePath.length) {
                Matcher m = p.matcher(rawResourcePath[index]);
                if (m.find()) {
                    String s = m.group(1);
                    if (s.equals(shortestString)) {
                        index++;
                    } else {
                        break;
                    }
                }
            }
        }
        return lst.toArray(new String[lst.size()]);
    }

    /**
     * @return
     */
    public static Boolean getIsEmailUserName() {
        return isEmailUserName;
    }

    /**
     * @return
     */
    public static RealmService getRealmService() {
        return realmService;
    }

    /**
     * @param realmService
     */
    public static void setRealmService(RealmService realmService) {
        UserCoreUtil.realmService = realmService;
    }

    /**
     * @return
     */
    public static Boolean getIsCrossTenantUniqueUserName() {
        return isCrossTenantUniqueUserName;
    }

    /**
     * process the original password to be stored as per the given hash method and the status of Kerberos Key
     * Distribution Center (KDC).
     * If KDC is enabled plain text password is returned in a byte array since it cannot operate with hashed passwords.
     * Otherwise if the provided hash method is not null password is hashed and returned in a byte array.
     *
     * @param password  original password as an Object
     * @param passwordHashMethod hash method of the password as a String
     * @param isKdcEnabled boolean true if KDC is enabled and false if not enabled
     * @return password to store in a byte array
     * @throws UserStoreException
     */
    public static byte[] getPasswordToStore(Object password, String passwordHashMethod, boolean isKdcEnabled)
            throws UserStoreException {

        Secret credentialObj;
        try {
            credentialObj = Secret.getSecret(password);
        } catch (UnsupportedSecretTypeException e) {
            throw new UserStoreException("Unsupported credential type", e);
        }

        try {
            byte[] passwordBytes = credentialObj.getBytes();
            byte[] passwordToStore = Arrays.copyOf(passwordBytes, passwordBytes.length);

            if (isKdcEnabled) {
                // If KDC is enabled we will always use plain text passwords.
                // Cause - KDC cannot operate with hashed passwords.
                return passwordToStore;
            }

            if (passwordHashMethod != null) {

                if (passwordHashMethod.equals(UserCoreConstants.RealmConfig.PASSWORD_HASH_METHOD_PLAIN_TEXT)) {
                    return passwordToStore;
                }

                try {
                    String provider = getJceProvider();
                    MessageDigest messageDigest;
                    if (provider != null) {
                        messageDigest = MessageDigest.getInstance(passwordHashMethod, provider);
                    } else {
                        messageDigest = MessageDigest.getInstance(passwordHashMethod);
                    }
                    byte[] digestValue = messageDigest.digest(passwordBytes);
                    String saltedPassword = "{" + passwordHashMethod + "}" + Base64.encode(digestValue);
                    passwordToStore = saltedPassword.getBytes();
                } catch (NoSuchAlgorithmException e) {
                    throw new UserStoreException("Invalid hashMethod", e);
                } catch (NoSuchProviderException e) {
                    throw new RuntimeException("Specified security provider is not available in this environment: ", e);
                }
            }

            return passwordToStore;
        } finally {
            credentialObj.clear();
        }
    }

    /**
     * @param realmConfig
     * @return
     */
    public static boolean isKdcEnabled(RealmConfiguration realmConfig) {

        String stringKdcEnabled = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_KDC_ENABLED);

        if (stringKdcEnabled != null) {
            return Boolean.parseBoolean(stringKdcEnabled);
        } else {
            return false;
        }
    }

    /**
     * @return
     */
    public static String getDummyPassword() {
        SecureRandom rand = new SecureRandom();
        return DUMMY_VALUE + rand.nextInt(999999);
    }

    /**
     * check whether value is contain in String array case insensitivity way
     *
     * @param name
     * @param names
     * @return
     */
    public static boolean isContain(String name, String[] names) {

        if (name == null || names == null || names.length == 0) {
            return false;
        }

        for (String n : names) {
            if (name.equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method generates a random password that adhere to most of the password policies defined
     * by various LDAPs such as AD, ApacheDS 2.0 etc
     *
     * @param username
     * @return
     * @throws UserStoreException
     */
    @Deprecated
    public static String getPolicyFriendlyRandomPassword(String username) throws UserStoreException {
        return getPolicyFriendlyRandomPassword(username, 8);
    }

    /**
     * This method generates a random password that adhere to most of the password policies defined by various LDAPs
     * such as AD, ApacheDS 2.0 etc.
     *
     * @param username username of the end user
     * @return random password generated as a character array
     * @throws UserStoreException
     */
    public static char[] getPolicyFriendlyRandomPasswordInChars(String username) throws UserStoreException {
        return getPolicyFriendlyRandomPasswordInChars(username, 8);
    }

    /**
     * This method generates a random password that adhere to most of the password policies defined
     * by various LDAPs such as AD, ApacheDS 2.0 etc
     *
     * @param username username of the end user
     * @param length   length of the generating password
     * @return random password as a String
     * @throws UserStoreException
     */
    @Deprecated
    public static String getPolicyFriendlyRandomPassword(String username, int length) throws UserStoreException {
        return new String(getPolicyFriendlyRandomPasswordInChars(username, length));
    }

    /**
     * This method generates a random password that adhere to most of the password policies defined
     * by various LDAPs such as AD, ApacheDS 2.0 etc
     *
     * @param username username of the end user
     * @param length length of the generating password
     * @return random password as a character array
     * @throws UserStoreException
     */
    public static char[] getPolicyFriendlyRandomPasswordInChars(String username, int length) throws UserStoreException {

        if (length < 8 || length > 50) {
            length = 12;
        }

        // Avoiding admin, administrator, root, wso2, carbon to be a password
        char[] chars = {'E', 'F', 'G', 'H', 'J', 'K', 'L', 'N', 'P', 'Q', 'U', 'V', 'W', 'X', 'Y',
                'Z', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'n', 'p', 'q', 'u', 'v', 'w', 'x', 'y',
                'z', '~', '!', '@', '#', '$', '%', '^', '&', '*', '_', '-', '+', '=',};

        char[] invalidChars = username.toCharArray();
        StringBuffer passwordFeed = new StringBuffer();

        // now we are going filter characters in the username
        for (char invalidCha : invalidChars) {
            for (char cha : chars) {
                if (cha != invalidCha)
                    passwordFeed.append(cha);
            }
        }

        // the password generation
        String passwordChars = passwordFeed.toString();
        char[] password = new char[length];
        String randomNum = null;

        try {
            String provider = getJceProvider();
            SecureRandom prng;
            if (provider != null) {
                // the secure random
                prng = SecureRandom.getInstance("DEFAULT", provider);
            } else {
                // the secure random
                prng = SecureRandom.getInstance("SHA1PRNG");
            }

            for (int i = 0; i < length; i++) {
                password[i] = passwordChars.charAt(prng.nextInt(passwordFeed.length()));
            }
            randomNum = new Integer(prng.nextInt()).toString();

        } catch (NoSuchAlgorithmException e) {
            String errorMessage = "Error while creating the random password for user : " + username;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException("Specified security provider is not available in this environment: ", e);
        }

        return ArrayUtils.addAll(password, randomNum.toCharArray());
    }

    /**
     * @param roleNames
     * @param domain
     * @return
     */
    public static RoleDTO[] convertRoleNamesToRoleDTO(String[] roleNames, String domain) {
        if (roleNames != null && roleNames.length != 0) {
            List<RoleDTO> dtos = new ArrayList<RoleDTO>();
            for (String roleName : roleNames) {
                RoleDTO dto = new RoleDTO();
                dto.setRoleName(roleName);
                dto.setDomainName(domain);
                dtos.add(dto);
            }
            return dtos.toArray(new RoleDTO[dtos.size()]);
        }
        return null;
    }

    /**
     * @param domain
     */
    public static void setDomainInThreadLocal(String domain) {

        if (domain == null || domain.trim().isEmpty() || UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME
                .equalsIgnoreCase(domain)) {
            // clear the thread local variable.
            threadLocalToSetDomain.remove();
        } else {
            threadLocalToSetDomain.set(domain.toUpperCase());
        }
    }

    /**
     * @return
     */
    public static String getDomainFromThreadLocal() {
        return (String) threadLocalToSetDomain.get();
    }

    /**
     * @param name
     * @return
     */
    public static String removeDomainFromName(String name) {

        int index;
        if ((index = name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) >= 0) {
            // remove domain name if exist
            name = name.substring(index + 1);
        }
        return name;
    }

    public static void setUserMgtContextInThreadLocal(UserMgtContext functionalContainer) {

        if (functionalContainer != null) {
            threadLocalToSetUserMgtContext.set(functionalContainer);
        } else {
            threadLocalToSetUserMgtContext.remove();
        }
    }

    public static UserMgtContext getUserMgtContextFromThreadLocal() {

        return threadLocalToSetUserMgtContext.get();
    }

    public static void removeUserMgtContextInThreadLocal() {

        threadLocalToSetUserMgtContext.remove();
    }

    /**
     * Removes the entry name if the name is in the base.
     *
     * @param base
     * @param entryName
     * @param nameAttribute
     * @return
     */
    public static String formatSearchBase(String base, String entryName, String nameAttribute) {
        entryName = removeDomainFromName(entryName);
        String key = nameAttribute + "=" + entryName;
        if (base.indexOf(key) >= 0) {
            String[] arr = base.split(key);
            base = "";
            for (String s : arr) {
                base += s;
            }
            if (base.startsWith(",")) {
                base = base.substring(1);
            }
            if (base.endsWith(",")) {
                base = base.substring(0, base.length() - 1);
            }
            return base;
        }
        return base;
    }

    /**
     * @param name
     * @return
     */
    public static String removeDistinguishedName(String name) {

        int index;
        if ((index = name.indexOf(UserCoreConstants.TENANT_DOMAIN_COMBINER)) >= 0) {
            name = name.substring(0, index);
        }
        return name;
    }

    /**
     * @param name
     * @return
     */
    public static String addInternalDomainName(String name) {

        if (name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR) < 0) {
            // domain name is not already appended, and if exist in user-mgt.xml, append it..
            // append domain name if exist
            name = UserCoreConstants.INTERNAL_DOMAIN + UserCoreConstants.DOMAIN_SEPARATOR + name;
        }
        return name;
    }

    /**
     * @param name
     * @return
     */
    public static String setDomainToUpperCase(String name) {

        int index = name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR);

        if (index > 0) {
            String domain = name.substring(0, index);
            name = domain.toUpperCase() + name.substring(index);
        }

        return name;
    }

    /**
     * @param domainName
     * @return
     */
    public static String addDomainToName(String name, String domainName) {

        if ((name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) < 0 &&
                !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domainName)) {
            // domain name is not already appended, and if exist in user-mgt.xml, append it..
            if (domainName != null) {
                // append domain name if exist
                domainName = domainName.toUpperCase() + UserCoreConstants.DOMAIN_SEPARATOR;
                name = domainName + name;
            }
        }
        return name;
    }

    /**
     * Append the distinguished name to the tenantAwareEntry name
     *
     * @param tenantAwareEntry
     * @param tenantDomain
     * @return
     */
    public static String addTenantDomainToEntry(String tenantAwareEntry, String tenantDomain) {

        if (StringUtils.isEmpty(tenantAwareEntry)){
            throw new IllegalArgumentException();
        } else if (!StringUtils.isEmpty(tenantDomain)) {
            return tenantAwareEntry + UserCoreConstants.TENANT_DOMAIN_COMBINER + tenantDomain;
        } else {
            return tenantAwareEntry + UserCoreConstants.TENANT_DOMAIN_COMBINER + Constants.SUPER_TENANT_DOMAIN_NAME;
        }
    }

    /**
     * @param realmConfig
     * @return
     */
    public static String getDomainName(RealmConfiguration realmConfig) {
        String domainName = realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
        if(domainName != null) {
            domainName = domainName.toUpperCase();
        }
        return domainName;
    }

    /**
     * Domain name is not already appended, and if it is provided or if exist in user-mgt.xml,
     * append it
     *
     * @param names
     * @param domainName
     * @return
     */
    public static String[] addDomainToNames(String[] names, String domainName) {

        if (domainName != null) {
            domainName = domainName.toUpperCase();
        }

        List<String> namesList = new ArrayList<String>();
        if (names != null && names.length != 0) {
            for (String name : names) {
                if ((name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) < 0 &&
                        !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domainName)) {
                    if (domainName != null) {
                        name = UserCoreUtil.addDomainToName(name, domainName);
                        namesList.add(name);
                        continue;
                    }
                }
                namesList.add(name);
            }
        }
        if (namesList.size() != 0) {
            return namesList.toArray(new String[namesList.size()]);
        } else {
            return names;
        }
    }

    /**
     * @param names
     * @return
     */
    public static String[] removeDomainFromNames(String[] names) {
        List<String> nameList = new ArrayList<String>();
        int index;
        if (names != null && names.length != 0) {
            for (String name : names) {
                if ((index = name.indexOf(UserCoreConstants.DOMAIN_SEPARATOR)) > 0) {
                    String domain = name.substring(0, index);
                    if (!UserCoreConstants.INTERNAL_DOMAIN.equalsIgnoreCase(domain)
                        && !APPLICATION_DOMAIN.equalsIgnoreCase(domain) && !WORKFLOW_DOMAIN.equalsIgnoreCase(domain)) {
                        // remove domain name if exist
                        nameList.add(name.substring(index + 1));
                    } else {
                        nameList.add(name);
                    }
                }
            }
        }
        if (nameList.size() != 0) {
            return nameList.toArray(new String[nameList.size()]);
        } else {
            return names;
        }
    }

    /**
     * @param domainName
     * @param userName
     * @param displayName
     * @return
     */
    public static String getCombinedName(String domainName, String userName, String displayName) {
        /*
		 * get the name in combined format if two different values are there for userName &
		 * displayName format: domainName/userName|domainName/displayName
		 */
        // if name and display name are equal, keep only one
        String combinedName = null;
        if (domainName != null &&
                !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domainName)) {
            domainName = domainName.toUpperCase() + UserCoreConstants.DOMAIN_SEPARATOR;
            if ((!userName.equals(displayName)) && (displayName != null)) {
                userName = domainName + userName;
                displayName = domainName + displayName;
                combinedName = userName + UserCoreConstants.NAME_COMBINER + displayName;
            } else {
                combinedName = domainName + userName;
            }
        } else {
            if (!userName.equals(displayName) && displayName != null) {
                combinedName = userName + UserCoreConstants.NAME_COMBINER + displayName;
            } else {
                combinedName = userName;
            }
        }
        return combinedName;
    }

    /**
     * @param userName
     * @param realmConfig
     * @return
     */
    public static boolean isPrimaryAdminUser(String userName, RealmConfiguration realmConfig) {

        String myDomain = getDomainName(realmConfig);

        if (myDomain != null) {
            myDomain += UserCoreConstants.DOMAIN_SEPARATOR;
        }

        if (realmConfig.isPrimary()) {
            if (realmConfig.getAdminUserName().equalsIgnoreCase(userName)
                    || realmConfig.getAdminUserName().equalsIgnoreCase(myDomain + userName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param roleName
     * @param realmConfig
     * @return
     */
    public static boolean isPrimaryAdminRole(String roleName, RealmConfiguration realmConfig) {

        String myDomain = getDomainName(realmConfig);

        if (myDomain != null) {
            myDomain += UserCoreConstants.DOMAIN_SEPARATOR;
        }

        if (realmConfig.isPrimary()) {
            if (realmConfig.getAdminRoleName().equalsIgnoreCase(roleName)
                    || realmConfig.getAdminRoleName().equalsIgnoreCase(myDomain + roleName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param roleName
     * @param realmConfig
     * @return
     */
    public static boolean isEveryoneRole(String roleName, RealmConfiguration realmConfig) {

        String myDomain = UserCoreConstants.INTERNAL_DOMAIN;

        if (myDomain != null) {
            myDomain += UserCoreConstants.DOMAIN_SEPARATOR;
        }

        if (realmConfig.isPrimary() && realmConfig.getEveryOneRoleName() != null
                && (realmConfig.getEveryOneRoleName().equalsIgnoreCase(roleName))
                || realmConfig.getEveryOneRoleName().equalsIgnoreCase(myDomain + roleName)) {
            return true;
        }
        return false;
    }

    /**
     * @param
     * @param realmConfig
     * @return
     */
    public static boolean canRoleBeRenamed(UserStore oldStore, UserStore newStore,
                                           RealmConfiguration realmConfig) {

        if (oldStore.getDomainName() == null && newStore.getDomainName() != null) {
            return false;
        }

        if (oldStore.getDomainName() != null
                && !oldStore.getDomainName().equalsIgnoreCase(newStore.getDomainName())) {
            return false;
        }

        if ((oldStore.isHybridRole() && realmConfig
                .isReservedRoleName(oldStore.getDomainFreeName()))
                || (newStore.isHybridRole() && realmConfig.isReservedRoleName(newStore
                .getDomainFreeName()))) {
            return false;
        }

        return true;
    }

    /**
     * @param userName
     * @param
     * @return
     */
    public static boolean isRegistryAnnonymousUser(String userName) {

        if (UserCoreConstants.REGISTRY_ANONNYMOUS_USERNAME.equalsIgnoreCase(userName)) {
            return true;
        }

        return false;
    }

    /**
     * @param userName
     * @param
     * @return
     */
    public static boolean isRegistrySystemUser(String userName) {

        if (UserCoreConstants.REGISTRY_SYSTEM_USERNAME.equalsIgnoreCase(userName)) {
            return true;
        }

        return false;
    }

    public static String extractDomainFromName(String nameWithDomain) {
        if (nameWithDomain.indexOf(UserCoreConstants.DOMAIN_SEPARATOR) > 0) {
            String names[] = nameWithDomain.split(UserCoreConstants.DOMAIN_SEPARATOR);
            return names[0];
        } else {
            if (UserStoreMgtDSComponent.getRealmService() != null) {
                //this check is added to avoid NullPointerExceptions if the osgi is not started yet.
                //as an example when running the unit tests.
                RealmConfiguration realmConfiguration = UserStoreMgtDSComponent.getRealmService()
                        .getBootstrapRealmConfiguration();
                if (realmConfiguration.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME) != null) {
                    return realmConfiguration.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                } else {
                    return UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME;
                }
            }
            return UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME;
        }
    }

    public static void persistDomain(String domain, int tenantId, DataSource dataSource) throws UserStoreException {
        Connection dbConnection = null;
        try {
            String sqlStatement = JDBCRealmConstants.ADD_DOMAIN_SQL;

            if (domain != null) {
                domain = domain.toUpperCase();
            }

            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            dbConnection.setAutoCommit(false);
            if (!isExistingDomain(domain, tenantId, dbConnection)) {
                DatabaseUtil.updateDatabase(dbConnection, sqlStatement, domain, tenantId);
            }
            dbConnection.commit();
        } catch (UserStoreException e) {
            String errorMessage =
                    "Error occurred while checking is existing domain : " + domain + " for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            DatabaseUtil.rollBack(dbConnection);
            throw new UserStoreException(errorMessage, e);
        } catch (SQLException e) {
            String errorMessage =
                    "DB error occurred while persisting domain : " + domain + " & tenant id : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }

    }

    public static void deletePersistedDomain(String domain, int tenantId, DataSource dataSource)
            throws UserStoreException {
        Connection dbConnection = null;
        try {
            String sqlStatement = JDBCRealmConstants.DELETE_DOMAIN_SQL;

            if (domain != null) {
                domain = domain.toUpperCase();
            }
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            dbConnection.setAutoCommit(false);
            if (isExistingDomain(domain, tenantId, dbConnection)) {
                DatabaseUtil.updateDatabase(dbConnection, sqlStatement, domain, tenantId);
            }
            dbConnection.commit();
        } catch (UserStoreException e) {
            String errorMessage =
                    "Error occurred while deleting domain : " + domain + " for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (SQLException e) {
            String errorMessage =
                    "DB error occurred while deleting domain : " + domain + " & tenant id : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public static void updatePersistedDomain(String previousDomain, String newDomain, int tenantId,
                                             DataSource dataSource) throws UserStoreException {
        Connection dbConnection = null;
        try {
            String sqlStatement = JDBCRealmConstants.UPDATE_DOMAIN_SQL;

            if (previousDomain != null) {
                previousDomain = previousDomain.toUpperCase();
            }
            if (newDomain != null) {
                newDomain = newDomain.toUpperCase();
            }

            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            // check for previous domain exists
            if (isExistingDomain(previousDomain, tenantId, dbConnection)) {

                // New domain already exists, delete it first
                if (!isExistingDomain(newDomain, tenantId, dbConnection)) {
                    deletePersistedDomain(newDomain, tenantId, dataSource);
                }

                // Now rename the domain name
                dbConnection.setAutoCommit(false);
                DatabaseUtil.updateDatabase(dbConnection, sqlStatement, newDomain, previousDomain, tenantId);
                dbConnection.commit();
            }
        } catch (UserStoreException e) {
            String errorMessage =
                    "Error occurred while updating domain : " + previousDomain + " to new domain : " + newDomain +
                    " for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } catch (SQLException e) {
            String errorMessage =
                    "DB error occurred while updating domain : " + previousDomain + " to new domain : " + newDomain +
                    " for tenant : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

//    public static void persistDomainWithId(int domainId, String domain, int tenantId, DataSource dataSource)
//            throws UserStoreException {
//        Connection dbConnection = null;
//
//        if (domain != null) {
//            domain = domain.toUpperCase();
//        }
//
//		try {
//			String sqlStatement = JDBCRealmConstants.ADD_DOMAIN_WITH_ID_SQL;
//			if (!checkExistingDomainId(domainId, tenantId, dataSource)) {
//				dbConnection = DatabaseUtil.getDBConnection(dataSource);
//				DatabaseUtil.updateDatabase(dbConnection, sqlStatement, domainId, domain, tenantId);
//				dbConnection.commit();
//			} else if (UserCoreConstants.PRIMARY_DOMAIN_ID == domainId) {
//				sqlStatement = JDBCRealmConstants.UPDATE_DOMAIN_WITH_ID_SQL;
//				dbConnection = DatabaseUtil.getDBConnection(dataSource);
//				DatabaseUtil.updateDatabase(dbConnection, sqlStatement, domain, domainId, tenantId);
//				dbConnection.commit();
//			}
//		} catch (UserStoreException e) {
//			throw new UserStoreException(e.getMessage());
//		} catch (SQLException e) {
//			throw new UserStoreException(e.getMessage());
//		} finally {
//			DatabaseUtil.closeAllConnections(dbConnection);
//		}
//    }

    private static boolean isExistingDomain(String domain, int tenantId, Connection connection)
            throws UserStoreException {
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isExisting = false;

        try {
            prepStmt = connection.prepareStatement(JDBCRealmConstants.IS_DOMAIN_EXISTING_SQL);
            if (domain != null) {
                domain = domain.toUpperCase();
            }
            prepStmt.setString(1, domain);
            prepStmt.setInt(2, tenantId);
            rs = prepStmt.executeQuery();
            if (rs.next()) {
                isExisting = true;
            }
            return isExisting;
        } catch (SQLException e) {
            String errorMessage =
                    "DB error occurred while checking is existing domain : " + domain + " & tenant id : " + tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections( null, rs, prepStmt );
        }
    }

    private static boolean checkExistingDomainId(int domainId, int tenantId, DataSource dataSource)
            throws UserStoreException {
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isExisting = false;

        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            prepStmt = dbConnection.prepareStatement(JDBCRealmConstants.CHECK_DOMAIN_ID_EXISTING_SQL);
            prepStmt.setInt(1, domainId);
            prepStmt.setInt(2, tenantId);
            rs = prepStmt.executeQuery();
            if (rs.next()) {
                int value = rs.getInt(1);
                if (domainId == value) {
                    isExisting = true;
                }
            }
            return isExisting;
        } catch (SQLException e) {
            String errorMessage =
                    "DB error occurred while checking is existing domain id : " + domainId + " & tenant id : " +
                    tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    public static boolean isSystemRole(String roleName, int tenantId, DataSource dataSource)
            throws UserStoreException {
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isExisting = false;

        try {
            dbConnection = DatabaseUtil.getDBConnection(dataSource);
            prepStmt = dbConnection.prepareStatement(DBConstants.IS_SYSTEM_ROLE);
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
            String errorMessage =
                    "DB error occurred while checking is existing system role for : " + roleName + " & tenant id : " +
                    tenantId;
            if (log.isDebugEnabled()) {
                log.debug(errorMessage, e);
            }
            throw new UserStoreException(errorMessage, e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    /**
     * Returns the shared group RDN for the tenants
     *
     * @param tenantOu
     * @return
     */
    public static String getTenantShareGroupBase(String tenantOu) {
        return tenantOu + "=" + Constants.SUPER_TENANT_DOMAIN_NAME;
    }

    /**
     * Clear sensitive char arrays
     *
     * @param chars char array to be cleared
     */
    public static void clearSensitiveChars(char[] chars) {
        if (chars == null) {
            return;
        }

        Arrays.fill(chars, '\u0000');
    }

    /**
     * Clear sensitive byte arrays
     *
     * @param bytes byte array to be cleared
     */
    public static void clearSensitiveBytes(byte[] bytes) {
        if (bytes == null) {
            return;
        }

        Arrays.fill(bytes, (byte) 0);
    }

    public static boolean isEmailUserName(){
        String enableEmailUserName = CarbonServerConfigurationService.getInstance().
                getFirstProperty(UserCoreConstants.ENABLE_EMAIL_USER_NAME);
        return enableEmailUserName != null && "true".equals(enableEmailUserName.trim());

    }

    public static void logUnsupportedOperation() {
        // please un-comment to find usages
        /*log.debug("This Functionality is not available in WSO2 Micro Integrator",
                 new Throwable("Unsupported operation"));*/
    }

    /**
     * Get the JCE provider to be used for encryption/decryption
     *
     * @return
     */
    public static String getJceProvider() {
        String provider = System.getProperty(SECURITY_JCE_PROVIDER);
        if (provider != null && (provider.equalsIgnoreCase(BOUNCY_CASTLE_FIPS_PROVIDER) ||
                provider.equalsIgnoreCase(BOUNCY_CASTLE_PROVIDER))) {
            return provider;
        }
        return null;
    }
}
