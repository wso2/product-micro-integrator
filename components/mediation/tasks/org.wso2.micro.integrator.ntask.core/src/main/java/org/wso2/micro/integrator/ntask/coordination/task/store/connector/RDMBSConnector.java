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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.ntask.coordination.TaskCoordinationException;
import org.wso2.micro.integrator.ntask.coordination.task.CoordinatedTask;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.ACTIVATE_TASK;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.ADD_TASK;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.CLEAN_TASKS_OF_NODE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.DELETE_TASK;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.DESTINED_NODE_ID;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.GET_ALL_ASSIGNED_INCOMPLETE_TASKS;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.REMOVE_ASSIGNMENT_AND_UPDATE_STATE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.REMOVE_TASKS_OF_NODE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.RETRIEVE_ALL_TASKS;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.RETRIEVE_TASKS_OF_NODE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.RETRIEVE_TASK_STATE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.RETRIEVE_UNASSIGNED_NOT_COMPLETED_TASKS;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.TASK_NAME;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.TASK_STATE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.UPDATE_ASSIGNMENT_AND_STATE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.UPDATE_TASK_STATE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.UPDATE_TASK_STATE_FOR_DESTINED_NODE;
import static org.wso2.micro.integrator.ntask.coordination.task.store.connector.TaskQueryHelper.UPDATE_TASK_STATUS_TO_DEACTIVATED;

/**
 * The connector class which deals with underlying coordinated task table.
 */
public class RDMBSConnector {

    private static final Log LOG = LogFactory.getLog(RDMBSConnector.class);
    private static final String ERROR_MSG = "Error while doing data base operation.";
    private static final String EMPTY_LIST = "Provided list is empty ";
    private static final String SQL_INTEGRITY_VIOLATION_CODE = "23";
    private DataSource dataSource;

