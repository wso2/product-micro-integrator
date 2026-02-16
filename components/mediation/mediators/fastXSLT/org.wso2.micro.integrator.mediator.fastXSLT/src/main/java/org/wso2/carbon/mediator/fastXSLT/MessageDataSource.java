package org.wso2.carbon.mediator.fastXSLT;

import org.apache.axiom.util.blob.OverflowBlob;

import javax.activation.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class MessageDataSource implements DataSource {
    private OverflowBlob overflowBlob;

    public MessageDataSource(OverflowBlob overflowBlob) {
        this.overflowBlob = overflowBlob;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return overflowBlob.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return overflowBlob.getOutputStream();
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    public OverflowBlob getOverflowBlob() {
        return overflowBlob;
    }

    public void setOverflowBlob(OverflowBlob overflowBlob) {
        this.overflowBlob = overflowBlob;
    }
}
