/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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
package org.wso2.micro.integrator.analytics.messageflow.data.publisher.observer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingFlow;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.ei.EIStatisticsPublisher;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.StatisticsPublisher;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticStatisticsPublisher;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.MoesifStatisticsPublisher;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.util.MediationDataPublisherConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AnalyticsMediationFlowObserver implements MessageFlowObserver, TenantInformation {

    private static final Log log = LogFactory.getLog(AnalyticsMediationFlowObserver.class);
    private int tenantId = -1234;
    private final Collection<StatisticsPublisher> statPublishers = new ArrayList<>();

    public AnalyticsMediationFlowObserver(List<String> publisherTypes) {
        if (publisherTypes == null || publisherTypes.isEmpty()) {
            statPublishers.add(ElasticStatisticsPublisher.GetInstance());
        } else {
            boolean hasAnyPublisher = false;

            if (publisherTypes.contains(MediationDataPublisherConstants.DATABRIDGE_PUBLISHER_TYPE)) {
                statPublishers.add(EIStatisticsPublisher.GetInstance());
                hasAnyPublisher = true;
            }
            if (publisherTypes.contains(MediationDataPublisherConstants.LOG_PUBLISHER_TYPE)) {
                statPublishers.add(ElasticStatisticsPublisher.GetInstance());
                hasAnyPublisher = true;
            }
            if (publisherTypes.contains(MediationDataPublisherConstants.MOESIF_PUBLISHER_TYPE)) {
                String moesifApplicationId = SynapsePropertiesLoader.getPropertyValue(
                        MediationDataPublisherConstants.MOESIF_APPLICATION_ID, null);
                if (null == moesifApplicationId) {
                    log.error("Moesif analytics publisher was not enabled as the Moesif application ID was" +
                            " not found");
                    return;
                    //TODO: Re-think how to handle this scenario
                }
                statPublishers.add(MoesifStatisticsPublisher.GetInstance());
                hasAnyPublisher = true;
            }
            if (!hasAnyPublisher) {
                statPublishers.add(ElasticStatisticsPublisher.GetInstance());
            }
        }
    }

    @Override
    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("Shutting down the mediation statistics observer of DAS");
        }
    }

    @Override
    public void updateStatistics(PublishingFlow flow) {
        statPublishers.forEach( statisticsPublisher -> {
            try {
                statisticsPublisher.process(flow, tenantId);
            } catch (Exception e) {
                log.error("failed to update statics from DAS publisher", e);
            }
        });
    }

    @Override
    public int getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(int i) {
        tenantId = i;
    }
}
