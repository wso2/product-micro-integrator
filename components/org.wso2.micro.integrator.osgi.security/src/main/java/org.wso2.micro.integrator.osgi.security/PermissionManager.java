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
package org.wso2.micro.integrator.osgi.security;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;

import java.security.AllPermission;

/**
 * OSGi Security Permission Manager
 */
public class PermissionManager implements BundleListener {

    private BundleContext context;

    public PermissionManager(BundleContext context) {
        this.context = context;
    }

    public void bundleChanged(BundleEvent bundleEvent) {
        int event = bundleEvent.getType();
        if (event == BundleEvent.INSTALLED) {
            Bundle installedBundle = bundleEvent.getBundle();
            String bundleLocation = installedBundle.getLocation();
            PermissionAdmin permissionAdmin = getPermissionAdmin(context);
            if (permissionAdmin != null) {
                if (bundleLocation.startsWith("reference:file:plugins/")) {
                    PermissionInfo[] superTenantPermInfos = {
                            new PermissionInfo(AllPermission.class.getName(), "", ""),
                    };
                    permissionAdmin.setPermissions(bundleLocation, superTenantPermInfos);
                }
            }
        }
    }

    public PermissionAdmin getPermissionAdmin(BundleContext context) {
        return (PermissionAdmin) context.getService(context.getServiceReference(PermissionAdmin.class.getName()));
    }
}
