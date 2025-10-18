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
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.libraries.LibClassLoader;
import org.apache.synapse.task.TaskStartupObserver;
import org.wso2.carbon.inbound.endpoint.common.InboundOneTimeTriggerEventBasedProcessor;
import org.wso2.carbon.inbound.endpoint.protocol.PollingConstants;
import org.wso2.carbon.inbound.endpoint.protocol.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class GenericEventBasedListener extends InboundOneTimeTriggerEventBasedProcessor implements TaskStartupObserver {

    private GenericEventBasedConsumer eventConsumer;
    private Properties properties;
    private String injectingSeq;
    private String onErrorSeq;
    private String classImpl;
    private boolean sequential;
    private boolean startInPausedMode;
    private static final Log log = LogFactory.getLog(GenericEventBasedListener.class);

    private static final String ENDPOINT_POSTFIX = "CLASS" + COMMON_ENDPOINT_POSTFIX;

    public GenericEventBasedListener(String name, String classImpl, Properties properties, String injectingSeq,
                                     String onErrorSeq, SynapseEnvironment synapseEnvironment, boolean coordination,
                                     boolean sequential) {
        this.name = name;
        this.properties = properties;
        this.injectingSeq = injectingSeq;
        this.onErrorSeq = onErrorSeq;
        this.synapseEnvironment = synapseEnvironment;
        this.classImpl = classImpl;
        this.coordination = coordination;
        this.sequential = sequential;
    }

    public GenericEventBasedListener(InboundProcessorParams params) {
        this.name = params.getName();
        this.properties = params.getProperties();
        this.coordination = true;
        if (properties.getProperty(PollingConstants.INBOUND_COORDINATION) != null) {
            this.coordination = Boolean.parseBoolean(properties.getProperty(PollingConstants.INBOUND_COORDINATION));
        }
        this.sequential = true;
        if (properties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL) != null) {
            this.sequential = Boolean
                    .parseBoolean(properties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL));
        }
        this.injectingSeq = params.getInjectingSeq();
        this.onErrorSeq = params.getOnErrorSeq();
        this.synapseEnvironment = params.getSynapseEnvironment();
        this.classImpl = params.getClassImpl();
        this.startInPausedMode = params.startInPausedMode();
    }

    public void init() {
        log.info("Inbound event based listener [" + name + "]" + " for class [" + classImpl + "] is initializing"
                + (this.startInPausedMode ? " but will remain in suspended mode..." : "..."));
        Map<String, ClassLoader> libClassLoaders = SynapseConfiguration.getLibraryClassLoaders();
        Class c = null;
        if (libClassLoaders != null) {
            for (Map.Entry<String, ClassLoader> entry : libClassLoaders.entrySet()) {
                try {
                    if (entry.getValue() instanceof LibClassLoader) {
                        c = entry.getValue().loadClass(classImpl);
                        break;
                    }
                } catch (ClassNotFoundException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Class " + classImpl + " not found in the classloader of the library " + entry.getKey());
                    }
                }
            }
        }
        if (c == null) {
            try {
                c = Class.forName(classImpl);
            } catch (ClassNotFoundException e) {
                handleException(
                        "Class " + classImpl + " not found. Please check the required class is added to the classpath.", e);
            }
        }
        try {
            Constructor cons = c.getConstructor(Properties.class, String.class, SynapseEnvironment.class, String.class,
                                                String.class, boolean.class, boolean.class);
            eventConsumer = (GenericEventBasedConsumer) cons
                    .newInstance(properties, name, synapseEnvironment, injectingSeq, onErrorSeq, coordination,
                                 sequential);
        } catch (NoSuchMethodException e) {
            handleException("Required constructor is not implemented.", e);
        } catch (InvocationTargetException e) {
            handleException("Unable to create the consumer", e);
        } catch (Exception e) {
            handleException("Unable to create the consumer", e);
        }
        start();
    }

    private void handleException(String msg, Exception ex) {
        log.error(msg, ex);
        throw new SynapseException(ex);
    }

    public void start() {
        try {
            GenericOneTimeTask task = new GenericOneTimeTask(eventConsumer);
            start(task, ENDPOINT_POSTFIX);
        } catch (Exception e) {
            log.error("Could not start Generic Event Based Processor. Error starting up scheduler. Error: " + e
                    .getLocalizedMessage());
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void update() {
        start();
    }

    public boolean activate() {
        if (Utils.checkMethodImplementation(eventConsumer.getClass(), "resume")) {
            return super.activate();
        } else {
            throw new UnsupportedOperationException("Deactivation is not supported for Inbound Endpoint '" + getName()
                    + "'. To enable this functionality, ensure that the 'destroy()' and 'resume()' methods are "
                    + "properly implemented. If using a WSO2-released inbound, please upgrade to the latest version.");
        }
    }

    @Override
    public boolean deactivate() {
        if (Utils.checkMethodImplementation(eventConsumer.getClass(), "destroy")
                && Utils.checkMethodImplementation(eventConsumer.getClass(), "resume")) {
            boolean isTaskDeactivated = super.deactivate();

            if (isTaskDeactivated) {
                eventConsumer.destroy();
                return true;
            }
        } else {
            throw new UnsupportedOperationException("Deactivation is not supported for Inbound Endpoint '" + getName()
                    + "'. To enable this functionality, ensure that the 'destroy()' and 'resume()' methods are "
                    + "properly implemented. If using a WSO2-released inbound, please upgrade to the latest version.");
        }
        return false;
    }

    @Override
    public void pause() {
        try {
            eventConsumer.pause();
        } catch (AbstractMethodError e) {
            if (log.isDebugEnabled()) {
                log.debug("Implement the 'pause()' method to enable graceful shutdown in your custom "
                        + "inbound endpoint: " + getName());
            }
        }
    }
}
