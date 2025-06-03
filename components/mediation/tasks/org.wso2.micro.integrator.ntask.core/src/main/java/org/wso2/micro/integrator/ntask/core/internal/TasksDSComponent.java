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
package org.wso2.micro.integrator.ntask.core.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.core.ServerStartupObserver;
import org.wso2.micro.integrator.coordination.ClusterCoordinator;
import org.wso2.micro.integrator.coordination.ClusterEventListener;
import org.wso2.micro.integrator.coordination.exception.ClusterCoordinationException;
import org.wso2.micro.integrator.coordination.util.RDBMSConstantUtils;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;
import org.wso2.micro.integrator.ndatasource.common.DataSourceException;
import org.wso2.micro.integrator.ndatasource.core.CarbonDataSource;
import org.wso2.micro.integrator.ndatasource.core.DataSourceService;
import org.wso2.micro.integrator.ntask.common.TaskException;
import org.wso2.micro.integrator.ntask.coordination.TaskCoordinationException;
import org.wso2.micro.integrator.ntask.coordination.task.TaskEventListener;
import org.wso2.micro.integrator.ntask.coordination.task.resolver.ActivePassiveResolver;
import org.wso2.micro.integrator.ntask.coordination.task.resolver.TaskLocationResolver;
import org.wso2.micro.integrator.ntask.coordination.task.store.TaskStore;
import org.wso2.micro.integrator.ntask.core.TaskStartupHandler;
import org.wso2.micro.integrator.ntask.core.TaskUtils;
import org.wso2.micro.integrator.ntask.core.impl.QuartzCachedThreadPool;
import org.wso2.micro.integrator.ntask.core.impl.standalone.ScheduledTaskManager;
import org.wso2.micro.integrator.ntask.core.service.TaskService;
import org.wso2.micro.integrator.ntask.core.service.impl.TaskServiceImpl;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

/**
 * This class represents the Tasks declarative service component.
 */
@Component(name = "org.wso2.micro.integrator.ntask.core.internal.TasksDSComponent",
        immediate = true)
public class TasksDSComponent {

    private static final String QUARTZ_PROPERTIES_FILE_NAME = "quartz.properties";
    private static final String TASK_CONFIG = "task_handling";
    private static final String RESOLVER_CLASS = "resolver_class";
    private static final String RESOLVING_PERIOD = "resolving_period";
    private static final String RESOLVING_FREQUENCY = "resolving_frequency";
    private static final String TASK_RESOLVER = "task_resolver";

    private final Log log = LogFactory.getLog(TasksDSComponent.class);
    private static Scheduler scheduler;

    private static SecretCallbackHandlerService secretCallbackHandlerService;

    private static TaskService taskService;

    private static ExecutorService executor = Executors.newCachedThreadPool();
    private static DataSourceService dataSourceService;
    private Object coordinationDatasourceObject;
    private DataHolder dataHolder = DataHolder.getInstance();
    private TaskStore taskStore;
    private TaskLocationResolver resolver;
    private ClusterCoordinator clusterCoordinator;

