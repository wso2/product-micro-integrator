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

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axis2.context.MessageContext;
import org.wso2.micro.integrator.dataservices.common.DBConstants.BoxcarringOps;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.DSSessionManager;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.TLConnectionStore;
import org.wso2.micro.integrator.dataservices.core.boxcarring.TLParamStore;

/**
 * Boxcarring data service request for service call grouping.
 */
public class BoxcarringDataServiceRequest extends DataServiceRequest {

	/**
	 * The data service request which is inside the boxcarring session
	 */
	private DataServiceRequest dsRequest;

	public BoxcarringDataServiceRequest(DataServiceRequest dsRequest) throws DataServiceFault {
		super(dsRequest.getDataService(), dsRequest.getRequestName());
		this.dsRequest = dsRequest;
	}

	public DataServiceRequest getDSRequest() {
		return dsRequest;
	}
	
	/**
     * @see DataServiceRequest#processRequest(MessageContext messageContext)
	 */
	@Override
    public OMElement processRequest(MessageContext messageContext) throws DataServiceFault {
		if (BoxcarringOps.BEGIN_BOXCAR.equals(this.getRequestName())) {
			/* clear earlier boxcarring sessions */
			DSSessionManager.getCurrentRequestBox().clear();
			/* set the status to boxcarring */
			DSSessionManager.setBoxcarring(true);
		} else if (BoxcarringOps.END_BOXCAR.equals(this.getRequestName())) {
			/* execute all the stored requests */
		    boolean error = true;
			try {
				DispatchStatus.setBoxcarringRequest();
				if (!this.getDataService().isInDTX()) {
				    this.getDataService().getDSSTxManager().begin();
				}
                OMElement lastRequestResult = DSSessionManager.getCurrentRequestBox().execute(messageContext);
			    error = false;
			    return lastRequestResult;
			} finally {
			    this.finalizeTx(error);
				DSSessionManager.getCurrentRequestBox().clear();
				DSSessionManager.setBoxcarring(false);
				TLParamStore.clear();
			}			
		} else if (BoxcarringOps.ABORT_BOXCAR.equals(this.getRequestName())) {
			DSSessionManager.getCurrentRequestBox().clear();
			DSSessionManager.setBoxcarring(false);
			this.finalizeTx(true);
		} else {
			DSSessionManager.getCurrentRequestBox().addRequest(this.getDSRequest());
			/* return an empty wrapper element result for each out/in-out boxcarring request,
			 * so the caller will get a valid result for out operations */
			return this.createBoxcarringRequestResultWrapper();
		}
		return null;
	}
	
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
	
	private OMElement createBoxcarringRequestResultWrapper() {
		String resultWrapper = this.getDataService().getResultWrapperForRequest(
				this.getRequestName());
		if (resultWrapper == null) {
			/* in-only request */
			return null;
		}
		String ns = this.getDataService().getNamespaceForRequest(this.getRequestName());
		OMFactory fac = DBUtils.getOMFactory();
		OMElement ele = fac.createOMElement(new QName(ns, resultWrapper));
		return ele;
	}
	
}

