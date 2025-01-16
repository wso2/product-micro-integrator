/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.micro.integrator.server.extensions;

import org.wso2.micro.integrator.server.LauncherConstants;
import org.wso2.micro.integrator.server.MicroIntegratorLaunchExtension;
import org.wso2.micro.integrator.server.util.BundleInfoLine;
import org.wso2.micro.integrator.server.util.FileUtils;
import org.wso2.micro.integrator.server.util.Utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deploy bundles found inside the dropins directory.
 * 1) Loop through all the bundles in the dropins directory
 * 2) Read their manifest.mf file and extract symbolic names and the bundle-versions.
 * 3) Add the entry to the bundles.info file.
 */
public class DropinsBundleDeployer implements MicroIntegratorLaunchExtension {
    private static final Logger logger = Logger.getLogger(DropinsBundleDeployer.class.getName());

    private static String dropinsDirPath;
    private static String bundlesInfoDirPath;
    private static final String DEFAULT_BUNDLE_VERSION = "0.0.0";

    static {
        dropinsDirPath = System.getProperty(LauncherConstants.CARBON_DROPINS_DIR_PATH);
        String componentPath = System.getProperty(LauncherConstants.CARBON_COMPONENTS_DIR_PATH);
        String profileName = System.getProperty(LauncherConstants.PROFILE, LauncherConstants.DEFAULT_CARBON_PROFILE);

        if (componentPath == null) {
            bundlesInfoDirPath = Paths.get("repository", "components", profileName, "configuration",
                                           "org.eclipse.equinox.simpleconfigurator").toString();
        } else {
            //setting relative path
            componentPath = Paths.get(System.getProperty(LauncherConstants.CARBON_HOME))
                    .relativize(Paths.get(componentPath)).toString();
            bundlesInfoDirPath = Paths
                    .get(componentPath, profileName, "configuration", "org.eclipse.equinox.simpleconfigurator")
                    .toString();
        }

        if (dropinsDirPath == null) {
            if (componentPath == null) {
                dropinsDirPath = Paths.get("repository", "components", "dropins").toString();
            } else {
                dropinsDirPath = Paths.get(componentPath, "dropins").toString();
            }
        } else {
            //setting relative path, need to remove / sign in dropins/
            dropinsDirPath = Paths.get(System.getProperty(LauncherConstants.CARBON_HOME))
                    .relativize(Paths.get(dropinsDirPath)).toString();
        }

    }

    /**
     * 1) Extract bundle info from the dropins directory
     * 2) Process the bundles.info file and populate a data structure, during this process we remove the stale
     * references as well.
     * 3) Add new bundles information to the data structure.
     * 4) Update the bundles.info file.
     */
    public void perform() {
        try {

            //1. Extract the bundle information from the dropins directory.
            File dropinsDir = Utils.getBundleDirectory(dropinsDirPath);
            File[] files = dropinsDir.listFiles(new Utils.JarFileFilter());
            if (files == null) {
                return;
            }

            BundleInfoLine[] newBundleInfoLines = getNewBundleInfoLines(files);

            //2. Read the bundles.info file and get the existing bundle info lines..
            File bundlesInfoDir = Utils.getBundleDirectory(bundlesInfoDirPath);
            File bundlesInfoFile = new File(bundlesInfoDir, "bundles.info");
            if (!bundlesInfoFile.exists()) {
                return;
            }

            Map<String, List<BundleInfoLine>> bundleInfoLineMap = processBundlesInfoFile(bundlesInfoFile,
                                                                                         newBundleInfoLines);

            //3. Adding new references...
            addNewBundleInfoLines(newBundleInfoLines, bundleInfoLineMap);

            //4. Update the bundles.info file
            updateBundlesInfoFile(bundlesInfoFile, bundleInfoLineMap);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error occured while deploying bundles in the dropins directory", e);
        }
    }

