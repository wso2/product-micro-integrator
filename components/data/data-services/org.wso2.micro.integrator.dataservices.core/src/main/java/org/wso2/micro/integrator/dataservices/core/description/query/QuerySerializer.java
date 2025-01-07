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
package org.wso2.micro.integrator.dataservices.core.description.query;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.common.DBConstants.DBSFields;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery.WithParam;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.XSLTTransformer;
import org.wso2.micro.integrator.dataservices.core.description.event.EventTrigger;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery;
import org.wso2.micro.integrator.dataservices.core.engine.OutputElement;
import org.wso2.micro.integrator.dataservices.core.engine.OutputElementGroup;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;
import org.wso2.micro.integrator.dataservices.core.engine.QueryParam;
import org.wso2.micro.integrator.dataservices.core.engine.Result;
import org.wso2.micro.integrator.dataservices.core.engine.StaticOutputElement;
import org.wso2.micro.integrator.dataservices.core.validation.Validator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.ArrayTypeValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.DoubleRangeValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.LengthValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.LongRangeValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.PatternValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.ScalarTypeValidator;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This class represents the serializing functionality of a Query.
 * @see Query
 */
public class QuerySerializer {

	public static OMElement serializeQuery(Query query) {
		OMFactory fac = DBUtils.getOMFactory();
		OMElement queryEl = fac.createOMElement(new QName(DBSFields.QUERY));
		queryEl.addAttribute(DBSFields.ID, query.getQueryId(), null);
		String configId = query.getConfigId();
		if (configId != null) {
			queryEl.addAttribute(DBSFields.USE_CONFIG, configId, null);
		} else {
			queryEl.addAttribute(DBSFields.USE_CONFIG, DBConstants.DEFAULT_CONFIG_ID, null);
		}
		/* populate the query element according to the query type, 
		 * note: no CSV specific query props */
		if (query instanceof SQLQuery) {
			serializeSQLQueryProps((SQLQuery) query, queryEl, fac);
		} else if (query instanceof ExcelQuery) {
			serializeExcelQueryProps((ExcelQuery) query, queryEl, fac);
		} else if (query instanceof GSpreadQuery) {
			serializeGSpreadQueryProps((GSpreadQuery) query, queryEl, fac);
		} else if (query instanceof RdfFileQuery) {
			serializeRdfFileQueryProps((RdfFileQuery) query, queryEl, fac);
		} else if (query instanceof SparqlEndpointQuery) {
			serializeSparqlEndpointQueryProps((SparqlEndpointQuery) query, queryEl, fac);
		} else if (query instanceof WebQuery) {
			serializeWebQueryProps((WebQuery) query, queryEl, fac);
		}
		/* add query params */
		serializeQueryParams(query.getQueryParams(), queryEl, fac);
		/* add event triggers */
		serializeEventTriggers(query, queryEl, fac);
		/* add advanced properties */
		serializeAdvancedProps(query, queryEl, fac);
		/* add the result */
		Result result = query.getResult();
		if (result != null) {
		    serializeResult(result, queryEl, fac);
		}
		return queryEl;
	}

	private static void serializeEventTriggers(Query query, OMElement queryEl, OMFactory fac) {
		EventTrigger inputTrigger = query.getInputEventTrigger();
		EventTrigger outputTrigger = query.getOutputEventTrigger();
		if (inputTrigger != null) {
			queryEl.addAttribute(DBSFields.INPUT_EVENT_TRIGGER,
					inputTrigger.getTriggerId(), null);
		}
		if (outputTrigger != null) {
			queryEl.addAttribute(DBSFields.OUTPUT_EVENT_TRIGGER,
					outputTrigger.getTriggerId(), null);
		}
	}

	private static void serializeAdvancedProps(Query query, OMElement queryEl, OMFactory fac) {
		Map<String, String> props = query.getAdvancedProperties();
		if (props != null && props.size() > 0) {
			OMElement propsEl = fac.createOMElement(new QName(DBSFields.PROPERTIES));
			OMElement propEl;
			for (Entry<String, String> entry : props.entrySet()) {
				propEl = fac.createOMElement(new QName(DBSFields.PROPERTY));
				propEl.addAttribute(DBSFields.NAME, entry.getKey(), null);
				propEl.setText(entry.getValue());
				propsEl.addChild(propEl);
			}
			queryEl.addChild(propsEl);
		}
	}

