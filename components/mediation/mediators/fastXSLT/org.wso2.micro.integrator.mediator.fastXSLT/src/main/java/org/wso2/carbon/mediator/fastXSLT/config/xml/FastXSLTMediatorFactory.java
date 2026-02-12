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

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.*;
import org.apache.synapse.mediators.Value;
import org.jaxen.JaxenException;
import org.wso2.carbon.mediator.fastXSLT.FastXSLTMediator;

import javax.xml.namespace.QName;
import java.util.Map;
import java.util.Properties;

/**
 * Factory for {@link org.apache.synapse.mediators.transform.XSLTMediator} instances.
 * <p>
 * Configuration syntax:
 * <pre>
 * &lt;fastXSLT key="property-key" [source="xpath"] [target="string"]&gt;
 *   &lt;property name="string" (value="literal" | expression="xpath")/&gt;*
 *   &lt;feature name="string" value="true| false" /&gt;*
 *   &lt;attribute name="string" value="string" /&gt;*
 *   &lt;resource location="..." key="..."/&gt;*
 * &lt;/transform&gt;
 * </pre>
 */
public class FastXSLTMediatorFactory extends AbstractMediatorFactory {

    private static final QName TAG_NAME
                = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "fastXSLT");

    public QName getTagQName() {
        return TAG_NAME;
    }

    public Mediator createSpecificMediator(OMElement elem, Properties properties) {

        FastXSLTMediator transformMediator = new FastXSLTMediator();

        OMAttribute attXslt   = elem.getAttribute(ATT_KEY);

        if (attXslt != null) {
            // ValueFactory for creating dynamic or static Value
            ValueFactory keyFac = new ValueFactory();
            // create dynamic or static key based on OMElement
            Value generatedKey = keyFac.createValue(XMLConfigConstants.KEY, elem);

            // set generated key as the Value
            transformMediator.setXsltKey(generatedKey);
        } else {
            handleException("The '" + XMLConfigConstants.KEY + "' " +
                    "attribute is required for the FastXSLT mediator");
        }


        // after successfully creating the mediator
        // set its common attributes such as tracing etc
        processAuditStatus(transformMediator, elem);
        addAllCommentChildrenToList(elem, transformMediator.getCommentsList());
        return transformMediator;
    }
}
