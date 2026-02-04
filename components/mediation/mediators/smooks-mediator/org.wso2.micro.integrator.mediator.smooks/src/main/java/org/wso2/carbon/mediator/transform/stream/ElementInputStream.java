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
import java.io.InputStream;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.transform.stream.IOElementPipe;

/**
 * This class extends from InputStream.
 * The data is retrieved from the IOElementPipe when reading from this class.
 */
public class ElementInputStream extends InputStream {

	/** This pipe is used to get data to read*/
	private IOElementPipe pipe;

	private static final Log log = LogFactory.getLog(ElementInputStream.class);

	/**
	 * Constructor which creates ElementInputStream object with IOElementPipe object.
	 * @param pipe
	 */
	public ElementInputStream(IOElementPipe pipe) {
		this.pipe = pipe;
	}

	/**
	 * Read data to byte array by getting data from pipe.
	 */
	public synchronized int read(byte[] b, int off, int len) {
		if (b == null) {
			throw new NullPointerException("Null byte array passed to read data");
		} else if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException("IndexOutOfBoundException when reading data");
		}

		if (len <= 0) {
			return 0;
		}
		try {
			// Get data from the pipe
			byte[] btemp = this.pipe.getData(len, off);
			if (len > btemp.length) {
				len = btemp.length;
			}
			if (btemp.length == 0) {
				this.pipe.closeConnections();
				return -1;
			}
			System.arraycopy(btemp, 0, b, off, len);
		} catch (XMLStreamException e) {
			String errMessage = "Error in writting xml events";
			log.error(errMessage);
		} catch (IOException e) {
			String errMessage = "Error in closing outputstream";
			log.error(errMessage);
		}
		return len;
	}

	@Override
	public int read() throws IOException {
		byte[] buf = new byte[1];
		int count = read(buf, 0, 1);
		if (count > 0) {
			return buf[0];
		} else {
			return -1;
		}
   }

}