	private static void serializeSQLQueryProps(SQLQuery sqlQuery, OMElement queryEl, OMFactory fac) {
		OMElement sqlEl = fac.createOMElement(new QName(DBSFields.SQL));
		sqlEl.setText(sqlQuery.getOriginalQuery());
		queryEl.addChild(sqlEl);
		if (sqlQuery.isReturnGeneratedKeys()) {
			queryEl.addAttribute(DBSFields.RETURN_GENERATED_KEYS, Boolean.TRUE.toString(), null);
		} else if (sqlQuery.isReturnUpdatedRowCount()) {
			queryEl.addAttribute(DBSFields.RETURN_UPDATED_ROW_COUNT, Boolean.TRUE.toString(), null);
		}
	}

	private static void serializeExcelQueryProps(ExcelQuery excelQuery, OMElement queryEl, OMFactory fac) {
		OMElement excelEl = fac.createOMElement(new QName(DBSFields.EXCEL));
		OMElement workbookNameEl = fac.createOMElement(new QName(DBConstants.Excel.WORKBOOK_NAME));
		OMElement hasheaderEl = fac.createOMElement(new QName(DBConstants.Excel.HAS_HEADER));
		OMElement startingrowEl = fac.createOMElement(new QName(DBConstants.Excel.STARTING_ROW));
		OMElement maxrowcountEl = fac.createOMElement(new QName(DBConstants.Excel.MAX_ROW_COUNT));
		workbookNameEl.setText(excelQuery.getWorkbookName());
		hasheaderEl.setText(String.valueOf(excelQuery.isHasHeader()));
		startingrowEl.setText(String.valueOf(excelQuery.getStartingRow()));
		maxrowcountEl.setText(String.valueOf(excelQuery.getMaxRowCount()));
		excelEl.addChild(workbookNameEl);
		excelEl.addChild(hasheaderEl);
		excelEl.addChild(startingrowEl);
		excelEl.addChild(maxrowcountEl);
		queryEl.addChild(excelEl);
	}

	private static void serializeGSpreadQueryProps(GSpreadQuery gspreadQuery, OMElement queryEl, OMFactory fac) {
		OMElement gspreadEl = fac.createOMElement(new QName(DBSFields.GSPREAD));
		OMElement worksheetnumberEl = fac.createOMElement(
				new QName(DBConstants.GSpread.WORKSHEET_NUMBER));
		OMElement hasheaderEl = fac.createOMElement(new QName(DBConstants.GSpread.HAS_HEADER));
		OMElement startingrowEl = fac.createOMElement(
				new QName(DBConstants.GSpread.STARTING_ROW));
		OMElement maxrowcountEl = fac.createOMElement(
				new QName(DBConstants.GSpread.MAX_ROW_COUNT));
		worksheetnumberEl.setText(String.valueOf(gspreadQuery.getWorksheetNumber()));
		hasheaderEl.setText(String.valueOf(gspreadQuery.isHasHeader()));
		startingrowEl.setText(String.valueOf(gspreadQuery.getStartingRow()));
		maxrowcountEl.setText(String.valueOf(gspreadQuery.getMaxRowCount()));
		gspreadEl.addChild(worksheetnumberEl);
		gspreadEl.addChild(hasheaderEl);
		gspreadEl.addChild(startingrowEl);
		gspreadEl.addChild(maxrowcountEl);
		queryEl.addChild(gspreadEl);
	}

	private static void serializeSparqlQueryProps(SparqlQueryBase sparqlQuery, OMElement queryEl, OMFactory fac) {
		OMElement sparqlEl = fac.createOMElement(new QName(DBSFields.SPARQL));
		sparqlEl.setText(sparqlQuery.getQuery());
		queryEl.addChild(sparqlEl);
	}

	private static void serializeRdfFileQueryProps (RdfFileQuery sparqlQuery, OMElement queryEl, OMFactory fac)  {
		serializeSparqlQueryProps((SparqlQueryBase)sparqlQuery, queryEl, fac);
	}
	
	private static void serializeSparqlEndpointQueryProps (SparqlEndpointQuery sparqlQuery, OMElement queryEl, OMFactory fac) {
		serializeSparqlQueryProps((SparqlQueryBase)sparqlQuery, queryEl, fac);
	}
	
	private static void serializeWebQueryProps (WebQuery webQuery, OMElement queryEl, OMFactory fac)  {
		OMElement weblEl = fac.createOMElement(new QName(DBSFields.SCRAPER_VARIABLE));
		weblEl.setText(webQuery.getScraperVariable());
		queryEl.addChild(weblEl);
	}
	
