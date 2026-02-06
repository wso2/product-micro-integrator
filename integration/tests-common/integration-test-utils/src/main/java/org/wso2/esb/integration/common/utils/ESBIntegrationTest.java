/*
 *Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *WSO2 Inc. licenses this file to you under the Apache License,
 *Version 2.0 (the "License"); you may not use this file except
 *in compliance with the License.
 *You may obtain a copy of the License at
 *
 *http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an
 *"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *KIND, either express or implied.  See the License for the
 *specific language governing permissions and limitations
 *under the License.
 */
package org.wso2.esb.integration.common.utils;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.synapse.SynapseException;
import org.awaitility.Awaitility;
import org.json.JSONObject;
import org.wso2.carbon.automation.engine.configurations.UrlGenerationUtil;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.beans.ContextUrls;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;
import org.wso2.esb.integration.common.utils.clients.stockquoteclient.StockQuoteClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPathExpressionException;

public abstract class ESBIntegrationTest {
    private static final String synapsePathFormBaseUri =
            File.separator + "repository" + File.separator + "deployment" + File.separator + "server" + File.separator
                    + "synapse-configs" + File.separator + "default" + File.separator + "synapse.xml";
    protected Log log = LogFactory.getLog(getClass());
    protected StockQuoteClient axis2Client;
    protected ContextUrls contextUrls = new ContextUrls();
    protected String sessionCookie;
    protected OMElement synapseConfiguration = null;
    protected ESBTestCaseUtils esbUtils;
    protected AutomationContext context;
    private List<String> proxyServicesList = null;
    private List<String> sequencesList = null;
    private List<String> endpointsList = null;
    private List<String> localEntryList = null;
    private List<String> messageProcessorsList = null;
    private List<String> messageStoresList = null;
    private List<String> sequenceTemplateList = null;
    private List<String> apiList = null;
    private List<String> priorityExecutorList = null;
    private List<String[]> scheduledTaskList = null;
    private List<String> inboundEndpointList = null;
    public static final int DEFAULT_INTERNAL_API_HTTPS_PORT = 9154;
    protected String hostName = null;
    protected int portOffset;
    protected final int DEFAULT_TIMEOUT = 60;
    protected boolean isManagementApiAvailable = false;
    private final String SERVER_DEPLOYMENT_DIR =
            System.getProperty(ESBTestConstant.CARBON_HOME) + File.separator + "repository" + File.separator
            + "deployment" + File.separator + "server" + File.separator + "synapse-configs" + File.separator
            + "default" + File.separator;
    protected final String PROXY_DIRECTORY = SERVER_DEPLOYMENT_DIR + File.separator + "proxy-services";

    protected void init() throws Exception {
        axis2Client = new StockQuoteClient();
        context = new AutomationContext();
        contextUrls = context.getContextUrls();
        esbUtils = new ESBTestCaseUtils();
        hostName = UrlGenerationUtil.getManagerHost(context.getInstance());
        portOffset = Integer.parseInt(System.getProperty("port.offset"));
        isManagementApiAvailable = false;
    }

    protected void initLight() {
        hostName = "localhost";
        portOffset = Integer.parseInt(System.getProperty("port.offset"));
        isManagementApiAvailable = false;
    }

    public String getHostname() {
        return this.hostName;
    }

    public int getPortOffset(){
        return this.portOffset;
    }

    protected void cleanup() throws Exception {
        // DO NOTHING;
    }

    protected String getMainSequenceURL() {
        return getMainSequenceURL(false);
    }

    protected String getMainSequenceURL(boolean https) {

        String mainSequenceUrl = contextUrls.getServiceUrl();
        if (https) {
            mainSequenceUrl = contextUrls.getSecureServiceUrl();
        }
        if (mainSequenceUrl.endsWith("/services")) {
            mainSequenceUrl = mainSequenceUrl.replace("/services", "");
        }
        if (!mainSequenceUrl.endsWith("/")) {
            mainSequenceUrl = mainSequenceUrl + "/";
        }
        return mainSequenceUrl;
    }

    protected String getProxyServiceURLHttp(String proxyServiceName) {
        return contextUrls.getServiceUrl() + "/" + proxyServiceName;
    }

    protected String getApiInvocationURL(String apiName) {
        return getMainSequenceURL() + apiName;
    }

    protected String getApiInvocationURLHttps(String apiName) {
        return getMainSequenceURL(true) + apiName;
    }

    protected String getProxyServiceURLHttps(String proxyServiceName) {
        return contextUrls.getSecureServiceUrl() + "/" + proxyServiceName;
    }

    protected void loadSampleESBConfiguration(int sampleNo) throws Exception {
        OMElement synapseSample = esbUtils.loadESBSampleConfiguration(sampleNo);
    }

    protected OMElement loadSampleESBConfigurationWithoutApply(int sampleNo) throws Exception {
        return esbUtils.loadESBSampleConfiguration(sampleNo);
    }

    protected void loadESBConfigurationFromClasspath(String relativeFilePath) throws Exception {
        // DO Nothing
    }

