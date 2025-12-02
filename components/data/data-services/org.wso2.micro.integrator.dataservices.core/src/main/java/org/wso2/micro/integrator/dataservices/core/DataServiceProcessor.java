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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axis2.context.MessageContext;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.core.dispatch.DataServiceRequest;
import org.wso2.micro.integrator.dataservices.core.dispatch.DispatchStatus;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingCollector;

import javax.xml.namespace.QName;

/**
 * Processes and dispatches data service requests.
 */
public class DataServiceProcessor {

	public static OMElement dispatch(MessageContext msgContext) throws DataServiceFault {
	    DispatchStatus.clearRequestStatus();
		DataServiceRequest request = DataServiceRequest.createDataServiceRequest(msgContext);
        DataServicesTracingCollector.reportQueryExecutionEvent(msgContext, request);
        OMElement result = request.dispatch(msgContext);
		if (result == null) {
			DataService ds = request.getDataService();
			String requestName = request.getRequestName();			
			if (!ds.hasResultForRequest(requestName) && ds.isReturningRequestStatus(requestName)) {
				/* in-only and returning the request status */
				result = generateRequestSuccessElement();
			}			
		}
        DataServicesTracingCollector.closeQueryExecutionEvent(msgContext, result);
		return result;
	}
	
	private static OMElement generateRequestSuccessElement() {
		OMFactory fac = OMAbstractFactory.getOMFactory();
		OMElement result = fac.createOMElement(new QName(DBConstants.WSO2_DS_NAMESPACE,
                DBConstants.REQUEST_STATUS_WRAPPER_ELEMENT));
		result.setText(DBConstants.REQUEST_STATUS_SUCCESSFUL_MESSAGE);
		OMDocument doc = fac.createOMDocument();
		doc.addChild(result);
		return doc.getOMDocumentElement();
	}

}
