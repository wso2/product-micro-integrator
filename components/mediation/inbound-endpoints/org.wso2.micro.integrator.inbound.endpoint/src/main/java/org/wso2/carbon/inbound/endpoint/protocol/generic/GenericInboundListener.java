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
package org.wso2.carbon.inbound.endpoint.protocol.generic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundRequestProcessor;

import java.lang.reflect.Constructor;

public abstract class GenericInboundListener implements InboundRequestProcessor {

    private static final Log log = LogFactory.getLog(GenericInboundListener.class);
    public static final String PARAM_INBOUND_ENDPOINT_BEHAVIOR = "inbound.behavior";
    public static final String PARAM_INBOUND_ENDPOINT_BEHAVIOR_LISTENING = "listening";

    protected String injectingSequence;
    protected String onErrorSequence;
    protected String name;
    protected InboundProcessorParams params;

    public GenericInboundListener(InboundProcessorParams inboundParams) {
        this.injectingSequence = inboundParams.getInjectingSeq();
        this.onErrorSequence = inboundParams.getOnErrorSeq();
        this.name = inboundParams.getName();
        this.params = inboundParams;
    }

    /**
     * This is to get the GenericInboundListener instance for given params
     *
     * @param inboundParams
     * @return
     */
    public static synchronized GenericInboundListener getInstance(InboundProcessorParams inboundParams) {
        String classImpl = inboundParams.getClassImpl();
        String name = inboundParams.getName();

        if (null == classImpl) {
            String msg = "GenericEndpointManager class not found";
            log.error(msg);
            throw new SynapseException(msg);
        }

        GenericInboundListener instance = null;

        log.info("Inbound listener " + name + " for class " + classImpl + " starting ...");
        try {
            // Dynamically load GenericEndpointManager from given classpath
            Class c = Class.forName(classImpl);
            Constructor cons = c.getConstructor(InboundProcessorParams.class);
            instance = (GenericInboundListener) cons.newInstance(inboundParams);
        } catch (ClassNotFoundException e) {
            handleException(
                    "Class " + classImpl + " not found. Please check the required class is added to the classpath.", e);
        } catch (Exception e) {
            handleException("Unable to create the consumer", e);
        }

        return instance;
    }

    /**
     * States whether generic endpoint is a listening
     * Return true; if listening
     *
     * @param inboundParameters Inbound Parameters for endpoint
     * @return boolean
     */
    public static boolean isListeningInboundEndpoint(InboundProcessorParams inboundParameters) {
        return inboundParameters.getProperties().containsKey(GenericInboundListener.PARAM_INBOUND_ENDPOINT_BEHAVIOR)
                && GenericInboundListener.PARAM_INBOUND_ENDPOINT_BEHAVIOR_LISTENING
                .equals(inboundParameters.getProperties()
                                .getProperty(GenericInboundListener.PARAM_INBOUND_ENDPOINT_BEHAVIOR));
    }

    protected static void handleException(String msg, Exception e) {
        log.error(msg, e);
        throw new SynapseException(msg, e);
    }

    @Override
    public boolean activate() {

        return false;
    }

    @Override
    public boolean deactivate() {

        return false;
    }

    @Override
    public boolean isDeactivated() {

        return false;
    }
}
