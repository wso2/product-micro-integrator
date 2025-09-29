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

package org.wso2.carbon.inbound.endpoint.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.inbound.endpoint.persistence.service.InboundEndpointPersistenceServiceDSComponent;
import org.wso2.micro.integrator.core.services.Axis2ConfigurationContextService;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic callback implementation for one time trigger inbound endpoints. In this case
 * Message injection is happens in a separate thread ( Callback ) per message.
 */
public abstract class OneTimeTriggerAbstractCallback {

    private volatile Semaphore callbackSuspensionSemaphore = new Semaphore(0);
    private AtomicBoolean isCallbackSuspended = new AtomicBoolean(false);
    private AtomicBoolean isShutdownFlagSet = new AtomicBoolean(false);
    protected String tenantDomain;
    private boolean isInboundRunnerMode = false;
    private static final Log log = LogFactory.getLog(OneTimeTriggerAbstractCallback.class);

    protected void handleReconnection() throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Started handling reconnection due to connection lost callback");
        }
        if (!isInboundRunnerMode) {
            isCallbackSuspended.set(true);
            callbackSuspensionSemaphore.acquire();
            if (!isShutdownFlagSet.get()) {
                reConnect();
            }
            isCallbackSuspended.set(false);
        } else {
            reConnect();
        }
    }

    protected void shutdown() {
        isShutdownFlagSet.set(true);
        if (isCallbackSuspended.get()) {
            callbackSuspensionSemaphore.release();
        }
    }

    protected abstract void reConnect();

    public void releaseCallbackSuspension() {
        if (callbackSuspensionSemaphore.availablePermits() < 1) {
            callbackSuspensionSemaphore.release();
        }
    }

    public boolean isCallbackSuspended() {
        return isCallbackSuspended.get();
    }

    public void setTenantDomain(String tenantDomain) {
        this.tenantDomain = tenantDomain;
    }

    public void startInboundTenantLoading(String inboundIdentifier) {
        //make sure tenant is loaded before the message flow is started
        //this case is only considered for the inbound runner mode
        if (this.isInboundRunnerMode && tenantDomain != null) {
            Axis2ConfigurationContextService configurationContext = InboundEndpointPersistenceServiceDSComponent
                    .getConfigContextService();

        }
    }

    public void setInboundRunnerMode(boolean isInboundRunnerMode) {
        this.isInboundRunnerMode = isInboundRunnerMode;
    }

    public boolean isInboundRunnerMode() {
        return this.isInboundRunnerMode;
    }

}
