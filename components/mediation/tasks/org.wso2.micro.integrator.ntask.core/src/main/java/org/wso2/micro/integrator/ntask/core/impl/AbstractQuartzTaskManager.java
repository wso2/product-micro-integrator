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
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Matcher;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.Trigger.TriggerState;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.TriggerListener;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.OperableTrigger;
import org.wso2.micro.integrator.ntask.common.TaskConstants;
import org.wso2.micro.integrator.ntask.common.TaskException;
import org.wso2.micro.integrator.ntask.coordination.TaskCoordinationException;
import org.wso2.micro.integrator.ntask.coordination.task.CoordinatedTask;
import org.wso2.micro.integrator.ntask.coordination.task.store.TaskStore;
import org.wso2.micro.integrator.ntask.core.TaskInfo;
import org.wso2.micro.integrator.ntask.core.TaskManager;
import org.wso2.micro.integrator.ntask.core.TaskRepository;
import org.wso2.micro.integrator.ntask.core.TaskUtils;
import org.wso2.micro.integrator.ntask.core.internal.TasksDSComponent;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class represents an abstract class implementation of TaskManager based on Quartz Scheduler.
 *
 * @see TaskManager
 */
public abstract class AbstractQuartzTaskManager implements TaskManager {

    private static final Log log = LogFactory.getLog(AbstractQuartzTaskManager.class);

    /**
     * The set of listeners to be notified when a local task is deleted where each listener is mapped to the job that
     * it should be notified of the deletion.
     */
    private static Map<String, LocalTaskActionListener> localTaskActionListeners = new HashMap<>();

    private TaskRepository taskRepository;
    private Scheduler scheduler;
    private TaskStore taskStore;

    public AbstractQuartzTaskManager(TaskRepository taskRepository, TaskStore taskStore) throws TaskException {

        this.taskRepository = taskRepository;
        this.scheduler = TasksDSComponent.getScheduler();
        this.taskStore = taskStore;
        try {
            Matcher<TriggerKey> tenantTaskTypeGroupMatcher = GroupMatcher.groupEquals(this.getTenantTaskGroup());
            this.getScheduler().getListenerManager().addTriggerListener(
                    new TaskTriggerListener(this.getTenantTaskGroup()), tenantTaskTypeGroupMatcher);
        } catch (SchedulerException e) {
            throw new TaskException("Error in initiating task trigger listener", TaskException.Code.UNKNOWN, e);
        }
    }

    protected TaskRepository getTaskRepository() {
        return taskRepository;
    }

    protected Scheduler getScheduler() {
        return scheduler;
    }

    public int getTenantId() {
        return this.getTaskRepository().getTenantId();
    }

    protected String getTaskType() {
        return this.getTaskRepository().getTasksType();
    }

    protected TaskState getLocalTaskState(String taskName) throws TaskException {
        String taskGroup = this.getTenantTaskGroup();
        try {
            return triggerStateToTaskState(this.getScheduler().getTriggerState(new TriggerKey(taskName, taskGroup)));
        } catch (SchedulerException e) {
            throw new TaskException("Error in checking state of the task with the name: " + taskName,
                                    TaskException.Code.UNKNOWN, e);
        }
    }

    protected void registerLocalTask(TaskInfo taskInfo) throws TaskException {
        this.getTaskRepository().addTask(taskInfo);
    }

    private TaskState triggerStateToTaskState(TriggerState triggerState) {
        if (triggerState == TriggerState.NONE) {
            return TaskState.NONE;
        } else if (triggerState == TriggerState.PAUSED) {
            return TaskState.PAUSED;
        } else if (triggerState == TriggerState.COMPLETE) {
            return TaskState.FINISHED;
        } else if (triggerState == TriggerState.ERROR) {
            return TaskState.ERROR;
        } else if (triggerState == TriggerState.NORMAL) {
            return TaskState.NORMAL;
        } else if (triggerState == TriggerState.BLOCKED) {
            return TaskState.BLOCKED;
        } else {
            return TaskState.UNKNOWN;
        }
    }