    protected void updateESBConfiguration(OMElement synapseConfig) throws Exception {

        // Do Nothing
    }

    protected void updateInboundEndpoint(OMElement inboundEndpoint) throws Exception {
        try {
            esbUtils.updateInboundEndpoint(contextUrls.getBackEndUrl(), sessionCookie, inboundEndpoint);
        } catch (Exception e) {
            throw new Exception("Error when adding InboundEndpoint", e);
        }
    }

    protected void updateESBRegistry(String resourcePath) throws Exception {

    }

    protected String getESBResourceLocation() {
        return FrameworkPathUtil.getSystemResourceLocation() + "artifacts" + File.separator + "ESB";
    }

    protected String getBackEndServiceUrl(String serviceName) throws XPathExpressionException {
        return EndpointGenerator.getBackEndServiceEndpointUrl(serviceName);
    }

    protected void verifyProxyServiceExistence(String proxyServiceName) throws RemoteException {
        /*Assert.assertTrue(esbUtils.isProxyServiceExist(contextUrls.getBackEndUrl(), sessionCookie, proxyServiceName),
                "Proxy Service not found. " + proxyServiceName);*/
    }

    protected boolean checkCarbonAppExistence(String carbonAppName) throws IOException {

        String response = retrieveArtifactUsingManagementApi("applications");
        return response.contains(carbonAppName);
    }

    protected boolean checkApiExistence(String apiName) throws IOException {

        String response = retrieveArtifactUsingManagementApi("apis");
        return response.contains(apiName);
    }

    protected boolean checkEndpointExistence(String endpoinName) throws IOException {

        String response = retrieveArtifactUsingManagementApi("endpoints");
        return response.contains(endpoinName);
    }

    protected boolean checkInboundEndpointExistence(String inboundEndpointName) throws IOException {

        String response = retrieveArtifactUsingManagementApi("inbound-endpoints");
        return response.contains(inboundEndpointName);
    }

    protected int getNoOfArtifacts(String artifactType) throws IOException {
        int count = 0;
        String response = retrieveArtifactUsingManagementApi(artifactType);
        JSONObject jsonObject = new JSONObject(response);
        if(jsonObject.has("count")) {
            count = jsonObject.getInt("count");
        }
        return count;
    }

    protected boolean checkProxyServiceExistence(String proxyServiceName) throws IOException {

        String response = retrieveArtifactUsingManagementApi("proxy-services");
        return response.contains(proxyServiceName);
    }

    protected String deployCarbonApplication(File carbonApp) throws IOException {

        return deployCarbonApplicationUsingManagementApi("applications", carbonApp);
    }

    protected String deployCarbonApplication(File carbonApp, Map<String, String> header) throws IOException {

        return deployCarbonApplicationUsingManagementApi("applications", carbonApp, header);
    }

    protected String unDeployCarbonApplication(String carbonApp) throws IOException {

        return unDeployCarbonApplicationUsingManagementApi("applications", carbonApp);
    }

    private boolean checkTaskExistence(String taskName) throws IOException {

        String response = retrieveArtifactUsingManagementApi("tasks");
        return response.contains(taskName);
    }

