/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.management.apis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.aspects.AspectConfiguration;
import org.json.JSONObject;

import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Generic helper that captures the repetitive pattern shared across
 * {@link ArtifactStatisticsManager}, {@link ArtifactTracingManager}, and
 * {@link ArtifactStatusManager}:
 * <ol>
 *   <li>Look up an artifact by name</li>
 *   <li>Return a 4xx error when the artifact is {@code null}</li>
 *   <li>Build a one-entry {@code info} {@link JSONObject}</li>
 *   <li>Delegate to the operation-specific handler</li>
 * </ol>
 */
class ArtifactOperationHelper {

    private static final Log log = LogFactory.getLog(ArtifactOperationHelper.class);

    /**
     * Functional interface for aspect operations (statistics / tracing) that accept an
     * {@link AspectConfiguration} and may throw {@link IOException}.
     * Both {@code Utils::handleStatistics} and {@code Utils::handleTracing} satisfy this shape.
     */
    @FunctionalInterface
    interface AspectOperation {
        JSONObject perform(String performedBy, String auditLogType, String artifactType,
                           JSONObject info, AspectConfiguration config, String artifactName,
                           org.apache.axis2.context.MessageContext axis2MC) throws IOException;
    }

    /**
     * Handles the lookup → null-check → info-build → aspect-operation pattern used by
     * {@link ArtifactStatisticsManager} and {@link ArtifactTracingManager}.
     *
     * @param artifact      looked-up artifact; {@code null} produces a 400 error response
     * @param name          artifact name placed in the info object and forwarded to {@code operation}
     * @param notFoundMsg   error message when {@code artifact} is {@code null}
     * @param infoKey       JSON key for the info object (e.g. {@code "proxyServiceName"})
     * @param performedBy   authenticated user performing the operation
     * @param auditLogType  audit-log type constant
     * @param artifactType  artifact-type constant (e.g. {@link Constants#PROXY_SERVICES})
     * @param getConfig     extracts the {@link AspectConfiguration} from the artifact
     * @param axis2MC       Axis2 message context used for error responses
     * @param operation     delegate to invoke, e.g. {@code Utils::handleStatistics}
     */
    static <T> JSONObject handleAspectOperation(
            T artifact, String name, String notFoundMsg, String infoKey,
            String performedBy, String auditLogType, String artifactType,
            Function<T, AspectConfiguration> getConfig,
            org.apache.axis2.context.MessageContext axis2MC,
            AspectOperation operation) throws IOException {

        if (artifact == null) {
            log.warn("Artifact lookup failed for name: " + name);
            return Utils.createJsonError(notFoundMsg, axis2MC, Constants.BAD_REQUEST);
        }
        JSONObject info = new JSONObject();
        info.put(infoKey, name);
        return operation.perform(performedBy, auditLogType, artifactType, info,
                getConfig.apply(artifact), name, axis2MC);
    }

    /**
     * Handles the lookup → null-check → info-build → status-operation pattern used by
     * {@link ArtifactStatusManager}.  The {@code operation} lambda receives the resolved
     * artifact and a pre-built info object, and is responsible for the actual status change
     * and audit logging.
     *
     * @param artifact    looked-up artifact; {@code null} produces a 404 error response
     * @param notFoundMsg error message when {@code artifact} is {@code null}
     * @param infoKey     JSON key for the info object
     * @param name        artifact name
     * @param axis2MC     Axis2 message context used for error responses
     * @param operation   status-change operation receiving {@code (artifact, info)}
     */
    static <T> JSONObject handleStatusOperation(
            T artifact, String notFoundMsg, String infoKey, String name,
            org.apache.axis2.context.MessageContext axis2MC,
            BiFunction<T, JSONObject, JSONObject> operation) {

        if (artifact == null) {
            log.warn("Artifact not found for status operation: " + name);
            return Utils.createJsonError(notFoundMsg, axis2MC, Constants.NOT_FOUND);
        }
        JSONObject info = new JSONObject();
        info.put(infoKey, name);
        return operation.apply(artifact, info);
    }
}
