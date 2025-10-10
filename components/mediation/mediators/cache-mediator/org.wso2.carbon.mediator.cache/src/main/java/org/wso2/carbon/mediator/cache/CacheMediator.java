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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.continuation.ContinuationStackManager;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.debug.constructs.EnclosedInlinedSequence;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.util.FixedByteArrayOutputStream;
import org.apache.synapse.util.MessageHelper;
import org.wso2.carbon.mediator.cache.digest.DigestGenerator;
import org.wso2.carbon.mediator.cache.util.HttpCachingFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.xml.stream.XMLStreamException;

/**
 * If a request comes to this class it creates and hash of the request and if the hash has a CachedResponse associated
 * with it the mediator will return the response without going to the backend. Otherwise it will pass on the request to
 * the next mediator.
 */
public class CacheMediator extends AbstractMediator implements ManagedLifecycle, EnclosedInlinedSequence {

    /**
     * The value of json content type as it appears in HTTP Content-Type header.
     */
    private final String jsonContentType = "application/json";

    private static final String CONTENT_TYPE = "Content-Type";

    /**
     * Cache configuration ID.
     */
    private String id;

    /**
     * The time duration for which the cache is kept.
     */
    private long timeout = CachingConstants.DEFAULT_TIMEOUT;

    /**
     * This specifies whether the mediator should be in the incoming path (to check the request) or in the outgoing path
     * (to cache the response).
     */
    private boolean collector = false;

    /**
     * The SequenceMediator to the onCacheHit sequence to be executed when an incoming message is identified as an
     * equivalent to a previously received message based on the value defined for the Hash Generator field.
     */
    private SequenceMediator onCacheHitSequence = null;

    /**
     * The reference to the onCacheHit sequence to be executed when an incoming message is identified as an equivalent
     * to a previously received message based on the value defined for the Hash Generator field.
     */
    private String onCacheHitRef = null;

    /**
     * The headers to exclude when caching.
     */
    private String[] headersToExcludeInHash = {""};

    /**
     * The headers to include when caching.
     */
    private String[] headersToIncludeInHash = {""};

    /**
     * This is used to define the logic used by the mediator to evaluate the hash values of incoming messages.
     */
    private DigestGenerator digestGenerator = CachingConstants.DEFAULT_HASH_GENERATOR;

    /**
     * The size of the messages to be cached in memory. If this is -1 then cache can contain any number of messages.
     */
    private int inMemoryCacheSize = CachingConstants.DEFAULT_SIZE;

    /**
     * The compiled pattern for the regex of the responseCodes.
     */
    private Pattern responseCodePattern;

    /**
     * The maximum size of the messages to be cached. This is specified in bytes.
     */
    private int maxMessageSize = CachingConstants.DEFAULT_SIZE;

    /**
     * The regex expression of the HTTP response code to be cached.
     */
    private String responseCodes = CachingConstants.ANY_RESPONSE_CODE;

    /**
     * The protocol type used in caching.
     */
    private String protocolType = CachingConstants.HTTP_PROTOCOL_TYPE;

    /**
     * The http method type that needs to be cached.
     */
    private String[] hTTPMethodsToCache = {CachingConstants.ALL};

    /**
     * This specifies whether the mediator should honor cache-control header.
     */
    private boolean cacheControlEnabled = CachingConstants.DEFAULT_ENABLE_CACHE_CONTROL;

    /**
     * This specifies whether an Age header needs to be included in the cached response.
     */
    private boolean addAgeHeaderEnabled = CachingConstants.DEFAULT_ADD_AGE_HEADER;

    /**
     * Variable to represent NOT_MODIFIED status code.
     */
    private static final String SC_NOT_MODIFIED = "304";

    /**
     * The cache manager to be used.
     */
    private CacheManager cacheManager;

    /**
     * The hash generator used in previous cache implementation
     */
    private String hashGenerator = null;

    /**
     * The cache scope used in previous cache implementation
     */
    private String scope = null;

    /**
     * The implementation used in previous cache implementation
     */
    private String implementationType = null;

    /**
     * To differentiate between the new and previous cache implementations, which will be used for EI Tooling
     */
    private boolean isPreviousCacheImplementation = false;

