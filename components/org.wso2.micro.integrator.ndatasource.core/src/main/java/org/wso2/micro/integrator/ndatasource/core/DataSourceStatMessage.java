/**
 *  Copyright (c) 2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.micro.integrator.ndatasource.core;

import org.apache.axis2.clustering.ClusteringCommand;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.ClusteringMessage;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.ndatasource.common.DataSourceException;

/**
 * This class represents the cluster message used to notify the cluster nodes of
 * data source information changes.
 */
public class DataSourceStatMessage extends ClusteringMessage {

	private static final long serialVersionUID = 8026941529427128824L;
	
	private static Log log = LogFactory.getLog(DataSourceStatMessage.class);
	
	private String dsName;
	
	private int tenantId;

	@Override
	public ClusteringCommand getResponse() {
		return null;
	}

	public void setDsName(String dsName) {
		this.dsName = dsName;
	}

	public String getDsName() {
		return dsName;
	}
	
	public void setTenantId(int tenantId) {
		this.tenantId = tenantId;
	}

	public int getTenantId() {
		return tenantId;
	}
	
	@Override
	public void execute(ConfigurationContext configCtx) throws ClusteringFault {
		try {

		    if (log.isDebugEnabled()) {
		    	log.debug("Cluster message arrived for tenant: " + 
		    			this.getTenantId() + " datasource: " + this.getDsName());
		    }
		    DataSourceManager.getInstance().getDataSourceRepository().refreshUserDataSource(
		    		this.getDsName());
		} catch (DataSourceException e) {
			throw new ClusteringFault("Error in handling data source stat message: " +
					e.getMessage(), e);
		}
	}
	
}
