/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.esb.integration.common.extensions.carbonserver;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.automation.engine.FrameworkConstants;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.beans.User;
import org.wso2.carbon.automation.engine.exceptions.AutomationFrameworkException;
import org.wso2.carbon.automation.engine.frameworkutils.CodeCoverageUtils;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.automation.engine.frameworkutils.ReportGenerator;
import org.wso2.carbon.automation.engine.frameworkutils.TestFrameworkUtils;
import org.wso2.carbon.automation.extensions.ExtensionConstants;
import org.wso2.carbon.automation.extensions.servers.utils.ArchiveExtractor;
import org.wso2.carbon.automation.extensions.servers.utils.ClientConnectionUtil;
import org.wso2.carbon.automation.extensions.servers.utils.FileManipulator;
import org.wso2.carbon.automation.extensions.servers.utils.ServerLogReader;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.xml.xpath.XPathExpressionException;

/**
 * A set of utility methods such as starting & stopping a Carbon server.
 */
public class CarbonServerManager {
    private static final Log log = LogFactory.getLog(CarbonServerManager.class);
    private Process process;
    private String carbonHome;
    private AutomationContext automationContext;
    private ServerLogReader inputStreamHandler;
    private ServerLogReader errorStreamHandler;
    private boolean isCoverageEnable = false;
    private String coverageDumpFilePath;
    private int portOffset = 0;
    private static final String SERVER_SHUTDOWN_MESSAGE = "Halting JVM";
    private static final String EXECUTABLES = "executables";
    private static final String MANAGEMENT_PORT = "managementPort";
    private static final long DEFAULT_START_STOP_WAIT_MS = 1000 * 60 * 5;
    private static final String CMD_ARG = "cmdArg";
    private static int defaultHttpsPort = Integer.parseInt(FrameworkConstants.SERVER_DEFAULT_HTTPS_PORT);
    private String scriptName;
    private int managementPort;
    private int retryLimit = 3;
    private int retryAttempt = 0;

    public CarbonServerManager(AutomationContext context) {
        this.automationContext = context;
    }

