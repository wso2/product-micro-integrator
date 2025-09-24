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
package org.wso2.micro.integrator.dataservices.core.auth;

import org.apache.axiom.util.base64.Base64Utils;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.core.util.KeyStoreManager;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.core.Constants;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is the implementation for JWT based authorization role retrieval.
 */
public class JWTAuthorizationProvider implements AuthorizationProvider {
    private static final Log log = LogFactory.getLog(
            JWTAuthorizationProvider.class);
    private static final String HTTP_SERVLET_REQUEST = "transport.http.servletRequest";
    private static final String JWT_TOKEN_HEADER_NAME = "X-JWT-Assertion";
    private static final String UTF_8_ENCODING = "UTF-8";
    private String endUserClaim = null;
    private static final String ENDUSER_CLAIM = "http://wso2.org/claims/enduser";
    private static final String ENDUSER_CLAIM_PROPERTY_KEY = "claimUri";
    //This is the string constant that separates the claim from the value.
    private static final String CLAIM_VALUE_SEPARATOR = "\":\"";
    private static final String ESCAPED_DOUBLE_QUOTATION = "\"";
    private static final String USERNAME = "username";

    private static ConcurrentHashMap<KeyStore, Certificate> publicCerts = new ConcurrentHashMap<KeyStore, Certificate>();
    private static ConcurrentHashMap<Integer, KeyStore> keyStores = new ConcurrentHashMap<Integer, KeyStore>();

    @Override
    public String[] getUserRoles(MessageContext msgContext) throws DataServiceFault {
        return DBUtils.getUserRoles(getUsername(msgContext)); //need to retrieve from msgcontext
    }

    @Override
    public String[] getAllRoles() throws DataServiceFault {
        int tenantId = Constants.SUPER_TENANT_ID;
        return DBUtils.getAllRoles(tenantId);
    }

    @Override
    public String getUsername(MessageContext msgContext) throws DataServiceFault {
        try {
            return extractUsernameFromJWT(msgContext);
        } catch (UnsupportedEncodingException e) {
            log.debug("Error in retrieving user name from message context - " + e.getMessage(), e);
            throw new DataServiceFault(e, "Error in retrieving user name from message context - " + e.getMessage());
        } catch (AxisFault axisFault) {
            log.debug("Error in retrieving user name from message context - " + axisFault.getMessage(), axisFault);
            throw new DataServiceFault(axisFault, "Error in retrieving user name from message context - "
                                                  + axisFault.getMessage());
        }
    }

    @Override
    public void init(Map<String, String> authorizationProps) throws DataServiceFault {
        endUserClaim = authorizationProps.get(ENDUSER_CLAIM_PROPERTY_KEY);
    }

    /**
     * This method gets the JWT token from the transport header, and extracts the user name from the JWT and
     * sets it to the message context.
     * Example Usage - is to enable user name token security in DSS and use the JWT token sent from APIM to
     * get the roles of the user in order to utilize the content filtering feature of DSS.
     * @param msgContext
     */
    private String extractUsernameFromJWT(MessageContext msgContext) throws UnsupportedEncodingException, AxisFault {
        if (endUserClaim == null || endUserClaim.isEmpty()) {
            endUserClaim = ENDUSER_CLAIM;
        }
        HttpServletRequest obj = (HttpServletRequest) msgContext.
                getProperty(HTTP_SERVLET_REQUEST);

        if (obj != null) {
            //Get the JWT token from the header.
            String jwt = obj.getHeader(JWT_TOKEN_HEADER_NAME);

            if (jwt != null && validateSignature(jwt)) {

                String jwtToken = null;

                //Decode the JWT token.
                jwtToken = new String(org.apache.axiom.om.util.Base64.decode(jwt), UTF_8_ENCODING);

                if (jwtToken != null) {
                    //Extract the end user claim.
                    String[] tempStr4 = jwtToken.split(endUserClaim + CLAIM_VALUE_SEPARATOR);
                    String[] decoded = tempStr4[1].split(ESCAPED_DOUBLE_QUOTATION);
                    System.out.println("tempStr4= " + tempStr4.toString());
                    System.out.println("decoded=" + decoded.toString());
                    //Set username to message context.
                    return decoded[0];
                }
            }
        }
        return null;
    }

