/*
 * Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.dataservices.core.opentelemetry;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.aspects.flow.statistics.collectors.RuntimeStatisticCollector;
import org.apache.synapse.aspects.flow.statistics.data.raw.StatisticDataUnit;
import org.apache.synapse.aspects.flow.statistics.tracing.opentelemetry.OpenTelemetryManagerHolder;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.description.query.Query;
import org.wso2.micro.integrator.dataservices.core.dispatch.DataServiceRequest;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery;
import org.wso2.micro.integrator.dataservices.core.engine.CallableRequest;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;

import java.util.HashMap;
import java.util.Map;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DATA_SERVICE_INDEX;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DATA_QUERY_EXECUTION_INDEX;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DB_CONFIG_ID;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DB_QUERY_ID;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DEFAULT_ODATA_SERVICE_NAME;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DEFAULT_PARENT_INDEX;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.HTTP_METHOD;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.HTTP_METHOD_OBJECT;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.HTTP_URL;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.ODATA_SERVICE;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.MULTI_REQUEST_BASE_INDEX;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.MULTI_REQUEST_LAST_INDEX_PROPERTY;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.TRANSPORT_IN_URL;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.URL_SEPARATOR;
import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.URL_SEPARATOR_CHAR;

/**
 * DataServicesTracingCollector receives statistic events and responsible for handling each of these events.
 */
public class DataServicesTracingCollector extends RuntimeStatisticCollector {
    private static final Log log = LogFactory.getLog(DataServicesTracingCollector.class);

    /**
     * Report entry event for data service invocation.
     * @param messageContext Axis2 message context
     */
    public static void reportEntryEvent(MessageContext messageContext) {
        if (isOpenTelemetryEnabled() && messageContext != null) {
            String componentName = messageContext.getAxisService().getName();
            Map<String, Object> customProperties = getHTTPProperties(messageContext);
            setStatisticsTraceId(messageContext);

            StatisticDataUnit statisticDataUnit = new StatisticDataUnit();
            statisticDataUnit.setComponentName(componentName);
            statisticDataUnit.setComponentId("DataService:" + componentName);
            statisticDataUnit.setOuterLayerSpan(true);
            statisticDataUnit.setCurrentIndex(DATA_SERVICE_INDEX);
            statisticDataUnit.setParentIndex(DEFAULT_PARENT_INDEX);
            statisticDataUnit.setComponentTypeString("DataService");
            statisticDataUnit.setTime(System.currentTimeMillis());
            statisticDataUnit.setCustomProperties(customProperties);

            setPayload(messageContext.getEnvelope(), statisticDataUnit);

            OpenTelemetryManagerHolder.getOpenTelemetryManager().getHandler()
                    .handleOpenEntryEvent(statisticDataUnit, messageContext);
        }
    }

    /**
     * Close entry event for data service invocation.
     *
     * @param messageContext Axis2 message context
     * @param result         Payload OMElement result payload
     */
    public static void closeEntryEvent(MessageContext messageContext, OMElement result) {
        handleCloseEvents(messageContext, result, DATA_SERVICE_INDEX);
    }

    /**
     * Report query execution event for data service invocation.
     *
     * @param messageContext Axis2 message context
     * @param request        Data service request
     */
    public static void reportQueryExecutionEvent(MessageContext messageContext, DataServiceRequest request) {
        if (isOpenTelemetryEnabled() && messageContext != null) {
            AxisService axisService = messageContext.getAxisService();
            String componentName = axisService.getName();
            Map<String, Object> customProperties = getDataServiceProperties(request);

            StatisticDataUnit statisticDataUnit = new StatisticDataUnit();
            statisticDataUnit.setComponentName(componentName);
            statisticDataUnit.setComponentId("QueryExecution:" + componentName);
            statisticDataUnit.setComponentTypeString("QueryExecution");
            statisticDataUnit.setParentIndex(DATA_SERVICE_INDEX);
            statisticDataUnit.setCurrentIndex(DATA_QUERY_EXECUTION_INDEX);
            statisticDataUnit.setTime(System.currentTimeMillis());
            statisticDataUnit.setCustomProperties(customProperties);
            setPayload(messageContext.getEnvelope(), statisticDataUnit);

            OpenTelemetryManagerHolder.getOpenTelemetryManager().getHandler().
                    handleOpenEntryEvent(statisticDataUnit, messageContext);
        }
    }

    /**
     * Close query execution event for data service invocation.
     *
     * @param messageContext Axis2 message context
     * @param result         Payload OMElement result payload
     */
    public static void closeQueryExecutionEvent(MessageContext messageContext, OMElement result) {
        handleCloseEvents(messageContext, result, DATA_QUERY_EXECUTION_INDEX);
    }