    public synchronized void startServerUsingCarbonHome(String carbonHome, Map<String, String> commandMap)
            throws AutomationFrameworkException {
        if (process != null) { // An instance of the server is running
            log.warn("Tried to start a new server when there is one already running");
            return;
        }
        if (commandMap.containsKey(EXECUTABLES)) {
            updateFilePermissions(commandMap);
        }
        portOffset = getPortOffsetFromCommandMap(commandMap);

        try {
            if (!commandMap.isEmpty() && getPortOffsetFromCommandMap(commandMap) == 0) {
                System.setProperty(ExtensionConstants.CARBON_HOME, carbonHome);
            }

            File commandDir = new File(carbonHome);

            log.info("Starting server ... ");
            if (StringUtils.isNotEmpty(System.getProperty("startupScript"))) {
                scriptName = System.getProperty("startupScript");
            } else {
                scriptName = commandMap.get("startupScript");
            }
            String componentBinPath = commandMap.get("runtimePath");

            if (scriptName == null && componentBinPath == null) {
                scriptName = TestFrameworkUtils.getStartupScriptFileName(carbonHome);
            }
            String[] parameters = expandServerStartupCommandList(commandMap);

            String[] cmdArray;

            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                //For other runtime based bins (Business-process etc)

                if (componentBinPath != null) {
                    commandDir = new File(carbonHome + File.separator + componentBinPath);
                    cmdArray = new String[] { "cmd.exe", "/c",
                            carbonHome + File.separator + componentBinPath + File.separator + scriptName + ".bat" };
                } else {
                    commandDir = new File(carbonHome + File.separator + "bin");
                    cmdArray = new String[] { "cmd.exe", "/c", commandDir + File.separator + scriptName + ".bat" };
                }

                cmdArray = mergePropertiesToCommandArray(parameters, cmdArray);
                process = Runtime.getRuntime().exec(cmdArray, null, commandDir);

            } else {
                if (componentBinPath != null) {
                    commandDir = new File(carbonHome + File.separator + componentBinPath);
                    cmdArray = new String[] { "sh",
                            carbonHome + File.separator + componentBinPath + File.separator + scriptName + ".sh" };
                } else {
                    commandDir = new File(carbonHome + File.separator + "bin");
                    cmdArray = new String[] { "sh", commandDir + File.separator + scriptName + ".sh" };
                }

                cmdArray = mergePropertiesToCommandArray(parameters, cmdArray);
                process = Runtime.getRuntime().exec(cmdArray, null, commandDir);
            }

            errorStreamHandler = new ServerLogReader("errorStream", process.getErrorStream());
            inputStreamHandler = new ServerLogReader("inputStream", process.getInputStream());
            // start the stream readers
            inputStreamHandler.start();
            errorStreamHandler.start();

            //register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    serverShutdown(portOffset , false);
                } catch (Exception e) {
                    log.error("Error while server shutdown ..", e);
                }
            }));

            if (commandMap.containsKey(MANAGEMENT_PORT)) {
                managementPort = Integer.parseInt(commandMap.get(MANAGEMENT_PORT)) + portOffset;
            } else if (StringUtils.isNotEmpty(System.getProperty(MANAGEMENT_PORT))) {
                managementPort = Integer.parseInt(System.getProperty(MANAGEMENT_PORT)) + portOffset;
            } else {
                managementPort = 9154 + portOffset;
            }

            waitTill(() -> !isRemotePortInUse("localhost", managementPort), 180, "startup");

            if (!isRemotePortInUse("localhost", managementPort)) {
                if (retryAttempt < retryLimit) {
                    retryAttempt++;
                    log.info("Restarting server due to startup failure. Retry attempt: " + retryAttempt);
                    serverShutdown(portOffset, true);
                    startServerUsingCarbonHome(carbonHome, commandMap);
                } else {
                    retryAttempt = 0;
                    throw new RuntimeException("Server initialization failed");
                }
            } else {
                retryAttempt = 0;
            }

            log.info("Server started successfully ...");

        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Unable to start server", e);
        }
    }

    private String[] mergePropertiesToCommandArray(String[] parameters, String[] cmdArray) {
        if (parameters != null) {
            cmdArray = mergerArrays(cmdArray, parameters);
        }
        return cmdArray;
    }

    /**
     * Unzip carbon zip file and return the carbon home. Based on the coverage configuration in automation.xml
     * This method will inject jacoco agent to the carbon server startup scripts.
     *
     * @param carbonServerZipFile - Carbon zip file, which should be specified in test module pom
     * @return - carbonHome - carbon home
     * @throws IOException - If pack extraction fails
     */
    public synchronized String setUpCarbonHome(String carbonServerZipFile, String startupScriptName)
            throws IOException, AutomationFrameworkException {
        if (process != null) { // An instance of the server is running
            return carbonHome;
        }
        int indexOfZip = carbonServerZipFile.lastIndexOf(".zip");
        if (indexOfZip == -1) {
            throw new IllegalArgumentException(carbonServerZipFile + " is not a zip file");
        }
        String fileSeparator = (File.separator.equals("\\")) ? "\\" : "/";
        if (fileSeparator.equals("\\")) {
            carbonServerZipFile = carbonServerZipFile.replace("/", "\\");
        }
        String extractedCarbonDir = carbonServerZipFile
                .substring(carbonServerZipFile.lastIndexOf(fileSeparator) + 1, indexOfZip);
        FileManipulator.deleteDir(extractedCarbonDir);
        String extractDir = "carbontmp" + System.currentTimeMillis();
        String baseDir = (System.getProperty("basedir", ".")) + File.separator + "target";
        log.info("Extracting carbon zip file.. ");

        new ArchiveExtractor().extractFile(carbonServerZipFile, baseDir + File.separator + extractDir);
        carbonHome =
                new File(baseDir).getAbsolutePath() + File.separator + extractDir + File.separator + extractedCarbonDir;
        copyResources();
        try {
            //read coverage status from automation.xml
            isCoverageEnable = Boolean.parseBoolean(automationContext.getConfigurationValue("//coverage"));
        } catch (XPathExpressionException e) {
            throw new AutomationFrameworkException("Coverage configuration not found in automation.xml", e);
        }

        //insert Jacoco agent configuration to carbon server startup script. This configuration
        //cannot be directly pass as server startup command due to script limitation.
        if (isCoverageEnable) {
            instrumentForCoverage(startupScriptName);
        }

        return carbonHome;
    }

    /**
     * Copy samples from target the pack.
     */
    private void copyResources() throws IOException {

        String srcDirectory =
                (System.getProperty("basedir", ".")) + File.separator + "target" + File.separator + "samples"
                        + File.separator;
        File srcFile = new File(srcDirectory);

        if ( srcFile.exists() ) {
            log.info("Copying resources from " + srcDirectory);
            FileUtils.copyDirectoryToDirectory( srcFile , new File(carbonHome));
            log.info("Completed copying resources");
        }
    }

    public synchronized void serverShutdown(int portOffset, boolean skipRunningCodeCoverage) throws AutomationFrameworkException {
        if (process != null) {
            log.info("Shutting down server ...");

            try {
                // Waiting for 3 mins max until the server gets shut down
                int retryCount = 36;
                for (int i = 0; i < retryCount; i++) {
                    if (isRemotePortInUse("localhost", managementPort)) {
                        startProcess(carbonHome, getStartScriptCommand("stop"));
                        waitTill(() -> isRemotePortInUse("localhost", managementPort), 5, "shutdown");
                    } else {
                        break;
                    }
                }

                if (isRemotePortInUse("localhost", managementPort)) {
                    throw new AutomationFrameworkException("Failed shutting down the sever");
                }

                log.info("Server stopped successfully ...");

            } catch (IOException | InterruptedException e) {
                throw new AutomationFrameworkException("Failed to stop server ", e);
            }

            inputStreamHandler.stop();
            errorStreamHandler.stop();
            process.destroy();
            process = null;

            if (!skipRunningCodeCoverage && isCoverageEnable) {
                try {
                    log.info("Generating Jacoco code coverage...");
                    generateCoverageReport(new File(
                            carbonHome + File.separator + "wso2" + File.separator + "components" + File.separator
                                    + "plugins" + File.separator));
                } catch (IOException e) {
                    log.error("Failed to generate code coverage ", e);
                    throw new AutomationFrameworkException("Failed to generate code coverage ", e);
                }
            }
            if (portOffset == 0) {
                System.clearProperty(ExtensionConstants.CARBON_HOME);
            }
        }
        else {
            log.warn("Trying to shut down a server that hasn't completed startup. Hence aborting shutdown.");
        }
    }

    private Process startProcess(String workingDirectory, String[] cmdArray) throws IOException {
        File commandDir = new File(workingDirectory);
        ProcessBuilder processBuilder = new ProcessBuilder(cmdArray);
        processBuilder.directory(commandDir);
        return processBuilder.start();
    }

    private void waitTill(BooleanSupplier predicate, int maxWaitTime, String task) throws InterruptedException {
        long time = System.currentTimeMillis() + maxWaitTime * 1000L;
        while (predicate.getAsBoolean() && System.currentTimeMillis() < time) {
            log.info("waiting for server " + task);
            Thread.sleep(1000);
        }
    }

    private boolean isRemotePortInUse(String hostName, int portNumber) {
        try {
            // Socket try to open a REMOTE port
            new Socket(hostName, portNumber).close();
            // remote port can be opened, this is a listening port on remote machine
            // this port is in use on the remote machine !
            return true;
        } catch (IOException e) {
            // remote port is closed, nothing is running on
            return false;
        }
    }

    private String[] getStartScriptCommand(String... commands) {
        String operatingSystem = System.getProperty("os.name").toLowerCase();

        ArrayList<String> commandArray;
        if (operatingSystem.contains("windows")) {
            commandArray = new ArrayList<>(Arrays.asList("cmd.exe", "/c",
                    carbonHome + File.separator + "bin" + File.separator + scriptName + ".bat"));
        } else {
            commandArray = new ArrayList<>(
                    Arrays.asList("sh", carbonHome + File.separator + "bin" + File.separator + scriptName + ".sh"));
        }

        commandArray.addAll(Arrays.asList(commands));
        return commandArray.toArray(new String[0]);
    }

    private void generateCoverageReport(File classesDir) throws IOException, AutomationFrameworkException {

        CodeCoverageUtils
                .executeMerge(FrameworkPathUtil.getJacocoCoverageHome(), FrameworkPathUtil.getCoverageMergeFilePath());
        ReportGenerator reportGenerator = new ReportGenerator(new File(FrameworkPathUtil.getCoverageMergeFilePath()),
                classesDir, new File(CodeCoverageUtils.getJacocoReportDirectory()), null);
        reportGenerator.create();

        log.info("Jacoco coverage dump file path : " + FrameworkPathUtil.getCoverageDumpFilePath());
        log.info("Jacoco class file path : " + classesDir);
        log.info("Jacoco coverage HTML report path : " + CodeCoverageUtils.getJacocoReportDirectory() + File.separator
                + "index.html");
    }

    public synchronized void restartGracefully() throws AutomationFrameworkException {

        try {
            int httpsPort = defaultHttpsPort + portOffset;
            //considering the port offset
            String backendURL = automationContext.getContextUrls().getSecureServiceUrl()
                    .replaceAll("(:\\d+)", ":" + httpsPort);
            User superUser = automationContext.getSuperTenant().getTenantAdmin();
            ClientConnectionUtil
                    .sendGraceFullRestartRequest(backendURL, superUser.getUserName(), superUser.getPassword());
        } catch (XPathExpressionException e) {
            throw new AutomationFrameworkException("restart failed", e);
        }

        long time = System.currentTimeMillis() + DEFAULT_START_STOP_WAIT_MS;
        while (!inputStreamHandler.getOutput().contains(SERVER_SHUTDOWN_MESSAGE) && System.currentTimeMillis() < time) {
            // wait until server shutdown is completed
        }

        time = System.currentTimeMillis();

        while (System.currentTimeMillis() < time + 5000) {
            //wait for port to close
        }

        try {
            ClientConnectionUtil.waitForPort(Integer.parseInt(automationContext.getInstance().getPorts().get("https")),
                    automationContext.getInstance().getHosts().get("default"));

            ClientConnectionUtil.waitForLogin(automationContext);

        } catch (XPathExpressionException e) {
            throw new AutomationFrameworkException("Connection attempt to carbon server failed", e);
        }
    }

    private String[] expandServerStartupCommandList(Map<String, String> commandMap) {
        if (commandMap == null || commandMap.size() == 0) {
            return null;
        }
        String[] cmdParaArray = null;
        String cmdArg = null;
        if (commandMap.containsKey(CMD_ARG)) {
            cmdArg = commandMap.get(CMD_ARG);
            cmdParaArray = cmdArg.trim().split("\\s+");
            commandMap.remove(CMD_ARG);
        }
        String[] parameterArray = new String[commandMap.size()];
        int arrayIndex = 0;
        Set<Map.Entry<String, String>> entries = commandMap.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            String parameter;
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                parameter = key;
            } else {
                parameter = key + "=" + value;
            }
            parameterArray[arrayIndex++] = parameter;
        }
        //setting cmdArg again
        if (cmdArg != null) {
            commandMap.put(CMD_ARG, cmdArg);
        }
        if (cmdParaArray == null || cmdParaArray.length == 0) {
            return parameterArray;
        } else {
            return ArrayUtils.addAll(parameterArray, cmdParaArray);
        }
    }

    private void updateFilePermissions(Map<String, String> commandMap) {

        String[] executableFiles = commandMap.get(EXECUTABLES).trim().split(",");
        Arrays.stream(executableFiles).map(file -> file.replace('/', File.separatorChar)).map(
                file -> carbonHome + File.separator + file.trim()).forEach(
                fileName -> new File(fileName).setExecutable(true));
    }

    private int getPortOffsetFromCommandMap(Map<String, String> commandMap) {
        int portOffset = 0;
        if (commandMap.containsKey(ExtensionConstants.PORT_OFFSET_COMMAND)) {
            portOffset = Integer.parseInt(commandMap.get(ExtensionConstants.PORT_OFFSET_COMMAND));
        }
        System.setProperty("port.offset", Integer.toString(portOffset));
        return portOffset;
    }

    private String[] mergerArrays(String[] array1, String[] array2) {
        return ArrayUtils.addAll(array1, array2);
    }

    /**
     * This methods will insert jacoco agent settings into startup script under JAVA_OPTS
     *
     * @param scriptName - Name of the startup script
     * @throws IOException - throws if shell script edit fails
     */
    private void insertJacocoAgentToShellScript(String scriptName) throws IOException {

        String jacocoAgentFile = CodeCoverageUtils.getJacocoAgentJarLocation();
        coverageDumpFilePath = FrameworkPathUtil.getCoverageDumpFilePath();

        File inFile = Paths.get(carbonHome, "bin", scriptName + ".sh").toFile();
        File tmpFile = Paths.get(carbonHome, "tmp" + scriptName + ".sh").toFile();
        String lineToBeChecked = "-Dwso2.server.standalone=true";
        String lineToBeInserted =
                "-javaagent:" + jacocoAgentFile + "=destfile=" + coverageDumpFilePath + "" + ",append=true,includes="
                        + CodeCoverageUtils.getInclusionJarsPattern(":") + ",excludes=" + CodeCoverageUtils
                        .getExclusionJarsPattern(":") + " \\";

        CodeCoverageUtils.insertStringToFile(inFile, tmpFile, lineToBeChecked, lineToBeInserted);
    }

    /**
     * This methods will insert jacoco agent settings into windows bat script
     *
     * @param scriptName - Name of the startup script
     * @throws IOException - throws if shell script edit fails
     */
    private void insertJacocoAgentToBatScript(String scriptName) throws IOException {

        String jacocoAgentFile = CodeCoverageUtils.getJacocoAgentJarLocation();
        coverageDumpFilePath = FrameworkPathUtil.getCoverageDumpFilePath();
        CodeCoverageUtils.insertJacocoAgentToStartupBat(Paths.get(carbonHome, "bin", scriptName + ".bat").toFile(),
                Paths.get(carbonHome, "tmp", scriptName + ".bat").toFile(), "-Dcatalina.base",
                "-javaagent:" + jacocoAgentFile + "=destfile=" + coverageDumpFilePath + "" + ",append=true,includes="
                        + CodeCoverageUtils.getInclusionJarsPattern(":") + ",excludes=" + CodeCoverageUtils
                        .getExclusionJarsPattern(":"));
    }

    /**
     * This method will check the OS and edit server startup script to inject jacoco agent
     *
     * @throws IOException - If agent insertion fails.
     */
    private void instrumentForCoverage(String startupScriptName) throws IOException {

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            insertJacocoAgentToBatScript(startupScriptName);
            if (log.isDebugEnabled()) {
                log.debug("Included files " + CodeCoverageUtils.getInclusionJarsPattern(":"));
                log.debug("Excluded files " + CodeCoverageUtils.getExclusionJarsPattern(":"));
            }
        } else {
            insertJacocoAgentToShellScript(startupScriptName);
        }

    }
}
