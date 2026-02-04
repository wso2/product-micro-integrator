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
package org.wso2.carbon.mediator.transform.stream;

import java.io.IOException;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import org.apache.axiom.om.OMElement;

/**
 * This class populates XML events from the OMElement and write to an OutputStream.
 * The written events are returned as a byte array when requested.
 */
public class IOElementPipe {

    /** XML events are written to this OutputStrem. */
	private ElementOutputStream outputStream;
    /** XMLEventReader created for OMElement. */
    private XMLEventReader eventReader;
    /** Written event count for a one request. */
    private final int MAX_EVENT_COUNT = 10;
    /** Writer used to write XML events. */
    private XMLEventWriter xmlWriter;
       
    /**
     * Constructor which create IOElementPipe object with an OMElement object.
     * @param element OMElement object
     * @throws XMLStreamException
     * @throws FactoryConfigurationError
     */
    public IOElementPipe(OMElement element) throws XMLStreamException, FactoryConfigurationError {
    	XMLStreamReader xmlReader = element.getXMLStreamReaderWithoutCaching();
    	this.eventReader = XMLInputFactory.newInstance().createXMLEventReader(xmlReader);
		this.outputStream = new ElementOutputStream();
		this.xmlWriter = XMLOutputFactory.newInstance().createXMLEventWriter(this.outputStream);
	}

    /**
     * Copy data from outputStream to a byte array.
     * @param count Number of bytes requested to read
     * @param off Stating point of byte array to copy data
     * @return byte array containing written events
     * @throws XMLStreamException
     * @throws FactoryConfigurationError
     */
    public final byte[] getData(int count, final int off) throws XMLStreamException {
		populateEvents();
		byte[] xmlEventsBuffer = this.outputStream.toByteArray();
		if (xmlEventsBuffer.length < count) {
			count = xmlEventsBuffer.length;
		}
		byte[] copiedEventsBuffer = new byte[count];
		System.arraycopy(xmlEventsBuffer, 0, copiedEventsBuffer, off, count);
		//InputStream read count length from outputStream buffer
		//resizeBuffer is called to remove count length from outputStream buffer
		this.outputStream.resizeBuffer(count);
		return copiedEventsBuffer;
	}

	/**
	 * Read MAX_EVENT_COUNT events from eventReader and writes to outputStream.
	 * @throws XMLStreamException
	 * @throws FactoryConfigurationError
	 */
	private void populateEvents() throws XMLStreamException {
		int count = 0;
		XMLEvent event;
		while (this.eventReader.hasNext() && count < MAX_EVENT_COUNT) {
			event = (XMLEvent) this.eventReader.next();
			this.xmlWriter.add(event);
			count++;
		}
		this.xmlWriter.flush();
	}
	
	/**
	 * Close the opened connections
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	public void closeConnections() throws XMLStreamException, IOException {
		this.eventReader.close();
		this.outputStream.close();
		this.xmlWriter.close();
	}

}
