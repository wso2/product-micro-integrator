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

import org.apache.axis2.description.AxisMessage;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.java2wsdl.Java2WSDLConstants;
import org.apache.axis2.description.java2wsdl.TypeTable;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.ws.commons.schema.*;
import org.apache.ws.commons.schema.constants.Constants;
import org.apache.ws.commons.schema.utils.NamespaceMap;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.common.DBConstants.ResultTypes;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery.WithParam;
import org.wso2.micro.integrator.dataservices.core.description.operation.Operation;
import org.wso2.micro.integrator.dataservices.core.description.query.Query;
import org.wso2.micro.integrator.dataservices.core.description.query.SQLQuery;
import org.wso2.micro.integrator.dataservices.core.description.resource.Resource;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery;
import org.wso2.micro.integrator.dataservices.core.engine.CallableRequest;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.OutputElement;
import org.wso2.micro.integrator.dataservices.core.engine.OutputElementGroup;
import org.wso2.micro.integrator.dataservices.core.engine.QueryParam;
import org.wso2.micro.integrator.dataservices.core.engine.Result;
import org.wso2.micro.integrator.dataservices.core.engine.StaticOutputElement;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.Map.Entry;

/**
 * Used to generate XML Schema for a Data Service.
 */
public class DataServiceDocLitWrappedSchemaGenerator {
	
	private static final String DUMMY_NAME = "__dummy__";

	/**
	 * Populates the given axis service schema with the data service information it encapsulates.
	 * @param axisService The target service
	 * @throws DataServiceFault
	 */
	public static void populateServiceSchema(AxisService axisService) throws DataServiceFault {
		DataService dataservice = (DataService) axisService.getParameter(
				DBConstants.DATA_SERVICE_OBJECT).getValue();
		CommonParams cparams = new CommonParams(axisService, dataservice);
		
		List<List<Operation>> allOps = extractOperations(dataservice);
		List<Operation> normalOperations = allOps.get(0);
		List<Operation> batchOperations = allOps.get(1);
		List<List<Resource>> allResources = extractResources(dataservice);
		List<Resource> normalResources = allResources.get(0);
        List<Resource> batchResources = allResources.get(1);
		
		/* create the fault element */
		createAndStoreFaultElement(cparams);
		
		/* create the request status element */
		createAndStoreRequestStatusElement(cparams);
		
		/* Creates the default data services response element */
		createAndStoreDataServiceResponseElement(cparams);
		
		/* process normal operations */
		for (Operation normalOp : normalOperations) {
			processRequest(cparams, normalOp);
		}
		/* process batch operations */
		for (Operation batchOp : batchOperations) {
			processRequest(cparams, batchOp);
		}
		/* process normal resources */
		for (Resource resource : normalResources) {
			processRequest(cparams, resource);
		}
		/* process batch resources */
        for (Resource resource : batchResources) {
            processRequest(cparams, resource);
        }
        /* process requestBox operation (Only have one element) */
        if (dataservice.isBoxcarringEnabled()) {
            Operation requestBoxOperation = dataservice.getOperation(DBConstants.REQUEST_BOX_ELEMENT);
            if (requestBoxOperation != null) {
	            List<List<CallableRequest>> callableRequests = new ArrayList<>();
	            for(List<CallableRequest> calls : (List<List<CallableRequest>>)(List<?>) allOps) {
		            callableRequests.add(calls);
	            }
	            for(List<CallableRequest> calls : (List<List<CallableRequest>>)(List<?>)allResources) {
		            callableRequests.add(calls);
	            }
                processRequestBox(cparams, requestBoxOperation, callableRequests);
            }
        }
				
		/* set the schema */
		axisService.addSchema(cparams.getSchemaMap().values());
	}
	
	/**
	 * Process a single request, i.e. operation/resource.
	 * @param cparams The common parameters used in the schema generator
	 * @param request The request to be processed
	 */
	private static void processRequest(CommonParams cparams, CallableRequest request)
            throws DataServiceFault {
		/* process input parameters */
		processRequestInput(cparams, request);
		/* process output types */
		processRequestOutput(cparams, request);
	}

    /**
     * Process RequestBox request //todo complete
     * @param cparams The common parameters used in the schema generator
     * @param request The request to be processed
     */
    private static void processRequestBox(CommonParams cparams, CallableRequest request, List<List<CallableRequest>> allOps)
            throws DataServiceFault {
		/* process input parameters */
        processRequestBoxInput(cparams, request, allOps);
		/* process output types */
        processRequestBoxOutput(cparams, request, allOps);
    }


