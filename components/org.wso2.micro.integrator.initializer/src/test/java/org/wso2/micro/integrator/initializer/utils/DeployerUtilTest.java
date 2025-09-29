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

import org.apache.axis2.deployment.DeploymentException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.wso2.micro.integrator.initializer.utils.Constants.DOUBLE_UNDERSCORE;

public class DeployerUtilTest {

    private File tempDir;

    public static File createCarFile(File dir, String carFileName) throws IOException {

        File carFile = new File(dir, carFileName);
        if (!carFile.exists()) {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(carFile))) {
                ZipEntry artifactsEntry = new ZipEntry("artifacts.xml");
                zos.putNextEntry(artifactsEntry);
                zos.write(new byte[0]);
                zos.closeEntry();

                ZipEntry metadataEntry = new ZipEntry("metadata.xml");
                zos.putNextEntry(metadataEntry);
                zos.write(new byte[0]);
                zos.closeEntry();
            }
        }
        return carFile;
    }

    public static void writeDescriptorToExistingCarFile(File carFile, String groupId, String artifactId, String version,
                                                        String... dependencies) throws Exception {

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(carFile))) {
            zos.putNextEntry(new ZipEntry("descriptor.xml"));
            StringBuilder descriptor = new StringBuilder();
            descriptor.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project>\n<id>")
                    .append(groupId).append(DOUBLE_UNDERSCORE).append(artifactId).append(DOUBLE_UNDERSCORE).append(version)
                    .append("</id>\n<dependencies>\n");
            for (String dep : dependencies) {
                descriptor.append(dep).append("\n");
            }
            descriptor.append("</dependencies>\n</project>");
            zos.write(descriptor.toString().getBytes());
            zos.closeEntry();
        }
    }

    @Before
    public void setUp() throws IOException {

        // Create a temp directory and dummy files
        tempDir = File.createTempFile("test", "");
        tempDir.delete(); // Delete the file so we can use the same name for directory
        tempDir.mkdir();
    }

    @After
    public void tearDown() {

        for (File file : Objects.requireNonNull(tempDir.listFiles())) {
            file.delete();
        }
        tempDir.delete();
    }

    @Test
    public void testGetCAppDescriptor() throws Exception {
        // Create a simple CAR file in the temp directory with simple names
        File carFile = createCarFile(tempDir, "a.car");
        String depB = "<dependency groupId=\"com.example\" artifactId=\"c\" version=\"1.0.0\" type=\"car\"/>";
        String depC = "<dependency groupId=\"com.example\" artifactId=\"c\" version=\"1.0.0\" type=\"car\"/>";
        writeDescriptorToExistingCarFile(carFile, "com.example", "a", "1.0.0", depB, depC);

        CAppDescriptor cAppDescriptor = new CAppDescriptor(carFile);
        assertEquals("com.example__a__1.0.0", cAppDescriptor.getCAppId());
        assertNotNull(cAppDescriptor.getCAppDependencies());
        assertTrue(cAppDescriptor.getCAppDependencies().contains("com.example__c__1.0.0"));
        assertTrue(cAppDescriptor.getCAppDependencies().contains("com.example__c__1.0.0"));
    }

    @Test
    public void testCreateCAppDependencyGraph() {

        Map<String, List<String>> dependencyGraph = new HashMap<>();
        dependencyGraph.put("com.example_TestProjectConfigs1_1.0.0",
                Collections.singletonList("com.example_TestProjectCompositeExporter1_1.0.0"));
        dependencyGraph.put("com.example_TestProjectConfigs2_1.0.0",
                Collections.singletonList("com.example_TestProjectCompositeExporter1_1.0.0"));
        dependencyGraph.put("com.example_TestProjectCompositeExporter1_1.0.0", Collections.emptyList());

        assertNotNull(dependencyGraph);
        assertTrue(dependencyGraph.containsKey("com.example_TestProjectConfigs1_1.0.0"));
        assertTrue(dependencyGraph.containsKey("com.example_TestProjectConfigs2_1.0.0"));
        assertTrue(dependencyGraph.containsKey("com.example_TestProjectCompositeExporter1_1.0.0"));
        assertTrue(dependencyGraph.get("com.example_TestProjectConfigs1_1.0.0")
                .contains("com.example_TestProjectCompositeExporter1_1.0.0"));
        assertTrue(dependencyGraph.get("com.example_TestProjectConfigs2_1.0.0")
                .contains("com.example_TestProjectCompositeExporter1_1.0.0"));
    }

    @Test
    public void testGetDependencyGraphProcessingOrder() throws Exception {

        // Create temporary CAR files using the helper method
        File carFileA = createCarFile(tempDir, "a.car");
        File carFileB = createCarFile(tempDir, "b.car");
        File carFileC = createCarFile(tempDir, "c.car");

        File[] testCarFiles = {carFileA, carFileB, carFileC};

        List<CAppDescriptor> cAppDescriptors = DeployerUtil.getCAppDescriptors(testCarFiles);

        Map<String, List<String>> dependencyGraph = DeployerUtil.createCAppDependencyGraph(cAppDescriptors);
        List<String> processingOrder = DeployerUtil.getDependencyGraphProcessingOrder(dependencyGraph);
        assertNotNull(processingOrder);
        assertEquals(3, processingOrder.size());
        assertTrue(processingOrder.get(0).equals("a.car"));
        assertTrue(processingOrder.get(1).equals("b.car"));
        assertTrue(processingOrder.get(2).equals("c.car"));
    }

    @Test
    public void testComplexDependencyGraphProcessingOrder() throws DeploymentException {

        Map<String, List<String>> dependencyGraph = new HashMap<>();
        dependencyGraph.put("A", Arrays.asList("B", "C"));
        dependencyGraph.put("B", Collections.singletonList("D"));
        dependencyGraph.put("C", Arrays.asList("D", "E"));
        dependencyGraph.put("D", Collections.emptyList());
        dependencyGraph.put("E", Collections.emptyList());

        List<String> processingOrder = DeployerUtil.getDependencyGraphProcessingOrder(dependencyGraph);
        assertNotNull(processingOrder);
        assertEquals(5, processingOrder.size());
        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), processingOrder);
    }

    @Test
    public void testMultipleSeparatedGraphsProcessingOrder() throws DeploymentException {

        Map<String, List<String>> dependencyGraph = new HashMap<>();
        // First graph
        dependencyGraph.put("A", Arrays.asList("B", "C"));
        dependencyGraph.put("B", Collections.singletonList("D"));
        dependencyGraph.put("C", Collections.emptyList());
        dependencyGraph.put("D", Collections.emptyList());

        // Second graph
        dependencyGraph.put("X", Arrays.asList("Y", "Z"));
        dependencyGraph.put("Y", Collections.emptyList());
        dependencyGraph.put("Z", Collections.emptyList());

        List<String> processingOrder = DeployerUtil.getDependencyGraphProcessingOrder(dependencyGraph);
        assertNotNull(processingOrder);
        assertEquals(7, processingOrder.size());
        assertEquals(Arrays.asList("A", "X", "B", "C", "Y", "Z", "D"), processingOrder);
    }

    @Test
    public void testGetCAppProcessingOrderWithMissingCApp() throws Exception {
        // Create only one CAR file, but declare a dependency on a missing CApp
        File carFileA = createCarFile(tempDir, "a.car");
        File[] testCarFiles = {carFileA};

        // Write descriptor for 'a.car' with a dependency on 'b.car' (which does not exist)
        String depB = "<dependency groupId=\"com.example\" artifactId=\"b\" version=\"1.0.0\" type=\"car\"/>";
        writeDescriptorToExistingCarFile(carFileA, "com.example", "a", "1.0.0", depB);

        try {
            DeployerUtil.getCAppProcessingOrder(testCarFiles);
            fail("Expected DeploymentException due to missing CApp");
        } catch (DeploymentException e) {
            assertTrue(e.getMessage().contains("Some CApps are missing:"));
            assertTrue(e.getMessage().contains("com.example__b__1.0.0"));
        }
    }

    @Test
    public void testMultipleDependenciesPointingToSameRootProject() throws DeploymentException {

        Map<String, List<String>> dependencyGraph = new HashMap<>();
        dependencyGraph.put("A", Arrays.asList("B", "D"));
        dependencyGraph.put("B", Collections.singletonList("C"));
        dependencyGraph.put("E", Arrays.asList("F", "G"));
        dependencyGraph.put("F", Collections.singletonList("C"));
        dependencyGraph.put("C", Collections.emptyList());
        dependencyGraph.put("D", Collections.emptyList());
        dependencyGraph.put("G", Collections.emptyList());

        List<String> processingOrder = DeployerUtil.getDependencyGraphProcessingOrder(dependencyGraph);
        assertNotNull(processingOrder);
        assertEquals(7, processingOrder.size());
        assertEquals(Arrays.asList("A", "E", "B", "D", "F", "G", "C"), processingOrder);
    }

    @Test
    public void testEmptyDependencyGraphProcessingOrder() throws DeploymentException {

        Map<String, List<String>> dependencyGraph = new HashMap<>();
        List<String> processingOrder = DeployerUtil.getDependencyGraphProcessingOrder(dependencyGraph);
        assertNotNull(processingOrder);
        assertTrue(processingOrder.isEmpty());
    }

    @Test
    public void testMultipleProjectsWithSameDependency() throws DeploymentException {

        Map<String, List<String>> dependencyGraph = new HashMap<>();
        dependencyGraph.put("I", Arrays.asList("A", "B", "C", "D", "E"));
        dependencyGraph.put("J", Arrays.asList("I", "K"));
        dependencyGraph.put("A", Collections.emptyList());
        dependencyGraph.put("B", Collections.emptyList());
        dependencyGraph.put("C", Collections.emptyList());
        dependencyGraph.put("D", Collections.emptyList());
        dependencyGraph.put("E", Collections.emptyList());
        dependencyGraph.put("K", Collections.emptyList());

        List<String> processingOrder = DeployerUtil.getDependencyGraphProcessingOrder(dependencyGraph);
        assertNotNull(processingOrder);
        assertEquals(8, processingOrder.size());
        assertEquals(Arrays.asList("J", "I", "K", "A", "B", "C", "D", "E"), processingOrder);
    }

    @Test
    public void testCyclicDependencyGraphProcessingOrder() {

        Map<String, List<String>> dependencyGraph = new HashMap<>();
        dependencyGraph.put("Exporter.car", Collections.singletonList("Config1.car"));
        dependencyGraph.put("Config1.car", Collections.singletonList("Config2.car"));
        dependencyGraph.put("Config2.car", Collections.singletonList("Exporter.car")); // Cyclic dependency
        try {
            DeployerUtil.getDependencyGraphProcessingOrder(dependencyGraph);
        } catch (DeploymentException e) {
            assertEquals(
                    "Cyclic dependency detected among the CApps provided. CApps involved in the cycle: Exporter.car, Config1.car, Config2.car",
                    e.getMessage());
        }
    }

    @Test
    public void testInnerCyclicDependencyGraphProcessingOrder() {

        Map<String, List<String>> dependencyGraph = new HashMap<>();
        dependencyGraph.put("A", Arrays.asList("B", "C"));
        dependencyGraph.put("B", Arrays.asList("C", "D"));
        dependencyGraph.put("C", Collections.singletonList("E"));
        dependencyGraph.put("D", Collections.singletonList("B")); // Cyclic dependency
        dependencyGraph.put("E", Collections.emptyList());

        try {
            DeployerUtil.getDependencyGraphProcessingOrder(dependencyGraph);
        } catch (DeploymentException e) {
            assertEquals("Cyclic dependency detected among the CApps provided. CApps involved in the cycle: B, C, D, E",
                    e.getMessage());
        }
    }
}
