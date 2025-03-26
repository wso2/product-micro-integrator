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
package org.wso2.micro.integrator.analytics.messageflow.data.publisher.services;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.aspects.ComponentType;
import org.apache.synapse.aspects.flow.statistics.data.raw.BasicStatisticDataUnit;
import org.apache.synapse.aspects.flow.statistics.data.raw.CallbackDataUnit;
import org.apache.synapse.aspects.flow.statistics.data.raw.StatisticDataUnit;
import org.apache.synapse.aspects.flow.statistics.data.raw.StatisticsLog;
import org.apache.synapse.aspects.flow.statistics.elasticsearch.ElasticMetadata;
import org.apache.synapse.aspects.flow.statistics.log.StatisticsReportingEvent;
import org.apache.synapse.aspects.flow.statistics.log.StatisticsReportingEventHolder;
import org.apache.synapse.aspects.flow.statistics.log.templates.AbstractStatisticEvent;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingFlow;
import org.apache.synapse.aspects.flow.statistics.util.TracingDataCollectionHelper;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.data.MessageFlowObserverStore;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.producer.AnalyticsCustomDataProvider;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.producer.AnalyticsDataProviderHolder;
import org.wso2.micro.integrator.initializer.services.SynapseEnvironmentService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.apache.synapse.SynapseConstants.ANALYTICS_METADATA;

/**
 * Worker which processes statistic events and publish to analytic server.
 */
public class MessageFlowReporterThread extends Thread {
    public static final String IGNORE_ELEMENT = "IgnoreElement";
    public static final int DEFAULT_HASHCODE = 0;
    private static Log log = LogFactory.getLog(MessageFlowReporterThread.class);

    private volatile boolean shutdownRequested = false;

    private MessageFlowObserverStore messageFlowObserverStore;

    /**
     * The reference to the synapse environment service
     */
    private SynapseEnvironmentService synapseEnvironmentService;

    private long delay = 5000;

    public MessageFlowReporterThread(SynapseEnvironmentService synEnvSvc,
                                     MessageFlowObserverStore messageFlowObserverStore) {
        this.synapseEnvironmentService = synEnvSvc;
        this.messageFlowObserverStore = messageFlowObserverStore;
    }

    public void setDelay(long delay) {
        if (log.isDebugEnabled()) {
            log.debug("Mediation statistics reporter delay set to " + delay + " ms");
        }
        this.delay = delay;
    }

    private void delay() {
        if (delay <= 0) {
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignore) {

        }
    }

    public void run() {
        StatisticsReportingEventHolder statisticsReportingEventHolder;
        while (!shutdownRequested) {
            try {
                statisticsReportingEventHolder = synapseEnvironmentService.getSynapseEnvironment().getMessageDataStore()
                        .dequeue();
                if (statisticsReportingEventHolder != null) {
                    processAndPublishEventList(statisticsReportingEventHolder);
                } else {
                    delay();
                }
            } catch (Exception exception) {//catching throwable since this shouldn't fail
                log.error("Error in mediation flow statistic data consumer while consuming data", exception);
            }
        }
    }

