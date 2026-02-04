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
package org.wso2.carbon.relay;

import org.apache.axiom.om.*;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPProcessingException;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.context.MessageContext;

import javax.activation.DataHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class BinaryRelayBuilder implements Builder{

	public static byte[] readAllFromInputSteam(InputStream in) throws IOException{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		int read = in.read(buf);

		while(read != -1){
			out.write(buf,0,read);
			read = in.read(buf);
		}
		return out.toByteArray();
	}


	public OMElement processDocument(InputStream inputStream,
			String contentType, MessageContext messageContext) throws AxisFault {
		try {
            //Fix for https://wso2.org/jira/browse/CARBON-7256
            messageContext.setProperty(Constants.Configuration.CONTENT_TYPE, contentType);

			//We will create a SOAP message, which holds the input message as a blob
			SOAPFactory factory;
			if (messageContext.isSOAP11()) {
				factory = OMAbstractFactory.getSOAP11Factory();
			} else {
				factory = OMAbstractFactory.getSOAP12Factory();
			}
			SOAPEnvelope env = factory.getDefaultEnvelope();
            if (inputStream != null) {
                OMNamespace ns = factory.createOMNamespace(
                        RelayConstants.BINARY_CONTENT_QNAME.getNamespaceURI(), "ns");
                OMElement omEle = factory.createOMElement(
                        RelayConstants.BINARY_CONTENT_QNAME.getLocalPart(), ns);

                StreamingOnRequestDataSource ds = new StreamingOnRequestDataSource(inputStream);
                DataHandler dataHandler = new DataHandler(ds);

                //create an OMText node with the above DataHandler and set optimized to true
                OMText textData = factory.createOMText(dataHandler, true);
                omEle.addChild(textData);
                env.getBody().addChild(omEle);
            }

			return env;
		} catch (SOAPProcessingException e) {
			throw AxisFault.makeFault(e);
		} catch (OMException e) {
			throw AxisFault.makeFault(e);
		}
	}

}
