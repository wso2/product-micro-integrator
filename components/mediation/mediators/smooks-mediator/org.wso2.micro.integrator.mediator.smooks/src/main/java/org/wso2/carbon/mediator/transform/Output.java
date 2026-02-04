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
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axis2.AxisFault;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;
import org.milyn.payload.JavaResult;

public class Output {
    public static final String ACTION_REPLACE = "replace";
    public static final String ACTION_ADD_CHILD = "child";
    public static final String ACTION_ADD_SIBLING = "sibling";

    public final static String AXIOMPAYLOADNS = "http://ws.apache.org/commons/ns/payload";

    public final static QName TEXTELT = new QName(AXIOMPAYLOADNS, "text", "ax");

    private SmooksMediator.TYPES type = SmooksMediator.TYPES.XML;

    private SynapseXPath expression = null;

    private String property = null;

    private String action = null;
    
    public void process(ByteArrayOutputStream outputStream,
                        MessageContext synCtx, SynapseLog synLog, JavaResult result) throws AxisFault {
    	ByteArrayInputStream inputStream = null;
    	
    	if (outputStream != null && outputStream.size() > 0) {
    		inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    	}
    	if (inputStream != null && type == SmooksMediator.TYPES.XML) {
                try {
                	StAXOMBuilder builder = new StAXOMBuilder(inputStream);
                    
                    // create the transformed element
                    OMElement transformedElement = builder.getDocumentElement();
                    if (property != null) {
                        synCtx.setProperty(property, transformedElement);
                    } else if (expression != null) {
                        Object targetObj = expression.selectSingleNode(synCtx);

                        if (targetObj instanceof OMElement) {
                            OMElement targetElem = (OMElement) targetObj;
                            insertElement(transformedElement, targetElem, synLog);
                        } else if (targetObj instanceof OMText) {
                            OMText targetText = (OMText) targetObj;
                            Object targetParent = targetText.getParent();
                            if (targetParent instanceof OMElement) {
                                targetText.detach();
                                ((OMElement) targetParent).addChild(transformedElement);
                            }
                        } else {
                            handleException("Invalid Target object to be enrich.", synLog);
                        }
                    } else {
                    	setXMLPayload(synCtx, transformedElement);
                    }
                } catch (XMLStreamException e) {
                    handleException("Error creating the  parser form the result of smooks", synLog, e);
                } catch (JaxenException e) {
                    handleException("Error evaluating the Xpath expression: " + expression, synLog, e);
                }
            } else if (inputStream != null && type == SmooksMediator.TYPES.TEXT) {
                if (expression != null) {
                    Object targetObj = null;
                    try {
                        targetObj = expression.selectSingleNode(synCtx);
                    } catch (JaxenException e) {
                        handleException("Error evaluating the XPath: " + expression, synLog, e);
                    }

                    if (targetObj instanceof OMElement) {
                        ((OMElement) targetObj).setText(byteArrayIStoString(inputStream));
                    } else if (targetObj instanceof OMText) {
                        OMText targetText = (OMText) targetObj;
                        if (targetText.getParent() != null) {
                            Object parent = targetText.getParent();
                            if (parent instanceof OMElement) {
                                ((OMElement) parent).setText(byteArrayIStoString(inputStream));
                            }
                        }
                    } else {
                        handleException("Invalid Target object to be enrich.", synLog);
                    }
                } else if (property != null) {
                    synCtx.setProperty(property, byteArrayIStoString(inputStream));
                } else {
                    setTextPayload(synCtx, byteArrayIStoString(inputStream));
                }
            } else if (type == SmooksMediator.TYPES.JAVA) {
            	if (property != null) {
            		synCtx.setProperty(property, result);
            	}
            }
    	}

    public String byteArrayIStoString(ByteArrayInputStream is) {
        int size = is.available();
        char[] theChars = new char[size];
        byte[] bytes = new byte[size];

        is.read(bytes, 0, size);

        for (int i = 0; i < size;) {
            theChars[i] = (char) (bytes[i++] & 0xff);
        }

        return new String(theChars);
    }

    private void insertElement(OMNode source, OMElement e, SynapseLog synLog) {
        if (action.equals(ACTION_REPLACE)) {
            boolean isInserted = false;

            if (source instanceof OMElement) {
                e.insertSiblingAfter(source);
                isInserted = true;
            } else if (source instanceof OMText) {
                e.setText(((OMText) source).getText());
            } else {
                handleException("Invalid Source object to be inserted.", synLog);
            }

            if (isInserted) {
                e.detach();
            }
        } else if (action.equals(ACTION_ADD_CHILD)) {
            if (source instanceof OMElement) {
                e.addChild(source);
            }
        } else if (action.equals(ACTION_ADD_SIBLING)) {
            if (source instanceof OMElement) {
                e.insertSiblingAfter(source);
            }
        }
    }

    public SmooksMediator.TYPES getType() {
        return type;
    }

    public SynapseXPath getExpression() {
        return expression;
    }

    public String getProperty() {
        return property;
    }

    public String getAction() {
        return action;
    }

    public void setType(SmooksMediator.TYPES type) {
        this.type = type;
    }

    public void setExpression(SynapseXPath expression) {
        this.expression = expression;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public void setAction(String action) {
        this.action = action;
    }

    private void handleException(String message, SynapseLog log) {
        log.error(message);
        throw new SynapseException(message);
    }

    private void handleException(String message, SynapseLog log, Exception e) {
        log.error(message);
        throw new SynapseException(message, e);
    }

    public static void setXMLPayload(MessageContext synCtx, OMElement element) throws AxisFault {
    	SOAPEnvelope envelope = synCtx.getEnvelope();
    	SOAPEnvelope newElement = getNewSOAPEnvelope(envelope);
    	newElement.getBody().addChild(element);
    	synCtx.setEnvelope(newElement);
    }

    public static void setTextPayload(MessageContext synCtx, String text) throws AxisFault {
    	SOAPEnvelope envelope = synCtx.getEnvelope();
		OMFactory fac = envelope.getOMFactory();
		OMElement textElt = envelope.getOMFactory().createOMElement(TEXTELT);
		OMText textNode = fac.createOMText(text);
		textElt.addChild(textNode);
		setXMLPayload(synCtx, textElt);
	}
    
    /**
     * New SOAPEnvelope is created with namespace & header elements of envelope
     * @param envelope Envelope from MessageContext
     * @return New SOAPEnvelope 
     */
    public static SOAPEnvelope getNewSOAPEnvelope(SOAPEnvelope envelope) {
    	OMNamespace namespace = envelope.getNamespace();
  		String nsURI = namespace.getNamespaceURI();
    	SOAPFactory soapFactory = null;
    	if (nsURI.equals(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI)) {
            soapFactory = OMAbstractFactory.getSOAP11Factory();
        } else if (nsURI.equals(SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI)) {
            soapFactory = OMAbstractFactory.getSOAP12Factory();
        }
    	if (soapFactory != null) {
    		SOAPEnvelope new_envelope = soapFactory.createSOAPEnvelope();
        	SOAPHeader header = envelope.getHeader();
        	if (header!=null) {
        		soapFactory.createSOAPHeader(new_envelope);
        		SOAPHeader new_header = new_envelope.getHeader();
    	    	for (Iterator<?> itr =header.getChildElements(); itr.hasNext();) {
        			OMElement element = (OMElement) itr.next();
        			new_header.addChild(element);
        		}
        	}
        	soapFactory.createSOAPBody(new_envelope);
        	return new_envelope;
    	}
    	return null;
    }
}