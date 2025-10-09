/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.micro.application.deployer.config;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.application.deployer.AppDeployerConstants;
import org.wso2.micro.application.deployer.AppDeployerUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * This is the runtime representation of the entire app configuration.
 */
public class ApplicationConfiguration {

    private static final Log log = LogFactory.getLog(ApplicationConfiguration.class);

    public static final String ARTIFACTS_XML = "artifacts.xml";
    public static final String METADATA_XML = "metadata.xml";
    public static final String DESCRIPTOR_XML = "descriptor.xml";
    public static final String FEATURE_POSTFIX = ".feature.group";

    private static final QName Q_VERSIONED_DEPLOYMENT = new QName("versionedDeployment");
    private static final QName Q_ID = new QName("id");
    private static final QName Q_DEPENDENCIES = new QName("dependencies");
    private static final QName Q_DEPENDENCY = new QName("dependency");

    private static final QName A_GROUP_ID = new QName("groupId");
    private static final QName A_ARTIFACT_ID = new QName("artifactId");
    private static final QName A_VERSION = new QName("version");
    private static final QName A_TYPE = new QName("type");

    // TODO - define a correct ns
    public static final String APPLICATION_NS = "http://products.wso2.org/carbon";

    private String appName;
    private boolean isVersionedDeployment = false;
    private String appArtifactIdentifier;
    private boolean isFatCAR = false;
    private HashMap<String, String> cAppDependencies = new HashMap<>();
    private String appVersion;
    private String mainSequence;
    private org.wso2.micro.application.deployer.config.Artifact applicationArtifact;

    /**
     * Constructor builds the cApp configuration by reading the artifacts.xml file from the
     * provided path.
     *
     * @param appXmlPath - absolute path to artifacts.xml file
     * @throws CarbonException - error while reading artifacts.xml
     */
    public ApplicationConfiguration(String appXmlPath) throws CarbonException {
        // First check for metadata.xml file ( New CAPP format )
        File f = new File(appXmlPath + ApplicationConfiguration.METADATA_XML);
        if (!f.exists()) {
            // If metadata not exists use the artifacts.xml file ( Old CAPP format )
            f = new File(appXmlPath + ApplicationConfiguration.ARTIFACTS_XML);
            if (!f.exists()) {
                throw new CarbonException("artifacts.xml file not found at : " + appXmlPath);
            }
        }
        InputStream xmlInputStream = null;
        try {
            xmlInputStream = new FileInputStream(f);
            buildConfiguration(new StAXOMBuilder(xmlInputStream).getDocumentElement());
            processProjectDependencies(appXmlPath);
        } catch (FileNotFoundException e) {
            handleException("artifacts.xml File cannot be loaded from " + appXmlPath, e);
        } catch (XMLStreamException e) {
            handleException("Error while parsing the artifacts.xml file ", e);
        } finally {
            if (xmlInputStream != null) {
                try {
                    xmlInputStream.close();
                } catch (IOException e) {
                    log.error("Error while closing input stream.", e);
                }
            }
        }
    }

    /**
     * Constructor builds the cApp configuration by reading the artifacts.xml file from the
     * provided xml input stream.
     *
     * @param xmlInputStream - input stream of the artifacts.xml
     * @throws CarbonException - error while reading artifacts.xml
     */
    public ApplicationConfiguration(InputStream xmlInputStream) throws CarbonException {
        try {
            buildConfiguration(new StAXOMBuilder(xmlInputStream).getDocumentElement());
        } catch (XMLStreamException e) {
            handleException("Error while parsing the artifacts.xml file content stream", e);
        }
    }

