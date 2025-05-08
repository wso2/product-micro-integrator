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
package org.wso2.micro.integrator.ntask.core.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.SchedulerConfigException;
import org.quartz.spi.ThreadPool;
import org.wso2.micro.integrator.ntask.core.TaskUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Quartz thread pool implementation which uses a cached thread executor service.
 */
public class QuartzCachedThreadPool implements ThreadPool {

    private ExecutorService executor;
    private static Log log = LogFactory.getLog(QuartzCachedThreadPool.class);

    @Override
    public int blockForAvailableThreads() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getPoolSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void initialize() throws SchedulerConfigException {
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public boolean runInThread(Runnable task) {
        if (executor.isShutdown()) {
            log.warn("Executor is shut down. Cannot accept new tasks.");
            return false;
        }
        this.executor.submit(task);
        return true;
    }

    @Override
    public void setInstanceId(String instanceId) {
    }

    @Override
    public void setInstanceName(String instanceName) {
    }

    @Override
    public void shutdown(boolean waitForJobsToComplete) {
        if (waitForJobsToComplete) {
            log.info("Graceful scheduler shutdown initiated. Waiting for tasks to finish...");
            this.executor.shutdown();
            try {
                if (!executor.awaitTermination(TaskUtils.getMaxWaitTimeInSeconds(), TimeUnit.SECONDS)) {
                    log.warn("Scheduler did not terminate in time. Forcing shutdown.");
                    executor.shutdownNow();
                } else {
                    log.info("Scheduler terminated cleanly.");
                }
            } catch (InterruptedException e) {
                log.warn("Scheduler shutdown interrupted. Forcing shutdown.");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } else {
            this.executor.shutdownNow();
        }
    }

}
