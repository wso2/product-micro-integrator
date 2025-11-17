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

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.micro.integrator.dataservices.common.DBConstants;
import org.wso2.micro.integrator.dataservices.common.DBConstants.CarbonDatasource;
import org.wso2.micro.integrator.dataservices.common.DBConstants.DBSFields;
import org.wso2.micro.integrator.dataservices.common.DBConstants.DataSourceTypes;
import org.wso2.micro.integrator.dataservices.common.DBConstants.JNDI;
import org.wso2.micro.integrator.dataservices.common.DBConstants.QueryParamTypes;
import org.wso2.micro.integrator.dataservices.common.DBConstants.QueryTypes;
import org.wso2.micro.integrator.dataservices.common.DBConstants.RDBMS;
import org.wso2.micro.integrator.dataservices.common.DBConstants.ResultTypes;
import org.wso2.micro.integrator.dataservices.common.RDBMSUtils;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery.WithParam;
import org.wso2.micro.integrator.dataservices.core.DBUtils;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.description.config.Config;
import org.wso2.micro.integrator.dataservices.core.description.config.JNDIConfig;
import org.wso2.micro.integrator.dataservices.core.description.config.SQLCarbonDataSourceConfig;
import org.wso2.micro.integrator.dataservices.core.description.event.EventTrigger;
import org.wso2.micro.integrator.dataservices.core.engine.CallQuery;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.OutputElementGroup;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;
import org.wso2.micro.integrator.dataservices.core.engine.QueryParam;
import org.wso2.micro.integrator.dataservices.core.engine.Result;
import org.wso2.micro.integrator.dataservices.core.engine.SQLDialect;
import org.wso2.micro.integrator.dataservices.core.engine.StaticOutputElement;
import org.wso2.micro.integrator.dataservices.core.validation.Validator;
import org.wso2.micro.integrator.dataservices.core.validation.ValidatorExt;
import org.wso2.micro.integrator.dataservices.core.validation.standard.ArrayTypeValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.DoubleRangeValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.LengthValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.LongRangeValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.PatternValidator;
import org.wso2.micro.integrator.dataservices.core.validation.standard.ScalarTypeValidator;

import javax.sql.DataSource;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A factory class to create queries in a data service.
 */
public class QueryFactory {
	
	private QueryFactory() { }

	public static Query createQuery(DataService dataService, OMElement queryEl)
            throws DataServiceFault {
		Query query;
		String configId = getConfigId(queryEl);
		Config config = dataService.getConfig(configId);
		if (config == null) {
			throw new DataServiceFault("Invalid configId: " + configId + " in :- \n" + queryEl);
		}
		String sourceType = config.getType();
		if (DataSourceTypes.RDBMS.equals(sourceType)
				|| DataSourceTypes.JNDI.equals(sourceType)
				|| DataSourceTypes.CARBON.equals(sourceType)
				|| DataSourceTypes.CUSTOM_TABULAR.equals(sourceType)) {
			query = createSQLQuery(dataService, queryEl);
		} else if (DataSourceTypes.CSV.equals(sourceType)) {
			query = createCSVQuery(dataService, queryEl);
		} else if (DataSourceTypes.EXCEL.equals(sourceType)) {
			query = createExcelQuery(dataService, queryEl);
		} else if (DataSourceTypes.GDATA_SPREADSHEET.equals(sourceType)) {
			query = createGSpreadQuery(dataService, queryEl);
        } else if (DataSourceTypes.RDF.equals(sourceType)) {
        	query = createRdfFileQuery(dataService, queryEl);
        } else if (DataSourceTypes.SPARQL.equals(sourceType)) {
        	query = createSparqlEndpointQuery(dataService, queryEl);
		} else if (DataSourceTypes.MONGODB.equals(sourceType)) {
            query = createMongoQuery(dataService, queryEl);
        } else if (DataSourceTypes.WEB.equals(sourceType)) {
			query = createWebQuery(dataService, queryEl);
		} else if (DataSourceTypes.CUSTOM_QUERY.equals(sourceType)) {
			query = createCustomQuery(dataService, queryEl);
		} else if (DataSourceTypes.CASSANDRA.equals(sourceType)) {
            query = createCassandraQuery(dataService, queryEl);
        } else {
			throw new DataServiceFault("Invalid configType: " +
					sourceType + " in :- \n" + queryEl);
		}
		return query;
	}

	private static String getConfigId(OMElement queryEl) {
		String configId = queryEl.getAttributeValue(new QName(DBSFields.USE_CONFIG));
		if (configId == null) {
			configId = DBConstants.DEFAULT_CONFIG_ID;
		}
		return configId;
	}

	private static String getQueryId(OMElement queryEl) {
		return queryEl.getAttributeValue(new QName(DBSFields.ID));
	}

	private static String getCustomQuery(OMElement queryEl) {
		return ((OMElement) queryEl.getChildrenWithLocalName(
				DBSFields.EXPRESSION).next()).getText();
	}

    private static String getQueryVariable(OMElement queryEl) {
        return queryEl.getFirstChildWithName(
                new QName(DBConstants.WebDatasource.QUERY_VARIABLE)).getText();
    }

    private static String extractQueryInputNamespace(DataService dataService,
                                                     Result result, OMElement queryEl) {
    	String inputNamespace = queryEl.getAttributeValue(new QName(DBSFields.INPUT_NAMESPACE));
    	if (DBUtils.isEmptyString(inputNamespace)) {
    		if (result != null) {
    			inputNamespace = result.getNamespace();
    		}
    		if (DBUtils.isEmptyString(inputNamespace)) {
    			inputNamespace = dataService.getDefaultNamespace();
    		}
    	}
    	return inputNamespace;
    }

	private static RdfFileQuery createRdfFileQuery(DataService dataService,
                                                   OMElement queryEl) throws DataServiceFault {
		String queryId, configId, sparql, inputNamespace;
		EventTrigger[] eventTriggers;
		Result result;
		try {
		    queryId = getQueryId(queryEl);
		    configId = getConfigId(queryEl);
		    sparql = queryEl.getFirstChildWithName(new QName(DBSFields.SPARQL)).getText();
		    eventTriggers = getEventTriggers(dataService, queryEl);
		    result = getResultFromQueryElement(dataService, queryEl);
		    inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
		} catch (Exception e) {
			throw new DataServiceFault(e, "Error in parsing SPARQL query element");
		}
		RdfFileQuery query = new RdfFileQuery(dataService, queryId, configId,
                                              sparql, getQueryParamsFromQueryElement(queryEl), result,
                                              eventTriggers[0], eventTriggers[1],
                                              extractAdvancedProps(queryEl), inputNamespace);
		return query;
	}

	private static SparqlEndpointQuery createSparqlEndpointQuery(DataService dataService,
                                                                 OMElement queryEl) throws DataServiceFault {
		String queryId, configId, sparql, inputNamespace;
		EventTrigger[] eventTriggers;
		Result result;
		try {
		    queryId = getQueryId(queryEl);
		    configId = getConfigId(queryEl);
		    sparql = queryEl.getFirstChildWithName(new QName(DBSFields.SPARQL)).getText();
		    eventTriggers = getEventTriggers(dataService, queryEl);
		    result = getResultFromQueryElement(dataService, queryEl);
		    inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
		} catch (Exception e) {
			throw new DataServiceFault(e, "Error in parsing SPARQL query element");
		}
		SparqlEndpointQuery query = new SparqlEndpointQuery(dataService, queryId, configId,
                                                            sparql, getQueryParamsFromQueryElement(queryEl), result,
                                                            eventTriggers[0], eventTriggers[1],
                                                            extractAdvancedProps(queryEl), inputNamespace);
		return query;
	}

