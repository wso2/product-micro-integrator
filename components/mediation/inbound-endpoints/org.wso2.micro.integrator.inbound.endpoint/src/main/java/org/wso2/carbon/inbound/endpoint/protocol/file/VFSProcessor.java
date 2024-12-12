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
package org.wso2.carbon.inbound.endpoint.protocol.file;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundTaskProcessor;
import org.apache.synapse.task.TaskStartupObserver;
import org.wso2.carbon.inbound.endpoint.common.InboundRequestProcessorImpl;
import org.wso2.carbon.inbound.endpoint.common.InboundTask;
import org.wso2.carbon.inbound.endpoint.protocol.PollingConstants;

import java.util.Properties;

public class VFSProcessor extends InboundRequestProcessorImpl implements TaskStartupObserver, InboundTaskProcessor {

    private static final String ENDPOINT_POSTFIX = "FILE" + COMMON_ENDPOINT_POSTFIX;
    private static final Log log = LogFactory.getLog(VFSProcessor.class);

    private FilePollingConsumer fileScanner;
    private Properties vfsProperties;
    private String injectingSeq;
    private String onErrorSeq;
    private boolean sequential;

    public VFSProcessor(InboundProcessorParams params) {
        this.name = params.getName();
        this.vfsProperties = params.getProperties();
        try {
            this.interval = Long.parseLong(vfsProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_INTERVAL));
        } catch (NumberFormatException nfe) {
            throw new SynapseException("Invalid numeric value for interval.", nfe);
        } catch (Exception e) {
            throw new SynapseException("Invalid value for interval.", e);
        }
        this.sequential = true;
        if (vfsProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL) != null) {
            this.sequential = Boolean
                    .parseBoolean(vfsProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL));
        }
        this.coordination = true;
        if (vfsProperties.getProperty(PollingConstants.INBOUND_COORDINATION) != null) {
            this.coordination = Boolean.parseBoolean(vfsProperties.getProperty(PollingConstants.INBOUND_COORDINATION));
        }
        this.injectingSeq = params.getInjectingSeq();
        this.onErrorSeq = params.getOnErrorSeq();
        this.synapseEnvironment = params.getSynapseEnvironment();
        this.startInPausedMode = params.startInPausedMode();
    }

    /**
     * This will be called at the time of synapse artifact deployment.
     */
    public void init() {
        log.info("Inbound file listener [" + name + "] is initializing"
                + (this.startInPausedMode ? " but will remain in suspended mode..." : "..."));
        fileScanner = new FilePollingConsumer(vfsProperties, name, synapseEnvironment, interval);
        fileScanner.registerHandler(
                new FileInjectHandler(injectingSeq, onErrorSeq, sequential, synapseEnvironment, vfsProperties));
        start();
    }

    /**
     * Register/start the schedule service
     */
    public void start() {
        InboundTask task = new FileTask(fileScanner, interval);
        start(task, ENDPOINT_POSTFIX);
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

    @Override
    public boolean deactivate() {
        fileScanner.close();
        return super.deactivate();
    }

    @Override
    public boolean activate() {
        fileScanner.start();
        return super.activate();
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
            fileScanner.destroy();
        }
    }
}
