/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.micro.integrator.initializer.deployment.application.deployer;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.deployment.AbstractDeployer;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.api.API;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.micro.application.deployer.AppDeployerUtils;
import org.wso2.micro.application.deployer.CarbonApplication;
import org.wso2.micro.application.deployer.config.ApplicationConfiguration;
import org.wso2.micro.application.deployer.config.Artifact;
import org.wso2.micro.application.deployer.handler.AppDeploymentHandler;
import org.wso2.micro.core.CarbonAxisConfigurator;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.core.util.FileManipulator;
import org.wso2.micro.integrator.initializer.serviceCatalog.ServiceCatalogDeployer;
import org.wso2.micro.integrator.initializer.utils.DeployerUtil;
import org.wso2.micro.integrator.initializer.utils.ServiceCatalogUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.stream.XMLStreamException;

import static org.wso2.micro.core.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.micro.integrator.initializer.deployment.synapse.deployer.SynapseAppDeployerConstants.API_TYPE;

public class CappDeployer extends AbstractDeployer {

    private static final Log log = LogFactory.getLog(CappDeployer.class);

    private AxisConfiguration axisConfig;

    private List<AppDeploymentHandler> appDeploymentHandlers = new ArrayList<>();
    private static ArrayList<CarbonApplication> cAppMap = new ArrayList<>();
    private static ArrayList<CarbonApplication> faultyCAppObjects = new ArrayList<>();
    private static ArrayList<String> faultyCapps = new ArrayList<>();
    private final Object lock = new Object();
    private static final String SWAGGER_SUBSTRING = "_swagger";
    private static final String METADATA_FOLDER_NAME = "metadata";
    private static final String ARTIFACT_FILE = "artifact.xml";
    /**
     * Carbon application repository directory.
     */
    private String cAppDir;

    private String appsDir;

    /**
     * Carbon application file directory (i.e. 'car').
     */
    private String extension;

    /**
     * Service Catalog Executor threads for publishing Services to Service Catalog.
     */
    private ExecutorService serviceCatalogExecutor;

    /**
     * Map object to store Service Catalog configuration
     */
    private Map serviceCatalogConfiguration;

    /**
     * SecretCallbackHandlerService to read Service Catalog Configs
     */
    private SecretCallbackHandlerService secretCallbackHandlerService;

    public void init(ConfigurationContext configurationContext) {

        if (log.isDebugEnabled()) {
            log.debug("Initializing Capp Deployer..");
        }
        this.axisConfig = configurationContext.getAxisConfiguration();

        //delete the older extracted capps for this tenant.
        String appUnzipDir = AppDeployerUtils.getAppUnzipDir() + File.separator +
                AppDeployerUtils.getTenantIdString();
        FileManipulator.deleteDir(appUnzipDir);

        if (ServiceCatalogUtils.isServiceCatalogEnabled()) {
            serviceCatalogConfiguration = ServiceCatalogUtils.readConfiguration(secretCallbackHandlerService);
            serviceCatalogExecutor = Executors.newFixedThreadPool(
                    ServiceCatalogUtils.getExecutorThreadCount(serviceCatalogConfiguration, 1));
        }
    }

    public void setDirectory(String cAppDir) {

        this.cAppDir = cAppDir;
    }

    public void setAppsDirectory(String appsDir) {

        this.appsDir = appsDir;
    }

    public void setExtension(String extension) {

        this.extension = extension;
    }

    /**
     * Axis2 deployment engine will call this method when a .car archive is deployed. So we only have to call the
     * cAppDeploymentManager to deploy it using the absolute path of the deployed .car file.
     *
     * @param deploymentFileData - info about the deployed file
     * @throws DeploymentException - error while deploying cApp
     */
    public void deploy(DeploymentFileData deploymentFileData) throws DeploymentException {
        String artifactPath = deploymentFileData.getAbsolutePath();
        try {
            deployCarbonApps(artifactPath);
        } catch (Exception e) {
            log.error("Error while deploying carbon application " + artifactPath, e);
        }

        super.deploy(deploymentFileData);
    }

