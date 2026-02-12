/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.mediator.fastXSLT.config.xml;

import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.*;
import org.apache.synapse.mediators.MediatorProperty;
import org.wso2.carbon.mediator.fastXSLT.FastXSLTMediator;

import javax.xml.namespace.QName;
import java.util.List;

/**
 * Serializer for {@link org.apache.synapse.mediators.transform.XSLTMediator} instances.
 *
 * @see FastXSLTMediatorFactory
 */
public class FastXSLTMediatorSerializer extends AbstractMediatorSerializer {

    private static final QName ATTRIBUTE_Q
                = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "attribute");

    public OMElement serializeSpecificMediator(Mediator m) {

        if (!(m instanceof FastXSLTMediator)) {
            handleException("Unsupported mediator passed in for serialization : " + m.getType());
        }

        FastXSLTMediator mediator = (FastXSLTMediator) m;
        OMElement fastXSLT = fac.createOMElement("fastXSLT", synNS);

        if (mediator.getXsltKey() != null) {
            // Serialize Value using ValueSerializer
            ValueSerializer keySerializer =  new ValueSerializer();
            keySerializer.serializeValue(mediator.getXsltKey(), XMLConfigConstants.KEY, fastXSLT);
        } else {
            handleException("Invalid FastXSLT mediator. XSLT registry key is required");
        }
        saveTracingState(fastXSLT,mediator);
        serializeComments(fastXSLT, mediator.getCommentsList());
        return fastXSLT;
    }

    public String getMediatorClassName() {
        return FastXSLTMediator.class.getName();
    }
}

