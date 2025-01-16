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

package org.wso2.micro.integrator.server.util;

import org.wso2.micro.integrator.server.LauncherConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PatchUtils {

    private static final Logger patchLog = Logger.getLogger(PatchUtils.class.getName());

    private static File bundleBackupDir;
    private static Set<String> servicepackPatchedList;
    private static List<String> previousPatchDirNames;

    /**
     * Here is the patch applying algorithm.
     * 1) Creates a patch0000 (if it does not exist) inside the patches directory. Backup all the bundles in the plugins
     * directory
     * 2) Before applying patches on components/patches directory apply servicepacks on components/servicepacks directory
     * 3) Then copy remaining patches which not contain on servicepacks patchxxxx to the plugins folder.
     *
     * @param servicepackDir parent servicepack directory "repository/components/servicepacks"
     * @param patchesDir     parent patch directory "repository/components/patches"
     * @param pluginsDir     plugin directory "repository/components/plugins"
     * @throws IOException
     */
    public static void applyServicepacksAndPatches(File servicepackDir, File patchesDir, File pluginsDir)
            throws IOException {
        bundleBackupDir = new File(patchesDir, LauncherConstants.BUNDLE_BACKUP_DIR);
        boolean alreadyBackedUp = bundleBackupDir.exists();
        if (!alreadyBackedUp) {
            //We need to backup the plugins in the components/repository/plugins folder.
            FileUtils.copyDirectory(pluginsDir, bundleBackupDir);

            patchLog.log(Level.INFO, "Backed up plugins to " + LauncherConstants.BUNDLE_BACKUP_DIR);
        }
        //Now lets apply latest servicepack and patches.
        patchLog.log(Level.FINE, "Applying patches ...");
        copyServicepacksAndPatches(servicepackDir, patchesDir, pluginsDir, alreadyBackedUp);
    }

    /**
     * copying jars inside patches-xxx directories to components/plugins
     *
     * @param source folder which contains the patches.
     * @param target target
     * @throws IOException
     */
    private static void copyServicepacksAndPatches(File servicepackDir, File source, File target,
                                                   boolean alreadyBackedUp) throws IOException {
        // Sorting patch folders.
        File[] files = source.listFiles(PatchUtils.getPatchFileNameFilter());
        Arrays.sort(files);
        File patchDirLogFile = new File(PatchUtils.getMetaDirectory(), LauncherConstants.PRE_PATCHED_DIR_FILE);
        BufferedWriter bufWriter = new BufferedWriter(new FileWriter(patchDirLogFile));

        // we don't need to restore backup if we just created it.
        if (alreadyBackedUp) {
            // copy all the files in patch0000 directory to plugins,
            // bundleFileName verification is not required for patch0000
            patchLog.log(Level.FINE, "restoring bundle backup directory");
            FileUtils.copyDirectory(bundleBackupDir, target);

        }
        // After applying the backuped plugins which is on patch0000 we are
        // start applying servicepacs before applying other patches
        copyLatestServicepack(servicepackDir, target, bufWriter);

        try {
            for (File file : files) {
                if (file.isDirectory()) {
                    // check if the patch file previously applied
                    // then skip applying the patch
                    if (!servicepackPatchedList.contains(file.getName())) {
                        if (file.equals(bundleBackupDir)) {
                            // we already have handled this case,but need to write to the
                            // LauncherConstants.PRE_PATCHED_DIR_FILE file , it will do by below try catch block
                        } else {
                            // verify bundleFileName before copying the files to plugins
                            File[] patchFiles = file.listFiles();
                            patchLog.log(Level.FINE, "Applying - " + file.getName());
                            for (File patch : patchFiles) {
                                String patchFileName = verifyBundleFileName(patch);
                                File copiedFile = new File(target, patchFileName);
                                FileUtils.copyFile(patch, copiedFile, true);
                                try {
                                    patchLog.log(Level.FINE, "Patched " + patch.getName() + "(MD5:" + PatchUtils
                                            .getMD5ChecksumHexString(patch) + ")");
                                } catch (Exception e) {
                                    // handle this exception, this shouldn't interrupt the patch applying process
                                    patchLog.log(Level.SEVERE,
                                                 "Error occurred while generating md5 checksum for " + patch.getName());
                                }
                            }
                        }
                        try {
                            // write applying patches directory names to LauncherConstants.PRE_PATCHED_DIR_FILE
                            bufWriter.write(file.getName());
                            bufWriter.newLine();
                        } catch (IOException e) {
                            patchLog.log(Level.SEVERE,
                                         "Error occurred while writing " + file.getName() + " directory name to "
                                                 + patchDirLogFile.getName());
                        }
                    }
                }
            }
        } finally {
            try {
                bufWriter.close();
            } catch (IOException e) {
                patchLog.log(Level.SEVERE, "Error occurred while closing patch directory log file Buffered Writer");
            }
        }
    }

    /**
     * This will copy jar files on servicepacks to plugins directory
     *
     * @param source servicepack directory
     * @param target plugin directory
     * @throws IOException
     */
    public static void copyLatestServicepack(File source, File target, BufferedWriter bufWriter) {
        File latestServicepack;
        // Sorting servicepacks folders.
        File[] servicepackFiles = source.listFiles(PatchUtils.getPatchFileNameFilter());
        if (servicepackFiles != null && servicepackFiles.length > 0) {
            Arrays.sort(servicepackFiles);
            latestServicepack = servicepackFiles[servicepackFiles.length - 1];

            if (latestServicepack.isDirectory()) {
                File servicepackLibs = FileUtils.getFile(latestServicepack, LauncherConstants.SERVICEPACK_LIB_DIR);
                patchLog.log(Level.FINE, "Start applying - " + latestServicepack.getName());
                File[] patchFiles = servicepackLibs.listFiles();
                if (patchFiles != null) {
                    for (File patch : patchFiles) {
                        try {
                            // verify bundleFileName before copying the files to plugins
                            String patchFileName = verifyBundleFileName(patch);
                            File copiedFile = new File(target, patchFileName);
                            // handle for both file and directories because original plugin dir has directories.
                            if (patch.isFile()) {
                                FileUtils.copyFile(patch, copiedFile, true);
                            } else if (patch.isDirectory()) {
                                FileUtils.copyDirectory(patch, copiedFile, true);
                            }
                            try {
                                patchLog.log(Level.FINE, "Patched " + patch.getName() + "(MD5:" + PatchUtils
                                        .getMD5ChecksumHexString(patch) + ")");
                            } catch (Exception e) {
                                // handle this exception, this shouldn't interrupt the patch applying process
                                patchLog.log(Level.SEVERE,
                                             "Error occurred while generating md5 checksum for " + patch.getName());
                            }
                        } catch (IOException e) {
                            patchLog.log(Level.SEVERE,
                                         "Error occurred while applying servicepack " + latestServicepack);
                        }
                    }
                }
                try {
                    // write applying servicepack directory names to LauncherConstants.PRE_PATCHED_DIR_FILE
                    bufWriter.write(latestServicepack.getName());
                    bufWriter.newLine();
                } catch (IOException e) {
                    patchLog.log(Level.SEVERE, "Error occurred while writing " + latestServicepack.getName() + " to "
                            + LauncherConstants.PRE_PATCHED_DIR_FILE);
                }
            }
        }

    }

    public static PatchInfo processPatches(File patchDirLogFile, File servicepackDir, File patchesDir)
            throws IOException {
        patchLog.log(Level.FINE, "Checking for patch changes ...");

        BufferedReader bufReader = null;
        PatchInfo patchInfo = new PatchInfo();
        previousPatchDirNames = new ArrayList<String>();
        List<String> patchApplyingOrder = getPatchApplyingOrder(servicepackDir, patchesDir);

        try {
            if (patchDirLogFile.exists()) {
                // read previously applied patches form file
                bufReader = new BufferedReader(new FileReader(patchDirLogFile));
                previousPatchDirNames = FileUtils.readLinesToList(bufReader);
                if (previousPatchDirNames != null) {
                    PatchUtils.checkForPatchChanges(patchApplyingOrder, previousPatchDirNames, patchInfo);
                }

            } else {
                if (patchApplyingOrder != null) {
                    for (String patchFile : patchApplyingOrder) {
                        if (!patchFile.equals(LauncherConstants.BUNDLE_BACKUP_DIR)) {
                            patchInfo.addNewPatches(patchFile);
                            if (patchFile.startsWith("servicepack")) {
                                patchLog.log(Level.FINE, "New service pack available - " + patchFile);
                            } else {
                                patchLog.log(Level.FINE, "New patch available - " + patchFile);
                            }
                        }
                    }
                }
                if (!patchDirLogFile.createNewFile()) {
                    patchLog.log(Level.SEVERE,
                                 "Error occurred while creating patch directory log file " + patchDirLogFile
                                         .getAbsolutePath());
                }
            }
            if (!patchInfo.isPatchesChanged()) {
                patchLog.log(Level.FINE,
                             "No new patch or service pack detected, server will start without applying patches ");
            }
            return patchInfo;
        } finally {
            // close bufferReader and release the LauncherConstants.PRE_PATCHED_DIR_FILE
            if (bufReader != null) {
                try {
                    bufReader.close();
                } catch (IOException e) {
                    patchLog.log(Level.SEVERE, "Error occurred while closing patch directory log file Buffered Reader");
                }
            }
        }
    }

    /**
     * get the list of most latest jars in latest servicepack and patches-xxx directories
     *
     * @param servicepackDir servicepack directory
     * @param parentPatchDir folder which contains the patches.
     * @return list of most latest jars in latest servicepack and patches directories
     */
    public static Map<String, JarInfo> getMostLatestJarsInServicepackAndPatches(File servicepackDir,
                                                                                File parentPatchDir) throws Exception {
        // this map contains all latest patched jars
        Map<String, JarInfo> latestPatchedJars = new HashMap<String, JarInfo>();
        // first lets take jars in servicepack directories
        File[] servicepackList = servicepackDir.listFiles(PatchUtils.getPatchFileNameFilter());
        if (servicepackList != null && servicepackList.length > 0) {
            // Sorting servicepack folders.
            Arrays.sort(servicepackList);
            File latestServicepack = servicepackList[servicepackList.length - 1];
            if (latestServicepack.isDirectory()) {
                // get latest jar file list of a servicepack
                File libDir = FileUtils.getFile(latestServicepack, LauncherConstants.SERVICEPACK_LIB_DIR);
                collectLatestJars(libDir, latestPatchedJars);
            }
        }
        // then take jars in patch directories
        getLatestJarsInPatches(parentPatchDir, servicepackPatchedList, latestPatchedJars);
        return latestPatchedJars;
    }

    /**
     * get the list of most latest jars in patches-xxx directories
     *
     * @param parentPatchDir     folder which contains the patches.
     * @param servicepackPatches
     * @param latestPatchedJars
     * @return
     */
    private static void getLatestJarsInPatches(File parentPatchDir, Set<String> servicepackPatches,
                                               Map<String, JarInfo> latestPatchedJars) throws Exception {
        // Sorting patch folders.
        File[] patchList = parentPatchDir.listFiles(PatchUtils.getPatchFileNameFilter());
        if (patchList != null) {
            Arrays.sort(patchList);
            for (File file : patchList) {
                if (file.isDirectory() && !servicepackPatches.contains(file.getName())) {
                    // if it's the patch0000 directory ignore it
                    // patch0000 directory is backup directory .
                    if (file.getName().equals(LauncherConstants.BUNDLE_BACKUP_DIR)) {
                        // do not need to check LauncherConstants.BUNDLE_BACKUP_DIR for latest jars
                    } else {
                        collectLatestJars(file, latestPatchedJars);
                    }
                }
            }
        }
    }

    /**
     * @param jarDirectory      directory need to be examined
     * @param latestPatchedJars latest jar map
     */
    private static void collectLatestJars(File jarDirectory, Map<String, JarInfo> latestPatchedJars) throws Exception {
        File[] patchFiles = jarDirectory.listFiles();
        if (patchFiles != null) {
            for (File patch : patchFiles) {
                String verifiedName = verifyBundleFileName(patch);
                latestPatchedJars.put(verifiedName, new JarInfo(verifiedName, patch.getAbsolutePath()));
            }
        }
    }

    /**
     * @param latestPatchedJar map of jar name and jar location
     * @param plugins          plugins directory
     * @throws Exception
     */
    public static void checkMD5Checksum(Map<String, JarInfo> latestPatchedJar, File plugins, boolean applyPatches)
            throws Exception {
        if (applyPatches) {
            patchLog.log(Level.INFO, "Patch verification started");
        } else {
            patchLog.log(Level.FINE, "Patch verification started");
        }
        List<String> warningList = new ArrayList<String>();
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(
                new File(PatchUtils.getMetaDirectory(), LauncherConstants.PRE_PATCHED_LATEST_JARS_FILE)));
        try {
            for (Map.Entry<String, JarInfo> entry : latestPatchedJar.entrySet()) {
                File file = FileUtils.getFile(plugins, entry.getKey());
                if (entry.getValue().getMd5SumValue() == null) {
                    entry.getValue().setMd5SumValue(getMD5ChecksumHexString(entry.getValue().getPath()));
                }
                String md5OfPatchedJar = entry.getValue().getMd5SumValue();
                bufferedWriter.write(entry.getKey() + ":" + entry.getValue().getMd5SumValue());
                bufferedWriter.newLine();

                if (file == null) {
                    warningList
                            .add(entry.getKey() + "(MD5: " + md5OfPatchedJar + ") has been patched with " + PatchUtils
                                    .getPatchedDirName(entry.getValue().getPath()) + ", but not applied");
                } else {
                    String md5OfPluginJar = getMD5ChecksumHexString(file);
                    if (!(md5OfPluginJar.equals(md5OfPatchedJar))) {
                        warningList.add(entry.getKey() + "(MD5: " + md5OfPatchedJar + ") has been patched with "
                                                + PatchUtils.getPatchedDirName(entry.getValue().getPath())
                                                + ", but not applied");
                    }
                }
            }
            if (warningList.size() > 0) {
                patchLog.log(Level.WARNING, "Problems found during patch verification. See below for details:");
                for (String warningMessage : warningList) {
                    patchLog.log(Level.WARNING, warningMessage);
                }
                patchLog.log(Level.WARNING,
                             "Patch verification completed with warnings. Please see  " + getPatchesLogsFile()
                                     .getAbsolutePath() + " for more details");
            } else {
                if (applyPatches) {
                    patchLog.log(Level.INFO, "Patch verification successfully completed");
                } else {
                    patchLog.log(Level.FINE, "Patch verification successfully completed");
                }
            }
        } finally {
            bufferedWriter.close();
        }
    }

    private static String getMD5ChecksumHexString(String filePath) throws Exception {
        return getMD5ChecksumHexString(new File(filePath));
    }

    /**
     * @param file generate md5 string to this file
     * @return generated md5 value as a string
     * @throws Exception
     */
    private static String getMD5ChecksumHexString(File file) throws Exception {
        byte[] _bytes = createChecksum(file, "MD5");
        StringBuilder sb = new StringBuilder();
        // convert byte[] to hex-String
        for (byte _byte : _bytes) {
            sb.append(String.format("%02x", _byte & 0xff));
        }
        return sb.toString();
    }

    /**
     * @param file generate checksum byte array to this file
     * @param type which type of checksum need to generate for eg: MD5 , SHA , MD2
     * @return generated byte array of checksum
     * @throws Exception
     */
    private static byte[] createChecksum(File file, String type) throws Exception {
        byte[] buffer = new byte[1024];
        // get the MessageDigest which handle the type(MD5 ,SHA , MD2) specifically
        MessageDigest complete = MessageDigest.getInstance(type);
        int numRead;
        InputStream fis = new FileInputStream(file);
        try {
            do {
                numRead = fis.read(buffer);
                if (numRead > 0) {
                    complete.update(buffer, 0, numRead);
                }
            } while (numRead != -1);
        } finally {
            fis.close();
        }
        return complete.digest();
    }

    /**
     * @param pathToPatchJar Absolute or relative path to jar in a  patch directory
     * @return patch directory name
     */
    private static String getPatchedDirName(String pathToPatchJar) {
        int endIndex = pathToPatchJar.lastIndexOf(File.separator);
        int beginIndex = pathToPatchJar.substring(0, endIndex).lastIndexOf(File.separator);
        return pathToPatchJar.substring(beginIndex + 1, endIndex);
    }

    /**
     * compare existing patch directories and previous applied patch directory to track down any changes
     *
     * @param patchApplyOrder    servicepacks and patches applying order
     * @param prePatchedDirNames BufferedReader to read patch directory log text file
     * @throws IOException
     */
    private static void checkForPatchChanges(List<String> patchApplyOrder, List<String> prePatchedDirNames,
                                             PatchInfo patchInfo) throws IOException {
        int prePatchedDirNamesCount = prePatchedDirNames.size();
        boolean checkPrePatch = (prePatchedDirNamesCount > 0);
        // handle service pack comparison
        if (checkPrePatch && patchApplyOrder.size() > 0) {
            if (patchApplyOrder.get(0).startsWith("s") && prePatchedDirNames.get(0).startsWith("s")) {
                int spackDiff = patchApplyOrder.get(0).compareTo(prePatchedDirNames.get(0));
                if (spackDiff == 0) {
                    // no servcie pack changes
                } else {
                    patchLog.log(Level.FINE, prePatchedDirNames.get(0) + " has been reverted");
                    patchInfo.addRemovedPatches(patchApplyOrder.get(0));
                    patchLog.log(Level.FINE, "New service pack available - " + patchApplyOrder.get(0));
                    patchInfo.addNewPatches(patchApplyOrder.get(0));
                }
                // remove both service patch entries
                prePatchedDirNames.remove(0);
                patchApplyOrder.remove(0);
            } else if (patchApplyOrder.get(0).startsWith("s")) {
                patchLog.log(Level.FINE, "New service pack available - " + patchApplyOrder.get(0));
                patchInfo.addNewPatches(patchApplyOrder.get(0));
                patchApplyOrder.remove(0);
            } else if (prePatchedDirNames.get(0).startsWith("s")) {
                patchLog.log(Level.FINE, prePatchedDirNames.get(0) + " has been reverted");
                patchInfo.addRemovedPatches(patchApplyOrder.get(0));
                prePatchedDirNames.remove(0);
            }
        } else if (checkPrePatch) {
            if (prePatchedDirNames.get(0).startsWith("s")) {
                patchLog.log(Level.FINE, prePatchedDirNames.get(0) + " has been reverted");
                patchInfo.addRemovedPatches(patchApplyOrder.get(0));
                prePatchedDirNames.remove(0);
            }
        } else if (patchApplyOrder.size() > 0) {
            if (patchApplyOrder.get(0).startsWith("s")) {
                patchLog.log(Level.FINE, "New service pack available - " + patchApplyOrder.get(0));
                patchInfo.addNewPatches(patchApplyOrder.get(0));
                patchApplyOrder.remove(0);
            }
        }

        int i = 0, j = 0;
        while (i < patchApplyOrder.size() && j < prePatchedDirNames.size()) {
            int diff = patchApplyOrder.get(i).compareTo(prePatchedDirNames.get(j));
            if (diff == 0) {
                i++;
                j++;
            } else if (diff > 0) {
                patchLog.log(Level.FINE, prePatchedDirNames.get(j) + " has been reverted");
                patchInfo.addRemovedPatches(prePatchedDirNames.get(j));
                j++;
            } else {
                patchLog.log(Level.FINE, "New patch available - " + patchApplyOrder.get(i));
                patchInfo.addNewPatches(patchApplyOrder.get(i));
                i++;
            }
        }

        while (i < patchApplyOrder.size()) {
            patchLog.log(Level.FINE, "New patch available - " + patchApplyOrder.get(i));
            patchInfo.addNewPatches(patchApplyOrder.get(i));
            i++;
        }

        while (j < prePatchedDirNames.size()) {
            patchLog.log(Level.FINE, prePatchedDirNames.get(j) + " has been reverted");
            patchInfo.addRemovedPatches(prePatchedDirNames.get(j));
            j++;
        }
    }

    /**
     * This method will return the new order list of servicepacks and patches to verify applied patches with PRE_PATCHED_DIR_FILE
     *
     * @param servicepackDir servicepack directory
     * @param patchesDir     patches directory
     * @return list of servicepacks and patches
     * @throws IOException
     */
    private static List<String> getPatchApplyingOrder(File servicepackDir, File patchesDir) {
        servicepackPatchedList = new HashSet<String>();
        List<String> patchApplyingList = new ArrayList<String>();
        getServiepackPatchOrder(servicepackDir, patchApplyingList);
        File[] patches = patchesDir.listFiles(PatchUtils.getPatchFileNameFilter());
        if (patches != null) {
            // Sorting patch folders.
            Arrays.sort(patches);
            for (File patch : patches) {
                if (patch.isDirectory()) {
                    if (!(servicepackPatchedList.contains(patch.getName()))) {
                        patchApplyingList.add(patch.getName());
                    }

                }
            }
        }
        return patchApplyingList;
    }

    private static void getServiepackPatchOrder(File servicepackDir, List<String> patchApplyingList) {
        File[] servicepacks = servicepackDir.listFiles(PatchUtils.getPatchFileNameFilter());
        if (servicepacks != null && servicepacks.length > 0) {
            // Sorting servicepack folders.
            Arrays.sort(servicepacks);
            File latestServicepack = servicepacks[servicepacks.length - 1];
            if (latestServicepack.isDirectory()) {
                patchApplyingList.add(latestServicepack.getName());
                // get patch list on a patches inside servicepack
                File servicepackPatchesFile = FileUtils
                        .getFile(latestServicepack, LauncherConstants.SERVICEPACK_PATCHES_FILE);
                try (BufferedReader bufReader = new BufferedReader(new FileReader(servicepackPatchesFile))) {
                    List<String> patchesInServicepack = FileUtils.readLinesToList(bufReader);
                    servicepackPatchedList.addAll(patchesInServicepack);
                } catch (IOException e) {
                    patchLog.log(Level.SEVERE, "Error occurred while reading " + latestServicepack + " patch file : "
                            + LauncherConstants.SERVICEPACK_PATCHES_FILE, e);
                }

            }
        }
    }

    /**
     * Verifies bundle file name against the naming convention:
     * bundleName_bundleVersion.jar
     *
     * @param file bungle to verify
     * @return verified bundle-fileName
     * @throws IOException
     */
    private static String verifyBundleFileName(File file) throws IOException {
        String newFileName = file.getName();
        if (file.getName().endsWith(".jar")) {
            try (JarFile jar = new JarFile(file)) {
                Attributes attributes = jar.getManifest().getMainAttributes();
                String name = attributes.getValue(LauncherConstants.BUNDLE_SYMBOLIC_NAME);
                String version = attributes.getValue(LauncherConstants.BUNDLE_VERSION);
                if (name != null && version != null) {
                    // Bundle-SymbolicName may have other parameters eg:singleton:=true , if so need to remove that part
                    int index = name.indexOf(";");
                    if (index != -1) {
                        name = name.substring(0, index);
                    }
                    String bundleFileName = name + "_" + version + ".jar";
                    // verify and correct the bundle filename
                    if (!(file.getName().equals(bundleFileName))) {
                        newFileName = bundleFileName;
                    }
                }
            }
        }
        return newFileName;
    }

    public static boolean checkUpdatedJars(Map<String, JarInfo> latestPatchedJar) throws Exception {
        File jarsFile = new File(PatchUtils.getMetaDirectory(), LauncherConstants.PRE_PATCHED_LATEST_JARS_FILE);
        if (jarsFile.exists()) {
            BufferedReader bufReader = new BufferedReader(new FileReader(jarsFile));
            Map<String, String> prePatchedJarsWithMD5;
            try {
                prePatchedJarsWithMD5 = FileUtils.readJarsWithMD5(bufReader);
            } finally {
                bufReader.close();
            }
            for (Map.Entry<String, JarInfo> jarInfoEntry : latestPatchedJar.entrySet()) {
                if (prePatchedJarsWithMD5.containsKey(jarInfoEntry.getKey())) {     // key is verified Bundle Name
                    String md5ofJar = getMD5ChecksumHexString(jarInfoEntry.getValue().getPath());
                    jarInfoEntry.getValue().setMd5SumValue(md5ofJar);
                    if (!md5ofJar.equals(prePatchedJarsWithMD5.get(jarInfoEntry.getKey()))) {
                        patchLog.log(Level.FINE, jarInfoEntry.getKey() + " has been updated");
                        return true;
                    }
                } else {
                    patchLog.log(Level.FINE, jarInfoEntry.getKey() + " has been added");
                    return true;
                }
            }
        }
        return false;
    }

    public static File getPatchesLogsFile() {
        return new File(Utils.getCarbonRepoPath() + File.separator + "logs" + File.separator
                                + LauncherConstants.PATCH_LOG_FILE);
    }

    public static File getMetaDirectory() {
        String patchesPath = System.getProperty("carbon.patches.dir.path");
        File metaDir;
        if (patchesPath == null) {
            metaDir = new File(Paths.get(Utils.getCarbonRepoPath(), "components", LauncherConstants.PARENT_PATCHES_DIR,
                                         LauncherConstants.PATCH_METADATA_DIR).toString());
        } else {
            metaDir = new File(Paths.get(patchesPath, LauncherConstants.PATCH_METADATA_DIR).toString());
        }

        if (!metaDir.exists() && !metaDir.mkdirs()) {
            patchLog.log(Level.WARNING, "Error while creating meta data directory in " + metaDir.getAbsolutePath());
        }
        return metaDir;
    }

    public static FilenameFilter getPatchFileNameFilter() {
        return new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("patch") || name.startsWith("servicepack");
            }
        };
    }
}