    @Activate
    protected void activate(ComponentContext ctx) {

        try {
            if (executor.isShutdown()) {
                executor = Executors.newCachedThreadPool();
            }
            String quartzConfigFilePath =
                    MicroIntegratorBaseUtils.getCarbonConfigDirPath() + File.separator + "etc" + File.separator
                            + QUARTZ_PROPERTIES_FILE_NAME;
            StdSchedulerFactory fac;
            if (new File(quartzConfigFilePath).exists()) {
                fac = new StdSchedulerFactory(quartzConfigFilePath);
            } else {
                fac = new StdSchedulerFactory(this.getStandardQuartzProps());
            }
            TasksDSComponent.scheduler = fac.getScheduler();
            TasksDSComponent.getScheduler().start();

            boolean isCoordinationEnabled = isCoordinationDataSourceAvailable();
            if (isCoordinationEnabled) {
                log.info("Initializing task coordination.");
                DataSource coordinationDataSource = (DataSource) coordinationDatasourceObject;
                clusterCoordinator = new ClusterCoordinator(coordinationDataSource);
                if (clusterCoordinator.checkDuplicateNodeExistence()) {
                    throw new ClusterCoordinationException(
                            "Node with id " + clusterCoordinator.getThisNodeId() + " already "
                                    + "exists in cluster or the previous shutdown of this node "
                                    + "hasn't elapsed the heart beat expiry time of " + clusterCoordinator
                                    .getHeartbeatMaxRetryInterval() + " milli seconds.");
                }
                dataHolder.setClusterCoordinator(clusterCoordinator);
                // initialize task data base.
                taskStore = new TaskStore(coordinationDataSource);
                // removing all tasks assigned to this node since this node hasn't even  joined the cluster yet and
                // cannot have tasks assigned to it already. Will be useful in case of static node ids
                try {
                    taskStore.deleteTasks(clusterCoordinator.getThisNodeId());
                } catch (TaskCoordinationException e) {
                    log.error("Error while removing the tasks of this node.", e);
                }
                // initialize task location resolver
                resolver = getResolver();
            }

            if (getTaskService() == null) {
                taskService = new TaskServiceImpl(taskStore);
            }

            BundleContext bundleContext = ctx.getBundleContext();
            bundleContext.registerService(ServerStartupObserver.class.getName(), new TaskStartupHandler(taskService),
                                          null);
            bundleContext.registerService(TaskService.class.getName(), getTaskService(), null);

            if (isCoordinationEnabled) {
                clusterCoordinator.registerListener(new ClusterEventListener(clusterCoordinator.getThisNodeId()));
                ScheduledTaskManager scheduledTaskManager = dataHolder.getTaskManager();
                if (scheduledTaskManager == null) {
                    throw new AssertionError("Task Manager is not initialized properly.");
                }
                clusterCoordinator.registerListener(new TaskEventListener(scheduledTaskManager, taskStore, resolver));
                // the task scheduler should be started after registering task service.
                CoordinatedTaskScheduleManager coordinatedTaskScheduleManager = new CoordinatedTaskScheduleManager(
                        scheduledTaskManager, taskStore, clusterCoordinator, resolver);
                // join cluster
                clusterCoordinator.startCoordinator();
                setSchedulerProperties();
                coordinatedTaskScheduleManager.startTaskScheduler("");
            }
        } catch (Throwable e) {
            log.error("Error in initializing Tasks component: " + e.getMessage(), e);
        }
    }

    private void setSchedulerProperties() {

        Map<String, Object> configs = ConfigParser.getParsedConfigs();
        Object exePeriod = configs.get(TASK_CONFIG + "." + RESOLVING_PERIOD);
        if (exePeriod != null) {
            int executionPeriod = Integer.parseInt(exePeriod.toString());
            if (executionPeriod < 1) {
                log.warn(
                        RESOLVING_PERIOD + " should be greater than or equal to 1 seconds. The value " + executionPeriod
                                + " will be ignored and default 2 will be used.");
            } else {
                CoordinatedTaskScheduleManager.setExecutionPeriod(executionPeriod);
            }
        }
        Object resolvingFreq = configs.get(TASK_CONFIG + "." + RESOLVING_FREQUENCY);
        if (resolvingFreq != null) {
            int resolvingFrequency = Integer.parseInt(resolvingFreq.toString());
            if (resolvingFrequency < 1) {
                log.warn(RESOLVING_FREQUENCY + " should be greater than or equal to 1. The value " + resolvingFrequency
                                 + " will be" + " ignored and default 5 will be used.");
            } else {
                CoordinatedTaskScheduleManager.setResolveFrequency(resolvingFrequency);
            }
        }
    }

    /**
     * Initializes the task resolver.
     *
     * @return - TaskLocationResolver
     */
    @SuppressWarnings(value = "unchecked")
    private TaskLocationResolver getResolver()
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException,
                   InvocationTargetException, TaskException {

        Map<String, Object> configs = ConfigParser.getParsedConfigs();
        Object resolverClass = configs.get(TASK_CONFIG + "." + RESOLVER_CLASS);
        if (resolverClass == null) {
            return getDefaultResolver();
        }
        String resolverClassName = resolverClass.toString();
        if (log.isDebugEnabled()) {
            log.debug("Task resolver class : " + resolverClassName);
        }
        TaskLocationResolver taskLocationResolver = (TaskLocationResolver) Class.forName(resolverClassName)
                .getDeclaredConstructor().newInstance();
        List<Object> resolverProperties = (List<Object>) configs.get(TASK_RESOLVER);
        if (resolverProperties != null && !resolverProperties.isEmpty()) {
            Map<String, String> properties = (Map<String, String>) resolverProperties.get(0);
            if (log.isDebugEnabled()) {
                properties.forEach((key, value) -> log.debug("Task resolver property :: " + key + ":" + value));
            }
            taskLocationResolver.init(properties);
        }
        return taskLocationResolver;
    }