    /**
     * Constructor.
     *
     * @param dataSource - The datasource config to initiate the connection.
     * @throws TaskCoordinationException - Exception.
     */
    public RDMBSConnector(DataSource dataSource) throws TaskCoordinationException {

        this.dataSource = dataSource;
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseType = metaData.getDatabaseProductName();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully connected to : " + databaseType);
            }
        } catch (SQLException ex) {
            throw new TaskCoordinationException("Error while initializing RDBMS connection.", ex);
        }
    }

    /**
     * Removes the node id of the task and update the task state.
     *
     * @param tasks - List of coordinated tasks which needs to be updated.
     */
    public void unAssignAndUpdateState(List<String> tasks) throws TaskCoordinationException {

        if (tasks.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(EMPTY_LIST + "for un assignment removal.");
            }
            return;
        }
        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                REMOVE_ASSIGNMENT_AND_UPDATE_STATE)) {
            for (String task : tasks) {
                preparedStatement.setString(1, task);
                preparedStatement.addBatch();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Removing the node assignment of task [" + task + "].");
                }
            }
            preparedStatement.executeBatch();
            if (LOG.isDebugEnabled()) {
                tasks.forEach(task -> LOG.debug("Successfully removed the node assignment of task [" + task + "]."));
            }
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Sets the destined node id to null and state to none if running or to paused if deactivated.
     *
     * @param nodeId - Node Id which needs to be set to null.
     */
    public void unAssignAndUpdateState(String nodeId) throws TaskCoordinationException {

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                CLEAN_TASKS_OF_NODE)) {
            preparedStatement.setString(1, nodeId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Un assigning the tasks of node [" + nodeId + "].");
            }
            preparedStatement.executeUpdate();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully un assigned the tasks of node [" + nodeId + "].");
            }
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Retrieves the list of task names.
     *
     * @param nodeID - Id of the node, for which the tasks need to be retrieved.
     * @param state  - State of the tasks which need to be retrieved.
     * @return - List of task names
     */
    public List<String> retrieveTaskNames(String nodeID, CoordinatedTask.States state)
            throws TaskCoordinationException {

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                RETRIEVE_TASKS_OF_NODE)) {
            preparedStatement.setString(1, nodeID);
            preparedStatement.setString(2, state.name());
            return query(preparedStatement, "for node [" + nodeID + "] with state [" + state.name() + "]");
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    private void printDebugLogs(List<Object> tasks, String msg) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(msg);
            tasks.forEach(LOG::debug);
        }
    }

    /**
     * Removes all the tasks assigned to the node.
     *
     * @param nodeId - The node id.
     */
    public void deleteTasks(String nodeId) throws TaskCoordinationException {

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                REMOVE_TASKS_OF_NODE)) {
            preparedStatement.setString(1, nodeId);
            preparedStatement.executeUpdate();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Removed all the tasks of node [" + nodeId + "].");
            }
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Deactivates the task.
     *
     * @param name - Name of the task.
     */
    public void deactivateTask(String name) throws TaskCoordinationException {
        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                UPDATE_TASK_STATUS_TO_DEACTIVATED)) {
            preparedStatement.setString(1, name);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Activates the task.
     *
     * @param name - Name of the task.
     */
    public void activateTask(String name) throws TaskCoordinationException {
        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                ACTIVATE_TASK)) {
            preparedStatement.setString(1, name);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Retrieve the state of the task.
     *
     * @param name name of the task
     * @return state of the task
     * @throws TaskCoordinationException if something goes wrong while doing db read
     */
    public CoordinatedTask.States getTaskState(String name) throws TaskCoordinationException {
        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                RETRIEVE_TASK_STATE)) {
            preparedStatement.setString(1, name);
            preparedStatement.executeQuery();
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return CoordinatedTask.States.valueOf(resultSet.getString(TASK_STATE));
                }
            }
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
        return null;
    }

    /**
     * Remove the task entry.
     *
     * @param tasks - List of tasks to be removed.
     */
    public void deleteTasks(List<String> tasks) throws TaskCoordinationException {

        if (tasks.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(EMPTY_LIST + " for deleting tasks.");
            }
            return;
        }
        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                DELETE_TASK)) {
            for (String task : tasks) {
                preparedStatement.setString(1, task);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            printDebugLogs(new ArrayList<>(tasks), "Following list of tasks were deleted.");
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Helper method to query data base and return task list.
     *
     * @param preparedStatement - Statement to be executed to retrieve the list of tasks.
     * @throws SQLException - Exception.
     */
    private List<String> query(PreparedStatement preparedStatement, String debug) throws SQLException {
        List<String> taskNames = new ArrayList<>();
        try (ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                taskNames.add(resultSet.getString(TASK_NAME));
            }
        }
        printDebugLogs(new ArrayList<>(taskNames), "Following list of tasks were retrieved " + debug);
        return taskNames;
    }

    /**
     * Helper method to query data base and return coordinated task list.
     *
     * @param preparedStatement - Statement to be executed to retrieve the list of tasks.
     * @throws SQLException - Exception.
     */
    private List<CoordinatedTask> queryTasks(PreparedStatement preparedStatement, String debug) throws SQLException {
        List<CoordinatedTask> tasks = new ArrayList<>();
        try (ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                CoordinatedTask task = new CoordinatedTask(resultSet.getString(TASK_NAME),
                        resultSet.getString(DESTINED_NODE_ID), CoordinatedTask.States.RUNNING);
                tasks.add(task);
            }
        }
        if (LOG.isDebugEnabled()) {
            printDebugLogs(tasks.stream().map(CoordinatedTask::getTaskName).collect(Collectors.toList()),
                    "Following list of tasks were retrieved " + debug);
        }
        return tasks;
    }

    /**
     * Helper method query data base and return task list.
     *
     * @param preparedStatement - Statement to be executed to retrieve the list of tasks.
     * @throws SQLException - Exception.
     */
    private List<CoordinatedTask> executeQuery(PreparedStatement preparedStatement) throws SQLException {

        List<CoordinatedTask> tasks = new ArrayList<>();
        try (ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {

                tasks.add(new CoordinatedTask(resultSet.getString(TASK_NAME), resultSet.getString(DESTINED_NODE_ID),
                                              CoordinatedTask.States.valueOf(resultSet.getString(TASK_STATE))));
            }
        }
        printDebugLogs(new ArrayList<>(tasks),
                       "Following list of tasks were retrieved for assigned and incomplete tasks.");
        return tasks;
    }

    /**
     * Retrieve all the tasks.
     *
     * @return - List of available tasks.
     */
    public List<CoordinatedTask> getAllTaskNames() throws TaskCoordinationException {

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                RETRIEVE_ALL_TASKS)) {
            return queryTasks(preparedStatement, "for all available tasks names.");
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Retrieve all assigned and incomplete tasks.
     *
     * @return - List of tasks.
     */
    public List<CoordinatedTask> getAllAssignedIncompleteTasks() throws TaskCoordinationException {

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                GET_ALL_ASSIGNED_INCOMPLETE_TASKS)) {
            return executeQuery(preparedStatement);
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Add the task if doesn't exist already.
     *
     * @param taskName - The task which needs to be added.
     */
    public void addTaskIfNotExist(String taskName, CoordinatedTask.States state) throws TaskCoordinationException {

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                ADD_TASK)) {
            preparedStatement.setString(1, taskName);
            preparedStatement.setString(2, state.name());
            preparedStatement.executeUpdate();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully added the task [" + taskName + "].");
            }
        } catch (SQLException ex) {
            if (ex.getSQLState().startsWith(SQL_INTEGRITY_VIOLATION_CODE)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Task [" + taskName + "] already exists.");
                }
            } else {
                throw new TaskCoordinationException(ERROR_MSG, ex);
            }
        }
    }

    /**
     * Updates the destined node id and state to none if it was in running.
     *
     * @param tasks - List of tasks to be updated.
     */
    public void updateAssignmentAndState(Map<String, String> tasks) throws TaskCoordinationException {

        if (tasks.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(EMPTY_LIST + " for update assignment and state change to none if running.");
            }
            return;
        }
        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                UPDATE_ASSIGNMENT_AND_STATE)) {
            for (Map.Entry<String, String> entry : tasks.entrySet()) {
                preparedStatement.setString(1, entry.getValue());
                preparedStatement.setString(2, entry.getKey());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            if (LOG.isDebugEnabled()) {
                tasks.forEach((task, destinedNode) -> LOG
                        .debug("Assigned the task [" + task + "] with destined node [" + destinedNode + "]"));
            }
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Update the state of task.
     *
     * @param taskName     Name of the task.
     * @param updatedState Updated state.
     * @param destinedId   Destined Node Id.
     * @return True if update is successful.
     * @throws TaskCoordinationException when something goes wrong while updating.
     */
    public boolean updateTaskState(String taskName, CoordinatedTask.States updatedState, String destinedId)
            throws TaskCoordinationException {

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                UPDATE_TASK_STATE_FOR_DESTINED_NODE)) {
            preparedStatement.setString(1, updatedState.name());
            preparedStatement.setString(2, taskName);
            preparedStatement.setString(3, destinedId);
            int result = preparedStatement.executeUpdate();
            if (LOG.isDebugEnabled()) {
                LOG.debug((result == 1 ? "Updated" : "Unable to update") + " state to [" + updatedState + "] for task ["
                                  + "] with destined nodeId [" + destinedId + "]");
            }
            return result == 1;
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Updates the stat of a task.
     *
     * @param tasks - Name of the task.
     * @param state - State to be updated.
     */
    public void updateTaskState(List<String> tasks, CoordinatedTask.States state) throws TaskCoordinationException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(EMPTY_LIST + " for    update assignment and state change to none if running.");
            return;
        }
        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                UPDATE_TASK_STATE)) {
            for (String task : tasks) {
                preparedStatement.setString(1, state.name());
                preparedStatement.setString(2, task);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            if (LOG.isDebugEnabled()) {
                tasks.stream().map(task -> "Paused task [" + task + "]").forEachOrdered(LOG::debug);
            }
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Get All unassigned tasks except the completed ones.
     *
     * @return - List of unassigned and in complete tasks.
     */
    public List<String> retrieveAllUnAssignedAndIncompleteTasks() throws TaskCoordinationException {

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(
                RETRIEVE_UNASSIGNED_NOT_COMPLETED_TASKS)) {
            return query(preparedStatement, "for unassigned incomplete tasks");
        } catch (SQLException ex) {
            throw new TaskCoordinationException(ERROR_MSG, ex);
        }
    }

    /**
     * Get connection.
     *
     * @return - Connection with auto commit true.
     * @throws SQLException -
     */
    private Connection getConnection() throws SQLException {

        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(true);
        return connection;
    }

}
