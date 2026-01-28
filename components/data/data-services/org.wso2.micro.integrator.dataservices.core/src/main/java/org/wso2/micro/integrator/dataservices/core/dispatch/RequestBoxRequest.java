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
package org.wso2.micro.integrator.dataservices.core.dispatch;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.context.MessageContext;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.TLConnectionStore;
import org.wso2.micro.integrator.dataservices.core.boxcarring.RequestBox;
import org.wso2.micro.integrator.dataservices.core.boxcarring.TLParamStore;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingCollector;

/**
 * Request box data service request for request grouping.
 */
public class RequestBoxRequest extends DataServiceRequest {

	/* Request box to hold multiple requests */
    private RequestBox requestBox;

    /**
     * Constructor
     * @param dataService with request
     * @param requestName of the parent request
     * @throws DataServiceFault
     */
    public RequestBoxRequest(DataService dataService, String requestName)
            throws DataServiceFault {
        super(dataService, requestName);
        requestBox = new RequestBox();
    }

    /**
     * Method to add requests to the request box.
     *
     * @param request to be added
     */
    public void addRequests(DataServiceRequest request) {
        requestBox.addRequest(request);
    }

	/**
     * @see DataServiceRequest#processRequest(MessageContext messageContext)
     */
    @Override
    public OMElement processRequest(MessageContext messageContext) throws DataServiceFault {

        boolean error = true;
        try {
            DispatchStatus.setBoxcarringRequest();
            if (!this.getDataService().isInDTX()) {
                this.getDataService().getDSSTxManager().begin();
            }
            OMElement lastRequestResult = this.requestBox.execute(messageContext);
            error = false;
            return lastRequestResult;
        } finally {
            this.finalizeTx(error);
            TLParamStore.clear();
        }

    }

    /**
     * Helper method to finish the transaction.
     *
     * @param error whether operation successful or not.
     * @throws DataServiceFault
     */
    private void finalizeTx(boolean error) throws DataServiceFault {
        if (error) {
            if (this.getDataService().isInDTX()) {
                TLConnectionStore.rollbackNonXAConns();
                TLConnectionStore.closeAll();
                if (this.getDataService().getDSSTxManager().isDTXInitiatedByUS()) {
                    this.getDataService().getDSSTxManager().rollback();
                }
            } else {
                TLConnectionStore.rollbackAll();
                TLConnectionStore.closeAll();
            }
        } else {
            if (this.getDataService().isInDTX()) {
                TLConnectionStore.commitNonXAConns();
            } else {
                TLConnectionStore.commitAll();
            }
            TLConnectionStore.closeAll();
            if (this.getDataService().getDSSTxManager().isDTXInitiatedByUS()) {
                this.getDataService().getDSSTxManager().commit();
            }
        }
    }
}

