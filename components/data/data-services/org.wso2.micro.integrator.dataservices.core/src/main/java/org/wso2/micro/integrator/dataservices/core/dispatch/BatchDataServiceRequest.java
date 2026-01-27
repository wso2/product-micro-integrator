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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.context.MessageContext;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.TLConnectionStore;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;
import org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingCollector;

import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.MULTI_REQUEST_LAST_INDEX_PROPERTY;

/**
 * Represents a batch data service request.
 */
public class BatchDataServiceRequest extends DataServiceRequest {

	/**
	 * The data service request list which belongs to this batch request
	 */
	private List<SingleDataServiceRequest> dsRequests;

	/**
	 * This is used to keep the dependent entities, whose cleanup methods must be called after a batch
	 * request is done.
	 */
	private static ThreadLocal<List<BatchRequestParticipant>> batchRequestParticipant = new ThreadLocal<List<BatchRequestParticipant>>() {
		protected synchronized List<BatchRequestParticipant> initialValue() {
			return new ArrayList<BatchRequestParticipant>();
		}
	};

	public BatchDataServiceRequest(DataService dataService, String requestName,
                                   List<Map<String, ParamValue>> batchParams) throws DataServiceFault {
		super(dataService, requestName);
		this.dsRequests = new ArrayList<SingleDataServiceRequest>();
		/* create the requests */
		for (Map<String, ParamValue> params : batchParams) {
			this.dsRequests.add(new SingleDataServiceRequest(dataService, requestName, params));
		}
	}

	public static void addParticipant(BatchRequestParticipant participant) {
		batchRequestParticipant.get().add(participant);
	}

	private static List<BatchRequestParticipant> getParticipants() {
		return batchRequestParticipant.get();
	}

	private static void releaseParticipantResources() {
		List<BatchRequestParticipant> finList = getParticipants();
		for (BatchRequestParticipant fin : finList) {
			fin.releaseBatchRequestResources();
		}
	}
	
	private static void clearParticipants() {
		getParticipants().clear();
	}

	private static void clearStatus() {
		DispatchStatus.clearBatchRequestStatus();
	}

	public List<SingleDataServiceRequest> getDSRequests() {
		return dsRequests;
	}

	/**
     * @see DataServiceRequest#processRequest(MessageContext messageContext)
	 */
	@Override
    public OMElement processRequest(MessageContext messageContext) throws DataServiceFault {
		boolean error = true;
		try {
			/* signal that we are batch processing */
			DispatchStatus.setBatchRequest();
			List<SingleDataServiceRequest> requests = this.getDSRequests();
			int count = requests.size();
			/* set the batch request count in TL */
			DispatchStatus.setBatchRequestCount(count);
			/* dispatch individual requests */
			OMElement result = null;
			for (int i = 0; i < count; i++) {
				/* set the current batch request number in TL */
			    DispatchStatus.setBatchRequestNumber(i);
				/* execute/enqueue request */
                DataServicesTracingCollector.reportMultiEvent(messageContext, i, requests.get(i));
                if (messageContext != null){
                    messageContext.setProperty(MULTI_REQUEST_LAST_INDEX_PROPERTY, i);
                }
                OMElement element = requests.get(i).dispatch(messageContext);
                DataServicesTracingCollector.closeMultiEvent(messageContext, i, element);
				if (element != null && element.getFirstOMChild() != null) {
					result = element;
				}
			}
			/* signal that there aren't any errors */
			error = false;
			/* no result in batch requests */
			return result;
		} finally {
		    /* finalize transactions */
            this.finalizeTx(error);
			/* release participants */
			releaseParticipantResources();
			clearParticipants();
			clearStatus();
		}
	}
	
	private void finalizeTx(boolean error) {
	    if (DispatchStatus.isBoxcarringRequest()) {
	        return;
	    }
	    if (error) {
            if (this.getDataService().isInDTX()) {
                TLConnectionStore.rollbackNonXAConns();
                TLConnectionStore.closeAll();
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
        }
	}

}
