/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
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

package org.wso2.micro.integrator.security.handler.oauth;

import org.wso2.config.mapper.ConfigParser;

import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.Caching;

public class CacheProvider {

    public static final String CACHE_MANAGER_NAME = "MICRO_INTEGRATOR_CACHE_MANAGER";
    public static final String SIGNED_JWT_CACHE = "SIGNED_JWT_CACHE";
    public static final String TOKEN_CACHE_NAME = "TOKEN_CACHE";
    public static final String JWKS_CACHE_NAME = "JWKS_CACHE";
    public static final String INVALID_TOKEN_CACHE_NAME = "INVALID_TOKEN_CACHE";
    public static final long DEFAULT_TIMEOUT = 900;

    static {
        createParsedSignJWTCache();
        createTokenCache();
        createJwksCache();
        createInvalidTokenCache();
    }

    /**
     * Create and return SIGNED_JWT_CACHE
     */
    public static Cache createParsedSignJWTCache() {

        return createCache(SIGNED_JWT_CACHE);
    }

    /**
     * Create and return TOKEN_CACHE
     */
    public static Cache createTokenCache() {

        return createCache(TOKEN_CACHE_NAME);
    }

    /**
     * Create and return JWKS_CACHE_NAME
     */
    public static Cache createJwksCache() {

        return createCache(JWKS_CACHE_NAME);
    }

    /**
     * Create and return INVALID_TOKEN_CACHE_NAME
     */
    public static Cache createInvalidTokenCache() {

        return createCache(INVALID_TOKEN_CACHE_NAME);
    }

    private static Cache createCache(String cacheName) {
        long cacheExpiry = DEFAULT_TIMEOUT;
        Object cacheExpiryConfig = ConfigParser.getParsedConfigs().get(OAuthConstants.CACHE_EXPIRY);
        if (cacheExpiryConfig != null) {
            cacheExpiry = ((Number) cacheExpiryConfig).longValue();
        }
        return getCache(CACHE_MANAGER_NAME, cacheName, cacheExpiry, cacheExpiry);
    }

    /**
     * @param cacheName name of the requested cache
     * @return cache
     */
    private static Cache getCache(final String cacheName) {
        return Caching.getCacheManager(CACHE_MANAGER_NAME).getCache(cacheName);
    }

    /**
     * Create the Cache object from the given parameters
     *
     * @param cacheManagerName - Name of the Cache Manager
     * @param cacheName        - Name of the Cache
     * @param modifiedExp      - Value of the MODIFIED Expiry Type
     * @param accessExp        - Value of the ACCESSED Expiry Type
     * @return - The cache object
     */
    public synchronized static Cache getCache(final String cacheManagerName, final String cacheName,
                                              final long modifiedExp, final long accessExp) {

        Iterable<Cache<?, ?>> availableCaches = Caching.getCacheManager(cacheManagerName).getCaches();
        for (Cache cache : availableCaches) {
            if (cache.getName().equalsIgnoreCase(cacheName)) {
                return Caching.getCacheManager(cacheManagerName).getCache(cacheName);
            }
        }

        return Caching.getCacheManager(
                        cacheManagerName).createCacheBuilder(cacheName).
                setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS,
                        modifiedExp)).
                setExpiry(CacheConfiguration.ExpiryType.ACCESSED, new CacheConfiguration.Duration(TimeUnit.SECONDS,
                        accessExp)).setStoreByValue(false).build();
    }

    /**
     *
     * @return SignedJWT Parsed Cache
     */
    public static Cache getSignedJWTParseCache() {

        return getCache(SIGNED_JWT_CACHE);
    }

    /**
     * @return jwks cache
     */
    public static Cache getJwksCache() {
        return getCache(JWKS_CACHE_NAME);
    }

    /**
     * @return token cache
     */
    public static Cache getTokenCache() {
        return getCache(TOKEN_CACHE_NAME);
    }

    /**
     * @return invalid token cache
     */
    public static Cache getInvalidTokenCache() {
        return getCache(INVALID_TOKEN_CACHE_NAME);
    }

}