    /**
     * Report entry event for OData data service invocation.
     *
     * @param messageContext Axis2 message context
     */
    public static void reportOdataEntryEvent(MessageContext messageContext) {
        if (isOpenTelemetryEnabled() && messageContext != null) {
            String[] serviceDetails = getOdataServiceDetails(messageContext);
            String componentName = serviceDetails[0];
            String configID = serviceDetails[1];
            Map<String, Object> customProperties = getHTTPProperties(messageContext);
            if (configID != null) {
                customProperties.put(DB_CONFIG_ID, configID);
            }
            setStatisticsTraceId(messageContext);

            StatisticDataUnit statisticDataUnit = new StatisticDataUnit();
            statisticDataUnit.setComponentName(componentName);
            statisticDataUnit.setComponentId("DataService:" + componentName);
            statisticDataUnit.setComponentTypeString("DataService");
            statisticDataUnit.setOuterLayerSpan(true);
            statisticDataUnit.setCurrentIndex(DATA_SERVICE_INDEX);
            statisticDataUnit.setParentIndex(DEFAULT_PARENT_INDEX);
            statisticDataUnit.setTime(System.currentTimeMillis());
            statisticDataUnit.setCustomProperties(customProperties);

            OpenTelemetryManagerHolder.getOpenTelemetryManager().getHandler()
                    .handleOpenEntryEvent(statisticDataUnit, messageContext);
        }
    }

    /**
     * Close entry event for OData data service invocation.
     *
     * @param messageContext Axis2 message context
     * @param result         Payload OMElement result payload
     */
    public static void closeOdataEntryEvent(MessageContext messageContext, OMElement result) {
        handleCloseEvents(messageContext, result, DATA_SERVICE_INDEX);
    }

    /**
     * Report query execution event for OData data service invocation.
     *
     * @param messageContext Axis2 message context
     */
    public static void reportOdataQueryExecutionEvent(MessageContext messageContext) {
        if (isOpenTelemetryEnabled() && messageContext != null) {
            String[] serviceDetails = getOdataServiceDetails(messageContext);
            String componentName = serviceDetails[0];
            String configID = serviceDetails[1];
            Map<String, Object> customProperties = new HashMap<>();
            if (configID != null) {
                customProperties.put(DB_CONFIG_ID, configID);
            }

            StatisticDataUnit statisticDataUnit = new StatisticDataUnit();
            statisticDataUnit.setComponentName(componentName);
            statisticDataUnit.setComponentId("QueryExecution:" + componentName);
            statisticDataUnit.setComponentTypeString("QueryExecution");
            statisticDataUnit.setParentIndex(DATA_SERVICE_INDEX);
            statisticDataUnit.setCurrentIndex(DATA_QUERY_EXECUTION_INDEX);
            statisticDataUnit.setTime(System.currentTimeMillis());
            statisticDataUnit.setCustomProperties(customProperties);

            OpenTelemetryManagerHolder.getOpenTelemetryManager().getHandler().
                    handleOpenEntryEvent(statisticDataUnit, messageContext);
        }
    }

    /**
     * Close query execution event for OData data service invocation.
     *
     * @param messageContext Axis2 message context
     */
    public static void closeOdataQueryExecutionEvent(MessageContext messageContext) {
        handleCloseEvents(messageContext, null, DATA_QUERY_EXECUTION_INDEX);
    }

    /**
     * Report entry event for request box or batch data service invocation.
     *
     * @param messageContext Axis2 message context
     * @param currentIndex   Current index of the request in the request box
     * @param request        Data service request
     */
    public static void reportMultiEvent(MessageContext messageContext, int currentIndex,
                                        DataServiceRequest request) {
        if (isOpenTelemetryEnabled() && messageContext != null) {
            AxisService axisService = messageContext.getAxisService();
            String componentName = axisService.getName();
            Map<String, Object> customProperties = getDataServiceProperties(request);
            Object queryId = customProperties.get(DB_QUERY_ID);
            String componentId = "RequestBox:" + componentName;
            if (queryId != null) {
                componentId = componentId + ":" + queryId;
            }

            StatisticDataUnit statisticDataUnit = new StatisticDataUnit();
            statisticDataUnit.setComponentName(componentName);
            statisticDataUnit.setComponentId(componentId);
            statisticDataUnit.setComponentTypeString("QueryExecution");
            statisticDataUnit.setCurrentIndex(currentIndex + MULTI_REQUEST_BASE_INDEX);
            statisticDataUnit.setParentIndex(DATA_QUERY_EXECUTION_INDEX);
            statisticDataUnit.setTime(System.currentTimeMillis());
            statisticDataUnit.setCustomProperties(customProperties);
            setPayload(messageContext.getEnvelope(), statisticDataUnit);
            OpenTelemetryManagerHolder.getOpenTelemetryManager().getHandler().
                    handleOpenEntryEvent(statisticDataUnit, messageContext);
        }
    }

    /**
     * Close entry event for request box or batch data service invocation.
     *
     * @param messageContext Axis2 message context
     * @param currentIndex   Current index of the request in the request box
     * @param result         Payload OMElement result payload
     */
    public static void closeMultiEvent(MessageContext messageContext, int currentIndex, OMElement result) {
        handleCloseEvents(messageContext, result, currentIndex + MULTI_REQUEST_BASE_INDEX);
    }

