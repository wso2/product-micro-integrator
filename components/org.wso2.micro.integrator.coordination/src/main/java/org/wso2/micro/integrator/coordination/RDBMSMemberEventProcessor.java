/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.coordination;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.coordination.exception.ClusterCoordinationException;
import org.wso2.micro.integrator.coordination.util.MemberEventType;
import org.wso2.micro.integrator.coordination.util.RDBMSConstantUtils;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * This class adds the event listener tasks for each group and executes them.
 */
public class RDBMSMemberEventProcessor {
    /**
     * Class log.
     */
    private static final Log log = LogFactory.getLog(RDBMSMemberEventProcessor.class);

    /**
     * Task map used store membership listener tasks.
     */
    private RDBMSMemberEventListenerTask membershipListenerTask;

    /**
     * Executor service used to run the event listening task.
     */
    private ScheduledExecutorService clusterMembershipReaderTaskScheduler;

    /**
     * Communication bus object to communicate with the database for the context store.
     */
    private RDBMSCommunicationBusContextImpl communicationBusContext;

    public RDBMSMemberEventProcessor(String localNodeId, String localGroupId, int heartbeatMaxRetry,
                                     RDBMSCommunicationBusContextImpl communicationBusContext, long maxDBReadTime) {
        this.communicationBusContext = communicationBusContext;
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("ClusterEventReaderTask-%d").build();
        this.clusterMembershipReaderTaskScheduler = Executors.newSingleThreadScheduledExecutor(namedThreadFactory);
        addNewListenerTask(localNodeId, localGroupId, heartbeatMaxRetry, maxDBReadTime);
    }

    /**
     * Method to start the membership listener task.
     *
     * @param nodeId the node ID of the node which starts the listening
     */
    private void addNewListenerTask(String nodeId, String localGroupId, int heartbeatMaxRetry, long maxDBReadTime) {
        int scheduledPeriod;
        String scheduledPeriodStr = System.getProperty(RDBMSConstantUtils.SCHEDULED_PERIOD);
        if (scheduledPeriodStr == null) {
            scheduledPeriodStr = System.getenv(RDBMSConstantUtils.SCHEDULED_PERIOD);
        }
        if (scheduledPeriodStr == null){
            scheduledPeriod = RDBMSConstantUtils.DEFAULT_SCHEDULED_PERIOD_INTERVAL;
        } else {
            try{
                scheduledPeriod = Integer.parseInt(scheduledPeriodStr);
            } catch (NumberFormatException e){
                scheduledPeriod = RDBMSConstantUtils.DEFAULT_SCHEDULED_PERIOD_INTERVAL;
            }
        }
        membershipListenerTask = new RDBMSMemberEventListenerTask(nodeId, localGroupId, communicationBusContext,
                heartbeatMaxRetry, maxDBReadTime);
        this.clusterMembershipReaderTaskScheduler.scheduleWithFixedDelay(membershipListenerTask,
                                                                         scheduledPeriod, scheduledPeriod, TimeUnit.MILLISECONDS);
        if (log.isDebugEnabled()) {
            log.debug("RDBMS cluster event listener started for node " + nodeId);
        }
    }

    /**
     * Method to stop the membership listener task.
     */
    public void stop() {
        clusterMembershipReaderTaskScheduler.shutdown();
    }

    /**
     * Notifies the other members in the group about the membership events.
     *
     * @param nodeID              the group id which triggered the event
     * @param groupID             the node id which triggered the event
     * @param nodes               The node list which the event should be updated to
     * @param membershipEventType the type of the membership event as an int
     * @throws ClusterCoordinationException
     */
    public void notifyMembershipEvent(String nodeID, String groupID, List<String> nodes,
                                      MemberEventType membershipEventType) throws ClusterCoordinationException {
        this.communicationBusContext.storeMembershipEvent(nodeID, groupID, nodes, membershipEventType.getCode());
    }

    /**
     * Add a listener to be notified of the cluster membership events.
     *
     * @param membershipListener membership listener object
     */
    public void addEventListener(MemberEventListener membershipListener) {
        membershipListenerTask.addEventListener(membershipListener);
    }

    /**
     * Set the unresponsiveness of a member if needed.
     *
     * @param nodeID          the node ID of the member
     * @param localGroupId    the local group ID
     * @param setUnresponsive flag to set unresponsiveness
     */
    public void setMemberUnresponsiveIfNeeded(String nodeID, String localGroupId, boolean setUnresponsive) {
        membershipListenerTask.setMemberUnresponsiveIfNeeded(nodeID, localGroupId, setUnresponsive);
    }

    /**
     * Check whether the member has rejoined after being unresponsive.
     *
     * @return true if the member has rejoined after being unresponsive, false otherwise
     */
    public boolean isMemberUnresponsive() {
        return membershipListenerTask.isMemberUnresponsive();
    }

    /**
     * Set the rejoin status of a member.
     *
     * @param nodeID       the node ID of the member
     * @param localGroupId the local group ID
     */
    public void setMemberRejoined(String nodeID, String localGroupId) {
        membershipListenerTask.notifyRejoin(nodeID, localGroupId);
    }

    /**
     * Remove a previously added listener.
     *
     * @param membershipListener membership listener object
     */
    public void removeEventListener(MemberEventListener membershipListener) {
        membershipListenerTask.removeEventListener(membershipListener);
    }

}
