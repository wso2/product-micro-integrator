/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.initializer.utils;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.util.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.api.API;
import org.apache.synapse.api.version.VersionStrategy;
import org.apache.synapse.config.xml.rest.VersionStrategyFactory;
import org.wso2.micro.application.deployer.AppDeployerUtils;
import org.wso2.micro.integrator.initializer.deployment.DuplicateCAppDescriptorException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.namespace.QName;

import static org.wso2.micro.integrator.initializer.utils.Constants.CAR_FILE_EXTENSION;
import static org.wso2.micro.integrator.initializer.utils.Constants.DESCRIPTOR_XML_FILE_NAME;

public class DeployerUtil {

    private static final Log log = LogFactory.getLog(DeployerUtil.class);

    /**
     * Partially build a synapse API for deployment purposes.
     * @param apiElement OMElement of API configuration.
     * @return API
     */
    public static API partiallyBuildAPI(OMElement apiElement) {
        OMAttribute nameAtt = apiElement.getAttribute(new QName("name"));
        OMAttribute contextAtt = apiElement.getAttribute(new QName("context"));
        API api = new API(nameAtt.getAttributeValue(), contextAtt.getAttributeValue());
        VersionStrategy vStrategy = VersionStrategyFactory.createVersioningStrategy(api, apiElement);
        api.setVersionStrategy(vStrategy);
        return api;
    }

