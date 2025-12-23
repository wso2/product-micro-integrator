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

import org.apache.axis2.clustering.ClusteringAgent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.core.services.ServerConfigurationService;

import javax.cache.spi.AnnotationProvider;

/**
 * TODO: class description
 */
public class DataHolder {
    private static final Log log = LogFactory.getLog(DataHolder.class);
    private static DataHolder instance = new DataHolder();

    private DistributedMapProvider distributedMapProvider;
    private ServerConfigurationService serverConfigurationService;
    private ClusteringAgent clusteringAgent;
    private CachingProviderImpl cachingProvider = new CachingProviderImpl();
    private AnnotationProvider annotationProvider = new AnnotationProviderImpl();

    public static DataHolder getInstance() {
        return instance;
    }

    private DataHolder() {
    }

    public DistributedMapProvider getDistributedMapProvider() {
        return distributedMapProvider;
    }

    public ServerConfigurationService getServerConfigurationService() {
        if (this.serverConfigurationService == null) {
            String msg = "Before activating javax caching  bundle, an instance of "
                    + "ServerConfigurationService should be in existence";
            log.error(msg);
        }
        return this.serverConfigurationService;
    }

    public void setDistributedMapProvider(DistributedMapProvider distributedMapProvider) {
        this.distributedMapProvider = distributedMapProvider;
        try {
            if (distributedMapProvider != null) {
                cachingProvider.switchToDistributedMode();
            }
        } catch (Exception e) {
            log.error("Cannot setDistributedMapProvider", e);
        }
    }

    public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
        this.serverConfigurationService = serverConfigurationService;
    }

    public void setClusteringAgent(ClusteringAgent clusteringAgent) {
        this.clusteringAgent = clusteringAgent;
    }

    public CachingProviderImpl getCachingProvider() {
        return cachingProvider;
    }

    public AnnotationProvider getAnnotationProvider() {
        return annotationProvider;
    }

    public ClusteringAgent getClusteringAgent() {
        return clusteringAgent;
    }
}
