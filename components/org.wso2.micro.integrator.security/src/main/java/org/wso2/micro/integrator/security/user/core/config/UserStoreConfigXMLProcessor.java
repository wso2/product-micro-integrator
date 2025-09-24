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
package org.wso2.micro.integrator.security.user.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.util.Base64;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.wso2.micro.core.Constants;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.core.UserStoreConfigConstants;
import org.wso2.micro.integrator.security.user.core.UserStoreException;
import org.wso2.micro.integrator.security.user.core.internal.UserStoreMgtDSComponent;
import org.wso2.micro.integrator.security.user.core.tracker.UserStoreManagerRegistry;
import org.wso2.micro.integrator.security.user.core.util.UserCoreUtil;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

public class UserStoreConfigXMLProcessor {

    private static final Log log = LogFactory.getLog(UserStoreConfigXMLProcessor.class);
    private static BundleContext bundleContext;
    private static final String CIPHER_TRANSFORMATION_SYSTEM_PROPERTY = "org.wso2.CipherTransformation";
    private static PrivateKey privateKey = getPrivateKey();
    private SecretResolver secretResolver;
    private String filePath = null;
    private Gson gson = new Gson();
    public static final String BOUNCY_CASTLE_PROVIDER = "BC";
    public static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
    public static final String SECURITY_JCE_PROVIDER = "security.jce.provider";

    public UserStoreConfigXMLProcessor(String path) {
        this.filePath = path;
    }

    public static void setBundleContext(BundleContext bundleContext) {
        UserStoreConfigXMLProcessor.bundleContext = bundleContext;
    }

    public static OMElement serialize(RealmConfiguration realmConfig) {
        OMFactory factory = OMAbstractFactory.getOMFactory();

        // add the user store manager properties
        OMElement userStoreManagerElement = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_USER_STORE_MANAGER));
        addPropertyElements(factory, userStoreManagerElement, realmConfig.getUserStoreClass(), realmConfig.getUserStoreProperties());

        return userStoreManagerElement;
    }

    /**
     * Add all the user store property elements
     *
     * @param factory
     * @param parent
     * @param className
     * @param properties
     */
    private static void addPropertyElements(OMFactory factory, OMElement parent, String className,
                                            Map<String, String> properties) {
        if (className != null) {
            parent.addAttribute(UserCoreConstants.RealmConfig.ATTR_NAME_CLASS, className, null);
        }
        Iterator<Map.Entry<String, String>> ite = properties.entrySet().iterator();
        while (ite.hasNext()) {
            Map.Entry<String, String> entry = ite.next();
            String name = entry.getKey();
            String value = entry.getValue();
            OMElement propElem = factory.createOMElement(new QName(
                    UserCoreConstants.RealmConfig.LOCAL_NAME_PROPERTY));
            OMAttribute propAttr = factory.createOMAttribute(
                    UserCoreConstants.RealmConfig.ATTR_NAME_PROP_NAME, null, name);
            propElem.addAttribute(propAttr);
            propElem.setText(value);
            parent.addChild(propElem);
        }
    }

    public RealmConfiguration buildUserStoreConfigurationFromFile() throws UserStoreException {
        OMElement realmElement;
        try {
            realmElement = getRealmElement();
            return buildUserStoreConfiguration(realmElement);
        } catch (Exception e) {
            String message = "Error while building user store manager from file";
            if (log.isDebugEnabled()) {
                log.debug(message, e);
            }
            throw new UserStoreException(message, e);
        }

    }

    public RealmConfiguration buildUserStoreConfiguration(OMElement userStoreElement) throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        RealmConfiguration realmConfig = null;
        String userStoreClass = null;
        Map<String, String> userStoreProperties = null;
        boolean passwordsExternallyManaged = false;
        XMLProcessorUtils xmlProcessorUtils = new XMLProcessorUtils();

        realmConfig = new RealmConfiguration();
