/*
 *  Copyright (c) 2005-2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.mediation.initializer;

import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.AddressEndpoint;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.SynapseConfigurationBuilder;
import org.apache.synapse.config.xml.MultiXMLConfigurationSerializer;
import org.apache.synapse.config.xml.XMLConfigurationBuilder;
import org.apache.synapse.config.xml.ProxyServiceFactory;
import org.apache.synapse.config.xml.SequenceMediatorFactory;
import org.apache.synapse.config.xml.endpoints.EndpointFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.AxisFault;
import org.wso2.micro.core.Constants;
import org.wso2.micro.integrator.initializer.services.SynapseConfigurationService;
import org.wso2.micro.integrator.initializer.services.SynapseConfigurationServiceImpl;
import org.wso2.micro.integrator.initializer.persistence.MediationPersistenceManager;
import junit.framework.TestCase;
import org.wso2.micro.integrator.initializer.ServiceBusConstants;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Properties;

public abstract class MediationPersistenceTest extends TestCase {

    protected String path = System.getProperty("basedir") + File.separator + "target" +
            File.separator + "synapse-config";

    protected SynapseConfigurationService synapseConfigSvc;

    public void setUp() {
        File tmpDir = new File(System.getProperty("basedir") + File.separator + "target" +
                File.separator + "tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        System.setProperty("java.io.tmpdir", tmpDir.getAbsolutePath());
        SynapseConfiguration synapseConfig = SynapseConfigurationBuilder.getDefaultConfiguration();
        synapseConfig.setAxisConfiguration(new AxisConfiguration());
        MultiXMLConfigurationSerializer serializer = new MultiXMLConfigurationSerializer(path);
        serializer.serialize(synapseConfig);
        synapseConfigSvc = new SynapseConfigurationServiceImpl(synapseConfig,
                                                               Constants.SUPER_TENANT_ID, null);
        /*getMediationPersistenceManager().init(null, path,
                synapseConfigSvc.getSynapseConfiguration(), 500L);*/
    }
    
    protected SynapseConfiguration getConfigurationFromSynapseXML() {
        try (FileInputStream fin = new FileInputStream(path + File.separator + "synapse.xml")) {
            return XMLConfigurationBuilder.getConfiguration(fin, new Properties());
        } catch (FileNotFoundException e) {
            fail("The synapse.xml file does not exist at the path: " + path);
        } catch (XMLStreamException e) {
            fail("Error parsing the synapse.xml file at the path: " + path);
        } catch (IOException e) {
            fail("IO error while reading the synapse.xml file at the path: " + path);
        }
        return null;
    }

    protected void tearDown() throws Exception {
        getMediationPersistenceManager().destroy();
    }

    protected void assertEquals(SequenceMediator expected, SequenceMediator actual) {

        if (expected != null && actual != null) {
            if (expected.getName() == null) {
                assertNull(actual.getName());
            } else {
                assertEquals(expected.getName(), actual.getName());
            }

            assertEquals(expected.getList().size(), actual.getList().size());
            for (int i = 0; i < expected.getList().size(); i++) {
                assertEquals(expected.getChild(i).getClass(), actual.getChild(i).getClass());
            }
            return;

        } else if (expected == null) {
            assertNull(actual);
            return;

        }
        fail("Sequences do not match");
    }

    protected void assertEquals(Endpoint expected, Endpoint actual) {
        if (expected != null && actual != null) {
            if (expected.getName() == null) {
                assertNull(actual.getName());
            } else {
                assertEquals(expected.getName(), actual.getName());
            }

            if (expected instanceof AddressEndpoint && actual instanceof AddressEndpoint) {
                AddressEndpoint original = (AddressEndpoint) expected;
                AddressEndpoint copy = (AddressEndpoint) actual;
                assertEquals(original.getDefinition().getAddress(), copy.getDefinition().getAddress());
                return;
            }

            // TODO: Implement support for comparing other types of endpoints
            // TODO: For the moment we can live with only address endpoints

        } else if (expected == null) {
            assertNull(actual);
            return;
        }
        fail("Endpoints do not match");
    }

    protected void assertEquals(ProxyService expected, ProxyService actual) {
        if (expected != null && actual != null) {
            assertEquals(expected.getName(), actual.getName());
            assertEquals(expected.getTargetInLineInSequence(), actual.getTargetInLineInSequence());
            assertEquals(expected.getTargetInLineOutSequence(), actual.getTargetInLineOutSequence());
            assertEquals(expected.getTargetInLineFaultSequence(), actual.getTargetInLineFaultSequence());
            assertEquals(expected.getTargetInLineEndpoint(), actual.getTargetInLineEndpoint());
            Map<String,Object> params = expected.getParameterMap();
            if (params != null) {
                assertEquals(params.size(), actual.getParameterMap().size());
                for (String key : params.keySet()) {
                    assertEquals(params.get(key), actual.getParameterMap().get(key));
                }
            } else {
                assertNull(actual.getParameterMap());
            }
            return;

        } else if (expected == null) {
            assertNull(actual);
            return;
        }

        fail("Proxy services do not match");
    }

    protected void hold() {
        try {
            Thread.sleep(2500);
        } catch (InterruptedException ignore) {

        }
    }

    protected OMElement parse(InputStream in) {
        try {
            StAXOMBuilder builder = new StAXOMBuilder(in);
            return builder.getDocumentElement();
        } catch (XMLStreamException e) {
            fail("Error while parsing the XML from the input stream");
            return null;
        }
    }

    protected Endpoint createEndpoint(InputStream in) {
        return EndpointFactory.getEndpointFromElement(parse(in), false, new Properties());
    }

    protected SequenceMediator createSequence(InputStream in) {
        SequenceMediatorFactory factory = new SequenceMediatorFactory();
        return (SequenceMediator) factory.createMediator(parse(in), new Properties());
    }

    protected ProxyService createProxy(InputStream in) {
        return ProxyServiceFactory.createProxy(parse(in), new Properties());
    }

    /**
     * Helper method to get the persistence manger
     * @return persistence manager for this configuration context
     */
    protected MediationPersistenceManager getMediationPersistenceManager() {
        Parameter p = synapseConfigSvc.getSynapseConfiguration().
                getAxisConfiguration().getParameter(
                ServiceBusConstants.PERSISTENCE_MANAGER);
        if (p != null) {
            return (MediationPersistenceManager) p.getValue();
        } else {
            MediationPersistenceManager persistenceManager =
                    new MediationPersistenceManager(path,
                            synapseConfigSvc.getSynapseConfiguration(), 100, "synapse-config");

            try {
                synapseConfigSvc.getSynapseConfiguration().getAxisConfiguration().addParameter(
                    new Parameter(ServiceBusConstants.PERSISTENCE_MANAGER, persistenceManager));
            } catch (AxisFault ignored) { }

            return persistenceManager;
        }
    }

}
