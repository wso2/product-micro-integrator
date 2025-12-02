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

import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.llom.OMSourcedElementImpl;
import org.apache.axis2.context.MessageContext;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.XSLTTransformer;
import org.wso2.micro.integrator.dataservices.core.description.event.EventTrigger;
import org.wso2.micro.integrator.dataservices.core.description.query.Query;
import org.wso2.micro.integrator.dataservices.core.engine.DSOMDataSource;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;
import org.wso2.micro.integrator.dataservices.core.engine.Result;

/**
 * Represents a single data service request.
 */
public class SingleDataServiceRequest extends DataServiceRequest {
		
	/**
	 * Request parameters
	 */
	private Map<String, ParamValue> params;
	
	public SingleDataServiceRequest(DataService dataService, String requestName,
                                    Map<String, ParamValue> params) throws DataServiceFault {
		super(dataService, requestName);
		this.params = params;
	}
	
	public Map<String, ParamValue> getParams() {
		return params;
	}
	
	/**
     * @see DataServiceRequest#processRequest(MessageContext messageContext)
	 */
	@Override
    public OMElement processRequest(MessageContext messageContext) throws DataServiceFault {
		try {
            Query.resetQueryPreprocessing();
            Query.setQueryPreprocessingInitial(true);
            Query.setQueryPreprocessingSecondary(false);
			OMElement result = processSingleRequest();
			if (result instanceof OMSourcedElementImpl) {
                /* first pass for preprocessing */
                DSOMDataSource dsomDS = (DSOMDataSource) ((OMSourcedElementImpl) result).getDataSource();
                Query.setQueryPreprocessingSecondary(false);
                try {
                    dsomDS.execute(null);
                } catch (XMLStreamException e) {
                    throw new DataServiceFault(e);
                }
                Query.setQueryPreprocessingInitial(false);
                Query.setQueryPreprocessingSecondary(true);
                Query defQuery = this.getDataService().getCallableRequest(
                        this.getRequestName()).getCallQuery().getQuery();
                /*
                * Checks if the result has to be pre-built, because in situations like having an
                * output-event-trigger, for XPath expression evaluations, the following operation
                * must be done, or it wont work.
                */
                if (defQuery.isPreBuildResult()) {
                    result = DBUtils.cloneAndReturnBuiltElement(result);
                }

                /* do XSLT transformation if available */
                result = this.executeXsltTranformation(result, defQuery);

                /* process events */
                this.processOutputEvents(result, defQuery);
            }
			return result;
		} catch (DataServiceFault e) {
			throw e;
		}
	}
	
	private OMElement processSingleRequest() throws DataServiceFault {
		DataService dataService = this.getDataService();
		String requestName = this.getRequestName();
		/* set the operation name to invoke and the parameters */
		DSOMDataSource ds = new DSOMDataSource(dataService, requestName, this.getParams());

		/* check if the current request has a result, if so, return the OMElement */
		if (dataService.hasResultForRequest(this.getRequestName())) {
			String resultWrapper = dataService.getResultWrapperForRequest(requestName);
			String ns = dataService.getNamespaceForRequest(requestName);
			return new OMSourcedElementImpl(new QName(ns,
					resultWrapper), DBUtils.getOMFactory(), ds);
		} else { /* if no response i.e. in-only, execute the request now */
			try {
				ds.executeInOnly();
			} catch (XMLStreamException e) {
				throw new DataServiceFault(e, "Error in DS non result invoke.");
			}
			return null;
		}
	}

	private OMElement executeXsltTranformation(OMElement input, Query query)
            throws DataServiceFault {
		Result result = query.getResult();
		XSLTTransformer transformer = result.getXsltTransformer();
		if (transformer == null) {
			return input;
		} else {
			try {
				return transformer.transform(input);
			} catch (Exception e) {
				throw new DataServiceFault(e,
                                           "Error in result XSLT transformation");
			}
		}
	}
	
	private void processOutputEvents(OMElement input, Query query)
            throws DataServiceFault {
		EventTrigger trigger = query.getOutputEventTrigger();
		/* if output event trigger is available, execute it */
		if (trigger != null) {
			trigger.execute(input, query.getQueryId());
		}
	}

}

