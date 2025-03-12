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
package org.wso2.micro.integrator.ndatasource.rdbms;

import java.lang.management.ManagementFactory;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.wso2.micro.integrator.ndatasource.common.DataSourceException;
import org.wso2.micro.integrator.ndatasource.rdbms.utils.RDBMSDataSourceUtils;
import org.wso2.micro.integrator.ndatasource.core.utils.DataSourceUtils;

/**
 * RDBMS data source implementation.
 */
public class RDBMSDataSource {

	private static Log log = LogFactory.getLog(RDBMSDataSource.class);

	private DataSource dataSource;

	private Reference dataSourceFactoryReference;

	private PoolConfiguration poolProperties;

	public RDBMSDataSource(RDBMSConfiguration config) throws DataSourceException {
		this.poolProperties = RDBMSDataSourceUtils.createPoolConfiguration(config);
		this.populateStandardProps();
	}

	private void populateStandardProps() {
		String jdbcInterceptors = this.poolProperties.getJdbcInterceptors();
		if (jdbcInterceptors == null) {
			jdbcInterceptors = "";
		}
		//Correlation log interceptor is added to the interceptor chain
		jdbcInterceptors = RDBMSDataSourceConstants.STANDARD_JDBC_INTERCEPTORS + getJDBCInterceptors(jdbcInterceptors)
		+ RDBMSDataSourceConstants.CORRELATION_LOG_INTERCEPTOR;
		this.poolProperties.setJdbcInterceptors(jdbcInterceptors);
	}

	private String getJDBCInterceptors(String jdbcInterceptors) {
		if (StringUtils.isEmpty(jdbcInterceptors) || jdbcInterceptors.endsWith(
                RDBMSDataSourceConstants.JDBC_INTERCEPTOR_SEPARATOR)) {
			return jdbcInterceptors;
		} else {
			return jdbcInterceptors + RDBMSDataSourceConstants.JDBC_INTERCEPTOR_SEPARATOR;
		}
	}

	public DataSource getDataSource() {
		if (this.dataSource == null) {
			this.dataSource = new DataSource(poolProperties);
		}
                if (poolProperties.isJmxEnabled()) {
                        this.registerMBean();
                }
		return this.dataSource;
	}

       private void registerMBean() {
                MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
                String mBean = "";
                try {
                        if (DataSourceUtils.getCurrentDataSourceId() == null) {
                                if (log.isDebugEnabled()) {
                                        log.debug("The current dataSource id is not set");
                                }
                                return;
                        }
                        String [] dataSourceId = DataSourceUtils.getCurrentDataSourceId().split(":");
                        mBean = dataSourceId[1] + "," + dataSourceId[0];
                        ObjectName objectName = new ObjectName(mBean + ":type=DataSource");
                        if (mBeanServer.isRegistered(objectName)) {
                            mBeanServer.unregisterMBean(objectName);
                        }
                        mBeanServer.registerMBean(this.dataSource.createPool().getJmxPool(),objectName);
                } catch (InstanceAlreadyExistsException e) {
                        //ignore as the mbean for the same datasource name is already exist
                } catch (MalformedObjectNameException e) {
                       log.error("Error while registering the MBean for dataSource '"
                               + mBean + " " + e.getMessage(), e);
                } catch (NotCompliantMBeanException e) {
                       log.error("Error while registering the MBean for dataSource '"
                               + mBean + " " + e.getMessage(), e);
                } catch (SQLException e) {
                       log.error("Error while registering the MBean for dataSource '"
                               + mBean + " " + e.getMessage(), e);
                } catch (MBeanRegistrationException e) {
                       log.error("Error while registering the MBean for dataSource '"
                               + mBean + " " + e.getMessage(), e);
                } catch (InstanceNotFoundException e) {
                       //ignore as the mbean for the same datasource name is already exist
                }
       }

	public Reference getDataSourceFactoryReference() throws DataSourceException {
		if (dataSourceFactoryReference == null) {
			dataSourceFactoryReference = new Reference("org.apache.tomcat.jdbc.pool.DataSource",
	                "org.apache.tomcat.jdbc.pool.DataSourceFactory", null);

			Map<String, String> poolConfigMap = RDBMSDataSourceUtils.extractPrimitiveFieldNameValuePairs(poolProperties);
			Iterator<Entry<String, String>> poolConfigMapIterator = poolConfigMap.entrySet().iterator();

			while (poolConfigMapIterator.hasNext()) {
				Entry<String, String> pairs = poolConfigMapIterator.next();
				dataSourceFactoryReference.add(new StringRefAddr(pairs.getKey(),
						pairs.getValue()));
			}
		}
		return dataSourceFactoryReference;
	}

}
