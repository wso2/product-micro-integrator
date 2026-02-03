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
package org.wso2.carbon.securevault.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.securevault.secret.SecretManager;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.carbon.securevault.SecretCallbackHandlerServiceImpl;
import org.wso2.carbon.securevault.SecretManagerInitializer;

@Component(name = "secret.manager.initializer.component", immediate = true)
public class SecretManagerInitializerComponent {

    private SecretManager secretManager = SecretManager.getInstance();
    private static final Log log = LogFactory.getLog(SecretManagerInitializerComponent.class);

    @Activate
    protected void activate(ComponentContext ctxt) {

        if (log.isDebugEnabled()) {
            log.debug("Starting 'SecretManagerInitializerComponent'");
        }

        SecretManagerInitializer secretManagerInitializer = new SecretManagerInitializer();
        SecretCallbackHandlerServiceImpl serviceImpl = secretManagerInitializer.init();

        ServiceRegistration registration = ctxt.getBundleContext().registerService(
                SecretCallbackHandlerService.class.getName(),
                serviceImpl, null);

    }

    @Deactivate
    protected void deactivate(ComponentContext ctxt) {

        if (log.isDebugEnabled()) {
            log.debug("Stopping 'SecretManagerInitializerComponent'");

        }
        secretManager.shoutDown();
    }

}
