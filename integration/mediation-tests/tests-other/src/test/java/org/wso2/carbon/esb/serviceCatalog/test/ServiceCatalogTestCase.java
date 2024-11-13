/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.esb.serviceCatalog.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.base.CarbonBaseUtils;
import org.wso2.carbon.integration.common.utils.exceptions.AutomationUtilException;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;
import org.wso2.micro.application.deployer.AppDeployerUtils;
import org.wso2.micro.core.util.CarbonException;
import org.yaml.snakeyaml.Yaml;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * This class will test the service catalog feature.
 */
public class ServiceCatalogTestCase extends ESBIntegrationTest {

    private static final String FAULTY_CAPP = "invalidCompositeApplication_1.0.0.car";
    private static final String CAPP_WITH_META_AND_ENV = "blaCompositeExporter_1.0.0-SNAPSHOT.car";
    private static final String CAPP_WITH_PROXY_META = "proxyCompositeExporter_1.0.0-SNAPSHOT.car";
    private static final String CAPP_WITHOUT_META = "HelloWorldWithoutMetadataCompositeExporter_1.0.0-SNAPSHOT.car";
    private static final String NEW_CAPP_NAME = "demoCompositeExporter_1.0.0-SNAPSHOT.car";
    private static final String MODIFIED_NEW_CAPP_NAME = "changed_demoCompositeExporter_1.0.0-SNAPSHOT.car";
    private static final String SH_FILE_NAME = "micro-integrator.sh";
    private static final String BAT_FILE_NAME = "micro-integrator.bat";
    private static final String SERVICE_URL = "serviceUrl";
    private static final String SERVICE_CATALOG_FOLDER_NAME = "ServiceCatalog";
    private static final String ZIP_FILE_NAME = "payload.zip";
    private static final String TOML_FILE = "deployment.toml";
    private static final String SERVICE_CATALOG_FOLDER = "serviceCatalog";
    private ServerConfigurationManager serverConfigurationManager;
    private CarbonLogReader carbonLogReader = null;
    private HttpsServer httpsServer = null;

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {
        super.init();
        int PORT = 9654;
        Utils.shutdownFailsafe(PORT);
        // Enabling service catalog
        serverConfigurationManager = new ServerConfigurationManager(context);
        serverConfigurationManager.applyMIConfigurationWithRestart(new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator + "FirstAPI" +
                        File.separator + TOML_FILE));
        carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();

        InetSocketAddress socketAddress = new InetSocketAddress(PORT);
        httpsServer = HttpsServer.create(socketAddress, 0);
        SSLContext sslContext = SSLContext.getInstance("TLS");

        File keystore = new File(CarbonBaseUtils.getCarbonHome() + File.separator + "repository" + File.separator +
                "resources" + File.separator + "security" + File.separator + "wso2carbon.jks");

