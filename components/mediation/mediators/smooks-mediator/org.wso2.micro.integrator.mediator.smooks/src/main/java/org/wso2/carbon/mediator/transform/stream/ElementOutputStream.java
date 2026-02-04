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

import java.io.ByteArrayOutputStream;

/**
 * This class extends ByteArrayOutputStream.
 * The internal byte array can be resized by removing read part of the array.
 */
public class ElementOutputStream extends ByteArrayOutputStream {

    /**
     * The read data of the buffer is removed and  rest is assigned.
     * For example if i=100 & this.buf.length=120 then copy byte 101 to 120 and assign to this.buf
     * So the first 100 bytes are removed from this.buf
     * @param i Position up to which the buffer had been read
     */
    public void resizeBuffer(int i) {
		byte[] temp = this.buf;
		int copyLength = temp.length - i;
		this.buf = new byte[copyLength];
		if (copyLength > 0) {
			System.arraycopy(temp, i, this.buf, 0, copyLength);
		}
		this.count = copyLength;
	}

}