	private static void serializeResult(Result result, OMElement queryEl, OMFactory fac) {
		OMElement resEl = fac.createOMElement(new QName(DBSFields.RESULT));		
		int resultType = result.getResultType();
		if (resultType == DBConstants.ResultTypes.RDF) {
			resEl.addAttribute(DBSFields.OUTPUT_TYPE, DBSFields.RDF, null);
			resEl.addAttribute(DBSFields.RDF_BASE_URI, result.getRDFBaseURI(), null);
		} else {
			String elementName = result.getElementName();
			if (elementName != null) {
				resEl.addAttribute(DBSFields.ELEMENT, elementName, null);
			}
			String escapeNonPrintableChar = String.valueOf(result.isEscapeNonPrintableChar());
			if (!escapeNonPrintableChar.isEmpty()) {
				resEl.addAttribute(DBSFields.ESCAPE_NON_PRINTABLE_CHAR, escapeNonPrintableChar, null);
			}
			String rowName = result.getRowName();
			if (rowName != null) {
				resEl.addAttribute(DBSFields.ROW_NAME, rowName, null);
			}
		}
		String defaultNamespace = result.getNamespace();
		if (defaultNamespace != null) {
			resEl.addAttribute(DBSFields.DEFAULT_NAMESPACE, defaultNamespace, null);
		}
        XSLTTransformer transformer = result.getXsltTransformer();
        if (transformer != null) {
            resEl.addAttribute(DBSFields.XSLT_PATH, transformer.getXsltPath(), null);
        }
        OutputElementGroup defGroup = result.getDefaultElementGroup();
		/* first add attributes */
		for (StaticOutputElement soe : defGroup.getAttributeEntries()) {
			serializeStaticOutputElement(soe, resEl, fac);
		}
		/* add elements, elements are iterated this manner is to retain 
		 * the order of elements and call queries */
		for (OutputElement oe : defGroup.getAllElements()) {
			/* normal elements */
			if (oe instanceof StaticOutputElement) {
			    serializeStaticOutputElement((StaticOutputElement) oe, resEl, fac);
			} else if (oe instanceof CallQuery) { /* call queries */
				serializeCallQuery((CallQuery) oe, resEl, fac);
			}
		}
		queryEl.addChild(resEl);
	}
	
	private static void serializeStaticOutputElement(StaticOutputElement soe,
                                                     OMElement resEl, OMFactory fac) {
		OMElement outEl = fac.createOMElement(new QName(soe.getElementType()));
		outEl.addAttribute(DBSFields.NAME, soe.getName(), null);
		outEl.addAttribute(soe.getParamType(), soe.getOriginalParam(), null);
		Set<String> requiredRoles = soe.getRequiredRoles();
		if (requiredRoles != null && requiredRoles.size() > 0) {
			outEl.addAttribute(DBSFields.REQUIRED_ROLES, 
					getRequiredRolesString(requiredRoles), null);
		}
		QName xsdType = soe.getXsdType();
		if (xsdType != null) {
			outEl.addAttribute(DBSFields.XSD_TYPE, 
					DBConstants.DEFAULT_XSD_PREFIX + ":" + xsdType.getLocalPart(), null);
		} else {
			outEl.addAttribute(DBSFields.XSD_TYPE, DBConstants.DEFAULT_XSD_TYPE, null);
		}
		resEl.addChild(outEl);
	}
		
	private static String getRequiredRolesString(Set<String> roles) {
		int c = roles.size();
		int i = 0;
		StringBuilder builder = new StringBuilder();
		for (String role : roles) {
			builder.append(role);
			i++;
			if (i < c) {
				builder.append(',');
			}
		}
		return builder.toString();
	}
	
	public static void serializeCallQuery(CallQuery callQuery,
                                          OMElement parentEl, OMFactory fac) {
		OMElement callQueryEl, withParamEl;
		Set<String> requiredRoles;
        callQueryEl = fac.createOMElement(new QName(DBSFields.CALL_QUERY));
        callQueryEl.addAttribute(DBSFields.HREF, callQuery.getQueryId(), null);
        requiredRoles = callQuery.getRequiredRoles();
        if (requiredRoles != null && requiredRoles.size() > 0) {
            callQueryEl.addAttribute(DBSFields.REQUIRED_ROLES,
                    getRequiredRolesString(requiredRoles), null);
        }
        for (WithParam withParam : callQuery.getWithParams().values()) {
            withParamEl = fac.createOMElement(new QName(DBSFields.WITH_PARAM));
            withParamEl.addAttribute(DBSFields.NAME, withParam.getName(), null);
            withParamEl.addAttribute(withParam.getParamType(), withParam.getParam(), null);
            callQueryEl.addChild(withParamEl);
        }
        parentEl.addChild(callQueryEl);
	}
	
