/*                                                                             
 * Copyright 2004,2005 The Apache Software Foundation.                         
 *                                                                             
 * Licensed under the Apache License, Version 2.0 (the "License");             
 * you may not use this file except in compliance with the License.            
 * You may obtain a copy of the License at                                     
 *                                                                             
 *      http://www.apache.org/licenses/LICENSE-2.0                             
 *                                                                             
 * Unless required by applicable law or agreed to in writing, software         
 * distributed under the License is distributed on an "AS IS" BASIS,           
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    
 * See the License for the specific language governing permissions and         
 * limitations under the License.                                              
 */
package org.wso2.micro.core;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.deployment.DeploymentEngine;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.transport.TransportListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.inbound.InboundEndpoint;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.wso2.micro.integrator.core.internal.CarbonCoreDataHolder;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;

import java.lang.management.ManagementPermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;

/**
 * Class for handling Server management functionalilty.
 * <p/>
 * At the moment, handles only maintenance mode operations
 */
public class ServerManagement {

    private static final Log log = LogFactory.getLog(ServerManagement.class);
    private static final long TIMEOUT = 60 * 1000;
    private Map<String, TransportInDescription> inTransports;
    private ConfigurationContext serverConfigContext;
    private static CarbonCoreDataHolder dataHolder = CarbonCoreDataHolder.getInstance();

    public ServerManagement(Map<String, TransportInDescription> inTransports,
                            ConfigurationContext serverConfigContext) {
        this.inTransports = inTransports;
        this.serverConfigContext = serverConfigContext;
    }

    /**
     * Method to switch a node to maintenance mode.
     * <p/>
     * Here is the sequence of events:
     * <p/>
     * <ol>
     * <li>Client calls this method</li>
     * <li>The server stops accepting new requests/connections, but continues to stay alive so
     * that old requests & connections can be served</li>
     * <li>Once all requests have been processed, the method returns</li
     * </ol>
     * @throws Exception - on errors while starting maintenance
     */
    public void startMaintenance() throws Exception {
        SecurityManager secMan = System.getSecurityManager();
        if (secMan != null) {
            secMan.checkPermission(new ManagementPermission("control"));
        }
        log.info("Starting to switch to maintenance mode...");
        stopTransportListeners();
        pauseInboundEndpoints();
        destroyTransportListeners();
        waitForRequestCompletion();
    }

    /**
     * Stop Transport Listeners asynchronously and wait for the completion of the tasks
     */
    private void stopTransportListeners() {
        ExecutorService transportListenerShutdownPool = Executors.newFixedThreadPool(inTransports.size());
        List<Future<Void>> listenerShutdownFutures = new ArrayList<>();
        for (TransportInDescription tinDesc : inTransports.values()) {
            TransportListener transport = tinDesc.getReceiver();
            Future<Void> future = transportListenerShutdownPool.submit(new TransportListenerShutdownTask(transport));
            listenerShutdownFutures.add(future);
        }

        // Wait until shutting down the transport listeners before proceeding
        for (Future<Void> future : listenerShutdownFutures) {
            try {
                future.get();
            } catch (Exception e) {
                log.error("Error while completing transport listener shutdown", e);
            }
        }
        transportListenerShutdownPool.shutdown();
        log.info("Stopped all transport listeners");
    }