    private static MongoQuery createMongoQuery(DataService dataService,
                                               OMElement queryEl) throws DataServiceFault {
        String queryId, configId, expression, inputNamespace;
        EventTrigger[] eventTriggers;
        Result result;
        try {
            queryId = getQueryId(queryEl);
            configId = getConfigId(queryEl);
            expression = ((OMElement) queryEl.getChildrenWithLocalName(
                    DBSFields.EXPRESSION).next()).getText();
            eventTriggers = getEventTriggers(dataService, queryEl);
            result = getResultFromQueryElement(dataService, queryEl);
            inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
        } catch (Exception e) {
            throw new DataServiceFault(e, "Error in parsing Mongo query element");
        }
        MongoQuery query = new MongoQuery(dataService, queryId, configId, expression,
                                          getQueryParamsFromQueryElement(queryEl), result,
                                          eventTriggers[0], eventTriggers[1],
                                          extractAdvancedProps(queryEl), inputNamespace);
        return query;
    }

	/**
	 * This method returns the input and output event triggers in a query.
	 * Returns [0] - Input EventTrigger, [1] - Output EventTrigger.
	 * @param dataService corresponding dataservice object
     * @param queryEl dataservices query element
     * @see EventTrigger
     * @return array of Event Trigger objects
	 */
	private static EventTrigger[] getEventTriggers(DataService dataService, OMElement queryEl) {
		EventTrigger inputEventTrigger = null;
		EventTrigger outputEventTrigger = null;
		String inTrigId = queryEl.getAttributeValue(new QName(DBSFields.INPUT_EVENT_TRIGGER));
		String outTrigId = queryEl.getAttributeValue(new QName(DBSFields.OUTPUT_EVENT_TRIGGER));
		if (inTrigId != null) {
			inputEventTrigger = dataService.getEventTrigger(inTrigId);
		}
		if (outTrigId != null) {
			outputEventTrigger = dataService.getEventTrigger(outTrigId);
		}
		return new EventTrigger[] { inputEventTrigger, outputEventTrigger };
	}

	private static Map<String, String> extractAdvancedProps(OMElement queryEl) {
		Map<String, String> advancedProperties;
		OMElement propsEl = queryEl.getFirstChildWithName(new QName(DBSFields.PROPERTIES));
		/* extract advanced query properties */
		if (propsEl != null) {
			advancedProperties = RDBMSUtils.convertConfigPropsFromV2toV3(DBUtils.extractProperties(propsEl));
		} else {
			advancedProperties = new HashMap<String, String>();
		}
		return advancedProperties;
	}

