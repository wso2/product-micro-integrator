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
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.crypto.CryptoConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.management.apis.Constants;
import org.wso2.micro.integrator.security.MicroIntegratorSecurityUtils;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.micro.integrator.security.user.core.file.FileBasedUserStoreManager;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecureVaultException;

import java.util.Map;
import javax.xml.namespace.QName;

public class SecurityUtils {

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
     * @param username the user to be validated as an admin
     * @return true if the admin role is assigned to the user
     * @throws UserStoreException if any error occurs while retrieving the user store manager or reading the user realm
     *                            configuration
     */
    public static boolean isAdmin(String username) throws UserStoreException {
        if (isFileBasedUserStoreEnabled()) {
            return FileBasedUserStoreManager.getUserStoreManager().isAdmin(username);
        } else {
            return MicroIntegratorSecurityUtils.isAdmin(username);
        }
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
        return isAdmin(userName) || !isNonAdminUsersReadOnly();
    }
}
