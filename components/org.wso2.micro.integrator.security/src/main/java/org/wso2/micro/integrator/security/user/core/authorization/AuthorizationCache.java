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
package org.wso2.micro.integrator.security.user.core.authorization;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.micro.integrator.security.user.core.UserStoreManager;
import org.wso2.micro.integrator.security.user.core.internal.UserStoreMgtDSComponent;
import org.wso2.micro.integrator.security.user.core.util.UserCoreUtil;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.CacheStatistics;
import javax.cache.Caching;

import static org.wso2.micro.integrator.security.SecurityConstants.CASE_INSENSITIVE_USERNAME;

/**
 * Date: Oct 1, 2010 Time: 10:32:26 AM
 */

/**
 * This class is used to cache some of autrhorization information.
 */
public class AuthorizationCache {
    public static final String AUTHORIZATION_CACHE_MANAGER = "AUTHORIZATION_CACHE_MANAGER";
    public static final String AUTHORIZATION_CACHE_NAME = "AUTHORIZATION_CACHE";
    private static Log log = LogFactory.getLog(AuthorizationCache.class);
    private static Boolean isEnable = true;

    private static AuthorizationCache authorizationCache = new AuthorizationCache();

    private AuthorizationCache() {
    }

    /**
     * Gets a new instance of AuthorizationCache.
     *
     * @return A new instance of AuthorizationCache.
     */
    public static AuthorizationCache getInstance() {
        return authorizationCache;
    }


    /**
     * Getting existing cache if the cache available, else returns a newly created cache.
     * This logic handles by javax.cache implementation
     */
    private Cache<AuthorizationKey, AuthorizeCacheEntry> getAuthorizationCache() {
        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = null;
        if (isEnable) {
            CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager(AUTHORIZATION_CACHE_MANAGER);
            cache = cacheManager.getCache(AUTHORIZATION_CACHE_NAME);
        }
        return cache;
    }

    /**
     * Avoiding NullPointerException when the cache is null
     *
     * @return boolean whether given cache is null
     */
    private boolean isCacheNull(Cache<AuthorizationKey, AuthorizeCacheEntry> cache) {
        if (cache == null) {
            if (log.isDebugEnabled()) {
                StackTraceElement[] elemets = Thread.currentThread()
                        .getStackTrace();
                String traceString = "";
                for (int i = 1; i < elemets.length; ++i) {
                    traceString += elemets[i]
                            + System.getProperty("line.separator");
                }
                log.debug("AUTHORIZATION_CACHE doesn't exist in CacheManager:\n"
                        + traceString);
            }
            return true;
        }
        return false;
    }

    /**
     * Adds an entry to the cache. Says whether given user or role is authorized
     * or not.
     *
     * @param serverId     unique identifier for carbon server instance
     * @param userName     Name of the user which was authorized. If this is null
     *                     roleName must not be null.
     * @param resourceId   The resource on which user/role was authorized.
     * @param action       The action which user/role authorized for.
     * @param isAuthorized Whether role/user was authorized or not. <code>true</code> for
     *                     authorized else <code>false</code>.
     */
    public void addToCache(String serverId, int tenantId, String userName,
                           String resourceId, String action, boolean isAuthorized) {

        if (!isCaseSensitiveUsername(userName, tenantId)) {
            userName = userName.toLowerCase();
        }
        // Element already in the cache. Remove it first
        clearCacheEntry(serverId, tenantId, userName, resourceId, action);

        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();
        // Check for null
        if (isCacheNull(cache)) {
            return;
        }
        AuthorizationKey key = new AuthorizationKey(serverId, tenantId, userName, resourceId, action);
        AuthorizeCacheEntry cacheEntry = new AuthorizeCacheEntry(isAuthorized);
        cache.put(key, cacheEntry);
    }

    /**
     * Looks up from cache whether given user is already authorized. If an entry
     * is not found throws an exception.
     *
     * @param serverId   unique identifier for carbon server instance
     * @param tenantId   tenant id
     * @param userName   User name. Both user name and role name cannot be null at the
     *                   same time.
     * @param resourceId The resource which we need to check.
     * @param action     The action on resource.
     * @return <code>true</code> if an entry is found in cache and user/role is
     * authorized. else <code>false</code>.
     * @throws AuthorizationCacheException an entry is not found in the cache.
     */
    public Boolean isUserAuthorized(String serverId, int tenantId,
                                    String userName, String resourceId, String action)
            throws AuthorizationCacheException {

        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();
        // check for null
        if (isCacheNull(cache)) {
            throw new AuthorizationCacheException(
                    "Authorization information not found in the cache.");
        }
        if (!isCaseSensitiveUsername(userName, tenantId)) {
            userName = userName.toLowerCase();
        }
        AuthorizationKey key = new AuthorizationKey(serverId, tenantId,
                userName, resourceId, action);
        if (!cache.containsKey(key)) {
            throw new AuthorizationCacheException(
                    "Authorization information not found in the cache.");
        }

        AuthorizeCacheEntry entry = (AuthorizeCacheEntry) cache.get(key);
        if (entry != null) {
            return entry.isUserAuthorized();
        } else {
            return null;
        }
    }

