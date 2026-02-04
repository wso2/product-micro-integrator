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

package org.wso2.carbon.mediator.transform;

import java.io.ByteArrayInputStream;
import java.util.List;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.util.ElementHelper;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.util.PayloadHelper;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;
import org.wso2.carbon.mediator.transform.stream.ElementInputStream;
import org.wso2.carbon.mediator.transform.stream.IOElementPipe;


public class Input {
    private SmooksMediator.TYPES type = SmooksMediator.TYPES.XML;

    private SynapseXPath expression = null;

    public StreamSource process(MessageContext synCtx, SynapseLog synLog) {
    	if (expression != null) {
            try {
            	List<?> selectedNodeList = expression.selectNodes(synCtx);
                if (selectedNodeList != null && selectedNodeList.size() != 0) {
                    OMNode node = (OMNode) selectedNodeList.get(0);
                    if (node instanceof OMElement) {
                    	IOElementPipe pipe = new IOElementPipe((OMElement) node);
        				ElementInputStream inputStream = new ElementInputStream(pipe);
        				return new StreamSource(inputStream);
                    } else if (node instanceof OMText) {
                    	return new StreamSource(new ByteArrayInputStream(((OMText) node).getText().getBytes()));
                    }
                } else {
                    synLog.error("Specified node by xpath cannot be found.");
                }
            } catch (JaxenException e) {
                handleException("Error evaluating the Expression", synLog);
            } catch (XMLStreamException e) {
            	handleException("Error serializing the input", synLog);
			} catch (FactoryConfigurationError e) {
				handleException("Failed to load XMLInputFactory instance", synLog);
			}
        } else {
            if (type == SmooksMediator.TYPES.TEXT) {
                OMElement element = PayloadHelper.getXMLPayload(synCtx.getEnvelope());
                if (element != null) {
                    byte[] bytes = element.getText().getBytes();
                    return new StreamSource(new ByteArrayInputStream(bytes));
                }
            } else if (type == SmooksMediator.TYPES.XML) {
            	IOElementPipe pipe = null;
				try {
					pipe = new IOElementPipe(PayloadHelper.getXMLPayload(synCtx.getEnvelope()));
				} catch (XMLStreamException e) {
					handleException("Error initializing IOElementPipe object", synLog);
				} catch (FactoryConfigurationError e) {
					handleException("Failed to load XMLInputFactory instance", synLog);
				}
				ElementInputStream inputStream = new ElementInputStream(pipe);
				return new StreamSource(inputStream);
            }
        }
        return null;
    }

    public SynapseXPath getExpression() {
        return expression;
    }

    public SmooksMediator.TYPES getType() {
        return type;
    }

    public void setType(SmooksMediator.TYPES type) {
        this.type = type;
    }

    public void setExpression(SynapseXPath expression) {
        this.expression = expression;
    }

    private void handleException(String message, SynapseLog log) {
        log.error(message);
        throw new SynapseException(message);
    }
}