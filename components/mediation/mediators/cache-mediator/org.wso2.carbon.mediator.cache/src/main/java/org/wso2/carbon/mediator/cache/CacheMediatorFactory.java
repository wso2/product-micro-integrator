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
package org.wso2.carbon.mediator.cache;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.config.xml.FactoryUtils;
import org.apache.synapse.config.xml.SequenceMediatorFactory;
import org.apache.synapse.config.xml.XMLConfigConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.wso2.carbon.mediator.cache.digest.DigestGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.xml.namespace.QName;

/**
 * Creates a {@link CacheMediator} using the given OMElement for the cache mediator.
 */
public class CacheMediatorFactory extends AbstractMediatorFactory {

    /**
     * QName of the timeout.
     */
    private static final QName ATT_TIMEOUT = new QName(CachingConstants.TIMEOUT_STRING);

    /**
     * QName of the collector.
     */
    private static final QName ATT_COLLECTOR = new QName(CachingConstants.COLLECTOR_STRING);

    /**
     * QName of the maximum message size.
     */
    private static final QName ATT_MAX_MSG_SIZE = new QName(CachingConstants.MAX_MESSAGE_SIZE_STRING);

    /**
     * QName of the onCacheHit mediator sequence reference.
     */
    private static final QName ON_CACHE_HIT_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                          CachingConstants.ON_CACHE_HIT_STRING);

    /**
     * QName of the mediator sequence.
     */
    private static final QName ATT_SEQUENCE = new QName(CachingConstants.SEQUENCE_STRING);

    /**
     * QName of the onCacheHit mediator sequence reference.
     */
    private static final QName PROTOCOL_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                      CachingConstants.PROTOCOL_STRING);

    /**
     * QName of the protocol type.
     */
    private static final QName ATT_TYPE = new QName(CachingConstants.TYPE_STRING);

    /**
     * QName of the hTTPMethodToCache.
     */
    private static final QName HTTP_METHODS_TO_CACHE_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                                   CachingConstants.METHODS_STRING);

    /**
     * QName of the headersToExcludeInHash.
     */
    private static final QName HEADERS_TO_EXCLUDE_IN_HASH_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                                        CachingConstants.HEADERS_TO_EXCLUDE_STRING);

    /**
     * QName of the headersToIncludeInHash.
     */
    private static final QName HEADERS_TO_INCLUDE_IN_HASH_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                                        CachingConstants.HEADERS_TO_INCLUDE_STRING);

    /**
     * QName of the response codes to include when hashing.
     */
    private static final QName RESPONSE_CODES_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                            CachingConstants.RESPONSE_CODES_STRING);

    /**
     * QName of the digest generator.
     */
    private static final QName HASH_GENERATOR_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                            CachingConstants.HASH_GENERATOR_STRING);

    /**
     * Attribute QName of the digest generator for backward compatibility.
     */
    private static final QName ATT_HASH_GENERATOR = new QName(CachingConstants.HASH_GENERATOR_STRING);

    /**
     * QName of the cache implementation.
     */
    private static final QName IMPLEMENTATION_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                            CachingConstants.IMPLEMENTATION_STRING);
    /**
     * QName of the maximum message size.
     */
    private static final QName ATT_SIZE = new QName(CachingConstants.MAX_SIZE_STRING);

    /**
     * QName of the enableCacheControl.
     */
    private static final QName ENABLE_CACHE_CONTROL_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                            CachingConstants.ENABLE_CACHE_CONTROL_STRING);
    /**
     * QName of the includeAgeHeader.
     */
    private static final QName INCLUDE_AGE_HEADER_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE,
                                                            CachingConstants.INCLUDE_AGE_HEADER_STRING);

    /**
     * QNama of the cache id.
     */
    private static final QName ATT_ID = new QName(CachingConstants.ID_STRING);

    /**
     * QName of the cache scope.
     */
    private static final QName ATT_SCOPE = new QName(CachingConstants.SCOPE_STRING);

    /**
     * The cache manager to be used in each cache instance.
     */
    private final CacheManager cacheManager = new CacheManager();
    /**
     * {@inheritDoc}
     */
    protected Mediator createSpecificMediator(OMElement elem, Properties properties) {
        if (!CachingConstants.CACHE_Q.equals(elem.getQName())) {
            handleException(
                    "Unable to create the cache mediator. Unexpected element as the cache mediator configuration");
        }

        CacheMediator cache = new CacheMediator(cacheManager);

        OMAttribute collectorAttr = elem.getAttribute(ATT_COLLECTOR);
        if (collectorAttr != null && collectorAttr.getAttributeValue() != null) {
            if ("true".equals(collectorAttr.getAttributeValue())) {
                cache.setCollector(true);

                OMAttribute scopeAttribute = elem.getAttribute(ATT_SCOPE);
                if (scopeAttribute != null && scopeAttribute.getAttributeValue() != null) {
                    cache.setScope(scopeAttribute.getAttributeValue().trim());
                }

            } else if ("false".equals(collectorAttr.getAttributeValue())) {
                cache.setCollector(false);

                OMAttribute timeoutAttr = elem.getAttribute(ATT_TIMEOUT);
                if (timeoutAttr != null && timeoutAttr.getAttributeValue() != null) {
                    cache.setTimeout(Long.parseLong(timeoutAttr.getAttributeValue().trim()));
                } else {
                    cache.setTimeout(CachingConstants.DEFAULT_TIMEOUT);
                }

                OMAttribute maxMessageSizeAttr = elem.getAttribute(ATT_MAX_MSG_SIZE);
                if (maxMessageSizeAttr != null && maxMessageSizeAttr.getAttributeValue() != null) {
                    cache.setMaxMessageSize(Integer.parseInt(maxMessageSizeAttr.getAttributeValue().trim()));
                } else {
                    cache.setMaxMessageSize(-1);
                }

                OMAttribute idAttribute = elem.getAttribute(ATT_ID);
                if (idAttribute != null && idAttribute.getAttributeValue() != null) {
                    cache.setId(idAttribute.getAttributeValue().trim());
                }

                OMAttribute hashGeneratorAttribute = elem.getAttribute(ATT_HASH_GENERATOR);
                if (hashGeneratorAttribute != null && hashGeneratorAttribute.getAttributeValue() != null) {
                    cache.setHashGenerator(hashGeneratorAttribute.getAttributeValue().trim());
                }

                OMAttribute scopeAttribute = elem.getAttribute(ATT_SCOPE);
                if (scopeAttribute != null && scopeAttribute.getAttributeValue() != null) {
                    cache.setScope(scopeAttribute.getAttributeValue().trim());
                }

                String className = null;
                OMElement protocolElem = elem.getFirstChildWithName(PROTOCOL_Q);
                Map<String, Object> props = new HashMap<>();
                if (protocolElem != null) {
                    OMAttribute typeAttr = protocolElem.getAttribute(ATT_TYPE);

                    if (typeAttr != null &&
                            typeAttr.getAttributeValue() != null) {
                        OMElement hashGeneratorElem = protocolElem.getFirstChildWithName(HASH_GENERATOR_Q);
                        if (hashGeneratorElem != null) {
                            className = hashGeneratorElem.getText();
                        }

                        String protocolType = typeAttr.getAttributeValue().toUpperCase().trim();
                        cache.setProtocolType(protocolType);
                        if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(protocolType)) {
                            OMElement methodElem = protocolElem.getFirstChildWithName(HTTP_METHODS_TO_CACHE_Q);
                            if (methodElem != null) {
                                String[] methods = methodElem.getText().split(",");
                                if (!"".equals(methods[0])) {
                                    for (int i = 0; i < methods.length; i++) {
                                        methods[i] = methods[i].toUpperCase().trim();
                                        if (!(PassThroughConstants.HTTP_POST.equals(methods[i]) ||
                                                PassThroughConstants.HTTP_GET.equals(methods[i]) ||
                                                PassThroughConstants.HTTP_HEAD.equals(
                                                        methods[i]) || PassThroughConstants.HTTP_PUT.equals(
                                                methods[i]) || PassThroughConstants.HTTP_DELETE.equals(methods[i]) ||
                                                PassThroughConstants.HTTP_OPTIONS.equals(methods[i]) ||
                                                PassThroughConstants.HTTP_CONNECT.equals(methods[i]) ||
                                                "PATCH".equals(methods[i]) || CachingConstants.ALL.equals(
                                                methods[i]))) {
                                            handleException("Unexpected method type: " + methods[i]);
                                        }
                                    }
                                    cache.setHTTPMethodsToCache(methods);
                                }
                            } else {
                                cache.setHTTPMethodsToCache(CachingConstants.ALL);
                            }

                            OMElement headersToIncludeInHash = protocolElem.getFirstChildWithName(
                                    HEADERS_TO_INCLUDE_IN_HASH_Q);
                            if (headersToIncludeInHash != null) {
                                String[] headers = headersToIncludeInHash.getText().split(",");
                                for (int i = 0; i < headers.length; i++) {
                                    headers[i] = headers[i].trim();
                                }
                                cache.setHeadersToIncludeInHash(headers);
                            } else {
                                cache.setHeadersToIncludeInHash("");
                            }

                            OMElement headersToExcludeInHash = protocolElem.getFirstChildWithName(
                                    HEADERS_TO_EXCLUDE_IN_HASH_Q);
                            if (headersToExcludeInHash != null) {
                                String[] headers = headersToExcludeInHash.getText().split(",");
                                for (int i = 0; i < headers.length; i++) {
                                    headers[i] = headers[i].trim();
                                }
                                cache.setHeadersToExcludeInHash(headers);
                            } else {
                                cache.setHeadersToExcludeInHash("");
                            }

                            OMElement responseCodesElem = protocolElem.getFirstChildWithName(RESPONSE_CODES_Q);
                            if (responseCodesElem != null) {
                                String responses = responseCodesElem.getText();
                                if (!"".equals(responses) && responses != null) {
                                    cache.setResponseCodes(responses);
                                }
                            } else {
                                cache.setResponseCodes(CachingConstants.ANY_RESPONSE_CODE);
                            }

                            OMElement enableCacheControlElem =
                                    protocolElem.getFirstChildWithName(ENABLE_CACHE_CONTROL_Q);
                            if (enableCacheControlElem != null) {
                                String cacheControlElemText = enableCacheControlElem.getText();
                                if (StringUtils.isNotEmpty(cacheControlElemText)) {
                                    cache.setCacheControlEnabled(Boolean.parseBoolean(cacheControlElemText));
                                }
                            } else {
                                cache.setCacheControlEnabled(CachingConstants.DEFAULT_ENABLE_CACHE_CONTROL);
                            }

                            OMElement addAgeHeaderElem =
                                    protocolElem.getFirstChildWithName(INCLUDE_AGE_HEADER_Q);
                            if (addAgeHeaderElem != null) {
                                String addAgeHeaderElemText = addAgeHeaderElem.getText();
                                if (StringUtils.isNotEmpty(addAgeHeaderElemText)) {
                                    cache.setAddAgeHeaderEnabled(Boolean.parseBoolean(addAgeHeaderElemText));
                                }
                            } else {
                                cache.setCacheControlEnabled(CachingConstants.DEFAULT_ADD_AGE_HEADER);
                            }

                            props.put(CachingConstants.INCLUDED_HEADERS_PROPERTY, cache.getHeadersToIncludeInHash());
                            props.put(CachingConstants.EXCLUDED_HEADERS_PROPERTY, cache.getHeadersToExcludeInHash());
                        }
                    } else {
                        cache.setProtocolType(CachingConstants.HTTP_PROTOCOL_TYPE);
                    }

                } else {
                    OMAttribute hashGeneratorAttr = elem.getAttribute(ATT_HASH_GENERATOR);
                    if (hashGeneratorAttr != null && hashGeneratorAttr.getAttributeValue() != null) {
                        className = hashGeneratorAttr.getAttributeValue();
                    }
                }
                if (className != null && !"".equals(className)) {
                    try {
                        Class generator = Class.forName(className);
                        Object o = generator.newInstance();
                        if (o instanceof DigestGenerator) {
                            cache.setDigestGenerator((DigestGenerator) o);
                        } else {
                            handleException("Specified class for the hashGenerator is not a " +
                                                    "DigestGenerator. It *must* implement " +
                                                    "org.wso2.carbon.mediator.cache.digest.DigestGenerator interface");
                        }

                    } catch (ClassNotFoundException e) {
                        handleException("Unable to load the hash generator class", e);
                    } catch (IllegalAccessException e) {
                        handleException("Unable to access the hash generator class", e);
                    } catch (InstantiationException e) {
                        handleException("Unable to instantiate the hash generator class", e);
                    }
                } else {
                    cache.setDigestGenerator(CachingConstants.DEFAULT_HASH_GENERATOR);
                }

                props.put(CachingConstants.PERMANENTLY_EXCLUDED_HEADERS_STRING,
                        CachingConstants.PERMANENTLY_EXCLUDED_HEADERS);
                cache.getDigestGenerator().init(props);

                OMElement onCacheHitElem = elem.getFirstChildWithName(ON_CACHE_HIT_Q);
                if (onCacheHitElem != null) {
                    OMAttribute sequenceAttr = onCacheHitElem.getAttribute(ATT_SEQUENCE);
                    if (sequenceAttr != null && sequenceAttr.getAttributeValue() != null) {
                        cache.setOnCacheHitRef(FactoryUtils.getFullyQualifiedName(properties, sequenceAttr.getAttributeValue()));
                    } else if (onCacheHitElem.getFirstElement() != null) {
                        cache.setOnCacheHitSequence(new SequenceMediatorFactory()
                                                            .createAnonymousSequence(onCacheHitElem, properties));
                    }
                } else {
                    cache.setOnCacheHitRef(null);
                    cache.setOnCacheHitSequence(null);
                }

                OMElement implElem = elem.getFirstChildWithName(IMPLEMENTATION_Q);
                if (implElem != null) {
                    OMAttribute sizeAttr = implElem.getAttribute(ATT_SIZE);
                    if (sizeAttr != null &&
                            sizeAttr.getAttributeValue() != null) {
                        cache.setInMemoryCacheSize(Integer.parseInt(sizeAttr.getAttributeValue().trim()));
                    } else {
                        cache.setInMemoryCacheSize(-1);
                    }

                    OMAttribute typeAttribute = implElem.getAttribute(ATT_TYPE);
                    if (typeAttribute != null && typeAttribute.getAttributeValue() != null) {
                        cache.setImplementationType(typeAttribute.getAttributeValue().trim());
                    }
                }
            } else {
                handleException("The value for collector has to be either true or false");
            }
        } else {
            handleException("The collector attribute must be specified");
        }

        addAllCommentChildrenToList(elem, cache.getCommentsList());

        return cache;
    }

    /**
     * {@inheritDoc}
     */
    public QName getTagQName() {
        return CachingConstants.CACHE_Q;
    }
}