    /**
     * Process the given request's input parameters. //todo complete
     * @param cparams The common parameters used in the schema generator
     * @param request The request used to process the input
     */
    private static void processRequestBoxInput(CommonParams cparams, CallableRequest request, List<List<CallableRequest>> allOps)
            throws DataServiceFault {
        String requestName = request.getRequestName();
        AxisOperation axisOp = cparams.getAxisService().getOperation(new QName(requestName));
        CallQuery callQuery = request.getCallQuery();
        Query query = callQuery.getQuery();
        AxisMessage inMessage = axisOp.getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
        if (inMessage != null) {
            inMessage.setName(requestName + Java2WSDLConstants.MESSAGE_SUFFIX);
                /* create input message element */
            XmlSchemaElement inputElement = createElement(cparams, query.getInputNamespace(),
                                                          requestName, true);
                /* complex type for input message element */
            XmlSchemaComplexType inputComplexType = createComplexType(cparams,
                                                                      query.getInputNamespace(), requestName, false);
                /* set element type */
            inputElement.setType(inputComplexType);
                /* batch requests */
            for (List<CallableRequest> callableRequests : allOps) {
                for (CallableRequest callableRequest : callableRequests) {
                    XmlSchemaElement nestedEl = new XmlSchemaElement();
                    if (callableRequest != null) {
                        if (!isBoxcarringOp(callableRequest.getRequestName())) {
                            nestedEl.setRefName(cparams.getRequestInputElementMap().get(
                                    callableRequest.getRequestName()));
//                            nestedEl.setMaxOccurs(Long.MAX_VALUE);
                            addElementToComplexTypeAll(cparams, inputComplexType,
                                                       query.getInputNamespace(),
                                                       nestedEl, false, false, true);
                        }
                    } else {
                        throw new DataServiceFault("No parent operation for batch request: "
                                                   + request.getRequestName());
                    }
                }
            }
                /* set the input element qname in message */
            inMessage.setElementQName(inputElement.getQName());
                /* store request name and element qname mapping */
            cparams.getRequestInputElementMap().put(request.getRequestName(),
                                                    inMessage.getElementQName());

        }
    }

    /**
     * Helper method to check whether operation is boxcarring operation or not.
     *
     * @param opName
     * @return true if one of boxarring operations false otherwise
     */
    private static boolean isBoxcarringOp(String opName) {
        if (opName.endsWith(DBConstants.BoxcarringOps.BEGIN_BOXCAR)
            || opName.endsWith(DBConstants.BoxcarringOps.END_BOXCAR)
            || opName.endsWith(DBConstants.BoxcarringOps.ABORT_BOXCAR)) {
            return true;
        }
        return false;
    }

	/**
	 * Process the given request's input parameters.
	 * @param cparams The common parameters used in the schema generator
	 * @param request The request used to process the input
	 */
	private static void processRequestInput(CommonParams cparams, CallableRequest request)
            throws DataServiceFault {
		String requestName = request.getRequestName(); 
		AxisOperation axisOp = cparams.getAxisService().getOperation(new QName(requestName));
		CallQuery callQuery = request.getCallQuery();
		Query query = callQuery.getQuery();
		boolean optional = false;
		AxisMessage inMessage = axisOp.getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
		if (inMessage != null) {
                inMessage.setName(requestName + Java2WSDLConstants.MESSAGE_SUFFIX);
                /* create input message element */
                XmlSchemaElement inputElement = createElement(cparams, query.getInputNamespace(),
                        requestName, true);
                /* complex type for input message element */
                XmlSchemaComplexType inputComplexType = createComplexType(cparams,
                        query.getInputNamespace(), requestName, false);
                /* set element type */
                inputElement.setType(inputComplexType);
                /* batch requests */
                if (request.isBatchRequest()) {
                    XmlSchemaElement nestedEl = new XmlSchemaElement();
                    CallableRequest parentReq = request.getParentRequest();
                    if (parentReq != null) {
                        nestedEl.setRefName(cparams.getRequestInputElementMap().get(
                                parentReq.getRequestName()));
                        nestedEl.setMaxOccurs(Long.MAX_VALUE);
                        addElementToComplexTypeSequence(cparams, inputComplexType,
                                query.getInputNamespace(),
                                nestedEl, false, false, false);
                    } else {
                        throw new DataServiceFault("No parent operation for batch request: "
                                + request.getRequestName());
                    }
                } else {
                    /* normal requests */
                    XmlSchemaElement tmpEl;
                    Map<String, WithParam> withParams = callQuery.getWithParams();
                    WithParam tmpWithParam;
                    /* create elements for individual parameters */
                    if (callQuery.getWithParams().size() > 0) {
                        for (QueryParam queryParam : query.getQueryParams()) {
                            if (DBConstants.QueryTypes.IN.equals(queryParam.getType())
                                    || DBConstants.QueryTypes.INOUT.equals(queryParam.getType())) {
                                tmpWithParam = withParams.get(queryParam.getName());
                                if (tmpWithParam == null) {
                                    /* this query param's value must be coming from an export, not
                                     * from the operation's parameter */
                                    continue;
                                }
                                tmpEl = createInputEntryElement(cparams, query, queryParam,
                                        tmpWithParam);
                                /* checking if query is SQL update query and for optional parameters*/
                                optional = callQuery.getQuery() instanceof SQLQuery
                                        && ((SQLQuery) query).getSqlQueryType() == SQLQuery.QueryType.UPDATE
                                        && queryParam.isOptional();
                                /* add to input element complex type */
                                addElementToComplexTypeSequence(cparams, inputComplexType, query.getInputNamespace(),
                                     tmpEl, false, false, optional);
                            }
                        }
                    } else {
                        /* Adds the operation name to the SOAP body when used with OUT_ONLY requests
                         * and further creates a complex type corresponds to the IN-MESSAGE with
                         * an empty sequence */
                        XmlSchemaSequence emptySeq = new XmlSchemaSequence();
                        inputComplexType.setParticle(emptySeq);
                    }
                }
                /* set the input element qname in message */
                inMessage.setElementQName(inputElement.getQName());
                /* store request name and element qname mapping */
                cparams.getRequestInputElementMap().put(request.getRequestName(),
                        inMessage.getElementQName());

            }
	}
	