    /**
     * Reads the content of descriptor.xml from a CApp (Carbon Application) file.
     *
     * @param cAppFilePath Path to the .car (CApp) file
     * @return Content of descriptor.xml as a String, or null if not found or error occurs
     */
    public static String readDescriptorXmlFromCApp(String cAppFilePath) throws IOException {
        File cappFile = new File(cAppFilePath);

        if (!cappFile.exists()) {
            throw new FileNotFoundException("CApp file not found: " + cAppFilePath);
        }

        try (ZipFile zip = new ZipFile(cappFile)) {
            ZipEntry entry = zip.getEntry(DESCRIPTOR_XML_FILE_NAME);
            if (entry != null) {
                try (InputStream stream = zip.getInputStream(entry)) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    return content.toString();
                }
            }
        }
        return null;
    }

    /**
     * Determines the processing order of Carbon Application (CApp) files based on their dependencies.
     *
     * <p>This method sorts the given CApp files alphabetically by name, analyzes their dependencies,
     * create a dependency graph, and returns an array of files in the correct processing order.</p>
     *
     * @param cAppFiles An array of `File` objects representing the CApp files to be processed.
     * @return An array of `File` objects in the order they should be processed.
     * @throws IllegalArgumentException If a identifier in the processing order cannot be resolved to a corresponding CApp file.
     */
    public static File[] getCAppProcessingOrder(File[] cAppFiles) throws DeploymentException {
        Arrays.sort(cAppFiles, Comparator.comparing(File::getName));
        List<CAppDescriptor> cAppDescriptors = getCAppDescriptors(cAppFiles);
        Map<String, List<String>> cAppDependencyGraph = createCAppDependencyGraph(cAppDescriptors);
        List<String> graphProcessingOrder = getDependencyGraphProcessingOrder(cAppDependencyGraph);
        if (log.isDebugEnabled()) {
            log.debug("CApp processing order: " + String.join(", ", graphProcessingOrder));
        }

        File[] orderedFiles = new File[cAppFiles.length];
        int index = 0;
        List<String> missingCAppInfos = new ArrayList<>();

        for (String fileIdentifier : graphProcessingOrder) {
            boolean fileFound = false;
            if (fileIdentifier.endsWith(CAR_FILE_EXTENSION)) {
                for (File file : cAppFiles) {
                    if (file.getName().equals(fileIdentifier)) {
                        orderedFiles[index++] = file;
                        fileFound = true;
                        break;
                    }
                }
            } else {
                for (CAppDescriptor cAppDescriptor : cAppDescriptors) {
                    if (cAppDescriptor.getCAppId().equals(fileIdentifier)) {
                        orderedFiles[index++] = cAppDescriptor.getCAppFile();
                        fileFound = true;
                        break;
                    }
                }
            }
            if (!fileFound) {
                List<String> dependents = cAppDependencyGraph.getOrDefault(fileIdentifier, Collections.emptyList());
                missingCAppInfos.add(fileIdentifier + " (required by: " + String.join(", ", dependents) + ")");
            }
        }
        if (!missingCAppInfos.isEmpty()) {
            throw new DeploymentException("Some CApps are missing: " + missingCAppInfos);
        }
        return orderedFiles;
    }

    /**
     * Counts the number of CApp archive files that contain a descriptor.xml file.
     *
     * @param cAppFiles An array of `File` objects representing the CApp files to check.
     * @return The count of CApp archives containing a descriptor.xml file.
     */
    public static int getCAppsWithDescriptorCount(File[] cAppFiles) {
        if (cAppFiles == null) {
            return 0;
        }

        AtomicInteger count = new AtomicInteger();

        for (File carFile : cAppFiles) {
            if (!Files.isRegularFile(carFile.toPath())) {
                continue;
            }
            try (ZipFile zip = new ZipFile(carFile)) {
                // Check top-level descriptor
                if (zip.getEntry(DESCRIPTOR_XML_FILE_NAME) != null) {
                    count.incrementAndGet();
                }

                // Check nested CARs under dependencies/
                zip.stream()
                        .filter(e -> !e.isDirectory())
                        .filter(e -> e.getName().startsWith(AppDeployerUtils.DEPENDENCIES_DIR))
                        .filter(e -> e.getName().toLowerCase(Locale.ROOT).endsWith(".car"))
                        .forEach(entry -> {
                            File tmp = null;
                            try (InputStream in = zip.getInputStream(entry)) {
                                tmp = File.createTempFile("dependent-capp-", ".car");
                                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);

                                try (ZipFile nestedZip = new ZipFile(tmp)) {
                                    if (nestedZip.getEntry(DESCRIPTOR_XML_FILE_NAME) != null) {
                                        count.incrementAndGet();
                                    }
                                }
                            } catch (IOException exception) {
                                throw new UncheckedDeploymentException(
                                        "Failed to read dependent Carbon Application '" + entry.getName() + "' in " + carFile, exception);
                            } finally {
                                if (tmp != null && tmp.exists()) {
                                    tmp.delete();
                                }
                            }
                        });

            } catch (UncheckedDeploymentException ude) {
                log.warn("Failed to read a dependent Carbon Application in " + carFile, ude);
            } catch (IOException e) {
                log.warn("Error scanning Fat Carbon Application: " + carFile, e);
            }
        }
        return count.get();
    }

    /**
     * Creates a list of `CAppDescriptor` objects from an array of Carbon Application (CApp) files.
     *
     * @param cAppFiles An array of `File` objects representing the CApp files.
     * @return A list of `CAppDescriptor` objects corresponding to the provided CApp files.
     */
    public static List<CAppDescriptor> getCAppDescriptors(File[] cAppFiles) throws DeploymentException {
        List<CAppDescriptor> cAppDescriptors = new ArrayList<>();
        for (File cAppFile : cAppFiles) {
            if (cAppFile.getPath().contains(AppDeployerUtils.DEPENDENCIES_DIR)) {
                continue;
            }
            CAppDescriptor descriptor = new CAppDescriptor(cAppFile);
            validateDuplicateCAppDescriptor(cAppDescriptors, descriptor);
            cAppDescriptors.add(descriptor);

            if (!descriptor.isFatCAR()) {
                continue;
            }

            // Scan for nested CARs under dependencies/
            try (ZipFile zipFile = new ZipFile(cAppFile)) {
                zipFile.stream()
                        .filter(entry -> !entry.isDirectory())
                        .filter(entry -> entry.getName().startsWith(AppDeployerUtils.DEPENDENCIES_DIR))
                        .filter(entry -> entry.getName().toLowerCase(Locale.ROOT).endsWith(".car"))
                        .forEach(entry -> {
                            File tmp = null;
                            try (InputStream in = zipFile.getInputStream(entry)) {
                                // Write to a temp .car on disk for CAppDescriptor to read
                                tmp = Files.createTempFile("dependent-capp-", ".car").toFile();
                                try (OutputStream out = new FileOutputStream(tmp)) {
                                    IOUtils.copy(in, out, false);
                                }
                                CAppDescriptor dep = new CAppDescriptor(tmp);
                                dep.setCAppFile(new File(cAppFile.getPath(), entry.getName()));
                                validateDuplicateCAppDescriptor(cAppDescriptors, dep);
                                cAppDescriptors.add(dep);
                            } catch (IOException exception) {
                                throw new UncheckedDeploymentException(
                                        "Failed to read dependent Carbon Application '" + entry.getName() + "' in " + cAppFile, exception);
                            } finally {
                                if (tmp != null && tmp.exists()) {
                                    tmp.delete();
                                }
                            }
                        });
            } catch (UncheckedDeploymentException ude) {
                throw new DeploymentException(ude.getMessage(), ude.getCause());
            } catch (IOException e) {
                throw new DeploymentException("Error scanning Fat Carbon Application: " + cAppFile, e);
            }
        }
        return cAppDescriptors;
    }

    /**
     * This method checks for duplicate CApp descriptors in the provided list.
     * If a duplicate is found, it throws a DeploymentException.
     *
     * @param cAppDescriptors CApp descriptor list
     * @param descriptor      CApp descriptor to be validated
     * @throws DeploymentException if a duplicate CApp descriptor is found
     */
    private static void validateDuplicateCAppDescriptor(List<CAppDescriptor> cAppDescriptors, CAppDescriptor descriptor)
            throws DuplicateCAppDescriptorException {

        for (CAppDescriptor existingDescriptor : cAppDescriptors) {
            if (existingDescriptor.getCAppId().equals(descriptor.getCAppId())) {
                throw new DuplicateCAppDescriptorException("Duplicate Carbon Application: " + descriptor.getCAppId() +
                        " found in files: " + existingDescriptor.getCAppFile().getAbsolutePath() + " and " +
                        descriptor.getCAppFile().getAbsolutePath());
            }
        }
    }

    /** Local unchecked wrapper to use inside streams. */
    private static final class UncheckedDeploymentException extends RuntimeException {
        UncheckedDeploymentException(String msg, Throwable cause) { super(msg, cause); }
    }

    /**
     * Creates a dependency graph for Carbon Applications (CApps) based on their dependencies.
     *
     * This method takes a list of `CAppDescriptor` objects, each representing a Carbon Application,
     * and constructs a directed graph where each node represents a CApp and edges represent
     * dependencies between them. The graph is represented as a map where the keys are CApp IDs
     * and the values are lists of dependent CApp IDs.
     *
     * @param cAppDescriptors A list of `CAppDescriptor` objects representing the Carbon Applications.
     * @return A map representing the dependency graph. The keys are CApp IDs, and the values are
     *         lists of IDs of CApps that depend on the key CApp.
     */
    public static Map<String, List<String>> createCAppDependencyGraph(List<CAppDescriptor> cAppDescriptors) {
        Map<String, List<String>> dependencyGraph = new LinkedHashMap<>();
        for (CAppDescriptor cAppDescriptor : cAppDescriptors) {
            for (String dependency : cAppDescriptor.getCAppDependencies()) {
                if (dependencyGraph.containsKey(dependency)) {
                    dependencyGraph.get(dependency).add(cAppDescriptor.getCAppId());
                } else {
                    List<String> dependentFiles = new ArrayList<>();
                    dependentFiles.add(cAppDescriptor.getCAppId());
                    dependencyGraph.put(dependency, dependentFiles);
                }
            }
            dependencyGraph.putIfAbsent(cAppDescriptor.getCAppId(), new ArrayList<>());
        }
        return dependencyGraph;
    }

    /**
     * Determines the processing order of nodes in a dependency graph using topological sorting.
     *
     * This method takes a directed acyclic graph (DAG) represented as an adjacency list and computes
     * the order in which the nodes should be processed, ensuring that each node is processed only
     * after all its dependencies have been processed.
     *
     * @param graph A map where the keys represent nodes and the values are lists of nodes that
     *              depend on the corresponding key node.
     * @return A list of nodes in the order they should be processed.
     * @throws DeploymentException If the graph contains cycles, making topological sorting impossible.
     */
    public static List<String> getDependencyGraphProcessingOrder(Map<String, List<String>> graph) throws DeploymentException {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String node : graph.keySet()) {
            inDegree.put(node, 0);
        }
        for (List<String> dependents : graph.values()) {
            for (String dependent : dependents) {
                inDegree.put(dependent, inDegree.getOrDefault(dependent, 0) + 1);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sortedOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedOrder.add(current);

            for (String neighbor : graph.getOrDefault(current, Collections.emptyList())) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sortedOrder.size() != inDegree.size()) {
            // Nodes with in-degree > 0 are part of the cycle
            List<String> cycleNodes = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    cycleNodes.add(entry.getKey());
                }
            }
            throw new DeploymentException(
                    "Cyclic dependency detected among the CApps provided. " +
                            "CApps involved in the cycle: " + String.join(", ", cycleNodes)
            );
        }
        return sortedOrder;
    }
}
