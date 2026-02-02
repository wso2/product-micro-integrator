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

import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.core.util.Axis2ConfigurationContextObserver;

import javax.cache.Caching;

import static org.wso2.carbon.caching.impl.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.carbon.caching.impl.Constants.SUPER_TENANT_ID;

/**
 * TODO: class description
 */
public class CachingAxisConfigurationObserver implements Axis2ConfigurationContextObserver {
    private static Log log = LogFactory.getLog(CachingAxisConfigurationObserver.class);

    @Override
    public void creatingConfigurationContext(int tenantId) {
        // Nothing to do
    }

    @Override
    public void createdConfigurationContext(ConfigurationContext configurationContext) {
        // Nothing to do
    }

    /**
     * In this method, we stop & remove all caches belonging to this tenant
     * Issue: if the tenant is active on other nodes, those also may get removed?
     * Remove only local caches on order to deal with issues that can arise when the cache in distributed
     *
     * @param configurationContext to Get the required information related to tenant
     */
    @Override
    public void terminatingConfigurationContext(ConfigurationContext configurationContext) {
        int tenantId = SUPER_TENANT_ID;
        if (log.isDebugEnabled()) {
            log.debug("Remove all caches of the tenant " + tenantId);
        }
        ((CacheManagerFactoryImpl) Caching.getCacheManagerFactory()).
                removeAllCacheManagers(SUPER_TENANT_DOMAIN_NAME);
    }

    @Override
    public void terminatedConfigurationContext(ConfigurationContext configurationContext) {
        // Nothing to do
    }
}
