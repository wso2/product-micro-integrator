/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.security.util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.CredentialException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.X509NameTokenizer;
import org.wso2.micro.core.Constants;
import org.wso2.micro.core.util.KeyStoreManager;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * ServerCrypto implementation to support a collection of keystores holding different trusted certs
 * and CA certs
 */
public class ServerCrypto implements Crypto {

    public final static String PROP_ID_KEY_STORE = "org.wso2.carbon.security.crypto.keystore";
    public final static String PROP_ID_PRIVATE_STORE = "org.wso2.carbon.security.crypto.privatestore";
    public final static String PROP_ID_TRUST_STORES = "org.wso2.carbon.security.crypto.truststores";
    public final static String PROP_ID_CERT_PROVIDER = "org.wso2.carbon.security.crypto.cert.provider";
    public final static String PROP_ID_DEFAULT_ALIAS = "org.wso2.carbon.security.crypto.alias";
    public final static String PROP_ID_REGISTRY = "org.wso2.carbon.security.crypto.registry";
    public final static String PROP_ID_CACERT_PASS = "org.wso2.carbon.security.crypto.cacert.pass";
    public final static String PROP_ID_XKMS_SERVICE_PASS_PHRASE = "org.wso2.wsas.security.wso2wsas.crypto.xkms.pass";
    public final static String PROP_ID_TENANT_ID = "org.wso2.stratos.tenant.id";
    public final static String PROP_ID_XKMS_SERVICE_URL = "org.wso2.carbon.security.crypto.xkms.url";
    public static final String BOUNCY_CASTLE_PROVIDER = "BC";
    public static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
    public static final String SECURITY_JCE_PROVIDER = "security.jce.provider";
    private static final String SKI_OID = "2.5.29.14";
    private static Log log = LogFactory.getLog(ServerCrypto.class);
    private static CertificateFactory certFact = null;
    private Properties properties = null;
    private KeyStore keystore = null;
    private KeyStore cacerts = null;
    private List<KeyStore> trustStores = new ArrayList<>();
    private Boolean useXkms;

    public ServerCrypto(Properties prop) throws CredentialException, IOException {
        this(prop, ServerCrypto.class.getClassLoader());
    }

