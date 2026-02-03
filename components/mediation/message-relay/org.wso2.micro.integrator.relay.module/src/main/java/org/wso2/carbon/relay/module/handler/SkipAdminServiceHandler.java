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
package org.wso2.carbon.relay.module.handler;

import java.io.IOException;
import java.io.InputStream;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.description.HandlerDescription;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.handlers.AbstractHandler;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.relay.StreamingOnRequestDataSource;
import org.wso2.carbon.relay.RelayConstants;
import org.wso2.carbon.relay.MessageBuilder;
import org.wso2.carbon.relay.module.RelayConfiguration;


/**
 * This is a protection against the relay to make sure it does not interfere with other things. Basically, this hander
 * will undo the wrapping done by the relay for certain services/patterns.
 *
 * @author Srinath Perera (srinath@wso2.com)
 */
public class SkipAdminServiceHandler extends AbstractHandler {
    private static final Log log = LogFactory.getLog(TransportUtils.class);

    public static final String ADMIN_SERVICE_PARAM_NAME = "adminService";
    public static final String HIDDEN_SERVICE_PARAM_NAME = "hiddenService";

    public InvocationResponse invoke(MessageContext msgContext)
            throws AxisFault {
        try {
            Parameter relayParam = msgContext.getParameter(RelayConstants.RELAY_CONFIG_PARAM);

            if (relayParam == null) {
                handleException("Relay not initialized");
            }

            RelayConfiguration relConf = (RelayConfiguration) relayParam.getValue();

            if (relConf == null) {
                handleException("Relay not initialized");                
            }

            if (isFilteredOutService(msgContext.getAxisService(), relConf)) {
                SOAPEnvelope envelope = msgContext.getEnvelope();

                OMElement contentEle = envelope.getBody().getFirstChildWithName(
                        RelayConstants.BINARY_CONTENT_QNAME);

                if (contentEle != null) {
                    OMNode node = contentEle.getFirstOMChild();
                    if (node != null && (node instanceof OMText)) {
                        OMText binaryDataNode = (OMText) node;
                        DataHandler dh = (DataHandler) binaryDataNode.getDataHandler();

                        if (dh == null) {
                            if (log.isDebugEnabled()) {
                                log.warn("Message has the Binary content element. " +
                                        "But doesn't have binary content embedded within it");
                            }
                            return InvocationResponse.CONTINUE;
                        }

                        DataSource dataSource = dh.getDataSource();
                        //Ask the data source to stream, if it has not alredy cached the request
                        if (dataSource instanceof StreamingOnRequestDataSource) {
                            ((StreamingOnRequestDataSource) dataSource).setLastUse(true);
                        }
                        InputStream in = dh.getInputStream();
                        //extract the wrapped binary content

                        //Select the right builder, create the envelope and stick it in.
                        String contentType = (String) msgContext.getProperty(
                                Constants.Configuration.CONTENT_TYPE);

                        OMElement element = relConf.getMessageBuilder()
                                .getDocument(contentType, msgContext, in);

                        if (element != null) {
                            msgContext.setEnvelope(TransportUtils.createSOAPEnvelope(element));

                            msgContext.setProperty(MessageBuilder.RELAY_FORMATTERS_MAP,
                                    relConf.getMessageBuilder().getFormatters());
                        } else {
                            log.warn("Error building the message, skipping message building");
                        }
                        //now we have undone thing done  by Relay
                        if (log.isDebugEnabled()) {
                            log.debug("Undo wrapping done by Relay");
                        }
                    } else {
                        //if is not wrapped binary content, there is nothing to be done
                        if (log.isDebugEnabled()) {
                            log.debug("not wrapped binary content, there is nothing to be done");
                        }
                    }
                } else {
                    //there is no body content, we will let it go
                    if (log.isDebugEnabled()) {
                        log.debug("Body of the Soap Envelope is empty, nothing to unwrap");
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Not a admin service, nothing to be done");
                }
            }
            return InvocationResponse.CONTINUE;
        } catch (OMException e) {
            throw AxisFault.makeFault(e);
        } catch (IOException e) {
            throw AxisFault.makeFault(e);
        } catch (XMLStreamException e) {
            throw AxisFault.makeFault(e);
        }
    }

    @Override
    public void init(HandlerDescription handlerdesc) {

    }


    public boolean isFilteredOutService(AxisService service, RelayConfiguration relConf) {
        if (relConf.getServices().contains(service.getName())) {
            return true;
        }
        
        if("__SynapseService".equals(service.getName())){
            return false;
        }
        else if("__MultitenantDispatcherService".equals(service.getName())){
            return false;
        }

        AxisServiceGroup axisServiceGroup = (AxisServiceGroup) service.getParent();
        String adminParamValue =
                (String) axisServiceGroup.getParameterValue(ADMIN_SERVICE_PARAM_NAME);
        if (adminParamValue == null) {
            adminParamValue = (String) service.getParameterValue(ADMIN_SERVICE_PARAM_NAME);
        }

        String hiddenParamValue =
                (String) axisServiceGroup.getParameterValue(HIDDEN_SERVICE_PARAM_NAME);
        if (hiddenParamValue == null) {
            hiddenParamValue = (String) service.getParameterValue(HIDDEN_SERVICE_PARAM_NAME);
        }

        if (Boolean.parseBoolean(adminParamValue)) {
            return true;
        } else if (relConf.isIncludeHiddenServices() && Boolean.parseBoolean(hiddenParamValue)) {
            return true;
        } else {
            return false;
        }
    }

    private void handleException(String msg) throws AxisFault {
        log.error(msg);
        throw new AxisFault(msg);
    }
}