    /**
     * Get the default resolver.
     *
     * @return - Default Resolver.
     */
    private TaskLocationResolver getDefaultResolver() {
        if (log.isDebugEnabled()) {
            log.debug("Task resolver class : " + ActivePassiveResolver.class.getName());
        }
        return new ActivePassiveResolver();
    }

    /**
     * Check whether the WSO2_COORDINATION_DB is defined.
     *
     * @return - true if datasource defined.
     * @throws DataSourceException
     */
    private boolean isCoordinationDataSourceAvailable() throws DataSourceException {

        CarbonDataSource dataSource = dataSourceService.getDataSource(RDBMSConstantUtils.COORDINATION_DB_NAME);
        if (dataSource == null) {
            return false;
        }
        coordinationDatasourceObject = dataSource.getDSObject();
        if (!(coordinationDatasourceObject instanceof DataSource)) {
            throw new DataSourceException("DataSource is not an RDBMS data source.");
        }
        return true;
    }

    private Properties getStandardQuartzProps() {

        Properties result = new Properties();
        result.put("org.quartz.scheduler.skipUpdateCheck", "true");
        result.put("org.quartz.threadPool.class", QuartzCachedThreadPool.class.getName());
        return result;
    }

    @Deactivate
    protected void deactivate(ComponentContext ctx) {

        ScheduledExecutorService executorService = dataHolder.getTaskScheduler();
        if (executorService != null) {
            log.info("Shutting down coordinated task scheduler.");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(TaskUtils.getMaxWaitTimeInSeconds()
                        , TimeUnit.SECONDS)) {
                    log.warn("Timeout while waiting for coordinated task scheduler. Forcing shutdown.");
                    executorService.shutdownNow();
                } else {
                    log.info("Coordinated task scheduler shut down cleanly.");
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted during coordinated task scheduler shutdown. Forcing shutdown.");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (TasksDSComponent.getScheduler() != null) {
            try {
                TasksDSComponent.getScheduler().shutdown(true);
            } catch (Exception e) {
                log.error(e);
            }
        }
        if (executor != null) {
            log.info("Shutting down global executor.");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(TaskUtils.getMaxWaitTimeInSeconds(), TimeUnit.SECONDS)) {
                    log.warn("Global executor shutdown timed out. Forcing shutdown.");
                    executor.shutdownNow();
                } else {
                    log.info("Global executor shut down cleanly.");
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted during global executor shutdown. Forcing shutdown.");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        taskService = null;
    }

    public static TaskService getTaskService() {

        return taskService;
    }

    public static Scheduler getScheduler() {

        return scheduler;
    }

    public static SecretCallbackHandlerService getSecretCallbackHandlerService() {

        return TasksDSComponent.secretCallbackHandlerService;
    }

    @Reference(name = "secret.callback.handler.service",
            service = org.wso2.carbon.securevault.SecretCallbackHandlerService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetSecretCallbackHandlerService")
    protected void setSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {

        TasksDSComponent.secretCallbackHandlerService = secretCallbackHandlerService;
    }

    protected void unsetSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {

        TasksDSComponent.secretCallbackHandlerService = null;
    }

    @Reference(name = "org.wso2.carbon.ndatasource",
               service = DataSourceService.class,
               cardinality = ReferenceCardinality.MANDATORY,
               policy = ReferencePolicy.DYNAMIC,
               unbind = "unsetDatasourceHandlerService")
    protected void setDatasourceHandlerService(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    protected void unsetDatasourceHandlerService(DataSourceService dataSourceService) {
        this.dataSourceService = null;
    }

}
