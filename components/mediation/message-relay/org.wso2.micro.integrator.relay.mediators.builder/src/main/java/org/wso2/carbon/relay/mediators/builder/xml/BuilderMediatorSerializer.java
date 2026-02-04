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
package org.wso2.carbon.relay.mediators.builder.xml;

import org.apache.synapse.config.xml.AbstractMediatorSerializer;
import org.apache.synapse.Mediator;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.transport.MessageFormatter;
import org.wso2.carbon.relay.mediators.builder.BuilderMediator;

public class BuilderMediatorSerializer extends AbstractMediatorSerializer {

    public OMElement serializeSpecificMediator(Mediator mediator) {
        BuilderMediator builderMediator = (BuilderMediator) mediator;

        OMElement builder = fac.createOMElement("builder", synNS);

        if (builderMediator.getSpecifiedBuilder() != null) {
            builder.addAttribute(fac.createOMAttribute("class", nullNS,
                    builderMediator.getSpecifiedBuilder().getClass().getName()));
            builder.addAttribute(fac.createOMAttribute("formatterClass", nullNS,
                    builderMediator.getSpecifiedFormatter().getClass().getName()));
        } else {

            for (String key : builderMediator.getMessageBuilders().keySet()) {
                OMElement msgBuilderEle = fac.createOMElement("messageBuilder", synNS);

                msgBuilderEle.addAttribute(fac.createOMAttribute("contentType", nullNS, key));

                String builderClass = builderMediator.getMessageBuilders().get(key).getClass().getName();
                msgBuilderEle.addAttribute(
                        fac.createOMAttribute("class", nullNS, builderClass));


                MessageFormatter formatter = builderMediator.getMessageFormatters().get(key);
                if (formatter != null) {
                    msgBuilderEle.addAttribute(fac.createOMAttribute("formatterClass", nullNS,
                            formatter.getClass().getName()));
                }

                builder.addChild(msgBuilderEle);
            }

        }
        serializeComments(builder, builderMediator.getCommentsList());
        return builder;
    }

    public String getMediatorClassName() {
        return BuilderMediator.class.getName();
    }
}
