/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.mediator.cache.util;

import com.google.common.net.HttpHeaders;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.wso2.carbon.mediator.cache.CachableResponse;
import org.wso2.carbon.mediator.cache.CachingConstants;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is used to apply the filter.
 */
public class HttpCachingFilter {

    private HttpCachingFilter() {
    }

    /**
     * If the cached response is expired, response needs to be fetched again. And if the cached response have the
     * no-cache header, existing cached response needs to be validated with the backend by sending a request with
     * If-None-Match header with ETag value. This method returns whether a request needs to be sent to the backend
     * or not.
     *
     * @param cachedResponse The cached response.
     * @param synCtx         The message context.
     * @return True if response need to validated with backend.
     */
    public static boolean isValidCacheEntry(CachableResponse cachedResponse, MessageContext synCtx) {
        Map<String, Object> httpHeaders = cachedResponse.getHeaderProperties();
        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        String eTagValue = null;
        boolean isNoCache = false;
        long maxAge = -1;
        //Read cache-control, max-age and ETag header from cached response.
        if (httpHeaders != null) {
            eTagValue = getETagValue(httpHeaders);
            if (httpHeaders.get(HttpHeaders.CACHE_CONTROL) != null) {
                String cacheControlHeaderValue = String.valueOf(httpHeaders.get(HttpHeaders.CACHE_CONTROL));
                List<String> cacheControlHeaders = Arrays.asList(cacheControlHeaderValue.split("\\s*,\\s*"));
                for (String cacheControlHeader : cacheControlHeaders) {
                    if (CachingConstants.NO_CACHE_STRING.equalsIgnoreCase(cacheControlHeader)) {
                        isNoCache = true;
                    }
                    if (cacheControlHeader.contains(CachingConstants.MAX_AGE_STRING)) {
                        maxAge = Long.parseLong(cacheControlHeader.split("=")[1]);
                    }
                }
            }
        }
        return isCachedResponseExpired(cachedResponse, maxAge) ||
                isValidateResponseWithETag(msgCtx, eTagValue, isNoCache);
    }

    /**
     * This method returns whether the cached response need to be validated using ETag.
     *
     * @param msgCtx    The messageContext.
     * @param eTagValue Value of ETag header.
     * @param isNoCache Whether no-cache is exist or not.
     * @return True if the cached response need to be validated using ETag.
     */
    private static boolean isValidateResponseWithETag(org.apache.axis2.context.MessageContext msgCtx, String eTagValue,
                                                      boolean isNoCache) {
        if (isNoCache && StringUtils.isNotEmpty(eTagValue)) {
            Map<String, Object> headerProp = new HashMap<>();
            headerProp.put(CachingConstants.IF_NONE_MATCH, eTagValue);
            msgCtx.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, headerProp);
            return true;
        }
        return false;
    }

    /**
     * This method returns whether cached response is expired or not.
     *
     * @param cachedResponse Cached response.
     * @param maxAge         The value of max-age header.
     * @return True if cached response is expired.
     */
    private static boolean isCachedResponseExpired(CachableResponse cachedResponse, long maxAge) {
        //Validate the TTL of the cached response.
        if (maxAge > -1) {
            long responseExpirationTime = cachedResponse.getResponseFetchedTime() + maxAge * 1000;
            if (responseExpirationTime < System.currentTimeMillis()) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method returns the ETag value.
     *
     * @param httpHeaders Http headers.
     * @return ETag value.
     */
    private static String getETagValue(Map<String, Object> httpHeaders) {
        if (httpHeaders.get(HttpHeaders.ETAG) != null) {
            return String.valueOf(httpHeaders.get(HttpHeaders.ETAG));
        }
        return null;
    }

    /**
     * This method sets the Age header.
     *
     * @param cachedResponse The cached response to be returned.
     * @param msgCtx         The messageContext.
     */
    @SuppressWarnings("unchecked")
    public static void setAgeHeader(CachableResponse cachedResponse,
                                    org.apache.axis2.context.MessageContext msgCtx) {
        Map excessHeaders = new MultiValueMap();
        long responseCachedTime = cachedResponse.getResponseFetchedTime();
        long age = Math.abs((responseCachedTime - System.currentTimeMillis()) / 1000);
        excessHeaders.put(HttpHeaders.AGE, String.valueOf(age));

        msgCtx.setProperty(NhttpConstants.EXCESS_TRANSPORT_HEADERS, excessHeaders);
    }

    /**
     * Set the response fetched time in milliseconds.
     *
     * @param headers  Transport headers.
     * @param response Response to be cached.
     * @throws ParseException throws exception if exception happen while parsing the date.
     */
    public static void setResponseCachedTime(Map<String, String> headers, CachableResponse response) throws
            ParseException {
        long responseFetchedTime;
        String dateHeaderValue;
        if (headers != null && (dateHeaderValue = headers.get(HttpHeaders.DATE)) != null) {
            SimpleDateFormat format = new SimpleDateFormat(CachingConstants.DATE_PATTERN);
            Date d = format.parse(dateHeaderValue);
            responseFetchedTime = d.getTime();
        } else {
            responseFetchedTime = System.currentTimeMillis();
        }
        response.setResponseFetchedTime(responseFetchedTime);
    }

    /**
     * This method returns whether no-store header exists in the response.
     *
     * @param msgCtx MessageContext with the transport headers.
     * @return Whether no-store exists or not.
     */
    @SuppressWarnings("unchecked")
    public static boolean isNoStore(org.apache.axis2.context.MessageContext msgCtx) {
        ConcurrentHashMap<String, Object> headerProperties = new ConcurrentHashMap<>();
        Map<String, String> headers = (Map<String, String>) msgCtx.getProperty(
                org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        String cacheControlHeaderValue = null;

        if(headers != null) {
            //Copying All TRANSPORT_HEADERS to headerProperties Map.
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headerProperties.put(entry.getKey(), entry.getValue());
            }
        }

        if (headerProperties.get(HttpHeaders.CACHE_CONTROL) != null) {
            cacheControlHeaderValue = String.valueOf(headerProperties.get(HttpHeaders.CACHE_CONTROL));
        }

        return StringUtils.isNotEmpty(cacheControlHeaderValue)
                && cacheControlHeaderValue.contains(CachingConstants.NO_STORE_STRING);
    }
}
