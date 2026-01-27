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
import org.apache.axiom.om.impl.llom.OMSourcedElementImpl;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.wso2.micro.core.util.StringUtils;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.common.DBConstants.BoxcarringOps;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.DSSessionManager;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.DataServiceUser;
import org.wso2.micro.integrator.dataservices.core.engine.CallableRequest;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents a data services request.
 */
public abstract class DataServiceRequest {

	/**
	 * Used to hold the axis2 operation name of the current dataservice operation.
	 */
	public static final String AXIS_OPERATION_NAME = "axisOperationName";
	/**
	 * contains the username of the user who made the current message request 
	 */
	private String user;
	
	/** 
	 * contains the current user's user roles 
	 */
	private String[] userRoles;
	
	/**
	 * The data service object of the request that will be run against 
	 */
	private DataService dataService;
	
	/**
	 * The data service request name
	 */
	private String requestName;
	
	/**
	 * Disable streaming flag
	 */
	private boolean disableStreaming;
	
	protected DataServiceRequest(DataService dataService, String requestName)
            throws DataServiceFault {
		this.dataService = dataService;
		this.requestName = requestName;
		CallableRequest request = this.dataService.getCallableRequest(this.requestName);
		if (request == null) {
			throw new DataServiceFault("A data service request named '" + requestName +
					"' does not exist in data service '" + dataService.getName() + "'");
		}
		this.disableStreaming = this.dataService.getCallableRequest(
				this.requestName).isDisableStreamingEffective();
	}
	
	public static DataServiceRequest createDataServiceRequest(
			MessageContext msgContext) throws DataServiceFault {
		AxisService axisService = msgContext.getAxisService();
		AxisOperation axisOp = msgContext.getAxisOperation();
		OMElement inputMessage = msgContext.getEnvelope().getBody().getFirstElement();
		// Fetching the operation name from the property
		// since axisOp.getName() provide different results in a load test scenario.
		String requestName = (String) msgContext.getProperty(AXIS_OPERATION_NAME);
		/* get operation/request name */
		if (StringUtils.isEmpty(requestName)) {
			requestName = axisOp.getName().getLocalPart();
		}
		if (Boolean.parseBoolean(System.getProperty("dss.force.xml.validation"))) {
			if (inputMessage != null && !requestName.equals(inputMessage.getLocalName())) {
				throw new DataServiceFault("Input Message and " + requestName + " Axis Operation didn't match.");
			}
		}
		/* retrieve the DataService object representing the current data service */
		DataService dataService = (DataService) axisService.getParameter(
				DBConstants.DATA_SERVICE_OBJECT).getValue();
		
		DataServiceRequest dsRequest;
        /* Check whether the request is collection of requests (request box), if so create RequestBoxRequest */
        if (isRequestBoxRequest(requestName)) {
            dsRequest = createRequestBoxRequest(dataService, requestName, inputMessage);
            return dsRequest;
        }
		/* check if batch or single request */
		if (isBatchRequest(inputMessage)) {
			dsRequest = new BatchDataServiceRequest(
					dataService, requestName, getBatchInputValuesFromOM(inputMessage));
		} else {
			dsRequest = new SingleDataServiceRequest(
					dataService, requestName, getSingleInputValuesFromOM(inputMessage)); 
		}
		
		/* set user information */
		populateUserInfo(dataService, dsRequest, msgContext);
		
		/* checks if this is a boxcarring session */
		if (isBoxcarringRequest(requestName)) {
			/* wrap the current request in a boxcarring request */
			dsRequest = new BoxcarringDataServiceRequest(dsRequest);
		}

		return dsRequest;
	}
	
	public DataService getDataService() {
		return dataService;
	}
	
	public String getRequestName() {
		return requestName;
	}
	
	public boolean isDisableStreaming() {
		return disableStreaming;
	}