    private void processAndPublishEventList(StatisticsReportingEventHolder statisticsReportingEventHolder) {
        List<StatisticsReportingEvent> remainingEvents = new ArrayList<>();
        List<StatisticsLog> messageFlowLogs = new ArrayList<>();

        for (StatisticsReportingEvent event : statisticsReportingEventHolder.getEventList()) {
            if (event.getEventType() == AbstractStatisticEvent.EventType.STATISTICS_OPEN_EVENT) {
                StatisticDataUnit statisticDataUnit = (StatisticDataUnit) event.getDataUnit();
                StatisticsLog statisticsLog = new StatisticsLog(statisticDataUnit);

                if (statisticDataUnit.getCurrentIndex() < messageFlowLogs.size()) {
                    messageFlowLogs.set(statisticDataUnit.getCurrentIndex(), statisticsLog);
                } else {

                    // Filling the gaps, if messageFlowLogs size is less than current index given
                    for (int i = messageFlowLogs.size(); i < statisticDataUnit.getCurrentIndex(); i++) {
                        messageFlowLogs.add(null);
                    }

                    // After filling gaps, add the new statistic-data-unit
                    messageFlowLogs.add(statisticDataUnit.getCurrentIndex(), statisticsLog);
                }

                if (statisticDataUnit.getParentList() != null && !statisticDataUnit.getParentList().isEmpty()) {
                    for (int parent : statisticDataUnit.getParentList()) {
                        messageFlowLogs.get(parent).setChildren(statisticDataUnit.getCurrentIndex());
                    }
                } else if (statisticDataUnit.getParentIndex() > -1) {
                    messageFlowLogs.get(statisticDataUnit.getParentIndex())
                            .setChildren(statisticDataUnit.getCurrentIndex());
                }

            } else {
                remainingEvents.add(event);
            }
        }

        for (StatisticsReportingEvent event : remainingEvents) {

            switch (event.getEventType()) {
            case STATISTICS_CLOSE_EVENT:

                StatisticDataUnit dataUnit = (StatisticDataUnit) event.getDataUnit();

                StatisticsLog statisticsLog = messageFlowLogs.get(dataUnit.getCurrentIndex());

                int parentIndex = statisticsLog.getParentIndex();
                if (parentIndex == -1 || messageFlowLogs.get(parentIndex).isFlowSplittingMediator()) {
                    statisticsLog.setParentIndex(parentIndex);
                } else {
                    statisticsLog.setParentIndex(getParent(messageFlowLogs, parentIndex));
                }
                setElasticMetaData(dataUnit, statisticsLog);
                if (statisticsLog.getHashCode() == null) {
                    statisticsLog.setHashCode(statisticsLog.getComponentId().hashCode());
                }
                statisticsLog.decrementOpenTimes();
                statisticsLog.setEndTime(dataUnit.getTime());
                statisticsLog.setAfterPayload(dataUnit.getPayload());
                updateParents(messageFlowLogs, statisticsLog.getParentIndex(), dataUnit.getTime());
                break;
            case CALLBACK_COMPLETION_EVENT:
                CallbackDataUnit callbackDataUnit = (CallbackDataUnit) event.getDataUnit();
                if (!callbackDataUnit.isOutOnlyFlow()) {
                    updateParents(messageFlowLogs, callbackDataUnit.getCurrentIndex(), callbackDataUnit.getTime());
                }
                break;
            case CALLBACK_RECEIVED_EVENT:
                CallbackDataUnit callbackReceivedDataUnit = (CallbackDataUnit) event.getDataUnit();
                if (!callbackReceivedDataUnit.isOutOnlyFlow()) {
                    updateParents(messageFlowLogs, callbackReceivedDataUnit.getCurrentIndex(),
                                  callbackReceivedDataUnit.getTime());
                }
                break;
            case ENDFLOW_EVENT:
                break;
            case FAULT_EVENT:
                BasicStatisticDataUnit basicDataUnit = event.getDataUnit();
                addFaultsToParents(messageFlowLogs, basicDataUnit, basicDataUnit.getCurrentIndex());
                break;
            case PARENT_REOPEN_EVENT:
                BasicStatisticDataUnit parentReopenDataUnit = event.getDataUnit();
                openFlowContinuableMediators(messageFlowLogs, parentReopenDataUnit.getCurrentIndex());
                break;
            default:
                break;
            }
        }

        //Removing Unnecessary entries for API invocations
        if ((ComponentType.API == messageFlowLogs.get(DEFAULT_HASHCODE).getComponentType())
                && messageFlowLogs.size() > 3) {
            for (int i = 2; i < messageFlowLogs.size(); i++) {
                StatisticsLog statisticsLog = messageFlowLogs.get(i);
                if (ComponentType.API == statisticsLog.getComponentType() && messageFlowLogs.get(DEFAULT_HASHCODE)
                        .getComponentId().equals(statisticsLog.getComponentId())) {
                    messageFlowLogs.get(DEFAULT_HASHCODE).setEndTime(statisticsLog.getEndTime());

                    statisticsLog.setStartTime(DEFAULT_HASHCODE);
                    statisticsLog.setEndTime(DEFAULT_HASHCODE);
                    statisticsLog.setComponentName(IGNORE_ELEMENT);
                    statisticsLog.setComponentId(IGNORE_ELEMENT);
                    statisticsLog.setComponentType(ComponentType.ANY);
                    statisticsLog.setHashCode(DEFAULT_HASHCODE);
                } else if (ComponentType.RESOURCE == statisticsLog.getComponentType() && messageFlowLogs.get(1)
                        .getComponentId().equals(statisticsLog.getComponentId())) {
                    messageFlowLogs.get(1).setEndTime(statisticsLog.getEndTime());

                    statisticsLog.setStartTime(DEFAULT_HASHCODE);
                    statisticsLog.setEndTime(DEFAULT_HASHCODE);
                    statisticsLog.setComponentName(IGNORE_ELEMENT);
                    statisticsLog.setComponentId(IGNORE_ELEMENT);
                    statisticsLog.setComponentType(ComponentType.ANY);
                    statisticsLog.setHashCode(DEFAULT_HASHCODE);
                }
            }
        }

        PublishingFlow publishingFlow = TracingDataCollectionHelper.createPublishingFlow(messageFlowLogs);

        messageFlowObserverStore.notifyObservers(publishingFlow);
    }