    protected synchronized boolean deleteLocalTask(String taskName) throws TaskException {
        String taskGroup = this.getTenantTaskGroup();
        boolean result;
        try {
            result = this.getScheduler().deleteJob(new JobKey(taskName, taskGroup));
            if (result) {
                log.info("Task deleted: [" + this.getTaskType() + "][" + taskName + "]");
                //notify the listeners of the task deletion
                LocalTaskActionListener listener = localTaskActionListeners.get(taskName);
                if (null != listener) {
                    listener.notifyLocalTaskRemoval(taskName);
                }
            }
        } catch (SchedulerException e) {
            throw new TaskException("Error in deleting task with name: " + taskName, TaskException.Code.UNKNOWN, e);
        }
        result &= this.getTaskRepository().deleteTask(taskName);
        return result;
    }

    protected synchronized void pauseLocalTask(String taskName) throws TaskException {
        String taskGroup = this.getTenantTaskGroup();
        try {
            this.getScheduler().pauseJob(new JobKey(taskName, taskGroup));
            //notify the listeners of the task pause
            LocalTaskActionListener listener = localTaskActionListeners.get(taskName);
            if (null != listener) {
                listener.notifyLocalTaskRemoval(taskName);
            }
            log.info("Task paused: [" + this.getTaskType() + "][" + taskName + "]");
        } catch (SchedulerException e) {
            throw new TaskException("Error in pausing task with name: " + taskName, TaskException.Code.UNKNOWN, e);
        }
    }

    protected synchronized void pauseLocalTaskTemporarily(String taskName) throws TaskException {
        String taskGroup = this.getTenantTaskGroup();
        try {
            this.getScheduler().pauseJob(new JobKey(taskName, taskGroup));
            log.info("Task temporarily paused: [" + this.getTaskType() + "][" + taskName + "]");
        } catch (SchedulerException e) {
            throw new TaskException("Error in temporarily pausing task with name: " + taskName,
                    TaskException.Code.UNKNOWN, e);
        }
    }

    protected String getTenantTaskGroup() {
        return "TENANT_" + this.getTenantId() + "_TYPE_" + this.getTaskType();
    }

    private JobDataMap getJobDataMapFromTaskInfo(TaskInfo taskInfo) {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(TaskConstants.TASK_CLASS_NAME, taskInfo.getTaskClass());
        dataMap.put(TaskConstants.TASK_PROPERTIES, taskInfo.getProperties());
        return dataMap;
    }

    protected synchronized void scheduleAllTasks() throws TaskException {
        List<TaskInfo> tasks = this.getTaskRepository().getAllTasks();
        for (TaskInfo task : tasks) {
            try {
                this.handleTask(task.getName());
            } catch (Exception e) {
                log.error("Error in scheduling task: " + e.getMessage(), e);
            }
        }
    }

    protected synchronized void scheduleLocalTask(String taskName) throws TaskException {
        boolean paused = TaskUtils.isTaskPaused(this.getTaskRepository(), taskName);
        this.scheduleLocalTask(taskName, paused);
    }

    protected synchronized void scheduleLocalTask(String taskName, boolean paused) throws TaskException {

        TaskInfo taskInfo = this.getTaskRepository().getTask(taskName);
        String taskGroup = this.getTenantTaskGroup();
        if (taskInfo == null) {
            throw new TaskException("Non-existing task for scheduling with name: " + taskName,
                                    TaskException.Code.NO_TASK_EXISTS);
        }
        if (this.isPreviouslyScheduled(taskName, taskGroup)) {
            /* to make the scheduleLocalTask operation idempotent */
            return;
        }
        Class<? extends Job> jobClass = taskInfo.getTriggerInfo().isDisallowConcurrentExecution() ?
                NonConcurrentTaskQuartzJobAdapter.class :
                TaskQuartzJobAdapter.class;
        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(taskName, taskGroup).usingJobData(
                this.getJobDataMapFromTaskInfo(taskInfo)).build();
        Trigger trigger = this.getTriggerFromInfo(taskName, taskGroup, taskInfo.getTriggerInfo());
        try {
            this.getScheduler().scheduleJob(job, trigger);
            if (paused) {
                this.getScheduler().pauseJob(job.getKey());
            }
            log.info("Task scheduled: [" + this.getTaskType() + "][" + taskName + "]" + (paused ? " [Paused]." : "."));
        } catch (SchedulerException e) {
            throw new TaskException("Error in scheduling task with name: " + taskName, TaskException.Code.UNKNOWN, e);
        }
    }

