/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;
import org.wso2.micro.integrator.management.apis.security.handler.SecurityUtils;
import org.wso2.micro.integrator.mediation.security.vault.external.ExternalVaultException;
import org.wso2.micro.integrator.mediation.security.vault.external.hashicorp.HashiCorpVaultLookupHandlerImpl;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.management.apis.Constants.NOT_FOUND;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;

public class ExternalVaultResource extends APIResource {

    private static Log LOG = LogFactory.getLog(ExternalVaultResource.class);

    // HTTP method types supported by the resource
    private Set<String> methods;

    private static final String SECRET_ID = "secretId";

    ExternalVaultResource(String urlTemplate) {
        super(urlTemplate);
        methods = new HashSet<>();
        methods.add(Constants.HTTP_POST);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    public boolean invoke(MessageContext messageContext) {
        buildMessage(messageContext);
        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        String pathParam = Utils.getPathParameter(messageContext, "vault");

        if ("hashicorp".equalsIgnoreCase(pathParam)) {
            if (Utils.isDoingPOST(axis2MessageContext)) {
                String userName = (String) messageContext.getProperty(USERNAME_PROPERTY);
                try {
                    if (SecurityUtils.canUserEdit(userName)) {
                        handleHashiCorpPost(axis2MessageContext);
                    } else {
                        Utils.sendForbiddenFaultResponse(axis2MessageContext);
                    }
                } catch (UserStoreException e) {
                    LOG.error("Error occurred while retrieving the user data", e);
                    Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("Error occurred while retrieving the user data"));
                }
            } else {
                JSONObject response = Utils.createJsonError("No such method for management/external-vault/"
                        + pathParam, axis2MessageContext, NOT_FOUND);
                Utils.setJsonPayLoad(axis2MessageContext, response);
            }
        } else {
            JSONObject response = Utils.createJsonError("No such resource as management/external-vault/"
                            + pathParam, axis2MessageContext, BAD_REQUEST);
            Utils.setJsonPayLoad(axis2MessageContext, response);
        }

        return true;
    }

    private void handleHashiCorpPost(org.apache.axis2.context.MessageContext axisMsgCtx) {
        try {
            JsonObject payload = Utils.getJsonPayload(axisMsgCtx);
            JSONObject jsonResponse = new JSONObject();
            if (payload.has(SECRET_ID)) {
                String secretId = payload.get(SECRET_ID).getAsString();
                HashiCorpVaultLookupHandlerImpl instance = HashiCorpVaultLookupHandlerImpl.getDefaultSecurityService();
                instance.setSecretId(secretId);

                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE,
                        "SecretId value is updated in HashiCorp vault runtime configurations. To persist the " +
                                "new SecretId in the next server startup, please update the deployment.toml file");
            } else {
                jsonResponse = Utils.createJsonError("Unsupported operation",
                        axisMsgCtx, BAD_REQUEST);
            }
            Utils.setJsonPayLoad(axisMsgCtx, jsonResponse);
        } catch (IOException e) {
            String message = "Error when parsing JSON payload";
            LOG.error(message, e);
            Utils.setJsonPayLoad(axisMsgCtx, Utils.createJsonErrorObject(message));
        } catch (ExternalVaultException e) {
            String message = "Error when getting initiated HashiCorp instance";
            LOG.error(message, e);
            Utils.setJsonPayLoad(axisMsgCtx, Utils.createJsonErrorObject(message));
        }
    }
}
