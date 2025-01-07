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

package org.wso2.micro.integrator.ntask.coordination.task.store.connector;

import org.wso2.micro.integrator.ntask.coordination.task.CoordinatedTask;

/**
 * The class which contains all the data base queries for the task database.
 */
public class TaskQueryHelper {

    //task table Name
    public static final String TABLE_NAME = "COORDINATED_TASK_TABLE";

    // Task table columns
    public static final String TASK_NAME = "TASK_NAME";
    public static final String DESTINED_NODE_ID = "DESTINED_NODE_ID";
    public static final String TASK_STATE = "TASK_STATE";

    private static final String TASK_STATE_CONST =
            "( CASE " + TASK_STATE + " WHEN '" + CoordinatedTask.States.RUNNING + "' THEN '"
                    + CoordinatedTask.States.NONE + "' WHEN '" + CoordinatedTask.States.DEACTIVATED + "'THEN '"
                    + CoordinatedTask.States.PAUSED + "' ELSE " + TASK_STATE + " END )";

    static final String ADD_TASK =
            "INSERT INTO " + TABLE_NAME + " ( " + TASK_NAME + ", " + DESTINED_NODE_ID + ", " + TASK_STATE + ") "
                    + "VALUES (?,NULL,?)";

    static final String UPDATE_ASSIGNMENT_AND_STATE =
            "UPDATE  " + TABLE_NAME + " SET  " + DESTINED_NODE_ID + " = ? , " + TASK_STATE + " = " + TASK_STATE_CONST
                    + " WHERE " + TASK_NAME + " = ?";

    static final String UPDATE_TASK_STATUS_TO_DEACTIVATED =
            "UPDATE  " + TABLE_NAME + "  SET " + TASK_STATE + " = '" + CoordinatedTask.States.DEACTIVATED + "' "
                    + "WHERE " + TASK_NAME + " =? AND " + TASK_STATE + " !='" + CoordinatedTask.States.PAUSED + "'";

    static final String ACTIVATE_TASK =
            "UPDATE  " + TABLE_NAME + "  SET " + TASK_STATE + " = '" + CoordinatedTask.States.ACTIVATED + "' WHERE "
                    + TASK_NAME + " =? AND " + TASK_STATE + " !='" + CoordinatedTask.States.RUNNING + "'";

    static final String UPDATE_TASK_STATE =
            "UPDATE  " + TABLE_NAME + "  SET " + TASK_STATE + " = ? WHERE " + TASK_NAME + " =? ";

    static final String UPDATE_TASK_STATE_FOR_DESTINED_NODE =
            "UPDATE  " + TABLE_NAME + "  SET " + TASK_STATE + " = ? WHERE " + TASK_NAME + " =? AND " + DESTINED_NODE_ID
                    + " =?";

    static final String RETRIEVE_ALL_TASKS = "SELECT  " + TASK_NAME  + "," + DESTINED_NODE_ID + " FROM " + TABLE_NAME;

    static final String RETRIEVE_UNASSIGNED_NOT_COMPLETED_TASKS =
            "SELECT " + TASK_NAME + " FROM " + TABLE_NAME + " WHERE  " + DESTINED_NODE_ID + " IS NULL AND " + TASK_STATE
                    + " !='" + CoordinatedTask.States.COMPLETED + "'";

    static final String RETRIEVE_TASKS_OF_NODE =
            "SELECT " + TASK_NAME + " FROM " + TABLE_NAME + "  WHERE " + DESTINED_NODE_ID + " =? AND " + TASK_STATE
                    + " =?";

    static final String RETRIEVE_TASK_STATE =
            "SELECT " + TASK_STATE + " FROM " + TABLE_NAME + "  WHERE " + TASK_NAME + " =?";

    static final String REMOVE_ASSIGNMENT_AND_UPDATE_STATE =
            "UPDATE " + TABLE_NAME + " SET " + DESTINED_NODE_ID + " = NULL , " + TASK_STATE + " = " + TASK_STATE_CONST
                    + " WHERE " + TASK_NAME + " =?";

    static final String REMOVE_TASKS_OF_NODE = "DELETE FROM " + TABLE_NAME + "  WHERE " + DESTINED_NODE_ID + " =? AND "
            + TASK_STATE + " NOT IN ('" + CoordinatedTask.States.COMPLETED + "', '" + CoordinatedTask.States.ACTIVATED
            + "', '" + CoordinatedTask.States.DEACTIVATED + "')";

    static final String DELETE_TASK = "DELETE FROM " + TABLE_NAME + " WHERE " + TASK_NAME + " =?";

    static final String CLEAN_TASKS_OF_NODE =
            "UPDATE " + TABLE_NAME + " SET " + DESTINED_NODE_ID + " = NULL , " + TASK_STATE + " = " + TASK_STATE_CONST
                    + " WHERE " + DESTINED_NODE_ID + " = ? AND " + TASK_STATE + " !='"
                    + CoordinatedTask.States.COMPLETED + "'";

    static final String GET_ALL_ASSIGNED_INCOMPLETE_TASKS =
            "SELECT * FROM " + TABLE_NAME + " WHERE " + DESTINED_NODE_ID + " IS NOT NULL AND " + TASK_STATE + " != '"
                    + CoordinatedTask.States.COMPLETED + "'";

    private TaskQueryHelper() throws IllegalAccessException {
        throw new IllegalAccessException("This class not to be initialized.");
    }

}
