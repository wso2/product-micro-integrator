/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.micro.integrator.server;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.config.mapper.ConfigParserException;
import org.wso2.micro.integrator.server.extensions.DefaultBundleCreator;
import org.wso2.micro.integrator.server.extensions.DropinsBundleDeployer;
import org.wso2.micro.integrator.server.extensions.EclipseIniRewriter;
import org.wso2.micro.integrator.server.extensions.LibraryFragmentBundleCreator;
import org.wso2.micro.integrator.server.extensions.PatchInstaller;
import org.wso2.micro.integrator.server.extensions.SystemBundleExtensionCreator;
import org.wso2.micro.integrator.server.util.Utils;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class Main {

    protected static final String FRAMEWORK_BUNDLE_NAME = "org.eclipse.osgi";
    protected static final String STARTER = "org.eclipse.core.runtime.adaptor.EclipseStarter";
    protected static final String FRAMEWORKPROPERTIES = "org.eclipse.osgi.framework.internal.core.FrameworkProperties";
    protected static final String NULL_IDENTIFIER = "@null";
    protected static final String OSGI_FRAMEWORK = "osgi.framework";
    protected static final String OSGI_INSTANCE_AREA = "osgi.instance.area";
    protected static final String OSGI_CONFIGURATION_AREA = "osgi.configuration.area";
    protected static final String OSGI_INSTALL_AREA = "osgi.install.area";
    protected static final String P2_DATA_AREA = "eclipse.p2.data.area";
    protected static final String ENABLE_EXTENSIONS = "wso2.enableExtensions";
    protected static final String DEPLOYMENT_CONFIG_FILE_PATH = "deployment.config.file.path";
    protected static final String AVOID_CONFIGURATION_UPDATE = "avoidConfigUpdate";
    protected static final String ONLY_PARSE_CONFIGURATION = "configParseOnly";
    protected static final String SKIP_STARTUP_EXTENSIONS = "skipStartupExtensions";
    protected static final String LOGFILES_HOME = "logfiles.home";

    static File platformDirectory;
    private static Log logger = LogFactory.getLog(Main.class);

    public static void main(String[] args) {

        //Setting pax-logging configurations
        String confPath = System.getProperty(LauncherConstants.CARBON_CONFIG_DIR_PATH);
        System.setProperty(LauncherConstants.PAX_DEFAULT_SERVICE_LOG_LEVEL, LauncherConstants.LOG_LEVEL_WARN);
        System.setProperty(LauncherConstants.PAX_LOGGING_PROPERTY_FILE_KEY,
                           confPath + File.separator + "etc" + File.separator
                                   + LauncherConstants.PAX_LOGGING_PROPERTIES_FILE);

        //Setting Carbon Home
        if (System.getProperty(LauncherConstants.CARBON_HOME) == null) {
            System.setProperty(LauncherConstants.CARBON_HOME, ".");
        }
        System.setProperty(LauncherConstants.AXIS2_HOME, System.getProperty(LauncherConstants.CARBON_HOME));

        //To keep track of the time taken to start the Carbon server.
        System.setProperty("wso2carbon.start.time", System.currentTimeMillis() + "");
        if (System.getProperty("carbon.instance.name") == null) {
            InetAddress addr;
            String ipAddr;
            String hostName;
            try {
                addr = InetAddress.getLocalHost();
                ipAddr = addr.getHostAddress();
                hostName = addr.getHostName();
            } catch (UnknownHostException e) {
                ipAddr = "localhost";
                hostName = "127.0.0.1";
            }
            String uuId = UUID.randomUUID().toString();
            String timeStamp = System.currentTimeMillis() + "";
            String carbon_instance_name = timeStamp + "_" + hostName + "_" + ipAddr + "_" + uuId;
            System.setProperty("carbon.instance.name", carbon_instance_name);
        }
        if (System.getProperty(LOGFILES_HOME) == null) {
            System.setProperty(LOGFILES_HOME,
                    System.getProperty(LauncherConstants.CARBON_HOME) +
                            File.separator + "repository" + File.separator + "logs" + File.separator);
        }
        processCmdLineArgs(args);

        boolean skipExtensions = false;
        if (StringUtils.equalsIgnoreCase(System.getProperty(SKIP_STARTUP_EXTENSIONS) , "true")) {
            skipExtensions = true;
        }
        handleConfiguration();          // handle config mapper configurations
        if (!skipExtensions) {
            writePID(System.getProperty(LauncherConstants.CARBON_HOME));
            invokeExtensions();
        }
        startEquinox();
    }

    /**
     * Invoke the extensions specified in the carbon.xml
     */
    public static void invokeExtensions() {

        // disables loading extensions such as patches, libs and ext jars only if the value of
        // property is false
        if (System.getProperty(ENABLE_EXTENSIONS) == null || System.getProperty(ENABLE_EXTENSIONS)
                .equalsIgnoreCase("true")) {
            //converting jars found under components/lib and putting them in components/dropins dir
            new DefaultBundleCreator().perform();
            new SystemBundleExtensionCreator().perform();
            //copying patched jars to components/plugins dir
            new PatchInstaller().perform();
            new LibraryFragmentBundleCreator().perform();
        }

        //Add bundles in the dropins directory to the bundles.info file.
        new DropinsBundleDeployer().perform();

        //rewriting the eclipse.ini file
        new EclipseIniRewriter().perform();
    }

    /**
     * Write the process ID of this process to the file.
     *
     * @param runtimePath wso2.runtime.path sys property value.
     */
    private static void writePID(String runtimePath) {
        // Adopted from: https://stackoverflow.com/a/7690178
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        int indexOfAt = jvmName.indexOf('@');
        if (indexOfAt < 1) {
            logger.warn("Cannot extract current process ID from JVM name '" + jvmName + "'.");
            return;
        }
        String pid = jvmName.substring(0, indexOfAt);

        Path runtimePidFile = Paths.get(runtimePath, "wso2carbon.pid");
        try {
            Files.write(runtimePidFile, pid.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warn("Cannot write process ID '" + pid + "' to '" + runtimePidFile.toString() + "' file.", e);
        }
    }

    /**
     * Process command line arguments and set corresponding system properties.
     *
     * @param args cmd line args
     */
    private static void processCmdLineArgs(String[] args) {
        String cmd = null;
        int index = 0;

        // Set the System properties
        for (String arg : args) {
            index++;
            if (arg.startsWith("-D")) {
                int indexOfEq = arg.indexOf('=');
                String property;
                String value;
                if (indexOfEq != -1) {
                    property = arg.substring(2, indexOfEq);
                    value = arg.substring(indexOfEq + 1);
                } else {
                    property = arg.substring(2);
                    value = "true";
                }
                System.setProperty(property, value);
            } else if (arg.toUpperCase().endsWith(LauncherConstants.COMMAND_HELP)) {
                Utils.printUsages();
                System.exit(0);
            } else if (arg.toUpperCase().endsWith(LauncherConstants.COMMAND_CLEAN_REGISTRY)) {
                // sets the system property marking a registry cleanup
                System.setProperty("carbon.registry.clean", "true");
            } else {
                if (cmd == null) {
                    cmd = arg;
                }
            }
        }
    }

    private static void startEquinox() {
        /**
         * Launches Equinox OSGi framework by  invoking EclipseStarter.startup() method using reflection.
         * Creates a ChildFirstClassLoader out of the OSGi framework jar and set the classloader as the framework
         * classloader.
         */
        URLClassLoader frameworkClassLoader = null;
        platformDirectory = Utils.getCarbonComponentRepo();
        if (platformDirectory == null) {
            throw new IllegalStateException("Could not start the Framework - (not deployed)");
        }

        if (frameworkClassLoader != null) {
            return;
        }

        final Map<String, String> initialPropsMap = buildInitialPropertyMap();
        String[] args2 = Utils.getArgs();

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            System.setProperty("osgi.framework.useSystemProperties", "false");

            frameworkClassLoader = java.security.AccessController
                    .doPrivileged(new java.security.PrivilegedAction<URLClassLoader>() {
                        public URLClassLoader run() {
                            URLClassLoader cl = null;
                            try {
                                cl = new ChildFirstURLClassLoader(
                                        new URL[] { new URL(initialPropsMap.get(OSGI_FRAMEWORK)) }, null);
                            } catch (MalformedURLException e) {
                                logger.error(e.getMessage(), e);
                            }
                            return cl;
                        }
                    });

            Class clazz = frameworkClassLoader.loadClass(STARTER);

            //Set the propertyMap by invoking setInitialProperties method.
            Method setInitialProperties = clazz.getMethod("setInitialProperties", Map.class);
            setInitialProperties.invoke(null, initialPropsMap);

            //Invokes the startup method with some arguments.
            Method runMethod = clazz.getMethod("startup", String[].class, Runnable.class);
            runMethod.invoke(null, args2, null);

        } catch (InvocationTargetException ite) {
            Throwable t = ite.getTargetException();
            if (t == null) {
                t = ite;
            }
            throw new RuntimeException(t.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    /**
     * buildInitialPropertyMap create the initial set of properties from the contents of launch.ini
     * and for a few other properties necessary to launch defaults are supplied if not provided.
     * The value '@null' will set the map value to null.
     *
     * @return a map containing the initial properties
     */
    private static Map<String, String> buildInitialPropertyMap() {
        Map<String, String> initialPropertyMap = new HashMap<String, String>();
        String carbonConfigHome = System.getProperty(LauncherConstants.CARBON_CONFIG_DIR_PATH);
        Properties launchProperties;
        if (carbonConfigHome == null) {
            String carbonHome = System.getProperty(LauncherConstants.CARBON_HOME);
            launchProperties = Utils.loadProperties(
                    Paths.get(carbonHome, "repository", "conf", "etc", LauncherConstants.LAUNCH_INI).toString());
        } else {
            launchProperties = Utils
                    .loadProperties(Paths.get(carbonConfigHome, "etc", LauncherConstants.LAUNCH_INI).toString());
        }
        for (Object o : launchProperties.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if (key.endsWith("*")) { //$NON-NLS-1$
                if (value.equals(NULL_IDENTIFIER)) {
                    Utils.clearPrefixedSystemProperties(key.substring(0, key.length() - 1), initialPropertyMap);
                }
            } else if (value.equals(NULL_IDENTIFIER)) {
                initialPropertyMap.put(key, null);
            } else {
                initialPropertyMap.put((String) entry.getKey(), (String) entry.getValue());
            }
        }
        try {

            /*
             *  in order to support multiple profiling, the new install, configuration and workspace area got to move
             *  from ../components/ to ../components/ PROFILE_ID/
             */
            // install.area if not specified
            if (initialPropertyMap.get(OSGI_INSTALL_AREA) == null) {
                //specifying the install.area according to the running Profile
                File installDir = new File(platformDirectory, System.getProperty(LauncherConstants.PROFILE_ID));

                initialPropertyMap.put(OSGI_INSTALL_AREA, installDir.toURL().toExternalForm());
            }

            // configuration.area if not specified
            if (initialPropertyMap.get(OSGI_CONFIGURATION_AREA) == null) {
                File configurationDirectory = new File(platformDirectory,
                                                       System.getProperty(LauncherConstants.PROFILE_ID) + File.separator
                                                               + "configuration");
                initialPropertyMap.put(OSGI_CONFIGURATION_AREA, configurationDirectory.toURL().toExternalForm());
            }

            // instance.area if not specified
            if (initialPropertyMap.get(OSGI_INSTANCE_AREA) == null) {
                File workspaceDirectory = new File(platformDirectory,
                                                   System.getProperty(LauncherConstants.PROFILE_ID) + File.separator
                                                           + "workspace");
                initialPropertyMap.put(OSGI_INSTANCE_AREA, workspaceDirectory.toURL().toExternalForm());
            }

            // osgi.framework if not specified
            if (initialPropertyMap.get(OSGI_FRAMEWORK) == null) {
                // search for osgi.framework in osgi.install.area
                /*String installArea = initialPropertyMap.get(OSGI_INSTALL_AREA);

                // only support file type URLs for install area
                if (installArea.startsWith(FILE_SCHEME)) {
                    installArea = installArea.substring(FILE_SCHEME.length());
                }

                String path = new File(installArea, "plugins").toString();*/
                String path = new File(platformDirectory, "plugins").toString();
                path = Utils.searchFor(FRAMEWORK_BUNDLE_NAME, path);
                if (path == null) {
                    throw new RuntimeException("Could not find framework");
                }

                initialPropertyMap.put(OSGI_FRAMEWORK, new File(path).toURL().toExternalForm());
            }
            if (initialPropertyMap.get(P2_DATA_AREA) == null) {
                /*initialPropertyMap.put(P2_DATA_AREA, new File(platformDirectory, System.getProperty(LauncherConstants.PROFILE_ID) +
                                                                   File.separator + "p2").toString());*/

                initialPropertyMap.put(P2_DATA_AREA, new File(platformDirectory, "p2").toString());
                //System.out.println("the data area: " + initialPropertyMap.get(P2_DATA_AREA));
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error establishing location");
        }
        return initialPropertyMap;
    }

    private static void handleConfiguration() {

        String resourcesDir = Paths.get(System.getProperty(LauncherConstants.CARBON_HOME),
                                        "repository", "resources", "conf").toString();

        String configFilePath = System.getProperty(DEPLOYMENT_CONFIG_FILE_PATH);
        if (StringUtils.isEmpty(configFilePath)) {
            configFilePath = Paths.get(System.getProperty(LauncherConstants.CARBON_CONFIG_DIR_PATH),
                                       ConfigParser.UX_FILE_PATH).toString();
        }
        // As deployment.toml is mandatory, set avoidConfigUpdate to false and configParseOnly to true
        if (Boolean.getBoolean(AVOID_CONFIGURATION_UPDATE)) {
            logger.warn("System property 'configParseOnly' will be set to true instead of " +
                    "'avoidConfigUpdate' as deployment.toml configuration is mandatory.");
            System.setProperty(AVOID_CONFIGURATION_UPDATE, "false");
            System.setProperty(ONLY_PARSE_CONFIGURATION, "true");
        }
        String outputDir = System.getProperty(LauncherConstants.CARBON_HOME);
        try {
            ConfigParser.parse(configFilePath, resourcesDir, outputDir);
        } catch (ConfigParserException e) {
            logger.fatal("Error while performing configuration changes", e);
            System.exit(1);
        }
    }
}
