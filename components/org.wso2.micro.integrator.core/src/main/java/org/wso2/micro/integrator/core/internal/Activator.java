/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.core.internal;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.wso2.carbon.crypto.api.ExternalCryptoProvider;
import org.wso2.micro.core.encryption.KeyStoreBasedExternalCryptoProvider;
import org.wso2.micro.core.util.CoreServerInitializerHolder;
import org.wso2.micro.integrator.core.UserStoreTemporaryService;
import org.wso2.micro.integrator.core.services.CarbonServerConfigurationService;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementPermission;

public class Activator implements BundleActivator {

    private static final Log log = LogFactory.getLog(Activator.class);

    private ServiceRegistration registration;

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug(Activator.class.getName() + "#start() BEGIN - " + System.currentTimeMillis());
        }
        try {
            // Need permissions in order to activate Carbon Core

            SecurityManager secMan = System.getSecurityManager();
            if (secMan != null) {
                secMan.checkPermission(new ManagementPermission("control"));
            }
            // We assume it's super tenant during the deployment time
//            PrivilegedCarbonContext privilegedCarbonContext = PrivilegedCarbonContext
//                    .getThreadLocalCarbonContext();
//            privilegedCarbonContext.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
//            privilegedCarbonContext.setTenantId(MultitenantConstants.SUPER_TENANT_ID);

            logServerInfo();

            initializeCarbonServerConfigurationService(bundleContext);


            if (log.isDebugEnabled()){
                log.debug("BouncyCastle security provider is successfully registered in JVM.");
            }
//            bundleContext.registerService(CarbonCoreInitializedEvent.class.getName(), new CarbonCoreInitializedEventImpl(), null);
//            GhostServiceMetaArtifactsLoader serviceMetaArtifactsLoader = new GhostServiceMetaArtifactsLoader();
//            bundleContext.registerService(GhostMetaArtifactsLoader.class.getName(), serviceMetaArtifactsLoader, null);
//            CarbonCoreDataHolder.getInstance().setBundleContext(bundleContext);

            if (Boolean.parseBoolean(System.getProperty("NonUserCoreMode"))) {
                log.debug("UserCore component activated in NonUserCoreMode Mode");
                // Registering a TemporaryService so that app deployer service component can continue.
                UserStoreTemporaryService userStoreTemporaryService = new UserStoreTemporaryService();
                bundleContext.registerService(UserStoreTemporaryService.class.getName(), userStoreTemporaryService, null);
            }

            // Initialize MI Server
            CoreServerInitializer coreServerInitializer =
                    new CoreServerInitializer(CarbonCoreDataHolder.getInstance().getServerConfigurationService(),
                            bundleContext);
            CoreServerInitializerHolder.getInstance().setCoreServerInitializer(coreServerInitializer);
            coreServerInitializer.initMIServer();
        } catch (Throwable e) {
            throw new Exception(e);
        }
        if (log.isDebugEnabled()) {
            log.debug(Activator.class.getName() + "#start() COMPLETED - " + System.currentTimeMillis());
        }
    }

    /**
     * Registers a service to read configurations from the carbon.xml
     *
     * @param bundleContext
     * @throws MicroIntegratorConfigurationException if an
     */
    private void initializeCarbonServerConfigurationService(BundleContext bundleContext)
            throws MicroIntegratorConfigurationException {
        CarbonServerConfigurationService carbonServerConfiguration = CarbonServerConfigurationService.getInstance();
        initServerConfiguration(carbonServerConfiguration);
        String portOffset = System.getProperty("portOffset",
                                               carbonServerConfiguration.getFirstProperty("Ports.Offset"));
        //setting the the retrieved ports.offset value as a system propery, in case it was not defined.
        //NIO transport make use of this system property
        System.setProperty("portOffset", portOffset);
        //register carbon server confg as an OSGi service
        registration = bundleContext.registerService(CarbonServerConfigurationService.class.getName(),
                                                     carbonServerConfiguration,
                                                     null);

        // Register the external crypto provider which is based on Carbon keystore management service.
        bundleContext.registerService(ExternalCryptoProvider.class, new KeyStoreBasedExternalCryptoProvider(), null);

        CarbonCoreDataHolder.getInstance().setServerConfigurationService(carbonServerConfiguration);
        CarbonCoreDataHolder.getInstance().setBundleContext(bundleContext);
    }

    private void initServerConfiguration(CarbonServerConfigurationService carbonServerConfiguration)
            throws MicroIntegratorConfigurationException {
        File carbonXML = new File(MicroIntegratorBaseUtils.getServerXml());
        InputStream in = null;
        try {
            in = new FileInputStream(carbonXML);
            carbonServerConfiguration.forceInit(in);
        } catch (MicroIntegratorConfigurationException e) {
            String msg = "Could not initialize server configuration";
            log.fatal(msg);
            throw e;
        } catch (FileNotFoundException e) {
            throw new MicroIntegratorConfigurationException("File: " + carbonXML + " could not be located.");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.warn("Cannot close FileInputStream of file " + carbonXML.getAbsolutePath());
                }
            }
        }
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        log.debug("Stopping Micro Integrator");
    }

    private void logServerInfo() {

        log.debug("Starting WSO2 Micro Integrator ...");
        log.debug("Operating System         : " + System.getProperty("os.name") + " " +
                System.getProperty("os.version") + ", " + System.getProperty("os.arch"));
        log.debug("Java Home                : " + System.getProperty("java.home"));
        log.debug("Java Version             : " + System.getProperty("java.version"));
        log.debug("Java VM                  : " + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.vm.version") + "," + System.getProperty("java.vendor"));

        String carbonHome;
        if ((carbonHome = System.getProperty("carbon.home")).equals(".")) {
            carbonHome = new File(".").getAbsolutePath();
        }

        log.debug("Micro Integrator Home    : " + carbonHome);
        log.debug("Java Temp Dir            : " + System.getProperty("java.io.tmpdir"));
        log.debug("User                     : " + System.getProperty("user.name") + ", "
                + System.getProperty("user.language") + "-" + System.getProperty("user.country")
                + ", " + System.getProperty("user.timezone"));

    }

}
