/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.crypto.provider.internal;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.wso2.carbon.crypto.api.CryptoException;
import org.wso2.carbon.crypto.api.InternalCryptoProvider;
import org.wso2.carbon.crypto.api.KeyResolver;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.crypto.provider.ContextIndependentKeyResolver;
import org.wso2.micro.integrator.crypto.provider.KeyStoreBasedInternalCryptoProvider;
import org.wso2.micro.integrator.crypto.provider.SymmetricKeyInternalCryptoProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

/**
 * The class which is used for deal with the OSGi runtime for service registration and injection.
 */
@Component(name = "org.wso2.micro.integrator.crypto.provider.internal.DefaultCryptoProviderComponent",
        immediate = true)
public class DefaultCryptoProviderComponent {

    public static final String CRYPTO_SECRET_PROPERTY_PATH = "CryptoService.Secret";
    private final static Log log = LogFactory.getLog(DefaultCryptoProviderComponent.class);
    private static final String INTERNAL_KEYSTORE_FILE_PROPERTY_PATH = "Security.InternalKeyStore.Location";
    private static final String INTERNAL_KEYSTORE_TYPE_PROPERTY_PATH = "Security.InternalKeyStore.Type";
    private static final String INTERNAL_KEYSTORE_PASSWORD_PROPERTY_PATH = "Security.InternalKeyStore.Password";
    private static final String INTERNAL_KEYSTORE_KEY_ALIAS_PROPERTY_PATH = "Security.InternalKeyStore.KeyAlias";
    private static final String INTERNAL_KEYSTORE_KEY_PASSWORD_PROPERTY_PATH = "Security.InternalKeyStore.KeyPassword";
    private static final String CRYPTO_SERVICE_ENABLING_PROPERTY_PATH = "CryptoService.Enabled";

    private ServiceRegistration<InternalCryptoProvider> defaultInternalCryptoProviderRegistration;
    private ServiceRegistration<InternalCryptoProvider> symmetricKeyInternalCryptoProviderRegistration;
    private ServiceRegistration<KeyResolver> contextIndependentResolverRegistration;
    private CarbonServerConfigurationService serverConfigurationService;
    private static final String BOUNCY_CASTLE_PROVIDER = "BC";
    private static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
    private static final String SECURITY_JCE_PROVIDER = "security.jce.provider";