    /**
     * Close flow forcefully event for data service invocation.
     *
     * @param messageContext Axis2 message context
     * @param currentIndex   Current index of the request in the request box
     * @param e              Exception occurred
     */
    public static void closeFlowForcefully(MessageContext messageContext, int currentIndex, Exception e) {
        if (isOpenTelemetryEnabled() && messageContext != null) {

            // Adjust current index for multi requests (request box or batch requests)
            Object lastIndexObj = messageContext.getProperty(MULTI_REQUEST_LAST_INDEX_PROPERTY);
            if (lastIndexObj instanceof Integer) {
                currentIndex = (Integer) lastIndexObj + MULTI_REQUEST_BASE_INDEX;
            }

            StatisticDataUnit dataUnit = new StatisticDataUnit();
            dataUnit.setTime(System.currentTimeMillis());
            dataUnit.setCurrentIndex(currentIndex);
            if (e instanceof DataServiceFault) {
                DataServiceFault dataServiceFault = (DataServiceFault) e;
                dataUnit.setErrorCode(dataServiceFault.getCode());
                dataUnit.setErrorMessage(dataServiceFault.getMessage());
            } else {
                dataUnit.setErrorCode(DBConstants.FaultCodes.UNKNOWN_ERROR);
                dataUnit.setErrorMessage(e.getMessage());
            }

            OpenTelemetryManagerHolder.getOpenTelemetryManager().getHandler()
                    .handleCloseFlowForcefully(dataUnit, messageContext);
        }
    }

    private static void handleCloseEvents(MessageContext messageContext, OMElement result, int dataServiceIndex) {
        if (isOpenTelemetryEnabled() && messageContext != null) {
            StatisticDataUnit statisticDataUnit = new StatisticDataUnit();
            statisticDataUnit.setCurrentIndex(dataServiceIndex);
            statisticDataUnit.setTime(System.currentTimeMillis());
            setPayload(result, statisticDataUnit);

            OpenTelemetryManagerHolder.getOpenTelemetryManager().getHandler().
                    handleCloseEntryEvent(statisticDataUnit, messageContext);
        }
    }

    private static String[] getOdataServiceDetails(MessageContext messageContext) {
        String odataServiceName;
        String odataServiceUri;
        String configID;
        Object transportInURL = messageContext.getProperty(TRANSPORT_IN_URL);
        String uri = transportInURL != null ? transportInURL.toString() : "";
        int index = uri.indexOf(ODATA_SERVICE);
        if (-1 != index) {
            int serviceStart = index + ODATA_SERVICE.length();
            if (uri.length() > serviceStart + 1) {
                odataServiceUri = uri.substring(serviceStart);
                if (-1 != odataServiceUri.indexOf(URL_SEPARATOR_CHAR)) {
                    String[] params = odataServiceUri.split(URL_SEPARATOR);
                    if (params.length >= 2) {
                        odataServiceName = params[0];
                        configID = params[1];
                        return new String[] {odataServiceName, configID};
                    } else if (params.length == 1) {
                        return new String[] {params[0], null};
                    }
                }
            }
        }
        return new String[] {DEFAULT_ODATA_SERVICE_NAME, null};
    }

    private static Map<String, Object> getHTTPProperties(MessageContext messageContext) {
        String method = (String) messageContext.getProperty(HTTP_METHOD_OBJECT);
        String address = messageContext.getTo() != null ? messageContext.getTo().getAddress() : null;
        Map<String, Object> customProperties = new HashMap<>();
        if (method != null) {
            customProperties.put(HTTP_METHOD, method);
        }
        if (address != null) {
            customProperties.put(HTTP_URL, address);
        }
        return customProperties;
    }

    private static Map<String, Object> getDataServiceProperties(DataServiceRequest request) {
        DataService dataService = request.getDataService();
        String requestName = request.getRequestName();
        Map<String, Object> customProperties = new HashMap<>();
        CallableRequest callableRequest = dataService.getCallableRequest(requestName);
        if (callableRequest == null) {
            return customProperties;
        }

        CallQuery callQuery = callableRequest.getCallQuery();
        if (callQuery == null) {
            return customProperties;
        }

        Query query = callQuery.getQuery();
        if (query == null) {
            return customProperties;
        }

        String queryId = query.getQueryId();
        String configId = query.getConfigId();
        if (queryId != null) {
            customProperties.put(DB_QUERY_ID, queryId);
        }
        if (configId != null) {
            customProperties.put(DB_CONFIG_ID, configId);
        }
        return customProperties;
    }

    private static void setPayload(OMElement payload, StatisticDataUnit dataUnit) {
        try {
            if (payload != null) {
                dataUnit.setPayload(payload.toString());
            }
        } catch (Exception e) {
            // We are catching exception to avoid any issues with toString() and
            // We should not fail the main flow due to tracing issues
            log.error("Error while setting payload for tracing span", e);
            dataUnit.setPayload("Bad Payload");
            dataUnit.setErrorMessage("Error while setting payload for tracing span");
            dataUnit.setErrorCode(DBConstants.FaultCodes.UNKNOWN_ERROR);
        }
    }
}
