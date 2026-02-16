/*
 * Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.esb.integration.common.utils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.aspects.flow.statistics.publishing.PublishingPayload;
import org.wso2.esb.integration.common.utils.common.TestConfigurationProvider;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.xml.bind.DatatypeConverter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class ESBTestCaseUtils {

    private static final String PROXY = "proxy";
    private static final String LOCAL_ENTRY = "localEntry";
    private static final String ENDPOINT = "endpoint";
    private static final String SEQUENCE = "sequence";
    private static final String MESSAGE_STORE = "messageStore";
    private static final String MESSAGE_PROCESSOR = "messageProcessor";
    private static final String TEMPLATE = "template";
    private static final String API = "api";
    private static final String PRIORITY_EXECUTOR = "priorityExecutor";
    private static final String KEY = "key";
    private static final String NAME = "name";
    private static final String TASK = "task";
    private static final String INBOUND_ENDPOINT = "inboundEndpoint";
    private static int SERVICE_DEPLOYMENT_DELAY = TestConfigurationProvider.getServiceDeploymentDelay();
    protected Log log = LogFactory.getLog(getClass());

    /**
     * Copy the given source file to the given destination
     *
     * @param sourceUri source file location
     * @param destUri   destination file location
     * @throws IOException
     */
    public static void copyFile(String sourceUri, String destUri) throws IOException {
        File sourceFile = new File(sourceUri);
        File destFile = new File(destUri);

        if (destFile.exists()) {
            destFile.delete();
        }
        destFile.createNewFile();
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;

        try {
            fileInputStream = new FileInputStream(sourceFile);
            fileOutputStream = new FileOutputStream(destFile);

            FileChannel source = fileInputStream.getChannel();
            FileChannel destination = fileOutputStream.getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            IOUtils.closeQuietly(fileInputStream);
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    /**
     * Decompress a compressed g gzip compressed string.
     *
     * @param str Compressed string
     * @return Decompressed string
     */
    public static Map<String, Object> decompress(String str) {
        ByteArrayInputStream byteInputStream = null;
        GZIPInputStream gzipInputStream = null;
        try {
            ThreadLocal<Kryo> kryoTL = new ThreadLocal<Kryo>() {
                protected Kryo initialValue() {
                    Kryo kryo = new Kryo();
                    // Class registering precedence matters. Hence intentionally giving a registration ID
                    kryo.register(HashMap.class, 111);
                    kryo.register(ArrayList.class, 222);
                    kryo.register(PublishingPayload.class, 333);
                    return kryo;
                }
            };
            byteInputStream = new ByteArrayInputStream(DatatypeConverter.parseBase64Binary(str));
            gzipInputStream = new GZIPInputStream(byteInputStream);
            byte[] unzippedBytes = IOUtils.toByteArray(gzipInputStream);
            Input input = new Input(unzippedBytes);
            return kryoTL.get().readObjectOrNull(input, HashMap.class);
        } catch (IOException e) {
            throw new RuntimeException("Error occured while decompressing events string: " + e.getMessage(), e);
        } finally {
            try {
                if (byteInputStream != null) {
                    byteInputStream.close();
                }
                if (gzipInputStream != null) {
                    gzipInputStream.close();
                }
            } catch (IOException e) {
                //ignore
            }
        }
    }

    /**
     * Loads the specified resource from the classpath and returns its content as an OMElement.
     *
     * @param path A relative path to the resource file
     * @return An OMElement containing the resource content
     */
    public OMElement loadResource(String path) throws FileNotFoundException, XMLStreamException {
        OMElement documentElement = null;
        FileInputStream inputStream = null;
        XMLStreamReader parser = null;
        StAXOMBuilder builder = null;
        path = TestConfigurationProvider.getResourceLocation() + path;
        File file = new File(path);
        if (file.exists()) {
            try {
                inputStream = new FileInputStream(file);
                parser = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);
                //create the builder
                builder = new StAXOMBuilder(parser);
                //get the root element (in this case the envelope)
                documentElement = builder.getDocumentElement().cloneOMElement();
            } finally {
                if (builder != null) {
                    builder.close();
                }
                if (parser != null) {
                    try {
                        parser.close();
                    } catch (XMLStreamException e) {
                        //ignore
                    }
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        //ignore
                    }
                }

            }
        } else {
            throw new FileNotFoundException("File Not Exist at " + path);
        }
        return documentElement;
    }

    /**
     * Loads the configuration of the specified sample into the ESB.
     *
     * @param number Sample number
     * @throws Exception If an error occurs while loading the sample configuration
     */
    public OMElement loadESBSampleConfiguration(int number) throws Exception {
        String filePath = Paths.get(TestConfigurationProvider.getResourceLocation("ESB"), "samples",
                                    "synapse_sample_" + number + ".xml").toString();
        File configFile = new File(filePath);
        FileInputStream inputStream = null;
        XMLStreamReader parser = null;
        StAXOMBuilder builder = null;
        OMElement documentElement = null;
        try {
            inputStream = new FileInputStream(configFile.getAbsolutePath());
            parser = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);
            builder = new StAXOMBuilder(parser);
            documentElement = builder.getDocumentElement().cloneOMElement();

        } finally {
            if (builder != null) {
                builder.close();
            }
            if (parser != null) {
                try {
                    parser.close();
                } catch (XMLStreamException e) {
                    //ignore
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }
        return documentElement;
    }

    /**
     * load synapse configuration from OMElement and fail if a configuration exists with the same name.
     *
     * @param synapseConfig synapse configuration
     * @param backendURL    server backEnd url
     * @param sessionCookie session Cookie
     * @throws java.rmi.RemoteException
     * @throws javax.xml.stream.XMLStreamException
     * @throws javax.servlet.ServletException
     */
    public void updateESBConfiguration(OMElement synapseConfig, String backendURL, String sessionCookie)
            throws Exception {
        // Do nothing

    }

    /**
     * @param backEndUrl
     * @param sessionCookie
     * @param proxyConfig
     * @throws javax.xml.stream.XMLStreamException
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    public void addProxyService(String backEndUrl, String sessionCookie, OMElement proxyConfig) throws Exception {

    }

    /**
     * Adds Inbound Endpoint using parameters
     *
     * @param backEndUrl
     * @param sessionCookie
     * @param inboundEndpoint
     * @throws Exception
     */
    public void addInboundEndpointFromParams(String backEndUrl, String sessionCookie, OMElement inboundEndpoint)
            throws Exception {
    }

    /**
     * Updates inbound Endpoint Using Parameters
     *
     * @param backEndUrl
     * @param sessionCookie
     * @param inboundEndpoint
     * @throws Exception
     */
    public void updateInboundEndpoint(String backEndUrl, String sessionCookie, OMElement inboundEndpoint)
            throws Exception {

    }

    /**
     * This method can be used to check whether file specified by the location has contents
     *
     * @param fullPath path to file
     * @return true if file has no contents
     */
    public boolean isFileEmpty(String fullPath) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fullPath));
            if (br.readLine() == null) {
                return true;
            }
        } catch (FileNotFoundException fileNotFoundException) {
            //synapse config is not found therefore it should copy original file to the location
            log.info("Synapse config file cannot be found in " + fullPath + " copying Backup Config to the location.");
            return true;
        } catch (IOException ioException) {
            //exception ignored
            log.info("Couldn't read the synapse config from the location " + fullPath);
        }
        return false;
    }
}