        char[] password = "wso2carbon".toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        FileInputStream fis = new FileInputStream(keystore);
        ks.load(fis, password);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, password);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        HttpsConfigurator httpsConfigurator = new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters httpsParameters) {
                SSLContext sslContext = getSSLContext();
                SSLParameters defaultSSLParameters = sslContext.getDefaultSSLParameters();
                httpsParameters.setSSLParameters(defaultSSLParameters);
            }
        };

        httpsServer.createContext("/api/am/service-catalog/v1/services", new FirstController());
        httpsServer.createContext("/second/api/am/service-catalog/v1/services", new SecondController());
        httpsServer.createContext("/third/api/am/service-catalog/v1/services", new ThirdController());
        httpsServer.createContext("/fourth/api/am/service-catalog/v1/services", new FourthController());
        httpsServer.setExecutor(Executors.newCachedThreadPool());
        httpsServer.setHttpsConfigurator(httpsConfigurator);
        httpsServer.start();
    }

    @Test(groups = {"wso2.esb"},
            description = "Test Service Catalog with a faulty CAPP", priority = 1)
    public void testServiceCatalogWithFaultyCAPP() throws InterruptedException, AutomationUtilException {
        serverConfigurationManager.restartMicroIntegrator();
        assertTrue(Utils.checkForLog(carbonLogReader, "Faulty CAPPs detected - aborting the service-catalog " +
                "uploader", 10), "Did not receive the expected info log");
    }

    @Test(groups = {"wso2.esb"},
            description = "Test service catalog without faulty and new CAPPs (CAPPs which has metadata)", priority = 2)
    public void testServiceCatalogWithoutFaultyCAPP()
            throws IOException, URISyntaxException, AutomationUtilException, InterruptedException {
        serverConfigurationManager.removeFromCarbonapps(FAULTY_CAPP);
        serverConfigurationManager.restartMicroIntegrator();
        assertTrue(Utils.checkForLog(carbonLogReader,
                "Metadata not included, hence not publishing to Service Catalog", 10),
                "Did not receive the expected info log");
    }

    @Test(groups = {"wso2.esb"},
            description = "Test service catalog by hot deploying CAapp without Metadata)", priority = 3)
    public void testServiceCatalogHotDeploymentWithoutMetaData()
            throws IOException, URISyntaxException, AutomationUtilException, InterruptedException {
        carbonLogReader.clearLogs();
        File metadataCAPP = new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator +
                        CAPP_WITHOUT_META);
        serverConfigurationManager.copyToCarbonapps(metadataCAPP);
        assertTrue(Utils.checkForLog(carbonLogReader,
                        "Metadata not included, hence not publishing to Service Catalog", 20),
                "Did not receive the expected info log");
        serverConfigurationManager.removeFromCarbonapps(CAPP_WITHOUT_META);
    }

    @Test(groups = {"wso2.esb"},
            description = "Test service catalog by hot deploying CAapp with Metadata)", priority = 4)
    public void testServiceCatalogHotDeploymentWithMetaData()
            throws IOException, URISyntaxException, AutomationUtilException, InterruptedException {
        carbonLogReader.clearLogs();
        File metadataCAPP = new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator +
                        CAPP_WITH_META_AND_ENV);
        serverConfigurationManager.copyToCarbonapps(metadataCAPP);
        assertTrue(Utils.checkForLog(carbonLogReader,
                        "Successfully updated the service catalog", 20),
                "Did not receive the expected info log");
        serverConfigurationManager.removeFromCarbonapps(CAPP_WITH_META_AND_ENV);
    }

    @Test(groups = {"wso2.esb"},
            description = "Test service catalog without setting env variables", priority = 5)
    public void testServiceCatalogMetadataWithoutEnv()
            throws IOException, AutomationUtilException, InterruptedException {
        File metadataCAPP = new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator +
                        CAPP_WITH_META_AND_ENV);
        serverConfigurationManager.copyToCarbonapps(metadataCAPP);
        serverConfigurationManager.restartMicroIntegrator();
        assertTrue(Utils.checkForLog(carbonLogReader,
                "Successfully updated the service catalog", 10), "Did not receive the expected info log");
    }

    @Test(groups = {"wso2.esb"},
            description = "Test service catalog after setting env variables", priority = 6)
    public void testServiceCatalogMetadataWithEnv()
            throws IOException, AutomationUtilException, InterruptedException {

        // Set env variables for linux and mac
        File newShFile = new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator + SH_FILE_NAME);
        File oldShFile =
                new File(CarbonBaseUtils.getCarbonHome() + File.separator + "bin" + File.separator + SH_FILE_NAME);
        serverConfigurationManager.applyConfigurationWithoutRestart(newShFile, oldShFile, true);

        // Set env variables for Windows
        File newBatFile = new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator + BAT_FILE_NAME);
        File oldBatFile =
                new File(CarbonBaseUtils.getCarbonHome() + File.separator + "bin" + File.separator + BAT_FILE_NAME);
        serverConfigurationManager.applyConfigurationWithoutRestart(newBatFile, oldBatFile, true);

        serverConfigurationManager.restartMicroIntegrator();
        assertTrue(Utils.checkForLog(carbonLogReader,
                "Successfully updated the service catalog", 10), "Did not receive the expected info log");
    }

    @Test(groups = {"wso2.esb"},
            description = "Test the ZIP file created by the service catalog", priority = 7)
    public void testServiceCatalogZipFile() throws CarbonException, FileNotFoundException, InterruptedException {
        File extracted = checkAndExtractPayloadZip();
        assertTrue(extracted.exists(), "Error occurred while extracting the ZIP");
        File metadataFile = new File(extracted, "healthcare_v1.0.0-SNAPSHOT");
        File yamlFile = new File(metadataFile, "metadata.yaml");
        assertTrue(yamlFile.exists(), "Could not find the metadata yaml file");
        Yaml yaml = new Yaml();
        Map<String, Object> obj =
                (Map<String, Object>) yaml.load(new FileInputStream(yamlFile));
        String currentServiceUrl = (String) obj.get(SERVICE_URL);
        Assert.assertEquals("https://localhost:8290/health", currentServiceUrl, "Parameterized server url creation " +
                "failed");
    }

    @Test(groups = {"wso2.esb"},
            description = "Test MI is uploading only newly added APIs", priority = 8)
    public void testUploadOnlyNewAPIs()
            throws CarbonException, IOException, AutomationUtilException, InterruptedException {
        File newCAPP = new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator + NEW_CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(newCAPP);
        serverConfigurationManager.applyMIConfigurationWithRestart(new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator + "SecondAPI" +
                        File.separator + TOML_FILE));
        assertTrue(Utils.checkForLog(carbonLogReader,
                "Successfully updated the service catalog", 10), "Did not receive the expected info log");
        File extracted = checkAndExtractPayloadZip();
        assertTrue(extracted.exists(), "Error occurred while extracting the ZIP");
        assertFalse(checkMetadataFileExists(extracted, "healthcare_v1.0.0-SNAPSHOT"),
                "healthcare API should not be uploaded again");
        assertTrue(checkMetadataFileExists(extracted, "SwaggerPetstore_v1.0.0-SNAPSHOT"), "Could not find " +
                "metadata yaml for petstore API");
        assertTrue(checkMetadataFileExists(extracted, "api1_v1.0.0-SNAPSHOT"), "Could not find " +
                "metadata yaml for api1");
    }

    @Test(groups = {"wso2.esb"},
            description = "Test MI is uploading only modified APIs", priority = 9)
    public void testUploadOnlyModifiedAPIs()
            throws CarbonException, IOException, AutomationUtilException, URISyntaxException, InterruptedException {
        // remove CAPP and add the modified one
        serverConfigurationManager.removeFromCarbonapps(NEW_CAPP_NAME);
        File newCAPP = new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator +
                        MODIFIED_NEW_CAPP_NAME);
        serverConfigurationManager.copyToCarbonapps(newCAPP);
        serverConfigurationManager.applyMIConfigurationWithRestart(new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator + "ThirdAPI" +
                        File.separator + TOML_FILE));
        assertTrue(Utils.checkForLog(carbonLogReader,
                "Successfully updated the service catalog", 10), "Did not receive the expected info log");
        File extracted = checkAndExtractPayloadZip();
        assertTrue(extracted.exists(), "Error occurred while extracting the ZIP");
        assertFalse(checkMetadataFileExists(extracted, "healthcare_v1.0.0-SNAPSHOT"),
                "healthcare API should not be uploaded again");
        assertFalse(checkMetadataFileExists(extracted, "SwaggerPetstore_v1.0.0-SNAPSHOT"), "Petstore API " +
                "should not be uploaded again");
        assertTrue(checkMetadataFileExists(extracted, "api1_v1.0.0-SNAPSHOT"), "Could not find metadata yaml for api1");
    }

    @Test(groups = {"wso2.esb"},
            description = "Test restart MI without any CAPP changes", priority = 10)
    public void testMIRestart() throws IOException, AutomationUtilException, InterruptedException {
        serverConfigurationManager.applyMIConfigurationWithRestart(new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator + "FourthAPI" +
                        File.separator + TOML_FILE));
        String payloadZipPath = CarbonBaseUtils.getCarbonHome() + File.separator + "tmp" + File.separator +
                SERVICE_CATALOG_FOLDER_NAME + File.separator + ZIP_FILE_NAME;
        assertTrue(Utils.checkForLog(carbonLogReader,
                "Service catalog already contains the latest configs, aborting the service-catalog uploader", 10),
                "Did not receive the expected info log");
        File zipFile = new File(payloadZipPath);
        assertFalse(zipFile.exists(), "Payload.zip file should not be created");
    }

    @Test(groups = {"wso2.esb"}, description = "Test service catalog with proxy services", priority = 11)
    public void testServiceCatalogProxyServiceMetadata()
            throws CarbonException, IOException, AutomationUtilException, InterruptedException {
        if (!Boolean.parseBoolean(System.getenv("CI_BUILD_SKIP"))) {
            File metadataCAPP = new File(getESBResourceLocation() + File.separator
                    + SERVICE_CATALOG_FOLDER + File.separator + CAPP_WITH_PROXY_META);
            serverConfigurationManager.copyToCarbonapps(metadataCAPP);
            // replace server startup scripts
            String shFile = CarbonBaseUtils.getCarbonHome() + File.separator + "bin" + File.separator + SH_FILE_NAME;
            String batFile = CarbonBaseUtils.getCarbonHome() + File.separator + "bin" + File.separator + BAT_FILE_NAME;
            File oldShFile = new File(shFile + ".backup");
            File newShFile = new File(shFile);
            if (new File(shFile).delete() && oldShFile.renameTo(newShFile)) {
                assertTrue(newShFile.exists(), "Error while replacing default sh script");
            }
            File oldBatFile = new File(batFile + ".backup");
            File newBatFile = new File(batFile);
            if (new File(batFile).delete() && oldBatFile.renameTo(newBatFile)) {
                assertTrue(newBatFile.exists(), "Error while replacing default bat script");
            }
            serverConfigurationManager.restartMicroIntegrator();
            assertTrue(Utils.checkForLog(carbonLogReader,
                    "Successfully updated the service catalog", 10), "Did not receive the expected info log");
            File extracted = checkAndExtractPayloadZip();
            assertTrue(extracted.exists(), "Error occurred while extracting the ZIP");
            File metadataFile = new File(extracted, "SampleProxyService_proxy_v1.0.0");
            File yamlFile = new File(metadataFile, "metadata.yaml");
            assertTrue(yamlFile.exists(), "Could not find the metadata yaml file");
            File wsdlFile = new File(metadataFile, "definition.wsdl");
            assertTrue(wsdlFile.exists(), "Could not find the definition wsdl file");
        }
    }

    private static File checkAndExtractPayloadZip() throws CarbonException, InterruptedException {
        Thread.sleep(1000);
        String payloadZipPath = CarbonBaseUtils.getCarbonHome() + File.separator + "tmp" + File.separator +
                SERVICE_CATALOG_FOLDER_NAME + File.separator + ZIP_FILE_NAME;
        File zipFile = new File(payloadZipPath);
        assertTrue(zipFile.exists(), "Could not find the payload.zip in the temp folder");
        String tempExtractedDirPath = AppDeployerUtils.extractCarbonApp(payloadZipPath);
        return new File(tempExtractedDirPath);
    }

    private static boolean checkMetadataFileExists(File extractedLocation, String foldername) {
        File extracted = new File(extractedLocation, foldername);
        if (extracted.exists()) {
            File metaFile = new File(extracted, "metadata.yaml");
            return metaFile.exists();
        }
        return false;
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        // Add the faulty CAPP back and restart the server
        serverConfigurationManager.copyToCarbonapps(new File(
                getESBResourceLocation() + File.separator + SERVICE_CATALOG_FOLDER + File.separator + FAULTY_CAPP));
        httpsServer.stop(1);
        super.cleanup();
        serverConfigurationManager.restartGracefully();
    }

    private static class FirstController implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (OutputStream responseBody = exchange.getResponseBody()) {
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                String payload = "{\n" +
                        "    \"list\": [],\n" +
                        "    \"pagination\": {\n" +
                        "        \"offset\": 0,\n" +
                        "        \"limit\": 25,\n" +
                        "        \"total\": 0,\n" +
                        "        \"next\": \"\",\n" +
                        "        \"previous\": \"\"\n" +
                        "    }\n" +
                        "}";
                exchange.sendResponseHeaders(200, payload.length());
                responseBody.write(payload.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class SecondController implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (OutputStream responseBody = exchange.getResponseBody()) {
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                File secondResponse = new File(
                        getESBResourceLocation() + File.separator + "serviceCatalog" + File.separator +
                                "Scenario2JsonResponse.json");
                String payload = FileUtils.readFileToString(secondResponse, StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, payload.length());
                responseBody.write(payload.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class ThirdController implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (OutputStream responseBody = exchange.getResponseBody()) {
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                File thirdResponse = new File(
                        getESBResourceLocation() + File.separator + "serviceCatalog" + File.separator +
                                "Scenario3JsonResponse.json");
                String payload = FileUtils.readFileToString(thirdResponse, StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, payload.length());
                responseBody.write(payload.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class FourthController implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (OutputStream responseBody = exchange.getResponseBody()) {
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                File thirdResponse = new File(
                        getESBResourceLocation() + File.separator + "serviceCatalog" + File.separator +
                                "Scenario4JsonResponse.json");
                String payload = FileUtils.readFileToString(thirdResponse, StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, payload.length());
                responseBody.write(payload.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