    public CacheMediator(CacheManager cacheManager) {
        this.id = UUID.randomUUID().toString();
        responseCodePattern = Pattern.compile(responseCodes);
        this.cacheManager = cacheManager;
    }

    /**
     * {@inheritDoc}
     */
    public void init(SynapseEnvironment se) {
        if (onCacheHitSequence != null) {
            onCacheHitSequence.init(se);
        }
        exposeInvalidator(se.createMessageContext());
    }

    /**
     * {@inheritDoc}
     */
    public void destroy() {
        if (onCacheHitSequence != null) {
            onCacheHitSequence.destroy();
        }
        cacheManager.remove(id);
    }

    /**
     * {@inheritDoc}
     */
    public boolean mediate(MessageContext synCtx) {
        if (synCtx.getEnvironment().isDebuggerEnabled()) {
            if (super.divertMediationRoute(synCtx)) {
                return true;
            }
        }
        SynapseLog synLog = getLog(synCtx);
        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : Cache mediator");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }

        ConfigurationContext cfgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext().getConfigurationContext();

        if (cfgCtx == null) {
            handleException("Unable to perform caching,  ConfigurationContext cannot be found", synCtx);
            return false; // never executes.. but keeps IDE happy
        }
        boolean result = true;
        try {
            if (synCtx.isResponse()) {
                processResponseMessage(synCtx, cfgCtx, synLog);
            } else {
                result = processRequestMessage(synCtx, synLog);
            }
        } catch (ExecutionException e) {
            synLog.traceOrDebug("Unable to get the response");

        }
        return result;
    }

    /**
     * Caches the CachableResponse object with currently available attributes against the requestHash in
     * LoadingCache<String, CachableResponse>. Called in the load method of CachingBuilder
     *
     * @param requestHash the request hash that has already been computed
     */
    private CachableResponse cacheNewResponse(String requestHash) {
        CachableResponse response = new CachableResponse();
        response.setRequestHash(requestHash);
        response.setTimeout(timeout);
        return response;
    }