	private static String[] extractKeyColumns(OMElement queryEl) {
		String keyColumnsStr = queryEl.getAttributeValue(new QName(DBSFields.KEY_COLUMNS));
		if (!DBUtils.isEmptyString(keyColumnsStr)) {
			String[] columns = keyColumnsStr.split(",");
			for (int i = 0; i < columns.length; i++) {
				columns[i] = columns[i].trim();
			}
			return columns;
		} else {
		    return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Iterator<OMElement> getSQLQueryElements (OMElement queryEl) {
		return queryEl.getChildrenWithName(new QName(DBSFields.SQL));
	}

	private static String getDefaultSQLQuery (OMElement queryEl) {
		String defaultSQL = null;
		Iterator<OMElement> itr = getSQLQueryElements(queryEl);
		while (itr.hasNext()) {
			OMElement sqlQuery = itr.next();
			if (sqlQuery.getAttributeValue(new QName(DBSFields.DIALECT)) == null) {
				defaultSQL = sqlQuery.getText();
				break;
			}
        }
		return defaultSQL;
	}

	private static List<SQLDialect> getDialectList(OMElement queryEl) throws DataServiceFault {
		Iterator<OMElement> itr = getSQLQueryElements(queryEl);
		boolean isRepeated = false;
		List<SQLDialect> dialectList = new ArrayList<SQLDialect>();
		while (itr.hasNext()) {
			OMElement sqlQuery = itr.next();
			String sqlDialectValue = sqlQuery.getAttributeValue(new QName(DBSFields.DIALECT));
			Set<String> dialectSet = new HashSet<String>();
			Set<String> intersect = null;
			SQLDialect sqlDialect = new SQLDialect();
			if (sqlDialectValue != null) {
				String dbTypes[] = sqlDialectValue.split(",");
				for (String dbType : dbTypes) {
					dialectSet.add(dbType);
				}
				for (SQLDialect dialect : dialectList) {
					intersect = new TreeSet<String>(dialect.getSqlDialects());
					intersect.retainAll(dialectSet);
					if (!intersect.isEmpty()) {
						isRepeated = true;
					}
				}
				if (!isRepeated) {
					sqlDialect.setSqlDialects(dialectSet);
					sqlDialect.setSqlQuery(sqlQuery.getText());
					dialectList.add(sqlDialect);
				} else {
					Iterator<String> it = intersect.iterator();
                    StringBuilder builder = new StringBuilder();
					while (it.hasNext()) {
						builder.append(it.next());
                        if (it.hasNext()) {
                            builder.append(" ");
                        }
					}
					throw new DataServiceFault("SQL Dialect(s) repeated: " + builder.toString());
				}
			}
		}
		return dialectList;
	}

	public static String getSQLQueryForConnectionURL (OMElement queryEl,
                                                      String connectionURL) throws DataServiceFault {
		String driver = null;
		String sql = null;
		if (connectionURL != null) {
			String urlProp[] = connectionURL.split(":");
			if (urlProp.length > 2) {
				driver = urlProp[1];
			}
            List<SQLDialect> dialectList = getDialectList(queryEl);
            for (SQLDialect dialect : dialectList) {
                for (String dialectName : dialect.getSqlDialects()) {
                    if (driver != null && driver.equals(dialectName)) {
                        sql = dialect.getSqlQuery();
                        break;
                    }
                }
            }
        }
		if (sql == null) {
			sql = getDefaultSQLQuery(queryEl);
			if (sql == null) {
				throw new DataServiceFault("The query with the query element :-\n" +
						queryEl + "\n does not have a matching query dialect for the given " +
                        "data source, and also doesn't provide a default query");
			}
		}
		return sql;

	}

	private static String getSQLQueryForDatasource(OMElement queryEl,
                                                   DataService dataService,
                                                   String configId)
            throws DataServiceFault, SQLException, XMLStreamException {
        Connection con = null;
		Config config = dataService.getConfig(configId);
		// RDBMS data source
		String connectionURL = config.getProperty(RDBMS.URL);
        if (connectionURL == null) { // if generic rdbms url is null then check for XA data source url
            connectionURL = DBUtils.getConnectionURL4XADataSource(config);
            if (connectionURL == null) {
                String carbonDSURL = config.getProperty(CarbonDatasource.NAME);
                if (carbonDSURL != null) {
                    SQLCarbonDataSourceConfig carbonDSConfig =
                            (SQLCarbonDataSourceConfig) dataService.getConfig(configId);
                    try {
                        DataSource ds = carbonDSConfig.getDataSource();
                        if (ds != null) {
                            con = ds.getConnection();
                            try {
                                connectionURL = con.getMetaData().getURL();
                            } catch (Exception ignore) {
								/* some drivers may not support meta-data lookup */
							}
                        } else {
                            throw new DataServiceFault("Data source referred by the name '" +
                                    carbonDSConfig.getDataSourceName() + "' does not exist");
                        }
                    } finally {
                        if (con != null) {
                            con.close();
                        }
                    }
                }
                String jndiDataSource = config.getProperty(JNDI.RESOURCE_NAME);
                if (jndiDataSource != null) {
                    // JNDI data source
                    JNDIConfig jndiConfig = (JNDIConfig) dataService.getConfig(configId);
                    try {
                        con = jndiConfig.getDataSource().getConnection();
                        connectionURL = con.getMetaData().getURL();
                    } finally {
                        if(con != null) {
                            con.close();
                        }
                    }
                }
            }
        }
        return getSQLQueryForConnectionURL(queryEl, connectionURL);
	}

	private static SQLQuery createSQLQuery(DataService dataService, OMElement queryEl) throws DataServiceFault {
		String queryId, configId, sql, inputNamespace;
		boolean returnGeneratedKeys = false;
		boolean isReturnUpdatedRowCount = false;
		EventTrigger[] eventTriggers;
		String[] keyColumns;
		Result result;
		try {
			queryId = getQueryId(queryEl);
			configId = getConfigId(queryEl);
			sql = getSQLQueryForDatasource(queryEl, dataService, configId);
			eventTriggers = getEventTriggers(dataService, queryEl);
			String returnRowIdStr = queryEl.getAttributeValue(new QName(DBSFields.RETURN_GENERATED_KEYS));
			if (returnRowIdStr != null) {
				returnGeneratedKeys = Boolean.parseBoolean(returnRowIdStr);
			}
			String returnUpdatedRowCountStr = queryEl.getAttributeValue(new QName(DBSFields.RETURN_UPDATED_ROW_COUNT));
			if (null != returnUpdatedRowCountStr) {
				isReturnUpdatedRowCount = Boolean.parseBoolean(returnUpdatedRowCountStr);
			}
			keyColumns = extractKeyColumns(queryEl);
			result = getResultFromQueryElement(dataService, queryEl);
			inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
		} catch (XMLStreamException e) {
			throw new DataServiceFault(e, "Error in parsing SQL query element");
		} catch (SQLException e) {
			throw new DataServiceFault(e, DBConstants.FaultCodes.CONNECTION_UNAVAILABLE_ERROR, e.getMessage());
		}
		SQLQuery query = new SQLQuery(dataService, queryId, configId, returnGeneratedKeys, isReturnUpdatedRowCount,
                                      keyColumns, sql, getQueryParamsFromQueryElement(queryEl), result,
                                      eventTriggers[0], eventTriggers[1], extractAdvancedProps(queryEl),
                                      inputNamespace);

		return query;
	}

	private static CSVQuery createCSVQuery(DataService dataService,
                                           OMElement queryEl) throws DataServiceFault {
		String queryId, configId, inputNamespace;
		EventTrigger[] eventTriggers;
		Result result;
		try {
		    queryId = getQueryId(queryEl);
		    configId = getConfigId(queryEl);
		    eventTriggers = getEventTriggers(dataService, queryEl);
		    result = getResultFromQueryElement(dataService, queryEl);
		    inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
		} catch (Exception e) {
			throw new DataServiceFault(e, "Error in parsing CSV query element");
		}
		CSVQuery query = new CSVQuery(dataService, queryId,
                                      getQueryParamsFromQueryElement(queryEl), configId, result,
                                      eventTriggers[0], eventTriggers[1],
                                      extractAdvancedProps(queryEl), inputNamespace);
		return query;
	}

	private static CassandraQuery createCassandraQuery(DataService dataService,
                                                       OMElement queryEl) throws DataServiceFault {
        String queryId, configId, inputNamespace, queryExpr;
        EventTrigger[] eventTriggers;
        Result result;
        try {
            queryId = getQueryId(queryEl);
            configId = getConfigId(queryEl);
            queryExpr = getCassandraQueryExpression(queryEl);
            eventTriggers = getEventTriggers(dataService, queryEl);
            result = getResultFromQueryElement(dataService, queryEl);
            inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
        } catch (Exception e) {
            throw new DataServiceFault(e, "Error in parsing Cassandra query "
                    + "element: " + queryEl.toString() + "\n - " + e.getMessage());
        }
        CassandraQuery query = new CassandraQuery(dataService, queryId, queryExpr,
                                                  getQueryParamsFromQueryElement(queryEl), result, configId,
                                                  eventTriggers[0], eventTriggers[1],
                                                  extractAdvancedProps(queryEl), inputNamespace);
        return query;
    }

	private static String getCassandraQueryExpression(OMElement queryEl) throws DataServiceFault {
	    OMElement el = queryEl.getFirstChildWithName(new QName(DBSFields.EXPRESSION));
	    if (el == null) {
	        throw new DataServiceFault("A Cassandra query must contain a 'expression' element");
	    }
	    return el.getText();
	}

	private static CustomQueryBasedDSQuery createCustomQuery(DataService dataService,
                                                             OMElement queryEl) throws DataServiceFault {
		String queryId, configId, inputNamespace, expr;
        EventTrigger[] eventTriggers;
        Result result;
        try {
        	expr = getCustomQuery(queryEl);
            queryId = getQueryId(queryEl);
            configId = getConfigId(queryEl);
            eventTriggers = getEventTriggers(dataService, queryEl);
            result = getResultFromQueryElement(dataService, queryEl);
		    inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
        } catch (Exception e) {
            throw new DataServiceFault(e, "Error in passing Web query element");
        }
		CustomQueryBasedDSQuery query = new CustomQueryBasedDSQuery(dataService, queryId,
                                                                    getQueryParamsFromQueryElement(queryEl), result, configId,
                                                                    eventTriggers[0], eventTriggers[1],
                                                                    extractAdvancedProps(queryEl), inputNamespace, expr);
        return query;
	}

    private static WebQuery createWebQuery(DataService dataService,
                                           OMElement queryEl) throws DataServiceFault {
        String queryId, configId, inputNamespace;
        EventTrigger[] eventTriggers;
        Result result;
        try {
            queryId = getQueryId(queryEl);
            configId = getConfigId(queryEl);
            eventTriggers = getEventTriggers(dataService, queryEl);
            result = getResultFromQueryElement(dataService, queryEl);
		    inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
        } catch (Exception e) {
            throw new DataServiceFault(e, "Error in passing Web query element");
        }
		WebQuery query = new WebQuery(dataService, queryId,
                                      getQueryParamsFromQueryElement(queryEl), configId, result,
                                      eventTriggers[0], eventTriggers[1],
                                      extractAdvancedProps(queryEl), getQueryVariable(queryEl),
                                      inputNamespace);
        return query;
    }


    private static ExcelQuery createExcelQuery(DataService dataService,
                                               OMElement queryEl) throws DataServiceFault {
		String queryId, configId, workbookName, inputNamespace;
		int startingRow, maxRowCount, headerRow;
		boolean hasHeader;
		EventTrigger[] eventTriggers;
		Result result;
		try {
		    queryId = getQueryId(queryEl);
		    configId = getConfigId(queryEl);
		    OMElement excelEl = queryEl.getFirstChildWithName(new QName(DBSFields.EXCEL));
		    workbookName = excelEl.getFirstChildWithName(
		    		new QName(DBConstants.Excel.WORKBOOK_NAME)).getText();

		    OMElement tmpStartingRow = excelEl.getFirstChildWithName(
		    		new QName(DBConstants.Excel.STARTING_ROW));
			if (tmpStartingRow != null) {
				startingRow = Integer.parseInt(tmpStartingRow.getText());
			} else {
				startingRow = 1;
			}

			OMElement tmpMaxRowCount = excelEl.getFirstChildWithName(
					new QName(DBConstants.Excel.MAX_ROW_COUNT));
			if (tmpMaxRowCount != null) {
				maxRowCount = Integer.parseInt(tmpMaxRowCount.getText());
			} else {
				maxRowCount = -1;
			}

			OMElement tmpHasHeader = excelEl.getFirstChildWithName(
					new QName(DBConstants.Excel.HAS_HEADER));
			if (tmpHasHeader != null) {
				hasHeader = Boolean.parseBoolean(tmpHasHeader.getText());
			} else {
				hasHeader = false;
			}

            OMElement tmpHeaderRow = excelEl.getFirstChildWithName(
                    new QName(DBConstants.Excel.HEADER_ROW));
            if (tmpHeaderRow != null) {
                headerRow = Integer.parseInt(tmpHeaderRow.getText());
            } else {
                headerRow = 1;
            }

			eventTriggers = getEventTriggers(dataService, queryEl);

			result = getResultFromQueryElement(dataService, queryEl);
		    inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
		} catch (Exception e) {
			throw new DataServiceFault(e, "Error in parsing GSpread query element");
		}
		ExcelQuery query = new ExcelQuery(dataService, queryId,
                                          getQueryParamsFromQueryElement(queryEl), configId,
                                          workbookName, hasHeader, startingRow, headerRow, maxRowCount, result,
                                          eventTriggers[0], eventTriggers[1],
                                          extractAdvancedProps(queryEl), inputNamespace);
		return query;
	}

	private static GSpreadQuery createGSpreadQuery(DataService dataService,
                                                   OMElement queryEl) throws DataServiceFault {
		String queryId, configId, inputNamespace;
		int worksheetNumber, startingRow, maxRowCount, headerRow;
		boolean hasHeader;
		EventTrigger[] eventTriggers;
		Result result;
		try {
		    queryId = getQueryId(queryEl);
		    configId = getConfigId(queryEl);
		    OMElement gspreadEl = queryEl.getFirstChildWithName(
		    		new QName(DBSFields.GSPREAD));

		    worksheetNumber = Integer.parseInt(gspreadEl.getFirstChildWithName(
		    		new QName(DBConstants.GSpread.WORKSHEET_NUMBER)).getText());

		    OMElement tmpStartingRow = gspreadEl.getFirstChildWithName(
		    		new QName(DBConstants.GSpread.STARTING_ROW));
			if (tmpStartingRow != null) {
				startingRow = Integer.parseInt(tmpStartingRow.getText());
			} else {
				startingRow = 1;
			}

			OMElement tmpMaxRowCount = gspreadEl.getFirstChildWithName(
					new QName(DBConstants.GSpread.MAX_ROW_COUNT));
			if (tmpMaxRowCount != null) {
				maxRowCount = Integer.parseInt(tmpMaxRowCount.getText());
			} else {
				maxRowCount = -1;
			}

			OMElement tmpHasHeader = gspreadEl.getFirstChildWithName(
					new QName(DBConstants.GSpread.HAS_HEADER));
			if (tmpHasHeader != null) {
				hasHeader = Boolean.parseBoolean(tmpHasHeader.getText());
			} else {
				hasHeader = false;
			}

            OMElement tmpHeaderRow = gspreadEl.getFirstChildWithName(
                    new QName(DBConstants.GSpread.HEADER_ROW));
            if (tmpHeaderRow != null) {
                headerRow = Integer.parseInt(tmpHeaderRow.getText());
            } else {
                headerRow = 1;
            }

			eventTriggers = getEventTriggers(dataService, queryEl);

			result = getResultFromQueryElement(dataService, queryEl);
		    inputNamespace = extractQueryInputNamespace(dataService, result, queryEl);
		} catch (Exception e) {
			throw new DataServiceFault(e, "Error in parsing GSpread query element");
		}
		GSpreadQuery query = new GSpreadQuery(dataService, queryId,
                                              getQueryParamsFromQueryElement(queryEl), configId,
                                              worksheetNumber, hasHeader, startingRow, headerRow, maxRowCount, result,
                                              eventTriggers[0], eventTriggers[1],
                                              extractAdvancedProps(queryEl), inputNamespace);
		return query;
	}

	private static Result getResultFromQueryElement(DataService dataService, OMElement queryEl)
            throws DataServiceFault {
		OMElement resEl = queryEl.getFirstChildWithName(new QName(DBSFields.RESULT));
		if (resEl == null) {
			return null;
		}

		// Checking data source case sensitivity mode
		OMAttribute datasourceIDAttribute = queryEl.getAttribute(new QName(DBSFields.USE_CONFIG));
		boolean isResultSetFieldsCaseSensitive;
		if (datasourceIDAttribute != null) {
			isResultSetFieldsCaseSensitive =
					dataService.getConfig(datasourceIDAttribute.getAttributeValue()).isResultSetFieldsCaseSensitive();
		} else {
			isResultSetFieldsCaseSensitive =
					dataService.getConfigs().values().iterator().next().isResultSetFieldsCaseSensitive();
		}

		String namespace = resEl.getAttributeValue(new QName(DBSFields.DEFAULT_NAMESPACE));
		if (namespace == null || namespace.trim().isEmpty()) {
            boolean enableEmptyDefaultNamespace = Boolean.parseBoolean(resEl.getAttributeValue(
                    new QName(DBSFields.ENABLE_SETTING_EMPTY_DEFAULT_NAMESPACE)));
            if (enableEmptyDefaultNamespace) {
                namespace = "";
            } else {
			    namespace = dataService.getDefaultNamespace();
            }
		}

        String xsltPath = resEl.getAttributeValue(new QName(DBSFields.XSLT_PATH));

        String outputType = resEl.getAttributeValue(new QName(DBSFields.OUTPUT_TYPE));
        int resultType = ResultTypes.XML;
        if (outputType == null || outputType.trim().length() == 0 || outputType.equals(
                DBSFields.RESULT_TYPE_XML)) {
            resultType = ResultTypes.XML;
        } else if (outputType.equals(DBSFields.RESULT_TYPE_RDF)) {
            resultType = ResultTypes.RDF;
        } else if (outputType.equals(DBSFields.RESULT_TYPE_JSON)) {
            resultType = ResultTypes.JSON;
        }

		Result result = new Result(xsltPath, resultType);

		boolean useColumnNumbers = false;
        String useColumnNumbersStr = resEl.getAttributeValue(new QName(DBSFields.USE_COLUMN_NUMBERS));
        if (!DBUtils.isEmptyString(useColumnNumbersStr)) {
        	useColumnNumbers = Boolean.parseBoolean(useColumnNumbersStr);
        }
        result.setUseColumnNumbers(useColumnNumbers);

        boolean escapeNonPrintableChar = false;
        String escapeNonPrintableCharStr = resEl.getAttributeValue(new QName(DBSFields.ESCAPE_NON_PRINTABLE_CHAR));
        if (!DBUtils.isEmptyString(escapeNonPrintableCharStr)) {
            escapeNonPrintableChar = Boolean.parseBoolean(escapeNonPrintableCharStr);
        }
        result.setEscapeNonPrintableChar(escapeNonPrintableChar);

		if (result.getResultType() == ResultTypes.XML) {
			populateXMLResult(result, dataService, resEl, namespace, isResultSetFieldsCaseSensitive);
		} else if (result.getResultType() == ResultTypes.RDF) {
			populateRDFResult(result, dataService, resEl, namespace, isResultSetFieldsCaseSensitive);
		} else if (result.getResultType() == ResultTypes.JSON) {
			populateJSONResult(result, dataService, resEl,
			                   calculateJSONXMLQueryNS(namespace, queryEl.getAttributeValue(new QName(DBSFields.ID))),
			                   isResultSetFieldsCaseSensitive);
		}

		return result;
	}

	private static String calculateJSONXMLQueryNS(String originalNS, String queryName) {
	    if (!originalNS.endsWith("/")) {
	        originalNS += "/";
	    }
	    return originalNS + queryName;
	}

	private static void populateRDFResult(Result result, DataService dataService, OMElement resEl,
                                          String namespace, boolean isCaseSensitive) throws DataServiceFault {
	    result.setElementName(DBSFields.RDF);
	    result.setRowName(DBSFields.RDF_DESCRIPTION);
	    result.setNamespace(namespace);
	    String rdfBaseURI = resEl.getAttributeValue(new QName(DBSFields.RDF_BASE_URI));
        result.setRDFBaseURI(rdfBaseURI);
        /* create default wrapping output element group for the result */
        OMElement groupEl = createElement(DBSFields.ELEMENT);
        addRHSChildrenToLHS(groupEl, resEl);
        /* create output element group and set it to the result */
        OutputElementGroup defGroup = createOutputElementGroup(dataService, groupEl,
                                                               namespace, result, 0, false, isCaseSensitive);
        result.setDefaultElementGroup(defGroup);
	}

    private static void populateXMLResult(Result result, DataService dataService, OMElement resEl,
                                          String namespace, boolean isCaseSensitive) throws DataServiceFault {
        result.setElementName(resEl.getAttributeValue(new QName(DBSFields.ELEMENT)));
        result.setRowName(resEl.getAttributeValue(new QName(DBSFields.ROW_NAME)));
        result.setNamespace(namespace);
        /* create default wrapping output element group for the result */
        OMElement groupEl = createElement(DBSFields.ELEMENT);
        addRHSChildrenToLHS(groupEl, resEl);
        /* create output element group and set it to the result */
        OutputElementGroup defGroup = createOutputElementGroup(dataService, groupEl,
                                                               namespace, result, 0, false, isCaseSensitive);
        result.setDefaultElementGroup(defGroup);
    }

    private static class JSONCallQueryParamsInfo {

        private List<String[]> withParams = new ArrayList<String[]>();

        public JSONCallQueryParamsInfo(String value) throws DataServiceFault {
            value = value.trim();
            String[] wpTokens = value.split(",");
            for (String wp : wpTokens) {
                this.processWP(wp);
            }
        }

        private void processWP(String wp) throws DataServiceFault {
            String[] tokens = wp.split("->");
            if (tokens.length != 2) {
                throw new DataServiceFault("The nested query call parameters should be separated "
                        + "by a single '->', found: " + wp);
            }
            tokens[0] = tokens[0].trim();
            tokens[1] = tokens[1].trim();
            if (!tokens[0].startsWith("$")) {
                throw new DataServiceFault(
                        "The nested query call parameter LHS should be a reference to an existing column/query-param, "
                        + "which should start with a $, found: " + tokens[0]);
            }
            this.withParams.add(new String[] { tokens[0].substring(1), tokens[1] });
        }

        public List<String[]> getWithParams() {
            return withParams;
        }

    }

    private static class ResultEntryColumnInfo {

        private String name;

        private String dataType;

        private String requiredRoles;

        private String isOptional;

        public ResultEntryColumnInfo(String value) throws DataServiceFault {
            // {"name":"$jack(type:integer;requiredRoles:admin,role1)"
            value = value.trim();
            int index1 = value.indexOf('(');
            if (!value.startsWith("$")) {
                throw new DataServiceFault("A result entry column value must start with "
                        + "$, found: " + value);
            }
            if (index1 == -1) {
                this.name = value.substring(1);
            } else {
                this.name = value.substring(1, index1);
                int index2 = value.lastIndexOf(")");
                if (index2 == -1) {
                    throw new DataServiceFault("A result entry column value with options must end "
                            + "with ')', found: " + value);
                }
                String options = value.substring(index1 + 1, index2);
                String[] optionsTokens = options.split(";");
                for (String optionsToken : optionsTokens) {
                    this.processOptionsToken(optionsToken);
                }
            }
        }

        private void processOptionsToken(String optionsToken) throws DataServiceFault {
            String[] tokens = optionsToken.split(":");
            if (tokens.length != 2) {
                throw new DataServiceFault("An option section must be separate by a single ':', "
                        + "found: " + optionsToken);
            }
            String name = tokens[0].trim();
            String value = tokens[1].trim();
            if (DBSFields.REQUIRED_ROLES.equals(name)) {
                this.requiredRoles = value;
            } else if (DBSFields.TYPE.equals(name)) {
                this.dataType = value;
            } else if (DBSFields.OPTIONAL.equals(name)) {
                this.isOptional = value;
            } else {
                throw new DataServiceFault("Unrecognized option type '" + name + "', "
                        + "found: " + name);
            }
        }

        public String getName() {
            return name;
        }

        public String getRequiredRoles() {
            return requiredRoles;
        }

        public String getDataType() {
            return dataType;
        }

        public String getIsOptional() {
            return isOptional;
        }
    }

    private static ResultEntryColumnInfo extractJSONResultColumnInfo(
            String value) throws DataServiceFault {
        return new ResultEntryColumnInfo(value);
    }

    private static JSONCallQueryParamsInfo extractJSONCallQueryParamInfo(
            String value) throws DataServiceFault {
        return new JSONCallQueryParamsInfo(value);
    }

    private static void addJSONMappingResultRecord(JSONObject obj,
            OMElement parentEl) throws DataServiceFault {
        Object item;
        for (String name : JSONObject.getNames(obj)) {
            try {
                item = obj.get(name);
            } catch (JSONException e) {
                throw new DataServiceFault(e, "Unexpected JSON parsing error: " + e.getMessage());
            }
            if (name.startsWith("@")) {
                processJSONMappingCallQuery(name.substring(1), item, parentEl);
            } else {
                processJSONMappingResultColumn(name, item, parentEl);
            }
        }
    }

    private static void processJSONMappingCallQuery(String name, Object item,
            OMElement parentEl) throws DataServiceFault {
        OMElement cqEl = createElement(DBSFields.CALL_QUERY);
        cqEl.addAttribute(DBSFields.HREF, name, null);
        parentEl.addChild(cqEl);
        JSONCallQueryParamsInfo info = extractJSONCallQueryParamInfo(item.toString());
        OMElement wpEl;
        for (String[] wp : info.getWithParams()) {
            wpEl = createElement(DBSFields.WITH_PARAM);
            wpEl.addAttribute(DBSFields.NAME, wp[0], null);
            wpEl.addAttribute(DBSFields.QUERY_PARAM, wp[1], null);
            cqEl.addChild(wpEl);
        }
    }

    private static void processJSONMappingResultColumn(String name, Object item,
            OMElement parentEl) throws DataServiceFault {
        OMElement childEl = createElement(DBSFields.ELEMENT);
        parentEl.addChild(childEl);
        childEl.addAttribute(DBSFields.NAME, name, null);
        if (item instanceof JSONObject) {
            addJSONMappingResultRecord((JSONObject) item, childEl);
        } else if (item instanceof JSONArray) {
            throw new DataServiceFault("A JSON Array cannot be contained in the result records");
        } else {
            ResultEntryColumnInfo info = extractJSONResultColumnInfo(item.toString());
            childEl.addAttribute(DBSFields.COLUMN, info.getName(), null);
            if (info.getDataType() != null) {
                childEl.addAttribute(DBSFields.XSD_TYPE, info.getDataType(), null);
            }
            if (info.getRequiredRoles() != null) {
                childEl.addAttribute(DBSFields.REQUIRED_ROLES, info.getRequiredRoles(), null);
            }
            if (info.getIsOptional() != null) {
                childEl.addAttribute(DBSFields.OPTIONAL, info.getIsOptional(), null);
            }
        }
    }

    public static OMElement getJSONResultFromText(String jsonMapping) throws DataServiceFault {
        try {
            OMElement resultEl = createElement(DBSFields.RESULT);
            JSONObject resultObj = new JSONObject(jsonMapping);
            String[] topLevelNames = JSONObject.getNames(resultObj);
            if (topLevelNames == null || topLevelNames.length != 1) {
                throw new DataServiceFault("There must be exactly 1 top level object in the JSON mapping, "
                        + "found " + (topLevelNames != null ? topLevelNames.length : 0));
            }
            String wrapperName = topLevelNames[0];
            String rowName = null;
            Object rootObj = resultObj.get(wrapperName);
            if (rootObj instanceof JSONArray) {
                throw new DataServiceFault("The top level object cannot be an array");
            } else if (rootObj instanceof JSONObject) {
                String[] secondLevelNames = JSONObject.getNames((JSONObject) rootObj);
                if (secondLevelNames.length == 1) {
                    Object obj = ((JSONObject) rootObj).get(secondLevelNames[0]);
                    if (obj instanceof JSONArray) {
                        rowName = secondLevelNames[0];
                        JSONArray array = (JSONArray) obj;
                        if (array.length() != 1) {
                            throw new DataServiceFault("The JSON array should be of size 1, found " +
                                    array.length());
                        }
                        obj = array.get(0);
                        if (!(obj instanceof JSONObject)) {
                            throw new DataServiceFault("The JSON array element must be a JSON Object");
                        }
                        addJSONMappingResultRecord((JSONObject) obj, resultEl);
                    } else {
                        addJSONMappingResultRecord((JSONObject) rootObj, resultEl);
                    }
                } else {
                    addJSONMappingResultRecord((JSONObject) rootObj, resultEl);
                }
            } else {
                throw new DataServiceFault("The top level object cannot be a simple type");
            }
            resultEl.addAttribute(DBSFields.ELEMENT, wrapperName, null);
            if (rowName != null) {
                resultEl.addAttribute(DBSFields.ROW_NAME, rowName, null);
            }
            return resultEl;
        } catch (DataServiceFault e) {
            throw e;
        } catch (Exception e) {
            throw new DataServiceFault(e, "Error in parsing JSON result mapping: " + e.getMessage());
        }
    }

	private static void populateJSONResult(Result result, DataService dataService, OMElement resultEl, String namespace,
                                           boolean isCaseSensitive) throws DataServiceFault {
	    /* create the XML mapping from the JSON mapping */
		resultEl = getJSONResultFromText(resultEl.getText());
		result.setResultType(ResultTypes.XML);
		/* process the XML mapping */
		populateXMLResult(result, dataService, resultEl, namespace, isCaseSensitive);
	}

	private static OMElement createElement(String name) {
		OMFactory factory = DBUtils.getOMFactory();
		return factory.createOMElement(new QName(name));
	}

	@SuppressWarnings("unchecked")
	private static void addRHSChildrenToLHS(OMElement lhs, OMElement rhs) {
		Iterator<OMElement> itr = rhs.getChildElements();
		OMElement el;
		while (itr.hasNext()) {
			el = itr.next();
			lhs.addChild(el);
		}
	}

	/**
	 * Checks if the current element is an element group / element with child elements.
	 * @param el The element to be checked
	 * @return True if the element is an element group, else False
	 */
	private static boolean isElementGroup(OMElement el) {
		if (el.getQName().getLocalPart().equals(DBSFields.ELEMENT)) {
			/* if the element only has a name, it must be an element group */
			return (el.getAttributeValue(new QName(DBSFields.COLUMN)) == null
					&& el.getAttributeValue(new QName(DBSFields.QUERY_PARAM)) == null
					&& el.getAttributeValue(new QName(DBSFields.VALUE)) == null
                    && el.getAttributeValue(new QName(DBSFields.RDF_REF_URI)) == null);
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private static OutputElementGroup createOutputElementGroup(DataService dataService, OMElement groupEl,
                                                               String parentNamespace, Result parentResult, int level, boolean optionalOverrideCurrent, boolean isCaseSensitive) throws
                                                                                                                                                                                 DataServiceFault {
		String name = groupEl.getAttributeValue(new QName(DBSFields.NAME));
		String namespace = groupEl.getAttributeValue(new QName(DBSFields.NAMESPACE));
        String arrayName = groupEl.getAttributeValue(new QName("arrayName"));
		int resultType = parentResult.getResultType();
		if (DBUtils.isEmptyString(namespace)) {
			namespace = parentNamespace;
		}
		Set<String> requiredRoles = extractRequiredRoles(groupEl);
		OutputElementGroup elGroup = new OutputElementGroup(name, namespace, requiredRoles, arrayName);
		elGroup.setParentResult(parentResult);
		QName elQName = new QName(DBSFields.ELEMENT);
		QName attrQName = new QName(DBSFields.ATTRIBUTE);
		QName cqQName = new QName(DBSFields.CALL_QUERY);
		Iterator<OMElement> resElItr = groupEl.getChildElements();
		OMElement el;
		boolean targetOptionalOverride = (level == 0) && (parentResult.getRowName() == null || "".equals(parentResult.getRowName()));
		while (resElItr.hasNext()) {
			el = resElItr.next();
			if (el.getQName().equals(elQName) && isElementGroup(el)) {
                elGroup.addOutputElementGroupEntry(createOutputElementGroup(dataService, el,
                        namespace, parentResult, ++level, targetOptionalOverride, isCaseSensitive));
			} else if (el.getQName().equals(elQName)) {
				elGroup.addElementEntry(createStaticOutputElement(dataService, el, namespace,
                        resultType, targetOptionalOverride, isCaseSensitive));
			} else if (el.getQName().equals(attrQName)) {
				elGroup.addAttributeEntry(createStaticOutputElement(dataService, el, namespace,
                        resultType, targetOptionalOverride, isCaseSensitive));
			} else if (el.getQName().equals(cqQName)) {
				CallQuery callQuery = createCallQuery(dataService, el, targetOptionalOverride);
				elGroup.addCallQueryEntry(callQuery);
				callQuery.setOptionalOverride(targetOptionalOverride);
			}
		}
		elGroup.setOptionalOverride(optionalOverrideCurrent);
		return elGroup;
	}

	@SuppressWarnings("unchecked")
	private static List<QueryParam> getQueryParamsFromQueryElement(
			OMElement queryEl) throws DataServiceFault {
		ArrayList<QueryParam> queryParams = new ArrayList<QueryParam>();

		Iterator<OMElement> paramItr = queryEl.getChildrenWithName(new QName(DBSFields.PARAM));
		OMElement paramEl;
		String name, sqlType, type, paramType, ordinalStr, defaultValue, structType;
		int ordinal, currentTmpOrdinal = 0;
		boolean forceDefault, optional = false;
		while (paramItr.hasNext()) {
			forceDefault  = false;
			optional  = false;
			paramEl = paramItr.next();
			name = paramEl.getAttributeValue(new QName(DBSFields.NAME));
            if (name != null) {
                name = name.trim();
            }
            defaultValue = paramEl.getAttributeValue(new QName(DBSFields.DEFAULT_VALUE));
			ordinalStr = paramEl.getAttributeValue(new QName(DBSFields.ORDINAL));
			/* handle <= 0 for backward compatibility */
			if (ordinalStr != null && (ordinal = Integer.parseInt(ordinalStr)) > 0) {
				currentTmpOrdinal = Math.max(ordinal, currentTmpOrdinal);
			} else {
				currentTmpOrdinal++;
				ordinal = currentTmpOrdinal;
			}
			sqlType = paramEl.getAttributeValue(new QName(DBSFields.SQL_TYPE)).toUpperCase();
			sqlType = (sqlType == null || sqlType.trim().length() == 0) ? sqlType :  sqlType.toUpperCase();
			type = paramEl.getAttributeValue(new QName(DBSFields.TYPE));
			type = (type == null || type.trim().length() == 0) ? QueryTypes.IN :  type.toUpperCase();
			paramType = paramEl.getAttributeValue(new QName(DBSFields.PARAM_TYPE));
			paramType = (paramType == null || paramType.trim().length() == 0) ? QueryParamTypes.SCALAR :
					paramType.toUpperCase();
			if (paramEl.getAttributeValue(new QName(DBSFields.FORCED_DEFAULT)) != null) {
			    forceDefault = Boolean.parseBoolean(paramEl.getAttributeValue(new QName(DBSFields.FORCED_DEFAULT)));
			}
			if (paramEl.getAttributeValue(new QName(DBSFields.OPTIONAL)) != null) {
				optional = Boolean.parseBoolean(paramEl.getAttributeValue(new QName(DBSFields.OPTIONAL)));
			}
			/* retrieve validators */
			List<Validator> validators = getValidators(paramType, paramEl);
            /* retrieve struct type  */
            structType = paramEl.getAttributeValue(new QName(DBSFields.STRUCT_TYPE));
			queryParams.add(new QueryParam(name, sqlType, type, paramType, ordinal,
                                           defaultValue == null ? null : new ParamValue(defaultValue), structType,
                                           validators, forceDefault, optional));
		}

		return queryParams;
	}

	private static List<Validator> getValidators(String paramType,
                                                 OMElement paramEl) throws DataServiceFault {
		/* add basic validators to check scalar, array etc.. */
		List<Validator> validators = new ArrayList<Validator>();
		if (paramType.equals("SCALAR")) {
			validators.add(ScalarTypeValidator.getInstance());
		} else if (paramType.equals("ARRAY")) {
			validators.add(ArrayTypeValidator.getInstance());
		}
		/* add specific validators as requested */
		OMElement valEl = paramEl.getFirstChildWithName(new QName(DBSFields.VALIDATE_LONG_RANGE));
		if (valEl != null) {
			validators.add(getLongRangeValidator(valEl));
		}
		valEl = paramEl.getFirstChildWithName(new QName(DBSFields.VALIDATE_DOUBLE_RANGE));
		if (valEl != null) {
			validators.add(getDoubleRangeValidator(valEl));
		}
		valEl = paramEl.getFirstChildWithName(new QName(DBSFields.VALIDATE_LENGTH));
		if (valEl != null) {
			validators.add(getLengthValidator(valEl));
		}
		valEl = paramEl.getFirstChildWithName(new QName(DBSFields.VALIDATE_PATTERN));
		if (valEl != null) {
			validators.add(getPatternValidator(valEl));
		}
		/* custom validator */
		valEl = paramEl.getFirstChildWithName(new QName(DBSFields.VALIDATE_CUSTOM));
		if (valEl != null) {
			validators.add(getCustomValidator(valEl));
		}
		return validators;
	}

	private static LongRangeValidator getLongRangeValidator(OMElement valEl) {
		long minimum = 0, maximum = 0;
		boolean hasMin = false, hasMax = false;
		String minStr = valEl.getAttributeValue(new QName(DBSFields.MINIMUM));
		if (minStr != null) {
			minimum = Long.parseLong(minStr);
			hasMin = true;
		}
		String maxStr = valEl.getAttributeValue(new QName(DBSFields.MAXIMUM));
		if (maxStr != null) {
			maximum = Long.parseLong(maxStr);
			hasMax = true;
		}
		LongRangeValidator validator = new LongRangeValidator(minimum, maximum, hasMin, hasMax);
		return validator;
	}

	private static DoubleRangeValidator getDoubleRangeValidator(OMElement valEl) {
		double minimum = 0.0, maximum = 0.0;
		boolean hasMin = false, hasMax = false;
		String minStr = valEl.getAttributeValue(new QName(DBSFields.MINIMUM));
		if (minStr != null) {
			minimum = Double.parseDouble(minStr);
			hasMin = true;
		}
		String maxStr = valEl.getAttributeValue(new QName(DBSFields.MAXIMUM));
		if (maxStr != null) {
			maximum = Double.parseDouble(maxStr);
			hasMax = true;
		}
		DoubleRangeValidator validator = new DoubleRangeValidator(minimum, maximum, hasMin, hasMax);
		return validator;
	}

	private static LengthValidator getLengthValidator(OMElement valEl) {
		int minimum = 0, maximum = 0;
		boolean hasMin = false, hasMax = false;
		String minStr = valEl.getAttributeValue(new QName(DBSFields.MINIMUM));
		if (minStr != null) {
			minimum = Integer.parseInt(minStr);
			hasMin = true;
		}
		String maxStr = valEl.getAttributeValue(new QName(DBSFields.MAXIMUM));
		if (maxStr != null) {
			maximum = Integer.parseInt(maxStr);
			hasMax = true;
		}
		LengthValidator validator = new LengthValidator(minimum, maximum, hasMin, hasMax);
		return validator;
	}

	private static PatternValidator getPatternValidator(OMElement valEl) {
		String regEx = valEl.getAttributeValue(new QName(DBSFields.PATTERN));
		PatternValidator validator = new PatternValidator(regEx);
		return validator;
	}

	@SuppressWarnings("unchecked")
	private static Validator getCustomValidator(OMElement valEl) throws DataServiceFault {
		String className = valEl.getAttributeValue(new QName(DBSFields.CLASS));
		try {
		    Class<Validator> clazz = (Class<Validator>) Class.forName(className);
		    Validator validator = clazz.newInstance();
		    if (validator instanceof ValidatorExt) {
		        Map<String, String> properties = extractAdvancedProps(valEl);
		        ((ValidatorExt) validator).init(properties);
		    }
		    return validator;
		} catch (Exception e) {
			throw new DataServiceFault(e, "Problem in creating custom validator class: " + className);
		}
	}

	private static StaticOutputElement createStaticOutputElement(DataService dataService,
                                                                 OMElement el, String namespace, int resultType, boolean optionalOverride, boolean isCaseSensitive)
            throws DataServiceFault {
		String name = el.getAttributeValue(new QName(DBSFields.NAME));
		String paramType = DBSFields.COLUMN;
		String param = el.getAttributeValue(new QName(paramType));
		if (param == null) {
			paramType = DBSFields.QUERY_PARAM;
			param = el.getAttributeValue(new QName(paramType));
			if (param == null) {
				paramType = DBSFields.VALUE;
				param = el.getAttributeValue(new QName(paramType));
				if (param == null) {
					paramType = DBSFields.RDF_REF_URI;
					param = el.getAttributeValue(new QName(paramType));
					if (param == null ) {
						throw new DataServiceFault(
								"Invalid param type in output element:-\n " + el);
					}
				}
			}
		}

		String originalParam = param;

		/* workaround for different character case issues in column names,
		 * constant values will be as it is */
		if (!DBSFields.VALUE.equals(paramType) && !isCaseSensitive) {
			param = param.toLowerCase();
		}

		/* namespace handling */
		String ownNamespace = el.getAttributeValue(new QName(DBSFields.NAMESPACE));
		if (!DBUtils.isEmptyString(ownNamespace)) {
			namespace = ownNamespace;
		}

		String elementType = el.getLocalName();
		String xsdTypeStr = el.getAttributeValue(new QName(DBSFields.XSD_TYPE));
		if (xsdTypeStr == null || xsdTypeStr.trim().length() == 0) {
			xsdTypeStr = "xs:string";
		}
		QName xsdType = getXsdTypeQName(xsdTypeStr);
		String rdfRef = el.getAttributeValue(new QName(DBSFields.RDF_REF_URI));

		int dataCategory;
		if (rdfRef == null || rdfRef.trim().length() == 0) {
			dataCategory = DBConstants.DataCategory.VALUE;
		} else {
			dataCategory = DBConstants.DataCategory.REFERENCE;
		}

		/* get the required roles in an output element */
		Set<String> requiredRoles = extractRequiredRoles(el);

		/* export value */
		String export = el.getAttributeValue(new QName(DBSFields.EXPORT));

        /* If the element represents an array, its name - in Lower case */
		String arrayName = el.getAttributeValue(new QName("arrayName"));
		if (arrayName != null && !isCaseSensitive) {
			arrayName = arrayName.toLowerCase();
		}

		/* export type */
		int exportType = ParamValue.PARAM_VALUE_SCALAR;
		String exportTypeStr = el.getAttributeValue(new QName(DBSFields.EXPORT_TYPE));
		if (exportTypeStr != null) {
			if (QueryParamTypes.ARRAY.equals(exportTypeStr)) {
				exportType = ParamValue.PARAM_VALUE_ARRAY;
			}
		}

		/* optional value */
		String optionalStr = el.getAttributeValue(new QName(DBSFields.OPTIONAL));
		boolean optional = false;
		if (optionalStr != null) {
			optional = Boolean.parseBoolean(optionalStr);
		}

		optionalOverride |= optional;

		StaticOutputElement soel = new StaticOutputElement(dataService, name, param,
                                                           originalParam, paramType, elementType, namespace,
                                                           xsdType, requiredRoles, dataCategory, resultType, export, exportType, arrayName);
		soel.setOptionalOverride(optionalOverride);
		return soel;
	}

	private static Set<String> extractRequiredRoles(OMElement outEl) {
		String rrStr = outEl.getAttributeValue(new QName("requiredRoles"));
		if (rrStr == null || rrStr.trim().length() == 0) {
			return new HashSet<String>();
		}
		Set<String> requiredRoles = new HashSet<String>();
		String[] values = rrStr.split(",");
		for (String value : values) {
			requiredRoles.add(value.trim());
		}
		return requiredRoles;
	}

	public static QName getXsdTypeQName(String xsdTypeStr) {
		String[] vals = xsdTypeStr.split(":");
		if (vals.length == 1) {
			return new QName(DBConstants.XSD_NAMESPACE, vals[0]);
		} else {
			return new QName(DBConstants.XSD_NAMESPACE, vals[1], vals[0]);
		}
	}

	/**
	 * Create a collection of call queries with the given call query element.
	 */
	@SuppressWarnings("unchecked")
	public static List<CallQuery> createCallQueries(
            DataService dataService, Iterator<OMElement> callQueryElItr) throws DataServiceFault {
		List<CallQuery> callQueryList = new ArrayList<CallQuery>();
		CallQuery callQuery;
		/* extract single call-queries */
		while (callQueryElItr.hasNext()) {
			callQuery = createCallQuery(dataService, callQueryElItr.next(), false);
			callQueryList.add(callQuery);
		}
		return callQueryList;
	}

	@SuppressWarnings("unchecked")
	private static CallQuery createCallQuery(DataService dataService,
                                             OMElement el, boolean optionalOverride) throws DataServiceFault {
		String queryId = el.getAttributeValue(new QName(DBSFields.HREF));
		Map<String, WithParam> withParamList = new HashMap<String, WithParam>();
		Iterator<OMElement> wpItr = el.getChildrenWithName(new QName(DBSFields.WITH_PARAM));
		OMElement wpEl;
		WithParam withParam;
		while (wpItr.hasNext()) {
			wpEl = wpItr.next();
			withParam = createWithParam(wpEl);
		    /* key - target query's name, value - withparam */
			withParamList.put(withParam.getName(), withParam);
		}
		/* get the required roles for the call query */
		Set<String> requiredRoles = extractRequiredRoles(el);

		CallQuery callQuery = new CallQuery(dataService, queryId, withParamList, requiredRoles);
		callQuery.setOptionalOverride(optionalOverride);
		return callQuery;
	}

	public static CallQuery createEmptyCallQuery(DataService dataService) {
		CallQuery callQuery = new CallQuery(dataService, DBConstants.EMPTY_QUERY_ID,
                                            new HashMap<String, WithParam>(), new HashSet<String>());
		return callQuery;
	}

	public static CallQuery createEmptyBoxcarCallQuery(DataService dataService) {
		CallQuery callQuery = new CallQuery(dataService, DBConstants.EMPTY_END_BOXCAR_QUERY_ID,
                                            new HashMap<String, WithParam>(), new HashSet<String>());
		return callQuery;
	}

	private static WithParam createWithParam(OMElement el) throws DataServiceFault {
		String name = el.getAttributeValue(new QName("name"));
        if (name != null) {
            name = name.trim();
        }
		String paramType = "column";
		String param = el.getAttributeValue(new QName(paramType));
		String originalParam = null;
        if (param == null) {
            paramType = "query-param";
            param = el.getAttributeValue(new QName(paramType));
            if (param != null) {
                param = param.trim();
            }
        }
		if (param == null) {
			throw new DataServiceFault("Invalid param type in with-param element:-\n " + el);
		} else {
			originalParam = param;
			/* 'toLowerCase' - workaround for different character case issues in column names */
			param = param.toLowerCase();
		}
		WithParam withParam = new WithParam(name, originalParam, param, paramType);
		return withParam;
	}

}