    /**
     * Clears the cache.
     */
    public void clearCache() {
        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();
        // check for null
        if (isCacheNull(cache)) {
            return;
        }

        cache.removeAll();
    }

    /**
     * Clears a given cache entry.
     *
     * @param serverId   unique identifier for carbon server instance
     * @param tenantId   tenant id
     * @param userName   User name to construct the cache key.
     * @param resourceId Resource id to construct the cache key.
     * @param action     Action to construct the cache key.
     */
    public void clearCacheEntry(String serverId, int tenantId, String userName,
                                String resourceId, String action) {
        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();
        // check for null
        if (isCacheNull(cache)) {
            return;
        }
        if (!isCaseSensitiveUsername(userName, tenantId)) {
            userName = userName.toLowerCase();
        }

        AuthorizationKey key = new AuthorizationKey(serverId, tenantId,
                userName, resourceId, action);
        if (cache.containsKey(key)) {
            cache.remove(key);
        }

    }

    /**
     * Clears the cache by user name.
     *
     * @param userName Name of the user.
     */
    public void clearCacheByUser(int tenantId, String userName) {
        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();
        // check for null
        if (isCacheNull(cache)) {
            return;
        }
        if (!isCaseSensitiveUsername(userName, tenantId)) {
            userName = userName.toLowerCase();
        }
        for (Cache.Entry<AuthorizationKey, AuthorizeCacheEntry> entry : cache) {
            AuthorizationKey authorizationKey = entry.getKey();
            if ((authorizationKey.getTenantId() == tenantId) && (authorizationKey.getUserName().equalsIgnoreCase(userName))) {
                cache.remove(authorizationKey);
            }
        }
    }

    /**
     * Method to get the cache hit rate.
     *
     * @return the cache hit rate.
     */
    public double hitRate() {
        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();
        // check for null
        if (isCacheNull(cache)) {
            return 0.0;
        }

        CacheStatistics stats = cache.getStatistics();
        return (double) stats.getCacheHits()
                / ((double) (stats.getCacheHits() + stats.getCacheMisses()));
    }

    /**
     * Clears the cache by tenantId to facilitate the cache clearance when role
     * authorization is cleared.
     *
     * @param tenantId
     */
    public void clearCacheByTenant(int tenantId) {
        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();

        if (!isCacheNull(cache)) {
            cache.removeAll();
        }
    }

    /**
     * Clears the cache by server Id to facilitate the cache clearance when role
     * authorization is cleared.
     *
     * @param serverId unique identifier for carbon server instance
     */
    public void clearCacheByServerId(String serverId) {
        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();
        // check for null
        if (isCacheNull(cache) || serverId == null) {
            return;
        }

        for (Cache.Entry<AuthorizationKey, AuthorizeCacheEntry> entry : cache) {
            AuthorizationKey authorizationKey = entry.getKey();
            if (serverId.equals(authorizationKey.getServerId())) {
                cache.remove(authorizationKey);
            }

        }
    }

    /**
     * To clear cache when resource authorization is cleared.
     *
     * @param serverId
     * @param tenantID
     * @param resourceID
     */
    public void clearCacheByResource(String serverId, int tenantID,
                                     String resourceID) {
        Cache<AuthorizationKey, AuthorizeCacheEntry> cache = this.getAuthorizationCache();
        // check for null
        if (isCacheNull(cache)) {
            return;
        }

        for (Cache.Entry<AuthorizationKey, AuthorizeCacheEntry> entry : cache) {
            AuthorizationKey authorizationKey = entry.getKey();
            if ((tenantID == (authorizationKey.getTenantId()))
                    && (resourceID.equals(authorizationKey.getResourceId()))
                    && (serverId == null || serverId.equals(authorizationKey
                    .getServerId()))) {
                cache.remove(authorizationKey);
            }
        }

    }

    /**
     * Disable cache completely. Can not enable the cache again.
     */
    public void disableCache() {
        isEnable = false;
    }

    private boolean isCaseSensitiveUsername(String username, int tenantId) {

        if (UserStoreMgtDSComponent.getRealmService() != null) {
            //this check is added to avoid NullPointerExceptions if the osgi is not started yet.
            //as an example when running the unit tests.
            try {
                if (UserStoreMgtDSComponent.getRealmService().getTenantUserRealm(tenantId) != null) {
                    UserStoreManager userStoreManager = (UserStoreManager) UserStoreMgtDSComponent.getRealmService()
                            .getTenantUserRealm(tenantId).getUserStoreManager();
                    UserStoreManager userAvailableUserStoreManager = userStoreManager.getSecondaryUserStoreManager
                            (UserCoreUtil.extractDomainFromName(username));
                    String isUsernameCaseInsensitiveString = userAvailableUserStoreManager.getRealmConfiguration()
                            .getUserStoreProperty(CASE_INSENSITIVE_USERNAME);
                    return !Boolean.parseBoolean(isUsernameCaseInsensitiveString);
                }
            } catch (UserStoreException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Error while reading user store property CaseInsensitiveUsername. Considering as false.");
                }
            }
        }
        return true;
    }
}