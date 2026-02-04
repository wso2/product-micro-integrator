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

package org.wso2.micro.integrator.management.apis;

import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.micro.integrator.utils.utils.ServerConstants;
import org.wso2.micro.core.util.StringUtils;
import org.wso2.micro.integrator.initializer.handler.DataHolder;
import org.wso2.micro.integrator.initializer.handler.transaction.exception.TransactionCounterException;
import org.wso2.micro.integrator.initializer.handler.transaction.store.TransactionStore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.management.apis.Constants.FORBIDDEN;
import static org.wso2.micro.integrator.management.apis.Constants.INTERNAL_SERVER_ERROR;


/**
 * This feature is deprecated and will be removed in a future release.
 * @deprecated
 *
 * Resource for a retrieving aggregated transaction count.
 * <p>
 * Handles resources in the form "management/transactions/{param}"
 */
public class RequestCountResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(RequestCountResource.class);

    @Override
    public Set<String> getMethods() {

        Set<String> methods = new HashSet<>();
        methods.add("GET");
        return methods;
    }

    @Override
    public boolean invoke(MessageContext synCtx,
                          org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {

        String pathParam = Utils.getPathParameter(synCtx, "param");

        if ("count".equalsIgnoreCase(pathParam)) {
            String yearParameter = Utils.getQueryParameter(synCtx, "year");
            String monthParameter = Utils.getQueryParameter(synCtx, "month");
            return handleTransactionCountCommand(axis2MessageContext, yearParameter, monthParameter);

        } else if ("report".equalsIgnoreCase(pathParam)) {
            String start = Utils.getQueryParameter(synCtx, "start");
            String end = Utils.getQueryParameter(synCtx, "end");
            return handleTransactionReportCommand(axis2MessageContext, start, end);

        } else {
            JSONObject response = Utils.createJsonError("No such resource as management/transactions/" + pathParam,
                                                        axis2MessageContext, BAD_REQUEST);
            Utils.setJsonPayLoad(axis2MessageContext, response);
            return true;
        }
    }

    private boolean handleTransactionCountCommand(org.apache.axis2.context.MessageContext axis2MessageContext,
                                                  String yearParameter, String monthParameter) {
        String errorMessage;
        if (StringUtils.isEmpty(yearParameter) && StringUtils.isEmpty(monthParameter)) {
            Date date = new Date();
            LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return takeTransactionCountOfTheMonth(axis2MessageContext, localDate.getYear(),
                                                  localDate.getMonthValue());
        } else if (StringUtils.isEmpty(yearParameter) || StringUtils.isEmpty(monthParameter)) {
            errorMessage = "Either both \"year\" and \"month\" arguments or none should be specified.";
        } else {
            Integer year = tryParseInt(yearParameter);
            Integer month = tryParseInt(monthParameter);

            if (null != year && null != month) {
                return takeTransactionCountOfTheMonth(axis2MessageContext, year, month);
            } else if (null == year && null == month) {
                errorMessage = "Invalid inputs for arguments \"year\" and \"month\".";
            } else if (null == year) {
                errorMessage = "Invalid input for argument \"year\".";
            } else {
                errorMessage = "Invalid input for argument \"month\".";
            }
        }

        JSONObject response = Utils.createJsonError(errorMessage, axis2MessageContext, BAD_REQUEST);
        Utils.setJsonPayLoad(axis2MessageContext, response);
        return true;
    }

    private boolean handleTransactionReportCommand(org.apache.axis2.context.MessageContext axis2MessageContext,
                                                   String start, String end) {
        String errorMessage;
        if (StringUtils.isEmpty(start)) {
            errorMessage = "Argument \"start\" cannot be empty";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate startDate = tryParseDate(start + "-01", formatter);
            if (null != startDate) {
                LocalDate endDate;
                if (StringUtils.isEmpty(end)) {
                    endDate = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    return getReportContent(axis2MessageContext, startDate, endDate);
                }
                endDate = tryParseDate(end + "-01", formatter);
                if (null != endDate) {
                    return getReportContent(axis2MessageContext, startDate, endDate);
                }
                errorMessage = "Invalid input for argument \"end\"";
            } else {
                errorMessage = "Invalid input for argument \"start\"";
            }
        }
        JSONObject response = Utils.createJsonError(errorMessage, axis2MessageContext, BAD_REQUEST);
        Utils.setJsonPayLoad(axis2MessageContext, response);
        return true;
    }

    // Take aggregated transaction count for a given month.
    private boolean takeTransactionCountOfTheMonth(org.apache.axis2.context.MessageContext axisCtx, int year,
                                                   int month) {

        JSONObject response;
        axisCtx.setProperty(org.apache.axis2.Constants.Configuration.MESSAGE_TYPE, "application/json");
        axisCtx.setProperty(Constants.Configuration.CONTENT_TYPE, "application/json");

        TransactionStore transactionStore = DataHolder.getInstance().getTransactionStore();
        if (transactionStore != null) {
            // Get transaction count of current Month
            long transactionCount;
            try {
                transactionCount = transactionStore.getTransactionCountOfMonth(year, month);
            } catch (TransactionCounterException e) {
                response = Utils.createJsonError("Error occurred while retrieving data from the database ", e,
                                                 axisCtx, INTERNAL_SERVER_ERROR);
                Utils.setJsonPayLoad(axisCtx, response);
                LOG.error("Error occurred while getting the transaction count from the database", e);
                return true;
            }
            if (transactionCount != -1L) {
                response = new JSONObject("{\"Year\" : " + year + ", \"Month\" : " + month + ", "
                                                  + "\"TransactionCount\" : " + transactionCount + "}");
            } else {
                response = new JSONObject("{\"error\" : \"Did not find stats for " + year + "/" + month + "\"}");
            }
        } else {
            response = Utils.createJsonError("Transaction Count Handler component is not initialized", axisCtx,
                                             FORBIDDEN);
        }
        Utils.setJsonPayLoad(axisCtx, response);
        return true;
    }

    /**
     * Create the content of the transaction count report and generate the report in <CARBON_HOME>/temp directory.
     *
     * @param axisCtx   axis message context
     * @param startDate date from which the values in the report should starts
     * @param endDate   date upto which the values in the report should includes
     * @return true
     */
    private boolean getReportContent(org.apache.axis2.context.MessageContext axisCtx, LocalDate startDate,
                                     LocalDate endDate) {
        String filePath = System.getProperty(ServerConstants.CARBON_HOME) + File.separator + "tmp" + File.separator +
                "transaction-count-summary-" + new Date().getTime() + ".csv";
        JSONObject response;
        axisCtx.setProperty(org.apache.axis2.Constants.Configuration.MESSAGE_TYPE, "application/json");
        axisCtx.setProperty(Constants.Configuration.CONTENT_TYPE, "application/json");

        TransactionStore transactionStore = DataHolder.getInstance().getTransactionStore();
        if (null == transactionStore) {
            response = Utils.createJsonError("Transaction Count Handler component is not initialized", axisCtx,
                                             FORBIDDEN);
        } else {
            String errorMessage;
            try {
                List<String[]> transactionCountData = transactionStore
                        .getTransactionCountDataWithColumnNames(startDate.toString(),
                                                                endDate.toString());
                writeToCSVFile(filePath, transactionCountData);
                response = new JSONObject();
                response.put("TransactionCountData", populateReportContent(transactionCountData));
                LOG.info("Transaction count report is created at " + filePath);
            } catch (TransactionCounterException e) {
                errorMessage = "Error occurred while retrieving the transaction count data from the database";
                response = Utils.createJsonError(errorMessage, e, axisCtx, INTERNAL_SERVER_ERROR);
                LOG.error(errorMessage, e);
            } catch (IOException e) {
                errorMessage = "Error occurred while writing the data to the output file: " + filePath;
                response = Utils.createJsonError(errorMessage, e, axisCtx, INTERNAL_SERVER_ERROR);
                LOG.error(errorMessage, e);
            }
        }
        Utils.setJsonPayLoad(axisCtx, response);
        return true;
    }

    /**
     * Populate the report content.
     *
     * @param dataLines Data to be included in the report.
     * @return a JSONArray with the report content
     */
    private JSONArray populateReportContent(List<String[]> dataLines) {
        JSONArray arr = new JSONArray();
        for (String[] line : dataLines) {
            arr.put(line);
        }
        return arr;
    }

    /**
     * Write data to the given CSV file.
     *
     * @param filePath  of the target csv file
     * @param dataLines Data to be written to the report.
     */
    private void writeToCSVFile(String filePath, List<String[]> dataLines) throws IOException {
        try (FileWriter csvOutputFile = new FileWriter(filePath, true);
             PrintWriter writer = new PrintWriter(csvOutputFile)) {
            dataLines.stream()
                    .map(this::convertToCSV)
                    .forEach(writer::println);
        }
    }

    /**
     * Convert a string array to a string separated by a ",".
     *
     * @param data to be converted to a comma separated string
     * @return comma separated string
     */
    private String convertToCSV(String[] data) {
        return String.join(",", data);
    }

    /**
     * Try to parse input string to an Integer.
     *
     * @param someText input string.
     * @return parsed integer, null if failed.
     */
    private static Integer tryParseInt(String someText) {

        try {
            return Integer.parseInt(someText);
        } catch (NumberFormatException ex) {
            LOG.error("Invalid input. Cannot parse the input '" + someText + "' as an Integer: ", ex);
            return null;
        }
    }

    /**
     * Try to parse input string to a LocalDate.
     *
     * @param date      input string
     * @param formatter date formatter
     * @return parsed Date, null if failed
     */
    private static LocalDate tryParseDate(String date, DateTimeFormatter formatter) {
        try {
            return LocalDate.parse(date, formatter);
        } catch (DateTimeParseException ex) {
            LOG.error("Invalid Date format. Cannot parse the input '" + date + "' as a LocalDate: ", ex);
            return null;
        }
    }
}
