/*
 *Copyright (c) 2005, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.automation.extensions.servers.jmsserver.client.JMSQueueMessageConsumer;
import org.wso2.carbon.automation.extensions.servers.jmsserver.controller.config.JMSBrokerConfigurationProvider;
import org.wso2.esb.integration.common.clients.mediation.MessageStoreAdminClient;
import org.wso2.esb.integration.common.extensions.carbonserver.CarbonServerExtension;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import javax.xml.stream.XMLStreamException;

public class Utils {

    private static Log log = LogFactory.getLog(Utils.class);

    private static String defaultDeploymentDirectory = String.join(File.separator, System.getProperty("carbon.home"),
                                                                   "repository", "deployment");

    public enum ArtifactType {
        API("api"),
        ENDPOINT("endpoints"),
        INBOUND_ENDPOINT("inbound-endpoints"),
        LOCAL_ENTRY("local-entries"),
        MESSAGE_PROCESSOR("message-processors"),
        MESSAGE_STORES("message-stores"),
        PROXY("proxy-services"),
        SEQUENCE("sequences"),
        TASK("tasks"),
        TEMPLATE("templates");

        private String type;

        ArtifactType(String type) {
            this.type = type;
        }

        public String getDirName() {
            return type;
        }
    }

    public static OMElement getStockQuoteRequest(String symbol) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMNamespace omNs = fac.createOMNamespace("http://services.samples", "ns");
        OMElement method = fac.createOMElement("getQuote", omNs);
        OMElement value1 = fac.createOMElement("request", omNs);
        OMElement value2 = fac.createOMElement("symbol", omNs);

        value2.addChild(fac.createOMText(value1, symbol));
        value1.addChild(value2);
        method.addChild(value1);

        return method;
    }

    /**
     * method to kill existing servers which are bind to the given port
     *
     * @param port
     */
    public static void shutdownFailsafe(int port) throws IOException {
        try {
            Process p = Runtime.getRuntime().exec("lsof -i:" + port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            reader.readLine();
            line = reader.readLine();
            if (line != null) {
                line = line.trim();
                String processId = line.split(" +")[1];
                if (processId != null) {
                    String killStr = "kill -9 " + processId;
                    Runtime.getRuntime().exec(killStr);
                }
            }
        } catch (IOException e) {
            log.error("Error while shutting down the process in port " + port, e);
        }
    }

    /**
     * Check if the log contains the expected string. The search will be done for maximum 10 seconds.
     *
     * @param carbonLogReader carbon log reader
     * @param expected        expected string
     * @return true if a match found, false otherwise
     */
    public static boolean assertIfSystemLogContains(CarbonLogReader carbonLogReader, String expected) {
        boolean matchFound = false;
        long startTime = System.currentTimeMillis();
        while (!matchFound && (System.currentTimeMillis() - startTime) < 10000) {
            matchFound = assertIfLogExists(carbonLogReader, expected);
        }
        return matchFound;
    }

    private static boolean assertIfLogExists(CarbonLogReader carbonLogReader, String expected) {
        boolean matchFound = false;
        if (carbonLogReader != null) {
            if (carbonLogReader.getLogs().contains(expected)) {
                carbonLogReader.stop();
                matchFound = true;
            }
        }
        return matchFound;
    }

    /**
     * Checks if the expected string is available in the tailed logs of carbon log reader. Stops the log reader once found.
     * The polling will happen in one second intervals
     *
     * @param logReader Carbon log reader
     * @param expected  expected log string
     * @param timeout   max time to do polling
     * @throws InterruptedException if interrupted while sleeping
     */
    public static boolean logExists(CarbonLogReader logReader, String expected, int timeout)
            throws InterruptedException {
        boolean logExists = false;
        for (int i = 0; i < timeout; i++) {
            Thread.sleep(5000);
            if (logReader.getLogs().contains(expected)) {
                logExists = true;
                logReader.stop();
                break;
            }
        }
        return logExists;
    }

    /**
     * Check for the existence of the given log message. Does not stop the log reader
     * The polling will happen in one second intervals
     *
     * @param logReader Carbon log reader
     * @param expected  expected log string
     * @param timeout   max time to do polling
     * @throws InterruptedException if interrupted while sleeping
     */
    public static boolean checkForLog(CarbonLogReader logReader, String expected, int timeout)
            throws InterruptedException {
        boolean logExists = false;
        for (int i = 0; i < timeout; i++) {
            Thread.sleep(1000);
            if (logReader.getLogs().contains(expected)) {
                logExists = true;
                break;
            }
        }
        return logExists;
    }

    public static boolean checkForFileExistence(Path filePath, int timeout) throws InterruptedException {

        for (int i = 0; i < timeout; i++) {
            if (Files.exists(filePath)) {
                return true;
            }
            Thread.sleep(1000);
        }
        log.error("File does not exists in " + filePath, new Throwable());
        return false;
    }

    /**
     * Wait for expected message count found in the message store until a defined timeout
     *
     * @param messageStoreName Message store name
     * @param expectedCount    Expected message count
     * @param timeout          Timeout to wait in Milliseconds
     * @return true if the expected message count found, false otherwise
     */
    public static boolean waitForMessageCount(MessageStoreAdminClient messageStoreAdminClient, String messageStoreName,
                                              int expectedCount, long timeout)
            throws InterruptedException, RemoteException {
        long elapsedTime = 0;
        boolean messageCountFound = false;
        while (elapsedTime < timeout && !messageCountFound) {
            Thread.sleep(500);
            messageCountFound = messageStoreAdminClient.getMessageCount(messageStoreName) == expectedCount;
            elapsedTime += 500;
        }
        return messageCountFound;
    }

    public static void deploySynapseConfiguration(File src, ArtifactType type) throws Exception {
        deploySynapseConfiguration(src, defaultDeploymentDirectory, type);
    }

    public static void deploySynapseConfiguration(File src, String depDirectory, ArtifactType type) throws Exception {
        FileUtils.copyFile(src, new File(getDestination(depDirectory, type.getDirName(), src.getName())));
    }

    private static String getDestination(String depDirectory, String artifactType, String name) {
        return String.join(File.separator, depDirectory, "server", "synapse-configs", "default", artifactType, name);
    }

    public static void deploySynapseConfiguration(OMElement config, String artifactName, ArtifactType type,
                                                  boolean isRestartRequired) throws IOException {
        deploySynapseConfiguration(config, artifactName, type.getDirName(), isRestartRequired);
    }

    public static void deploySynapseConfiguration(OMElement config, String artifactName, String artifactType,
                                                  boolean isRestartRequired) throws IOException {

        String directory = System.getProperty("carbon.home") + File.separator + "repository" + File.separator + "deployment"
                + File.separator + "server" + File.separator + "synapse-configs" + File.separator + "default"
                + File.separator + artifactType;
        String path = directory + File.separator + artifactName + ".xml";

        if (!Files.exists(FileSystems.getDefault().getPath(directory))) {
            try {
                Files.createDirectories(FileSystems.getDefault().getPath(directory));
            } catch (IOException e) {
                throw new IOException("Error while creating the directory, " + directory + ".", e);
            }
        }
        log.info("Deploying artifact named ('" + artifactName + "') of type ('" + artifactType + "')");
        try (OutputStream outputStream = new FileOutputStream(path)) {
            config.serialize(outputStream);
            if (isRestartRequired) {
                CarbonServerExtension.restartServer();
            }
        } catch (IOException exception) {
            log.error("Error when creating file", exception);
        } catch (XMLStreamException e) {
            log.error("Error when serializing synapse config", e);
        }
    }

    public static void undeploySynapseConfiguration(String artifactName, ArtifactType type, boolean restartServer) {
        undeploySynapseConfiguration(artifactName, type.getDirName(), restartServer);
    }

    public static void undeploySynapseConfiguration(String artifactName, String artifactType) {
        undeploySynapseConfiguration(artifactName, artifactType, true);
    }

    public static void undeploySynapseConfiguration(String artifactName, String artifactType, boolean restartServer) {

        if (restartServer) {
            CarbonServerExtension.shutdownServer();
        }
        String pathString = System.getProperty("carbon.home") + File.separator + "repository" + File.separator + "deployment"
                + File.separator + "server" + File.separator + "synapse-configs" + File.separator + "default"
                + File.separator + artifactType + File.separator + artifactName + ".xml";
        Path path = FileSystems.getDefault().getPath(pathString);
        try {
            log.info("Un deploying artifact named ('" + artifactName + "') of type ('" + artifactType + "')");
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Error while deleting the file", e);
        }
        if (restartServer) {
            CarbonServerExtension.startServer();
        }
    }

    /**
     * Un-deploy a carbon application from the server artifacts location and restart if needed.
     *
     * @param artifactName  CAPP name ( must include the extension Ex:- app1.car )
     * @param restartServer Server restart required
     */
    public static void undeployCarbonApplication(String artifactName, boolean restartServer) {
        CarbonServerExtension.shutdownServer();
        String pathString =
                System.getProperty("carbon.home") + File.separator + "repository" + File.separator + "deployment"
                        + File.separator + "server" + File.separator + "carbonapps" + File.separator + artifactName;
        Path path = FileSystems.getDefault().getPath(pathString);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Error while deleting the file", e);
        }
        if (restartServer) {
            CarbonServerExtension.restartServer();
        }
    }

    /**
     * Check if the given queue does not contain any messages
     *
     * @param queueName queue to be checked
     * @return true in queue is empty, false otherwise
     * @throws Exception if error while checking
     */
    public static boolean isQueueEmpty(String queueName) throws Exception {

        String poppedMessage;
        JMSQueueMessageConsumer consumer = new JMSQueueMessageConsumer(
                JMSBrokerConfigurationProvider.getInstance().getBrokerConfiguration());
        try {
            consumer.connect(queueName);
            poppedMessage = consumer.popMessage();
        } finally {
            consumer.disconnect();
        }

        return poppedMessage == null;
    }
}
