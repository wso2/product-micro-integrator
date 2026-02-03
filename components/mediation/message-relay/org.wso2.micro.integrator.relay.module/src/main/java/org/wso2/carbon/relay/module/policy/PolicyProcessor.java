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
package org.wso2.carbon.relay.module.policy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axis2.description.PolicySubject;
import org.apache.axis2.AxisFault;
import org.apache.neethi.Policy;
import org.apache.neethi.PolicyEngine;
import org.apache.neethi.All;
import org.apache.neethi.builders.xml.XmlPrimtiveAssertion;
import org.apache.axiom.om.OMElement;
import org.wso2.carbon.relay.module.RelayConfiguration;
import org.wso2.carbon.relay.RelayConstants;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.List;
import java.util.Iterator;


public class PolicyProcessor {

    private static Log log = LogFactory.getLog(PolicyProcessor.class);

    public static RelayConfiguration processPolicy(PolicySubject policySubject) throws AxisFault {

        if (policySubject.getAttachedPolicyComponents().size() != 0) {
            RelayConfiguration relayConfig = new RelayConfiguration();

            Collection topLevelAssertionList = policySubject.getAttachedPolicyComponents();

            handlePolicyComponents(relayConfig, topLevelAssertionList);
            return relayConfig;
        } else {
            return null;
        }
    }

    private static boolean handlePolicyComponents(RelayConfiguration relayConfiguration,
                                               Collection topLevelAssertionList) throws AxisFault {
        for (Object topLevelAssertionObject : topLevelAssertionList) {
            if (topLevelAssertionObject instanceof Policy) {
                Policy policy = (Policy) topLevelAssertionObject;

                Collection policyComponents = policy.getPolicyComponents();

                handlePolicyComponents(relayConfiguration, policyComponents);
            } else if (topLevelAssertionObject instanceof XmlPrimtiveAssertion) {

                XmlPrimtiveAssertion tlxa = (XmlPrimtiveAssertion) topLevelAssertionObject;

                QName qName = tlxa.getName();

                // validating the relay assertion
                if (!qName.equals(RelayConstants.RELAY_ASSERSION_QNAME)) {
                    return false;
                }

                Policy rpc = PolicyEngine.getPolicy(tlxa.getValue());

                for (Object configAssertion : rpc.getPolicyComponents()) {
                    // Validating the relay policy
                    if (!(configAssertion instanceof Policy)) {
                        return false;
                    }

                    Policy cachingPolicy = (Policy) configAssertion;
                    List childAssertionsList = cachingPolicy.getPolicyComponents();

                    for (Object configData : childAssertionsList) {
                        if (!(configData instanceof All)) {
                            handleException("Unexpected relay " +
                                    "policy, \"wsp:All\" expected");
                        }

                        All all = (All) configData;
                        List configDataList = all.getPolicyComponents();
                        for (Object configDtaObject : configDataList) {
                            if (!(configDtaObject instanceof XmlPrimtiveAssertion)) {
                                // invalid relay policy
                                handleException("Unexpected relay policy " +
                                        "assertion for the relay module");

                            }
                            XmlPrimtiveAssertion assertion
                                    = (XmlPrimtiveAssertion) configDtaObject;

                            if (assertion.getName().equals(
                                    RelayConstants.INCLUDE_HIDDEN_SERVICES_QNAME)) {
                                String value = assertion.getValue().getText();
                                relayConfiguration.setIncludeHiddenServices(
                                        Boolean.parseBoolean(value));
                            }

                            if (assertion.getName().equals(
                                    RelayConstants.BUILDERS_QNAME) &&
                                    assertion.getValue() != null) {
                                processBuilders(assertion.getValue(), relayConfiguration);
                            }

                            if (assertion.getName().equals(
                                    RelayConstants.SERVICES_QNAME) &&
                                    assertion.getValue() != null) {
                                processServices(assertion.getValue(), relayConfiguration);
                            }
                        }
                    }
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean processBuilders(OMElement builders, RelayConfiguration configuration) {
        Iterator itr = builders.getChildrenWithName(RelayConstants.MESSAGE_BUILDER_QNAME);

        while (itr.hasNext()) {
            OMElement e = (OMElement) itr.next();

            String contentType = e.getAttributeValue(RelayConstants.CONTENT_TYPE_QNAME);
            if (contentType == null) {
                return false;
            }

            String className = e.getAttributeValue(RelayConstants.CLASS_NAME_QNAME);
            if (className == null) {
                return false;
            }

            configuration.addBuilder(contentType, className);

            String formatterClass = e.getAttributeValue(RelayConstants.FORMATTER_CLASS_NAME_QNAME);
            if (formatterClass != null) {
                configuration.addFormatter(contentType, formatterClass);
            }
        }
        return true;
    }

    private static boolean processServices(OMElement services, RelayConfiguration configuration) {
        Iterator itr = services.getChildrenWithName(RelayConstants.SERVICE_QNAME);

        while (itr.hasNext()) {
            OMElement e = (OMElement) itr.next();

            configuration.addService(e.getText());
        }

        return true;
    }
   
    public static void handleException(String msg) throws AxisFault {
        log.error(msg);
        throw new AxisFault(msg);
    }
}



