/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.inbound.endpoint.protocol.jms.jakarta;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.MessageEOFException;
import java.io.InputStream;

/**
 * Input stream that reads data from a JMS {@link BytesMessage}.
 * Note that since the current position in the message is managed by
 * the underlying {@link BytesMessage} object, it is not possible to
 * use several instances of this class operating on a single
 * {@link BytesMessage} at the same time.
 */
public class BytesMessageInputStream extends InputStream {
    private final BytesMessage message;

    public BytesMessageInputStream(BytesMessage message) {
        this.message = message;
    }

    @Override
    public int read() throws JMSExceptionWrapper {
        try {
            return message.readByte() & 0xFF;
        } catch (MessageEOFException ex) {
            return -1;
        } catch (JMSException ex) {
            throw new JMSExceptionWrapper(ex);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws JMSExceptionWrapper {
        if (off == 0) {
            try {
                return message.readBytes(b, len);
            } catch (JMSException ex) {
                throw new JMSExceptionWrapper(ex);
            }
        } else {
            byte[] b2 = new byte[len];
            int c = read(b2);
            if (c > 0) {
                System.arraycopy(b2, 0, b, off, c);
            }
            return c;
        }
    }

    @Override
    public int read(byte[] b) throws JMSExceptionWrapper {
        try {
            return message.readBytes(b);
        } catch (JMSException ex) {
            throw new JMSExceptionWrapper(ex);
        }
    }
}