	/**
	 * Set the current session user's name, user roles etc..
	 * @param dataService data service
	 * @param dsRequest Current data service request
	 * @param msgContext Incoming message's message context
	 * @throws DataServiceFault
	 */
	private static void populateUserInfo(DataService dataService, DataServiceRequest dsRequest,
                                         MessageContext msgContext) throws DataServiceFault {
		/* set request username */
		String username = dataService.getAuthorizationProvider().getUsername(msgContext);
		dsRequest.setUser(username);
		/* if only there's a user .. */
		if (username != null) {
			/* set user roles */
			try {
				dsRequest.setUserRoles(dataService.getAuthorizationProvider().getUserRoles(msgContext));
			} catch (Exception e) {
				throw new DataServiceFault(e, "Error setting user roles");
			}
		}
	}
	
    /**
     * Checks if the given message is a batch request or not,
     * a batch request contains service parameters in separate element todo need to check better approach than this(check element name which ends with batchreq)
     */
    private static boolean isBatchRequest(OMElement inputMessage) {
		if (inputMessage != null) {
			OMElement el = inputMessage.getFirstElement();
			if (el != null) {
				if (el.getChildElements().hasNext()) {
					return true;
				}
			}
		}
    	return false;
    }

//    private static boolean isBatchRequest(OMElement inputMessage) {
//        if (inputMessage != null) {
//            String elName = inputMessage.getLocalName();
//            if (elName.endsWith(DBConstants.BATCH_OPERATON_NAME_SUFFIX)) {
//                return true;
//            }
//        }
//        return false;
//    }
    
    /**
     * Checks if the given request is a boxcarring request.
     * @param requestName The request name
     * @return True if this is a boxcarring request.
     */
    public static boolean isBoxcarringRequest(String requestName) {
    	if (BoxcarringOps.BEGIN_BOXCAR.equals(requestName)) {
    		return true;
    	}
    	if (BoxcarringOps.END_BOXCAR.equals(requestName)) {
    		return true;
    	}
    	if (BoxcarringOps.ABORT_BOXCAR.equals(requestName)) {
    		return true;
    	}
    	if (DSSessionManager.isBoxcarring()) {
    		return true;
    	}
    	return false;
    }

    /**
     * Helper method to determine whether the request is collection of requests.
     *
     * @param requestName name of the parent request
     * @return true if this is a request containing multiple requests
     */
    private static boolean isRequestBoxRequest(String requestName) {
        if (requestName.endsWith(DBConstants.REQUEST_BOX_ELEMENT)) {
            return true;
        }
        return false;
    }


    /**
     * Extracts the data service single request parameters from the incoming message.
     */
    @SuppressWarnings("unchecked")
	private static Map<String, ParamValue> getSingleInputValuesFromOM(OMElement inputMessage) {
    	/* in the input is null, return an empty list of params */
    	if (inputMessage == null) {
    		return new HashMap<String, ParamValue>();
    	}
		Map<String, ParamValue> inputs = new HashMap<String, ParamValue>();
		Map<String, List<OMElement>> inputMap = new HashMap<String, List<OMElement>>();
		Iterator<OMElement> iter = inputMessage.getChildElements();
		String name;
		List<OMElement> omElList;
		ParamValue paramValue;
		while (iter.hasNext()) {
            OMElement element = iter.next();
            name = element.getLocalName();
            if (!inputMap.containsKey(name)) {
            	inputMap.put(name, new ArrayList<OMElement>());
            }
            omElList = inputMap.get(name);
            omElList.add(element);
        }
		for (String key : inputMap.keySet()) {
			omElList = inputMap.get(key);
			if (omElList.size() == 1) { /* scalar */
				paramValue = new ParamValue(getTextValueFromOMElement(omElList.get(0)));
			} else { /* array */
				paramValue = new ParamValue(ParamValue.PARAM_VALUE_ARRAY);
				for (OMElement omEl : omElList) {
					paramValue.addToArrayValue(new ParamValue(getTextValueFromOMElement(omEl)));
				}
			}
			inputs.put(key, paramValue);
		}
        return inputs;
	}
    
