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

package org.wso2.micro.integrator.management.apis.security.handler;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.crypto.CryptoConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.initializer.utils.SecretResolverUtil;
import org.wso2.micro.integrator.management.apis.Constants;
import org.wso2.micro.integrator.security.MicroIntegratorSecurityUtils;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.micro.integrator.security.user.core.file.FileBasedUserStoreManager;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecureVaultException;

import java.util.Map;
import java.util.function.Supplier;
import javax.xml.namespace.QName;

public class SecurityUtils {

    private static final Log LOG = LogFactory.getLog(SecurityUtils.class);

    /**
     * Returns the transport header map of a given axis2 message context.
     *
     * @param axis2MessageContext axis2 message context
     * @return transport header map
     */
    public static Map getHeaders(org.apache.axis2.context.MessageContext axis2MessageContext) {

        Object headers = axis2MessageContext.getProperty(
                org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        Map headersMap = null;
        if (headers != null && headers instanceof Map) {
            headersMap = (Map) headers;
        }
        return headersMap;
    }

    /**
     * Sets the provided status code for the response.
     *
     * @param messageContext Synapse message context
     * @param statusCode     status code
     */
    public static void setStatusCode(MessageContext messageContext, int statusCode) {

        org.apache.axis2.context.MessageContext axis2MessageContext
                = ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        axis2MessageContext.setProperty(AuthConstants.HTTP_STATUS_CODE, statusCode);
        axis2MessageContext.setProperty(AuthConstants.NO_ENTITY_BODY, true);
        messageContext.setProperty(AuthConstants.RESPONSE, AuthConstants.TRUE);
        messageContext.setTo(null);
    }

    /**
     * Returns the resolved secret.
     *
     * @param secretResolver secret resolver initialized with relevant OM element
     * @param paramElement   OMElement password
     * @return resolved password
     */
    public static String getSecureVaultValue(SecretResolver secretResolver, OMElement paramElement) {
        String value = null;
        if (paramElement != null) {
            OMAttribute attribute = paramElement.getAttribute(new QName(CryptoConstants.SECUREVAULT_NAMESPACE,
                    CryptoConstants.SECUREVAULT_ALIAS_ATTRIBUTE));
            if (attribute != null && attribute.getAttributeValue() != null && !attribute.getAttributeValue().isEmpty
                    ()) {
                if (secretResolver == null) {
                    throw new SecureVaultException("Cannot resolve secret password because axis2 secret resolver "
                            + "is null");
                }
                if (secretResolver.isTokenProtected(attribute.getAttributeValue())) {
                    value = secretResolver.resolve(attribute.getAttributeValue());
                }
            } else {
                value = paramElement.getText();
            }
        }
        return value;
    }

    public static Boolean isFileBasedUserStoreEnabled() {

        Object fileUserStore = ConfigParser.getParsedConfigs().get(Constants.FILE_BASED_USER_STORE_ENABLE);
        if (fileUserStore != null) {
            return Boolean.valueOf(fileUserStore.toString());
        }
        return false;
    }

    /**
     * Method to assert if a user is an admin.
     *
     * Note: For better performance, use isAdmin(MessageContext, String) when MessageContext is available,
     * as it can leverage the IS_ADMIN_USER property set by authentication/authorization handlers.
     *
     * @param username the user to be validated as an admin
     * @return true if the admin role is assigned to the user
     * @throws UserStoreException if any error occurs while retrieving the user store manager or reading the user realm
     *                            configuration
     */
    public static boolean isAdmin(String username) throws UserStoreException {
        // Early null check to prevent NPEs in user store methods
        if (username == null) {
            return false;
        }
        // Fall back to user store check
        if (isFileBasedUserStoreEnabled()) {
            return FileBasedUserStoreManager.getUserStoreManager().isAdmin(username);
        } else {
            return MicroIntegratorSecurityUtils.isAdmin(username);
        }
    }

    /**
     * Method to assert if a user is an admin, with support for generic IS_ADMIN_USER property.
     * This method first checks if the IS_ADMIN_USER property is set in the MessageContext
     * (which can be set by any authentication/authorization handler such as ICP JWT, OAuth, etc.).
     * If not set, it falls back to the regular user store lookup.
     *
     * SECURITY: The cached property only applies to the logged-in user. If checking a different user,
     * this method will fall back to user store query.
     *
     * @param messageContext the message context that may contain the IS_ADMIN_USER property
     * @param username the user to be validated as an admin
     * @return true if the admin role is assigned to the user or if IS_ADMIN_USER property is true
     * @throws UserStoreException if any error occurs while retrieving the user store manager or reading the user realm
     *                            configuration
     */
    public static boolean isAdmin(MessageContext messageContext, String username) throws UserStoreException {
        // Early null check to prevent NPEs in user store methods
        if (username == null) {
            return false;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Checking admin status for user: " + username);
        }
        // First check if IS_ADMIN_USER property is set by authentication handler
        if (messageContext != null) {
            Object isAdminProperty = messageContext.getProperty(Constants.IS_ADMIN_USER_PROPERTY);
            if (Boolean.TRUE.equals(isAdminProperty)) {
                // SECURITY: Only use cached property if checking the logged-in user
                Object loggedInUserObj = messageContext.getProperty(Constants.USERNAME_PROPERTY);
                if (loggedInUserObj != null) {
                    String loggedInUser = loggedInUserObj.toString();
                    if (username.equals(loggedInUser)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Admin status retrieved from cached IS_ADMIN_USER property for user: "
                                    + username);
                        }
                        return true;
                    }
                }
            }
        }