    protected boolean isArtifactDeployed(BooleanSupplier methodToCheck, int maxWaitTime) throws InterruptedException {

        for (int i = 0; i < maxWaitTime; i++) {
            if (methodToCheck.getAsBoolean()) {
                return true;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        return false;
    }

    protected boolean checkSequenceExistence(String sequenceName) {

        String response;
        try {
            response = retrieveArtifactUsingManagementApi("sequences");
        } catch (IOException e) {
            log.error(e);
            return false;
        }
        return response.contains(sequenceName);
    }

    protected boolean checkLocalEntryExistence(String localEntryName) throws IOException {

        String response = retrieveArtifactUsingManagementApi("local-entries");
        return response.contains(localEntryName);
    }

    protected boolean checkMessageStoreExistence(String messageStoreName) throws IOException {

        String response = retrieveArtifactUsingManagementApi("message-stores");
        return response.contains(messageStoreName);
    }

    protected boolean checkMessageProcessorExistence(String messageProcessorName) {

        String response;
        try {
            response = retrieveArtifactUsingManagementApi("message-processors");
        } catch (IOException e) {
            log.error(e);
            return false;
        }
        return response.contains(messageProcessorName);
    }

    private String retrieveArtifactUsingManagementApi(String artifactType) throws IOException {

        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }

        SimpleHttpClient client = new SimpleHttpClient();
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");

        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + artifactType;

        HttpResponse response = client.doGet(endpoint, headers);
        return client.getResponsePayload(response);
    }

    private String deployCarbonApplicationUsingManagementApi(String artifactType, File cabonApp) throws IOException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).
                    atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        SimpleHttpClient client = new SimpleHttpClient();

        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + artifactType;
        HttpResponse response = client.doPostWithMultipart(endpoint, cabonApp);
        return client.getResponsePayload(response);
    }

    private String deployCarbonApplicationUsingManagementApi(String artifactType, File cabonApp, Map<String, String> header) throws IOException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).
                    atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                              until(isManagementApiAvailable());
        }
        SimpleHttpClient client = new SimpleHttpClient();

        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                          + artifactType;
        HttpResponse response = client.doPostWithMultipart(endpoint, cabonApp, header);
        // throw error if statusCode is not 200
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IOException("Failed to deploy Carbon Application. HTTP error code: " + response.getStatusLine().getStatusCode()
                                  + ". Response: " + client.getResponsePayload(response));
        }
        return client.getResponsePayload(response);
    }

    private String unDeployCarbonApplicationUsingManagementApi(String artifactType, String cabonApp)
            throws IOException {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).
                    atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        SimpleHttpClient client = new SimpleHttpClient();

        String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                + artifactType + "/" + cabonApp;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        HttpResponse response = client.doDelete(endpoint, headers);
        return client.getResponsePayload(response);
    }

    public Callable<Boolean> isManagementApiAvailable() {
        return () -> {
            try (Socket s = new Socket(hostName, DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset)) {
                isManagementApiAvailable = true;
                return true;
            } catch (Exception e) {
                log.error("Error while opening socket for port " + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset), e);
                return false;
            }
        };
    }

    protected OMElement replaceEndpoints(String relativePathToConfigFile, String serviceName, String port)
            throws XMLStreamException, FileNotFoundException, XPathExpressionException {
        String config = esbUtils.loadResource(relativePathToConfigFile).toString();
        config = config
                .replace("http://localhost:" + port + "/services/" + serviceName, getBackEndServiceUrl(serviceName));

        return AXIOMUtil.stringToOM(config);
    }

    private String readInputStreamAsString(InputStream in) throws IOException {

        BufferedInputStream bis = new BufferedInputStream(in);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int result = bis.read();
        while (result != -1) {
            byte b = (byte) result;
            buf.write(b);
            result = bis.read();
        }
        return buf.toString();
    }

    protected String getSessionCookie() {
        return sessionCookie;
    }

    //todo - getting role as the user
    protected String[] getUserRole() {
        return new String[] { "admin" };
    }

    /**
     * This method to be used after restart the server to update the sessionCookie variable.
     *
     * @throws Exception
     */
    protected void reloadSessionCookie() throws Exception {
        /*context = new AutomationContext(ESBTestConstant.ESB_PRODUCT_GROUP, TestUserMode.SUPER_TENANT_ADMIN);
        sessionCookie = login(context);*/
    }

    /**
     * This method enables the HTTP wire logs in log4j2 properties file.
     *
     * @param logLevel - The log-level of synapse-transport-http-wire logger
     */
    public void configureHTTPWireLogs(String logLevel) {
        String loggerName = "synapse-transport-http-wire";
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).
                    until(isManagementApiAvailable());
        }
        try {
            SimpleHttpClient client = new SimpleHttpClient();
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");

            String endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/"
                    + "logging";

            JSONObject payload = new JSONObject();
            payload.put("loggerName", loggerName);
            payload.put("loggingLevel", logLevel);

            client.doPatch(endpoint, headers, payload.toString(), "application/json");
            Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).
                    atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS).until(isLogsConfigured(endpoint, loggerName, logLevel));
        } catch (IOException e) {
            throw new SynapseException("Error updating the log-level of synapse-transport-http-wire logger", e);
        }
    }

    private Callable<Boolean> isLogsConfigured(String endpoint, String loggerName, String logLevel) {
        return () -> isLogConfigured(endpoint + "?loggerName=" + loggerName, logLevel);
    }

    private boolean isLogConfigured(String endpoint, String logLevel) {
        try {
            SimpleHttpClient client = new SimpleHttpClient();
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");

            HttpResponse response = client.doGet(endpoint, headers);
            String responsePayload = client.getResponsePayload(response);
            if (response.getStatusLine().getStatusCode() != 200) {
                return false;
            }
            JSONObject jsonResponse = new JSONObject(responsePayload);
            return jsonResponse.get("level").toString().equals(logLevel);
        } catch (IOException e) {
            log.error("Error occurred while checking the log level", e);
            return false;
        }
    }

    private void copyArtifactToDeploymentDirectory(String sourceArtifactPath, String artifactName,
                                                   String deploymentDirectory) throws IOException {
        Files.copy(new File(sourceArtifactPath + File.separator + artifactName + ".xml").toPath(),
                   new File(deploymentDirectory + File.separator + artifactName + ".xml").toPath(),
                   StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteArtifactFromDeploymentDirectory(String artifactName, String deploymentDirectory) throws IOException {
        Files.delete(new File(deploymentDirectory + File.separator + artifactName).toPath());
    }

    protected void undeployProxyService(String name) throws IOException {
        deleteArtifactFromDeploymentDirectory(name + ".xml", PROXY_DIRECTORY);
    }

    protected void deployProxyService(String name, String resourcePath) throws IOException {
        copyArtifactToDeploymentDirectory(resourcePath, name, PROXY_DIRECTORY);
    }

}