    /***
     * Validates the signature of the JWT token.
     * @param signedJWTToken
     * @return
     * @throws AxisFault
     */
    private Boolean validateSignature(String signedJWTToken) throws AxisFault{

        //verify signature
        boolean isVerified = false;
        String[] split_string = signedJWTToken.split("\\.");
        String base64EncodedHeader = split_string[0];
        String base64EncodedBody = split_string[1];
        String base64EncodedSignature = split_string[2];

        String decodedHeader = new String(Base64Utils.decode(base64EncodedHeader));
        byte[] decodedSignature = Base64Utils.decode(base64EncodedSignature);
        Pattern pattern = Pattern.compile("^[^:]*:[^:]*:[^:]*:\"(.+)\"}$");
        Matcher matcher = pattern.matcher(decodedHeader);
        String base64EncodedCertThumb = null;
        if(matcher.find()){
            base64EncodedCertThumb = matcher.group(1);
        }
        byte[] decodedCertThumb = Base64Utils.decode(base64EncodedCertThumb);

        KeyStore keystore = getKeyStore();
        Certificate publicCert = null;
        if(keystore != null){
            publicCert = publicCerts.get(keystore);

            if(publicCert == null){
                String alias = getAliasForX509CertThumb(decodedCertThumb, keystore);
                try {
                    publicCert = keystore.getCertificate(alias);
                }catch (KeyStoreException e) {
                    throw new AxisFault("Error getting public certificate from keystore using alias");
                }
            }
        }else{
            throw new AxisFault("No keystore found");
        }
        if(publicCert != null){
            try{
                //Create signature instance with signature algorithm and public cert, to verify the signature.
                Signature verifySig = null;
                String providerName = DBUtils.getPreferredJceProvider();
                if (providerName != null) {
                    verifySig = Signature.getInstance("SHA256withRSA", providerName);
                } else {
                    verifySig = Signature.getInstance("SHA256withRSA");
                }
                verifySig.initVerify(publicCert);
                //Update signature with signature data.
                verifySig.update((base64EncodedHeader+"."+base64EncodedBody).getBytes());
                isVerified = verifySig.verify(decodedSignature);
            }catch (NoSuchAlgorithmException e) {
                throw new AxisFault("SHA256withRSA cannot be found");
            }catch (InvalidKeyException e) {
                throw new AxisFault("Invalid Key");
            } catch (SignatureException e) {
                throw new AxisFault("Signature Object not initialized properly");
            } catch (NoSuchProviderException e) {
                throw new AxisFault("Specified security provider is not available in this environment: ", e);
            }
        }else{
            throw new AxisFault("No public cert found");
        }
        if(!isVerified){
            throw new AxisFault("Signature validation failed");
        }
        return isVerified;
    }

    /**
     * Gets the key store for the tenant.
     * @return KeyStore
     */
    private KeyStore getKeyStore() throws AxisFault{

        //get tenant domain
        String tenantDomain = Constants.SUPER_TENANT_DOMAIN_NAME;
        //get tenantId
        int tenantId = Constants.SUPER_TENANT_ID;
        KeyStore keyStore = keyStores.get(tenantId);

        if(keyStore == null){
            //get tenant's key store manager
            KeyStoreManager tenantKSM = KeyStoreManager.getInstance(tenantId);
            try {
                if(!tenantDomain.equals(Constants.SUPER_TENANT_DOMAIN_NAME)){
                    //derive key store name
                    String ksName = tenantDomain.trim().replace(".", "-");
                    String jksName = ksName + ".jks";
                    keyStore = tenantKSM.getKeyStore(jksName);
                }else{
                    keyStore = tenantKSM.getPrimaryKeyStore();
                }
            }catch (Exception e) {
                throw new AxisFault("Error getting keystore");
            }
        }
        return keyStore;
    }

    /**
     * Get the alias for the X509 certificate thumb
     * @param thumb
     * @param keyStore
     * @return
     * @throws AxisFault
     */
    private String getAliasForX509CertThumb(byte[] thumb, KeyStore keyStore) throws AxisFault {
        Certificate cert = null;
        MessageDigest sha = null;

        try {
            String providerName = DBUtils.getPreferredJceProvider();
            if (providerName != null) {
                sha = MessageDigest.getInstance("SHA-256", providerName);
            } else {
                sha = MessageDigest.getInstance("SHA-1");
            }
        } catch (NoSuchAlgorithmException e1) {
            throw new AxisFault("noSHA1availabe", e1);
        } catch (NoSuchProviderException e) {
            throw new AxisFault("Specified security provider is not available in this environment: ", e);
        }
        try {
            for (Enumeration<String> e = keyStore.aliases(); e.hasMoreElements();) {
                String alias = (String) e.nextElement();
                Certificate[] certs = keyStore.getCertificateChain(alias);
                if (certs == null || certs.length == 0) {
                    // no cert chain, so lets check if getCertificate gives us a result.
                    cert = keyStore.getCertificate(alias);
                    if (cert == null) {
                        return null;
                    }
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
                    throw new AxisFault("Error encoding certificate");
                }
                byte[] data = sha.digest();
                if (new String(thumb).equals(hexify(data))) {
                    return alias;
                }
            }
        } catch (KeyStoreException e) {
            throw new AxisFault("KeyStore exception while getting alias for X509CertThumb");
        }
        return null;
    }

    /**
     * Converts the byte array to hex string
     * @param bytes
     * @return
     */
    private String hexify(byte bytes[]) {

        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
                            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

        StringBuffer buf = new StringBuffer(bytes.length * 2);

        for (int i = 0; i < bytes.length; ++i) {
            buf.append(hexDigits[(bytes[i] & 0xf0) >> 4]);
            buf.append(hexDigits[bytes[i] & 0x0f]);
        }

        return buf.toString();
    }
}