    /**
     * Pauses all inbound endpoints in the server to stop accepting new requests.
     * <p>
     * This method pauses all the inbound endpoints defined in the Synapse configuration.
     * Each endpoint is paused in a separate task using a thread pool,
     * and this method waits until all endpoints are fully paused before returning.
     * <p>
     * This ensures that no new requests are accepted in inbound endpoints during server shutdown
     * or maintenance activities.
     * <p>
     * Any errors encountered while pausing individual endpoints are logged but do not halt the suspension
     * process for other endpoints.
     */
    private void pauseInboundEndpoints() {
        MicroIntegratorBaseUtils.checkSecurity();
        log.info("Pausing all Inbound Endpoints...");

        Parameter synCfgParam =
                serverConfigContext.getAxisConfiguration().getParameter(SynapseConstants.SYNAPSE_CONFIG);
        if (synCfgParam == null) {
            log.error("Unable to pause inbound endpoints. Synapse configuration not found!");
        } else {
            SynapseConfiguration synapseConfiguration = (SynapseConfiguration) synCfgParam.getValue();
            Collection<InboundEndpoint> inboundEndpoints =  synapseConfiguration.getInboundEndpoints();
            if (log.isDebugEnabled()) {
                log.debug("Found " + inboundEndpoints.size() + " inbound endpoints to pause");
            }

            ExecutorService inboundEndpointShutdownPool = Executors.newFixedThreadPool(inboundEndpoints.size());
            List<Future<Void>> listenerShutdownFutures = new ArrayList<>();

            for (InboundEndpoint inboundEndpoint : inboundEndpoints) {
                Future<Void> future = inboundEndpointShutdownPool.submit(new InboundEndpointShutdownTask(inboundEndpoint));
                listenerShutdownFutures.add(future);
                if (log.isDebugEnabled()) {
                    log.debug("Submitted pause task for inbound endpoint: " + inboundEndpoint.getName());
                }
            }

            // Wait until suspending the inbound endpoints before proceeding
            for (Future<Void> future : listenerShutdownFutures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.error("An error occurred while pausing an inbound endpoint during graceful shutdown.", e);
                }
            }
            inboundEndpointShutdownPool.shutdown();
        }

