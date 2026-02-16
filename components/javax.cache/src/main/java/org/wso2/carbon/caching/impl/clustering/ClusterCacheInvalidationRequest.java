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

import org.apache.axis2.clustering.ClusteringCommand;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.ClusteringMessage;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.caching.impl.CacheImpl;

import java.io.Serializable;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;

import static org.wso2.carbon.caching.impl.CachingConstants.CLEAR_ALL_PREFIX;

/**
 * This is the cluster-wide local cache invalidation message that is sent
 * to all the other nodes in a cluster. This invalidates its own cache.
 *
 * This is based on Axis2 clustering.
 *
 */
public class ClusterCacheInvalidationRequest extends ClusteringMessage {

    private static final transient Log log = LogFactory.getLog(ClusterCacheInvalidationRequest.class);
    private static final long serialVersionUID = 94L;

    private CacheInfo cacheInfo;
    private String tenantDomain;
    private int tenantId;

    public ClusterCacheInvalidationRequest(CacheInfo cacheInfo, String tenantDomain, int tenantId) {

        this.cacheInfo = cacheInfo;
        this.tenantDomain = tenantDomain;
        this.tenantId = tenantId;
    }

    @Override
    public void execute(ConfigurationContext configurationContext) throws ClusteringFault {
        if (log.isDebugEnabled()) {
            log.debug("Received [" + this + "] ");
        }

        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager(cacheInfo.cacheManagerName);
        Cache<Object, Object> cache = cacheManager.getCache(cacheInfo.cacheName);
        if (cache instanceof CacheImpl) {
            if (CLEAR_ALL_PREFIX.equals(cacheInfo.cacheKey)) {
                ((CacheImpl) cache).removeAllLocal();
            } else {
                ((CacheImpl) cache).removeLocal(cacheInfo.cacheKey);
            }
        }
    }

    @Override
    public String toString() {

        return "ClusterCacheInvalidationRequest{" +
                "tenantId=" + tenantId +
                ", tenantDomain='" + tenantDomain + '\'' +
                ", messageId=" + getUuid() +
                ", cacheManager=" + cacheInfo.cacheManagerName +
                ", cache=" + cacheInfo.cacheName +
                ", cacheKey=" +cacheInfo.cacheKey +
                '}';
    }

    @Override
    public ClusteringCommand getResponse() {
        return null;
    }

    public static class CacheInfo implements Serializable {

        private String cacheManagerName;
        private String cacheName;
        private Object cacheKey;

        public CacheInfo(String cacheManagerName, String cacheName, Object cacheKey) {
            this.cacheManagerName = cacheManagerName;
            this.cacheName = cacheName;
            this.cacheKey = cacheKey;
        }
    }

}
