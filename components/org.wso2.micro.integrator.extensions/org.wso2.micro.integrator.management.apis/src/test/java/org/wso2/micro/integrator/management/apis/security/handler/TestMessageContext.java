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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.addressing.RelatesTo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ContinuationState;
import org.apache.synapse.FaultHandler;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.mediators.template.TemplateMediator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class TestMessageContext implements MessageContext {

    private Map properties = new HashMap();

    private Map<String, Object> localEntries = new HashMap<String, Object>();

    private Stack<FaultHandler> faultStack = new Stack<FaultHandler>();

    private SynapseConfiguration synCfg = null;

    private SynapseEnvironment synEnv;

    private SOAPEnvelope envelope = null;

    private EndpointReference to = null;

    private String soapAction = null;
    
    public SynapseConfiguration getConfiguration() {
        return synCfg;
    }

    public void setConfiguration(SynapseConfiguration cfg) {
        this.synCfg = cfg;
    }

    public SynapseEnvironment getEnvironment() {
        return synEnv;
    }

    public void setEnvironment(SynapseEnvironment se) {
        synEnv = se;
    }

    public Map<String, Object> getContextEntries() {
        return localEntries;
    }

    public void setContextEntries(Map<String, Object> entries) {
        this.localEntries = entries;
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public Object getEntry(String key) {
        Object ret = properties.get(key);
        if (ret != null) {
            return ret;
        } else if (getConfiguration() != null) {
            return getConfiguration().getEntry(key);
        } else {
            return null;
        }
    }

    @Override
    public Object getLocalEntry(String key) {
        Object ret = properties.get(key);
        if (ret != null) {
            return ret;
        } else if (getConfiguration() != null) {
            return getConfiguration().getLocalRegistryEntry(key);
        } else {
            return null;
        }
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public Set getPropertyKeySet() {
        return properties.keySet();
    }

    public Mediator getMainSequence() {
        Object o = localEntries.get(SynapseConstants.MAIN_SEQUENCE_KEY);
        if (o != null && o instanceof Mediator) {
            return (Mediator) o;
        } else {
            Mediator main = getConfiguration().getMainSequence();
            localEntries.put(SynapseConstants.MAIN_SEQUENCE_KEY, main);
            return main;
        }
    }

    public Mediator getFaultSequence() {
        Object o = localEntries.get(SynapseConstants.FAULT_SEQUENCE_KEY);
        if (o != null && o instanceof Mediator) {
            return (Mediator) o;
        } else {
            Mediator fault = getConfiguration().getFaultSequence();
            localEntries.put(SynapseConstants.FAULT_SEQUENCE_KEY, fault);
            return fault;
        }
    }

    public Mediator getSequence(String key) {
        Object o = localEntries.get(key);
        if (o != null && o instanceof Mediator) {
            return (Mediator) o;
        } else {
            Mediator m = getConfiguration().getSequence(key);
            if (m instanceof SequenceMediator) {
                SequenceMediator seqMediator = (SequenceMediator) m;
                synchronized (m) {
                    if (!seqMediator.isInitialized()) {
                        seqMediator.init(synEnv);
                    }
                }
            }
            localEntries.put(key, m);
            return m;
        }
    }

    public OMElement getFormat(String key) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Endpoint getEndpoint(String key) {
        Object o = localEntries.get(key);
        if (o != null && o instanceof Endpoint) {
            return (Endpoint) o;
        } else {
            Endpoint e = getConfiguration().getEndpoint(key);
            synchronized (e) {
                if (!e.isInitialized()) {
                    e.init(synEnv);
                }
            }
            localEntries.put(key, e);
            return e;
        }
    }

    //---------
    public SOAPEnvelope getEnvelope() {
        if (envelope == null)
            return OMAbstractFactory.getSOAP11Factory().getDefaultEnvelope();
        else
            return envelope;
    }

    public void setEnvelope(SOAPEnvelope envelope) throws AxisFault {
        this.envelope = envelope;
    }

    public EndpointReference getFaultTo() {
        return null;
    }

    public void setFaultTo(EndpointReference reference) {
    }

    public EndpointReference getFrom() {
        return null;
    }

    public void setFrom(EndpointReference reference) {
    }

    public String getMessageID() {
        return null;
    }

    public void setMessageID(String string) {
    }

    public RelatesTo getRelatesTo() {
        return null;
    }

    public void setRelatesTo(RelatesTo[] reference) {
    }

    public EndpointReference getReplyTo() {
        return null;
    }

    public void setReplyTo(EndpointReference reference) {
    }

    public EndpointReference getTo() {
        return to;
    }

    public void setTo(EndpointReference reference) {
        to = reference;
    }

    public void setWSAAction(String actionURI) {
    }

    public String getWSAAction() {
        return null;
    }

    public String getSoapAction() {
        return soapAction;
    }

    public void setSoapAction(String string) {
        soapAction = string;
    }

    public void setWSAMessageID(String messageID) {
        // TODO
    }

    public String getWSAMessageID() {
        return null;  // TODO
    }

    public boolean isDoingMTOM() {
        return false;
    }

    public boolean isDoingSWA() {
        return false;
    }

    public void setDoingMTOM(boolean b) {
    }

    public void setDoingSWA(boolean b) {
    }

    public boolean isDoingPOX() {
        return false;
    }

    public void setDoingPOX(boolean b) {
    }

    public boolean isDoingGET() {
        return false;
    }

    public void setDoingGET(boolean b) {
    }

    public boolean isSOAP11() {
        return envelope.getNamespace().getNamespaceURI().equals(
            SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
    }

    public void setResponse(boolean b) {
    }

    public boolean isResponse() {
        return false;
    }

    public void setFaultResponse(boolean b) {
    }

    public boolean isFaultResponse() {
        return false;
    }

    public int getTracingState() {
        return 0;  // TODO
    }

    public void setTracingState(int tracingState) {
        // TODO
    }

    public MessageContext getSynapseContext() {
        return null;
    }

    public void setSynapseContext(MessageContext env) {
    }

    public Stack<FaultHandler> getFaultStack() {
        return faultStack;
    }

    public void pushFaultHandler(FaultHandler fault) {
        faultStack.push(fault);
    }

    public void pushContinuationState(ContinuationState continuationState) {
    }

    public Stack<ContinuationState> getContinuationStateStack() {
        return null;
    }

    @Override
    public void restoreContinuationStateStack(Stack<ContinuationState> stack) {

    }

    public boolean isContinuationEnabled() {
        return false;
    }

    public void setContinuationEnabled(boolean contStateStackEnabled) {
    }

    public Log getServiceLog() {
        return LogFactory.getLog(TestMessageContext.class);
    }

    public Mediator getSequenceTemplate(String key) {
        Object o = localEntries.get(key);
        if (o != null && o instanceof Mediator) {
            return (Mediator) o;
        } else {
            Mediator m = getConfiguration().getSequence(key);
            if (m instanceof TemplateMediator) {
                TemplateMediator templateMediator = (TemplateMediator) m;
                synchronized (m) {
                    if (!templateMediator.isInitialized()) {
                        templateMediator.init(synEnv);
                    }
                }
            }
            localEntries.put(key, m);
            return m;
        }
    }

	public Mediator getDefaultConfiguration(String key) {
		Object o = localEntries.get(key);
		if (o != null && o instanceof Mediator) {
			return (Mediator) o;
		} else {
			Mediator m = getConfiguration().getDefaultConfiguration(key);
			localEntries.put(key, m);
			return m;
		}
	}

    public void addComponentToMessageFlow(String mediatorId){
    }

    public String getMessageString() {
        return null;
    }

    public void setMessageFlowTracingState(int tracingState){
    }

    @Override
    public Object getVariable(String s) {
        return null;
    }

    @Override
    public void setVariable(String s, Object o) {
        //
    }

    @Override
    public Set getVariableKeySet() {
        return null;
    }

    public int getMessageFlowTracingState(){
        return SynapseConstants.TRACING_OFF;
    }
}
