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

import org.wso2.micro.integrator.coordination.node.NodeDetail;

/**
 * Interface that declares methods to be run when a cluster membership event change.
 */
public abstract class MemberEventListener {

    private String groupId;

    /**
     * Invoked when a new member is added to the cluster.
     *
     * @param nodeDetail node detail object of the new node
     */
    public abstract void memberAdded(NodeDetail nodeDetail);

    /**
     * Invoked when an existing member leaves the cluster.
     *
     * @param nodeDetail node detail object of the removed node
     */
    public abstract void memberRemoved(NodeDetail nodeDetail);

    /**
     * Invoked when the coordinator is changed in the cluster.
     *
     * @param nodeDetail node detail object of the new coordinator node
     */
    public abstract void coordinatorChanged(NodeDetail nodeDetail);

    /**
     * Invoked when the node is in inactive state.
     */
    public abstract void becameUnresponsive(String nodeId);

    /**
     * Invoked when the node is back to active state after being inactive.
     *
     * @param nodeId - The Id of this node
     * @param callBack - The callback to be called when an exception is thrown
     */
    public abstract void reJoined(String nodeId, RDBMSMemberEventCallBack callBack);

    /**
     * Invoked when the message processor state is changed.
     *
     * @param messageProcessorName - The name of the message processor
     * @param state - The new state of the message processor
     */
    public abstract void messageProcessorStateChanged(String messageProcessorName, String state);

    public String getGroupId() {
        return this.groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}