	private static void serializeQueryParams(List<QueryParam> queryParams,
			OMElement queryEl, OMFactory fac) {
		OMElement queryParamEl;
		String paramType, sqlType, type;
		int ordinal;
		ParamValue defaultValue;
		for (QueryParam queryParam : queryParams) {
			queryParamEl = fac.createOMElement(new QName(DBSFields.PARAM));
			queryParamEl.addAttribute(DBSFields.NAME, queryParam.getName(), null);
			paramType = queryParam.getParamType();
			if (paramType != null) {
				queryParamEl.addAttribute(DBSFields.PARAM_TYPE, paramType, null);
			}
			sqlType = queryParam.getSqlType();
			if (sqlType != null) {
				queryParamEl.addAttribute(DBSFields.SQL_TYPE, sqlType, null);
			}
			type = queryParam.getType();
			if (type != null) {
				queryParamEl.addAttribute(DBSFields.TYPE, type, null);
			}
			ordinal = queryParam.getOrdinal();
			if (ordinal > 0) {
				queryParamEl.addAttribute(DBSFields.ORDINAL, String.valueOf(ordinal), null);
			}
			defaultValue = queryParam.getDefaultValue();
			if (defaultValue != null && defaultValue.getScalarValue() != null) {
				queryParamEl.addAttribute(DBSFields.DEFAULT_VALUE, defaultValue.getScalarValue(), null);
			}
			/* add validators */
			serializeValidators(queryParam.getValidators(), queryParamEl, fac);
			/* add queryParam to query */
			queryEl.addChild(queryParamEl);
		}	
	}
	
	private static void serializeValidators(List<Validator> validators,
			OMElement queryParamEl, OMFactory fac) {
		for (Validator validator : validators) {
			/* standard validators */
			if (validator instanceof LengthValidator) {
				serializeLengthValidator((LengthValidator) validator, queryParamEl, fac);
			} else if (validator instanceof PatternValidator) {
				serializePatternValidator((PatternValidator) validator, queryParamEl, fac);
			} else if (validator instanceof LongRangeValidator) {
				serializeLongRangeValidator((LongRangeValidator) validator, queryParamEl, fac);
			} else if (validator instanceof DoubleRangeValidator) {
				serializeDoubleRangeValidator((DoubleRangeValidator) validator, queryParamEl, fac);
			} else if (validator instanceof ArrayTypeValidator) {
				/* ignore - implicitly added validators */
			} else if (validator instanceof ScalarTypeValidator) {
				/* ignore - implicitly added validators */
			} else { /* custom validators */
				serializeCustomValidator(validator, queryParamEl, fac);
			}
		}
	}
	
	private static void serializeLengthValidator(LengthValidator validator,
                                                 OMElement queryParamEl, OMFactory fac) {
		OMElement valEl = fac.createOMElement(new QName(DBSFields.VALIDATE_LENGTH));
		if (validator.isHasMin()) {
			valEl.addAttribute(DBSFields.MINIMUM, String.valueOf(validator.getMinLength()), null);
		}
		if (validator.isHasMax()) {
			valEl.addAttribute(DBSFields.MAXIMUM, String.valueOf(validator.getMaxLength()), null);
		}
		queryParamEl.addChild(valEl);
	}
	
    private static void serializePatternValidator(PatternValidator validator,
                                                  OMElement queryParamEl, OMFactory fac) {
    	OMElement valEl = fac.createOMElement(new QName(DBSFields.VALIDATE_PATTERN));
    	valEl.addAttribute(DBSFields.PATTERN, validator.getPattern().pattern(), null);
		queryParamEl.addChild(valEl);
	}

    private static void serializeLongRangeValidator(LongRangeValidator validator,
                                                    OMElement queryParamEl, OMFactory fac) {
    	OMElement valEl = fac.createOMElement(new QName(DBSFields.VALIDATE_LONG_RANGE));
		if (validator.isHasMin()) {
			valEl.addAttribute(DBSFields.MINIMUM, String.valueOf(validator.getMinimum()), null);
		}
		if (validator.isHasMax()) {
			valEl.addAttribute(DBSFields.MAXIMUM, String.valueOf(validator.getMaximum()), null);
		}
		queryParamEl.addChild(valEl);
    }

    private static void serializeDoubleRangeValidator(DoubleRangeValidator validator,
                                                      OMElement queryParamEl, OMFactory fac) {
    	OMElement valEl = fac.createOMElement(new QName(DBSFields.VALIDATE_DOUBLE_RANGE));
		if (validator.isHasMin()) {
			valEl.addAttribute(DBSFields.MINIMUM, String.valueOf(validator.getMinimum()), null);
		}
		if (validator.isHasMax()) {
			valEl.addAttribute(DBSFields.MAXIMUM, String.valueOf(validator.getMaximum()), null);
		}
		queryParamEl.addChild(valEl);
    }

    private static void serializeCustomValidator(Validator validator,
                                                 OMElement queryParamEl, OMFactory fac) {
    	OMElement valEl = fac.createOMElement(new QName(DBSFields.VALIDATE_CUSTOM));
    	valEl.addAttribute(DBSFields.CLASS, validator.getClass().getName(), null);
		queryParamEl.addChild(valEl);
    }
    
}