    private Trigger getTriggerFromInfo(String taskName, String taskGroup, TaskInfo.TriggerInfo triggerInfo)
            throws TaskException {
        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger().withIdentity(taskName, taskGroup);
        if (triggerInfo.getStartTime() == null) {
            triggerBuilder = triggerBuilder.startNow();
        } else {
            triggerBuilder = triggerBuilder.startAt(triggerInfo.getStartTime());
        }
        if (triggerInfo.getEndTime() != null) {
            triggerBuilder.endAt(triggerInfo.getEndTime());
        }
        Trigger trigger;
        if (triggerInfo.getCronExpression() != null) {
            trigger = triggerBuilder.withSchedule(this.getCronScheduleBuilder(triggerInfo)).build();
        } else {
            if (triggerInfo.getRepeatCount() == 0) {
                /* only once executed */
                trigger = triggerBuilder.build();
            } else {
                trigger = triggerBuilder.withSchedule(this.getSimpleScheduleBuilder(triggerInfo)).build();
            }
        }
        return trigger;
    }

    protected synchronized void rescheduleLocalTask(String taskName) throws TaskException {
        String taskGroup = this.getTenantTaskGroup();
        TaskInfo taskInfo = this.getTaskRepository().getTask(taskName);
        Trigger trigger = this.getTriggerFromInfo(taskName, taskGroup, taskInfo.getTriggerInfo());
        try {
            boolean paused = TaskUtils.isTaskPaused(this.getTaskRepository(), taskName);
            Date resultDate = this.getScheduler().rescheduleJob(new TriggerKey(taskName, taskGroup), trigger);
            if (resultDate == null) {
                /* do normal schedule */
                this.scheduleLocalTask(taskName, paused);
            } else if (paused) {
                this.pauseLocalTask(taskName);
            }
        } catch (SchedulerException e) {
            throw new TaskException("Error in rescheduling task with name: " + taskName, TaskException.Code.UNKNOWN, e);
        }
    }

    protected synchronized void resumeLocalTask(String taskName) throws TaskException {
        String taskGroup = this.getTenantTaskGroup();
        if (!this.isPreviouslyScheduled(taskName, taskGroup)) {
            throw new TaskException("Non-existing task for resuming with name: " + taskName,
                                    TaskException.Code.NO_TASK_EXISTS);
        }
        try {
            Trigger trigger = this.getScheduler().getTrigger(new TriggerKey(taskName, taskGroup));
            if (trigger instanceof OperableTrigger) {
                ((OperableTrigger) trigger).setNextFireTime(trigger.getFireTimeAfter(null));
            }
            this.getScheduler().resumeJob(new JobKey(taskName, taskGroup));
            log.info("Task resumed: [" + this.getTaskType() + "][" + taskName + "]");
        } catch (SchedulerException e) {
            throw new TaskException("Error in resuming task with name: " + taskName, TaskException.Code.UNKNOWN, e);
        }
    }

    protected boolean isPreviouslyScheduled(String taskName, String taskGroup) throws TaskException {
        try {
            return this.getScheduler().checkExists(new JobKey(taskName, taskGroup));
        } catch (SchedulerException e) {
            throw new TaskException("Error in retrieving task details", TaskException.Code.UNKNOWN, e);
        }
    }

    private CronScheduleBuilder getCronScheduleBuilder(TaskInfo.TriggerInfo triggerInfo) throws TaskException {
        CronScheduleBuilder cb = CronScheduleBuilder.cronSchedule(triggerInfo.getCronExpression());
        cb = this.handleCronScheduleMisfirePolicy(triggerInfo, cb);
        return cb;
    }

