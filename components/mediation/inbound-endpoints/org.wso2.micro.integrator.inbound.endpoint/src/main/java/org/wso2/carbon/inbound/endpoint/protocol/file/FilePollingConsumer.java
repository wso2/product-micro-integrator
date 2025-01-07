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
package org.wso2.carbon.inbound.endpoint.protocol.file;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileNotFolderException;
import org.apache.commons.vfs2.FileNotFoundException;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.SynapseException;
import org.apache.synapse.commons.vfs.VFSConstants;
import org.apache.synapse.commons.vfs.VFSParamDTO;
import org.apache.synapse.commons.vfs.VFSUtils;
import org.apache.synapse.core.SynapseEnvironment;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * This class implement the processing logic related to inbound file protocol.
 * Common functinalities (with synapse vfs transport) are include in synapse
 * util that is found in synapse commons
 */
public class FilePollingConsumer {

    private static final Log log = LogFactory.getLog(FilePollingConsumer.class);
    private Properties vfsProperties;
    private boolean fileLock = true;
    private DefaultFileSystemManager fsManager = null;
    private String name;
    private SynapseEnvironment synapseEnvironment;
    private long scanInterval;
    private Long lastRanTime;
    private int lastCycle;
    private FileInjectHandler injectHandler;
    private Long waitTimeBeforeRead;
    private double fileSizeLimit = VFSConstants.DEFAULT_TRANSPORT_FILE_SIZE_LIMIT;

    private FileObject fileObject;
    private Integer iFileProcessingInterval = null;
    private Integer iFileProcessingCount = null;
    private int maxRetryCount;
    private long reconnectionTimeout;
    private String strFilePattern;
    private boolean autoLockRelease;
    private Boolean autoLockReleaseSameNode;
    private Long autoLockReleaseInterval;
    private boolean distributedLock;
    private Long distributedLockTimeout;
    private FileSystemOptions fso;
    private boolean isClosed;

    private boolean readSubDirectories = false;
    private String fileURI;

    private String actionAfterProcess;
    private boolean moveProcessedFilesToSubDirectories = false;
    private String moveFileURI;

    private String actionAfterFailure;
    private boolean moveFailureFilesToSubDirectories = false;
    private String moveFailureFileURI;

    // The symbol to include sub directories will be either '/*' or '\*' depending on the Operating system.
    private final int INCLUDE_SUB_DIR_SYMBOL_LENGTH = 2;

    private final String MOVE = "MOVE";
    private final String RELATIVE_PATH = "RELATIVE_PATH";

    public FilePollingConsumer(Properties vfsProperties, String name, SynapseEnvironment synapseEnvironment,
                               long scanInterval) {
        this.vfsProperties = vfsProperties;
        this.name = name;
        this.synapseEnvironment = synapseEnvironment;
        this.scanInterval = scanInterval;
        this.lastRanTime = null;

        setupParams();
        try {
            StandardFileSystemManager fsm = new StandardFileSystemManager();
            fsm.setConfiguration(getClass().getClassLoader().getResource("providers.xml"));
            fsm.init();
            fsManager = fsm;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException(e);
        }
        //Setup SFTP Options
        try {
            fso = VFSUtils.attachFileSystemOptions(VFSUtils.parseSchemeFileOptions(fileURI, vfsProperties), fsManager);
        } catch (Exception e) {
            log.warn("Unable to set the sftp Options", e);
            fso = null;
        }
    }

    /**
     * Register a handler to process the file stream after reading from the
     * source
     *
     * @param injectHandler
     */
    public void registerHandler(FileInjectHandler injectHandler) {
        this.injectHandler = injectHandler;
    }

