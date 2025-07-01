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

        cAppDependencies.add(dependency);
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
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(descriptorXml)));

                NodeList idElements = document.getElementsByTagName("id");
                if (idElements.getLength() > 0) {
                    setCAppId(idElements.item(0).getTextContent());
                }

                NodeList dependencyNodes = document.getElementsByTagName("dependency");
                for (int i = 0; i < dependencyNodes.getLength(); i++) {
                    Node dependencyNode = dependencyNodes.item(i);
                    String groupId = dependencyNode.getAttributes().getNamedItem("groupId").getNodeValue();
                    String artifactId = dependencyNode.getAttributes().getNamedItem("artifactId").getNodeValue();
                    String version = dependencyNode.getAttributes().getNamedItem("version").getNodeValue();
                    addDependency(groupId + "_" + artifactId + "_" + version);
                }
            }
        } catch (Exception e) {
            log.error("Error reading descriptor.xml from " + this.cAppFile.getName() + ": " + e.getMessage(), e);
        }
    }
}
