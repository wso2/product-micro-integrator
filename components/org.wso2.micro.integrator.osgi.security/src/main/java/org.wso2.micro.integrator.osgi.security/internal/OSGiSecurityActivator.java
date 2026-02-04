package org.wso2.micro.integrator.osgi.security.internal;
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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.wso2.micro.integrator.osgi.security.PermissionManager;

/**
 * OSGi bundle Activator for enabling OSGi security
 */
public class OSGiSecurityActivator implements BundleActivator {

    /**
     * Implements BundleActivator.start().
     *
     * @param context the framework context for the bundle.
     */
    public void start(BundleContext context) throws Exception {
        try {
            context.addBundleListener(new PermissionManager(context));
        } catch (Throwable e) {
            e.printStackTrace(); // We haven't imported commons.logging
        }
    }

    /**
     * Implements BundleActivator.stop().
     *
     * @param context the framework context for the bundle.
     */
    public void stop(BundleContext context) {
    }
}
