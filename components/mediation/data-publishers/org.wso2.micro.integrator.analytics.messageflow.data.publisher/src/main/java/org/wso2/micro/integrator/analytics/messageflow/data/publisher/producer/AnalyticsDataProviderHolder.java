/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.analytics.messageflow.data.publisher.producer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticConstants;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Holder class to initialize and hold the custom data provider
 */
public class AnalyticsDataProviderHolder {
    private static final Log log = LogFactory.getLog(AnalyticsDataProviderHolder.class);
    private static volatile AnalyticsDataProviderHolder instance;

    private AnalyticsCustomDataProvider analyticsCustomDataProvider;

    private AnalyticsDataProviderHolder() {
        loadCustomDataProvider();
    }

    /**
     * Get the instance of the holder
     *
     * @return Instance of the holder
     */
    public static AnalyticsDataProviderHolder getInstance() {
        if (instance == null) {
            synchronized (AnalyticsDataProviderHolder.class) {
                if (instance == null) {
                    instance = new AnalyticsDataProviderHolder();
                }
            }
        }
        return instance;
    }

    private void loadCustomDataProvider() {
        try {
            String analyticsCustomDataProviderClass = SynapsePropertiesLoader.getPropertyValue(
                    ElasticConstants.SynapseConfigKeys.ELASTICSEARCH_CUSTOM_DATA_PROVIDER_CLASS, null);
            if (analyticsCustomDataProviderClass == null) {
                return;
            }
            Class<?> clazz = Class.forName(analyticsCustomDataProviderClass);
            Constructor<?> constructor = clazz.getConstructors()[0];
            analyticsCustomDataProvider = (AnalyticsCustomDataProvider) constructor.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException e) {
            log.error("Error in obtaining custom producer class", e);
        }
    }

    /**
     * Create if absent and return the custom data provider
     *
     * @return Custom data provider
     */
    public AnalyticsCustomDataProvider getAnalyticsCustomDataProvider() {
        return analyticsCustomDataProvider;
    }
}
