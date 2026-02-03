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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;

import org.apache.axiom.attachments.ByteArrayDataSource;

/**
 * This class has a special flag, which says this is it's last use and not to cache data
 * if it did not have done so already.
 *
 * @author Srinath Perera (srinath@wso2.com)
 */

public class StreamingOnRequestDataSource implements DataSource {
    private InputStream in;

    public StreamingOnRequestDataSource(InputStream in) {
        super();
        this.in = in;
    }

    private boolean lastUse = false;

    private ByteArrayDataSource cachedData = null;

    public String getContentType() {
        return "application/octet-stream";
    }

    public InputStream getInputStream() throws IOException {
        if (cachedData != null) {
            return cachedData.getInputStream();
        } else {
            if (lastUse && in != null) {
                InputStream returnStram = in;
                this.in = null;
                return returnStram;
            } else if (in != null) {
                byte[] data = BinaryRelayBuilder.readAllFromInputSteam(in);
                cachedData = new ByteArrayDataSource(data);
                return cachedData.getInputStream();
            } else {
		cachedData = new ByteArrayDataSource(new byte[]{});
                return cachedData.getInputStream();
            }
        }
    }

    public String getName() {
        return "StreamingOnRequestDataSource";
    }

    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * This flag says that this is the last use of the stream, hence do not
     * need to cache it if it has not
     * already cached.
     *
     * @param lastUse
     */
    public void setLastUse(boolean lastUse) {
        this.lastUse = lastUse;
	}

    /**
     * Indicates whether or not the InputStream of the message has been consumed
     * @return true if the InputStream has already been consumed, false otherwise.
     */
    public boolean isConsumed() {
        return  lastUse;
    }
}
