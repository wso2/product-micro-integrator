/**
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 *
 */


package org.wso2.micro.integrator.registry;

import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.impl.llom.OMTextImpl;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.activation.DataHandler;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Properties;

public class TestMicroIntegratorRegistry {

    private static MicroIntegratorRegistry microIntegratorRegistry;
    private static Path governanceRegistry;

    private static Path registryRoot;

    @BeforeClass
    public static void init() throws IOException {
        //create registry folder
        File registryFolder = new File("./registry");
        registryFolder.mkdir();

        microIntegratorRegistry = new MicroIntegratorRegistry();
        registryRoot = Paths.get("src", "test", "resources", "registry").toAbsolutePath();

        governanceRegistry = Paths.get("src", "test", "resources", "registry", "governance").toAbsolutePath();
        Files.createDirectories(governanceRegistry);

        Properties properties = new Properties();
        properties.setProperty(MicroIntegratorRegistryConstants.REG_ROOT, registryRoot.toUri().toURL().toString());
        microIntegratorRegistry.init(properties);
    }

    @Before
    public void deployRegistryResource() {

        String filePath = "gov:/custom/checkJsScript.js";
        String content = "function checkLog(mc) {\n"
                         + "\tvar log = mc.getServiceLog();\n"
                         + "\tlog.info(\"Logging inside Script Mediator\");\n"
                         + "}\n";

        microIntegratorRegistry.newNonEmptyResource(filePath, false, "application/javascript", content, "");

        String emptyMediaTypeFilePath = "conf:/custom/QueueName";
        String emptyMediaTypeFileContent = "ordersQueue";
        microIntegratorRegistry.newNonEmptyResource(emptyMediaTypeFilePath, false, "", emptyMediaTypeFileContent, "");

        String resourceFilePath = "gov:mi-resources/custom/checkJsScript.js";
        String resourceContent = "function checkLog(mc) {\n"
                + "\tvar log = mc.getServiceLog();\n"
                + "\tlog.info(\"Logging inside Script Mediator\");\n"
                + "}\n";

        microIntegratorRegistry.newNonEmptyResource(resourceFilePath, false, "application/javascript", resourceContent, "");
    }

    @Test
    public void testRegistryResourceDeployment() throws IOException {

        File resourceFile = Paths.get(governanceRegistry.toString(), "custom", "checkJsScript.js").toFile();
        Assert.assertTrue("checkJsScript.js file should be created", resourceFile.exists());

        File metadataFile =
                Paths.get(governanceRegistry.toString(), "custom", ".metadata", "checkJsScript.js.meta").toFile();
        Assert.assertTrue(".metadata/checkJsScript.js.meta file should be created", metadataFile.exists());

        Properties metadata = new Properties();
        try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile))) {
            metadata.load(reader);
        }
        String mediaType = metadata.getProperty("mediaType");
        Assert.assertEquals("Media type should be as expected", "application/javascript", mediaType);
    }


    @Test
    public void testIllegalRegistryResourceDeployment() throws IOException {

        String filePath = "gov:/custom/../../checkJsScript.js";
        String content = "function checkLog(mc) {\n"
                + "\tvar log = mc.getServiceLog();\n"
                + "\tlog.info(\"Logging inside Script Mediator\");\n"
                + "}\n";
        try {
            microIntegratorRegistry.newNonEmptyResource(filePath, false, "application/javascript", content, "");
            assert false;
        } catch (Exception e) {
            Assert.assertEquals("The registry key  'gov:/custom/../../checkJsScript.js' is illegal " +
                    "which points to a location outside the registry", e.getMessage());
        }

    }

    @Test
    public void testRegistryResourceRead() throws IOException {

        OMNode omNode = microIntegratorRegistry.lookup("gov:/custom/checkJsScript.js");
        String mediaType = ((DataHandler) ((OMTextImpl) omNode).getDataHandler()).getContentType();
        Assert.assertEquals("Media type should be as expected", "application/javascript", mediaType);
    }

    @Test
    public void testResourceRead() {

        OMNode omNode = microIntegratorRegistry.lookup("resources:custom/checkJsScript.js");
        String mediaType = ((DataHandler) ((OMTextImpl) omNode).getDataHandler()).getContentType();
        Assert.assertEquals("Media type should be as expected", "application/javascript", mediaType);
    }

    @Test
    public void testRegistryPropertiesDeploymentAndLookup() throws IOException {

        String filePath = "gov:/custom/payload/template.json";
        String content = "{\"Hello\":\"World\"}";
        Properties properties = new Properties();
        properties.setProperty("token", "12345");
        properties.setProperty("owner", "John");
        microIntegratorRegistry.addNewNonEmptyResource(filePath, false, "application/json", content, properties);

        File resourceFile = Paths.get(governanceRegistry.toString(), "custom", "payload", "template.json").toFile();
        File propertiesFile = Paths.get(governanceRegistry.toString(), "custom", "payload", "template.json.properties").toFile();
        Assert.assertTrue("template.json file should be created", resourceFile.exists());
        Assert.assertTrue("template.json.properties file should be created", propertiesFile.exists());

        Properties readProperties = microIntegratorRegistry.getResourceProperties(filePath);
        Assert.assertEquals("Properties should be as expected", "12345", readProperties.getProperty("token"));
        Assert.assertEquals("Properties should be as expected", "John", readProperties.getProperty("owner"));

    }

    @Test
    public void testRegistryCollectionPropertiesDeploymentAndLookup() throws IOException {

        String filePath = "gov:/custom/folder";
        Properties properties = new Properties();
        properties.setProperty("token", "12345");
        properties.setProperty("owner", "John");
        microIntegratorRegistry.addNewNonEmptyResource(filePath, true, "application/json", "", properties);

        File propertiesFile = Paths.get(governanceRegistry.toString(), "custom", "folder.collections.properties").toFile();
        Assert.assertTrue("folder.collection.properties file should be created", propertiesFile.exists());

        Properties readProperties = microIntegratorRegistry.getResourceProperties(filePath);
        Assert.assertEquals("Properties should be as expected", "12345", readProperties.getProperty("token"));
        Assert.assertEquals("Properties should be as expected", "John", readProperties.getProperty("owner"));

        microIntegratorRegistry.delete(filePath);
    }

    @Test
    public void testRegistryResourceReadWithEmptyMediaType() {
        OMNode omNode = microIntegratorRegistry.lookup("conf:/custom/QueueName");
        Assert.assertEquals("File content should be as expected","ordersQueue", ((OMTextImpl) omNode).getText().trim());
    }

    @AfterClass
    public static void cleanup() throws IOException {
        Files.walk(Paths.get(governanceRegistry.getParent().toString()))
                .map(Path::toFile)
                .sorted(Comparator.reverseOrder())
                .forEach(File::delete);
    }
}
