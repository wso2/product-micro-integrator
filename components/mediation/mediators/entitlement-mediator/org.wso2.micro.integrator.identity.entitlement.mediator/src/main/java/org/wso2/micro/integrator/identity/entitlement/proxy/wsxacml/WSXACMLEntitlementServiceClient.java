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

package org.wso2.micro.integrator.identity.entitlement.proxy.wsxacml;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.transport.http.HttpTransportProperties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.util.SecurityManager;
import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.joda.time.DateTime;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.impl.IssuerBuilder;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.xacml.ctx.RequestType;
import org.opensaml.xacml.ctx.ResponseType;
import org.opensaml.xacml.profile.saml.XACMLAuthzDecisionQueryType;
import org.opensaml.xacml.profile.saml.XACMLAuthzDecisionStatementType;
import org.opensaml.xacml.profile.saml.impl.XACMLAuthzDecisionQueryTypeImplBuilder;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilder;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.opensaml.xmlsec.signature.support.Signer;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.wso2.carbon.identity.saml.common.util.SAMLInitializer;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.identity.entitlement.proxy.AbstractEntitlementServiceClient;
import org.wso2.micro.integrator.identity.entitlement.proxy.Attribute;
import org.wso2.micro.integrator.identity.entitlement.proxy.XACMLRequetBuilder;
import org.wso2.micro.integrator.identity.entitlement.proxy.exception.EntitlementProxyException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class WSXACMLEntitlementServiceClient extends AbstractEntitlementServiceClient {

    private static final Log log = LogFactory.getLog(WSXACMLEntitlementServiceClient.class);
    private static final int ENTITY_EXPANSION_LIMIT = 0;
    public static final String ISSUER_URL = "https://identity.carbon.wso2.org";
    public static final String DOCUMENT_BUILDER_FACTORY = "javax.xml.parsers.DocumentBuilderFactory";
    public static final String DOCUMENT_BUILDER_FACTORY_IMPL = "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl";
    private static boolean isBootStrapped = false;
    public static final String URN_OASIS_NAMES_TC_XACML_2_0_CONTEXT_SCHEMA_OS = "urn:oasis:names:tc:xacml:2.0:context:schema:os";
    private static final String BOUNCY_CASTLE_PROVIDER = "BC";
    private static final String BOUNCY_CASTLE_FIPS_PROVIDER = "BCFIPS";
    private static final String SECURITY_JCE_PROVIDER = "security.jce.provider";

    private static OMNamespace xacmlContextNS = OMAbstractFactory.getOMFactory()
            .createOMNamespace(URN_OASIS_NAMES_TC_XACML_2_0_CONTEXT_SCHEMA_OS, "xacml-context");
    HttpTransportProperties.Authenticator authenticator;
    private String serverUrl;

    public WSXACMLEntitlementServiceClient(String serverUrl, String userName, String password) {
        this.serverUrl = serverUrl;
        authenticator = new HttpTransportProperties.Authenticator();
        authenticator.setUsername(userName);
        authenticator.setPassword(password);
        authenticator.setPreemptiveAuthentication(true);
    }

    /**
     * Bootstrap the OpenSAML2 library only if it is not bootstrapped.
     */
    public static void doBootstrap() {

        if (!isBootStrapped) {
            try {
                SAMLInitializer.doBootstrap();
                isBootStrapped = true;
            } catch (InitializationException e) {
                log.error("Error in bootstrapping the OpenSAML2 library", e);
            }
        }
    }

    /**
     * Set relevant xacml namespace to all the children in the given iterator.
     *
     * @param iterator: Iterator for all children inside OMElement
     */
    private static void setXACMLNamespace(Iterator iterator) {

        while (iterator.hasNext()) {
            OMElement omElemnt2 = (OMElement) iterator.next();
            omElemnt2.setNamespace(xacmlContextNS);
            if (omElemnt2.getChildElements().hasNext()) {
                setXACMLNamespace(omElemnt2.getChildElements());
            }
        }
    }

    /**
     * Create the issuer object to be added
     *
     * @return : the issuer of the statements
     */
    private static Issuer createIssuer() {

        IssuerBuilder issuer = (IssuerBuilder) XMLObjectProviderRegistrySupport.getBuilderFactory().
                getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
        Issuer issuerObject = issuer.buildObject();
        issuerObject.setValue(ISSUER_URL);
        issuerObject.setSPProvidedID("SPPProvierId");

        return issuerObject;
    }

    /**
     * Get decision in a secured manner using the
     * SAML implementation of XACML using X.509 credentials
     *
     * @return decision extracted from the SAMLResponse sent from PDP
     * @throws Exception
     */
    @Override
    public String getDecision(Attribute[] attributes, String appId) throws Exception {

        String xacmlRequest;
        String xacmlAuthzDecisionQuery;
        OMElement samlResponseElement;
        String samlResponse;
        String result;
        try {
            xacmlRequest = XACMLRequetBuilder.buildXACML3Request(attributes);
            xacmlAuthzDecisionQuery = buildSAMLXACMLAuthzDecisionQuery(xacmlRequest);
            ServiceClient sc = new ServiceClient();
            Options opts = new Options();
            opts.setTo(new EndpointReference(serverUrl + "ws-xacml"));
            opts.setAction("XACMLAuthzDecisionQuery");
            opts.setProperty(org.apache.axis2.transport.http.HTTPConstants.AUTHENTICATE, authenticator);
            opts.setManageSession(true);
            sc.setOptions(opts);
            samlResponseElement = sc.sendReceive(AXIOMUtil.stringToOM(xacmlAuthzDecisionQuery));
            samlResponse = samlResponseElement.toString();
            result = extractXACMLResponse(samlResponse);
            sc.cleanupTransport();
            return result;
        } catch (Exception e) {
            log.error("Error occurred while getting decision using SAML.", e);
            throw new Exception("Error occurred while getting decision using SAML.", e);
        }
    }

    @Override
    public boolean subjectCanActOnResource(String subjectType, String alias, String actionId, String resourceId,
                                           String domainId, String appId) throws Exception {
        return false;
    }

    @Override
    public boolean subjectCanActOnResource(String subjectType, String alias, String actionId, String resourceId,
                                           Attribute[] attributes, String domainId, String appId) throws Exception {
        return false;
    }

    @Override
    public List<String> getResourcesForAlias(String alias, String appId) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public List<String> getActionableResourcesForAlias(String alias, String appId) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public List<String> getActionableChildResourcesForAlias(String alias, String parentResource, String action,
                                                            String appId) throws Exception {

        return new ArrayList<>();
    }

    @Override
    public List<String> getActionsForResource(String alias, String resources, String appId) throws Exception {

        return new ArrayList<>();
    }

    /**
     * Extract XACML response from the SAML response
     *
     * @param samlResponse : SAML response that carries the XACML response from PDP
     * @return the XACML response
     */
    private String extractXACMLResponse(String samlResponse) throws EntitlementProxyException {

        Response samlResponseObject = null;
        ResponseType xacmlResponse = null;
        doBootstrap();
        Init.init();

        try {
            samlResponseObject = (Response) unmarshall(samlResponse);
        } catch (Exception e) {
            log.error("Error occurred while unmarshalling the SAML Response!", e);
            throw new EntitlementProxyException("Error occurred while unmarshalling the SAML Response!", e);
        }

        String xacmlResponseString = null;
        //Access the XACML response only if Issuer and the Signature are valid.
        if (validateIssuer(samlResponseObject.getIssuer())) {

            if (validateSignature(samlResponseObject.getSignature())) {
                List<Assertion> assertionList = samlResponseObject.getAssertions();
                //under the assumption that the first assertion carries the decisionStatement
                Assertion assertion1 = assertionList.get(0);
                if (validateIssuer(assertion1.getIssuer())) {

                    xacmlResponse = ((XACMLAuthzDecisionStatementType) assertion1.
                            getStatements(XACMLAuthzDecisionStatementType.TYPE_NAME_XACML20).get(0)).getResponse();
                    try {
                        xacmlResponseString = org.apache.axis2.util.XMLUtils.toOM(xacmlResponse.getDOM()).
                                toString().replaceAll("xacml-context:", "");

                    } catch (Exception e) {
                        log.error("Error occurred while converting the SAML Response DOM to OMElement", e);
                        throw new EntitlementProxyException(
                                "Error occurred while converting the SAML Response DOM to OMElement", e);
                    }
                } else {
                    log.debug("The submitted issuer is not valid for assertion.");
                }
            } else {
                log.debug("The submitted signature is not valid for the saml response.");
            }
        } else {
            log.debug("The submitted issuer is not valid for the saml response.");
        }
        return xacmlResponseString;
    }

    /**
     * Check for the validity of the issuer
     *
     * @param issuer :who makes the claims inside the Query
     * @return whether the issuer is valid
     */
    private boolean validateIssuer(Issuer issuer) {

        boolean isValidated = false;
        if (ISSUER_URL.equals(issuer.getValue()) && "SPPProvider".equals(issuer.getSPProvidedID())) {
            isValidated = true;
        }
        return isValidated;
    }

    /**
     * Check the validity of the Signature
     *
     * @param signature : XML Signature that authenticates the assertion
     * @return whether the signature is valid
     * @throws Exception
     */
    private boolean validateSignature(Signature signature) throws EntitlementProxyException {

        boolean isSignatureValid = false;

        try {
            SignatureValidator.validate(signature, getPublicX509CredentialImpl());
            isSignatureValid = true;
        } catch (SignatureException e) {
            log.warn("Signature validation failed.", e);
        }

        return isSignatureValid;
    }

    /**
     * get public X509Credentials using the configured basic credentials
     *
     * @return X509Credential implementation
     */
    private X509CredentialImpl getPublicX509CredentialImpl() throws EntitlementProxyException {

        X509CredentialImpl credentialImpl = null;
        // load the default public cert using the configuration in carbon.xml
        java.security.cert.X509Certificate cert = createBasicCredentials().getEntityCertificate();
        credentialImpl = new X509CredentialImpl(cert);
        return credentialImpl;

    }

    /**
     * Build the SAML XACMLAuthzDecisionQuery to be passed to PDP
     *
     * @param xacmlRequest:XACML request with subject, action, resource and environment
     * @return The XACMLAuthzDecisionQuery
     */
    private String buildSAMLXACMLAuthzDecisionQuery(String xacmlRequest) throws EntitlementProxyException {

        RequestType request = null;
        doBootstrap();
        String xacmlAuthzDecisionQueryString = null;

        try {
            request = ((RequestType) unmarshall(formatRequest(xacmlRequest)));
        } catch (Exception e) {
            log.error("Error occurred while unmarshalling the XACML Request!", e);
            throw new EntitlementProxyException("Error occurred while unmarshalling the XACML Request!", e);
        }
        XACMLAuthzDecisionQueryTypeImplBuilder xacmlauthz = (XACMLAuthzDecisionQueryTypeImplBuilder) XMLObjectProviderRegistrySupport
                .getBuilderFactory().
                        getBuilder(XACMLAuthzDecisionQueryType.TYPE_NAME_XACML20);

        XACMLAuthzDecisionQueryType xacmlAuthzDecisionQuery = xacmlauthz
                .buildObject(XACMLAuthzDecisionQueryType.TYPE_NAME_XACML20);
        DateTime currentTime = new DateTime();
        xacmlAuthzDecisionQuery.setRequest(request);
        xacmlAuthzDecisionQuery.setInputContextOnly(true);
        xacmlAuthzDecisionQuery.setReturnContext(false);
        xacmlAuthzDecisionQuery.setIssueInstant(currentTime);
        xacmlAuthzDecisionQuery.setIssuer(createIssuer());

        try {
            xacmlAuthzDecisionQuery = setSignature(xacmlAuthzDecisionQuery, XMLSignature.ALGO_ID_SIGNATURE_RSA,
                                                   createBasicCredentials());
        } catch (Exception e) {
            log.error("Error while building SAMLXACMLAuthzDecisionQuery from the given xacml request", e);
            throw new EntitlementProxyException(
                    "Error while building SAMLXACMLAuthzDecisionQuery from the given xacml request", e);
        }

        if (xacmlAuthzDecisionQuery != null) {
            try {
                xacmlAuthzDecisionQueryString = marshall(xacmlAuthzDecisionQuery);
                xacmlAuthzDecisionQueryString = xacmlAuthzDecisionQueryString
                        .replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "").replace("\n", "");
            } catch (Exception e) {
                log.error("Error occurred while marshalling XACMLAuthzDecisionQuery.", e);
                throw new EntitlementProxyException("Error occurred while marshalling XACMLAuthzDecisionQuery.", e);
            }
        }

        return xacmlAuthzDecisionQueryString;
    }

    /**
     * Format the sent in request as required by OpenSAML
     *
     * @param xacmlRequest : received XACML request
     * @return formatted request
     * @throws Exception
     */
    private String formatRequest(String xacmlRequest) throws EntitlementProxyException {

        xacmlRequest = xacmlRequest.replace("\n", "");

        OMElement omElemnt = null;
        try {
            omElemnt = AXIOMUtil.stringToOM(xacmlRequest);
            omElemnt.setNamespace(xacmlContextNS);
            Iterator childIterator = omElemnt.getChildElements();
            setXACMLNamespace(childIterator);
            return omElemnt.toString();

        } catch (Exception e) {
            log.error("Error occurred while formatting the XACML request", e);
            throw new EntitlementProxyException("Error occurred while formatting the XACML request", e);
        }

    }

    /**
     * Constructing the SAML or XACML Objects from a String
     *
     * @param xmlString Decoded SAML or XACML String
     * @return SAML or XACML Object
     * @throws EntitlementProxyException
     */
    private XMLObject unmarshall(String xmlString) throws EntitlementProxyException {

        try {
            doBootstrap();
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setXIncludeAware(false);
            documentBuilderFactory.setExpandEntityReferences(false);
            try {
                documentBuilderFactory
                        .setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
                documentBuilderFactory
                        .setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE,
                                    false);
                documentBuilderFactory
                        .setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
                documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            } catch (ParserConfigurationException e) {
                log.error("Failed to load XML Processor Feature " + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE + " or "
                                  + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE + " or "
                                  + Constants.LOAD_EXTERNAL_DTD_FEATURE + " or secure-processing.");
            }

            SecurityManager securityManager = new SecurityManager();
            securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
            documentBuilderFactory.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY,
                                                securityManager);

            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder
                    .parse(new ByteArrayInputStream(xmlString.trim().getBytes(Charset.forName("UTF-8"))));
            Element element = document.getDocumentElement();
            UnmarshallerFactory unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            return unmarshaller.unmarshall(element);
        } catch (Exception e) {
            log.error("Error in constructing XML(SAML or XACML) Object from the encoded String", e);
            throw new EntitlementProxyException("Error in constructing XML(SAML or XACML) from the encoded String", e);
        }
    }

    /**
     * Serialize XML objects
     *
     * @param xmlObject : XACML or SAML objects to be serialized
     * @return serialized XACML or SAML objects
     */
    private String marshall(XMLObject xmlObject) throws EntitlementProxyException {

        try {
            doBootstrap();
            System.setProperty(DOCUMENT_BUILDER_FACTORY, DOCUMENT_BUILDER_FACTORY_IMPL);

            MarshallerFactory marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory();
            Marshaller marshaller = marshallerFactory.getMarshaller(xmlObject);
            Element element = marshaller.marshall(xmlObject);

            ByteArrayOutputStream byteArrayOutputStrm = new ByteArrayOutputStream();
            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
            DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
            LSSerializer writer = impl.createLSSerializer();
            LSOutput output = impl.createLSOutput();
            output.setByteStream(byteArrayOutputStrm);
            writer.write(element, output);
            return new String(byteArrayOutputStrm.toByteArray(), Charset.forName("UTF-8"));
        } catch (Exception e) {
            log.error("Error Serializing the SAML Response");
            throw new EntitlementProxyException("Error Serializing the SAML Response", e);
        }
    }

    /**
     * Overloaded method to sign a XACMLAuthzDecisionQuery
     *
     * @param xacmlAuthzDecisionQueryType : xacmlAuthzdecisonQuery to be signed
     * @param signatureAlgorithm          :  algorithm to be used in signing
     * @param cred                        : signing credentials
     * @return signed xacmlAuthzDecisionQuery
     * @throws EntitlementProxyException
     */
    private XACMLAuthzDecisionQueryType setSignature(XACMLAuthzDecisionQueryType xacmlAuthzDecisionQueryType,
                                                     String signatureAlgorithm, X509Credential cred)
            throws EntitlementProxyException {

        doBootstrap();
        try {
            Signature signature = (Signature) buildXMLObject(Signature.DEFAULT_ELEMENT_NAME);
            signature.setSigningCredential(cred);
            signature.setSignatureAlgorithm(signatureAlgorithm);
            signature.setCanonicalizationAlgorithm(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

            try {
                KeyInfo keyInfo = (KeyInfo) buildXMLObject(KeyInfo.DEFAULT_ELEMENT_NAME);
                X509Data data = (X509Data) buildXMLObject(X509Data.DEFAULT_ELEMENT_NAME);
                X509Certificate cert = (X509Certificate) buildXMLObject(X509Certificate.DEFAULT_ELEMENT_NAME);
                String value = org.apache.xml.security.utils.Base64.encode(cred.getEntityCertificate().getEncoded());
                cert.setValue(value);
                data.getX509Certificates().add(cert);
                keyInfo.getX509Datas().add(data);
                signature.setKeyInfo(keyInfo);
            } catch (CertificateEncodingException e) {

                if (log.isDebugEnabled()) {
                    log.debug("Certificate Encoding Exception occurred : ", e);
                }

                throw new EntitlementProxyException("Error getting the certificate.");
            }

            xacmlAuthzDecisionQueryType.setSignature(signature);

            List<Signature> signatureList = new ArrayList<Signature>();
            signatureList.add(signature);

            //Marshall and Sign
            MarshallerFactory marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory();
            Marshaller marshaller = marshallerFactory.getMarshaller(xacmlAuthzDecisionQueryType);
            marshaller.marshall(xacmlAuthzDecisionQueryType);

            Init.init();
            Signer.signObjects(signatureList);
            return xacmlAuthzDecisionQueryType;

        } catch (Exception e) {
            throw new EntitlementProxyException("Error When signing the assertion.", e);
        }
    }

    /**
     * Create basic X509 credentials using server configuration
     *
     * @return basicX509Credential
     */
    private BasicX509Credential createBasicCredentials() {

        PrivateKey issuerPK = null;
        Certificate certificate = null;
        CarbonServerConfigurationService serverConfig = CarbonServerConfigurationService.getInstance();
        String ksPassword = serverConfig.getFirstProperty("Security.KeyStore.Password");
        String ksLocation = serverConfig.getFirstProperty("Security.KeyStore.Location");
        String keyAlias = serverConfig.getFirstProperty("Security.KeyStore.KeyAlias");
        String ksType = serverConfig.getFirstProperty("Security.KeyStore.Type");
        String privateKeyPassword = serverConfig.getFirstProperty("Security.KeyStore.KeyPassword");
        String provider = getPreferredJceProvider();
        try {
            FileInputStream fis = new FileInputStream(ksLocation);
            BufferedInputStream bis = new BufferedInputStream(fis);
            KeyStore keyStore;
            if (provider != null) {
                keyStore = KeyStore.getInstance(ksType, provider);
            } else {
                keyStore = KeyStore.getInstance(ksType);
            }
            keyStore.load(bis, ksPassword.toCharArray());
            bis.close();
            issuerPK = (PrivateKey) keyStore.getKey(keyAlias, privateKeyPassword.toCharArray());
            certificate = keyStore.getCertificate(keyAlias);

        } catch (KeyStoreException e) {
            log.error("Error in getting a keystore.", e);
        } catch (FileNotFoundException e) {
            log.error("Error in reading the keystore file from given the location.", e);
        } catch (CertificateException e) {
            log.error("Error in creating a X.509 certificate.", e);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error in loading the keystore.", e);
        } catch (IOException e) {
            log.error("Error in reading keystore file.", e);
        } catch (UnrecoverableKeyException e) {
            log.error("Error in getting the private key.", e);
        } catch (NoSuchProviderException e) {
            log.error("Specified security provider is not available in this environment: ", e);
        }

        BasicX509Credential basicCredential = new BasicX509Credential((java.security.cert.X509Certificate) certificate);
        basicCredential.setEntityCertificate((java.security.cert.X509Certificate) certificate);
        basicCredential.setPrivateKey(issuerPK);

        return basicCredential;
    }

    /**
     * Create XMLObject from a given QName
     *
     * @param objectQName: QName of the object to be built into a XMLObject
     * @return built xmlObject
     * @throws EntitlementProxyException
     */
    private XMLObject buildXMLObject(QName objectQName) throws EntitlementProxyException {

        XMLObjectBuilder builder = XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilder(objectQName);
        if (builder == null) {
            throw new EntitlementProxyException("Unable to retrieve builder for object QName " + objectQName);
        }
        return builder.buildObject(objectQName.getNamespaceURI(), objectQName.getLocalPart(), objectQName.getPrefix());
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
