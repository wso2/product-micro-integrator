/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.util.SecurityManager;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class SecureDocumentBuilderFactory {

    private static Log log =
            LogFactory.getLog(org.wso2.micro.integrator.initializer.utils.SecureDocumentBuilderFactory.class);
    private static final int ENTITY_EXPANSION_LIMIT = 0;

    public static DocumentBuilderFactory newDocumentBuilderFactory() {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        try {
            factory.setFeature(Constants.SAX_FEATURE_PREFIX +
                    Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
        } catch (ParserConfigurationException e) {
            log.error("Failed to load XML Processor Feature " +
                    Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE);
        }
        try {
            factory.setFeature(Constants.SAX_FEATURE_PREFIX +
                    Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
        } catch (ParserConfigurationException e) {
            log.error("Failed to load XML Processor Feature " +
                    Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE);
        }
        try {
            factory.setFeature(Constants.XERCES_FEATURE_PREFIX +
                    Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
        } catch (ParserConfigurationException e) {
            log.error("Failed to load XML Processor Feature " +
                    Constants.LOAD_EXTERNAL_DTD_FEATURE);
        }

        SecurityManager securityManager = new SecurityManager();
        securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
        factory.setAttribute(Constants.XERCES_PROPERTY_PREFIX +
                Constants.SECURITY_MANAGER_PROPERTY, securityManager);

        return factory;
    }
}