	/**
	 * Create an element for an input parameter.
	 * @param cparams The common parameters used in the schema generator
	 * @param query The query which the parameter belongs to
	 * @param queryParam The query parameter
	 * @param withParam The operation parameter mapping
	 * @return Newly created element 
	 */
	private static XmlSchemaElement createInputEntryElement(CommonParams cparams, Query query,
                                                            QueryParam queryParam, WithParam withParam) {
		XmlSchemaElement element = createElement(cparams, query.getInputNamespace(), 
				withParam.getOriginalName(), false);
		element.setSchemaTypeName(DBUtils.getSimpleSchemaTypeName(cparams.getTypeTable(),
                                                                  DBUtils.getJavaTypeFromSQLType(queryParam.getSqlType())));
		/* default is minOccurs=1, maxOccurs=1 */
		if (DBConstants.QueryParamTypes.ARRAY.equals(queryParam.getParamType())) {
			element.setMaxOccurs(Long.MAX_VALUE);
			element.setMinOccurs(0);
		} else if (queryParam.getDefaultValue() != null) {
			element.setMinOccurs(0);
		}
		element.setNillable(true);
		return element;
	}

	/**
	 * Process the given request's output types.
	 * @param cparams The common parameters used in the schema generator
	 * @param request The request used to process the output
	 */
	private static void processRequestOutput(CommonParams cparams, CallableRequest request)
            throws DataServiceFault {
		CallQuery callQuery = request.getCallQuery();
		if (!(callQuery.getQuery().hasResult() || request.isReturnRequestStatus())) {
			return;
		}
		
		AxisOperation axisOp = cparams.getAxisService().getOperation(
				new QName(request.getRequestName()));
		AxisMessage outMessage = axisOp.getMessage(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
		outMessage.setName(request.getRequestName() + Java2WSDLConstants.RESPONSE);
		
		if (request.isReturnRequestStatus() && !callQuery.getQuery().hasResult()) {
			outMessage.setElementQName(new QName(DBConstants.WSO2_DS_NAMESPACE,
					DBConstants.REQUEST_STATUS_WRAPPER_ELEMENT));
			return;
		}
		
		Result result = callQuery.getQuery().getResult();
		if (result.isXsAny() || result.getResultType() == ResultTypes.RDF) {
			outMessage.setElementQName(new QName(DBConstants.WSO2_DS_NAMESPACE,
					DBConstants.DATA_SERVICE_RESPONSE_WRAPPER_ELEMENT));
			return;
		}
		
		/* create dummy element to contain the result element */
		XmlSchemaElement dummyParentElement = new XmlSchemaElement();
		dummyParentElement.setQName(new QName(result.getNamespace(), DUMMY_NAME));
		XmlSchema dummySchema = retrieveSchema(cparams, result.getNamespace());
		XmlSchemaComplexType dummyType = new XmlSchemaComplexType(dummySchema);
		dummyType.setName(DUMMY_NAME);
		dummyParentElement.setType(dummyType);
		/* lets do it */
		processCallQuery(cparams, dummyParentElement, callQuery);
		/* extract the element and set it to the message */
		XmlSchemaElement resultEl = (XmlSchemaElement) ((XmlSchemaSequence) dummyType.getParticle())
				.getItems().getItem(0);
		outMessage.setElementQName(resultEl.getRefName());
	}

	/**
	 * Process the given request's output types.
	 * @param cparams The common parameters used in the schema generator
	 * @param request The request used to process the output
	 */
	private static void processRequestBoxOutput(CommonParams cparams, CallableRequest request,
			List<List<CallableRequest>> allOps) throws DataServiceFault {
		AxisOperation axisOp = cparams.getAxisService().getOperation(new QName(request.getRequestName()));
		AxisMessage outMessage = axisOp.getMessage(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
		outMessage.setName(request.getRequestName() + Java2WSDLConstants.RESPONSE);

		XmlSchemaElement parentElement = createElement(cparams, request.getCallQuery().getNamespace() , DBConstants.DATA_SERVICE_REQUEST_BOX_RESPONSE_WRAPPER_ELEMENT,true);
		XmlSchemaComplexType outputComplexType = createComplexType(cparams,
				request.getCallQuery().getQuery().getInputNamespace(), request.getRequestName(), false);
		parentElement.setType(outputComplexType);
		for (List<CallableRequest> callableRequests : allOps) {
			for (CallableRequest callableRequest : callableRequests) {
				if (callableRequest != null) {
					CallQuery callQuery = callableRequest.getCallQuery();
                    Result result = callQuery.getQuery().getResult();
                    if (!isBoxcarringOp(callableRequest.getRequestName()) && result != null) {
						Query query = callQuery.getQuery();
						XmlSchemaElement dummyParentElement = new XmlSchemaElement();
						dummyParentElement.setQName(new QName(result.getNamespace(), DUMMY_NAME));
						XmlSchema dummySchema = retrieveSchema(cparams, result.getNamespace());
						XmlSchemaComplexType dummyType = new XmlSchemaComplexType(dummySchema);
						dummyType.setName(DUMMY_NAME);
						dummyParentElement.setType(dummyType);
						/* lets do it */
						processCallQuery(cparams, dummyParentElement, callQuery);
	                    /* extract the element and set it to the message */
						XmlSchemaElement resultEl = (XmlSchemaElement) ((XmlSchemaSequence) dummyType.getParticle())
								.getItems().getItem(0);
						addElementToComplexTypeAll(cparams, outputComplexType, query.getInputNamespace(), resultEl,
								false, false, true);
					}
				} else {
					throw new DataServiceFault("No parent operation for batch request: " + request.getRequestName());
				}
			}
		}

		/* create dummy element to contain the result element */
		outMessage.setElementQName(parentElement.getQName());
	}
	
	/**
	 * Process the given call query to create the element/types.
	 * @param cparams The common parameters used in the schema generator
	 * @param parentElement The parent element where the created element will be added to
	 * @param callQuery The call query used to create the element/types 
	 */
	private static void processCallQuery(CommonParams cparams, XmlSchemaElement parentElement, 
			CallQuery callQuery) throws DataServiceFault {
		if (!callQuery.getQuery().hasResult()) {
			return;
		}
		Result result = callQuery.getQuery().getResult();
		XmlSchemaElement activeElement = parentElement;
		XmlSchemaElement tmpElement;
		/* process result wrapper */
		if (!DBUtils.isEmptyString(result.getElementName())) {
			tmpElement = getElement(cparams, result.getNamespace(), result.getElementName());
			if (tmpElement != null) {
				/* the element already exists .. */
				addToElement(cparams, activeElement, tmpElement, true, false, callQuery.isOptional());
				/* no need to continue */
				return;
			}
			activeElement = createAndAddToElement(cparams, activeElement,
					result.getElementName(), result.getNamespace(), true, false,
					callQuery.isOptional());			
		}
		/* process result row */
		if (!DBUtils.isEmptyString(result.getRowName())) {
			tmpElement = getElement(cparams, result.getNamespace(), result.getRowName());
			if (tmpElement != null) {
				addToElement(cparams, activeElement, tmpElement, true, false, false);
				return;
			}
			activeElement = createAndAddToElement(cparams, activeElement, 
					result.getRowName(), result.getNamespace(), false, false,
					callQuery.isOptional() );
			/* rows can be from zero to infinity */
			activeElement.setMinOccurs(0);
			activeElement.setMaxOccurs(Long.MAX_VALUE);
		}

		/* process the result content */
		processElementGroup(cparams, activeElement, result.getDefaultElementGroup());
	}
	
	/**
	 * Process the given element group.
	 * @param cparams The common parameters used in the schema generator
	 * @param parentElement The parent element where the element group will be added to
	 * @param elementGroup The element group to be added to the parent element
	 * @throws DataServiceFault
	 */
	private static void processElementGroup(CommonParams cparams, XmlSchemaElement parentElement,
			OutputElementGroup elementGroup) throws DataServiceFault {
		XmlSchemaElement activeElement = parentElement;
		if (!DBUtils.isEmptyString(elementGroup.getName())) {
			XmlSchemaElement tmpElement = getElement(cparams, 
					elementGroup.getNamespace(), elementGroup.getName());
			if (tmpElement != null) {
				if (elementGroup.getArrayName() == null) {
                    addToElement(cparams, activeElement, tmpElement, true, false, elementGroup.isOptional());
                } else {
                    addToElement(cparams, activeElement, tmpElement, true, true, elementGroup.isOptional());
                }
                /* element group already exists, nothing else to do here .. */
                return;
			}
			if (elementGroup.getArrayName() != null) {
                activeElement = createAndAddToElement(cparams, activeElement,
                    elementGroup.getName(), elementGroup.getNamespace(), true, true, elementGroup.isOptional());
            } else {
               activeElement = createAndAddToElement(cparams, activeElement,
                    elementGroup.getName(), elementGroup.getNamespace(), true, false, elementGroup.isOptional());
            }
			/* Setting boundaries for array elements */
            if (elementGroup.getArrayName() != null) {
                activeElement.setMinOccurs(0);
                activeElement.setMaxOccurs(Long.MAX_VALUE);
            }
		}
		/* process attributes */
		for (StaticOutputElement attribute : elementGroup.getAttributeEntries()) {
			addAttributeToComplexType(extractElementSchemaType(cparams, activeElement),
					attribute.getName(), attribute.getXsdType());
		}
		/* process elements / elementGroups / call-queries */
		StaticOutputElement tmpStaticEl;
		for (OutputElement outEl : elementGroup.getAllElements()) {
			if (outEl instanceof StaticOutputElement) {
				tmpStaticEl = (StaticOutputElement) outEl;
				processStaticOutputElement(cparams, activeElement, tmpStaticEl);
			} else if (outEl instanceof CallQuery) {
				processCallQuery(cparams, activeElement, 
						((CallQuery) outEl));
			} else if (outEl instanceof OutputElementGroup) {
				processElementGroup(cparams, activeElement, (OutputElementGroup) outEl);
			}
		}
	}
	
	/**
	 * Processes a static output element, and adds its element to the schema.
	 * @param cparams The common parameters used in the schema generator
	 * @param parentElement Parent element the new element will be added to
	 * @param staticEl The output element, the schema element to be based on
	 */
	private static void processStaticOutputElement(CommonParams cparams, 
			XmlSchemaElement parentElement,	StaticOutputElement staticEl) {
		XmlSchemaComplexType parentType = extractElementSchemaType(cparams, parentElement);
		boolean global = !parentElement.getQName().getNamespaceURI().equals(staticEl.getNamespace());
		XmlSchemaElement tmpSchemaEl = createElement(cparams, staticEl.getNamespace(), 
				staticEl.getName(), global);
		if (staticEl.getArrayName() != null) {
            tmpSchemaEl.setMaxOccurs(Long.MAX_VALUE);
        } else {
            tmpSchemaEl.setMaxOccurs(1);
        }
		tmpSchemaEl.setNillable(true);
		tmpSchemaEl.setSchemaTypeName(staticEl.getXsdType());
		if (staticEl.getArrayName() == null) {
            addElementToComplexTypeSequence(cparams, parentType,
                    parentElement.getQName().getNamespaceURI(), tmpSchemaEl, global, false,
                    staticEl.isOptional());
        } else {
            addElementToComplexTypeSequence(cparams, parentType,
                    parentElement.getQName().getNamespaceURI(), tmpSchemaEl, global, true,
                    staticEl.isOptional());
        }
	}
	
	/**
	 * Extracts the schema type from the given element.
	 * @param cparams The common parameters used in the schema generator
	 * @param element The element the type will be extract from
	 * @return The element type
	 */
	private static XmlSchemaComplexType extractElementSchemaType(CommonParams cparams,
			XmlSchemaElement element) {
		XmlSchemaComplexType type = (XmlSchemaComplexType) element.getSchemaType();
		/* if null, this must be globally defined */
		if (type == null) {
			type = cparams.getTypeMap().get(element.getSchemaTypeName());
		}
		return type;
	}

	/**
	 * Creates a new element and complex schema type with the given name and the namespace.
	 * @param cparams The common parameters used in the schema generator
	 * @param parentElement The parent element to where the new element will be added
	 * @param name The name of the element
	 * @param namespace The namespace of the element
	 * @param global Signal if the newly created element is global or not
	 * @return The newly added XML schema element
	 */
	private static XmlSchemaElement createAndAddToElement(CommonParams cparams,
			XmlSchemaElement parentElement, String name, String namespace, boolean global,
			boolean isArrayElement, boolean optional) {
		XmlSchemaElement tmpElement = createElement(cparams, namespace, name, global);
		XmlSchemaComplexType type = createComplexType(cparams, namespace, name, true);
		tmpElement.setSchemaTypeName(type.getQName());
		if (isArrayElement) {
            addToElement(cparams, parentElement, tmpElement, global, true, optional);
        } else {
            addToElement(cparams, parentElement, tmpElement, global, false, optional);
        }
		return tmpElement;
	}

	private static void addToElement(CommonParams cparams,
			XmlSchemaElement parentElement, XmlSchemaElement element, boolean elementRef, 
			boolean isArrayElement, boolean optional) {
		/* check if new element's namespace and parent's namespace is the same,
		 * if it's different, this has to be forced to be element references */
		if (!parentElement.getQName().getNamespaceURI().equals(
				element.getQName().getNamespaceURI())) {
			elementRef = true;
		}		
		XmlSchemaType type = extractElementSchemaType(cparams, parentElement);
		if (isArrayElement) {
            addElementToComplexTypeSequence(cparams, (XmlSchemaComplexType) type,
                parentElement.getQName().getNamespaceURI(), element, elementRef, true, optional);
        } else {
            addElementToComplexTypeSequence(cparams, (XmlSchemaComplexType) type,
                parentElement.getQName().getNamespaceURI(), element, elementRef, false, optional);
        }
	}
	
	/**
	 * Creates the default data services fault element, and stores it in the schema.
	 * @param cparams The common parameters used in the schema generator
	 */
	private static void createAndStoreFaultElement(CommonParams cparams) {
		XmlSchemaElement element = createElement(cparams, DBConstants.WSO2_DS_NAMESPACE,
				DBConstants.DS_FAULT_ELEMENT, true);
        XmlSchemaComplexType type = createComplexType(cparams, DBConstants.WSO2_DS_NAMESPACE,
                DBConstants.DS_FAULT_ELEMENT, false);
        element.setType(type);
		if ("true".equalsIgnoreCase(DBUtils.getCurrentParamsDisabledProperty())) {
			createAndAddSimpleStringElements(cparams, element,
					DBConstants.FaultParams.CURRENT_REQUEST_NAME, DBConstants.FaultParams.NESTED_EXCEPTION);
		} else {
			createAndAddSimpleStringElements(cparams, element,
					DBConstants.FaultParams.CURRENT_PARAMS, DBConstants.FaultParams.CURRENT_REQUEST_NAME,
					DBConstants.FaultParams.NESTED_EXCEPTION);
		}
        XmlSchemaElement dataServiceElement = createElement(cparams, DBConstants.WSO2_DS_NAMESPACE,
                DBConstants.FaultParams.SOURCE_DATA_SERVICE, false);
        XmlSchemaComplexType dataServiceComplexType = createComplexType(cparams, DBConstants.WSO2_DS_NAMESPACE,
                DBConstants.FaultParams.SOURCE_DATA_SERVICE, false);
        addToElement(cparams, element, dataServiceElement, false, false, false);
        dataServiceElement.setType(dataServiceComplexType);
        createAndAddSimpleStringElements(cparams, dataServiceElement,
                DBConstants.FaultParams.LOCATION, DBConstants.FaultParams.DEFAULT_NAMESPACE,
                DBConstants.FaultParams.DESCRIPTION, DBConstants.FaultParams.DATA_SERVICE_NAME);
        createAndAddSimpleStringElements(cparams, element, DBConstants.FaultParams.DS_CODE);
	}

    /**
     * Creates a new Simple String type element with the given name and added to the given parent element.
     * @param cparams The common parameters used in the schema generator
     * @param parentElement The parent element to where the new element will be added
     * @param elementNames The name of the elements to be added
     */
    private static void createAndAddSimpleStringElements(CommonParams cparams,
                                                   XmlSchemaElement parentElement, String... elementNames) {
        for (String elementName : elementNames) {
            XmlSchemaElement tmpSchemaEl = createElement(cparams, DBConstants.WSO2_DS_NAMESPACE,
                    elementName, false);
            tmpSchemaEl.setNillable(false);
            tmpSchemaEl.setSchemaTypeName(Constants.XSD_STRING);
            addToElement(cparams, parentElement, tmpSchemaEl, false, false, false);
        }
    }

	/**
	 * Creates the default data services request status element, and stores it in the schema.
	 * @param cparams The common parameters used in the schema generator
	 */
	private static void createAndStoreRequestStatusElement(CommonParams cparams) {
		XmlSchemaElement element = createElement(cparams, DBConstants.WSO2_DS_NAMESPACE,
				DBConstants.REQUEST_STATUS_WRAPPER_ELEMENT, true);
		element.setSchemaTypeName(Constants.XSD_STRING);
	}
	
	/**
	 * Creates the default data services response element, and stores it in the schema.
	 * @param cparams The common parameters used in the schema generator
	 */
	private static void createAndStoreDataServiceResponseElement(CommonParams cparams) {
		XmlSchemaElement element = createElement(cparams, DBConstants.WSO2_DS_NAMESPACE,
				DBConstants.DATA_SERVICE_RESPONSE_WRAPPER_ELEMENT, true);
		XmlSchemaComplexType type = createComplexType(cparams, DBConstants.WSO2_DS_NAMESPACE, 
				DBConstants.DATA_SERVICE_RESPONSE_WRAPPER_ELEMENT, false);
		element.setType(type);
		XmlSchemaAny anyEl = new XmlSchemaAny();
		anyEl.setMinOccurs(0);
		XmlSchemaSequence seq = new XmlSchemaSequence();
		seq.getItems().add(anyEl);
		type.setParticle(seq);
	}
		
	/**
	 * Creates an XML schema element. If an element with the given QName already exists, 
	 * the returned elements's name maybe different in the case of creating a new element 
	 * and the given name is already taken and re-using is disabled.
	 * @param cparams The common parameters used in the schema generator
	 * @param namespace The namespace of the element
	 * @param name The name of the element
	 * @param global If this is a global element
	 * @return The created or existing XML schema element
	 */
	private static XmlSchemaElement createElement(CommonParams cparams, String namespace,
			String name, boolean global) {
		Map<QName, XmlSchemaElement> elementMap = cparams.getElementMap();
		if (name == null || namespace == null) {
			return new XmlSchemaElement();
		}
		QName qname = new QName(namespace, name);
		if (global && elementMap.containsKey(qname)) {
			int suffix = 1; // start with 2, if x is there, the next one would be x2
			while (elementMap.containsKey((new QName(namespace, name + (++suffix)))));
			name = name + suffix;
			qname = new QName(namespace, name);				
		}
		XmlSchemaElement element = new XmlSchemaElement();
		element.setQName(qname);
		element.setName(name);
		if (global) {
			elementMap.put(qname, element);
			XmlSchema schema = retrieveSchema(cparams, namespace);
			schema.getItems().add(element);
			schema.getElements().add(qname, element);
		}
		return element;
	}
		
	/**
	 * Returns the XML schema element, if it's already created.
	 * @param cparams The common parameters used in the schema generator
	 * @param namespace The namespace of the element
	 * @param name The local name of the element
	 * @return Existing element or, null if the element cannot be found
	 */
	private static XmlSchemaElement getElement(CommonParams cparams, String namespace, 
			String name) {
		// cparams.getElementMap().get(new QName(namespace, name)); wont work !! 
		for (Entry<QName, XmlSchemaElement> entry : cparams.getElementMap().entrySet()) {
			if (entry.getKey().getLocalPart().equals(name)
					&& entry.getKey().getNamespaceURI().equals(namespace)) {
				return entry.getValue();
			}
		}
		return null;
	}
	
	/**
	 * Creates XML schema complex type with the given namespace and the name.
	 * @param cparams The common parameters used in the schema generator
	 * @param namespace The namespace of the complex type
	 * @param name The name of the complex type
	 * @param global Signal if the newly created type is global or not
	 * @return The newly create XML schema complex type
	 */
	private static XmlSchemaComplexType createComplexType(CommonParams cparams, String namespace,
			String name, boolean global) {		
		XmlSchema schema = retrieveSchema(cparams, namespace);
		XmlSchemaComplexType type = new XmlSchemaComplexType(schema);
		Map<QName, XmlSchemaComplexType> typeMap = cparams.getTypeMap();
		if (global) {
			QName qname = new QName(namespace, name);
			if (typeMap.containsKey(qname)) {
				int suffix = 1; // start with 2, if T is there, the next one would be T2
				while (typeMap.containsKey(new QName(namespace, name + (++suffix))));
				name = name + suffix;
				qname = new QName(namespace, name);
			}		
			type.setName(name);
			cparams.getTypeMap().put(qname, type);
			schema.getItems().add(type);
            schema.getSchemaTypes().add(qname, type);
		}
		return type;
	}
	
	/**
	 * Adds the given parameter to the complex type.
	 * @param complexType The complex type which the attribute will be added to
	 * @param name The name of the attribute
	 * @param xsdType The type of the attribute
	 */
	@SuppressWarnings("unchecked")
	private static void addAttributeToComplexType(XmlSchemaComplexType complexType, String name,
			QName xsdType) {
		XmlSchemaAttribute attr = new XmlSchemaAttribute();
		attr.setName(name);
		attr.setSchemaTypeName(xsdType);
		attr.setUse(new XmlSchemaUse("optional"));
		XmlSchemaAttribute tmpAttr;
		for (Iterator<XmlSchemaAttribute> itr = complexType.getAttributes().getIterator(); 
		         itr.hasNext();) {
			tmpAttr = itr.next();
			if (tmpAttr.getName().equals(attr.getName())) {
				/* current attribute is already set, nothing more to do */
				return;
			}
		}
		complexType.getAttributes().add(attr);
	}
	
	/**
	 * Adds the given element to the complex type sequence.
	 * @param cparams The common parameters used in the schema generator
	 * @param complexType The complex type to where the element to be added
	 * @param complexTypeNS The complex type namespace
	 * @param element The element to be added
	 * @param elementRef Tells if the element passed in should be added as an element reference
	 */
	private static void addElementToComplexTypeSequence(CommonParams cparams, 
			XmlSchemaComplexType complexType, String complexTypeNS, 
			XmlSchemaElement element, boolean elementRef, boolean isArrayElement,
			boolean optional) {
		XmlSchemaParticle particle = complexType.getParticle();
		XmlSchemaSequence sequence;
		if (particle instanceof XmlSchemaSequence) {
			sequence = (XmlSchemaSequence) particle;
		} else {
			sequence = new XmlSchemaSequence();
			complexType.setParticle(sequence);
		}
		XmlSchemaElement tmpElement;
		if (elementRef) {
			tmpElement = new XmlSchemaElement();
            if (isArrayElement) {
                tmpElement.setMaxOccurs(Long.MAX_VALUE);
            }
			tmpElement.setRefName(element.getQName());
			resolveSchemaImports(cparams.getSchemaMap().get(
					complexTypeNS),	tmpElement.getRefName().getNamespaceURI());
		} else {
			tmpElement = element;
		}
		tmpElement.setMinOccurs(optional ? 0 : 1);
		sequence.getItems().add(tmpElement);
	}

    /**
     * Adds the given element to the complex type all
     *
     * @param cparams The common parameters used in the schema generator
     * @param complexType The complex type to where the element to be added
     * @param complexTypeNS The complex type namespace
     * @param element The element to be added
     * @param elementRef Tells if the element passed in should be added as an element reference
     * @param isArrayElement Whether the element is an array or not
     * @param optional Element is optional or not
     */
    private static void addElementToComplexTypeAll(CommonParams cparams,
                                                        XmlSchemaComplexType complexType, String complexTypeNS,
                                                        XmlSchemaElement element, boolean elementRef, boolean isArrayElement,
                                                        boolean optional) {
        XmlSchemaParticle particle = complexType.getParticle();
        XmlSchemaAll sequence;
        if (particle instanceof XmlSchemaAll) {
            sequence = (XmlSchemaAll) particle;
        } else {
            sequence = new XmlSchemaAll();
            complexType.setParticle(sequence);
        }
        XmlSchemaElement tmpElement;
        if (elementRef) {
            tmpElement = new XmlSchemaElement();
            if (isArrayElement) {
                tmpElement.setMaxOccurs(Long.MAX_VALUE);
            }
            tmpElement.setRefName(element.getQName());
            resolveSchemaImports(cparams.getSchemaMap().get(
                    complexTypeNS),	tmpElement.getRefName().getNamespaceURI());
        } else {
            tmpElement = element;
        }
//        tmpElement.setMinOccurs(optional ? 0 : 1);
        sequence.setMinOccurs(optional ? 0 : 1);
        sequence.getItems().add(tmpElement);
    }
		
	/**
	 * Resolves schema imports by adding an schema import section to the host schema.
	 * @param hostSchema The host schema
	 * @param refNamespace The external schema reference
	 */
	private static void resolveSchemaImports(XmlSchema hostSchema, String refNamespace) {
		if (!hostSchema.getTargetNamespace().equals(refNamespace)) {
			XmlSchemaImport schemaImport = new XmlSchemaImport();
			schemaImport.setNamespace(refNamespace);
			if (!containsSchemaImport(hostSchema, schemaImport)) {
				hostSchema.getItems().add(schemaImport);
			}
		}
	}
	
	/**
	 * Checks if the specified schema import is existent.
	 * @param schema The target schema to be checked
	 * @param schemaImport The schema import
	 * @return true if the schema import is there in the given schema
	 */
	private static boolean containsSchemaImport(XmlSchema schema, XmlSchemaImport schemaImport) {
		XmlSchemaObjectCollection list = schema.getItems();
		int c = list.getCount();
		XmlSchemaObject obj;
		for (int i = 0; i < c; i++) {
			obj = list.getItem(i);
			if (obj instanceof XmlSchemaImport) {
				if (((XmlSchemaImport) obj).getNamespace().equals(schemaImport.getNamespace())) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Extracts all the data service operations.
	 * @param dataservice The data service which contains the operations
	 * @return [0] - Normal operation list, [1] - Batch operation list
	 */
	private static List<List<Operation>> extractOperations(DataService dataservice) {
		List<Operation> normalOperations = new ArrayList<Operation>();
		List<Operation> batchOperations = new ArrayList<Operation>();
		Operation tmpOp;
		for (String opName : dataservice.getOperationNames()) {
			tmpOp = dataservice.getOperation(opName);
			if (tmpOp.isBatchRequest()) {
				batchOperations.add(tmpOp);
			} else if (!opName.equals(DBConstants.REQUEST_BOX_ELEMENT)){
                normalOperations.add(tmpOp);
            }
		}		
		List<List<Operation>> allOps = new ArrayList<List<Operation>>();
		allOps.add(normalOperations);
		allOps.add(batchOperations);
		return allOps;
	}
	
	/**
	 * Extracts all the resources in the data service.
	 * @param dataservice The data service which contains the resources
	 * @return [0] - Normal resource list, [1] - Batch resources list
	 */
	private static List<List<Resource>> extractResources(DataService dataservice) {
	    List<Resource> normalResources = new ArrayList<Resource>();
        List<Resource> batchResources = new ArrayList<Resource>();
        Resource tmpRes;
		for (Resource.ResourceID rid : dataservice.getResourceIds()) {
		    tmpRes = dataservice.getResource(rid);
		    if (tmpRes.isBatchRequest()) {
		        batchResources.add(tmpRes);
		    } else if (!rid.getPath().endsWith(DBConstants.REQUEST_BOX_ELEMENT)){
		        normalResources.add(tmpRes);
		    }
		}
		List<List<Resource>> allResourses = new ArrayList<List<Resource>>();
        allResourses.add(normalResources);
        allResourses.add(batchResources);
        return allResourses;
	}
	
	/**
	 * Retrieve the XML schema with the given namespace.
	 * @param cparams Common parameters used in the schema generator
	 * @param namespace The target namespace of the XML schema
	 * @return The XML schema object
	 */
	private static XmlSchema retrieveSchema(CommonParams cparams, String namespace) {
		Map<String, XmlSchema> schemaMap = cparams.getSchemaMap();
		if (!schemaMap.containsKey(namespace)) {
			XmlSchema schema = new XmlSchema(namespace, cparams.getXmlSchemaCollection());
			schema.setNamespaceContext(new NamespaceMap());
			schemaMap.put(namespace, schema);
			schema.setElementFormDefault(new XmlSchemaForm(XmlSchemaForm.QUALIFIED));
			schema.setAttributeFormDefault(new XmlSchemaForm(XmlSchemaForm.UNQUALIFIED));
		}
		return schemaMap.get(namespace);
	}
	
	/**
	 * Represents commonly used parameters in the schema generator,
	 * an instance of this is passed around the methods.
	 */
	public static class CommonParams {
		
		private AxisService axisService;
		
		private DataService dataservice;
		
		private Map<String, XmlSchema> schemaMap;
		
		private XmlSchemaCollection xmlSchemaCollection;
		
		private Map<QName, XmlSchemaElement> elementMap;
		
		private Map<QName, XmlSchemaComplexType> typeMap;
		
		private TypeTable typeTable = new TypeTable();
		
		private Map<String, QName> requestInputElementMap = new HashMap<String, QName>();
		
		public CommonParams(AxisService axisService, DataService dataservice) {
			this.schemaMap = new HashMap<String, XmlSchema>();
			this.xmlSchemaCollection = new XmlSchemaCollection();
			this.elementMap = new HashMap<QName, XmlSchemaElement>();
			this.typeMap = new HashMap<QName, XmlSchemaComplexType>();
			this.axisService = axisService;
			this.dataservice = dataservice;
		}

		public Map<String, QName> getRequestInputElementMap() {
			return requestInputElementMap;
		}
		
		public TypeTable getTypeTable() {
			return typeTable;
		}

		public AxisService getAxisService() {
			return axisService;
		}
		
		public DataService getDataService() {
			return dataservice;
		}
		
		public Map<String, XmlSchema> getSchemaMap() {
			return schemaMap;
		}
		
		public XmlSchemaCollection getXmlSchemaCollection() {
			return xmlSchemaCollection;
		}
		
		public Map<QName, XmlSchemaElement> getElementMap() {
			return elementMap;
		}
		
		public Map<QName, XmlSchemaComplexType> getTypeMap() {
			return typeMap;
		}
		
	}
	
}
