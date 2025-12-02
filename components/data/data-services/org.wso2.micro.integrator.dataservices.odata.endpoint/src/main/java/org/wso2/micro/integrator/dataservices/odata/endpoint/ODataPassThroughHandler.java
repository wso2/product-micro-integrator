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

package org.wso2.micro.integrator.dataservices.odata.endpoint;

import java.io.IOException;
import javax.ws.rs.core.MediaType;
import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.AbstractSynapseHandler;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.util.RelayUtils;
import org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingCollector;

import javax.xml.stream.XMLStreamException;

import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DATA_SERVICE_INDEX;

public class ODataPassThroughHandler extends AbstractSynapseHandler {
    private static final Log LOG = LogFactory.getLog(ODataPassThroughHandler.class);

    private static final String IS_ODATA_SERVICE = "IsODataService";
    private static final String TRANSPORT_IN_URL = "TransportInURL";

    @Override
    public boolean handleRequestInFlow(MessageContext messageContext) {
        try {
            org.apache.axis2.context.MessageContext axis2MessageContext =
                    ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            Object isODataService = axis2MessageContext.getProperty(IS_ODATA_SERVICE);
            // In this if block we are skipping proxy services, inbound related message contexts & api.
            if (axis2MessageContext.getProperty(TRANSPORT_IN_URL) != null && isODataService != null) {
                // Marking the message to skip the main sequence in Synapse Axis2 Environment.
                messageContext.setProperty(SynapseConstants.SKIP_MAIN_SEQUENCE, Boolean.TRUE);

                DataServicesTracingCollector.reportOdataEntryEvent(axis2MessageContext);

                RelayUtils.buildMessage(axis2MessageContext);
                ODataServletRequest request = new ODataServletRequest(axis2MessageContext);
                ODataServletResponse response = new ODataServletResponse(axis2MessageContext);
                synchronized (this) {
                    ODataEndpoint.process(request, response);
                    streamResponseBack(response, messageContext, axis2MessageContext);
                }
                DataServicesTracingCollector.closeOdataEntryEvent(axis2MessageContext,
                        axis2MessageContext.getEnvelope());
            }
            return true;
        } catch (Exception e) {
            DataServicesTracingCollector.closeFlowForcefully(
                    ((Axis2MessageContext) messageContext).getAxis2MessageContext(), DATA_SERVICE_INDEX, e);
            this.handleException("Error occurred in integrator handler.", e, messageContext);
            return true;
        }
    }

    /**
     * This method starts streaming the response from the server to the client.
     *
     * @param response       OData Servlet response.
     * @param messageContext Message context.
     * @throws XMLStreamException if unexpected processing errors occurred while building the message.
     * @throws IOException        if any interrupted I/O operations occurred while building the message.
     */
    private void streamResponseBack(ODataServletResponse response, MessageContext messageContext,
                                    org.apache.axis2.context.MessageContext axis2MessageContext) throws IOException {
        axis2MessageContext.setProperty(Constants.Configuration.MESSAGE_TYPE, MediaType.TEXT_PLAIN);
        axis2MessageContext.removeProperty(PassThroughConstants.NO_ENTITY_BODY);
        messageContext.setTo(null);
        messageContext.setResponse(true);
        ODataAxisEngine oDataAxisEngine = new ODataAxisEngine();
        oDataAxisEngine.stream(axis2MessageContext, response);
    }

    private void handleException(String msg, Exception e, MessageContext msgContext) {
        LOG.error(msg, e);
        if (msgContext.getServiceLog() != null) {
            msgContext.getServiceLog().error(msg, e);
        }
        throw new SynapseException(msg, e);
    }

    @Override
    public boolean handleRequestOutFlow(MessageContext messageContext) {
        return true;
    }

    @Override
    public boolean handleResponseInFlow(MessageContext messageContext) {
        return true;
    }

    @Override
    public boolean handleResponseOutFlow(MessageContext messageContext) {
        return true;
    }

}
