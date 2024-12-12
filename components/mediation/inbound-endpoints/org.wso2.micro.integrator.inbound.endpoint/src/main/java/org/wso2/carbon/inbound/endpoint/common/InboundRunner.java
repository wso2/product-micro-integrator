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

import java.util.Date;

/**
 * InboundRunner class is used to run the non coordinated processors in
 * background according to the scheduled interval
 */
public class InboundRunner implements Runnable {

    private InboundTask task;
    private long interval;

    private volatile boolean execute = true;
    private volatile boolean isPaused;
    private volatile boolean init = false;
    // Following will be used to calculate the sleeping interval
    private long lastRuntime;
    private long currentRuntime;
    private long cycleInterval;
    private String tenantDomain;
    private boolean runOnManagerOverride = false;

    private static final String CLUSTERING_PATTERN = "clusteringPattern";
    private static final String CLUSTERING_PATTERN_WORKER_MANAGER = "WorkerManager";
    private static final Log log = LogFactory.getLog(InboundRunner.class);
    private final Object lock = new Object();

    public InboundRunner(InboundTask task, long interval, String tenantDomain, boolean mgrOverride, boolean startInPausedMode) {
        this.task = task;
        this.interval = interval;
        this.tenantDomain = tenantDomain;
        this.runOnManagerOverride = mgrOverride;
        this.isPaused = startInPausedMode;
    }

    /**
     * Pauses the execution of the thread.
     * <p>
     * This method sets the {@code isPaused} flag to {@code true}, indicating that
     * the thread should pause its execution. Threads can check this flag and
     * enter a wait state if necessary.
     * </p>
     */
    public void pause() {
        synchronized (lock) {
            isPaused = true;
        }
    }

    /**
     * Resumes the execution of a paused thread.
     * <p>
     * This method sets the {@code isPaused} flag to {@code false} and notifies
     * all threads waiting on the {@code lock} object, allowing the thread to continue execution.
     * </p>
     */
    public void resume() {
        synchronized (lock) {
            isPaused = false;
            lock.notifyAll(); // Wake up the thread
        }
    }

    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Exit the running while loop and terminate the thread
     */
    public void terminate() {
        synchronized (lock) {
            execute = false;
            isPaused = false; // Ensure the thread is not stuck in pause
            lock.notifyAll(); // Wake up the thread to exit
        }
    }

    @Override
    public void run() {
        log.debug("Starting the Inbound Endpoint.");

        log.debug("Configuration context loaded. Running the Inbound Endpoint.");
        // Run the poll cycles
        while (execute) {
            synchronized (lock) {
                while (isPaused && execute) {
                    try {
                        lock.wait(); // Pause the thread
                    } catch (InterruptedException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Inbound thread got interrupted while paused, but continuing...");
                        }
                    }
                }
            }
            if (!execute) break; // Exit right away if the thread is terminated

            if (log.isDebugEnabled()) {
                log.debug("Executing the Inbound Endpoint.");
            }
            lastRuntime = getTime();
            try {
                task.taskExecute();
            } catch (Exception e) {
                log.error("Error executing the inbound endpoint polling cycle.", e);
            }
            currentRuntime = getTime();
            cycleInterval = interval - (currentRuntime - lastRuntime);
            if (cycleInterval > 0) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Unable to sleep the inbound thread for interval of : " + interval + "ms.");
                    }
                }
            }
        }
        log.debug("Exit the Inbound Endpoint running loop.");
    }

    private Long getTime() {
        return new Date().getTime();
    }
}
