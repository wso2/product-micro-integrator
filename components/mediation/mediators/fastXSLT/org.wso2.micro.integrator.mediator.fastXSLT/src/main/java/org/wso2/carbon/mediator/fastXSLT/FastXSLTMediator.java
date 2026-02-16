package org.wso2.carbon.mediator.fastXSLT;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.commons.io.IOUtils;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.config.Entry;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.MediatorProperty;
import org.apache.synapse.mediators.Value;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.Pipe;
import org.apache.synapse.transport.passthru.config.PassThroughConfiguration;
import org.apache.synapse.transport.passthru.util.RelayConstants;
import org.apache.synapse.transport.passthru.util.RelayUtils;
import org.apache.synapse.util.jaxp.DOOMResultBuilderFactory;
import org.apache.synapse.util.jaxp.DOOMSourceBuilderFactory;
import org.apache.synapse.util.jaxp.ResultBuilderFactory;
import org.apache.synapse.util.jaxp.SourceBuilderFactory;
import org.apache.synapse.util.jaxp.StreamResultBuilderFactory;
import org.apache.synapse.util.jaxp.StreamSourceBuilderFactory;
import org.apache.synapse.util.resolver.ResourceMap;
import org.apache.synapse.util.xpath.SourceXPathSupport;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.wso2.carbon.relay.StreamingOnRequestDataSource;

public class FastXSLTMediator extends AbstractMediator implements ManagedLifecycle {


	/**
     * The feature for which deciding switching between DOM and Stream during the
     * transformation process
     */
    public static final String USE_DOM_SOURCE_AND_RESULTS =
            "http://ws.apache.org/ns/synapse/transform/feature/dom";

    /**
     * The name of the attribute that allows to specify the {@link SourceBuilderFactory}.
     */
    public static final String SOURCE_BUILDER_FACTORY =
            "http://ws.apache.org/ns/synapse/transform/attribute/sbf";

    /**
     * The name of the attribute that allows to specify the {@link ResultBuilderFactory}.
     */
    public static final String RESULT_BUILDER_FACTORY =
            "http://ws.apache.org/ns/synapse/transform/attribute/rbf";

    private Value xsltKey=null;

    private final Object transformerLock = new Object();
    private Map<String, Templates> cachedTemplatesMap = new Hashtable<String, Templates>();
    private TransformerFactory transFact = TransformerFactory.newInstance();
    /**
     * The (optional) XPath expression which yields the source element for a transformation
     */
    private final SourceXPathSupport source = new SourceXPathSupport();

    /**
     * The name of the message context property to store the transformation result
     */
    private String targetPropertyName = null;
    /**
     * A resource map used to resolve xsl:import and xsl:include.
     */
    private ResourceMap resourceMap;

    /**
     * Any parameters which should be passed into the XSLT transformation
     */
    private final List<MediatorProperty> properties = new ArrayList<MediatorProperty>();

    /**
     * The source builder factory to use.
     */
    private SourceBuilderFactory sourceBuilderFactory = new StreamSourceBuilderFactory();

    /**
     * The result builder factory to use.
     */
    private ResultBuilderFactory resultBuilderFactory = new StreamResultBuilderFactory();

    /**
     * Any features which should be set to the TransformerFactory explicitly
     */
    private final List<MediatorProperty> transformerFactoryFeatures = new ArrayList<MediatorProperty>();

    /**
     * Any attributes which should be set to the TransformerFactory explicitly
     */
    private final List<MediatorProperty> transformerFactoryAttributes
            = new ArrayList<MediatorProperty>();

    private int bufferSizeSupport = 1024*8;

    public boolean mediate(MessageContext context) {

        if (context.getEnvironment().isDebuggerEnabled()) {
            if (super.divertMediationRoute(context)) {
                return true;
            }
        }

        InputStream inMessage = null;

        Templates cTemplate = null;
        org.apache.axis2.context.MessageContext axis2MC =null;
        SynapseLog synLog = getLog(context);

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("XMLConverter mediator : start");
        }

        try {
        	axis2MC = ((Axis2MessageContext)context).getAxis2MessageContext();
        	Pipe pipe= (Pipe) axis2MC.getProperty(PassThroughConstants.PASS_THROUGH_PIPE);
            if(pipe != null){
                inMessage = getMessageInputStreamFromSoapEnvelop(context);
                if(inMessage == null ) {
                    inMessage = getMessageInputStreamPT(axis2MC, pipe);
                }

            } else{
        	 inMessage = getMessageInputStreamBinaryRelay(context);
            }

            if( inMessage == null){
                inMessage = getMessageInputStreamFromSoapEnvelop(context);
            }


        } catch (IOException e) {
            handleException("Error while reading the input stream ", e, context);
        }

