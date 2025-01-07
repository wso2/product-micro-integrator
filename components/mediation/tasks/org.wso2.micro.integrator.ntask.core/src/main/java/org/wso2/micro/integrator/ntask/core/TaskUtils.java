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
package org.wso2.micro.integrator.ntask.core;

import org.apache.axiom.om.OMElement;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.util.SecurityManager;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.micro.core.util.CryptoException;
import org.wso2.micro.integrator.ntask.common.TaskException;
import org.wso2.micro.integrator.ntask.core.internal.TasksDSComponent;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * This class contains utitilty functions related to tasks.
 */
public class TaskUtils {

    public static final String SECURE_VAULT_NS = "http://org.wso2.securevault/configuration";

    public static final String SECRET_ALIAS_ATTR_NAME = "secretAlias";

    public static final String TASK_STATE_PROPERTY = "TASK_STATE_PROPERTY";

    public static final String TASK_OWNER_PROPERTY = "taskOwner";

    public static final String TASK_OWNER_NAME = "taskOwnerName";

    public static final String TASK_BELONGS_TO_INBOUND_ENDPOINT = "InboundEndpoint";

    public static final String START_IN_PAUSED_MODE = "startInPausedMode";

    private static SecretResolver secretResolver;

    public static Document convertToDocument(File file) throws TaskException {
        DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
        fac.setNamespaceAware(true);
        fac.setXIncludeAware(false);
        fac.setExpandEntityReferences(false);
        try {
            fac.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            fac.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            fac.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
            SecurityManager securityManager = new SecurityManager();
            securityManager.setEntityExpansionLimit(0);
            fac.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY, securityManager);
            return fac.newDocumentBuilder().parse(file);
        } catch (Exception e) {
            throw new TaskException("Error in creating an XML document from file: " + e.getMessage(),
                                    TaskException.Code.CONFIG_ERROR, e);
        }
    }

    private static void secureLoadElement(Element element) throws CryptoException {
        Attr secureAttr = element.getAttributeNodeNS(SECURE_VAULT_NS, SECRET_ALIAS_ATTR_NAME);
        if (secureAttr != null) {
            element.setTextContent(loadFromSecureVault(secureAttr.getValue()));
            element.removeAttributeNode(secureAttr);
        }
        NodeList childNodes = element.getChildNodes();
        int count = childNodes.getLength();
        Node tmpNode;
        for (int i = 0; i < count; i++) {
            tmpNode = childNodes.item(i);
            if (tmpNode instanceof Element) {
                secureLoadElement((Element) tmpNode);
            }
        }
    }

    private static synchronized String loadFromSecureVault(String alias) {
        if (secretResolver == null) {
            secretResolver = SecretResolverFactory.create((OMElement) null, false);
            secretResolver.init(TasksDSComponent.getSecretCallbackHandlerService().getSecretCallbackHandler());
        }
        return secretResolver.resolve(alias);
    }

    public static void secureResolveDocument(Document doc) throws TaskException {
        Element element = doc.getDocumentElement();
        if (element != null) {
            try {
                secureLoadElement(element);
            } catch (CryptoException e) {
                throw new TaskException("Error in secure load of document: " + e.getMessage(),
                                        TaskException.Code.UNKNOWN, e);
            }
        }
    }

    public static void setTaskState(org.wso2.micro.integrator.ntask.core.TaskRepository taskRepo, String taskName,
                                    TaskManager.TaskState taskState) throws TaskException {
        taskRepo.setTaskMetadataProp(taskName, TASK_STATE_PROPERTY, taskState.toString());
    }

    public static TaskManager.TaskState getTaskState(org.wso2.micro.integrator.ntask.core.TaskRepository taskRepo,
                                                     String taskName) throws TaskException {
        String currentTaskState = taskRepo.getTaskMetadataProp(taskName, TASK_STATE_PROPERTY);
        if (currentTaskState != null) {
            for (TaskManager.TaskState taskState : TaskManager.TaskState.values()) {
                if (currentTaskState.equalsIgnoreCase(taskState.toString())) {
                    return taskState;
                }
            }
        }
        return null;
    }

    public static void setTaskPaused(org.wso2.micro.integrator.ntask.core.TaskRepository taskRepo, String taskName,
                                     boolean paused) throws TaskException {
        if (paused) {
            setTaskState(taskRepo, taskName, TaskManager.TaskState.PAUSED);
        } else {
            setTaskState(taskRepo, taskName, TaskManager.TaskState.NORMAL);
        }
    }

    public static boolean isTaskPaused(org.wso2.micro.integrator.ntask.core.TaskRepository taskRepo, String taskName)
            throws TaskException {
        TaskManager.TaskState currentState = getTaskState(taskRepo, taskName);
        if (currentState == null || !currentState.equals(TaskManager.TaskState.PAUSED)) {
            return false;
        } else
            return true;
    }

    public static void setTaskFinished(org.wso2.micro.integrator.ntask.core.TaskRepository taskRepo, String taskName,
                                       boolean finished) throws TaskException {
        if (finished) {
            setTaskState(taskRepo, taskName, TaskManager.TaskState.FINISHED);
        } else {
            setTaskState(taskRepo, taskName, TaskManager.TaskState.NORMAL);
        }
    }

    public static boolean isTaskFinished(TaskRepository taskRepo, String taskName) throws TaskException {
        TaskManager.TaskState currentState = getTaskState(taskRepo, taskName);
        if (currentState == null || !currentState.equals(TaskManager.TaskState.FINISHED)) {
            return false;
        } else
            return true;
    }

}
