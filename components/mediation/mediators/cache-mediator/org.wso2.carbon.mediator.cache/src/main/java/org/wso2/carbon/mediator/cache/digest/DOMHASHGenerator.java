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

package org.wso2.carbon.mediator.cache.digest;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMProcessingInstruction;
import org.apache.axiom.om.OMText;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.cache.CachingException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * This is the default DigestGenerator for the cache and this implements the
 * <a href="http://www.ietf.org/rfc/rfc2803.txt">DOMHASH
 * algorithm</a> over an XML node to implement retrieving a unique key for the normalized xml node.
 */
@Deprecated
public class DOMHASHGenerator implements DigestGenerator {

    /**
     * String representing the MD5 digest algorithm.
     */
    public static final String MD5_DIGEST_ALGORITHM = "MD5";

    /**
     * String representing the SHA digest algorithm.
     */
    public static final String SHA_DIGEST_ALGORITHM = "SHA";

    /**
     * String representing the SHA1 digest algorithm.
     */
    public static final String SHA1_DIGEST_ALGORITHM = "SHA1";

    /**
     * Holds the log for the logging.
     */
    private static final Log log = LogFactory.getLog(DOMHASHGenerator.class);

    @Override
    public void init(Map<String, Object> properties) {

    }

    /**
     * This is the implementation of the getDigest method and will implement the DOMHASH algorithm based XML node
     * identifications. This will consider only the SOAP payload and this does not consider the SOAP headers in
     * generating the digets. So, in effect this will uniquely identify the SOAP messages with the same payload.
     *
     * @param msgContext - MessageContext on which the XML node identifier will be generated
     * @return Object representing the DOMHASH value of the normalized XML node
     * @throws CachingException if there is an error in generating the digest key
     *                          <p>
     *                          #getDigest(org.apache.axis2.context.MessageContext)
     */
    public String getDigest(MessageContext msgContext) throws CachingException {

        OMNode request = msgContext.getEnvelope().getBody();
        if (request != null) {
            byte[] digest = getDigest(request, MD5_DIGEST_ALGORITHM);
            return digest != null ? getStringRepresentation(digest) : null;
        } else {
            return null;
        }
    }

    /**
     * This is an overloaded method for the digest generation for OMNode.
     *
     * @param node            - OMNode to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided node
     * @throws CachingException if there is an error in generating the digest
     */
    public byte[] getDigest(OMNode node, String digestAlgorithm) throws CachingException {

        if (node.getType() == OMNode.ELEMENT_NODE) {
            return getDigest((OMElement) node, digestAlgorithm);
        } else if (node.getType() == OMNode.TEXT_NODE) {
            return getDigest((OMText) node, digestAlgorithm);
        } else if (node.getType() == OMNode.PI_NODE) {
            return getDigest((OMProcessingInstruction) node, digestAlgorithm);
        } else {
            return new byte[0];
        }
    }

