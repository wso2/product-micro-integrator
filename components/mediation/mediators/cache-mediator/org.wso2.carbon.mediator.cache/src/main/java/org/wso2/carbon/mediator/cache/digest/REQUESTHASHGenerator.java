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
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMProcessingInstruction;
import org.apache.axiom.om.OMText;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.cache.CachingConstants;
import org.wso2.carbon.mediator.cache.CachingException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;

/**
 * This is the extended implementation of <a href="http://www.ietf.org/rfc/rfc2803.txt">DOMHASH algorithm</a> over a
 * HTTP request and Payload (XML Node) for retrieving a unique key for the request.
 *
 * @see org.wso2.caching.digest.DigestGenerator
 */
@Deprecated
public class REQUESTHASHGenerator extends DOMHASHGenerator {

    /**
     * String representing the MD5 digest algorithm.
     */
    public static final String MD5_DIGEST_ALGORITHM = "MD5";

    private static final Log log = LogFactory.getLog(REQUESTHASHGenerator.class);

    /**
     * This is the implementation of the getDigest method and will implement the Extended DOMHASH algorithm based HTTP
     * request identifications. This will consider To address of the request, HTTP headers and XML Payload in generating
     * the digets. So, in effect this will uniquely identify the HTTP request with the same To address, Headers and
     * Payload.
     *
     * @param msgContext - MessageContext on which the XML node identifier will be generated
     * @return Object representing the DOMHASH value of the normalized XML node
     * @throws CachingException if there is an error in generating the digest key
     * @see org.wso2.caching.digest.DigestGenerator #getDigest(org.apache.axis2.context.MessageContext)
     */
    public String getDigest(MessageContext msgContext) throws CachingException {
        OMNode body = msgContext.getEnvelope().getBody();
        String toAddress = null;
        if (msgContext.getTo() != null) {
            toAddress = msgContext.getTo().getAddress();
        }
        String[]  permanentlyExcludedHeaders = CachingConstants.PERMANENTLY_EXCLUDED_HEADERS;
        Map<String, String> headers = new TreeMap<String, String>(new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        });
        headers.putAll((Map<String, String>)msgContext.getProperty(MessageContext.TRANSPORT_HEADERS));
        //remove permanently excluded headers from hashing methods
        for (String excludedHeader : permanentlyExcludedHeaders) {
            headers.remove(excludedHeader);
        }
        if (body != null) {
            byte[] digest = null;
            if (toAddress != null) {
                digest = getDigest(body, toAddress, headers, MD5_DIGEST_ALGORITHM);
            } else {
                digest = getDigest(body, MD5_DIGEST_ALGORITHM);
            }
            return digest != null ? getStringRepresentation(digest) : null;
        } else {
            return null;
        }
    }

    /**
     * This is an overloaded method for the digest generation for OMNode and request.
     *
     * @param node            - OMNode to be subjected to the key generation
     * @param toAddress       - Request To address to be subjected to the key generation
     * @param headers         - Header parameters to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided node
     * @throws CachingException if there is an error in generating the digest
     */
    public byte[] getDigest(OMNode node, String toAddress, Map<String, String> headers,
                            String digestAlgorithm) throws CachingException {

        if (node.getType() == OMNode.ELEMENT_NODE) {
            return getDigest((OMElement) node, toAddress, headers, digestAlgorithm);
        } else if (node.getType() == OMNode.TEXT_NODE) {
            return getDigest((OMText) node, digestAlgorithm);
        } else if (node.getType() == OMNode.PI_NODE) {
            return getDigest((OMProcessingInstruction) node, digestAlgorithm);
        } else {
            return new byte[0];
        }
    }

    /**
     * This is an overloaded method for the digest generation for OMElement and request.
     *
     * @param element         - OMElement to be subjected to the key generation
     * @param toAddress       - Request To address to be subjected to the key generation
     * @param headers         - Header parameters to be subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided element
     * @throws CachingException if there is an io error or the specified algorithm is incorrect
     */
    public byte[] getDigest(OMElement element, String toAddress, Map<String, String> headers,
                            String digestAlgorithm) throws CachingException {

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

            dos.write(toAddress.getBytes("UnicodeBigUnmarked"));

            /*String acceptHeader = headers.get("accept");
            acceptHeader = (acceptHeader == null) ? headers.get("Accept") : acceptHeader;

            if (acceptHeader != null) {
                dos.write(acceptHeader.getBytes("UnicodeBigUnmarked"));
            }

            String contentTypeHeader = headers.get("content-type");
            contentTypeHeader = (contentTypeHeader == null) ? headers.get("Content-Type") : contentTypeHeader;

            if (contentTypeHeader != null) {
                dos.write(contentTypeHeader.getBytes("UnicodeBigUnmarked"));
            }*/

            Iterator itr = headers.keySet().iterator();
            while (itr.hasNext()) {
                String key = (String) itr.next();
                String value = headers.get(key);
                dos.write(getDigest(key, value, digestAlgorithm));
            }

            Collection attrs = getAttributesWithoutNS(element);
            dos.writeInt(attrs.size());


            itr = attrs.iterator();
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
                dos.write(getDigest(node, toAddress, headers, digestAlgorithm));
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
     * This is an overloaded method for the digest generation for HTTP header propery.
     *
     * @param key             - Key of the header property subjected to the key generation
     * @param value           - Value of the header property subjected to the key generation
     * @param digestAlgorithm - digest algorithm as a String
     * @return byte[] representing the calculated digest over the provided attribute
     * @throws CachingException if the specified algorithm is incorrect or the encoding is not supported by the
     *                          processor
     */
    public byte[] getDigest(String key, String value, String digestAlgorithm) throws CachingException {

        byte[] digest = new byte[0];

        if (!key.equalsIgnoreCase("Date") && !key.equalsIgnoreCase("User-Agent")) {
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
                md.update(key.getBytes("UnicodeBigUnmarked"));

                if (value != null) {
                    md.update((byte) 0);
                    md.update((byte) 0);
                    md.update(value.getBytes("UnicodeBigUnmarked"));
                }

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

    private void handleException(String message, Throwable cause) throws CachingException {
        log.debug(message, cause);
        throw new CachingException(message, cause);
    }

}
