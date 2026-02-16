/*
 * Copyright (c)2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.esb.samples.test.util;

import org.apache.axiom.om.OMElement;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.ESBTestCaseUtils;

import java.io.File;
import java.util.regex.Matcher;

/**
 * The ESBSampleIntegrationTest class provides a way to update synapse with provided a configuration file or a sample.
 */
public class ESBSampleIntegrationTest extends ESBIntegrationTest {

    public static ESBTestCaseUtils esbUtils = new ESBTestCaseUtils();

    /**
     * Load a synapse configuration given the relative path of the file that contains the synapse configuration.
     *
     * @param relativeFilePath the path of the file relative to the resources directory
     * @throws Exception if an error occurs while updating the synapse configuration.
     */
    protected void loadESBConfigurationFromClasspath(String relativeFilePath) throws Exception {
        relativeFilePath = relativeFilePath.replaceAll("[\\\\/]", Matcher.quoteReplacement(File.separator));

        OMElement synapseConfig = esbUtils.loadResource(relativeFilePath);
        updateESBConfiguration(synapseConfig);

    }

    /**
     * Load a sample synapse configuration when the sample number is provided.
     *
     * @param sampleNo the sample configuration to be loaded
     * @throws Exception if an error occurs while updating the sample synapse configuration
     */
    protected void loadSampleESBConfiguration(int sampleNo) throws Exception {
        OMElement synapseSample = esbUtils.loadESBSampleConfiguration(sampleNo);
        updateESBConfiguration(synapseSample);
    }

}