    public String getAppName() {
        return appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public org.wso2.micro.application.deployer.config.Artifact getApplicationArtifact() {
        return applicationArtifact;
    }

    public String getAppNameWithVersion() {
        if (isVersionedDeployment()) {
            return getAppArtifactIdentifier();
        }
        if (getAppName() != null) {
            if (getAppVersion() != null) {
                return getAppName() + "_" + getAppVersion();
            }else{
                return getAppName();
            }
        }else{
            return null;
        }
    }

    /**
     * Builds the cApp configuration from the given OMElement which represents the artifacts.xml
     *
     * @param documentElement - root OMElement
     * @throws CarbonException - error while building
     */
    private void buildConfiguration(OMElement documentElement) throws CarbonException {
        if (documentElement == null) {
            throw new CarbonException("Document element for artifacts.xml is null. Can't build " +
                    "the cApp configuration");
        }

        Iterator artifactItr = documentElement.getChildrenWithLocalName(
                org.wso2.micro.application.deployer.config.Artifact.ARTIFACT);
        org.wso2.micro.application.deployer.config.Artifact appArtifact = null;
        while (artifactItr.hasNext()) {
            org.wso2.micro.application.deployer.config.Artifact temp = org.wso2.micro.application.deployer.AppDeployerUtils
                    .populateArtifact((OMElement) artifactItr.next());
            if (org.wso2.micro.application.deployer.AppDeployerConstants.CARBON_APP_TYPE.equals(temp.getType())) {
                appArtifact = temp;
                break;
            }
        }
        if (appArtifact == null) {
            throw new CarbonException("artifacts.xml is invalid. No Artifact " +
                    "found with the type - " + AppDeployerConstants.CARBON_APP_TYPE);
        }
        this.appName = appArtifact.getName();
        this.appVersion = appArtifact.getVersion();
        this.setMainSequence(appArtifact.getMainSequence());

        String[] serverRoles = AppDeployerUtils.readServerRoles();
        List<org.wso2.micro.application.deployer.config.Artifact.Dependency> depsToRemove = new ArrayList<org.wso2.micro.application.deployer.config.Artifact.Dependency>();

        /**
         * serverRoles contains regular expressions. So for each dependency's role, we have to
         * check whether there's a matching role from the list of serverRoles.
         */
        String role;
        for (org.wso2.micro.application.deployer.config.Artifact.Dependency dep : appArtifact.getDependencies()) {
            boolean matched = false;
            role = dep.getServerRole();
            // try to find a matching serverRole for this dep
            for (String currentRole : serverRoles) {
                if (role.matches(currentRole)) {
                    matched = true;
                    break;
                }
            }
            
            if (!matched) {
                depsToRemove.add(dep);
            }
        }

        // removing unwanted dependencies for the current server
        for (Artifact.Dependency item : depsToRemove) {
            appArtifact.removeDependency(item);
        }
        this.applicationArtifact = appArtifact;
    }

    private void processProjectDependencies(String appPath) throws CarbonException {

        File f = new File(appPath + ApplicationConfiguration.DESCRIPTOR_XML);
        if (f.exists()) {
            InputStream xmlInputStream = null;
            try {
                xmlInputStream = new FileInputStream(f);
                populateArtifactIdentifiers(new StAXOMBuilder(xmlInputStream).getDocumentElement());
            } catch (XMLStreamException | FileNotFoundException e) {
                handleException("Error while parsing the descriptor.xml file ", e);
            } finally {
                if (xmlInputStream != null) {
                    try {
                        xmlInputStream.close();
                    } catch (IOException e) {
                        log.error("Error while closing input stream.", e);
                    }
                }
            }
        }
    }

    private void populateArtifactIdentifiers(OMElement descriptorElement) throws CarbonException {

        if (isVersionedDeploymentEnabled(descriptorElement)) {
            this.isVersionedDeployment = true;
            this.appArtifactIdentifier = getRequiredElementText(descriptorElement, Q_ID,
                    "Invalid descriptor.xml. Artifact id is missing for a versioned deployment");

            OMElement fatCarElement = descriptorElement.getFirstChildWithName(new QName(org.apache.axis2.Constants.FAT_CAR_ENABLED));
            isFatCAR = fatCarElement != null && "true".equals(fatCarElement.getText().trim());

            OMElement dependenciesElement = descriptorElement.getFirstChildWithName(Q_DEPENDENCIES);
            if (dependenciesElement != null) {
                for (Iterator<?> it = dependenciesElement.getChildrenWithName(Q_DEPENDENCY); it.hasNext(); ) {
                    OMElement dependency = (OMElement) it.next();
                    String depName = getAttr(dependency, A_GROUP_ID) + "__" + getAttr(dependency, A_ARTIFACT_ID);
                    cAppDependencies.put(depName, getAttr(dependency, A_VERSION));
                }
            }
        }
    }

    /**
     * Checks if versioned deployment is enabled.
     * <p>
     * This method determines whether versioned deployment is enabled by checking
     * the property `versionedDeployment` in the provided descriptor element.
     *
     * @param descriptorElement The descriptor element to check for deployment type
     * @return true if versioned deployment is enabled, false otherwise
     */
    private boolean isVersionedDeploymentEnabled(OMElement descriptorElement) {

        OMElement deploymentElement = descriptorElement.getFirstChildWithName(Q_VERSIONED_DEPLOYMENT);
        if (deploymentElement != null) {
            String versionedDeploymentStr = deploymentElement.getText().trim();
            return Boolean.parseBoolean(versionedDeploymentStr);
        }
        return false;
    }

    private String getRequiredElementText(OMElement parent, QName childName, String errorMessage) throws CarbonException {

        OMElement child = parent.getFirstChildWithName(childName);
        if (child == null) {
            throw new CarbonException(errorMessage);
        }
        return child.getText().trim();
    }

    private void handleException(String msg, Exception e) throws CarbonException {
        log.error(msg, e);
        throw new CarbonException(msg, e);
    }

    public String getMainSequence() {
        return mainSequence;
    }

    public void setMainSequence(String mainSequence) {
        this.mainSequence = mainSequence;
    }

    public boolean isVersionedDeployment() {

        return isVersionedDeployment;
    }

    public String getAppArtifactIdentifier() {

        return appArtifactIdentifier;
    }

    public HashMap<String, String> getCAppDependencies() {

        return cAppDependencies;
    }

    private static String getAttr(OMElement el, QName qn) {
        OMAttribute a = el.getAttribute(qn);
        return a != null ? a.getAttributeValue().trim() : null;
    }

    public boolean isFatCAR() {
        return isFatCAR;
    }
}