    /**
     * Extracts the data service batch request parameters from the incoming message. 
     */
    @SuppressWarnings("unchecked")
	private static List<Map<String, ParamValue>> getBatchInputValuesFromOM(
			OMElement inputMessage) {
    	List<Map<String, ParamValue>> batchParams = new ArrayList<Map<String, ParamValue>>();
    	Iterator<OMElement> paramItr = inputMessage.getChildElements();
    	OMElement paramEl;
    	while (paramItr.hasNext()) {
    		paramEl = paramItr.next();
    		batchParams.add(getSingleInputValuesFromOM(paramEl));
    	}
    	return batchParams;
    }

    /**
     * Helper method to generate the request box request using the incoming message.
     *
     * @param dataService to be used
     * @param requestName of the original parent request
     * @param inputMessage with other requests
     * @return dsRequest which was generated
     * @throws DataServiceFault
     */
    @SuppressWarnings("unchecked")
    private static DataServiceRequest createRequestBoxRequest(DataService dataService, String requestName,
                                                              OMElement inputMessage)
            throws DataServiceFault {
        RequestBoxRequest dsRequest = new RequestBoxRequest(dataService, requestName);

	    if (inputMessage == null) {
		    throw new DataServiceFault("Input message is null for REQUEST_BOX request");
	    }

        Iterator<OMElement> paramItr = inputMessage.getChildElements();
        OMElement paramEl;
        while (paramItr.hasNext()) {
            DataServiceRequest childRequest;
            paramEl = paramItr.next();
            if (isBatchRequest(paramEl)) {
                childRequest = new BatchDataServiceRequest(
                        dataService, paramEl.getLocalName(), getBatchInputValuesFromOM(paramEl));
            } else {
                childRequest = new SingleDataServiceRequest(
                        dataService, paramEl.getLocalName(), getSingleInputValuesFromOM(paramEl));
            }
            dsRequest.addRequests(childRequest);
        }
        return dsRequest;

    }
    
    private static String getTextValueFromOMElement(OMElement omEl) {
    	String nillValue = omEl.getAttributeValue(
    			new QName(DBConstants.XSI_NAMESPACE, DBConstants.NIL));
    	if (nillValue != null && (nillValue.equals("1") || nillValue.equals("true"))) {
    		return null;
    	} else {
    		return omEl.getText();
    	}
    }
    
    public void setUser(String requestUser) {
    	this.user = requestUser;
    }
    
    public String getUser() {
    	return user;
    }
    
    public void setUserRoles(String[] userRoles) {
    	this.userRoles = userRoles;
    }
    
    public String[] getUserRoles() {
    	return userRoles;
    }
    
	/**
	 * Dispatches the current request. This method does common dispatching logic and call the 
     * request type specific {@link DataServiceRequest}{@link #processRequest(MessageContext messageContext)} method.
	 * @return The result of the request invocation
	 * @throws DataServiceFault
	 */
    public OMElement dispatch(MessageContext messageContext) throws DataServiceFault {
		/* set user */
		if (this.getUserRoles() != null) {
			DataServiceUser currentUser = new DataServiceUser(this.getUser(),
                                                              new HashSet<String>(Arrays.asList(this.getUserRoles())));
			DataService.setCurrentUser(currentUser);
		}		
		
		/* request specific processing */
        OMElement result = this.processRequest(messageContext);
		/* check disable streaming */
		if (this.isDisableStreaming()) {
			/* if result is of type OMSourcedElementImpl, that means,
			 * it is still in streaming mode, result.isComplete does not work */
			if (result instanceof OMSourcedElementImpl) {
				result = DBUtils.cloneAndReturnBuiltElement(result);
			}
		}
		return result;
	}

    /**
     * Dispatches the current request.
     *
     * @return The result of the request invocation
     * @throws DataServiceFault
     */
    public OMElement dispatch() throws DataServiceFault {
        return dispatch(null);
    }
	
	/**
	 * This method must implement the request specific request processing logic.
     * @param messageContext Axis2 message context of the request
	 * @return The result of the request invocation
	 * @throws DataServiceFault
     */
    public abstract OMElement processRequest(MessageContext messageContext) throws DataServiceFault;
}