    @Activate
    public void activate(ComponentContext context) {

        try {
            BundleContext bundleContext = context.getBundleContext();

            if (isCryptoServiceEnabled()) {
                registerServiceImplementations(bundleContext);
            } else {
                if (log.isInfoEnabled()) {
                    log.debug("Crypto service is NOT enabled. "
                                      + "Therefore the key resolver and crypto provider implementations will NOT be registered.");
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("'org.wso2.carbon.crypto.provider' bundle has been activated.");
            }

        } catch (Exception e) {
            String errorMessage =
                    "An error occurred while activating 'org.wso2.carbon.crypto.provider' " + "component.";
            log.error(errorMessage, e);
        }
    }

    private void registerServiceImplementations(BundleContext bundleContext) throws CryptoException {

        InternalCryptoProvider defaultInternalCryptoProvider = getKeyStoreBasedInternalCryptoProviderFromServerConfiguration();

        InternalCryptoProvider symmetricKeyInternalCryptoProvider = getSymmetricKeyInternalCryptoProvider();

        KeyResolver contextIndependentKeyResolver = getContextIndependentKeyResolver();

        defaultInternalCryptoProviderRegistration = bundleContext
                .registerService(InternalCryptoProvider.class, defaultInternalCryptoProvider, null);

        if (log.isDebugEnabled()) {
            log.debug(String.format("'%s' has been registered as an implementation of '%s'",
                                    defaultInternalCryptoProvider.getClass().getCanonicalName(),
                                    InternalCryptoProvider.class.getCanonicalName()));
        }

        if (symmetricKeyInternalCryptoProvider != null) {
            symmetricKeyInternalCryptoProviderRegistration = bundleContext
                    .registerService(InternalCryptoProvider.class, symmetricKeyInternalCryptoProvider, null);

            if (log.isDebugEnabled()) {
                log.debug(String.format("'%s' has been registered as an implementation of '%s'",
                                        symmetricKeyInternalCryptoProvider.getClass().getCanonicalName(),
                                        InternalCryptoProvider.class.getCanonicalName()));
            }
        }

        contextIndependentResolverRegistration = bundleContext
                .registerService(KeyResolver.class, contextIndependentKeyResolver, null);

        if (log.isDebugEnabled()) {
            log.debug(String.format("'%s' has been registered as an implementation of '%s'",
                                    contextIndependentKeyResolver.getClass().getCanonicalName(),
                                    KeyResolver.class.getCanonicalName()));
        }
    }

    private SymmetricKeyInternalCryptoProvider getSymmetricKeyInternalCryptoProvider() throws CryptoException {

        String secret = serverConfigurationService.getFirstProperty(CRYPTO_SECRET_PROPERTY_PATH);

        if (StringUtils.isBlank(secret)) {

            // If the secret is not set, log an info and don't register the provider.
            String infoMessage = String.format("'%s' property has not been set. '%s' won't be registered as an "
                                                       + "internal crypto provider. Please set the secret if the provider needs to be registered.",
                                               CRYPTO_SECRET_PROPERTY_PATH,
                                               SymmetricKeyInternalCryptoProvider.class.getCanonicalName());

            if (log.isInfoEnabled()) {
                log.info(infoMessage);
            }
            return null;
        } else {
            return new SymmetricKeyInternalCryptoProvider(secret);
        }
    }

    private KeyResolver getContextIndependentKeyResolver() {

        ContextIndependentKeyResolver contextIndependentKeyResolver = new ContextIndependentKeyResolver(
                this.serverConfigurationService);

        // Try to make sure that this resolver is used after any other resolver, without configuration.
        contextIndependentKeyResolver.setPriority(99);

        return contextIndependentKeyResolver;
    }

    @Deactivate
    public void deactivate(ComponentContext context) {

        defaultInternalCryptoProviderRegistration.unregister();
        if (symmetricKeyInternalCryptoProviderRegistration != null) {
            symmetricKeyInternalCryptoProviderRegistration.unregister();
        }
        contextIndependentResolverRegistration.unregister();
    }

    @Reference(name = "serverConfigurationService",
            service = CarbonServerConfigurationService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            unbind = "unsetServerConfigurationService")
    public void setServerConfigurationService(CarbonServerConfigurationService serverConfigurationService) {

        this.serverConfigurationService = serverConfigurationService;
    }

    public void unsetServerConfigurationService(CarbonServerConfigurationService serverConfigurationService) {

        this.serverConfigurationService = null;
    }

    private InternalCryptoProvider getKeyStoreBasedInternalCryptoProviderFromServerConfiguration()
            throws CryptoException {

        try {
            KeyStore keyStore = getInternalKeyStore();
            String keyAlias = getKeyStoreConfigurationPropertyOrFail(INTERNAL_KEYSTORE_KEY_ALIAS_PROPERTY_PATH);
            String keyPassword = getKeyStoreConfigurationPropertyOrFail(INTERNAL_KEYSTORE_KEY_PASSWORD_PROPERTY_PATH);

            return new KeyStoreBasedInternalCryptoProvider(keyStore, keyAlias, keyPassword);
        } catch (CertificateException | NoSuchAlgorithmException | IOException | KeyStoreException e) {
            String errorMessage = "An error occurred while loading the internal keystore using the configurations in "
                    + "'Security.InternalKeyStore' block.";
            throw new CryptoException(errorMessage, e);
        }
    }

    private boolean isCryptoServiceEnabled() {

        return StringUtils
                .isNotBlank(serverConfigurationService.getFirstProperty(CRYPTO_SERVICE_ENABLING_PROPERTY_PATH));
    }

    private KeyStore getInternalKeyStore()
            throws CryptoException, IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {

        FileInputStream keyStoreFileInputStream = null;
        try {
            String keyStoreFileName = getKeyStoreConfigurationPropertyOrFail(INTERNAL_KEYSTORE_FILE_PROPERTY_PATH);

            if (log.isDebugEnabled()) {
                log.debug("Internal key store path : " + keyStoreFileName);
            }

            String file = new File(keyStoreFileName).getAbsolutePath();

            String password = getKeyStoreConfigurationPropertyOrFail(INTERNAL_KEYSTORE_PASSWORD_PROPERTY_PATH);

            keyStoreFileInputStream = new FileInputStream(file);

            String keyStoreType = getKeyStoreConfigurationPropertyOrFail(INTERNAL_KEYSTORE_TYPE_PROPERTY_PATH);
            String provider = getPreferredJceProvider();
            KeyStore keyStore;
            if (provider != null) {
                keyStore = KeyStore.getInstance(keyStoreType, provider);
            } else {
                keyStore = KeyStore.getInstance(keyStoreType);
            }

            keyStore.load(keyStoreFileInputStream, password.toCharArray());

            return keyStore;

        } catch (FileNotFoundException e) {
            // Handle FileNotFoundException exception and throw a CryptoException without original stack trace
            // for security purposes.
            String errorMessage = String
                    .format("Internal keystore file does not exist in the path as configured " + "in '%s' property.",
                            INTERNAL_KEYSTORE_FILE_PROPERTY_PATH);
            throw new CryptoException(errorMessage);
        } catch (NoSuchProviderException e) {
            throw new CryptoException("Specified security provider is not available in this environment", e);
        } finally {
            if (keyStoreFileInputStream != null) {
                try {
                    keyStoreFileInputStream.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private String getKeyStoreConfigurationPropertyOrFail(String internalKeystorePropertyName) throws CryptoException {

        String propertyValue = serverConfigurationService.getFirstProperty(internalKeystorePropertyName);

        if (StringUtils.isBlank(propertyValue)) {
            throw new CryptoException(String.format("Could not find a non empty value for the property '%s'",
                                                    internalKeystorePropertyName));
        }

        return propertyValue;
    }

    /**
     * Get the preferred JCE provider.
     *
     * @return the preferred JCE provider
     */
    public static String getPreferredJceProvider() {
        String provider = System.getProperty(SECURITY_JCE_PROVIDER);
        if (provider != null && (provider.equalsIgnoreCase(BOUNCY_CASTLE_FIPS_PROVIDER) ||
                provider.equalsIgnoreCase(BOUNCY_CASTLE_PROVIDER))) {
            return provider;
        }
        return null;
    }
}