    public ServerCrypto(Properties prop, ClassLoader loader) throws CredentialException,
            IOException {
        boolean isSetDoomFalse = false;
        String provider = getJceProvider();
        try {

            int tenantId = Constants.SUPER_TENANT_ID;

            this.properties = prop;

            KeyStoreManager keyMan = KeyStoreManager.getInstance(tenantId);
            String ksId = this.properties.getProperty(PROP_ID_PRIVATE_STORE);
            if (ksId != null) {
                this.keystore = keyMan.getKeyStore(ksId);
            }

            // Get other keystores if available
            String trustStoreIds = this.properties.getProperty(PROP_ID_TRUST_STORES);
            if (trustStoreIds != null && trustStoreIds.trim().length() != 0) {
                String[] ids = trustStoreIds.trim().split(",");
                this.trustStores = new ArrayList(ids.length);
                for (int i = 0; i < ids.length; i++) {
                    String id = ids[i];
                    KeyStore tstks = keyMan.getKeyStore(id);
                    this.trustStores.add(i, tstks);
                }
            }
        } catch (Exception e) {
            log.error("error creating ServerCryto", e);
            throw new CredentialException(3, "secError00", e);
        }

        /**
         * Load cacerts
         */
        String cacertsPath = System.getProperty("java.home") + "/lib/security/cacerts";
        String type;
        if (provider != null && provider.equalsIgnoreCase(BOUNCY_CASTLE_FIPS_PROVIDER)) {
            type = prop.getProperty("org.wso2.carbon.security.crypto.type", "BCFKS");
        } else {
            type = prop.getProperty("org.wso2.carbon.security.crypto.type", KeyStore.getDefaultType());
        }
        try (InputStream cacertsIs = Files.newInputStream(Paths.get(cacertsPath))) {
            String cacertsPasswd = properties.getProperty(PROP_ID_CACERT_PASS, "changeit");
            if (provider != null) {
                cacerts = KeyStore.getInstance(type, provider);
            } else {
                cacerts = KeyStore.getInstance(type);
            }
            cacerts.load(cacertsIs, cacertsPasswd.toCharArray());

        } catch (GeneralSecurityException e) {
            log.warn("Unable load to cacerts from the JDK.", e);
            if (CollectionUtils.isNotEmpty(trustStores)) {
                cacerts = this.trustStores.get(0);
            } else {
                throw new CredentialException(3, "secError00", e);
            }
        }

    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#loadCertificate(java.io.InputStream)
     */
    public X509Certificate loadCertificate(InputStream in) throws WSSecurityException {
        X509Certificate cert;
        try {
            cert = (X509Certificate) getCertificateFactory().generateCertificate(in);
        } catch (CertificateException e) {
            throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                    "parseError");
        }
        return cert;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getX509Certificates(byte[], boolean)
     */
    public X509Certificate[] getX509Certificates(byte[] data, boolean reverse)
            throws WSSecurityException {
        InputStream in = new ByteArrayInputStream(data);
        CertPath path;
        try {
            path = getCertificateFactory().generateCertPath(in);
        } catch (CertificateException e) {
            throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                    "parseError");
        }
        List l = path.getCertificates();
        X509Certificate[] certs = new X509Certificate[l.size()];
        Iterator iterator = l.iterator();
        for (int i = 0; i < l.size(); i++) {
            certs[reverse ? (l.size() - 1 - i) : i] = (X509Certificate) iterator.next();
        }
        return certs;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getCertificateData(boolean,
     * java.security.cert.X509Certificate[])
     */
    public byte[] getCertificateData(boolean reverse, X509Certificate[] certs)
            throws WSSecurityException {
        Vector list = new Vector();
        for (int i = 0; i < certs.length; i++) {
            if (reverse) {
                list.insertElementAt(certs[i], 0);
            } else {
                list.add(certs[i]);
            }
        }
        try {
            CertPath path = getCertificateFactory().generateCertPath(list);
            return path.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                    "encodeError");
        } catch (CertificateException e) {
            throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                    "parseError");
        }
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getPrivateKey(java.lang.String,
     * java.lang.String)
     */
    public PrivateKey getPrivateKey(String alias, String password) throws Exception {
        if (alias == null) {
            throw new Exception("alias is null");
        }
        boolean b = keystore.isKeyEntry(alias);

        if (!b) {
            log.error("Cannot find key for alias: " + alias);
            throw new Exception("Cannot find key for alias: " + alias);
        }

        Key keyTmp = keystore.getKey(alias, password.toCharArray());

        if (!(keyTmp instanceof PrivateKey)) {
            throw new Exception("Key is not a private key, alias: " + alias);
        }

        return (PrivateKey) keyTmp;
    }

