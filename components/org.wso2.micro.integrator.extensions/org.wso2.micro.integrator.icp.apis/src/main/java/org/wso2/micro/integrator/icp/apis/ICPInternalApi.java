/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.icp.apis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.api.cors.CORSConfiguration;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;
import org.wso2.carbon.inbound.endpoint.internal.http.api.InternalAPI;
import org.wso2.carbon.inbound.endpoint.internal.http.api.InternalAPIHandler;

import java.util.ArrayList;
import java.util.List;

import static org.wso2.micro.integrator.management.apis.Constants.PREFIX_ARTIFACTS;
import static org.wso2.micro.integrator.management.apis.Constants.PREFIX_ICP;

/**
 * Internal API for ICP (Integrated Control Plane) endpoints.
 * This API handles ICP-specific operations with dedicated authentication.
 */
public class ICPInternalApi implements InternalAPI {

    private static final Log log = LogFactory.getLog(ICPInternalApi.class);

    private String name;
    private APIResource[] resources;
    private List<InternalAPIHandler> handlerList = null;
    private CORSConfiguration apiCORSConfiguration = null;

    public ICPInternalApi() {
        ArrayList<APIResource> resourcesList = new ArrayList<>();
        resourcesList.add(new ICPArtifactResource(PREFIX_ARTIFACTS));
        resourcesList.add(new WsdlResource(PREFIX_ARTIFACTS + "/wsdl"));
        resourcesList.add(new ICPGetLocalEntryValueResource(PREFIX_ARTIFACTS + "/local-entry"));
        resourcesList.add(new ICPGetParamsResource(PREFIX_ARTIFACTS + "/parameters"));
        resourcesList.add(new ICPStatusResource(PREFIX_ARTIFACTS + "/status"));
        resourcesList.add(new ICPTracingResource(PREFIX_ARTIFACTS + "/tracing"));
        resourcesList.add(new ICPStatisticsResource(PREFIX_ARTIFACTS + "/statistics"));
        resources = resourcesList.toArray(new APIResource[0]);
        log.info("ICP Internal API initialized with " + resources.length + " resources");
    }

    @Override
    public APIResource[] getResources() {
        return resources;
    }

    @Override
    public String getContext() {
        return PREFIX_ICP;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setHandlers(List<InternalAPIHandler> handlerList) {
        this.handlerList = handlerList;
    }

    @Override
    public List<InternalAPIHandler> getHandlers() {
        return this.handlerList;
    }

    @Override
    public void setCORSConfiguration(CORSConfiguration corsConfiguration) {
        apiCORSConfiguration = corsConfiguration;
    }

    @Override
    public CORSConfiguration getCORSConfiguration() {
        return apiCORSConfiguration;
    }
}