//        String[] fileNames = filePath.split(File.separator);
        String pattern = Pattern.quote(System.getProperty("file.separator"));
        String[] fileNames = filePath.split(pattern);
        String fileName = fileNames[fileNames.length - 1].replace(".xml", "").replace("_", ".");
        org.wso2.micro.integrator.security.user.api.RealmConfiguration primaryRealm =
                UserStoreMgtDSComponent.getRealmService().getBootstrapRealmConfiguration();
        userStoreClass = userStoreElement.getAttributeValue(new QName(UserCoreConstants.RealmConfig.ATTR_NAME_CLASS));
        userStoreProperties = getChildPropertyElements(userStoreElement, secretResolver);

        if (!userStoreProperties.get(UserStoreConfigConstants.DOMAIN_NAME).equalsIgnoreCase(fileName)) {
            throw new UserStoreException("File name is required to be the user store domain name(eg.: wso2.com-->wso2_com.xml).");
        }

//        if(!xmlProcessorUtils.isValidDomain(fileName,true)){
//            throw new UserStoreException("Invalid domain name provided");
//        }

        if (!xmlProcessorUtils.isMandatoryFieldsProvided(userStoreProperties, UserStoreManagerRegistry.getUserStoreProperties(userStoreClass).getMandatoryProperties())) {
            throw new UserStoreException("A required mandatory field is missing.");
        }

        String sIsPasswordExternallyManaged = userStoreProperties
                .get(UserCoreConstants.RealmConfig.LOCAL_PASSWORDS_EXTERNALLY_MANAGED);

        if (null != sIsPasswordExternallyManaged
                && !sIsPasswordExternallyManaged.trim().equals("")) {
            passwordsExternallyManaged = Boolean.parseBoolean(sIsPasswordExternallyManaged);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("External password management is disabled.");
            }
        }

        Map<String, String> multipleCredentialsProperties = getMultipleCredentialsProperties(userStoreElement);

        realmConfig.setUserStoreClass(userStoreClass);
        realmConfig.setAuthorizationManagerClass(primaryRealm.getAuthorizationManagerClass());
        realmConfig.setEveryOneRoleName(UserCoreUtil.addDomainToName(primaryRealm.getEveryOneRoleName(),
                UserCoreConstants.INTERNAL_DOMAIN));
        realmConfig.setUserStoreProperties(userStoreProperties);
        realmConfig.setPasswordsExternallyManaged(passwordsExternallyManaged);
        realmConfig.setAuthzProperties(primaryRealm.getAuthzProperties());
        realmConfig.setRealmProperties(primaryRealm.getRealmProperties());
        realmConfig.setPasswordsExternallyManaged(primaryRealm.isPasswordsExternallyManaged());
        realmConfig.addMultipleCredentialProperties(userStoreClass, multipleCredentialsProperties);

        if (realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST) == null) {
            realmConfig.getUserStoreProperties().put(
                    UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST,
                    UserCoreConstants.RealmConfig.PROPERTY_VALUE_DEFAULT_MAX_COUNT);
        }

        if (realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY) == null) {
            realmConfig.getUserStoreProperties().put(
                    UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY,
                    UserCoreConstants.RealmConfig.PROPERTY_VALUE_DEFAULT_READ_ONLY);
        }

        return realmConfig;
    }

    private Map<String, String> getChildPropertyElements(OMElement omElement, SecretResolver secretResolver)
            throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        String domainName = "";
        try {
            AXIOMXPath xPath = new AXIOMXPath(UserCoreConstants.RealmConfig.DOMAIN_NAME_XPATH);
            OMElement val = (OMElement) xPath.selectSingleNode(omElement);
            if (val != null) {
                domainName = "." + val.getText();
            }
        } catch (Exception e) {
            log.debug("Error While getting DomainName from Configurations ");
        }

        Map<String, String> map = new HashMap<String, String>();
        Iterator<?> ite = omElement.getChildrenWithName(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_PROPERTY));
        boolean tokenProtected = false;
        while (ite.hasNext()) {
            OMElement propElem = (OMElement) ite.next();
            String propName = propElem.getAttributeValue(new QName(
                    UserCoreConstants.RealmConfig.ATTR_NAME_PROP_NAME));
            String propValue = propElem.getText();
            if (secretResolver != null && secretResolver.isInitialized()) {
                String alias = MiscellaneousUtil.getProtectedToken(propValue);
                if (StringUtils.isNotEmpty(alias) && secretResolver.isTokenProtected(alias)) {
                    propValue = secretResolver.resolve(alias);
                    tokenProtected = true;
                } else {
                    if (secretResolver.isTokenProtected("UserManager.Configuration.Property."
                            + propName + domainName)) {
                        propValue = secretResolver.resolve("UserManager.Configuration.Property."
                                + propName + domainName);
                        tokenProtected = true;
                    }
                    if (secretResolver.isTokenProtected("UserStoreManager.Property." + propName + domainName)) {
                        propValue = secretResolver.resolve("UserStoreManager.Property." + propName + domainName);
                        tokenProtected = true;
                    }
                }

            }
            if (!tokenProtected && propValue != null) {
                propValue = resolveEncryption(propElem);
            }
            tokenProtected = false;
            if (propName != null && propValue != null) {
                map.put(propName.trim(), propValue.trim());
            }

        }
        return map;
    }

    private Map<String, String> getMultipleCredentialsProperties(OMElement omElement) {
        Map<String, String> map = new HashMap<String, String>();
        OMElement multipleCredentialsEl = omElement.getFirstChildWithName(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_MULTIPLE_CREDENTIALS));
        if (multipleCredentialsEl != null) {
            Iterator<?> ite = multipleCredentialsEl
                    .getChildrenWithLocalName(UserCoreConstants.RealmConfig.LOCAL_NAME_CREDENTIAL);
            while (ite.hasNext()) {

                Object OMObj = ite.next();
                if (!(OMObj instanceof OMElement)) {
                    continue;
                }
                OMElement credsElem = (OMElement) OMObj;
                String credsType = credsElem.getAttributeValue(new QName(
                        UserCoreConstants.RealmConfig.ATTR_NAME_TYPE));
                String credsClassName = credsElem.getText();
                map.put(credsType.trim(), credsClassName.trim());
            }
        }
        return map;
    }

    /**
     * Read in realm element from config file
     *
     * @return
     * @throws XMLStreamException
     * @throws IOException
     * @throws org.wso2.micro.integrator.security.user.core.UserStoreException
     */
    private OMElement getRealmElement() throws XMLStreamException, IOException, UserStoreException {
        StAXOMBuilder builder = null;
        InputStream inStream = null;
        inStream = new FileInputStream(filePath);

        try {
            inStream = MicroIntegratorBaseUtils.replaceSystemVariablesInXml(inStream);
            builder = new StAXOMBuilder(inStream);
            OMElement documentElement = builder.getDocumentElement();
            setSecretResolver(documentElement);

            return documentElement;
        } catch (CarbonException e) {
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage(), e);
            }
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            inStream.close();
        }
    }

    public void setSecretResolver(OMElement rootElement) {
        secretResolver = SecretResolverFactory.create(rootElement, true);
    }

    /**
     * decrypts encrypted text value if the property element has the attribute encrypt="true"
     *
     * @param propElem Property OMElement
     * @return decrypted text value
     */
    private String resolveEncryption(OMElement propElem) throws org.wso2.micro.integrator.security.user.api.UserStoreException {
        String propValue = propElem.getText();
        if (propValue != null) {
            String secretPropName = propElem.getAttributeValue(new QName("encrypted"));
            if (secretPropName != null && secretPropName.equalsIgnoreCase("true")) {
                if (log.isDebugEnabled()) {
                    log.debug("Eligible to be decrypted=" + propElem.getAttributeValue(new QName(
                            UserCoreConstants.RealmConfig.ATTR_NAME_PROP_NAME)));
                }
                try {
                    propValue = decryptProperty(propValue);
                } catch (GeneralSecurityException e) {
                    String errMsg = "encryption of Property=" + propElem.getAttributeValue(
                            new QName(UserCoreConstants.RealmConfig.ATTR_NAME_PROP_NAME))
                            + " failed";
                    log.error(errMsg, e);
                }
            }
        }
        return propValue;
    }

    /**
     * Initializes and assign the keyStoreCipher only for the first time.
     */
    private static PrivateKey getPrivateKey() {
        CarbonServerConfigurationService serverConfigurationService = CarbonServerConfigurationService.getInstance();

        if (serverConfigurationService == null) {
            String message = "Key store initialization for decrypting secondary store failed due to" +
                    " serverConfigurationService is null while attempting to decrypt secondary store";
            log.error(message);
            return null;
        }

        InputStream in = null;

        // Get the encryption keystore.
        String encryptionKeyStore = serverConfigurationService.getFirstProperty(
                "Security.UserStorePasswordEncryption");

        String passwordXPath = "Security.KeyStore.Password";
        String keypassXPath = "Security.KeyStore.KeyPassword";
        String keyAliasXPath = "Security.KeyStore.KeyAlias";
        String locationXPath = "Security.KeyStore.Location";
        String typeXPath = "Security.KeyStore.Type";

        // If the encryption keystore is selected to be internal then select that keystore XPath.
        if ("InternalKeystore".equalsIgnoreCase(encryptionKeyStore)) {
            passwordXPath = "Security.InternalKeyStore.Password";
            keypassXPath = "Security.InternalKeyStore.KeyPassword";
            keyAliasXPath = "Security.InternalKeyStore.KeyAlias";
            locationXPath = "Security.InternalKeyStore.Location";
            typeXPath = "Security.InternalKeyStore.Type";

        }
        String password = serverConfigurationService.getFirstProperty(passwordXPath);
        String keyPass = serverConfigurationService.getFirstProperty(keypassXPath);
        String keyAlias = serverConfigurationService.getFirstProperty(keyAliasXPath);
        String provider = getJceProvider();
        KeyStore store;
        try {
            if (provider != null) {
                store = KeyStore.getInstance(serverConfigurationService.getFirstProperty(typeXPath), provider);
            } else {
                store = KeyStore.getInstance(serverConfigurationService.getFirstProperty(typeXPath));
            }
            String file = new File(serverConfigurationService.getFirstProperty(locationXPath)).getAbsolutePath();
            in = new FileInputStream(file);
            store.load(in, password.toCharArray());
            return (PrivateKey) store.getKey(keyAlias, keyPass.toCharArray());
        } catch (FileNotFoundException e) {
            String errorMsg = "Keystore File Not Found in configured location";
            log.error(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "Keystore File IO operation failed";
            log.error(errorMsg, e);
        } catch (KeyStoreException e) {
            String errorMsg = "Faulty keystore";
            log.error(errorMsg, e);
        } catch (GeneralSecurityException e) {
            String errorMsg = "Some parameters assigned to access the " +
                    "keystore is invalid";
            log.error(errorMsg, e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error("Error occurred while closing Registry key store file", e);
                }
            }
        }
        return null;
    }

    /**
     * Function to decrypt given cipher text
     *
     * @param propValue base64encoded ciphertext
     * @return plaintext
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws org.wso2.micro.integrator.security.user.api.UserStoreException
     * @throws InvalidKeyException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     */
    private String decryptProperty(String propValue)
            throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException,
            org.wso2.micro.integrator.security.user.api.UserStoreException, InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException {

        Cipher keyStoreCipher;
        String cipherTransformation = System.getProperty(CIPHER_TRANSFORMATION_SYSTEM_PROPERTY);
        byte[] cipherTextBytes = Base64.decode(propValue.trim());

        privateKey = (privateKey == null) ? getPrivateKey() : privateKey;
        if (privateKey == null) {
            throw new org.wso2.micro.integrator.security.user.api.UserStoreException(
                    "Private key initialization failed. Cannot decrypt the userstore password.");
        }
        String provider = getJceProvider();
        if(cipherTransformation != null) {
            // extract the original cipher if custom transformation is used configured in carbon.properties.
            CipherHolder cipherHolder = cipherTextToCipherHolder(cipherTextBytes);
            if (cipherHolder != null) {
                // cipher with meta data.
                if (log.isDebugEnabled()) {
                    log.debug("Cipher transformation for decryption : " + cipherHolder.getTransformation());
                }
                if (provider != null) {
                    keyStoreCipher = Cipher.getInstance(cipherHolder.getTransformation(), provider);
                } else {
                    keyStoreCipher = Cipher.getInstance(cipherHolder.getTransformation());
                }
                cipherTextBytes = cipherHolder.getCipherBase64Decoded();
            } else {
                // If the ciphertext is not a self-contained, directly decrypt using transformation configured in
                // carbon.properties file
                if (provider != null) {
                    keyStoreCipher = Cipher.getInstance(cipherTransformation, provider);
                } else {
                    keyStoreCipher = Cipher.getInstance(cipherTransformation);
                }
            }
        } else {
            // If reach here, user have removed org.wso2.CipherTransformation property or carbon.properties file
            // hence RSA is considered as default transformation
            if (provider != null) {
                keyStoreCipher = Cipher.getInstance("RSA", provider);
            } else {
                keyStoreCipher = Cipher.getInstance("RSA");
            }
        }
        keyStoreCipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(keyStoreCipher.doFinal(cipherTextBytes), Charset.defaultCharset());
    }

    /**
     * Function to convert cipher byte array to {@link CipherHolder}.
     *
     * @param cipherText cipher text as a byte array
     * @return if cipher text is not a cipher with meta data
     */
    private CipherHolder cipherTextToCipherHolder(byte[] cipherText) {

        String cipherStr = new String(cipherText, Charset.defaultCharset());
        try {
            return gson.fromJson(cipherStr, CipherHolder.class);
        } catch (JsonSyntaxException e) {
            if (log.isDebugEnabled()) {
                log.debug("Deserialization failed since cipher string is not representing cipher with metadata");
            }
            return null;
        }
    }

    /**
        * Get the JCE provider to be used.
        *
        * @return JCE provider
        */
    private static String getJceProvider() {
        String provider = System.getProperty(SECURITY_JCE_PROVIDER);
        if (provider != null && (provider.equalsIgnoreCase(BOUNCY_CASTLE_FIPS_PROVIDER) ||
                provider.equalsIgnoreCase(BOUNCY_CASTLE_PROVIDER))) {
            return provider;
        }
        return null;
    }

    /**
     * Holds encrypted cipher with related metadata.
     *
     * IMPORTANT: this is copy of org.wso2.carbon.core.util.CipherHolder, what ever changes applied here need to update
     *              on above
     */
    private class CipherHolder {

        // Base64 encoded ciphertext.
        private String c;

        // Transformation used for encryption, default is "RSA".
        private String t = "RSA";

        // Thumbprint of the certificate.
        private String tp;

        // Digest used to generate certificate thumbprint.
        private String tpd;


        public String getTransformation() {
            return t;
        }

        public void setTransformation(String transformation) {
            this.t = transformation;
        }

        public String getCipherText() {
            return c;
        }

        public byte[] getCipherBase64Decoded() {
            return Base64.decode(c);
        }

        public void setCipherText(String cipher) {
            this.c = cipher;
        }

        public String getThumbPrint() {
            return tp;
        }

        public void setThumbPrint(String tp) {
            this.tp = tp;
        }

        public String getThumbprintDigest() {
            return tpd;
        }

        public void setThumbprintDigest(String digest) {
            this.tpd = digest;
        }

        /**
         * Function to base64 encode ciphertext and set ciphertext
         * @param cipher
         */
        public void setCipherBase64Encoded(byte[] cipher) {
            this.c = Base64.encode(cipher);
        }

        /**
         * Function to set thumbprint
         * @param tp thumb print
         * @param digest digest (hash algorithm) used for to create thumb print
         */
        public void setThumbPrint(String tp, String digest) {
            this.tp = tp;
            this.tpd = digest;
        }

        @Override
        public String toString() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }
}
