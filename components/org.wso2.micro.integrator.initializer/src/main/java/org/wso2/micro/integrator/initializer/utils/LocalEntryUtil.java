/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.initializer.utils;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;

public class LocalEntryUtil {

	private static Log log = LogFactory.getLog(org.wso2.micro.integrator.initializer.utils.LocalEntryUtil.class);

	/**
	 * 
	 * Convert file to xml element
	 * 
	 * */
	public static OMElement getOMElement(File file) {
		FileInputStream is;
		OMElement document = null;

		try {
			is = FileUtils.openInputStream(file);
		} catch (IOException e) {
			log.error("Error while opening the file: " + file.getName() + " for reading", e);
			return null;
		}

		try {
			document = new StAXOMBuilder(is).getDocumentElement();
			document.build();
			is.close();
		} catch (XMLStreamException e) {
			log.error("Error while parsing the content of the file: " + file.getName(), e);
		} catch (IOException e) {
			log.warn("Error while closing the input stream from the file: " + file.getName(), e);
		} catch (Exception e) {
			log.error("Error while building the content of the file: " + file.getName(), e);			
		}

		return document;
	}

	public static OMElement nonCoalescingStringToOm(String xmlStr) throws XMLStreamException {
		StringReader strReader = new StringReader(xmlStr);
		XMLInputFactory xmlInFac = XMLInputFactory.newInstance();
		// Non-Coalescing parsing
		xmlInFac.setProperty("javax.xml.stream.isCoalescing", false);
        xmlInFac.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        xmlInFac.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
		XMLStreamReader parser = xmlInFac.createXMLStreamReader(strReader);
		StAXOMBuilder builder = new StAXOMBuilder(parser);

		return builder.getDocumentElement();
	}
}
    