    /**
     * This is an overloaded method for the digest generation for OMDocument.
     *
     * @param document        - OMDocument to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided document
     * @throws CachingException if there is an io error or the specified algorithm is incorrect
     */
    public byte[] getDigest(OMDocument document, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];
        String provider = Util.getPreferredJceProvider();
        MessageDigest md;
        try {
            if (provider != null) {
                md = MessageDigest.getInstance(digestAlgorithm, provider);
            } else {
                md = MessageDigest.getInstance(digestAlgorithm);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(9);
            Collection childNodes = getValidElements(document);
            dos.writeInt(childNodes.size());

            for (Iterator itr = childNodes.iterator(); itr.hasNext(); ) {
                OMNode node = (OMNode) itr.next();
                if (node.getType() == OMNode.PI_NODE) {
                    dos.write(getDigest((OMProcessingInstruction) node, digestAlgorithm));
                } else if (
                        node.getType() == OMNode.ELEMENT_NODE) {
                    dos.write(getDigest((OMElement) node, digestAlgorithm));
                }
            }

            dos.close();
            md.update(baos.toByteArray());
            digest = md.digest();

        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (IOException e) {
            handleException("Error in calculating the " +
                                    "digest value for the OMDocument : " + document, e);
        } catch (NoSuchProviderException e) {
            handleException("Specified security provider is not available in this environment: ", e);
        }

        return digest;
    }

    /**
     * This is an overloaded method for the digest generation for OMElement.
     *
     * @param element         - OMElement to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided element
     * @throws CachingException if there is an io error or the specified algorithm is incorrect
     */
    public byte[] getDigest(OMElement element, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];
        String provider = Util.getPreferredJceProvider();
        MessageDigest md;
        try {
            if (provider != null) {
                md = MessageDigest.getInstance(digestAlgorithm, provider);
            } else {
                md = MessageDigest.getInstance(digestAlgorithm);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(1);
            dos.write(getExpandedName(element).getBytes("UnicodeBigUnmarked"));
            dos.write((byte) 0);
            dos.write((byte) 0);

            Collection attrs = getAttributesWithoutNS(element);
            dos.writeInt(attrs.size());

            Iterator itr = attrs.iterator();
            while (itr.hasNext()) {
                dos.write(getDigest((OMAttribute) itr.next(), digestAlgorithm));
            }
            OMNode node = element.getFirstOMChild();

            // adjoining Texts are merged,
            // there is  no 0-length Text, and
            // comment nodes are removed.
            int length = 0;
            itr = element.getChildElements();
            while (itr.hasNext()) {
                length++;
                itr.next();
            }
            dos.writeInt(length);

            while (node != null) {
                dos.write(getDigest(node, digestAlgorithm));
                node = node.getNextOMSibling();
            }
            dos.close();
            md.update(baos.toByteArray());

            digest = md.digest();

        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (IOException e) {
            handleException("Error in calculating the " +
                                    "digest value for the OMElement : " + element, e);
        } catch (NoSuchProviderException e) {
            handleException("Specified security provider is not available in this environment: ", e);
        }

        return digest;
    }

    /**
     * This method is an overloaded method for the digest generation for OMProcessingInstruction.
     *
     * @param pi              - OMProcessingInstruction to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided pi
     * @throws CachingException if the specified algorithm is incorrect or the encoding is not supported by the
     *                          processor
     */
    public byte[] getDigest(OMProcessingInstruction pi, String digestAlgorithm)
            throws CachingException {

        byte[] digest = new byte[0];
        String provider = Util.getPreferredJceProvider();
        MessageDigest md;
        try {
            if (provider != null) {
                md = MessageDigest.getInstance(digestAlgorithm, provider);
            } else {
                md = MessageDigest.getInstance(digestAlgorithm);
            }
            md.update((byte) 0);
            md.update((byte) 0);
            md.update((byte) 0);
            md.update((byte) 7);
            md.update(pi.getTarget().getBytes("UnicodeBigUnmarked"));

            md.update((byte) 0);
            md.update((byte) 0);
            md.update(pi.getValue().getBytes("UnicodeBigUnmarked"));

            digest = md.digest();

        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (UnsupportedEncodingException e) {
            handleException("Error in generating the digest " +
                                    "using the provided encoding : UnicodeBigUnmarked", e);
        } catch (NoSuchProviderException e) {
            handleException("Specified security provider is not available in this environment: ", e);
        }

        return digest;
    }

    /**
     * This is an overloaded method for the digest generation for OMAttribute.
     *
     * @param attribute       - OMAttribute to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided attribute
     * @throws CachingException if the specified algorithm is incorrect or the encoding is not supported by the
     *                          processor
     */
    public byte[] getDigest(OMAttribute attribute, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];

        if (!(attribute.getLocalName().equals("xmlns") ||
                attribute.getLocalName().startsWith("xmlns:"))) {
            String provider = Util.getPreferredJceProvider();
            MessageDigest md;
            try {
                if (provider != null) {
                    md = MessageDigest.getInstance(digestAlgorithm, provider);
                } else {
                    md = MessageDigest.getInstance(digestAlgorithm);
                }
                md.update((byte) 0);
                md.update((byte) 0);
                md.update((byte) 0);
                md.update((byte) 2);
                md.update(getExpandedName(attribute).getBytes("UnicodeBigUnmarked"));

                md.update((byte) 0);
                md.update((byte) 0);
                md.update(attribute.getAttributeValue().getBytes("UnicodeBigUnmarked"));

                digest = md.digest();

            } catch (NoSuchAlgorithmException e) {
                handleException("Can not locate the algorithm " +
                                        "provided for the digest generation : " + digestAlgorithm, e);
            } catch (UnsupportedEncodingException e) {
                handleException("Error in generating the digest " +
                                        "using the provided encoding : UnicodeBigUnmarked", e);
            } catch (NoSuchProviderException e) {
                handleException("Specified security provider is not available in this environment: ", e);
            }
        }

        return digest;
    }

    /**
     * This method is an overloaded method for the digest generation for OMText.
     *
     * @param text            - OMText to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided text
     * @throws CachingException if the specified algorithm is incorrect or the encoding is not supported by the
     *                          processor
     */
    public byte[] getDigest(OMText text, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];

        String provider = Util.getPreferredJceProvider();
        MessageDigest md;
        try {
            if (provider != null) {
                md = MessageDigest.getInstance(digestAlgorithm, provider);
            } else {
                md = MessageDigest.getInstance(digestAlgorithm);
            }
            md.update((byte) 0);
            md.update((byte) 0);
            md.update((byte) 0);
            md.update((byte) 3);
            md.update(text.getText().getBytes("UnicodeBigUnmarked"));

            digest = md.digest();

        } catch (NoSuchAlgorithmException e) {
            handleException("Can not locate the algorithm " +
                                    "provided for the digest generation : " + digestAlgorithm, e);
        } catch (UnsupportedEncodingException e) {
            handleException("Error in generating the digest " +
                                    "using the provided encoding : UnicodeBigUnmarked", e);
        } catch (NoSuchProviderException e) {
            handleException("Specified security provider is not available in this environment: ", e);
        }

        return digest;
    }

    /**
     * This is an overloaded method for getting the expanded name as namespaceURI followed by the local name for
     * OMElement.
     *
     * @param element - OMElement of which the expanded name is retrieved
     * @return expanded name of OMElement as an String in the form {ns-uri:local-name}
     */
    public String getExpandedName(OMElement element) {

        if (element.getNamespace() != null) {
            return element.getNamespace().getNamespaceURI() + ":" + element.getLocalName();
        } else {
            return element.getLocalName();
        }
    }

    /**
     * This is an overloaded method for getting the expanded name as namespaceURI followed by the local name for
     * OMAttribute.
     *
     * @param attribute - OMAttribute of which the expanded name is retrieved
     * @return expanded name of the OMAttribute as an String in the form {ns-uri:local-name}
     */
    public String getExpandedName(OMAttribute attribute) {

        if (attribute.getNamespace() != null) {
            return attribute.getNamespace().getNamespaceURI() + ":" + attribute.getLocalName();
        } else {
            return attribute.getLocalName();
        }
    }

    /**
     * Gets the collection of attributes which are none namespace declarations for an OMElement sorted according to the
     * expanded names of the attributes.
     *
     * @param element - OMElement of which the none ns declaration attributes to be retrieved
     * @return the collection of attributes which are none namespace declarations
     */
    public Collection getAttributesWithoutNS(OMElement element) {

        SortedMap map = new TreeMap();

        Iterator itr = element.getAllAttributes();
        while (itr.hasNext()) {
            OMAttribute attribute = (OMAttribute) itr.next();

            if (!(attribute.getLocalName().equals("xmlns") ||
                    attribute.getLocalName().startsWith("xmlns:"))) {

                map.put(getExpandedName(attribute), attribute);
            }
        }

        return map.values();
    }

    /**
     * Gets the valid element collection of an OMDocument. This returns only the OMElement and OMProcessingInstruction
     * nodes
     *
     * @param document - OMDocument of which the valid elements to be retrieved
     * @return the collection of OMProcessingInstructions and OMElements in the provided document
     */
    public Collection getValidElements(OMDocument document) {

        ArrayList list = new ArrayList();
        Iterator itr = document.getChildren();
        while (itr.hasNext()) {
            OMNode node = (OMNode) itr.next();
            if (node.getType() == OMNode.ELEMENT_NODE || node.getType() == OMNode.PI_NODE) {
                list.add(node);
            }
        }

        return list;
    }

    /**
     * Gets the String representation of the byte array.
     *
     * @param array - byte[] of which the String representation is required
     * @return the String representation of the byte[]
     */
    public String getStringRepresentation(byte[] array) {

        StringBuffer strBuff = new StringBuffer(array.length);
        for (int i = 0; i < array.length; i++) {
            strBuff.append(array[i]);
        }
        return strBuff.toString();
    }

    /**
     * Compares two OMNodes for the XML equality.
     *
     * @param node            - OMNode to be compared
     * @param comparingNode   - OMNode to be compared
     * @param digestAlgorithm - digest algorithm as a String to be used in the comparison
     * @return boolean true if the two OMNodes are XML equal, and false otherwise
     * @throws CachingException if there is an error in generating the digest key
     */
    public boolean compareOMNode(OMNode node, OMNode comparingNode, String digestAlgorithm)
            throws CachingException {

        return Arrays.equals(getDigest(node, digestAlgorithm),
                             getDigest(comparingNode, digestAlgorithm));
    }

    /**
     * Compares two OMDocuments for the XML equality.
     *
     * @param document          - OMDocument to be compared
     * @param comparingDocument - OMDocument to be compared
     * @param digestAlgorithm   - digest algorithm as a String to be used in the comparison
     * @return boolean true if the two OMDocuments are XML equal, and false otherwise
     * @throws CachingException if there is an error in generating the digest key
     */
    public boolean compareOMDocument(OMDocument document, OMDocument comparingDocument,
                                     String digestAlgorithm) throws CachingException {

        return Arrays.equals(getDigest(document, digestAlgorithm),
                             getDigest(comparingDocument, digestAlgorithm));
    }

    /**
     * Compares two OMAttributes for the XML equality.
     *
     * @param attribute          - OMAttribute to be compared
     * @param comparingAttribute - OMAttribute to be compared
     * @param digestAlgorithm    - digest algorithm as a String to be used in the comparison
     * @return boolean true if the two OMAttributes are XML equal, and false otherwise
     * @throws CachingException if there is an error in generating the digest key
     */
    public boolean compareOMAttribute(OMAttribute attribute, OMAttribute comparingAttribute,
                                      String digestAlgorithm) throws CachingException {

        return Arrays.equals(getDigest(attribute, digestAlgorithm),
                             getDigest(comparingAttribute, digestAlgorithm));
    }

    private void handleException(String message, Throwable cause) throws CachingException {
        log.debug(message, cause);
        throw new CachingException(message, cause);
    }
}
