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
     * Thread-local cache for SecretResolver instances.
     * Each thread can have its own resolver to avoid synchronization overhead.
     */
    private static final ThreadLocal<SecretResolver> secretResolverCache = new ThreadLocal<>();

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
     * Usage example:
     * <pre>
     * String secret = SecretResolverUtil.resolveSecret(configuredValue);
     * </pre>
     *
     * @param value the value to resolve (may be a plain value or a Secure Vault alias like $secret{alias})
     * @return the resolved secret value, or the original value if not an alias or if resolution fails
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
     * This method creates and initializes a SecretResolver on demand using the provided
     * SecretCallbackHandlerService supplier. The resolver is thread-safe through thread-local caching.
     *
     * Usage example:
     * <pre>
     * String secret = SecretResolverUtil.resolveSecret(
     *     configuredValue,
     *     () -> ICPApiServiceComponent.getSecretCallbackHandlerService()
     * );
     * </pre>
     *
     * @param value the value to resolve (may be a plain value or a Secure Vault alias like $secret{alias})
     * @param secretCallbackHandlerServiceSupplier supplier that provides the SecretCallbackHandlerService
     *                                             (e.g., () -> AppDeployerServiceComponent.getSecretCallbackHandlerService())
     * @return the resolved secret value, or the original value if not an alias or if resolution fails
     */
    public static String resolveSecret(String value, Supplier<SecretCallbackHandlerService> secretCallbackHandlerServiceSupplier) {
        String alias = MiscellaneousUtil.getProtectedToken(value);
        if (alias == null || alias.isEmpty()) {
            return value;
        }
        try {
            SecretResolver resolver = getOrCreateSecretResolver(secretCallbackHandlerServiceSupplier);
            if (resolver == null || !resolver.isInitialized()) {
                LOG.warn("Secure Vault is not initialized. Using configured value as-is.");
                return value;
            }
            return MiscellaneousUtil.resolve(alias, resolver);
        } catch (Exception e) {
            LOG.error("Error resolving secret from Secure Vault. Using configured value as-is.", e);
            return value;
        }
    }

    /**
     * Gets or creates a SecretResolver instance, initializing it if necessary.
     * This method uses thread-local caching to avoid creating multiple resolvers.
     *
     * @param secretCallbackHandlerServiceSupplier supplier that provides the SecretCallbackHandlerService
     * @return an initialized SecretResolver instance, or null if initialization fails
     */
    private static SecretResolver getOrCreateSecretResolver(Supplier<SecretCallbackHandlerService> secretCallbackHandlerServiceSupplier) {
        SecretResolver resolver = secretResolverCache.get();
        if (resolver == null) {
            resolver = SecretResolverFactory.create((OMElement) null, false);
            secretResolverCache.set(resolver);
        }
        if (!resolver.isInitialized()) {
            SecretCallbackHandlerService service = secretCallbackHandlerServiceSupplier.get();
            if (service != null) {
                resolver.init(service.getSecretCallbackHandler());
            }
        }
        return resolver;
    }
}
