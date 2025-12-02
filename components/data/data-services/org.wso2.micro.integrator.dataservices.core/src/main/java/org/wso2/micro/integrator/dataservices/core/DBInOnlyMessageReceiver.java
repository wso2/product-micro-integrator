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
package org.wso2.micro.integrator.dataservices.core;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.receivers.RawXMLINOnlyMessageReceiver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingCollector;

import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DATA_SERVICE_INDEX;

/**
 * This class represents the Axis2 message receiver used to dispatch in-only service calls.
 */
public class DBInOnlyMessageReceiver extends RawXMLINOnlyMessageReceiver {

	private static final Log log = LogFactory.getLog(DBInOnlyMessageReceiver.class);

	/**
	 * Invokes the business logic invocation on the service implementation
	 * class
	 *
	 * @param msgContext
	 *            the incoming message context
	 * @throws AxisFault
	 *             on invalid method (wrong signature) or behavior (return
	 *             null)
	 */
    public void invokeBusinessLogic(MessageContext msgContext) throws AxisFault {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Request received to DSS:  Data Service - " + msgContext.getServiceContext().getName() +
                        ", Operation - " + msgContext.getSoapAction() + ", Request body - " +
                        msgContext.getEnvelope().getText() + ", ThreadID - " + Thread.currentThread().getId());
            }
            DataServicesTracingCollector.reportEntryEvent(msgContext);
            DataServiceProcessor.dispatch(msgContext);
            msgContext.setProperty(DBConstants.TENANT_IN_ONLY_MESSAGE, Boolean.TRUE);
            DataServicesTracingCollector.closeEntryEvent(msgContext, null);
        } catch (Exception e) {
            log.error("Error in in-only message receiver", e);
            msgContext.setProperty(Constants.FAULT_NAME, DBConstants.DS_FAULT_NAME);
            DataServicesTracingCollector.closeFlowForcefully(msgContext, DATA_SERVICE_INDEX, e);
            throw DBUtils.createAxisFault(e);
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("Request processing completed from DSS: Data Service - " +
                        msgContext.getServiceContext().getName() + ", Operation - " + msgContext.getSoapAction() +
                        ", ThreadID - " + Thread.currentThread().getId());
            }
        }
    }

}