        String generatedXsltKey = xsltKey.evaluateValue(context);

        // determine if it is needed to create or create the template
        if (isCreationOrRecreationRequired(context)) {
            // many threads can see this and come here for acquiring the lock
            synchronized (transformerLock) {
                // only first thread should create the template
                if (isCreationOrRecreationRequired(context)) {
                    cTemplate = createTemplate(context, generatedXsltKey);
                } else {
                    cTemplate = cachedTemplatesMap.get(generatedXsltKey);
                }
            }
        } else {
            //If already cached template then load it from cachedTemplatesMap
            synchronized (transformerLock) {
                cTemplate = cachedTemplatesMap.get(generatedXsltKey);
            }
        }

        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");

        try {
            ByteArrayOutputStream _transformedOutMessage = new ByteArrayOutputStream();
            transform(inMessage, _transformedOutMessage, cTemplate);

            ByteArrayOutputStream _transformedOutMessageNew = new ByteArrayOutputStream();
            IOUtils.write(_transformedOutMessage.toByteArray(), _transformedOutMessageNew);

            BufferedInputStream bufferedStream = new BufferedInputStream(new ByteArrayInputStream(_transformedOutMessageNew.toByteArray()));

        	Pipe pipe= (Pipe) axis2MC.getProperty(PassThroughConstants.PASS_THROUGH_PIPE);
            if(pipe != null) {
                OutputStream msgContextOutStream = pipe.resetOutputStream();

                axis2MC.setProperty(PassThroughConstants.BUFFERED_INPUT_STREAM, bufferedStream);
                boolean fullLenthDone = false;
                if (_transformedOutMessage.toByteArray().length > bufferSizeSupport) {
                    RelayUtils.buildMessage(axis2MC, false, bufferedStream);
                    fullLenthDone = true;
                }

                if (!fullLenthDone &&
                        (Boolean.TRUE.equals(context.getProperty(SynapseConstants.IS_SEQ_CONTENT_AWARE)) ||
                                Boolean.TRUE
                                .equals(axis2MC.getProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED)))) {
                    RelayUtils.buildMessage(axis2MC, false, bufferedStream);
                } else if (!fullLenthDone) {
                    IOUtils.write(_transformedOutMessage.toByteArray(), msgContextOutStream);

                }
            } else {
                OMElement omElement = context.getEnvelope().getBody().getFirstElement();
                if (omElement != null) {
                    omElement.detach();
                }
                String omString = _transformedOutMessage.toString();
                OMElement responseOM = AXIOMUtil.stringToOM(omString);
                context.getEnvelope().getBody().addChild(responseOM);
            }