    /**
     * Deploy synapse artifacts in a .car file.
     *
     * @param artifactPath - file path to be processed
     * @throws CarbonException - error while building
     */
    private void deployCarbonApps(String artifactPath) throws CarbonException {

        File cAppDirectory = new File(this.cAppDir);

        String archPathToProcess = AppDeployerUtils.formatPath(artifactPath);
        String cAppName = archPathToProcess.substring(archPathToProcess.lastIndexOf('/') + 1);

        if (!isCAppArchiveFile(cAppName)) {
            log.warn("Only .car files are processed. Hence " + cAppName + " will be ignored");
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Carbon Application detected : " + cAppName);
        }

        String targetCAppPath = cAppDirectory + File.separator + cAppName;
        String extractedPath = extractCarbonApplication(targetCAppPath);
        deployCarbonApplications(cAppName, targetCAppPath, extractedPath);
    }

    /**
     * Deploy synapse artifacts in a carbon application under apps directory.
     *
     * @param artifactPath - file path to be processed
     * @throws CarbonException - error while building
     */
    public void deployApps(String artifactPath) throws CarbonException {

        String archPathToProcess = AppDeployerUtils.formatPath(artifactPath);
        String cAppName = archPathToProcess.substring(archPathToProcess.lastIndexOf('/') + 1);
        String targetCAppPath = artifactPath.endsWith(File.separator) ? artifactPath : artifactPath + File.separator;
        deployCarbonApplications(cAppName, targetCAppPath, targetCAppPath);
    }