        // Fall back to regular user store check
        return isAdmin(username);
    }

    /**
     * Checks if non-admin users are treated as read-only users based on the configuration.
     *
     * @return {@code true} if non-admin users are read-only according to the config, {@code false} otherwise.
     */
    private static boolean isNonAdminUsersReadOnly() {
        return (Boolean) ConfigParser.getParsedConfigs().getOrDefault(Constants.MAKE_NON_ADMIN_USERS_READ_ONLY, false);
    }

    /**
     * Determines if the specified user has permission to edit.
     *
     * Admin users always have edit permissions. For non-admin users, the edit permission depends
     * on the configuration: if make_non_admin_users_read_only == true, they cannot edit; otherwise, they can.
     *
     * @param userName the name of the user to check for edit permissions
     * @return {@code true} if the user has edit permissions, {@code false} otherwise
     * @throws UserStoreException if there is an error accessing the user store
     */
    public static boolean canUserEdit(String userName) throws UserStoreException {
        if (userName == null) {
            return true;
        }
        // Return true if non-admin users can edit, or if user is admin (short-circuits to avoid unnecessary lookup)
        return !isNonAdminUsersReadOnly() || isAdmin(userName);
    }

    /**
     * Determines if the specified user has permission to edit, with support for generic IS_ADMIN_USER property.
     * This method first checks if the IS_ADMIN_USER property is set in the MessageContext
     * (which can be set by any authentication/authorization handler such as ICP JWT, OAuth, etc.).
     *
     * Admin users always have edit permissions. For non-admin users, the edit permission depends
     * on the configuration: if make_non_admin_users_read_only == true, they cannot edit; otherwise, they can.
     *
     * @param messageContext the message context that may contain the IS_ADMIN_USER property
     * @param userName the name of the user to check for edit permissions
     * @return {@code true} if the user has edit permissions, {@code false} otherwise
     * @throws UserStoreException if there is an error accessing the user store
     */
    public static boolean canUserEdit(MessageContext messageContext, String userName) throws UserStoreException {
        if (userName == null) {
            return true;
        }
        // Return true if non-admin users can edit, or if user is admin (short-circuits to avoid unnecessary lookup)
        return !isNonAdminUsersReadOnly() || isAdmin(messageContext, userName);
    }

    /**
     * Resolves a Secure Vault alias to its actual secret value.
     * Uses the default SecretCallbackHandlerService from AppDeployerServiceComponent.
     *
     * If the value is not a Secure Vault alias (doesn't match the $secret{...} pattern),
     * it returns the value as-is.
     *
     * If the value is a Secure Vault alias but resolution fails (e.g., alias not found),
     * this method may return {@code null}.
     *
     * This method delegates to SecretResolverUtil for the actual resolution.
     *
     * Usage example:
     * <pre>
     * try {
     *     String secret = SecurityUtils.resolveSecret(configuredValue);
     *     if (secret == null) {
     *         // Handle unresolved secret (e.g., alias not found)
     *     }
     * } catch (IllegalStateException e) {
     *     // Handle Secure Vault initialization errors
     * }
     * </pre>
     *
     * @param value the value to resolve (may be a plain value or a Secure Vault alias like $secret{alias})
     * @return the resolved secret value, the original value if not an alias, or {@code null} if a secret alias cannot be resolved
     * @throws IllegalStateException if Secure Vault is not properly initialized
     */
    public static String resolveSecret(String value) {
        return SecretResolverUtil.resolveSecret(value);
    }

    /**
     * Resolves a Secure Vault alias to its actual secret value.
     * Allows providing a custom SecretCallbackHandlerService supplier.
     *
     * If the value is not a Secure Vault alias (doesn't match the $secret{...} pattern),
     * it returns the value as-is.
     *
     * If the value is a Secure Vault alias but resolution fails (e.g., Secure Vault not initialized,
     * or alias not found), this method throws an IllegalStateException.
     *
     * This method delegates to SecretResolverUtil for the actual resolution.
     *
     * Usage example:
     * <pre>
     * try {
     *     String secret = SecurityUtils.resolveSecret(
     *         configuredValue,
     *         () -> ICPApiServiceComponent.getSecretCallbackHandlerService()
     *     );
     * } catch (IllegalStateException e) {
     *     // Handle unresolved secret
     * }
     * </pre>
     *
     * @param value the value to resolve (may be a plain value or a Secure Vault alias like $secret{alias})
     * @param secretCallbackHandlerServiceSupplier supplier that provides the SecretCallbackHandlerService
     *                                             (e.g., () -> ICPApiServiceComponent.getSecretCallbackHandlerService())
     * @return the resolved secret value, or the original value if not an alias
     * @throws IllegalStateException if the value is a secret placeholder but resolution fails
     */
    public static String resolveSecret(String value, Supplier<SecretCallbackHandlerService> secretCallbackHandlerServiceSupplier) {
        return SecretResolverUtil.resolveSecret(value, secretCallbackHandlerServiceSupplier);
    }
}