    private CronScheduleBuilder handleCronScheduleMisfirePolicy(TaskInfo.TriggerInfo triggerInfo,
                                                                CronScheduleBuilder cb) throws TaskException {
        switch (triggerInfo.getMisfirePolicy()) {
        case DEFAULT:
            return cb;
        case IGNORE_MISFIRES:
            return cb.withMisfireHandlingInstructionIgnoreMisfires();
        case FIRE_AND_PROCEED:
            return cb.withMisfireHandlingInstructionFireAndProceed();
        case DO_NOTHING:
            return cb.withMisfireHandlingInstructionDoNothing();
        default:
            throw new TaskException("The task misfire policy '" + triggerInfo.getMisfirePolicy()
                                            + "' cannot be used in cron schedule tasks",
                                    TaskException.Code.CONFIG_ERROR);
        }
    }

    private SimpleScheduleBuilder getSimpleScheduleBuilder(TaskInfo.TriggerInfo triggerInfo) throws TaskException {
        SimpleScheduleBuilder scheduleBuilder = null;
        if (triggerInfo.getRepeatCount() == -1) {
            scheduleBuilder = SimpleScheduleBuilder.simpleSchedule().repeatForever();
        } else if (triggerInfo.getRepeatCount() > 0) {
            scheduleBuilder = SimpleScheduleBuilder.simpleSchedule().withRepeatCount(triggerInfo.getRepeatCount());
        }
        if (scheduleBuilder != null) {
            scheduleBuilder = scheduleBuilder.withIntervalInMilliseconds(triggerInfo.getIntervalMillis());
            scheduleBuilder = this.handleSimpleScheduleMisfirePolicy(triggerInfo, scheduleBuilder);
        }
        return scheduleBuilder;
    }

    private SimpleScheduleBuilder handleSimpleScheduleMisfirePolicy(TaskInfo.TriggerInfo triggerInfo,
                                                                    SimpleScheduleBuilder sb) throws TaskException {
        switch (triggerInfo.getMisfirePolicy()) {
        case DEFAULT:
            return sb;
        case FIRE_NOW:
            return sb.withMisfireHandlingInstructionFireNow();
        case IGNORE_MISFIRES:
            return sb.withMisfireHandlingInstructionIgnoreMisfires();
        case NEXT_WITH_EXISTING_COUNT:
            return sb.withMisfireHandlingInstructionNextWithExistingCount();
        case NEXT_WITH_REMAINING_COUNT:
            return sb.withMisfireHandlingInstructionNextWithRemainingCount();
        case NOW_WITH_EXISTING_COUNT:
            return sb.withMisfireHandlingInstructionNowWithExistingCount();
        case NOW_WITH_REMAINING_COUNT:
            return sb.withMisfireHandlingInstructionNowWithRemainingCount();
        default:
            throw new TaskException("The task misfire policy '" + triggerInfo.getMisfirePolicy()
                                            + "' cannot be used in simple schedule tasks",
                                    TaskException.Code.CONFIG_ERROR);
        }
    }

    @Override
    public void registerLocalTaskActionListener(LocalTaskActionListener listener, String taskName) {
        localTaskActionListeners.put(taskName, listener);
    }

    /**
     * Task trigger listener to check when a task is finished.
     */
    public class TaskTriggerListener implements TriggerListener {

        private String name;

        TaskTriggerListener(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void triggerFired(Trigger trigger, JobExecutionContext jobExecutionContext) {
            // do nothing here
        }

        @Override
        public boolean vetoJobExecution(Trigger trigger, JobExecutionContext jobExecutionContext) {
            return false;
        }

        @Override
        public void triggerMisfired(Trigger trigger) {
            // do nothing here
        }

        @Override
        public void triggerComplete(Trigger trigger, JobExecutionContext jobExecutionContext,
                                    Trigger.CompletedExecutionInstruction completedExecutionInstruction) {

            if (trigger.getNextFireTime() == null) {
                try {
                    String taskName = trigger.getJobKey().getName();
                    TaskUtils.setTaskFinished(getTaskRepository(), taskName, true);
                    if (getAllCoordinatedTasksDeployed().contains(taskName)) {
                        removeTaskFromLocallyRunningTaskList(taskName);
                        taskStore.updateTaskState(Collections.singletonList(taskName),
                                                  CoordinatedTask.States.COMPLETED);
                    }
                } catch (TaskException | TaskCoordinationException e) {
                    log.error("Error in Finishing Task [" + trigger.getJobKey().getName() + "]: " + e.getMessage(), e);
                }
            }
        }

    }

}