    void updateParents(List<StatisticsLog> messageFlowLogs, int index, long endTime) {
        while (index > -1) {
            StatisticsLog dataUnit = messageFlowLogs.get(index);

            if (dataUnit.getEndTime() == 0 || dataUnit.getEndTime() < endTime) {
                dataUnit.setEndTime(endTime);
            }
            index = dataUnit.getParentIndex();
        }
    }

    private int getParent(List<StatisticsLog> messageFlowLogs, int parentIndex) {
        int trueParentIndex = 0;
        while (parentIndex > -1) {
            StatisticsLog updatingLog = messageFlowLogs.get(parentIndex);
            if (updatingLog.isOpenLog()) {
                trueParentIndex = updatingLog.getCurrentIndex();
                break;
            }
            parentIndex = updatingLog.getParentIndex();
        }
        return trueParentIndex;
    }

    /**
     * Set flow continuable mediators in parent path to open state. This is used when there is a continuation call.
     *
     * @param messageFlowLogs raw statistic data unit
     */
    private void openFlowContinuableMediators(List<StatisticsLog> messageFlowLogs, int index) {
        StatisticsLog statisticsLog = messageFlowLogs.get(index);
        while (statisticsLog.getCurrentIndex() > 0) {
            if (statisticsLog.isFlowContinuable()) {
                statisticsLog.incrementOpenTimes();
            }
            statisticsLog = messageFlowLogs.get(statisticsLog.getParentIndex());
        }
    }

    void addFaultsToParents(List<StatisticsLog> messageFlowLogs, BasicStatisticDataUnit dataUnit, int index) {
        while (index > -1) {
            StatisticsLog updatingLog = messageFlowLogs.get(index);
            updatingLog.incrementNoOfFaults();
            index = updatingLog.getParentIndex();
            setElasticMetaData(dataUnit, updatingLog);
        }

    }

    private void setElasticMetaData(BasicStatisticDataUnit dataUnit, StatisticsLog statisticsLog) {
        if (dataUnit.getElasticMetadata() == null) {
            if (dataUnit.getMessageContext() == null) {
                log.warn("Cannot generate elastic metadata. Message context is null.");
                return;
            }
            dataUnit.generateElasticMetadata(dataUnit.getMessageContext());
        }
        ElasticMetadata elasticMetadata = dataUnit.getElasticMetadata();
        AnalyticsCustomDataProvider customDataProvider = AnalyticsDataProviderHolder
                .getInstance()
                .getAnalyticsCustomDataProvider();
        if (customDataProvider != null) {
            if (elasticMetadata.getAnalyticsMetadata() == null) {
                dataUnit.getMessageContext().setProperty(ANALYTICS_METADATA, new HashMap<String, Object>());
                elasticMetadata = new ElasticMetadata(dataUnit.getMessageContext());
            }
            elasticMetadata.getAnalyticsMetadata().putAll(
                    customDataProvider.getCustomProperties(dataUnit.getMessageContext()));
        }
        statisticsLog.setElasticMetadata(elasticMetadata);
    }

    public void shutdown() {
        if (log.isDebugEnabled()) {
            log.debug("Statistics reporter thread is being stopped");
        }
        shutdownRequested = true;
    }

}