    /**
     * This method scan through the dropins directory and construct corresponding BundleInfoLine objects
     *
     * @param bundleFileList list of bundles available in the dropins directory.
     * @return An array of BundleInfoLine objects
     * @throws Exception in the event of an error
     */
    private BundleInfoLine[] getNewBundleInfoLines(File[] bundleFileList) throws Exception {
        ArrayList<BundleInfoLine> bundleInfoArray = new ArrayList<BundleInfoLine>();

        for (File file : bundleFileList) {
            try (JarFile jarFile = new JarFile(file.getAbsoluteFile())) {
                if (jarFile.getManifest() == null || jarFile.getManifest().getMainAttributes() == null) {
                    logger.log(Level.SEVERE, "Invalid Bundle found in the dropins directory: " + file.getName());
                    continue;
                }

                String bundleSymbolicName = jarFile.getManifest().getMainAttributes().
                        getValue(LauncherConstants.BUNDLE_SYMBOLIC_NAME);
                String bundleVersion = jarFile.getManifest().getMainAttributes().
                        getValue(LauncherConstants.BUNDLE_VERSION);

                if (bundleSymbolicName == null || bundleVersion == null) {
                    logger.log(Level.SEVERE,
                               "Required Bundle manifest headers do not exists: " + file.getAbsoluteFile());
                    continue;
                } else {
                    //BSN can have values like, Bundle-SymbolicName: com.example.acme;singleton:=true
                    // refer - http://wiki.osgi.org/wiki/Bundle-SymbolicName for more details
                    if (bundleSymbolicName.contains(";")) {
                        bundleSymbolicName = bundleSymbolicName.split(";")[0];
                    }
                }
                //According to the OSGi spec, Bundle-Version is an optional header; the default value is 0.0.0
                if (bundleVersion == null) {
                    bundleVersion = DEFAULT_BUNDLE_VERSION;
                }

                //Checking whether this bundle is a fragment or not.
                boolean isFragment = jarFile.getManifest().getMainAttributes().
                        getValue(LauncherConstants.FRAGMENT_HOST) != null;

                String dropinsAbsolutePath = System.getProperty(LauncherConstants.CARBON_DROPINS_DIR_PATH);
                if (dropinsAbsolutePath != null) {
                    String compoenentProfilePath = Paths.get(Utils.getCarbonComponentRepo().getPath(),
                                                             System.getProperty(LauncherConstants.PROFILE)).toString();
                    String relativePathToDropinsFolder = Paths.get(compoenentProfilePath)
                            .relativize(Paths.get(dropinsAbsolutePath)).toString();
                    bundleInfoArray.add(new BundleInfoLine(bundleSymbolicName, bundleVersion,
                                                           Paths.get(relativePathToDropinsFolder, file.getName())
                                                                   .toString(), 4, isFragment));
                } else {
                    bundleInfoArray.add(new BundleInfoLine(bundleSymbolicName, bundleVersion,
                                                           Paths.get("..", "dropins", file.getName()).toString(), 4,
                                                           isFragment));
                }
            }
        }
        return bundleInfoArray.toArray(new BundleInfoLine[bundleInfoArray.size()]);
    }

    /**
     * Reads the bundles.info file and populates a data structure to hold the information.
     * During this process, we remove the stale references in the file.
     * i.e. references to the bundles which were there in the dropins directory.
     *
     * @param bundlesInfoFile    Original bundles.info file available in the system.
     * @param newBundleInfoLines List of BundleInfoLine objects which are found in the dropins directory.
     * @return the filtered BundleInfoLine Map.
     * @throws Exception in the event of an error
     */
    private Map<String, List<BundleInfoLine>> processBundlesInfoFile(File bundlesInfoFile,
                                                                     BundleInfoLine[] newBundleInfoLines)
            throws Exception {

        String line;
        Map<String, List<BundleInfoLine>> bundleInfoLineMap = new HashMap<String, List<BundleInfoLine>>();

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(bundlesInfoFile.getAbsoluteFile()));

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                BundleInfoLine bundleInfoLine = BundleInfoLine.getInstance(line);

                if (bundleInfoLine.isFromDropins()) {
                    //This bundle is from the dropins directory. We need to check whether this bundle is still there in
                    //the dropins directory. If it does not exits, we remove it.
                    //This is how we check whether two bundle lines are identical in this scenario.
                    //BundleSymbolicNames and BundleVersions should be equal,
                    //Fragment-ness should be equal.

                    boolean found = false;

                    for (BundleInfoLine newBundleInfoLine : newBundleInfoLines) {
                        if (newBundleInfoLine.getBundleSymbolicName().equals(bundleInfoLine.getBundleSymbolicName())
                                && newBundleInfoLine.getBundleVersion().equals(bundleInfoLine.getBundleVersion())) {
                            //Now the symbolicName and the version is equal. Now we need to check the fragment-ness of
                            // these bundles.
                            if (newBundleInfoLine.isFragment() == bundleInfoLine.isFragment()) {
                                //This means fragment-ness property is equal.
                                found = true;
                            }
                        }
                    }

                    if (!found) {
                        //This dropins bundle is no longer available in the dropins directory. Hence we remove it.
                        continue;
                    }
                }