        log.info("Completed pausing of all inbound endpoints during graceful shutdown.");
    }

    /**
     * Destroy Transport Listeners
     */
    private void destroyTransportListeners() {
        // Destroy the TransportListener at the end to clear up resources
        for (TransportInDescription tinDesc : inTransports.values()) {
            TransportListener transport = tinDesc.getReceiver();
            transport.destroy();
        }
    }

    /**
     * Method the switch to Maintenance mode when the server is shutting down. In addition to the
     * normal maintenance mode, here we have to wait until all deployment threads are executed.
     *
     * @throws Exception - on errors while starting maintenance
     */
    public void startMaintenanceForShutDown() throws Exception {
        startMaintenance();
        waitForDeploymentThreadCompletion();
        //cleanCAppWorkDir();
        waitForServerTaskCompletion();
    }

    private void waitForServerTaskCompletion() {
        MicroIntegratorBaseUtils.checkSecurity();
        log.info("Waiting for server task completion...");
        BundleContext bundleContext = dataHolder.getBundleContext();
        if (bundleContext != null) {
            @SuppressWarnings("unchecked")
            ServiceTracker tracker = new ServiceTracker(bundleContext,
                                                        WaitBeforeShutdownObserver.class.getName(),
                                                        null);
            tracker.open();
            Object[] services = tracker.getServices();
            if (services != null) {
                for (Object service : services) {
                    ((WaitBeforeShutdownObserver) service).startingShutdown();

                }
                boolean allTasksComplete;
                do {
                    // Assume all tasks are completed by now.
                    allTasksComplete = true;
                    for (Object service : services) {
                        allTasksComplete &= ((WaitBeforeShutdownObserver) service).isTaskComplete();
                    }
                    // check again if at least one task is not complete.
                } while (!allTasksComplete);
            }
            tracker.close();
        }
        log.info("All server tasks have been completed.");
    }

    /**
     * Wait until all deployment tasks have completed
     */
    private void waitForDeploymentThreadCompletion() {
        MicroIntegratorBaseUtils.checkSecurity();
        log.info("Waiting for deployment completion...");

        // Stop all deployment tasks by calling cleanup on the super-tenant & tenant configurators
        //invoking  CarbonDeploymentSchedulerExtenders for all tenants before shutdown.
        //(this is done for the purpose of persisting all remaining stat data.)


        serverConfigContext.getAxisConfiguration().getConfigurator().cleanup();
        // uncomment following to execute registered deployment  scheduler extenders for super
        // tenant in maintenance mode.
        /*DeploymentUtils.invokeCarbonDeploymentSchedulerExtenders(
                serverConfigContext.getAxisConfiguration());*/

        boolean isDeploymentSchedulerRunning;
        log.info("All deployment tasks have been completed.");
    }

    private boolean isDeploymentSchedulerRunning(ConfigurationContext configurationContext) {
        DeploymentEngine deploymentEngine =
                (DeploymentEngine) configurationContext.getAxisConfiguration().getConfigurator();
        return deploymentEngine.isDeploymentTaskRunning();
    }

    /**
     * Wait till all service requests have been serviced. This method will only wait for a maximum
     * of {@link ServerManagement#TIMEOUT}
     *
     * @throws Exception If an error occurs while trying to connect to the Tomcat MBean
     */
    public void waitForRequestCompletion() throws Exception {
        SecurityManager secMan = System.getSecurityManager();
        if (secMan != null) {
            secMan.checkPermission(new ManagementPermission("control"));
        }
        log.info("Waiting for request service completion...");
        /**
         * Get all MBeans with names such as Catalina:type=RequestProcessor,worker=http-9762,name=HttpRequest<n>
         * & Catalina:type=RequestProcessor,worker=http-9762,name=HttpsRequest<n>
         */
        MBeanServer mbs = MbeanManagementFactory.getMBeanServer();
        boolean areRequestsInService;
        long start = System.currentTimeMillis();
        do {
            // Check whether there are any processors which are currently in the SERVICE stage (3)
            QueryExp query = Query.eq(Query.attr("stage"), Query.value(3));  // 3 = org.apache.coyote.Constants.STAGE_SERVICE
            Set set = mbs.queryNames(new ObjectName("Catalina:type=RequestProcessor,*"), query);
            if (set.size() > 0) {
                areRequestsInService = true;
                if (System.currentTimeMillis() - start > TIMEOUT) {
                    log.warn("Timeout occurred even though there are active connections.");
                    break;
                }
                Thread.sleep(2000);
            } else {
                areRequestsInService = false;
            }
        } while (areRequestsInService);
        log.info("All requests have been served.");
    }

    /**
     * Method to change the state of a node from "maintenance" to "normal"
     *
     * @throws Exception If an error occurs while trying to connect to the Tomcat MBean
     */
    public void endMaintenance() throws Exception {
        SecurityManager secMan = System.getSecurityManager();
        if (secMan != null) {
            secMan.checkPermission(new ManagementPermission("control"));
        }
        log.info("Switching to normal mode...");
        for (Iterator iter = inTransports.values().iterator(); iter.hasNext();) {
            TransportInDescription tinDesc = (TransportInDescription) iter.next();
            TransportListener transport = tinDesc.getReceiver();
            transport.start();
        }
        log.info("Switched to normal mode");
    }

    /**
     * Callable task to pause and shutdown a transport listener
     */
    private class TransportListenerShutdownTask implements Callable<Void> {
        private TransportListener transport;

        public TransportListenerShutdownTask(TransportListener transport) {
            this.transport = transport;
        }

        public Void call() throws Exception {
            try {
                transport.stop();
            } catch (Exception e) {
                log.error("Error while stopping Transport Listener", e);
            }
            return null;
        }
    }

    private class InboundEndpointShutdownTask implements Callable<Void> {
        private InboundEndpoint inboundEndpoint;

        public InboundEndpointShutdownTask(InboundEndpoint inboundEndpoint) {
            this.inboundEndpoint = inboundEndpoint;
        }

        public Void call() throws Exception {
            try {
                inboundEndpoint.pause();
            } catch (Exception e) {
                log.error("Error while stopping Inbound Endpoint: " + inboundEndpoint.getName(), e);
            }
            return null;
        }
    }
}