    private void deployCarbonApplications(String cAppName, String cAppPath, String targetCAppPath) throws CarbonException {

        CarbonApplication currentApp = null;
        try {
            currentApp = buildCarbonApplication(cAppPath, targetCAppPath, cAppName, axisConfig);
            if (currentApp != null) {
                // deploy sub artifacts of this cApp
                this.searchArtifacts(currentApp.getExtractedPath(), currentApp);

                if (isArtifactReadyToDeploy(currentApp.getAppConfig().getApplicationArtifact())) {
                    // Now ready to deploy
                    // send the CarbonApplication instance through the handler chain
                    for (AppDeploymentHandler appDeploymentHandler : appDeploymentHandlers) {
                        appDeploymentHandler.deployArtifacts(currentApp, axisConfig);
                    }
                } else {
                    log.error("Some dependencies were not satisfied in cApp:" + currentApp.getAppNameWithVersion() +
                            "Check whether all dependent artifacts are included in cApp file: " +
                            targetCAppPath);

                    deleteExtractedCApp(currentApp.getExtractedPath());
                    // Validate synapse config to remove half added swagger definitions in the case of a faulty CAPP.
                    SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).validateSwaggerTable();
                    return;
                }

                // Deployment Completed
                currentApp.setDeploymentCompleted(true);
                this.addCarbonApp(currentApp);
                log.info("Successfully Deployed Carbon Application : " + currentApp.getAppNameWithVersion() +
                        AppDeployerUtils.getTenantIdLogString(AppDeployerUtils.getTenantId()));
            }
        } catch (DeploymentException e) {
            log.error("Error occurred while deploying the Carbon application: " + cAppName
                      + ". Reverting successfully deployed artifacts in the CApp.", e);
            undeployCarbonApp(currentApp, axisConfig);
            // Validate synapse config to remove half added swagger definitions in the case of a faulty CAPP.
            SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME).validateSwaggerTable();
            faultyCAppObjects.add(currentApp);
            faultyCapps.add(cAppName);
        }
        if (serviceCatalogConfiguration != null && !faultyCapps.contains(cAppName)) {
            ServiceCatalogDeployer serviceDeployer = new ServiceCatalogDeployer(cAppName,
                    ((CarbonAxisConfigurator) axisConfig.getAxisConfiguration().getConfigurator()).getRepoLocation(),
                    serviceCatalogConfiguration);
            serviceCatalogExecutor.execute(serviceDeployer);
        }
    }

    public void deployCarbonAppsDirectory() {

        File cAppDirectory = new File(this.appsDir);
        File[] cAppFiles = cAppDirectory.listFiles();
        if (cAppFiles.length == 0) {
            return;
        }
        for (File cAppFile : cAppFiles) {
            if (cAppFile.isDirectory()) {
                try {
                    deployApps(cAppFile.getAbsolutePath());
                } catch (CarbonException e) {
                    log.error("Error while deploying carbon application " + cAppFile.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * Extracts the carbon application to the tmp/carbonapps directory.
     *
     * @param targetCAppPath - path of the carbon application
     * @return - path to the extracted carbon application
     * @throws CarbonException - error while extracting
     */
    private String extractCarbonApplication(String targetCAppPath) throws CarbonException {

        return AppDeployerUtils.extractCarbonApp(targetCAppPath);
    }

    /**
     * Builds the carbon application from app configuration created using the artifacts.xml path.
     *
     * @param cappPath             - path of the carbon application
     * @param extractedCAppDirectory - path to extracted carbon application
     * @param cAppName             - name of the carbon application
     * @param axisConfig           - AxisConfiguration instance
     * @return - CarbonApplication instance if successfull. otherwise null..
     * @throws CarbonException - error while building
     */
    private CarbonApplication buildCarbonApplication(String cappPath, String extractedCAppDirectory, String cAppName,
                                                     AxisConfiguration axisConfig) throws CarbonException {

        // Build the app configuration by providing the artifacts.xml path
        ApplicationConfiguration appConfig = new ApplicationConfiguration(extractedCAppDirectory);

        // If we don't have features (artifacts) for this server, ignore
        if (appConfig.getApplicationArtifact().getDependencies().isEmpty()) {
            log.warn("No artifacts found to be deployed in this server. " +
                    "Ignoring Carbon Application : " + cAppName);
            return null;
        }

        CarbonApplication carbonApplication = new CarbonApplication();
        carbonApplication.setAppFilePath(cappPath);
        carbonApplication.setExtractedPath(extractedCAppDirectory);
        carbonApplication.setAppConfig(appConfig);

        // Set App Name
        String appName = appConfig.getAppName();
        if (appName == null) {
            log.warn("No application name found in Carbon Application : " + cAppName + ". Using " +
                    "the file name as the application name");
            appName = cAppName.substring(0, cAppName.lastIndexOf('.'));
        }
        // to support multiple capp versions, we check app name with version
        if (appExists(appConfig.getAppNameWithVersion(), axisConfig)) {
            String msg =
                    "Carbon Application : " + appConfig.getAppNameWithVersion() + " already exists. Two applications " +
                            "can't have the same Id. Deployment aborted.";
            log.error(msg);
            throw new CarbonException(msg);
        }
        carbonApplication.setAppName(appName);

        // Set App Version
        String appVersion = appConfig.getAppVersion();
        if (appVersion != null && !("").equals(appVersion)) {
            carbonApplication.setAppVersion(appVersion);
        }
        String mainSeq = appConfig.getMainSequence();
        if (mainSeq != null && !("").equals(mainSeq)) {
            carbonApplication.setMainSequence(mainSeq);
        }
        return carbonApplication;
    }

    /**
     * Check whether there is an already existing Carbon application with the given name. Use app name with version to
     * support multiple capp versions
     *
     * @param newAppNameWithVersion - name of the new app
     * @param axisConfig            - AxisConfiguration instance
     * @return - true if exits
     */
    private boolean appExists(String newAppNameWithVersion, AxisConfiguration axisConfig) {
        CarbonApplication appToRemove = null;
        for (CarbonApplication carbonApp : getCarbonApps()) {
            if (newAppNameWithVersion.equals(carbonApp.getAppNameWithVersion())) {
                if (carbonApp.isDeploymentCompleted()) {
                    return true;
                } else {
                    appToRemove = carbonApp;
                    break;
                }
            }
        }
        if (appToRemove != null) {
            undeployCarbonApp(appToRemove, axisConfig);
        }
        return false;
    }

    /**
     * Deletes a directory given it's path.
     *
     * @param path the path of the directory to be deleted
     */
    private void deleteExtractedCApp(String path) {
        try {
            FileUtils.deleteDirectory(new File(path));
        } catch (IOException e) {
            log.warn("Unable to locate: " + path);
        }
    }

    /**
     * Add a new carbon application to cAppMap.
     *
     * @param carbonApp - CarbonApplication instance
     */
    private void addCarbonApp(CarbonApplication carbonApp) {
        synchronized (lock) {
            cAppMap.add(carbonApp);
        }
    }

    /**
     * Get the list of CarbonApplications. If the list is null, return an empty ArrayList.
     *
     * @return - list of cApps
     */
    public static List<CarbonApplication> getCarbonApps() {
        return Collections.unmodifiableList(cAppMap);
    }

    public static CarbonApplication getCarbonAppByName(String cAppName) {
        for (CarbonApplication capp : cAppMap) {
            if (cAppName.equals(capp.getAppName())) {
                return capp;
            }
        }
        return null;
    }

    /**
     * Checks whether a given file is a jar or an aar file.
     *
     * @param filename file to check
     * @return Returns boolean.
     */
    private boolean isCAppArchiveFile(String filename) {
        return (filename.endsWith(".car"));
    }

    /**
     * Function to register application deployers.
     *
     * @param handler - app deployer which implements the AppDeploymentHandler interface
     */
    public synchronized void registerDeploymentHandler(AppDeploymentHandler handler) {
        appDeploymentHandlers.add(handler);
    }

    /**
     * Deploys all artifacts under a root artifact.
     *
     * @param rootDirPath - root dir of the extracted artifact
     * @param parentApp   - capp instance
     * @throws org.wso2.micro.core.util.CarbonException - on error
     */
    private void searchArtifacts(String rootDirPath, CarbonApplication parentApp) throws CarbonException {
        SynapseConfiguration synapseConfiguration =
                SynapseConfigUtils.getSynapseConfiguration(SUPER_TENANT_DOMAIN_NAME);
        // For each CAPP initiate again.
        Map<String, String> swaggerTable = new HashMap<String, String>();
        Map<String, String> apiArtifactMap = new HashMap<String, String>();
        File extractedDir = new File(rootDirPath);
        File[] allFiles = extractedDir.listFiles();
        if (allFiles == null) {
            return;
        }

        // list to keep all artifacts
        List<Artifact> allArtifacts = new ArrayList<Artifact>();

        // search for all directories under the extracted path
        for (File artifactDirectory : allFiles) {
            if (!artifactDirectory.isDirectory()) {
                continue;
            }

            String directoryPath = AppDeployerUtils.formatPath(artifactDirectory.getAbsolutePath());
            String artifactXmlPath = directoryPath + File.separator + Artifact.ARTIFACT_XML;

            File f = new File(artifactXmlPath);
            // if the artifact.xml not found, ignore this dir
            if (!f.exists()) {
                // Add swagger files to the synapse configuration context.
                if (directoryPath.endsWith(METADATA_FOLDER_NAME)) {
                    File[] metadataFiles = new File(directoryPath).listFiles();
                    for (File metaFile : metadataFiles) {
                        if (metaFile.isDirectory()) {
                            try {
                                InputStream xmlInputStream =
                                        new FileInputStream(new File(metaFile, ARTIFACT_FILE));
                                Artifact artifact = this.buildAppArtifact(parentApp, xmlInputStream);
                                Artifact parentArtifact = parentApp.getAppConfig().getApplicationArtifact();
                                // Removing metadata dependencies from the CAPP parent artifact
                                boolean removed =
                                        parentArtifact.getDependencies()
                                                .removeIf(c -> c.getName().equals(artifact.getName()));
                                if (removed)
                                    parentArtifact.unresolvedDepCount--;

                                if (metaFile.getName().contains(SWAGGER_SUBSTRING)) {
                                    File swaggerFile = new File(metaFile, artifact.getFiles().get(0).getName());
                                    byte[] bytes = Files.readAllBytes(Paths.get(swaggerFile.getPath()));
                                    String artifactName = artifact.getName()
                                            .substring(0, artifact.getName().indexOf(SWAGGER_SUBSTRING));
                                    swaggerTable.put(artifactName, new String(bytes));
                                }
                            } catch (FileNotFoundException e) {
                                log.error("Could not find the Artifact.xml file for the metadata", e);
                            } catch (IOException e) {
                                log.error("Error occurred while reading the swagger file from metadata", e);
                            }
                        }
                    }
                }
                continue;
            }

            Artifact artifact = null;
            InputStream xmlInputStream = null;
            try {
                xmlInputStream = new FileInputStream(f);
                artifact = this.buildAppArtifact(parentApp, xmlInputStream);
                // If artifact is an API, add apiMapping to the synapse configuration.
                if (artifact.getType().equals(API_TYPE)) {
                    String apiXmlPath = directoryPath + File.separator + artifact.getFiles().get(0).getName();
                    String apiName = getApiNameFromFile(new FileInputStream(apiXmlPath));
                    if (!StringUtils.isEmpty(apiName)) {
                        // Re-constructing swagger table with API name since artifact name is not unique
                        apiArtifactMap.put(artifact.getName(),apiName);
                    }
                }
            } catch (FileNotFoundException e) {
                handleException("artifacts.xml File cannot be loaded from " + artifactXmlPath, e);
            } finally {
                if (xmlInputStream != null) {
                    try {
                        xmlInputStream.close();
                    } catch (IOException e) {
                        log.error("Error while closing input stream.", e);
                    }
                }
            }

            if (artifact == null) {
                return;
            }
            artifact.setExtractedPath(directoryPath);
            allArtifacts.add(artifact);
        }
        Artifact appArtifact = parentApp.getAppConfig().getApplicationArtifact();
        buildDependencyTree(appArtifact, allArtifacts);
        for (String artifactName : swaggerTable.keySet()) {
            String apiname = apiArtifactMap.get(artifactName);
            if (!StringUtils.isEmpty(apiname)) {
                synapseConfiguration.addSwaggerDefinition(apiname, swaggerTable.get(artifactName));
            }
        }
    }

    /**
     * Builds the artifact from the given input steam. Then adds it as a dependency in the provided parent carbon
     * application.
     *
     * @param parentApp         - parent application
     * @param artifactXmlStream - xml input stream of the artifact.xml
     * @return - Artifact instance if successfull. otherwise null..
     * @throws CarbonException - error while building
     */
    private Artifact buildAppArtifact(CarbonApplication parentApp, InputStream artifactXmlStream)
            throws CarbonException {
        Artifact artifact = null;
        try {
            OMElement artElement = new StAXOMBuilder(artifactXmlStream).getDocumentElement();

            if (Artifact.ARTIFACT.equals(artElement.getLocalName())) {
                artifact = AppDeployerUtils.populateArtifact(artElement);
            } else {
                log.error("artifact.xml is invalid. Parent Application : "
                                  + parentApp.getAppNameWithVersion());
                return null;
            }
        } catch (XMLStreamException e) {
            handleException("Error while parsing the artifact.xml file ", e);
        }

        if (artifact == null || artifact.getName() == null) {
            log.error("Invalid artifact found in Carbon Application : " + parentApp.getAppNameWithVersion());
            return null;
        }
        return artifact;
    }

    /**
     * Checks whether the given cApp artifact is complete with all it's dependencies. Recursively checks all it's
     * dependent artifacts as well..
     *
     * @param rootArtifact - artifact to check
     * @return true if ready, else false
     */
    private boolean isArtifactReadyToDeploy(Artifact rootArtifact) {
        if (rootArtifact == null) {
            return false;
        }
        boolean isReady = true;
        for (Artifact.Dependency dep : rootArtifact.getDependencies()) {
            isReady = isArtifactReadyToDeploy(dep.getArtifact());
            if (!isReady) {
                return false;
            }
        }
        if (rootArtifact.unresolvedDepCount > 0) {
            isReady = false;
        }
        return isReady;
    }

    /**
     * If the given artifact is a dependent artifact for the rootArtifact, include it as the actual dependency. The
     * existing one is a dummy one. So remove it. Do this recursively for the dependent artifacts as well..
     *
     * @param rootArtifact - root to start search
     * @param allArtifacts - all artifacts found under current cApp
     */
    private void buildDependencyTree(Artifact rootArtifact, List<Artifact> allArtifacts) {
        for (Artifact.Dependency dep : rootArtifact.getDependencies()) {
            for (Artifact temp : allArtifacts) {
                if (dep.getName().equals(temp.getName())) {
                    String depVersion = dep.getVersion();
                    String attVersion = temp.getVersion();
                    if ((depVersion == null && attVersion == null) ||
                            (depVersion != null && depVersion.equals(attVersion))) {
                        dep.setArtifact(temp);
                        rootArtifact.unresolvedDepCount--;
                        break;
                    }
                }
            }

            // if we've found the dependency, check for it's dependencies as well..
            if (dep.getArtifact() != null) {
                buildDependencyTree(dep.getArtifact(), allArtifacts);
            }
        }
    }

    private void handleException(String msg, Exception e) throws CarbonException {
        log.error(msg, e);
        throw new CarbonException(msg, e);
    }

    /**
     * Undeploys the cApp from system when the .car file is deleted from the repository. Find the relevant cApp using
     * the file path and call the undeploy method on applicationManager.
     *
     * @param filePath - deleted .car file path
     * @throws DeploymentException - error while un-deploying cApp
     */
    public void undeploy(String filePath) throws DeploymentException {
        CarbonApplication existingApp = null;
        for (CarbonApplication carbonApp : getCarbonApps()) {
            if (filePath.equals(carbonApp.getAppFilePath())) {
                existingApp = carbonApp;
                break;
            }
        }
        if (existingApp != null) {
            undeployCarbonApp(existingApp, axisConfig);
        } else {
            log.info("Undeploying Faulty Carbon Application On : " + filePath);
            removeFaultyCarbonApp(filePath);
        }
        super.undeploy(filePath);
    }

    /**
     * Undeploy the provided carbon App by sending it through the registered undeployment handler chain.
     *
     * @param carbonApp  - CarbonApplication instance
     * @param axisConfig - AxisConfiguration of the current tenant
     */
    private void undeployCarbonApp(CarbonApplication carbonApp,
                                   AxisConfiguration axisConfig) {
        log.info("Undeploying Carbon Application : " + carbonApp.getAppNameWithVersion() + "...");
        // Call the undeployer handler chain
        try {
            for (int handlerIndex = appDeploymentHandlers.size() - 1; handlerIndex >= 0; handlerIndex--) {
                AppDeploymentHandler handler = appDeploymentHandlers.get(handlerIndex);
                handler.undeployArtifacts(carbonApp, axisConfig);
            }
            // Remove the app from cAppMap list
            removeCarbonApp(carbonApp);

            // Remove the app from registry
            // removing the extracted CApp form tmp/carbonapps/
            FileManipulator.deleteDir(carbonApp.getExtractedPath());
            log.info("Successfully undeployed Carbon Application : " + carbonApp.getAppNameWithVersion()
                             + AppDeployerUtils.getTenantIdLogString(AppDeployerUtils.getTenantId()));
        } catch (Exception e) {
            log.error("Error occurred while trying to unDeploy  : " + carbonApp.getAppNameWithVersion(), e);
        }
    }

    /**
     * Remove a carbon application from cAppMap.
     *
     * @param carbonApp - CarbonApplication instance
     */
    private void removeCarbonApp(CarbonApplication carbonApp) {
        synchronized (lock) {
            cAppMap.remove(carbonApp);
        }
    }

    /**
     * Remove a faulty cApp from faultyCapps.
     *
     * @param appFilePath - file path to faulty carbon application
     */
    void removeFaultyCarbonApp(String appFilePath) {
        synchronized (lock) {
            String cAppName = appFilePath.substring(appFilePath.lastIndexOf(File.separator) + 1);
            faultyCapps.remove(cAppName);
            for (CarbonApplication application : faultyCAppObjects) {
                if (application.getAppFilePath().equals(appFilePath)) {
                    faultyCAppObjects.remove(application);
                    break;
                }
            }
        }
    }

    /**
     * Get a list of faulty CAPPs in the server.
     *
     * @return list of faulty CAPPs
     */
    public static List<String> getFaultyCapps() {
        return Collections.unmodifiableList(faultyCapps);
    }

    /**
     * Get a list of faulty cApp objects in the server.
     *
     * @return list of faulty cApp objects
     */
    public static List<CarbonApplication> getFaultyCAppObjects() {
        return Collections.unmodifiableList(faultyCAppObjects);
    }

    public void cleanup() {
        //cleanup the capp list during the unload
        cAppMap.clear();
        faultyCapps.clear();
        faultyCAppObjects.clear();
    }

    public void setSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {
        this.secretCallbackHandlerService = secretCallbackHandlerService;
    }

    /**
     * Partially building the API to get the API name
     *
     * @param apiXmlStream input stream of the API file.
     * @return name of the API.
     */
    private String getApiNameFromFile(InputStream apiXmlStream) {

        try {
            OMElement apiElement = new StAXOMBuilder(apiXmlStream).getDocumentElement();
            API api = DeployerUtil.partiallyBuildAPI(apiElement);
            return api.getName();
        } catch (XMLStreamException | OMException e) {
            // Cannot find the API file or API is faulty.
            // This error is properly handled later in the deployers and CAPP will go faulty.
            // Hence the exception is not propagated from here.
            return null;
        }
    }
}
