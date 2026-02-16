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

package org.wso2.micro.integrator.icp.apis.internal;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;

@Component(name = "org.wso2.micro.integrator.icp.apis.internal.ICPApiServiceComponent", immediate = true)
public class ICPApiServiceComponent {

    private static SecretCallbackHandlerService secretCallbackHandlerService;

    public static SecretCallbackHandlerService getSecretCallbackHandlerService() {

        return secretCallbackHandlerService;
    }

    @Reference(name = "secret.callback.handler.service",
            service = SecretCallbackHandlerService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetSecretCallbackHandlerService")
    protected void setSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {

        ICPApiServiceComponent.secretCallbackHandlerService = secretCallbackHandlerService;
    }

    protected void unsetSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {

        ICPApiServiceComponent.secretCallbackHandlerService = null;
    }
}