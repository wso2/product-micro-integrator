/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer.utils;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.micro.integrator.initializer.deployment.AppDeployerServiceComponent;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;

import java.util.function.Supplier;

/**
 * Utility class for resolving Secure Vault secrets.
 * Provides centralized secret resolution logic that can be reused across components.
 */
public class SecretResolverUtil {

    private static final Log LOG = LogFactory.getLog(SecretResolverUtil.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private SecretResolverUtil() {
    }

    /**
     * Resolves a Secure Vault alias to its actual secret value.
     * Uses the default AppDeployerServiceComponent for SecretCallbackHandlerService.
     *
     * If the value is not a Secure Vault alias (doesn't match the $secret{...} pattern),
     * it returns the value as-is.
     *
     * If the value is a Secure Vault alias but resolution fails (e.g., Secure Vault not initialized,
     * or alias not found), this method throws an IllegalStateException to prevent using unresolved
     * secret placeholders.
     *
     * Usage example:
     * <pre>
     * try {
     *     String secret = SecretResolverUtil.resolveSecret(configuredValue);
     * } catch (IllegalStateException e) {
     *     // Handle unresolved secret
     * }
     * </pre>
     *
     * @param value the value to resolve (may be a plain value or a Secure Vault alias like $secret{alias})
     * @return the resolved secret value, or the original value if not an alias
     * @throws IllegalStateException if the value is a secret placeholder but resolution fails
     */
    public static String resolveSecret(String value) {
        return resolveSecret(value, AppDeployerServiceComponent::getSecretCallbackHandlerService);
    }

    /**
     * Resolves a Secure Vault alias to its actual secret value.
     * Allows providing a custom SecretCallbackHandlerService supplier.
     *
     * If the value is not a Secure Vault alias (doesn't match the $secret{...} pattern),
     * it returns the value as-is.
     *
     * If the value is a Secure Vault alias but resolution fails (e.g., Secure Vault not initialized,
     * alias not found, or resolution returns null), this method throws an IllegalStateException
     * to prevent using unresolved secret placeholders in downstream operations.
     *
     * This method creates and initializes a fresh SecretResolver for each call using the provided
     * SecretCallbackHandlerService supplier, ensuring the resolver uses the correct service instance.
     *
     * Usage example:
     * <pre>
     * try {
     *     String secret = SecretResolverUtil.resolveSecret(
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
     *                                             (e.g., () -> AppDeployerServiceComponent.getSecretCallbackHandlerService())
     * @return the resolved secret value, or the original value if not an alias
     * @throws IllegalStateException if the value is a secret placeholder but resolution fails
     *         (including if the resolver returns null, indicating the alias was not found)
     */
    public static String resolveSecret(String value, Supplier<SecretCallbackHandlerService> secretCallbackHandlerServiceSupplier) {
        String alias = MiscellaneousUtil.getProtectedToken(value);
        if (alias == null || alias.isEmpty()) {
            // Not a secret placeholder, return as-is
            return value;
        }
        // Value is a secret placeholder like $secret{...}, must resolve it
        try {
            SecretResolver resolver = getOrCreateSecretResolver(secretCallbackHandlerServiceSupplier);
            if (resolver == null || !resolver.isInitialized()) {
                String errorMsg = "Secure Vault is not initialized but secret placeholder detected: " + value;
                LOG.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            String resolved = resolver.resolve(alias);
            // Verify that resolution succeeded
            // (resolved must not be null, indicating alias was found and resolved)
            if (resolved == null) {
                String errorMsg = "Secret alias not found or resolution returned null for placeholder: " + value;
                LOG.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            // Also check if resolution failed and returned the placeholder pattern
            if (resolved.startsWith("$secret{")) {
                String errorMsg = "Failed to resolve secret placeholder: " + value;
                LOG.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            return resolved;
        } catch (IllegalStateException e) {
            // Re-throw IllegalStateException (already logged above)
            throw e;
        } catch (Exception e) {
            String errorMsg = "Error resolving secret from Secure Vault for placeholder: " + value;
            LOG.error(errorMsg, e);
            throw new IllegalStateException(errorMsg, e);
        }
    }

    /**
     * Creates and initializes a SecretResolver instance using the provided supplier.
     * Each call creates a fresh resolver to ensure it uses the correct SecretCallbackHandlerService.
     *
     * @param secretCallbackHandlerServiceSupplier supplier that provides the SecretCallbackHandlerService
     * @return an initialized SecretResolver instance, or null if initialization fails
     */
    private static SecretResolver getOrCreateSecretResolver(Supplier<SecretCallbackHandlerService> secretCallbackHandlerServiceSupplier) {
        SecretResolver resolver = SecretResolverFactory.create((OMElement) null, false);
        SecretCallbackHandlerService service = secretCallbackHandlerServiceSupplier.get();
        if (service != null) {
            resolver.init(service.getSecretCallbackHandler());
        }
        return resolver;
    }
}