            //letting pipe know that the raw serialization has been completed and if reach pipe consume operation
            //by looking at this variable the the pipe consumer operation encoder will completes after
            //writing the byte stream to the output channel

            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("XMLConverter mediator : Done");
            }

            return true;
        } catch (Exception e) {
            handleException("Error while transforming the Stream ", e, context);
        }

        handleException("Unexpected SOAP message content found. " + this.getClass().getName() +
                " mediator can only be used with messages built with BinaryRelayBuilder", context);
        return false;
    }

    private void transform(InputStream xmlIn, OutputStream out, Templates templates)
            throws Exception {

        Transformer trans = templates.newTransformer();
        Source source = new StreamSource(xmlIn);
        Result resultXML = new StreamResult(out);
        trans.transform(source, resultXML);

    }

    /**
     * Create a XSLT template object and assign it to the cachedTemplates variable
     *
     * @param synCtx  current message
     * @param xsltKey evaluated xslt key(real key value) for dynamic or static key
     * @return cached template
     */
    private Templates createTemplate(MessageContext synCtx, String xsltKey) {
        // Assign created template
        Templates cachedTemplates = null;

        try {
            cachedTemplates = transFact.newTemplates(
                    SynapseConfigUtils.getStreamSource(synCtx.getEntry(xsltKey)));
            if (cachedTemplates == null) {
                // if cached template creation failed
                handleException("Error compiling the XSLT with key : " + xsltKey, synCtx);
            } else {
                // if cached template is created then put it in to cachedTemplatesMap
                cachedTemplatesMap.put(xsltKey, cachedTemplates);
            }
        } catch (Exception e) {
            handleException("Error creating XSLT transformer using : " + xsltKey, e, synCtx);
        }
        return cachedTemplates;
    }

    private InputStream getMessageInputStreamBinaryRelay(MessageContext context) throws IOException {
        InputStream temp;
        SOAPEnvelope envelope = context.getEnvelope();
        OMElement contentEle = envelope.getBody().getFirstElement();

        if (contentEle != null) {

            OMNode node = contentEle.getFirstOMChild();

            if (node != null && (node instanceof OMText)) {

                OMText binaryDataNode = (OMText) node;
                DataHandler dh = (DataHandler) binaryDataNode.getDataHandler();
                DataSource dataSource = dh.getDataSource();

                if (dataSource instanceof StreamingOnRequestDataSource) {
                    // preserve the content while reading the incoming data stream
                    ((StreamingOnRequestDataSource) dataSource).setLastUse(false);
                    // forcing to consume the incoming data stream
                    temp = dataSource.getInputStream();
                    return temp;
                }
            }
        }
        return null;
    }

    private InputStream getMessageInputStreamFromSoapEnvelop(MessageContext messageContext) {
        InputStream temp = null;
        SOAPEnvelope envelope = messageContext.getEnvelope();
        OMElement contentEle = envelope.getBody().getFirstElement();
        if (contentEle != null) {
            String omElement = contentEle.toString();
            temp = IOUtils.toInputStream(omElement);
        }
        return temp;
    }





    private InputStream getMessageInputStreamPT(org.apache.axis2.context.MessageContext context,Pipe pipe) throws IOException {
    	//AtomicBoolean inBufferInputMode = new AtomicBoole
        if (pipe != null && Boolean.TRUE.equals(context.getProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED)) && context.getProperty(PassThroughConstants.BUFFERED_INPUT_STREAM) != null){
             BufferedInputStream bufferedInputStream= (BufferedInputStream) context.getProperty(PassThroughConstants.BUFFERED_INPUT_STREAM);
             bufferedInputStream.reset();
             bufferedInputStream.mark(0);
             return bufferedInputStream;
         }

         
        if(pipe != null ){
           return pipe.getInputStream(); 
        }
        return null;
    }

    private MessageDataSource getResultBlob(MessageContext synCtx) throws IOException {
        SynapseEnvironment synEnv = synCtx.getEnvironment();
        return new MessageDataSource(synEnv.createOverflowBlob());
    }


    private boolean writeResult(MessageContext synCtx, MessageDataSource resulMessage) throws IOException, XMLStreamException {
        if (resulMessage != null) {

            SOAPFactory factory = OMAbstractFactory.getSOAP12Factory();

            QName BINARY_CONTENT_QNAME = new QName("http://ws.apache.org/commons/ns/payload", "binary");
            OMNamespace ns = factory.createOMNamespace(
                    BINARY_CONTENT_QNAME.getNamespaceURI(), "ns");
            OMElement omEle = factory.createOMElement(
                    BINARY_CONTENT_QNAME.getLocalPart(), ns);

            DataHandler dataHandler = new DataHandler(resulMessage);
            OMText textData = factory.createOMText(dataHandler, true);

            omEle.addChild(textData);

            SOAPBody body = synCtx.getEnvelope().getBody();
            if(body.getFirstElement() != null){
            	 body.getFirstElement().detach();
            }
           
            body.addChild(omEle);

            return true;
        }
        return false;
    }


    /**
     * Utility method to determine weather it is needed to create a XSLT template
     *
     * @param synCtx current message
     * @return true if it is needed to create a new XSLT template
     */
    private boolean isCreationOrRecreationRequired(MessageContext synCtx) {

        // Derive actual key from message context
        String generatedXsltKey = xsltKey.evaluateValue(synCtx);

        // if there are no cachedTemplates inside cachedTemplatesMap or
        // if the template related to this generated key is not cached
        // then it need to be cached
        if (cachedTemplatesMap.isEmpty() || !cachedTemplatesMap.containsKey(generatedXsltKey)) {
            // this is a creation case
            return true;
        } else {
            // build transformer - if necessary
            Entry dp = synCtx.getConfiguration().getEntryDefinition(generatedXsltKey);
            // if the xsltKey refers to a dynamic resource, and if it has been expired
            // it is a recreation case
            return dp != null && dp.isDynamic() && (!dp.isCached() || dp.isExpired());
        }
    }

    public Value getXsltKey() {
        return xsltKey;
    }

    public void setXsltKey(Value xsltKey) {
        this.xsltKey = xsltKey;
    }

    public void setSourceXPathString(String sourceXPathString) {
        this.source.setXPathString(sourceXPathString);
    }

    public SynapseXPath getSource() {
        return source.getXPath();
    }

    public void setSource(SynapseXPath source) {
        this.source.setXPath(source);
    }

    public String getTargetPropertyName() {
        return targetPropertyName;
    }

    public void setTargetPropertyName(String targetPropertyName) {
        this.targetPropertyName = targetPropertyName;
    }

    public void addProperty(MediatorProperty p) {
        properties.add(p);
    }

    /**
     * Set the properties defined in the mediator as parameters on the stylesheet.
     *
     * @param transformer Transformer instance
     * @param synCtx MessageContext instance
     * @param synLog SynapseLog instance
     */
    private void applyProperties(Transformer transformer, MessageContext synCtx,
                                 SynapseLog synLog) {
        for (MediatorProperty prop : properties) {
            if (prop != null) {
                String value;
                if (prop.getValue() != null) {
                    value = prop.getValue();
                } else {
                    value = prop.getExpression().stringValueOf(synCtx);
                }
                if (synLog.isTraceOrDebugEnabled()) {
                    if (value == null) {
                        synLog.traceOrDebug("Not setting parameter '" + prop.getName() + "'");
                    } else {
                        synLog.traceOrDebug("Setting parameter '" + prop.getName() + "' to '"
                                + value + "'");
                    }
                }
                if (value != null) {
                    transformer.setParameter(prop.getName(), value);
                }
            }
        }
    }

    /**
     * Add a feature to be set on the {@link TransformerFactory} used by this mediator instance.
     * This method can also be used to enable some Synapse specific optimizations and
     * enhancements as described in the documentation of this class.
     *
     * @param featureName The name of the feature
     * @param isFeatureEnable the desired state of the feature
     *
     * @see TransformerFactory#setFeature(String, boolean)
     * @see FastXSLTMediator
     */
    public void addFeature(String featureName, boolean isFeatureEnable) {
        MediatorProperty mp = new MediatorProperty();
        mp.setName(featureName);
        if (isFeatureEnable) {
            mp.setValue("true");
        } else {
            mp.setValue("false");
        }
        transformerFactoryFeatures.add(mp);
        if (USE_DOM_SOURCE_AND_RESULTS.equals(featureName)) {
            if (isFeatureEnable) {
                sourceBuilderFactory = new DOOMSourceBuilderFactory();
                resultBuilderFactory = new DOOMResultBuilderFactory();
            }
        } else {
            try {
                transFact.setFeature(featureName, isFeatureEnable);
            } catch (TransformerConfigurationException e) {
                String msg = "Error occurred when setting features to the TransformerFactory";
                log.error(msg, e);
                throw new SynapseException(msg, e);
            }
        }
    }

    /**
     * Add an attribute to be set on the {@link TransformerFactory} used by this mediator instance.
     * This method can also be used to enable some Synapse specific optimizations and
     * enhancements as described in the documentation of this class.
     *
     * @param name The name of the feature
     * @param value should this feature enable?
     *
     * @see TransformerFactory#setAttribute(String, Object)
     * @see FastXSLTMediator
     */
    public void addAttribute(String name, String value) {
        MediatorProperty mp = new MediatorProperty();
        mp.setName(name);
        mp.setValue(value);
        transformerFactoryAttributes.add(mp);
        if (SOURCE_BUILDER_FACTORY.equals(name) || RESULT_BUILDER_FACTORY.equals(name)) {
            Object instance;
            try {
                instance = Class.forName(value).newInstance();
            } catch (ClassNotFoundException e) {
                String msg = "The class specified by the " + name + " attribute was not found";
                log.error(msg, e);
                throw new SynapseException(msg, e);
            } catch (Exception e) {
                String msg = "The class " + value + " could not be instantiated";
                log.error(msg, e);
                throw new SynapseException(msg, e);
            }
            if (SOURCE_BUILDER_FACTORY.equals(name)) {
                sourceBuilderFactory = (SourceBuilderFactory)instance;
            } else {
                resultBuilderFactory = (ResultBuilderFactory)instance;
            }
        } else {
            try {
                transFact.setAttribute(name, value);
            } catch (IllegalArgumentException e) {
                String msg = "Error occurred when setting attribute to the TransformerFactory";
                log.error(msg, e);
                throw new SynapseException(msg, e);
            }
        }
    }

    /**
     * @return Return the features explicitly set to the TransformerFactory through this mediator.
     */
    public List<MediatorProperty> getFeatures(){
        return transformerFactoryFeatures;
    }

    /**
     * @return Return the attributes explicitly set to the TransformerFactory through this mediator.
     */
    public List<MediatorProperty> getAttributes(){
        return transformerFactoryAttributes;
    }

    public void addAllProperties(List<MediatorProperty> list) {
        properties.addAll(list);
    }

    public List<MediatorProperty> getProperties() {
        return properties;
    }

    public ResourceMap getResourceMap() {
        return resourceMap;
    }

    public void setResourceMap(ResourceMap resourceMap) {
        this.resourceMap = resourceMap;
    }

    public boolean isContentAware() {
        return false;
    }

	@Override
    public void destroy() {
	    // TODO Auto-generated method stub
	    
    }

	@Override
    public void init(SynapseEnvironment arg0) {
		 PassThroughConfiguration conf = PassThroughConfiguration.getInstance();
		 bufferSizeSupport =conf.getIOBufferSize();
    }
}