    @Override
    /**
     * This first looks into the primary keystore and then looks at the other trust stores
     *
     * @see org.apache.ws.security.components.crypto.Crypto#getCertificates(String)
     */
    public X509Certificate[] getCertificates(String alias) throws WSSecurityException {

        Certificate[] certs = new Certificate[0];
        Certificate cert = null;
        try {
            if (this.keystore != null) {
                // There's a chance that there can only be a set of trust stores
                certs = keystore.getCertificateChain(alias);
                if (certs == null || certs.length == 0) {
                    // no cert chain, so lets check if getCertificate gives us a
                    // result.
                    cert = keystore.getCertificate(alias);
                }
            }

            if (certs == null && cert == null && this.trustStores != null) {
                // Now look into the trust stores
                Iterator trustStoreIter = this.trustStores.iterator();
                while (trustStoreIter.hasNext()) {
                    KeyStore store = (KeyStore) trustStoreIter.next();
                    certs = store.getCertificateChain(alias);
                    if (certs != null) {
                        break; // found the certs
                    } else {
                        cert = store.getCertificate(alias);
                    }
                }
            }

            if (certs == null && cert == null && this.cacerts != null) {
                // There's a chance that there can only be a set of ca store
                certs = cacerts.getCertificateChain(alias);
                if (certs == null || certs.length == 0) {
                    // no cert chain, so lets check if getCertificate gives us a
                    // result.
                    cert = cacerts.getCertificate(alias);
                }
            }

            if (cert != null) {
                certs = new Certificate[]{cert};
            } else if (certs == null) {

                // At this pont we don't have certs or a cert
                return new X509Certificate[0];
            }
        } catch (KeyStoreException e) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "keystore");
        }

        X509Certificate[] x509certs = new X509Certificate[0];
        if (certs != null) {
            x509certs = new X509Certificate[certs.length];
            for (int i = 0; i < certs.length; i++) {
                x509certs[i] = (X509Certificate) certs[i];
            }
        }
        return x509certs;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getAliasForX509Cert(java.security.cert.Certificate)
     */
    public String getAliasForX509Cert(Certificate cert) throws WSSecurityException {
        try {
            String alias = null;

            if (this.keystore != null) {
                alias = keystore.getCertificateAlias(cert);

                // Use brute force search
                if (alias == null) {
                    alias = findAliasForCert(this.keystore, cert);
                }
            }

            // Check the trust stores
            if (alias == null && this.trustStores != null) {
                for (Iterator trustStoreIter = this.trustStores.iterator(); trustStoreIter
                        .hasNext(); ) {
                    KeyStore store = (KeyStore) trustStoreIter.next();
                    alias = store.getCertificateAlias(cert);
                    if (alias != null) {
                        break;
                    }
                }
            }

            // Use brute force search on the trust stores
            if (alias == null && this.trustStores != null) {
                for (Iterator trustStoreIter = this.trustStores.iterator(); trustStoreIter
                        .hasNext(); ) {
                    KeyStore store = (KeyStore) trustStoreIter.next();
                    alias = this.findAliasForCert(store, cert);
                    if (alias != null) {
                        break;
                    }
                }
            }

            if (alias == null && this.cacerts != null) {
                alias = cacerts.getCertificateAlias(cert);

                // Use brute force search
                if (alias == null) {
                    alias = findAliasForCert(this.cacerts, cert);
                }
            }

            if (alias != null) {
                return alias;
            }

        } catch (KeyStoreException e) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "keystore");
        }

        return null;
    }

    private String findAliasForCert(KeyStore ks, Certificate cert) throws KeyStoreException {
        Enumeration e = ks.aliases();
        while (e.hasMoreElements()) {
            String alias = (String) e.nextElement();
            X509Certificate cert2 = (X509Certificate) ks.getCertificate(alias);
            if (cert2.equals(cert)) {
                return alias;
            }
        }
        return null;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getAliasForX509Cert(java.lang.String)
     */
    public String getAliasForX509Cert(String issuer) throws WSSecurityException {
        String alias = getAliasForX509Cert(issuer, null, false, this.keystore);
        if (alias == null) {
            Iterator<KeyStore> ite = this.trustStores.iterator();
            while (ite.hasNext()) {
                KeyStore ks = ite.next();
                alias = getAliasForX509Cert(issuer, null, false, ks);
                if (alias != null) {
                    break;
                }
            }

        }
        return alias;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getAliasForX509Cert(java.lang.String,
     * java.math.BigInteger)
     */
    public String getAliasForX509Cert(String issuer, BigInteger serialNumber)
            throws WSSecurityException {
        String alias = getAliasForX509Cert(issuer, serialNumber, true, this.keystore);
        if (alias == null) {
            Iterator<KeyStore> ite = this.trustStores.iterator();
            while (ite.hasNext()) {
                KeyStore ks = ite.next();
                alias = getAliasForX509Cert(issuer, serialNumber, true, ks);
                if (alias != null) {
                    break;
                }
            }
        }
        return alias;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getAliasForX509Cert(byte[])
     */
    public String getAliasForX509Cert(byte[] skiBytes) throws WSSecurityException {
        try {

            Certificate cert;
            for (Enumeration e = keystore.aliases(); e.hasMoreElements(); ) {
                String alias = (String) e.nextElement();
                Certificate[] certs = this.getCertificates(alias);
                if (certs == null || certs.length == 0) {
                    return null;
                } else {
                    cert = certs[0];
                }
                if (!(cert instanceof X509Certificate)) {
                    continue;
                }
                byte[] data = getSKIBytesFromCert((X509Certificate) cert);
                if (data.length != skiBytes.length) {
                    continue;
                }
                if (Arrays.equals(data, skiBytes)) {
                    return alias;
                }
            }

        } catch (KeyStoreException e) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "keystore");
        }
        return null;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getDefaultX509Alias()
     */
    public String getDefaultX509Alias() {
        return this.properties.getProperty(PROP_ID_DEFAULT_ALIAS);
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getSKIBytesFromCert(java.security.cert.X509Certificate)
     */
    public byte[] getSKIBytesFromCert(X509Certificate cert) throws WSSecurityException {
        /*
         * Gets the DER-encoded OCTET string for the extension value (extnValue)
         * identified by the passed-in oid String. The oid string is represented
         * by a set of positive whole numbers separated by periods.
         */
        byte[] derEncodedValue = cert.getExtensionValue(SKI_OID);

        if (cert.getVersion() < 3 || derEncodedValue == null) {
            PublicKey key = cert.getPublicKey();
            if (!(key instanceof RSAPublicKey)) {
                throw new WSSecurityException(1, "noSKIHandling",
                        new Object[]{"Support for RSA key only"});
            }
            byte[] encoded = key.getEncoded();
            // remove 22-byte algorithm ID and header
            byte[] value = new byte[encoded.length - 22];
            System.arraycopy(encoded, 22, value, 0, value.length);
            MessageDigest sha;
            String provider = getJceProvider();
            try {
                if (provider != null) {
                    sha = MessageDigest.getInstance("SHA-256", provider);
                } else {
                    sha = MessageDigest.getInstance("SHA-1");
                }
            } catch (NoSuchAlgorithmException ex) {
                throw new WSSecurityException(1, "noSKIHandling",
                        new Object[]{"Wrong certificate version (<3) and no "
                                + "SHA1 message digest availabe"});
            } catch (NoSuchProviderException e) {
                throw new WSSecurityException("Specified security provider is not available in this environment: ", e);
            }
            sha.reset();
            sha.update(value);
            return sha.digest();
        }

        /**
         * Strip away first four bytes from the DerValue (tag and length of
         * ExtensionValue OCTET STRING and KeyIdentifier OCTET STRING)
         */
        byte abyte0[] = new byte[derEncodedValue.length - 4];

        System.arraycopy(derEncodedValue, 4, abyte0, 0, abyte0.length);
        return abyte0;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getAliasForX509CertThumb(byte[])
     */
    public String getAliasForX509CertThumb(byte[] thumb) throws WSSecurityException {
        Certificate cert;
        MessageDigest sha;
        String provider = getJceProvider();
        try {
            if (provider != null) {
                sha = MessageDigest.getInstance("SHA-256", provider);
            } else {
                sha = MessageDigest.getInstance("SHA-1");
            }
        } catch (NoSuchAlgorithmException e1) {
            throw new WSSecurityException(0, "noSHA1availabe");
        } catch (NoSuchProviderException e) {
            throw new WSSecurityException("Specified security provider is not available in this environment: ", e);
        }
        try {
            for (Enumeration e = keystore.aliases(); e.hasMoreElements(); ) {
                String alias = (String) e.nextElement();
                Certificate[] certs = this.getCertificates(alias);
                if (certs == null || certs.length == 0) {
                    return null;
                } else {
                    cert = certs[0];
                }
                if (!(cert instanceof X509Certificate)) {
                    continue;
                }
                sha.reset();
                try {
                    sha.update(cert.getEncoded());
                } catch (CertificateEncodingException e1) {
                    throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                            "encodeError");
                }
                byte[] data = sha.digest();

                if (Arrays.equals(data, thumb)) {
                    return alias;
                }
            }
        } catch (KeyStoreException e) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "keystore");
        }
        return null;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getKeyStore()
     */
    public KeyStore getKeyStore() {
        return this.keystore;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getCertificateFactory()
     */
    public CertificateFactory getCertificateFactory() throws WSSecurityException {
        if (certFact == null) {
            try {
                String provider = properties.getProperty(PROP_ID_CERT_PROVIDER);
                if (provider == null) {
                    provider = getJceProvider();
                }
                if (provider == null || provider.length() == 0) {
                    certFact = CertificateFactory.getInstance("X.509");
                } else {
                    certFact = CertificateFactory.getInstance("X.509", provider);
                }
            } catch (CertificateException e) {
                throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                        "unsupportedCertType");
            } catch (NoSuchProviderException e) {
                throw new WSSecurityException(WSSecurityException.SECURITY_TOKEN_UNAVAILABLE,
                        "noSecProvider");
            }
        }
        return certFact;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#validateCertPath(java.security.cert.X509Certificate[])
     */
    public boolean validateCertPath(X509Certificate[] certs) throws WSSecurityException {

        boolean result;

        result = this.validateCertPath(this.keystore, certs);

        if (!result) {
            Iterator trustStoreIter = this.trustStores.iterator();
            while (!result) {
                result = this.validateCertPath((KeyStore) trustStoreIter.next(), certs);
            }
        }

        if (!result && cacerts != null) {
            result = this.validateCertPath(this.cacerts, certs);
        }

        return result;
    }

    @Override
    /**
     * @see org.apache.ws.security.components.crypto.Crypto#getAliasesForDN(java.lang.String)
     */
    public String[] getAliasesForDN(String subjectDN) throws WSSecurityException {

        // Store the aliases found
        Vector aliases = new Vector();
        Certificate cert;

        // The DN to search the keystore for
        Vector subjectRDN = splitAndTrim(subjectDN);

        // Look at every certificate in the keystore
        try {
            for (Enumeration e = keystore.aliases(); e.hasMoreElements(); ) {
                String alias = (String) e.nextElement();

                Certificate[] certs = this.getCertificates(alias);
                if (certs == null || certs.length == 0) {
                    return new String[0];
                } else {
                    cert = certs[0];
                }
                if (cert instanceof X509Certificate) {
                    Vector foundRDN = splitAndTrim(((X509Certificate) cert).getSubjectDN()
                            .getName());

                    if (subjectRDN.equals(foundRDN)) {
                        aliases.add(alias);
                    }
                }
            }
        } catch (KeyStoreException e) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "keystore");
        }

        // Convert the vector into an array
        String[] result = new String[aliases.size()];
        for (int i = 0; i < aliases.size(); i++) {
            result[i] = (String) aliases.elementAt(i);
        }
        return result;

    }

    private String getAliasForX509Cert(String issuer, BigInteger serialNumber,
                                       boolean useSerialNumber, KeyStore ks) throws WSSecurityException {
        Vector issuerRDN = splitAndTrim(issuer);
        X509Certificate x509cert;
        Vector certRDN;
        Certificate cert;
        try {
            for (Enumeration e = ks.aliases(); e.hasMoreElements(); ) {
                String alias = (String) e.nextElement();
                Certificate[] certs = this.getCertificates(alias);

                if (certs == null || certs.length == 0) {
                    return null;
                } else {
                    cert = certs[0];
                }
                if (!(cert instanceof X509Certificate)) {
                    continue;
                }
                x509cert = (X509Certificate) cert;
                if (useSerialNumber && x509cert.getSerialNumber().compareTo(serialNumber) == 0) {
                    certRDN = splitAndTrim(x509cert.getIssuerDN().getName());
                    if (certRDN.equals(issuerRDN)) {
                        return alias;
                    }
                }
            }
        } catch (KeyStoreException e) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "keystore");
        }
        return null;
    }

    private Vector splitAndTrim(String inString) {
        X509NameTokenizer nmTokens = new X509NameTokenizer(inString);
        Vector vr = new Vector();

        while (nmTokens.hasMoreTokens()) {
            vr.add(nmTokens.nextToken());
        }
        java.util.Collections.sort(vr);
        return vr;
    }

    private boolean validateCertPath(KeyStore ks, Certificate[] certs) throws WSSecurityException {

        try {

            // Generate cert path
            java.util.List certList = java.util.Arrays.asList(certs);
            CertPath path = this.getCertificateFactory().generateCertPath(certList);

            // Use the certificates in the keystore as TrustAnchors
            PKIXParameters param = new PKIXParameters(ks);

            // Do not check a revocation list
            param.setRevocationEnabled(false);

            // Verify the trust path using the above settings
            String provider = properties
                    .getProperty("org.apache.ws.security.crypto.merlin.cert.provider");
            CertPathValidator certPathValidator;
            if (provider == null) {
                provider = getJceProvider();
            }
            if (provider == null || provider.length() == 0) {
                certPathValidator = CertPathValidator.getInstance("PKIX");
            } else {
                certPathValidator = CertPathValidator.getInstance("PKIX", provider);
            }
            certPathValidator.validate(path, param);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | CertificateException |
                InvalidAlgorithmParameterException | CertPathValidatorException | KeyStoreException ex) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "certpath",
                    new Object[]{ex.getMessage()}, ex);
        }
        return true;
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
