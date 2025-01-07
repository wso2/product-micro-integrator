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
package org.wso2.carbon.inbound.endpoint.protocol.jms;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundTaskProcessor;
import org.apache.synapse.task.TaskStartupObserver;
import org.wso2.carbon.inbound.endpoint.common.InboundRequestProcessorImpl;
import org.wso2.carbon.inbound.endpoint.common.InboundTask;
import org.wso2.carbon.inbound.endpoint.protocol.PollingConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class JMSProcessor extends InboundRequestProcessorImpl implements TaskStartupObserver, InboundTaskProcessor {

    private static final Log log = LogFactory.getLog(JMSProcessor.class.getName());

    private static final String ENDPOINT_POSTFIX = "JMS" + COMMON_ENDPOINT_POSTFIX;

    private List<JMSPollingConsumer> pollingConsumers = new ArrayList<>();
    private Properties jmsProperties;
    private boolean sequential;
    private String injectingSeq;
    private String onErrorSeq;
    private int concurrentConsumers;

    public JMSProcessor(InboundProcessorParams params) {
        this.name = params.getName();
        this.startInPausedMode = params.startInPausedMode();
        this.jmsProperties = params.getProperties();

        String inboundEndpointInterval = jmsProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_INTERVAL);
        if (inboundEndpointInterval != null) {
            try {
                this.interval = Long.parseLong(inboundEndpointInterval);
            } catch (NumberFormatException nfe) {
                throw new SynapseException("Invalid numeric value for interval.", nfe);
            }
        }
        this.sequential = true;
        String inboundEndpointSequential = jmsProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL);
        if (inboundEndpointSequential != null) {
            this.sequential = Boolean.parseBoolean(inboundEndpointSequential);
        }
        this.coordination = true;
        String inboundCoordination = jmsProperties.getProperty(PollingConstants.INBOUND_COORDINATION);
        if (inboundCoordination != null) {
            this.coordination = Boolean.parseBoolean(inboundCoordination);
        }
        this.concurrentConsumers = 1;
        String concurrentConsumers = jmsProperties.getProperty(PollingConstants.INBOUND_CONCURRENT_CONSUMERS);
        if (concurrentConsumers != null) {
            if (Integer.parseInt(concurrentConsumers) == 0) {
                throw new SynapseException("Number of Concurrent Consumers should be Greater than 0");
            }
            this.concurrentConsumers = Integer.parseInt(concurrentConsumers);
        }
        this.injectingSeq = params.getInjectingSeq();
        this.onErrorSeq = params.getOnErrorSeq();
        this.synapseEnvironment = params.getSynapseEnvironment();

    }

    /**
     * This will be called at the time of synapse artifact deployment.
     */
    public void init() {
        /*
         * The activate/deactivate functionality for the JMS protocol is not currently implemented
         * for Inbound Endpoints.
         *
         * Therefore, the following check has been added to immediately return if the "suspend"
         * attribute is set to true in the inbound endpoint configuration.
         *
         * Note: This implementation is temporary and should be revisited and improved once
         * the activate/deactivate capability for JMS listener is implemented.
         */
        if (startInPausedMode) {
            return;
        }
        log.info("Initializing inbound JMS listener for inbound endpoint " + name);
        for (int consumers = 0; consumers < concurrentConsumers; consumers++) {
            JMSPollingConsumer jmsPollingConsumer = new JMSPollingConsumer(jmsProperties, interval, name);
            jmsPollingConsumer.registerHandler(
                    new JMSInjectHandler(injectingSeq, onErrorSeq, sequential, synapseEnvironment, jmsProperties));
            pollingConsumers.add(jmsPollingConsumer);
            start(jmsPollingConsumer, consumers);
        }
    }

    /**
     * Stop the inbound polling processor This will be called when inbound is
     * undeployed/redeployed or when server stop
     */
    public void destroy() {
        for (JMSPollingConsumer pollingConsumer : pollingConsumers) {
            pollingConsumer.destroy();
        }
        super.destroy();
    }

    /**
     * Register/start the schedule service
     */
    public void start(JMSPollingConsumer pollingConsumer, int consumer) {
        InboundTask task = new JMSTask(pollingConsumer, interval);
        start(task, (ENDPOINT_POSTFIX + consumer));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void update() {
        // This will not be called for inbound endpoints
    }

    /**
     * Remove inbound endpoints.
     *
     * @param removeTask Whether to remove scheduled task from the registry or not.
     */
    @Override
    public void destroy(boolean removeTask) {
        if (removeTask) {
            destroy();
        }
    }

    @Override
    public boolean activate() {

        return false;
    }

    @Override
    public boolean deactivate() {

        return false;
    }
}