    /**
     * Processes a request message through the cache mediator. Generates the request hash and looks up for a hit, if
     * found; then the specified named or anonymous sequence is executed or marks this message as a response and sends
     * back directly to client.
     *
     * @param synCtx incoming request message
     * @param synLog the Synapse log to use
     * @return should this mediator terminate further processing?
     */
    private boolean processRequestMessage(MessageContext synCtx, SynapseLog synLog)
            throws ExecutionException {
        if (collector) {
            handleException("Request messages cannot be handled in a collector cache", synCtx);
        }
        org.apache.axis2.context.MessageContext msgCtx =
                ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        String requestHash = null;
        try {
            requestHash = digestGenerator.getDigest(((Axis2MessageContext) synCtx).getAxis2MessageContext());
            synCtx.setProperty(CachingConstants.REQUEST_HASH, requestHash);
        } catch (CachingException e) {
            handleException("Error in calculating the hash value of the request", e, synCtx);
        }
        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Generated request hash : " + requestHash);
        }
        CachableResponse cachedResponse = getMediatorCache().get(requestHash);
        synCtx.setProperty(CachingConstants.CACHED_OBJECT, cachedResponse);
        //This is used to store the http method of the request.
        String httpMethod = (String) msgCtx.getProperty(Constants.Configuration.HTTP_METHOD);
        cachedResponse.setHttpMethod(httpMethod);
        cachedResponse.setProtocolType(protocolType);
        cachedResponse.setResponseCodePattern(responseCodePattern);
        cachedResponse.setHTTPMethodsToCache(hTTPMethodsToCache);
        cachedResponse.setMaxMessageSize(maxMessageSize);
        cachedResponse.setCacheControlEnabled(cacheControlEnabled);
        cachedResponse.setAddAgeHeaderEnabled(addAgeHeaderEnabled);
        if (cachedResponse.getResponsePayload() != null || cachedResponse.getResponseEnvelope() != null) {
            // get the response from the cache and attach to the context and change the
            // direction of the message
            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("Cache-hit for message ID : " + synCtx.getMessageID());
            }
            //Validate the response based on max-age and no-cache headers.
            if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(getProtocolType())
                    && cachedResponse.isCacheControlEnabled() &&
                    HttpCachingFilter.isValidCacheEntry(cachedResponse, synCtx)) {
                return true;
            }
            // mark as a response and replace envelope from cache
            synCtx.setResponse(true);
            replaceEnvelopeWithCachedResponse(synCtx, synLog, msgCtx, cachedResponse);
            return false;
        }
        return true;
    }

    /**
     * This method returns the existing cached response.
     * @param synCtx Message context.
     * @param synLog Synapse log.
     * @param msgCtx Axis2 contex.
     * @param cachedResponse Cached response.
     */
    private void replaceEnvelopeWithCachedResponse(MessageContext synCtx, SynapseLog synLog,
                                                   org.apache.axis2.context.MessageContext msgCtx, CachableResponse cachedResponse) {
        Map<String, Object> headerProperties;
        try {
            if (cachedResponse.isJson()) {
                byte[] payload = cachedResponse.getResponsePayload();
                OMElement response = JsonUtil.getNewJsonPayload(msgCtx, payload, 0,
                        payload.length, false, false);
                if (msgCtx.getEnvelope().getBody().getFirstElement() != null) {
                    msgCtx.getEnvelope().getBody().getFirstElement().detach();
                }
                msgCtx.getEnvelope().getBody().addChild(response);

            } else {
                msgCtx.setEnvelope(MessageHelper.cloneSOAPEnvelope(cachedResponse.getResponseEnvelope()));
            }
        } catch (AxisFault e) {
            handleException("Error creating response OM from cache : " + id, synCtx);
        }
        if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(getProtocolType())) {
            if (cachedResponse.getStatusCode() != null) {
                msgCtx.setProperty(NhttpConstants.HTTP_SC,
                        Integer.parseInt(cachedResponse.getStatusCode()));
            }
            if (cachedResponse.getStatusReason() != null) {
                msgCtx.setProperty(PassThroughConstants.HTTP_SC_DESC, cachedResponse.getStatusReason());
            }
            //Set Age header to the cached response.
            if (cachedResponse.isAddAgeHeaderEnabled()) {
                HttpCachingFilter.setAgeHeader(cachedResponse, msgCtx);
            }
        }
        if (msgCtx.isDoingREST()) {

            msgCtx.removeProperty(PassThroughConstants.NO_ENTITY_BODY);
            msgCtx.removeProperty(Constants.Configuration.CONTENT_TYPE);
        }
        if ((headerProperties = cachedResponse.getHeaderProperties()) != null) {
            Map clonedMap = new HashMap();
            clonedMap.putAll(headerProperties);
            msgCtx.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, clonedMap);
            msgCtx.setProperty(Constants.Configuration.MESSAGE_TYPE,
                    clonedMap.get(Constants.Configuration.MESSAGE_TYPE));
            msgCtx.setProperty(Constants.Configuration.CONTENT_TYPE,
                    headerProperties.get(CONTENT_TYPE));
        }

        // take specified action on cache hit
        if (onCacheHitSequence != null) {
            // if there is an onCacheHit use that for the mediation
            synLog.traceOrDebug("Delegating message to the onCacheHit "
                    + "Anonymous sequence");
            ContinuationStackManager.addReliantContinuationState(synCtx, 0, getMediatorPosition());
            if (onCacheHitSequence.mediate(synCtx)) {
                ContinuationStackManager.removeReliantContinuationState(synCtx);
            }

        } else if (onCacheHitRef != null) {
            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("Delegating message to the onCacheHit "
                        + "sequence : " + onCacheHitRef);
            }
            ContinuationStackManager.updateSeqContinuationState(synCtx, getMediatorPosition());
            synCtx.getSequence(onCacheHitRef).mediate(synCtx);

        } else {

            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("Request message " + synCtx.getMessageID() +
                        " was served from the cache");
            }
            // send the response back if there is not onCacheHit is specified
            synCtx.setTo(null);
            //Todo continueExecution if needed
            Axis2Sender.sendBack(synCtx);

        }
    }

    /**
     * Process a response message through this cache mediator. This finds the Cache used, and updates it for the
     * corresponding request hash
     *
     * @param synLog the Synapse log to use
     * @param synCtx the current message (response)
     * @param cfgCtx the abstract context in which the cache will be kept
     */
    @SuppressWarnings("unchecked")
    private void processResponseMessage(MessageContext synCtx, ConfigurationContext cfgCtx, SynapseLog synLog) {
        if (!collector) {
            handleException("Response messages cannot be handled in a non collector cache", synCtx);
        }
        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        CachableResponse response = (CachableResponse) synCtx.getProperty(CachingConstants.CACHED_OBJECT);

        if (response != null) {
            boolean toCache = true;
            if (CachingConstants.HTTP_PROTOCOL_TYPE.equals(response.getProtocolType())) {
                Object httpStatus = msgCtx.getProperty(NhttpConstants.HTTP_SC);
                String statusCode = null;
                //Honor no-store header if cacheControlEnabled.
                // If "no-store" header presents in the response, returned response can not be cached.
                if (response.isCacheControlEnabled() && HttpCachingFilter.isNoStore(msgCtx)) {
                    response.clean();
                    return;
                }
                //Need to check the data type of HTTP_SC to avoid classcast exceptions.
                if (httpStatus instanceof String) {
                    statusCode = ((String) httpStatus).trim();
                } else if (httpStatus != null) {
                    statusCode = String.valueOf(httpStatus);
                }

                if (statusCode != null) {
                    //If status code is SC_NOT_MODIFIED then return the cached response.
                    if (statusCode.equals(SC_NOT_MODIFIED)) {
                        replaceEnvelopeWithCachedResponse(synCtx, synLog, msgCtx, response);
                        return;
                    }
                    // Now create matcher object.
                    Matcher m = response.getResponseCodePattern().matcher(statusCode);
                    if (m.matches()) {
                        response.setStatusCode(statusCode);
                        response.setStatusReason((String) msgCtx.getProperty(PassThroughConstants.HTTP_SC_DESC));
                    } else {
                        toCache = false;
                    }
                }

                if (toCache) {
                    toCache = false;
                    String httpMethod = response.getHttpMethod();
                    for (String method : response.getHTTPMethodsToCache()) {
                        if (method.equals("*") || method.equals(httpMethod)) {
                            toCache = true;
                            break;
                        }
                    }
                }
            }
            if (toCache) {
                if (JsonUtil.hasAJsonPayload(msgCtx)) {
                    byte[] responsePayload = JsonUtil.jsonPayloadToByteArray(msgCtx);
                    if (response.getMaxMessageSize() > -1 &&
                            responsePayload.length > response.getMaxMessageSize()) {
                        synLog.traceOrDebug(
                                "Message size exceeds the upper bound for caching, request will not be cached");
                        return;
                    }
                    response.setResponsePayload(responsePayload);
                    response.setResponseEnvelope(null);
                    response.setJson(true);
                } else {
                    SOAPEnvelope clonedEnvelope = MessageHelper.cloneSOAPEnvelope(synCtx.getEnvelope());
                    if (response.getMaxMessageSize() > -1) {
                        FixedByteArrayOutputStream fbaos = new FixedByteArrayOutputStream(
                                response.getMaxMessageSize());
                        try {
                            clonedEnvelope.serialize(fbaos);
                        } catch (XMLStreamException e) {
                            handleException("Error in checking the message size", e, synCtx);
                        } catch (SynapseException syne) {
                            synLog.traceOrDebug(
                                    "Message size exceeds the upper bound for caching, request will not be cached");
                            return;
                        } finally {
                            try {
                                fbaos.close();
                            } catch (IOException e) {
                                handleException("Error occurred while closing the FixedByteArrayOutputStream ", e,
                                        synCtx);
                            }
                        }
                    }

                    response.setResponsePayload(null);
                    response.setResponseEnvelope(clonedEnvelope);
                    response.setJson(false);

                }

                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug("Storing the response message into the cache with ID : "
                            + id + " for request hash : " + response.getRequestHash());
                }//remove
                if (synLog.isTraceOrDebugEnabled()) {
                    synLog.traceOrDebug(
                            "Storing the response for the message with ID : " + synCtx.getMessageID() + " " +
                                    "with request hash ID : " + response.getRequestHash() + " in the cache");
                }

                Map<String, String> headers =
                        (Map<String, String>) msgCtx.getProperty(
                                org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
                String messageType = (String) msgCtx.getProperty(Constants.Configuration.MESSAGE_TYPE);
                Map<String, Object> headerProperties = Collections.synchronizedMap(
                        new TreeMap<>(new Comparator<String>() {
                            public int compare(String o1, String o2) {
                                return o1.compareToIgnoreCase(o2);
                            }
                        })
                );

                //Store the response fetched time.
                if (response.isCacheControlEnabled() || response.isAddAgeHeaderEnabled()) {
                    try {
                        HttpCachingFilter.setResponseCachedTime(headers, response);
                    } catch (ParseException e) {
                        synLog.auditWarn("Error occurred while parsing the date." + e.getMessage());
                    }
                }

                if(headers != null) {
                    //Individually copying All TRANSPORT_HEADERS to headerProperties Map instead putting whole
                    //TRANSPORT_HEADERS map as single Key/Value pair to fix hazelcast serialization issue.
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        headerProperties.put(entry.getKey(), entry.getValue());
                    }
                }

                headerProperties.put(Constants.Configuration.MESSAGE_TYPE, messageType);
                headerProperties.put(CachingConstants.CACHE_KEY, response.getRequestHash());
                response.setHeaderProperties(headerProperties);
                msgCtx.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, headerProperties);

            } else {
                response.clean();
            }
        } else {
            synLog.auditWarn("A response message without a valid mapping to the " +
                    "request hash found. Unable to store the response in cache");
        }

    }

    /**
     * Creates default cache to keep mediator cache.
     *
     * @return global cache
     */
    public LoadingCache<String, CachableResponse> getMediatorCache() {
        LoadingCache<String, CachableResponse> cache = cacheManager.get(id);
        if (cache == null) {
            if (inMemoryCacheSize > -1) {
                cache = CacheBuilder.newBuilder().expireAfterWrite(timeout,
                        TimeUnit.SECONDS).maximumSize(inMemoryCacheSize)
                        .build(new CacheLoader<String, CachableResponse>() {
                            @Override
                            public CachableResponse load(String requestHash) throws Exception {
                                return cacheNewResponse(requestHash);
                            }
                        });
            } else {
                cache = CacheBuilder.newBuilder().expireAfterWrite(timeout,
                        TimeUnit.SECONDS).build(
                        new CacheLoader<String, CachableResponse>() {
                            @Override
                            public CachableResponse load(String requestHash) throws Exception {
                                return cacheNewResponse(requestHash);
                            }
                        });
            }
            cacheManager.put(id, cache);
        }
        return cache;
    }

    /**
     * {@inheritDoc}
     */
    public Mediator getInlineSequence(SynapseConfiguration synCfg, int inlinedSeqIdentifier) {
        if (inlinedSeqIdentifier == 0) {
            if (onCacheHitSequence != null) {
                return onCacheHitSequence;
            } else if (onCacheHitRef != null) {
                return synCfg.getSequence(onCacheHitRef);
            }
        }
        return null;
    }

    /**
     * Exposes the whole mediator cache through jmx MBean.
     */
    public void exposeInvalidator(MessageContext msgCtx) {
        String name = "org.wso2.carbon.mediator.cache:type=Cache,tenant=" +
                org.wso2.micro.core.Constants.SUPER_TENANT_DOMAIN_NAME;
        try {
            ObjectName cacheMBeanObjName = new ObjectName(name);
            MBeanServer mserver = getMBeanServer();
            Set<ObjectName> set = mserver.queryNames(cacheMBeanObjName, null);
            if (set.isEmpty()) {
                MediatorCacheInvalidator cacheMBean = new MediatorCacheInvalidator(cacheManager,
                        org.wso2.micro.core.Constants.SUPER_TENANT_DOMAIN_NAME,
                        org.wso2.micro.core.Constants.SUPER_TENANT_ID, msgCtx);
                mserver.registerMBean(cacheMBean, cacheMBeanObjName);
            }
        } catch (MalformedObjectNameException e) {
            handleException("The format of the string does not correspond to a valid ObjectName.", e, msgCtx);
        } catch (NotCompliantMBeanException e) {
            handleException("MBean with the name " + name + " is already registered.", e, msgCtx);
        } catch (InstanceAlreadyExistsException e) {
            handleException("MBean implementation is not compliant with JMX specification standard MBean.", e, msgCtx);
        } catch (MBeanRegistrationException e) {
            handleException("Could not register MediatorCacheInvalidator MBean.", e, msgCtx);
        }
    }

    /**
     * Obtains existing mbean server instance or create new one.
     *
     * @return MBeanServer instance
     */
    private MBeanServer getMBeanServer() {
        MBeanServer mserver;
        if (MBeanServerFactory.findMBeanServer(null).size() > 0) {
            mserver = MBeanServerFactory.findMBeanServer(null).get(0);
        } else {
            mserver = MBeanServerFactory.createMBeanServer();
        }
        return mserver;
    }

    /**
     * This method gives the DigestGenerator to evaluate the hash values of incoming messages.
     *
     * @return DigestGenerator used evaluate hash values.
     */
    public DigestGenerator getDigestGenerator() {
        return digestGenerator;
    }

    /**
     * This method sets the DigestGenerator to evaluate the hash values of incoming messages.
     *
     * @param digestGenerator DigestGenerator to be set to evaluate hash values.
     */
    public void setDigestGenerator(DigestGenerator digestGenerator) {
        this.digestGenerator = digestGenerator;
    }

    /**
     * This method gives the timeout period in milliseconds.
     *
     * @return timeout in milliseconds
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * This method sets the timeout period as milliseconds.
     *
     * @param timeout millisecond timeout period to be set.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * This method gives whether the mediator should be in the incoming path or in the outgoing path as a boolean.
     *
     * @return boolean true if incoming path false if outgoing path.
     */
    public boolean isCollector() {
        return collector;
    }

    /**
     * This method sets whether the mediator should be in the incoming path or in the outgoing path as a boolean.
     *
     * @param collector boolean value to be set as collector.
     */
    public void setCollector(boolean collector) {
        this.collector = collector;
    }

    /**
     * This method gives array of headers that would be excluded when hashing.
     *
     * @return array of headers to exclude from hashing
     */
    public String[] getHeadersToExcludeInHash() {
        return headersToExcludeInHash;
    }

    /**
     * This method sets the array of headers that would be excluded when hashing.
     *
     * @param headersToExcludeInHash array of headers to exclude from hashing.
     */
    public void setHeadersToExcludeInHash(String... headersToExcludeInHash) {
        this.headersToExcludeInHash = headersToExcludeInHash;
    }

    /**
     * This method gives array of headers that would be included when hashing.
     *
     * @return array of headers to included in hashing
     */
    public String[] getHeadersToIncludeInHash() {
        return headersToIncludeInHash;
    }

    /**
     * This method sets the array of headers that would be included when hashing.
     *
     * @param headersToIncludeInHash array of headers to include in hashing.
     */
    public void setHeadersToIncludeInHash(String... headersToIncludeInHash) {
        this.headersToIncludeInHash = headersToIncludeInHash;
    }


    /**
     * This method gives SequenceMediator to be executed.
     *
     * @return sequence mediator to be executed.
     */
    public SequenceMediator getOnCacheHitSequence() {
        return onCacheHitSequence;
    }

    /**
     * This method sets SequenceMediator to be executed.
     *
     * @param onCacheHitSequence sequence mediator to be set.
     */
    public void setOnCacheHitSequence(SequenceMediator onCacheHitSequence) {
        this.onCacheHitSequence = onCacheHitSequence;
    }

    /**
     * This method gives reference to the onCacheHit sequence to be executed.
     *
     * @return reference to the onCacheHit sequence.
     */
    public String getOnCacheHitRef() {
        return onCacheHitRef;
    }

    /**
     * This method sets reference to the onCacheHit sequence to be executed.
     *
     * @param onCacheHitRef reference to the onCacheHit sequence to be set.
     */
    public void setOnCacheHitRef(String onCacheHitRef) {
        this.onCacheHitRef = onCacheHitRef;
    }

    /**
     * This method gives the size of the messages to be cached in memory.
     *
     * @return memory cache size in bytes.
     */
    public int getInMemoryCacheSize() {
        return inMemoryCacheSize;
    }

    /**
     * This method sets the size of the messages to be cached in memory.
     *
     * @param inMemoryCacheSize value(number of bytes) to be set as memory cache size.
     */
    public void setInMemoryCacheSize(int inMemoryCacheSize) {
        this.inMemoryCacheSize = inMemoryCacheSize;
    }

    /**
     * This method gives the HTTP method that needs to be cached.
     *
     * @return the HTTP method to be cached
     */
    public String[] getHTTPMethodsToCache() {
        return hTTPMethodsToCache;
    }

    /**
     * This sets the HTTP method that needs to be cached.
     *
     * @param hTTPMethodToCache the HTTP method to be cached
     */
    public void setHTTPMethodsToCache(String... hTTPMethodToCache) {
        this.hTTPMethodsToCache = hTTPMethodToCache;
    }

    /**
     * @return the protocol type of the messages
     */
    public String getProtocolType() {
        return protocolType;
    }

    /**
     * This method sets protocolType of the messages.
     *
     * @param protocolType protocol type of the messages.
     */
    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    /**
     * @return The regex expression of the HTTP response code of the messages to be cached
     */
    public String getResponseCodes() {
        return responseCodes;
    }

    /**
     * This method sets the response codes that needs to be cached.
     *
     * @param responseCodes the response codes to be cached in regex form.
     */
    public void setResponseCodes(String responseCodes) {
        this.responseCodes = responseCodes;
        responseCodePattern = Pattern.compile(responseCodes);
    }

    /**
     * This method gives the maximum size of the messages to be cached in bytes.
     *
     * @return maximum size of the messages to be cached in bytes.
     */
    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * This method sets the maximum size of the messages to be cached in bytes.
     *
     * @param maxMessageSize maximum size of the messages to be set in bytes.
     */
    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    /**
     * This method returns whether cache-control is enabled or not.
     *
     * @return whether cache-control is enabled or not.
     */
    public boolean isCacheControlEnabled() {
        return cacheControlEnabled;
    }

    /**
     * This method sets whether cache-control is enabled or not.
     *
     * @param cacheControlEnabled whether cache-control is enabled or not.
     */
    public void setCacheControlEnabled(boolean cacheControlEnabled) {
        this.cacheControlEnabled = cacheControlEnabled;
    }

    /**
     * This method returns whether an Age header needs to be included or not.
     *
     * @return whether an Age header needs to be included or not.
     */
    public boolean isAddAgeHeaderEnabled() {
        return addAgeHeaderEnabled;
    }

    /**
     * This method sets whether an Age header needs to be included or not.
     *
     * @param addAgeHeaderEnabled whether an Age header needs to be included or not.
     */
    public void setAddAgeHeaderEnabled(boolean addAgeHeaderEnabled) {
        this.addAgeHeaderEnabled = addAgeHeaderEnabled;
    }

    /**
     * This method sets the id of the cache configuration.
     *
     * @param id id of the cache configuration
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * This method returns the id of the cache configuration.
     *
     * @return id of the cache configuration
     */
    public String getId() {
        return this.id;
    }

    /**
     * This method sets the hash generator class.
     *
     * @param hashGenerator hash generator used to evaluate the hash value
     */
    public void setHashGenerator(String hashGenerator) {
        this.hashGenerator = hashGenerator;
    }

    /**
     * This returns the hash generator used to evaluate the hash value.
     *
     * @return hash generator used to evaluate the hash value
     */
    public String getHashGenerator() {
        return this.hashGenerator;
    }

    /**
     * This method sets the scope of the cache.
     *
     * @param scope scope of the cache
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * This method returns the scope of the cache.
     *
     * @return the scope of the cache
     */
    public String getScope() {
        return this.scope;
    }

    /**
     * This method sets the cache implementation type.
     *
     * @param implementationType cache implementation type
     */
    public void setImplementationType(String implementationType) {
        this.implementationType = implementationType;
    }

    /**
     * This method returns the cache implementation type.
     *
     * @return cache implementation type
     */
    public String getImplementationType() {
        return this.implementationType;
    }

    /**
     * This method returns whether this represents the previous cache implementation or not.
     *
     * @return whether this represents the previous cache implementation or not
     */
    public boolean isPreviousCacheImplementation() {
        return isPreviousCacheImplementation;
    }

    /**
     * This method sets whether this represents the previous cache implementation or not.
     *
     * @param previousCacheImplementation whether this represents the previous cache implementation or not
     */
    public void setPreviousCacheImplementation(boolean previousCacheImplementation) {
        isPreviousCacheImplementation = previousCacheImplementation;
    }
}
