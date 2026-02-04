/*
*  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.caching.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TODO: class description
 * <p/>
 * TODO: Also handle cache eviction - remove items from cache when the cache is full
 */
public class CacheCleanupTask implements Runnable {
    private static final Log log = LogFactory.getLog(CacheCleanupTask.class);
    private List<CacheImpl> caches = new CopyOnWriteArrayList<CacheImpl>();

    public void addCacheForMonitoring(CacheImpl cache) {
        caches.add(cache);
    }

    public void removeCacheFromMonitoring(CacheImpl cache) {
        caches.remove(cache);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void run() {
        if (log.isDebugEnabled()) {
            log.debug("Cache expiry scheduler running...");
        }

        // Get all the caches
        // Get the configurations from the caches
        // Check the timeout policy and clear out old values
        try {
            for (CacheImpl cache : caches) {
                cache.runCacheExpiry();
                if (log.isDebugEnabled()) {
                    log.debug("Cache expiry completed for cache " + cache.getName());
                }
            }
        } catch (IllegalStateException e) {
            log.debug("Error occurred while running CacheCleanupTask", e);
        } catch (Throwable e) {
            log.error("Error occurred while running CacheCleanupTask", e);
        }
    }
}
