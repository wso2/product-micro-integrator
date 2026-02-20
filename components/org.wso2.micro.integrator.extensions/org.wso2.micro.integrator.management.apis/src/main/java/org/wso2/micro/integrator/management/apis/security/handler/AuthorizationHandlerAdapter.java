/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.management.apis.security.handler;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.integrator.management.apis.Constants;
import org.wso2.micro.integrator.management.apis.ManagementApiUndefinedException;
import org.wso2.micro.integrator.management.apis.Utils;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Objects;

import static org.wso2.micro.integrator.management.apis.Constants.ICP_AUTHENTICATED_PROPERTY;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;

/**
 * This class provides an abstraction for all security handlers using authorization for management api.
 */
public abstract class AuthorizationHandlerAdapter extends SecurityHandlerAdapter {

    private static final Log LOG = LogFactory.getLog(AuthorizationHandlerAdapter.class);

    public AuthorizationHandlerAdapter(String context) throws CarbonException, XMLStreamException, IOException,
            ManagementApiUndefinedException {
        super(context);
    }

    @Override
    public Boolean handle(MessageContext messageContext) {
        if (Boolean.TRUE.equals(messageContext.getProperty(ICP_AUTHENTICATED_PROPERTY))) {
            // Already authenticated by ICP HMAC JWT — skip admin authorization check
            if (LOG.isDebugEnabled()) {
                LOG.debug("Request authenticated by ICP HMAC JWT, skipping admin authorization check");
            }
            LOG.debug("Skipping admin authorization check for ICP authenticated request");
            return true;
        }

        String userName = Utils.getStringPropertyFromMessageContext(messageContext, USERNAME_PROPERTY);

        String resourcePath = messageContext.getTo().getAddress();
        String resourceHttpMethod = String.valueOf(((Axis2MessageContext) messageContext).getAxis2MessageContext()
                .getProperty(Constants.HTTP_METHOD_PROPERTY));
        // PATCH type requests are being skipped for the /users resource to allow users to update their passwords.
        if (resourcePath.startsWith(context.concat(Constants.PREFIX_USERS)) &&
                StringUtils.isNotBlank(resourceHttpMethod) && resourceHttpMethod.equals(Constants.HTTP_METHOD_PATCH)) {
            return true;
        }

        if (Objects.nonNull(userName)) {
            if (authorize(userName)) {
                return true;
            } else {
                SecurityUtils.setStatusCode(messageContext, AuthConstants.SC_FORBIDDEN);
                return false;
            }
        } else {
            LOG.error("The user has not been authenticated. Consider adding an AuthenticationHandler prior to the "
                      + "AuthorizationHandler");
        }
        return false;
    }

    /**
     * Executes the authentication logic relevant to the handler.
     *
     * @param userName user name of the logged in user
     * @return Boolean authenticated
     */
    protected abstract Boolean authorize(String userName);

}