                List<BundleInfoLine> bundleInfoLineList = bundleInfoLineMap.get(bundleInfoLine.getBundleSymbolicName());
                if (bundleInfoLineList == null) {
                    bundleInfoLineList = new ArrayList<BundleInfoLine>();
                    bundleInfoLineList.add(bundleInfoLine);
                    bundleInfoLineMap.put(bundleInfoLine.getBundleSymbolicName(), bundleInfoLineList);
                } else {
                    bundleInfoLineList.add(bundleInfoLine);
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Unable to close the InputStream " + e.getMessage(), e);
                }
            }
        }
        return bundleInfoLineMap;
    }

    /**
     * Add new bundles information to the data structure.
     *
     * @param newBundleInfoLines        List of BundleInfoLine objects which are found in the dropins directory.
     * @param existingBundleInfoLineMap List of bundles available in the system.
     */
    private void addNewBundleInfoLines(BundleInfoLine[] newBundleInfoLines,
                                       Map<String, List<BundleInfoLine>> existingBundleInfoLineMap) {

        for (BundleInfoLine newBundleInfoLine : newBundleInfoLines) {
            String symbolicName = newBundleInfoLine.getBundleSymbolicName();
            String version = newBundleInfoLine.getBundleVersion();
            boolean isFragment = newBundleInfoLine.isFragment();

            List<BundleInfoLine> bundleInfoLineList = existingBundleInfoLineMap.get(symbolicName);

            if (bundleInfoLineList == null) {
                //Bundle does not exists in the bundles.info line, hence we add it.
                bundleInfoLineList = new ArrayList<BundleInfoLine>();
                bundleInfoLineList.add(newBundleInfoLine);
                existingBundleInfoLineMap.put(symbolicName, bundleInfoLineList);
                logger.log(Level.FINE, "Deploying bundle: " + newBundleInfoLine.getBundlePath());

            } else {
                //Bundle symbolic names exists. Now we need to check whether their versions are equal.
                boolean found = false;
                for (BundleInfoLine existingBundleInfoLIne : bundleInfoLineList) {

                    if (existingBundleInfoLIne.getBundleVersion().equals(version)) {
                        //SymbolicName and the version match with an existing bundle.
                        //Now we need to compare the fragment-ness.
                        if (existingBundleInfoLIne.isFragment() ^ isFragment) {
                            //This means fragment-ness property is not equal.
                            if (!existingBundleInfoLIne.getBundlePath().equals(newBundleInfoLine.getBundlePath())) {
                                logger.log(Level.WARNING,
                                           "Ignoring the deployment of bundle: " + newBundleInfoLine.getBundlePath()
                                                   + ", because it is already available in the system: "
                                                   + existingBundleInfoLIne.getBundlePath()
                                                   + ". Bundle-SymbolicName and Bundle-Version headers are identical ");
                                found = true;
                                break;
                            }
                        } else {
                            //This means fragment-ness property is equal. Seems like we have a match.
                            //Now lets check whether their locations are equal. If the locations are equal, we don't
                            //need to add it again. But if the locations are different we should throw a WARN.
                            if (existingBundleInfoLIne.getBundlePath().equals(newBundleInfoLine.getBundlePath())) {
                                //We have a exact match. So we don't need to add it again.
                                logger.log(Level.FINE, "Deploying bundle: " + newBundleInfoLine.getBundlePath());
                                found = true;
                                break;

                            } else {
                                //We have an exact match, but their locations are different.
                                logger.log(Level.WARNING,
                                           "Ignoring the deployment of bundle: " + newBundleInfoLine.getBundlePath()
                                                   + ", because it is already available in the system: "
                                                   + existingBundleInfoLIne.getBundlePath()
                                                   + ". Bundle-SymbolicName and Bundle-Version headers are identical ");
                                found = true;
                                break;

                            }
                        }
                    } else {
                        //version property is different. Therefore this new bundle does not exist in the system.
                        //Therefore 'found' is still false;
                    }
                }

                if (!found) {
                    //Dropins bundle is not available in the system. Lets add it.
                    bundleInfoLineList.add(newBundleInfoLine);
                    logger.log(Level.FINE, "Deploying bundle: " + newBundleInfoLine.getBundlePath());
                }
            }
        }
    }

    /**
     * 1. Generates the new bundles.info file int a temp location.
     * 2. Replaces it with the original one.
     *
     * @param bundlesInfoFile   Original bundles.info file.
     * @param bundleInfoLineMap Data Structure which contains information about all the bundles.
     * @throws Exception in the event of an error.
     */
    private void updateBundlesInfoFile(File bundlesInfoFile, Map<String, List<BundleInfoLine>> bundleInfoLineMap)
            throws Exception {

        BufferedWriter writer = null;

        try {
            //1. Generates the new bundles.info file int a temp location.
            String tempDir = System.getProperty("java.io.tmpdir");
            if (tempDir == null || tempDir.length() == 0) {
                throw new Exception("java.io.tmpdir property is null. Cannot proceed.");
            }

            String tempBundlesInfoDirPath = tempDir + File.separator + "bundles_info_" + UUID.randomUUID().toString();
            String tempBundlesInfoFilePath = tempBundlesInfoDirPath + File.separator + "bundles.info";

            File tempBundlesInfoDir = new File(tempBundlesInfoDirPath);

            boolean created = tempBundlesInfoDir.mkdir();
            if (!created) {
                throw new IOException("Failed to create the directory: " + tempBundlesInfoFilePath);
            }

            writer = new BufferedWriter(new FileWriter(tempBundlesInfoFilePath, true));

            String[] keyArray = bundleInfoLineMap.keySet().toArray(new String[bundleInfoLineMap.keySet().size()]);
            java.util.Arrays.sort(keyArray);

            for (String key : keyArray) {
                List<BundleInfoLine> bundleInfoLineList = bundleInfoLineMap.get(key);
                for (BundleInfoLine bundleInfoLine : bundleInfoLineList) {
                    writer.write(bundleInfoLine.toString());
                    writer.newLine();
                }
            }
            writer.flush();

            //2. Replaces it with the original one.
            FileUtils.copyFile(new File(tempBundlesInfoFilePath), bundlesInfoFile, false);

        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new Exception("Error occurred while updating the bundles.info file.", e);

        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Unable to close the OutputStream " + e.getMessage(), e);
            }
        }
    }
}
