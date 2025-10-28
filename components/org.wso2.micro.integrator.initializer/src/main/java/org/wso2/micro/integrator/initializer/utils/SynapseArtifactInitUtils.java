/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer.utils;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.config.xml.SynapseImportSerializer;
import org.apache.synapse.libraries.imports.SynapseImport;
import org.wso2.micro.integrator.initializer.ServiceBusConstants;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

/**
 * Utility class containing util functions to initialize synapse artifacts
 */
public class SynapseArtifactInitUtils {

    private static final Log log = LogFactory.getLog(SynapseArtifactInitUtils.class);
    private static final String CONNECTOR_XML = "connector.xml";

    private static String APP_UNZIP_DIR;
    private static boolean isAppDirCreated = false;

    static {
        String javaTmpDir = System.getProperty("java.io.tmpdir");
        APP_UNZIP_DIR = javaTmpDir.endsWith(File.separator) ? javaTmpDir + ServiceBusConstants.SYNAPSE_LIB_CONFIGS :
                javaTmpDir + File.separator + ServiceBusConstants.SYNAPSE_LIB_CONFIGS;
    }

    public static String getAppUnzipDir() {
        return APP_UNZIP_DIR;
    }

    /**
     * Function to create synapse imports to enable installed connectors
     *
     * @param axisConfiguration axis configuration
     */
    public static void initializeConnectors (AxisConfiguration axisConfiguration) {
        String synapseLibPath = axisConfiguration.getRepository().getPath() +
                                        File.separator + ServiceBusConstants.SYNAPSE_LIB_CONFIGS;
        File synapseLibDir = new File(synapseLibPath);
        if (synapseLibDir.exists() && synapseLibDir.isDirectory()) {
            File[] connectorList = synapseLibDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".zip");
                }
            });

            if (connectorList == null) {
                // No connectors found
                return;
            }

            //Check import related to the connector is available
            String importConfigDirPath = axisConfiguration.getRepository().getPath() +
                    ServiceBusConstants.SYNAPSE_IMPORTS_CONFIG_PATH;
            File importsDir = new File(importConfigDirPath);

            if (!importsDir.exists() && !importsDir.mkdirs()) {
                log.error("Import synapse config directory does not exists and unable to create: " +
                        importsDir.getAbsolutePath());
                // Retrying the same for other connectors is waste
                return;
            }

            for (File connectorZip : connectorList) {
                if (log.isDebugEnabled()) {
                    log.debug("Generating import for connector deployed with package: " + connectorZip.getName());
                }
                String connectorExtractedPath = null;
                try {
                    connectorExtractedPath = extractConnector(connectorZip.getAbsolutePath());
                } catch (IOException e) {
                    log.error("Error while extracting Connector zip : " + connectorZip.getAbsolutePath(), e);
                    continue;
                }
                QName qualifiedName = retrieveQualifiedConnectorName(connectorExtractedPath);
                if (qualifiedName == null) {
                    continue;
                }
                File importFile = new File(importsDir, qualifiedName.toString() + ".xml");

                if (!importFile.exists()) {
                    // Import file enabling file connector not available in synapse imports directory
                    if (log.isDebugEnabled()) {
                        log.debug("Generating import config to enable connector: " + qualifiedName);
                    }
                    generateImportConfig(qualifiedName, importFile);
                }
            }
        }
    }

    /**
     * Function to create import configuration enabling connector
     *
     * @param qualifiedName
     * @param targetImportFile
     */
    private static void generateImportConfig (QName qualifiedName, File targetImportFile) {
        SynapseImport synImport = new SynapseImport();
        synImport.setLibName(qualifiedName.getLocalPart());
        synImport.setLibPackage(qualifiedName.getNamespaceURI());
        synImport.setStatus(true);
        OMElement impEl = SynapseImportSerializer.serializeImport(synImport);

        if (impEl != null) {
            try (FileWriter fileWriter = new FileWriter(targetImportFile)) {
                fileWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + impEl.toString());
            } catch (IOException e) {
                log.error("Error occurred while writing import file: " + qualifiedName);
            }
        } else {
            log.error("Could not add Synapse Import. Invalid import params for libName : " +
                    qualifiedName.getLocalPart() + " packageName : " + qualifiedName.getNamespaceURI());
        }
    }


    private static QName retrieveQualifiedConnectorName(String extractedPath) {
        File connectorXml = new File(extractedPath + CONNECTOR_XML);
        if (!connectorXml.exists()) {
            log.error("connector.xml file not found at : " + extractedPath);
        }

        try (InputStream xmlInputStream =  new FileInputStream(connectorXml)) {
            OMElement connectorDef = new StAXOMBuilder(xmlInputStream).getDocumentElement();
            OMAttribute packageAttr = connectorDef.getFirstElement().getAttribute(new QName("package"));
            OMAttribute nameAttr = connectorDef.getFirstElement().getAttribute(new QName("name"));
            if (nameAttr != null && packageAttr != null) {
                String connectorName = nameAttr.getAttributeValue();
                String packageName = packageAttr.getAttributeValue();
                return new QName(packageName, connectorName);
            }
        } catch (XMLStreamException e) {
            log.error("Error while parsing the connector.xml file ", e);
        } catch (FileNotFoundException e) {
            log.error("connector.xml File cannot be loaded from " + extractedPath, e);
        } catch (IOException e) {
            log.error("Error occurred while reading: " + connectorXml.getPath());
        }
        return null;
    }



    /**
     * Extract the connector at the provided path to the java temp dir. Return the
     * extracted location
     *
     * @param connectorPath - Absolute path of the Carbon application .car file
     * @return - extracted location
     * @throws IOException if an error occurs while extracting
     */
    public static String extractConnector(String connectorPath) throws IOException {
        createTempDirectory();

        String tempConnectorPathFormatted = formatPath(connectorPath);
        String fileName = tempConnectorPathFormatted.substring(tempConnectorPathFormatted.lastIndexOf('/') + 1);
        String dest = getAppUnzipDir() + File.separator + System.currentTimeMillis() + fileName + File.separator;

        createDir(dest);

        extract(connectorPath, dest);
        return dest;
    }

    private static void createTempDirectory(){
        if(isAppDirCreated){
            return;
        }
        createDir(getAppUnzipDir());
        isAppDirCreated = true;
    }

    public static void createDir(String path) {
        File temp = new File(path);
        if (!temp.exists() && !temp.mkdirs()) {
            log.error("Error while creating directory : " + path);
            return;
        }

    }

    /**
     * Format the string paths to match any platform.. windows, linux etc..
     *
     * @param path - input file path
     * @return formatted file path
     */
    public static String formatPath(String path) {
        // removing white spaces
        String pathformatted = path.replaceAll("\\b\\s+\\b", "%20");
        try {
            pathformatted = java.net.URLDecoder.decode(pathformatted, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("Unsupported Encoding in the path :"+ pathformatted);
        }
        // replacing all "\" with "/"
        return pathformatted.replace('\\', '/');
    }

    private static void extract(String sourcePath, String destPath) throws IOException {
        Enumeration entries;
        ZipFile zipFile;

        zipFile = new ZipFile(sourcePath);
        entries = zipFile.entries();

        String canonicalDestPath = new File(destPath).getCanonicalPath();
        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            String canonicalEntryPath = new File(destPath + entry.getName()).getCanonicalPath();
            if (!canonicalEntryPath.startsWith(canonicalDestPath)) {
                throw new IOException("Entry is outside of the target dir: " + entry.getName());
            }
            // if the entry is a directory, create a new dir
            if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(CONNECTOR_XML)) {
                // if the entry is a file, write the file
                copyInputStream(zipFile.getInputStream(entry),
                    new BufferedOutputStream(new FileOutputStream(destPath + entry.getName())));
            }
        }
        zipFile.close();
    }

    private static void copyInputStream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[40960];
        int len;

        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }

        in.close();
        out.close();
    }

}
