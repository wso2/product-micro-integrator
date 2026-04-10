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

package org.wso2.micro.core.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

/**
 * AuditLogger is to log Audit logs from the management API.
 */
public class AuditLogger {
    private static Log AUDIT_LOG = LogFactory.getLog("AUDIT_LOG");
    public static void logAuditMessage(String performedBy, String type, String action, JSONObject info) {
        JSONObject logMessage = new JSONObject();
        logMessage.put("performedBy", performedBy);
        logMessage.put("action", action);
        logMessage.put("type", type);
        logMessage.put("info", info.toString());
        AUDIT_LOG.info(logMessage);
    }
    public static void logAuditMessage(String performedBy, String action, JSONObject info) {
        JSONObject logMessage = new JSONObject();
        logMessage.put("performedBy", performedBy);
        logMessage.put("action", action);
        logMessage.put("info", info.toString());
        AUDIT_LOG.info(logMessage);
    }
    public static void logAuditMessage(String log) {
        AUDIT_LOG.info(log);
    }
}
