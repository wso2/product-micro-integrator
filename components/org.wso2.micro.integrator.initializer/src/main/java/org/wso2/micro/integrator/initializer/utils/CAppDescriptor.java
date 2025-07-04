/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer.utils;

import org.apache.axis2.deployment.DeploymentEngine;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.wso2.micro.integrator.initializer.utils.DeployerUtil.readDescriptorXmlFromCApp;

public class CAppDescriptor {

    private static final Log log = LogFactory.getLog(DeploymentEngine.class);
    private File cAppFile;
    private String cAppId;
    private List<String> cAppDependencies;

    public CAppDescriptor(File cAppFile) {

        this.cAppFile = cAppFile;
        this.cAppId = cAppFile.getName();
        this.cAppDependencies = new ArrayList<>();
        parseDescriptor();
    }

    public void setCAppId(String cAppId) {

        this.cAppId = cAppId;
    }

    public void addDependency(String dependency) {

        if (!cAppDependencies.contains(dependency)) {
            cAppDependencies.add(dependency);
        }
    }

    public String getCAppId() {

        return cAppId;
    }

    public File getCAppFile() {

        return cAppFile;
    }

    public List<String> getCAppDependencies() {

        return cAppDependencies;
    }

    /**
     * Parses the descriptor XML file associated with the current CApp file.
     * Extracts the CApp ID and its dependencies from the descriptor
     */
    private void parseDescriptor() {
        try {
            String descriptorXml = readDescriptorXmlFromCApp(this.cAppFile.getAbsolutePath());
            if (descriptorXml != null && !descriptorXml.isEmpty()) {
                DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newDocumentBuilderFactory();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(descriptorXml)));

                NodeList idElements = document.getElementsByTagName(Constants.ID);
                if (idElements.getLength() > 0) {
                    setCAppId(idElements.item(0).getTextContent());
                }

                NodeList dependencyNodes = document.getElementsByTagName(Constants.DEPENDENCY);
                for (int i = 0; i < dependencyNodes.getLength(); i++) {
                    Node dependencyNode = dependencyNodes.item(i);
                    String groupId = dependencyNode.getAttributes().getNamedItem(Constants.CAPP_GROUP_ID).getNodeValue();
                    String artifactId = dependencyNode.getAttributes().getNamedItem(Constants.CAPP_ARTIFACT_ID).getNodeValue();
                    String version = dependencyNode.getAttributes().getNamedItem(Constants.CAPP_VERSION).getNodeValue();
                    if (StringUtils.isNotEmpty(groupId) && StringUtils.isNotEmpty(artifactId) && StringUtils.isNotEmpty(version)) {
                        addDependency(groupId + Constants.UNDERSCORE + artifactId + Constants.UNDERSCORE + version);
                    } else {
                        log.warn("Skipping dependency with missing attributes in descriptor.xml for CApp: "
                                + this.cAppFile.getName());
                    }

                }
            }
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            log.error("Could not initialize the XML parser for descriptor.xml in CApp: "
                    + this.cAppFile.getName() + ". Please check your system configuration. Error: "
                    + e.getMessage(), e);
        } catch (org.xml.sax.SAXException e) {
            log.error("Failed to parse descriptor.xml in CApp: "
                    + this.cAppFile.getName() + ". The XML file may be invalid. Error: "
                    + e.getMessage(), e);
        } catch (java.io.IOException e) {
            log.error("Could not read descriptor.xml from CApp: "
                    + this.cAppFile.getName() + ". The file may be missing or inaccessible. Error: "
                    + e.getMessage(), e);
        } catch (NullPointerException e) {
            log.error("Missing dependency attribute in descriptor.xml for CApp: "
                    + this.cAppFile.getName() + ". A dependency element may lack groupId, artifactId, or version. Details: "
                    + e.getMessage(), e);
        }
    }
}
