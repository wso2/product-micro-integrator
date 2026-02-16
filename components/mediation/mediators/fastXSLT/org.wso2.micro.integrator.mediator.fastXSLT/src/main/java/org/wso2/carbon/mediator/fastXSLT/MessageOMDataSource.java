package org.wso2.carbon.mediator.fastXSLT;

import org.apache.axiom.om.OMDataSource;
import org.apache.axiom.om.OMOutputFormat;
import org.apache.axiom.om.util.StAXUtils;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;

public class MessageOMDataSource implements OMDataSource {

    private InputStream inputStream = null;

    public MessageOMDataSource(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public void serialize(OutputStream outputStream, OMOutputFormat omOutputFormat) throws XMLStreamException {
        InputStream inStream = null;
        int val;
        inStream = inputStream;
        try {
            while ((val = inStream.read()) != -1)
                outputStream.write(val);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void serialize(Writer writer, OMOutputFormat omOutputFormat) throws XMLStreamException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void serialize(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public XMLStreamReader getReader() throws XMLStreamException {
        return StAXUtils.createXMLStreamReader(inputStream);
    }
}
