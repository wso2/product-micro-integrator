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
package org.wso2.micro.integrator.bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * The bootstrap class used for bootstrapping a Carbon server in standalone mode.
 * <p/>
 * Added this mainly to support child-first classloading for webapps
 * <p/>
 * See http://frank.zinepal.com/embedded-tomcat-class-loading-trickery
 * See http://tomcat.apache.org/tomcat-7.0-doc/class-loader-howto.html
 */
public class Bootstrap {

    private final Set<URL> classpath = new LinkedHashSet<URL>();
    private static final String CARBON_HOME = "carbon.home";
    private static final String INTERNAL_CARBON_LIB_DIR_PATH = "carbon.internal.lib.dir.path";
    protected static final String ROOT = System.getProperty(CARBON_HOME, ".");
    private static final String CARBON_PROPERTIES = "carbon.properties";
    private static final String CONF_DIRECTORY_PATH = "carbon.config.dir.path";

    public static void main(String args[]) {
        new Bootstrap().loadClass(args);
    }

    protected final void loadClass(String args[]) {
        try {
            addSystemProperties();
            addClassPathEntries();
            ClassLoader cl = new URLClassLoader(classpath.toArray(new URL[classpath.size()]));

            // Set the proper classloader for this thread.
            Thread.currentThread().setContextClassLoader(cl);

            // Use reflection to load a class to normally load the rest of the app.
            // Reflection will use the Thread's context class loader and therefore pick up
            // the rest of our libraries.
            Class appClass = cl.loadClass(getClassToLoad());
            Object app = appClass.newInstance();

            Method m = app.getClass().getMethod(getMethodToInvoke(), new Class[] { String[].class });
            m.invoke(app, new Object[] { args });
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }

    }

    private void addSystemProperties() {
        Properties properties = new Properties();
        String filePath = System.getProperty(CONF_DIRECTORY_PATH) + File.separator + CARBON_PROPERTIES;
        File file = new File(filePath);

        if (file.exists()) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                properties.load(in);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                        // Exception is ignored as there is no need to break the execution here
                    }
                }
            }
        }

        Set<Object> keys = properties.keySet();
        for (Object key : keys) {
            System.setProperty((String) key, (String) properties.get(key));
        }
        System.setProperty("javax.xml.bind.JAXBContextFactory", "com.sun.xml.bind.v2.ContextFactory");
        // System property to acknowledge server startup process
        System.setProperty("org.wso2.mi.server.startup.mode", "true");
    }

    protected void addClassPathEntries() throws MalformedURLException {

        // Add lib
        String internalLib = System.getProperty(INTERNAL_CARBON_LIB_DIR_PATH);
        if (internalLib == null) {
            File libFile = Paths.get(ROOT, "lib").toFile();
            addFileUrl(libFile);
            addJarFileUrls(libFile);
        } else {
            addFileUrl(new File(internalLib));
            addJarFileUrls(new File(internalLib));
        }
    }

    /**
     * Add a given file or directory to the list of URLs.
     *
     * @param file the directory to recursively search for JAR files.
     * @throws MalformedURLException If a provided JAR file URL is malformed
     */
    protected final void addFileUrl(File file) throws MalformedURLException {
        classpath.add(file.toURI().toURL());
    }

    /**
     * Add JAR files found in the given directory to the list of URLs.
     *
     * @param root the directory to recursively search for JAR files.
     * @throws MalformedURLException If a provided JAR file URL is malformed
     */
    protected final void addJarFileUrls(File root) throws MalformedURLException {
        File[] children = root.listFiles();

        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isFile() && child.canRead() && child.getName().toLowerCase().endsWith(".jar")) {
                classpath.add(child.toURI().toURL());
            }
        }
    }

    protected String getClassToLoad() {

        return "org.wso2.micro.integrator.server.Main";
    }

    protected String getMethodToInvoke() {
        return "main";
    }
}
