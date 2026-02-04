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
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.wso2.carbon.caching.impl.clustering;

import org.apache.axis2.clustering.ClusteringAgent;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.caching.impl.CachingConstants;
import org.wso2.carbon.caching.impl.DataHolder;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

import static org.wso2.carbon.caching.impl.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.carbon.caching.impl.Constants.SUPER_TENANT_ID;

/**
 * Listens for cache entry removals and updates, and sends a cache invalidation request
 * to the other members in the cluster.
 * <p>
 * This is feature intended only when separate local caches are maintained by each node
 * in the cluster.
 */
public class ClusterCacheInvalidationRequestSender implements CacheEntryRemovedListener, CacheEntryUpdatedListener,
        CacheEntryCreatedListener {

    private static final Log log = LogFactory.getLog(ClusterCacheInvalidationRequestSender.class);

    @Override
    public void entryRemoved(CacheEntryEvent event) throws CacheEntryListenerException {
        send(event);
    }

    @Override
    public void entryUpdated(CacheEntryEvent event) throws CacheEntryListenerException {
        send(event);
    }

    @Override
    public void entryCreated(CacheEntryEvent event) throws CacheEntryListenerException {
        send(event);
    }

    /**
     * We will invalidate the particular cache in other nodes whenever
     * there is an remove/update of the local cache in the current node.
     */
    public void send(CacheEntryEvent cacheEntryEvent) {

        int tenantId = SUPER_TENANT_ID;

        if (!cacheEntryEvent.getSource().getName().startsWith(CachingConstants.LOCAL_CACHE_PREFIX) ||
                getClusteringAgent() == null ) {
            return;
        }
        int numberOfRetries = 0;
        if (log.isDebugEnabled()) {
            log.debug(
                    "Sending cache invalidation message to other cluster nodes for '" + cacheEntryEvent.getKey()
                            + "' of the cache '" + cacheEntryEvent.getSource().getName()
                            + "' of the cache manager " + cacheEntryEvent.getSource().getCacheManager()
                            .getName() + "'");
        }

        //Send the cluster message
        ClusterCacheInvalidationRequest.CacheInfo cacheInfo = new ClusterCacheInvalidationRequest.CacheInfo(
                cacheEntryEvent.getSource().getCacheManager().getName(),
                cacheEntryEvent.getSource().getName(),
                cacheEntryEvent.getKey());

        ClusterCacheInvalidationRequest clusterCacheInvalidationRequest = new ClusterCacheInvalidationRequest(
                cacheInfo, SUPER_TENANT_DOMAIN_NAME, tenantId);

        while (numberOfRetries < 60) {
            try {
                getClusteringAgent().sendMessage(clusterCacheInvalidationRequest, true);
                log.debug("Sent [" + clusterCacheInvalidationRequest + "]");
                break;
            } catch (ClusteringFault e) {
                numberOfRetries++;
                if (numberOfRetries < 60) {
                    log.warn("Could not send CacheInvalidationMessage for tenant " +
                            tenantId + ". Retry will be attempted in 2s. Request: " +
                            clusterCacheInvalidationRequest, e);
                } else {
                    log.error("Could not send CacheInvalidationMessage for tenant " +
                            tenantId + ". Several retries failed. Request:" + clusterCacheInvalidationRequest, e);
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private ClusteringAgent getClusteringAgent() {
        return DataHolder.getInstance().getClusteringAgent();
    }

}
