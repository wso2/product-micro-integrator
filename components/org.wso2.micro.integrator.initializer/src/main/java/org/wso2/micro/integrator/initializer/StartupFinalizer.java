/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.ListenerManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.wso2.carbon.inbound.endpoint.EndpointListenerLoader;
import org.wso2.micro.core.ServerStatus;
import org.wso2.micro.integrator.initializer.utils.ConfigurationHolder;

/**
 * Responsible for finalizing startup of the MI server. This should run finalizeStartup() after all other
 * components & service required for the Carbon server to reach a stable state become available.
 *
 * This is mainly responsible for starting the Axis2 Transport ListenerManager
 * once all the required OSGi services in the system become available. This is because of the
 * fact  that requests from external parties should only be serviced after the Axis2 engine
 * has reached a stable and consistent state.
 */
public class StartupFinalizer {
    private static final Log log = LogFactory.getLog(StartupFinalizer.class);
    public static final String START_TIME = "wso2carbon.start.time";
    public static final String SERVER_START_TIME = "wso2carbon.server.start.time";
    public static final String START_UP_DURATION = "wso2carbon.startup.duration";

    private ConfigurationContext configCtx;
    private BundleContext bundleContext;
    private ConfigurationHolder dataHolder = ConfigurationHolder.getInstance();
    private ServiceRegistration listerManagerServiceRegistration;

    public StartupFinalizer(ConfigurationContext configCtx, BundleContext bundleContext) {
        this.configCtx = configCtx;
        this.bundleContext = bundleContext;
    }

    /**
     * Finalizes the server startup.
     */
    public void finalizeStartup() {
        completeInitialization();
    }

    /**
     * Cleanup registered services
     */
    public void cleanup() {
        /*listerManagerServiceRegistration.unregister();*/
    }

    private void completeInitialization() {
        ListenerManager listenerManager = configCtx.getListenerManager();
        if (listenerManager == null) {
            listenerManager = new ListenerManager();
        }
        listenerManager.setShutdownHookRequired(false);

        // Init and start axis2 transports
        listenerManager.startSystem(configCtx);

        // Start Inbound Endpoint Listeners
        EndpointListenerLoader.loadListeners();

        /*listerManagerServiceRegistration =
                bundleContext.registerService(ListenerManager.class.getName(), listenerManager, null);*/

        setServerStartTimeParam();
        printInfo();
    }
    
    private void setServerStartTimeParam() {
        Parameter startTimeParam = new Parameter();
        startTimeParam.setName(SERVER_START_TIME);
        startTimeParam.setValue(System.getProperty(START_TIME));
        try {
            configCtx.getAxisConfiguration().addParameter(startTimeParam);
        } catch (AxisFault e) {
            log.error("Could not set the  server start time parameter", e);
        }
    }
    
    private void setServerStartUpDurationParam(String startupTime) {
        Parameter startupDurationParam = new Parameter();
        startupDurationParam.setName(START_UP_DURATION);
        startupDurationParam.setValue(startupTime);
        try {
            configCtx.getAxisConfiguration().addParameter(startupDurationParam);
        } catch (AxisFault e) {
            log.error("Could not set the  server start up duration parameter", e);
        }
    }

    private void printInfo() {
        long startTime = Long.parseLong(System.getProperty(START_TIME));
        double startupTime = (System.currentTimeMillis() - startTime) / 1000.0;
        try {
            if (log.isDebugEnabled()) {
                log.debug("Server           :  " +
                        dataHolder.getCarbonServerConfigurationService().getFirstProperty("Name") + "-" +
                        dataHolder.getCarbonServerConfigurationService().getFirstProperty("Version"));
            }
        } catch (Exception e) {
            log.debug("Error while retrieving server configuration",e);
        }
        try {
            ServerStatus.setServerRunning();
        } catch (AxisFault e) {
            String msg = "Cannot set server to running mode";
            log.error(msg, e);
        }
        log.info(String.format("WSO2 Micro Integrator started in %.2f seconds", startupTime));
        setServerStartUpDurationParam(String.valueOf(startupTime));
        System.getProperties().remove("setup"); // Clear the setup System property
        System.setProperty("org.wso2.mi.server.startup.mode", "false");
    }
}
