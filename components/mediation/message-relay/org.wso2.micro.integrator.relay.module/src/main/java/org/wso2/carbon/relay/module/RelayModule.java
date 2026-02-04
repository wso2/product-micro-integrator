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
package org.wso2.carbon.relay.module;

import org.apache.axis2.modules.Module;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisModule;
import org.apache.axis2.description.AxisDescription;
import org.apache.axis2.description.PolicySubject;
import org.apache.axis2.AxisFault;
import org.apache.neethi.Assertion;
import org.apache.neethi.Policy;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.wso2.carbon.relay.module.policy.PolicyProcessor;
import org.wso2.carbon.relay.RelayConstants;


public class RelayModule implements Module {
    private Log log = LogFactory.getLog(RelayModule.class);

    public void init(ConfigurationContext configurationContext, AxisModule axisModule)
            throws AxisFault {
        PolicySubject policy = axisModule.getPolicySubject();
        RelayConfiguration configuration = null;
        if (policy != null) {
            try {
                configuration = PolicyProcessor.processPolicy(policy);
            } catch (AxisFault e) {
                handleException("Unable to initialize the relay module : " +
                        "Error in processing relay policy", e);
            }
        }

        // it is a must to have a gloabl configuration
        if (configuration == null) {
            if (log.isDebugEnabled()) {
                log.debug("Using the default initializer for the RelayConfiguration");
            }
            configuration = new RelayConfiguration();
        }

        configuration.init();

        configurationContext.getAxisConfiguration().addParameter(
                RelayConstants.RELAY_CONFIG_PARAM, configuration);
    }

    public void engageNotify(AxisDescription axisDescription) throws AxisFault {

    }

    public boolean canSupportAssertion(Assertion assertion) {
        return false;
    }

    public void applyPolicy(Policy policy, AxisDescription axisDescription) throws AxisFault {}

    public void shutdown(ConfigurationContext configurationContext) throws AxisFault {}

    private void handleException(String message, Throwable cause) throws AxisFault {
        if (log.isDebugEnabled()) {
            log.debug(message, cause);
        }
        throw new AxisFault(message, cause);
    }
}