    /**
     * This will be called by the task scheduler. If a cycle execution takes
     * more than the schedule interval, tasks will call this method ignoring the
     * interval. Timestamp based check is done to avoid that.
     */
    public void execute() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Start : File Inbound EP : " + name);
            }
            // Check if the cycles are running in correct interval and start
            // scan
            long currentTime = (new Date()).getTime();
            if (lastRanTime == null || ((lastRanTime + (scanInterval)) <= currentTime)) {
                lastRanTime = currentTime;
                poll();
            } else if (log.isDebugEnabled()) {
                log.debug(
                        "Skip cycle since cuncurrent rate is higher than the scan interval : VFS Inbound EP : " + name);
            }
            if (log.isDebugEnabled()) {
                log.debug("End : File Inbound EP : " + name);
            }
        } catch (Exception e) {
            log.error("Error while reading file. " + e.getMessage(), e);
        }
    }

    /**
     * Do the file processing operation for the given set of properties. Do the
     * checks and pass the control to processFile method
     */
    public FileObject poll() {
        if (fileURI == null || fileURI.trim().equals("")) {
            log.error("Invalid file url. Check the inbound endpoint configuration. Endpoint Name : " + name
                              + ", File URL : " + VFSUtils.maskURLPassword(fileURI));
            return null;
        }

        if (log.isDebugEnabled()) {
            log.debug("Start : Scanning directory or file : " + VFSUtils.maskURLPassword(fileURI));
        }

        if (!initFileCheck()) {
            // Unable to read from the source location provided.
            return null;
        }

        // If file/folder found proceed to the processing stage
        try {
            lastCycle = 0;
            if (fileObject.exists() && fileObject.isReadable()) {
                FileObject[] children = null;
                try {
                    children = fileObject.getChildren();
                } catch (FileNotFolderException ignored) {
                    if (log.isDebugEnabled()) {
                        log.debug("No Folder found. Only file found on : " + VFSUtils.maskURLPassword(fileURI));
                    }
                } catch (FileSystemException ex) {
                    log.error(ex.getMessage(), ex);
                }

                // if this is a file that would translate to a single message
                if (children == null || children.length == 0) {
                    // Fail record is a one that is processed but was not moved
                    // or deleted due to an error.
                    boolean isFailedRecord = VFSUtils.isFailRecord(fsManager, fileObject, fso);
                    if (!isFailedRecord) {
                        fileHandler();
                        if (injectHandler == null) {
                            return fileObject;
                        }
                    } else {
                        try {
                            lastCycle = 2;
                            moveOrDeleteAfterProcessing(fileObject);
                        } catch (SynapseException synapseException) {
                            log.error("File object '" + VFSUtils.maskURLPassword(fileObject.getURL().toString()) + "' "
                                              + "cloud not be moved after first attempt", synapseException);
                        }
                        if (fileLock) {
                            // TODO: passing null to avoid build break. Fix properly
                            VFSUtils.releaseLock(fsManager, fileObject, fso);
                        }
                        if (log.isDebugEnabled()) {
                            log.debug("File '" + VFSUtils.maskURLPassword(fileObject.getURL().toString())
                                              + "' has been marked as a failed" + " record, it will not process");
                        }
                    }
                } else {
                    FileObject fileObject = directoryHandler(children);
                    if (fileObject != null) {
                        return fileObject;
                    }
                }
            } else {
                log.warn("Unable to access or read file or directory : " + VFSUtils.maskURLPassword(fileURI) + "."
                                 + " Reason: " + (fileObject.exists() ?
                        (fileObject.isReadable() ? "Unknown reason" : "The file can not be read!") :
                        "The file does not exists!"));
                return null;
            }
        } catch (FileSystemException e) {
            log.error("Error checking for existence and readability : " + VFSUtils.maskURLPassword(fileURI), e);
            return null;
        } catch (Exception e) {
            log.error("Error while processing the file/folder in URL : " + VFSUtils.maskURLPassword(fileURI), e);
            return null;
        } finally {
            try {
                fileObject.close();
            } catch (Exception e) {
                log.error("Unable to close the file system. " + e.getMessage());
                log.error(e);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("End : Scanning directory or file : " + VFSUtils.maskURLPassword(fileURI));
        }
        return null;
    }

    /**
     * If not a folder just a file handle the flow
     *
     * @throws FileSystemException
     */
    private void fileHandler() throws FileSystemException {
        if (fileObject.getType() == FileType.FILE) {
            if (!fileLock || (fileLock && acquireLock(fsManager, fileObject))) {
                boolean runPostProcess = true;
                try {
                    if (processFile(fileObject) == null) {
                        runPostProcess = false;
                    }
                    lastCycle = 1;
                } catch (SynapseException e) {
                    lastCycle = 2;
                    log.error(
                            "Error processing File URI : " + VFSUtils.maskURLPassword(fileObject.getName().toString()),
                            e);
                }

                if (runPostProcess) {
                    try {
                        moveOrDeleteAfterProcessing(fileObject);
                    } catch (SynapseException synapseException) {
                        lastCycle = 3;
                        log.error("File object '" + VFSUtils.maskURLPassword(fileObject.getURL().toString()) + "' "
                                          + "cloud not be moved", synapseException);
                        VFSUtils.markFailRecord(fsManager, fileObject);
                    }
                }

                if (fileLock) {
                    // TODO: passing null to avoid build break. Fix properly
                    VFSUtils.releaseLock(fsManager, fileObject, fso);
                    if (log.isDebugEnabled()) {
                        log.debug("Removed the lock file '" + VFSUtils.maskURLPassword(fileObject.toString())
                                          + ".lock' of the file '" + VFSUtils.maskURLPassword(fileObject.toString()));
                    }
                }

            } else {
                log.error("Couldn't get the lock for processing the file : " + VFSUtils
                        .maskURLPassword(fileObject.getName().toString()));
            }

        } else {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find the file or failed file record. File : " + VFSUtils.maskURLPassword(fileURI));
            }
        }
    }

    /**
     * Setup the required parameters
     */
    private void setupParams() {

        ResolvedFileUri inFileUri = extractFileUri(VFSConstants.TRANSPORT_FILE_FILE_URI);
        if (Objects.nonNull(inFileUri)) {
            readSubDirectories = inFileUri.supportSubDirectories;
            fileURI = inFileUri.resolvedUri;
        } else {
            log.error("Invalid file url. Check the inbound endpoint configuration. Endpoint Name : "
                    + name + ", File URL : " + VFSUtils.maskURLPassword(fileURI));
        }

        actionAfterProcess = vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_ACTION_AFTER_PROCESS);
        if (MOVE.equals(actionAfterProcess)) {
            ResolvedFileUri outFileUri = extractFileUri(VFSConstants.TRANSPORT_FILE_MOVE_AFTER_PROCESS);
            if (Objects.nonNull(outFileUri)) {
                moveProcessedFilesToSubDirectories = outFileUri.supportSubDirectories;
                moveFileURI = outFileUri.resolvedUri;
            } else {
                log.error(VFSConstants.TRANSPORT_FILE_MOVE_AFTER_PROCESS + " undefined with "
                        + VFSConstants.TRANSPORT_FILE_ACTION_AFTER_PROCESS
                        + "=MOVE. Files will be deleted after processing");
            }
        }

        actionAfterFailure = vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_ACTION_AFTER_FAILURE);
        if (MOVE.equals(actionAfterFailure)) {
            ResolvedFileUri failFileUri = extractFileUri(VFSConstants.TRANSPORT_FILE_MOVE_AFTER_FAILURE);
            if (Objects.nonNull(failFileUri)) {
                moveFailureFilesToSubDirectories = failFileUri.supportSubDirectories;
                moveFailureFileURI = failFileUri.resolvedUri;
            } else {
                log.error(VFSConstants.TRANSPORT_FILE_MOVE_AFTER_FAILURE + " undefined with "
                        + VFSConstants.TRANSPORT_FILE_ACTION_AFTER_FAILURE
                        + "=MOVE. Files will be deleted after failure");
            }
        }

        String strFileLock = vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_LOCKING);
        if (strFileLock != null && strFileLock.toLowerCase().equals(VFSConstants.TRANSPORT_FILE_LOCKING_DISABLED)) {
            fileLock = false;
        }

        strFilePattern = vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_FILE_NAME_PATTERN);
        if (vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_INTERVAL) != null) {
            try {
                iFileProcessingInterval = Integer
                        .valueOf(vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_INTERVAL));
            } catch (NumberFormatException e) {
                log.warn("Invalid param value for transport.vfs.FileProcessInterval : " + vfsProperties
                        .getProperty(VFSConstants.TRANSPORT_FILE_INTERVAL) + ". Expected numeric value.");
            }
        }
        if (vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_COUNT) != null) {
            try {
                iFileProcessingCount = Integer.valueOf(vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_COUNT));
            } catch (NumberFormatException e) {
                log.warn("Invalid param value for transport.vfs.FileProcessCount : " + vfsProperties
                        .getProperty(VFSConstants.TRANSPORT_FILE_COUNT) + ". Expected numeric value.");
            }
        }
        maxRetryCount = 0;
        if (vfsProperties.getProperty(VFSConstants.MAX_RETRY_COUNT) != null) {
            try {
                maxRetryCount = Integer.valueOf(vfsProperties.getProperty(VFSConstants.MAX_RETRY_COUNT));
            } catch (NumberFormatException e) {
                log.warn("Invalid values for Max Retry Count");
                maxRetryCount = 0;
            }
        }

        reconnectionTimeout = 1;
        if (vfsProperties.getProperty(VFSConstants.RECONNECT_TIMEOUT) != null) {
            try {
                reconnectionTimeout = Long.valueOf(vfsProperties.getProperty(VFSConstants.RECONNECT_TIMEOUT));
            } catch (NumberFormatException e) {
                log.warn("Invalid values for Reconnection Timeout");
                reconnectionTimeout = 1;
            }
        }

        String strAutoLock = vfsProperties.getProperty(VFSConstants.TRANSPORT_AUTO_LOCK_RELEASE);
        autoLockRelease = false;
        autoLockReleaseSameNode = true;
        autoLockReleaseInterval = null;
        if (strAutoLock != null) {
            try {
                autoLockRelease = Boolean.parseBoolean(strAutoLock);
            } catch (Exception e) {
                autoLockRelease = false;
                log.warn("VFS Auto lock removal not set properly. Current value is : " + strAutoLock, e);
            }
            if (autoLockRelease) {
                String strAutoLockInterval = vfsProperties
                        .getProperty(VFSConstants.TRANSPORT_AUTO_LOCK_RELEASE_INTERVAL);
                if (strAutoLockInterval != null) {
                    try {
                        autoLockReleaseInterval = Long.parseLong(strAutoLockInterval);
                    } catch (Exception e) {
                        autoLockReleaseInterval = null;
                        log.warn("VFS Auto lock removal property not set properly. Current value is : "
                                         + strAutoLockInterval, e);
                    }
                }
                String strAutoLockReleaseSameNode = vfsProperties
                        .getProperty(VFSConstants.TRANSPORT_AUTO_LOCK_RELEASE_SAME_NODE);
                if (strAutoLockReleaseSameNode != null) {
                    try {
                        autoLockReleaseSameNode = Boolean.parseBoolean(strAutoLockReleaseSameNode);
                    } catch (Exception e) {
                        autoLockReleaseSameNode = true;
                        log.warn("VFS Auto lock removal property not set properly. Current value is : "
                                         + autoLockReleaseSameNode, e);
                    }
                }
            }

        }
        distributedLock = false;
        distributedLockTimeout = null;
        String strDistributedLock = vfsProperties.getProperty(VFSConstants.TRANSPORT_DISTRIBUTED_LOCK);
        if (strDistributedLock != null) {
            try {
                distributedLock = Boolean.parseBoolean(strDistributedLock);
            } catch (Exception e) {
                autoLockRelease = false;
                log.warn("VFS Distributed lock not set properly. Current value is : " + strDistributedLock, e);
            }

            if (distributedLock) {
                String strDistributedLockTimeout = vfsProperties
                        .getProperty(VFSConstants.TRANSPORT_DISTRIBUTED_LOCK_TIMEOUT);
                if (strDistributedLockTimeout != null) {
                    try {
                        distributedLockTimeout = Long.parseLong(strDistributedLockTimeout);
                    } catch (Exception e) {
                        distributedLockTimeout = null;
                        log.warn("VFS Distributed lock timeout property not set properly. Current value is : "
                                         + strDistributedLockTimeout, e);
                    }
                }

            }

        }

        waitTimeBeforeRead = null;
        String strWaitTimeBeforeRead = vfsProperties.getProperty(VFSConstants.WAIT_TIME_BEFORE_READ);
        if (strWaitTimeBeforeRead != null) {
            try {
                waitTimeBeforeRead = Long.parseLong(strWaitTimeBeforeRead);
            } catch (NumberFormatException e) {
                waitTimeBeforeRead = null;
                log.warn("VFS Wait time before read is not set properly. Current value is: " + strWaitTimeBeforeRead,
                         e);
            }
        }

        String strFileSizeLimit = vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_SIZE_LIMIT);
        try {
            fileSizeLimit = Objects.nonNull(strFileSizeLimit)
                    ? Double.parseDouble(strFileSizeLimit) : VFSConstants.DEFAULT_TRANSPORT_FILE_SIZE_LIMIT;
        } catch (NumberFormatException e) {
            log.warn("VFS " + VFSConstants.TRANSPORT_FILE_SIZE_LIMIT + "is not set properly. Current value is: "
                    + strFileSizeLimit + ", using default: unlimited");
        }
    }

    /**
     * Handle directory with chile elements
     *
     * @param children
     * @return
     * @throws FileSystemException
     */
    private FileObject directoryHandler(FileObject[] children) throws FileSystemException {
        // Process Directory
        lastCycle = 0;
        int failCount = 0;
        int successCount = 0;
        int processCount = 0;

        if (log.isDebugEnabled()) {
            log.debug(
                    "File name pattern : " + vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_FILE_NAME_PATTERN));
        }

        // Sort the files
        String strSortParam = vfsProperties.getProperty(VFSConstants.FILE_SORT_PARAM);
        if (strSortParam != null && !"NONE".equals(strSortParam)) {
            log.debug("Start Sorting the files.");
            String strSortOrder = vfsProperties.getProperty(VFSConstants.FILE_SORT_ORDER);
            boolean bSortOrderAsscending = true;
            if (strSortOrder != null && strSortOrder.toLowerCase().equals("false")) {
                bSortOrderAsscending = false;
            }
            if (log.isDebugEnabled()) {
                log.debug("Sorting the files by : " + strSortOrder + ". (" + bSortOrderAsscending + ")");
            }
            if (strSortParam.equals(VFSConstants.FILE_SORT_VALUE_NAME) && bSortOrderAsscending) {
                Arrays.sort(children, new FileNameAscComparator());
            } else if (strSortParam.equals(VFSConstants.FILE_SORT_VALUE_NAME) && !bSortOrderAsscending) {
                Arrays.sort(children, new FileNameDesComparator());
            } else if (strSortParam.equals(VFSConstants.FILE_SORT_VALUE_SIZE) && bSortOrderAsscending) {
                Arrays.sort(children, new FileSizeAscComparator());
            } else if (strSortParam.equals(VFSConstants.FILE_SORT_VALUE_SIZE) && !bSortOrderAsscending) {
                Arrays.sort(children, new FileSizeDesComparator());
            } else if (strSortParam.equals(VFSConstants.FILE_SORT_VALUE_LASTMODIFIEDTIMESTAMP)
                    && bSortOrderAsscending) {
                Arrays.sort(children, new FileLastmodifiedtimestampAscComparator());
            } else if (strSortParam.equals(VFSConstants.FILE_SORT_VALUE_LASTMODIFIEDTIMESTAMP)
                    && !bSortOrderAsscending) {
                Arrays.sort(children, new FileLastmodifiedtimestampDesComparator());
            }
            log.debug("End Sorting the files.");
        }

        for (FileObject child : children) {
            // skipping *.lock / *.fail file
            if (child.getName().getBaseName().endsWith(".lock") || child.getName().getBaseName().endsWith(".fail")) {
                continue;
            }
            boolean isFailedRecord = VFSUtils.isFailRecord(fsManager, child, fso);
            boolean isReadyToRead = VFSUtils.isReadyToRead(child, waitTimeBeforeRead);

            if (readSubDirectories && child.isFolder()) {
                // If file/folder found proceed to the processing stage
                if (child.exists() && child.isReadable()) {
                    FileObject[] childrenOfChild;
                    childrenOfChild = child.getChildren();
                    if (null != childrenOfChild && 0 != childrenOfChild.length) {
                        directoryHandler(childrenOfChild);
                    }
                } else {
                    log.warn("Unable to access or read file or directory : " + VFSUtils.maskURLPassword(fileURI)
                            + ". Reason: "
                            + (child.exists() ? (child.isReadable() ? "Unknown reason" : "The file can not be read!")
                            : "The file does not exists!"));
                }
            }
            //Skip processing sub directories if not specified
            else if (child.isFolder()){
                continue;
            }

            // child's file name matches the file name pattern or process all
            // files now we try to get the lock and process
            else if ((strFilePattern == null || child.getName().getBaseName().matches(strFilePattern)) && !isFailedRecord
                    && isReadyToRead) {

                if (log.isDebugEnabled()) {
                    log.debug("Matching file : " + child.getName().getBaseName());
                }

                if ((!fileLock || (fileLock && acquireLock(fsManager, child)))) {
                    // process the file
                    boolean runPostProcess = true;
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("Processing file :" + VFSUtils.maskURLPassword(child.toString()));
                        }
                        processCount++;
                        if (processFile(child) == null) {
                            runPostProcess = false;
                        } else {
                            successCount++;
                        }
                        // tell moveOrDeleteAfterProcessing() file was success
                        lastCycle = 1;
                    } catch (Exception e) {
                        if (e.getCause() instanceof FileNotFoundException) {
                            log.warn("Error processing File URI : " + VFSUtils
                                    .maskURLPassword(child.getName().toString())
                                             + ". This can be due to file moved from another process.");
                            runPostProcess = false;
                        } else {
                            log.error("Error processing File URI : " + VFSUtils
                                    .maskURLPassword(child.getName().toString()), e);
                            failCount++;
                            // tell moveOrDeleteAfterProcessing() file failed
                            lastCycle = 2;
                        }

                    }
                    // skipping un-locking file if failed to do delete/move
                    // after process
                    boolean skipUnlock = false;
                    if (runPostProcess) {
                        try {
                            moveOrDeleteAfterProcessing(child);
                        } catch (SynapseException synapseException) {
                            log.error("File object '" + VFSUtils.maskURLPassword(child.getURL().toString())
                                              + "'cloud not be moved, will remain in \"locked\" state",
                                      synapseException);
                            skipUnlock = true;
                            failCount++;
                            lastCycle = 3;
                            VFSUtils.markFailRecord(fsManager, child);
                        }
                    }
                    // if there is a failure or not we'll try to release the
                    // lock
                    if (fileLock && !skipUnlock) {
                        // TODO: passing null to avoid build break. Fix properly
                        VFSUtils.releaseLock(fsManager, child, fso);
                    }
                    if (injectHandler == null) {
                        return child;
                    }
                }
            } else if (log.isDebugEnabled() && strFilePattern != null && !child.getName().getBaseName()
                    .matches(strFilePattern) && !isFailedRecord) {
                // child's file name does not match the file name pattern
                log.debug("Non-Matching file : " + child.getName().getBaseName());
            } else if (isFailedRecord) {
                // it is a failed record
                try {
                    lastCycle = 1;
                    moveOrDeleteAfterProcessing(child);
                } catch (SynapseException synapseException) {
                    log.error("File object '" + VFSUtils.maskURLPassword(child.getURL().toString())
                                      + "'cloud not be moved, will remain in \"fail\" state", synapseException);
                }
                if (fileLock) {
                    // TODO: passing null to avoid build break. Fix properly
                    VFSUtils.releaseLock(fsManager, child, fso);
                    VFSUtils.releaseLock(fsManager, fileObject, fso);
                }
                if (log.isDebugEnabled()) {
                    log.debug("File '" + VFSUtils.maskURLPassword(fileObject.getURL().toString())
                                      + "' has been marked as a failed record, it will not " + "process");
                }
            } else if (!isReadyToRead) {
                log.debug("File cannot be read as it has to wait for some time: " + child.getName().getBaseName());
            }

            //close the file system after processing
            try {
                child.close();
            } catch (Exception e) {
            }

            // Manage throttling of file processing
            if (iFileProcessingInterval != null && iFileProcessingInterval > 0) {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Put the VFS processor to sleep for : " + iFileProcessingInterval);
                    }
                    Thread.sleep(iFileProcessingInterval);
                } catch (InterruptedException ie) {
                    log.error("Unable to set the interval between file processors." + ie);
                    Thread.currentThread().interrupt();
                }
            } else if (iFileProcessingCount != null && iFileProcessingCount <= processCount) {
                break;
            }
        }
        if (failCount == 0 && successCount > 0) {
            lastCycle = 1;
        } else if (successCount == 0 && failCount > 0) {
            lastCycle = 4;
        } else {
            lastCycle = 5;
        }
        return null;
    }

    /**
     * Check if the file/folder exists before proceeding and retrying
     */
    private boolean initFileCheck() {
        boolean wasError = true;
        int retryCount = 0;

        fileObject = null;
        while (wasError) {
            try {
                if (isClosed) {
                    return false;
                }
                retryCount++;
                fileObject = fsManager.resolveFile(fileURI, fso);
                if (fileObject == null) {
                    log.error("fileObject is null");
                    throw new FileSystemException("fileObject is null");
                }
                wasError = false;
            } catch (FileSystemException e) {
                if (retryCount >= maxRetryCount) {
                    log.error("Repeatedly failed to resolve the file URI: " + VFSUtils.maskURLPassword(fileURI), e);
                    return false;
                } else {
                    log.warn("Failed to resolve the file URI: " + VFSUtils.maskURLPassword(fileURI) + ", in attempt "
                                     + retryCount + ", " + e.getMessage() + " Retrying in " + reconnectionTimeout
                                     + " milliseconds.");
                }
            }
            if (wasError) {
                try {
                    Thread.sleep(reconnectionTimeout);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                    log.error("Thread was interrupted while waiting to reconnect.", e2);
                }
            }
        }
        return true;
    }

    /**
     * Acquire distributed lock if required first, then do the file level locking
     *
     * @param fsManager
     * @param fileObject
     * @return
     */
    private boolean acquireLock(FileSystemManager fsManager, FileObject fileObject) {
        String strContext = fileObject.getName().getURI();
        boolean rtnValue = false;

        // When processing a directory list is fetched initially. Therefore
        // there is still a chance of file processed by another process.
        // Need to check the source file before processing.
        try {
            String parentURI = fileObject.getParent().getName().getURI();
            if (parentURI.contains("?")) {
                String suffix = parentURI.substring(parentURI.indexOf("?"));
                strContext += suffix;
            }
            FileObject sourceFile = fsManager.resolveFile(strContext, fso);
            if (!sourceFile.exists()) {
                return false;
            }
        } catch (FileSystemException e) {
            return false;
        }
        VFSParamDTO vfsParamDTO = new VFSParamDTO();
        vfsParamDTO.setAutoLockRelease(autoLockRelease);
        vfsParamDTO.setAutoLockReleaseSameNode(autoLockReleaseSameNode);
        vfsParamDTO.setAutoLockReleaseInterval(autoLockReleaseInterval);
        rtnValue = VFSUtils.acquireLock(fsManager, fileObject, vfsParamDTO, fso, true);

        return rtnValue;
    }

    /**
     * Actual processing of the file/folder
     *
     * @param file
     * @return
     * @throws SynapseException
     */
    private FileObject processFile(FileObject file) throws SynapseException {
        try {
            FileContent content = file.getContent();
            String fileName = file.getName().getBaseName();
            if (fileSizeLimit >= 0 && (content.getSize() > fileSizeLimit)) {
                if (log.isDebugEnabled()) {
                    log.debug("Ignoring file: " + fileName + " size: " + content.getSize() + " since it exceeds file size limit: " + fileSizeLimit);
                }
                return null;
            }
            String filePath = file.getName().getPath();
            String fileURI = file.getName().getURI();

            if (injectHandler != null) {
                Map<String, Object> transportHeaders = new HashMap<String, Object>();
                transportHeaders.put(VFSConstants.FILE_PATH, filePath);
                transportHeaders.put(VFSConstants.FILE_NAME, fileName);
                transportHeaders.put(VFSConstants.FILE_URI, fileURI);
                if (readSubDirectories) {
                    try {
                        transportHeaders.put(RELATIVE_PATH, extractRelativePath(file));
                    } catch (FileSystemException e) {
                        log.warn("Unable to extract the relative path of the file.", e);
                    }
                }

                try {
                    transportHeaders.put(VFSConstants.FILE_LENGTH, content.getSize());
                    transportHeaders.put(VFSConstants.LAST_MODIFIED, content.getLastModifiedTime());
                } catch (FileSystemException e) {
                    log.warn("Unable to set file length or last modified date header.", e);
                }

                injectHandler.setTransportHeaders(transportHeaders);
                // injectHandler
                if (!injectHandler.invoke(file, name)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to inject the file : " + VFSUtils.maskURLPassword(file.toString()));
                    }
                    return null;
                }
            }

        } catch (FileSystemException e) {
            log.error("Error reading file content or attributes : " + VFSUtils.maskURLPassword(file.toString()), e);
        }
        return file;
    }

    /**
     * Do the post processing actions
     *
     * @param fileObject
     * @throws SynapseException
     */
    private void moveOrDeleteAfterProcessing(FileObject fileObject) throws SynapseException {

        String moveToDirectoryURI = null;
        boolean supportSubDirectory = false;
        try {
            switch (lastCycle) {
            case 1:
                if (MOVE.equals(actionAfterProcess)) {
                    supportSubDirectory = moveProcessedFilesToSubDirectories;
                    moveToDirectoryURI = optionallyAppendDateToUri(moveFileURI);
                }
                break;

            case 2:
                if (MOVE.equals(actionAfterFailure)) {
                    supportSubDirectory = moveFailureFilesToSubDirectories;
                    //Postfix the date given timestamp format
                    moveToDirectoryURI = optionallyAppendDateToUri(moveFailureFileURI);
                }
                break;

            default:
                return;
            }

            if (moveToDirectoryURI != null) {
                if (supportSubDirectory) {
                    moveToDirectoryURI = resolveActualOutUrl(fileObject, moveToDirectoryURI);
                }
                // This handles when file needs to move to a different file-system
                FileSystemOptions destinationFSO = null;
                try {
                    destinationFSO = VFSUtils
                            .attachFileSystemOptions(VFSUtils.parseSchemeFileOptions(moveToDirectoryURI, vfsProperties),
                                                     fsManager);
                } catch (Exception e) {
                    log.warn("Unable to set the options for processed file location ", e);
                }
                FileObject moveToDirectory = fsManager.resolveFile(moveToDirectoryURI, destinationFSO);
                String prefix;
                if (vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_MOVE_TIMESTAMP_FORMAT) != null) {
                    prefix = new SimpleDateFormat(
                            vfsProperties.getProperty(VFSConstants.TRANSPORT_FILE_MOVE_TIMESTAMP_FORMAT))
                            .format(new Date());
                } else {
                    prefix = "";
                }

                //Forcefully create the folder(s) if does not exists
                boolean createFolder
                        = Boolean.parseBoolean(vfsProperties.getProperty(VFSConstants.FORCE_CREATE_FOLDER));
                if ((supportSubDirectory || createFolder) && !moveToDirectory.exists()) {
                    moveToDirectory.createFolder();
                }

                FileObject dest = moveToDirectory.resolveFile(prefix + fileObject.getName().getBaseName());
                if (log.isDebugEnabled()) {
                    log.debug("Moving to file :" + VFSUtils.maskURLPassword(dest.getName().getURI()));
                }
                try {
                    String updateLastModified = vfsProperties.getProperty(VFSConstants.UPDATE_LAST_MODIFIED);
                    if (updateLastModified != null) {
                        dest.setUpdateLastModified(Boolean.parseBoolean(updateLastModified));
                    }
                    fileObject.moveTo(dest);
                } catch (FileSystemException e) {
                    if (!VFSUtils.isFailRecord(fsManager, fileObject, fso)) {
                        VFSUtils.markFailRecord(fsManager, fileObject, fso);
                    }
                    log.error(
                            "Error moving file : " + VFSUtils.maskURLPassword(fileObject.toString()) + " to " + VFSUtils
                                    .maskURLPassword(moveToDirectoryURI), e);
                }
            } else {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Deleting file :" + VFSUtils.maskURLPassword(fileObject.toString()));
                    }
                    fileObject.close();
                    if (!fileObject.delete()) {
                        String msg = "Cannot delete file : " + VFSUtils.maskURLPassword(fileObject.toString());
                        log.error(msg);
                        throw new SynapseException(msg);
                    }
                } catch (FileSystemException e) {
                    log.error("Error deleting file : " + VFSUtils.maskURLPassword(fileObject.toString()), e);
                }
            }
        } catch (FileSystemException e) {
            if (!VFSUtils.isFailRecord(fsManager, fileObject, fso)) {
                VFSUtils.markFailRecord(fsManager, fileObject, fso);
                log.error("Error resolving directory to move after processing : " + VFSUtils
                        .maskURLPassword(moveToDirectoryURI), e);
            }
        }
    }

    private String optionallyAppendDateToUri(String moveToDirectoryURI) {
        String strSubfoldertimestamp = vfsProperties
                .getProperty(VFSConstants.SUBFOLDER_TIMESTAMP);
        if (strSubfoldertimestamp != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(strSubfoldertimestamp);
                String strDateformat = sdf.format(new Date());
                int iIndex = moveToDirectoryURI.indexOf("?");
                if (iIndex > -1) {
                    moveToDirectoryURI = moveToDirectoryURI.substring(0, iIndex)
                            + strDateformat
                            + moveToDirectoryURI.substring(iIndex);
                }else{
                    moveToDirectoryURI += strDateformat;
                }
            } catch (Exception e) {
                log.warn("Error generating subfolder name with date", e);
            }
        }
        return moveToDirectoryURI;
    }

    /**
     * Comparator classed used to sort the files according to user input
     */
    class FileNameAscComparator implements Comparator<FileObject> {
        @Override
        public int compare(FileObject o1, FileObject o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }

    class FileLastmodifiedtimestampAscComparator implements Comparator<FileObject> {
        @Override
        public int compare(FileObject o1, FileObject o2) {
            Long lDiff = 0l;
            try {
                lDiff = o1.getContent().getLastModifiedTime() - o2.getContent().getLastModifiedTime();
            } catch (FileSystemException e) {
                log.warn("Unable to compare lastmodified timestamp of the two files.", e);
            }
            return lDiff.intValue();
        }
    }

    class FileSizeAscComparator implements Comparator<FileObject> {
        @Override
        public int compare(FileObject o1, FileObject o2) {
            Long lDiff = 0l;
            try {
                lDiff = o1.getContent().getSize() - o2.getContent().getSize();
            } catch (FileSystemException e) {
                log.warn("Unable to compare size of the two files.", e);
            }
            return lDiff.intValue();
        }
    }

    class FileNameDesComparator implements Comparator<FileObject> {
        @Override
        public int compare(FileObject o1, FileObject o2) {
            return o2.getName().compareTo(o1.getName());
        }
    }

    class FileLastmodifiedtimestampDesComparator implements Comparator<FileObject> {
        @Override
        public int compare(FileObject o1, FileObject o2) {
            Long lDiff = 0l;
            try {
                lDiff = o2.getContent().getLastModifiedTime() - o1.getContent().getLastModifiedTime();
            } catch (FileSystemException e) {
                log.warn("Unable to compare lastmodified timestamp of the two files.", e);
            }
            return lDiff.intValue();
        }
    }

    class FileSizeDesComparator implements Comparator<FileObject> {
        @Override
        public int compare(FileObject o1, FileObject o2) {
            Long lDiff = 0l;
            try {
                lDiff = o2.getContent().getSize() - o1.getContent().getSize();
            } catch (FileSystemException e) {
                log.warn("Unable to compare size of the two files.", e);
            }
            return lDiff.intValue();
        }
    }

    protected Properties getInboundProperties() {
        return vfsProperties;
    }

    void destroy() {
        fsManager.close();
        this.close();
    }

    void close() {
        isClosed = true;
    }

    void start() {
        isClosed = false;
    }

    private String sanitizeFileUriWithSub(String originalFileUri) {
        String[] splitUri = originalFileUri.split("\\?");
        splitUri[0] = splitUri[0].substring(0, splitUri[0].length() - INCLUDE_SUB_DIR_SYMBOL_LENGTH);
        return splitUri.length == 1 ? splitUri[0] : splitUri[0] + "?" + splitUri[1];
    }

    private boolean supportSubDirectory(String originalFileUri) {
        String[] splitUri = originalFileUri.split("\\?");

        // The symbol to include sub directories will be either '/*' or '\*' depending on the Operating system.
        return splitUri[0].endsWith("/*") || splitUri[0].endsWith("\\*");
    }

    private static class ResolvedFileUri {
        String resolvedUri;
        boolean supportSubDirectories;

        ResolvedFileUri(String fileUri, boolean supportSubDirectories) {
            this.resolvedUri = fileUri;
            this.supportSubDirectories = supportSubDirectories;
        }
    }

    private ResolvedFileUri extractFileUri(String propertyForUri) {
        String definedFileUri = vfsProperties.getProperty(propertyForUri);
        if (StringUtils.isNotEmpty(definedFileUri)) {
            if (supportSubDirectory(definedFileUri)) {
                return new ResolvedFileUri(sanitizeFileUriWithSub(definedFileUri), true);
            } else {
                return new ResolvedFileUri(definedFileUri, false);
            }
        }
        return null;
    }

    private String resolveActualOutUrl(FileObject fileObject, String definedOutUrl) {
        String pathRelativeToInDirectory;
        try {
            pathRelativeToInDirectory = extractRelativePath(fileObject);
        } catch (FileSystemException e) {
            throw new SynapseException("Error accessing parent directory of processed file.", e);
        }

        String[] splitUri = definedOutUrl.split("\\?");
        if (splitUri.length == 1) {
            return (pathRelativeToInDirectory.isEmpty() ? definedOutUrl : definedOutUrl + pathRelativeToInDirectory);
        } else {
            return (pathRelativeToInDirectory.isEmpty() ? definedOutUrl :
                    splitUri[0] + pathRelativeToInDirectory + "?" + splitUri[1]);
        }
    }

    private String extractRelativePath(FileObject fileObject) throws FileSystemException {
        
        String parentPath = fileObject.getParent().getPublicURIString();
        // Escape the meta characters . [ ] { } ( ) \ ^ $ | ? * +
        String path = this.fileObject.getPublicURIString().replaceAll("([\\Q{}()[]^$|?*+&$\\E])",  "\\\\$1");
        String pathRelativeToInDirectory = parentPath.replaceFirst(path, "");
        return pathRelativeToInDirectory;
    }
}
