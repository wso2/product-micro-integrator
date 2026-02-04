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
package org.wso2.carbon.mediator.datamapper;

import com.google.gson.Gson;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.impl.llom.OMTextImpl;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.Value;
import org.apache.synapse.mediators.template.TemplateContext;
import org.apache.synapse.mediators.v2.VariableMediator;
import org.apache.synapse.util.AXIOMUtils;
import org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.JSException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.ReaderException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.WriterException;
import org.wso2.carbon.mediator.datamapper.engine.core.executors.ScriptExecutor;
import org.wso2.carbon.mediator.datamapper.engine.core.executors.ScriptRunner;
import org.wso2.carbon.mediator.datamapper.engine.core.mapper.JSFunction;
import org.wso2.carbon.mediator.datamapper.engine.core.mapper.MappingHandler;
import org.wso2.carbon.mediator.datamapper.engine.core.mapper.MappingResource;
import org.wso2.carbon.mediator.datamapper.engine.core.mapper.XSLTMappingHandler;
import org.wso2.carbon.mediator.datamapper.engine.core.mapper.XSLTMappingResource;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.Schema;
import org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants;
import org.wso2.carbon.mediator.datamapper.engine.utils.InputOutputDataType;
import org.xml.sax.SAXException;

import javax.script.Compilable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.AXIS2_CLIENT_CONTEXT;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.AXIS2_CONTEXT;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.DEFAULT_CONTEXT;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.EMPTY_STRING;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.FUNCTION_CONTEXT;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.INPUT_TYPE;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.OPERATIONS_CONTEXT;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.OUTPUT_TYPE;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.SYNAPSE_CONTEXT;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.TRANSPORT_CONTEXT;
import static org.wso2.carbon.mediator.datamapper.config.xml.DataMapperMediatorConstants.TRANSPORT_HEADERS;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.INPUT_VARIABLE_IDENTIFIER;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.ORG_APACHE_SYNAPSE_DATAMAPPER_EXECUTOR_POOL_SIZE;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.ENCODE_CHAR_HYPHEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.HYPHEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.PROPERTIES_OBJECT_NAME;

/**
 * By using the input schema, output schema and mapping configuration,
 * DataMapperMediator generates the output required by the next mediator for the
 * input received by the previous mediator.
 */
public class DataMapperMediator extends AbstractMediator implements ManagedLifecycle {

    private static final Log log = LogFactory.getLog(DataMapperMediator.class);
    private static final String cSVToXMLOpeningTag = "<text xmlns=\"http://ws.apache.org/commons/ns/payload\">";
    private static final String cSVToXMLClosingTag = "</text>";
    private static final int INDEX_OF_CONTEXT = 0;
    private static final int INDEX_OF_NAME = 1;
    private static List<JSFunction> compiledFunctionList = new ArrayList<>();
    private Value mappingConfigurationKey = null;
    private Value inputSchemaKey = null;
    private Value outputSchemaKey = null;
    private Value xsltStyleSheetKey = null;
    private String inputType = null;
    private String outputType = null;
    private MappingResource mappingResource = null;
    private boolean usingXSLTMapping = false;
    private XSLTMappingResource xsltMappingResource = null;
    private XSLTMappingHandler xsltMappingHandler = null;
    private final Object xsltHandlerLock = new Object();
    private String targetVariableName = null;
    private String target = null;
    private static ScriptRunner scriptRunner = null;

    /**
     * Returns registry resources as input streams to create the MappingResourceLoader object
     *
     * @param synCtx Message context
     * @param key    location in the registry
     * @return mapping configuration, inputSchema and outputSchema as inputStreams
     */
    private static InputStream getRegistryResource(MessageContext synCtx, String key) {
        InputStream inputStream = null;
        Object entry = synCtx.getEntry(key);
        if (entry instanceof OMTextImpl) {
            if (log.isDebugEnabled()) {
                log.debug("Retrieving the key :" + key);
            }
            OMTextImpl text = (OMTextImpl) entry;
            String content = text.getText();
            inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
        return inputStream;
    }

    /**
     * Gets the key which is used to pick the mapping configuration from the
     * registry
     *
     * @return the key which is used to pick the mapping configuration from the
     * registry
     */
    public Value getMappingConfigurationKey() {
        return mappingConfigurationKey;
    }

    /**
     * Sets the registry key in order to pick the mapping configuration
     *
     * @param dataMapperconfigKey registry key for the mapping configuration
     */
    public void setMappingConfigurationKey(Value dataMapperconfigKey) {
        this.mappingConfigurationKey = dataMapperconfigKey;
    }

    /**
     * Gets the key which is used to pick the input schema from the
     * registry
     *
     * @return the key which is used to pick the input schema from the
     * registry
     */
    public Value getInputSchemaKey() {
        return inputSchemaKey;
    }

    /**
     * Sets the registry key in order to pick the input schema
     *
     * @param dataMapperInSchemaKey registry key for the input schema
     */
    public void setInputSchemaKey(Value dataMapperInSchemaKey) {
        this.inputSchemaKey = dataMapperInSchemaKey;
    }

    /**
     * Sets the registry key in order to pick the input schema
     *
     * @param dataMapperXsltStyleSheetKey registry key for the input schema
     */
    public void setXsltStyleSheetKey(Value dataMapperXsltStyleSheetKey) {
        this.xsltStyleSheetKey = dataMapperXsltStyleSheetKey;
    }

    /**
     *
     * @return dataMapperXsltStyleSheetKey registry key for the input schema
     */
    public Value getXsltStyleSheetKey() {
        return xsltStyleSheetKey;
    }

    /**
     * Gets the key which is used to pick the output schema from the
     * registry
     *
     * @return the key which is used to pick the output schema from the
     * registry
     */
    public Value getOutputSchemaKey() {
        return outputSchemaKey;
    }

    /**
     * Sets the registry key in order to pick the output schema
     *
     * @param dataMapperOutSchemaKey registry key for the output schema
     */
    public void setOutputSchemaKey(Value dataMapperOutSchemaKey) {
        this.outputSchemaKey = dataMapperOutSchemaKey;
    }

    /**
     * Gets the input data type
     *
     * @return the input data type
     */
    public String getInputType() {
        return inputType;
    }

    /**
     * Sets the input data type
     *
     * @param type the input data type
     */
    public void setInputType(String type) {
        this.inputType = type;
    }

    /**
     * Gets the output data type
     *
     * @return the output data type
     */
    public String getOutputType() {
        return outputType;
    }

    /**
     * Sets the output data type
     *
     * @param type the output data type
     */
    public void setOutputType(String type) {
        this.outputType = type;
    }

    /**
     * Get the target variable name
     *
     * @return the target variable name
     */
    public String getTargetVariableName() {
        return targetVariableName;
    }

    /**
     * Set the target variable name
     *
     * @param targetVariableName the target variable name
     */
    public void setTargetVariableName(String targetVariableName) {
        this.targetVariableName = targetVariableName;
    }

    /**
     * Get the target
     *
     * @return the target
     */
    public String getTarget() {
        return target;
    }

    /**
     * Set the target
     *
     * @param target the target
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * Set a pre-built mapping resource
     * This method is used by data-mapper test feature in EI-Tooling
     *
     * @param mappingResource prebuilt mapping resource
     */
    public void setMappingResource(MappingResource mappingResource) {
        this.mappingResource = mappingResource;
    }

    /**
     * Get the values from the message context to do the data mapping
     *
     * @param synCtx current message for the mediation
     * @return true if mediation happened successfully else false.
     */
    @Override
    public boolean mediate(MessageContext synCtx) {

        SynapseLog synLog = getLog(synCtx);

        if (synCtx.getEnvironment().isDebuggerEnabled()) {
            if (super.divertMediationRoute(synCtx)) {
                return true;
            }
        }
        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : DataMapper mediator");
            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message :" + synCtx.getEnvelope());
            }
        }

        if (mappingResource == null && xsltMappingResource == null) {
            checkForXSLTTransformation(synCtx);
            if (mappingResource == null && !usingXSLTMapping) {
                String configKey = mappingConfigurationKey.evaluateValue(synCtx);
                String inSchemaKey = inputSchemaKey.evaluateValue(synCtx);
                String outSchemaKey = outputSchemaKey.evaluateValue(synCtx);
                if (!(StringUtils.isNotEmpty(configKey) && StringUtils.isNotEmpty(inSchemaKey) &&
                      StringUtils.isNotEmpty(outSchemaKey))) {
                    handleException("DataMapper mediator : Invalid configurations", synCtx);
                } else {
                    // mapping resources needed to get the final output
                    try {
                        mappingResource = getMappingResource(synCtx, configKey, inSchemaKey, outSchemaKey);
                        initializeInputOutputType();
                    } catch (IOException e) {
                        handleException("DataMapper mediator mapping resource generation failed", e, synCtx);
                    }
                }
            }
        }

        if (target != null || targetVariableName != null) {
            // new datamapping behaviour without schema validation is decided based on above attributes
            try {
                String input = getInput(synCtx, inputType);
                String output = transform(synCtx, mappingConfigurationKey.evaluateValue(synCtx), input);

                if (DataMapperMediatorConstants.TARGET_BODY.equalsIgnoreCase(target)) {
                    setOutput(synCtx, outputType, output);
                } else if (DataMapperMediatorConstants.TARGET_VARIABLE.equalsIgnoreCase(target) &&
                        targetVariableName != null) {
                    setOutputVariable(synCtx, outputType, output, targetVariableName);
                } else {
                    handleException("Invalid target attribute found in DataMapper mediator", synCtx);
                }
            } catch (IOException e) {
                handleException("Mapping failed in DataMapper mediator", e, synCtx);
            }
        } else {
            // preserve backward compatibility
            // Does message conversion and gives the final result
            transformWithSchemaValidation(synCtx);
        }

        //setting output type in the axis2 message context
        switch (outputType) {
            case "JSON":
                ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty("messageType", "application/json");
                ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty("ContentType", "application/json");
                break;
            case "XML":
                ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty("messageType", "application/xml");
                ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty("ContentType", "application/xml");
                break;
            case "CSV":
                ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty("messageType", "text/xml");
                ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty("ContentType", "text/xml");
                break;
            default:
                throw new SynapseException("Unsupported output data type found : " + outputType);
        }

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("End : DataMapper mediator");
            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }

        return true;
    }

    /**
     * this method check for possibility in xslt based message transformation
     * initialize the xslt resources if possible to use xslt based message transformation
     *
     * @param synCtx message context
     */
    private void checkForXSLTTransformation(MessageContext synCtx) {
        if (xsltStyleSheetKey != null && (InputOutputDataType.XML.toString().equals(inputType) &&
                                          InputOutputDataType.XML.toString().equals(outputType))) {
            usingXSLTMapping = true;
            if (xsltMappingResource == null) {
                String xsltKey = xsltStyleSheetKey.evaluateValue(synCtx);
                try {
                    xsltMappingResource = getXsltMappingResource(synCtx, xsltKey);
                } catch (SAXException | IOException |
                        ParserConfigurationException e) {
                    handleException("DataMapper mediator xslt mapping resource generation " +
                                    "failed", e, synCtx);
                    usingXSLTMapping = false;
                }
            }
            if (xsltMappingResource == null || !xsltMappingResource.isXsltCompatible()) {
                usingXSLTMapping = false;
            }
        }
    }

    public String transform(MessageContext synCtx, String configKey, String inputJson) throws IOException {

        if (scriptRunner == null) {
            String jsCode = getScriptResource(synCtx, configKey);
            scriptRunner = new ScriptRunner(jsCode);
        }
        return scriptRunner.runScript(inputJson, convertVariablesMapToJSON(synCtx));
    }

    /**
     * Convert the variables map to a JSON String.
     *
     * @param synCtx Message context
     * @return JSON String
     */
    private String convertVariablesMapToJSON(MessageContext synCtx) {
        return new Gson().toJson(((Axis2MessageContext) synCtx).getVariables());
    }

    private String getInput(MessageContext context, String inputType) {

        String inputString = null;
        try {
            switch (InputOutputDataType.fromString(inputType)) {
                case XML:
                case CSV:
                    inputString = JsonUtil.toJsonString(context.getEnvelope()).toString();
                    break;
                case JSON:
                    org.apache.axis2.context.MessageContext a2mc =
                            ((Axis2MessageContext) context).getAxis2MessageContext();
                    if (JsonUtil.hasAJsonPayload(a2mc)) {
                        try {
                            inputString = IOUtils.toString(JsonUtil.getJsonPayload(a2mc));
                        } catch (IOException e) {
                            handleException("Unable to read input message in Data Mapper mediator. " +
                                    e.getMessage(), e, context);
                        }
                    }
                    break;
                default:
                    inputString = context.getEnvelope().toString();
            }
        } catch (OMException e) {
            handleException("Unable to read input message in Data Mapper mediator. " + e.getMessage(), e,
                    context);
        } catch (AxisFault e) {
            handleException("Unable to convert input message from XML to JSON in Data Mapper mediator. " +
                    e.getMessage(), e, context);
        }
        return inputString;
    }

    private void setOutput(MessageContext msgCtx, String outputType, String output) {

        org.apache.axis2.context.MessageContext a2mc = ((Axis2MessageContext) msgCtx).getAxis2MessageContext();
        switch (InputOutputDataType.fromString(outputType)) {
            case JSON:
                try {
                    JsonUtil.getNewJsonPayload(a2mc, output, true, true);
                } catch (AxisFault e) {
                    handleException("Unable to add output JSON to Message Context in Data Mapper mediator. " +
                            e.getMessage(), e, msgCtx);
                }
                break;
            case XML:
            case CSV:
            default:
                try {
                    JsonUtil.removeJsonPayload(a2mc);
                    OMElement omXML = JsonUtil.toXml(IOUtils.toInputStream(output), false);
                    a2mc.getEnvelope().getBody().addChild(omXML.getFirstElement());
                } catch (AxisFault e) {
                    handleException("Unable to convert output JSON to XML in Data Mapper mediator. " +
                            e.getMessage(), e, msgCtx);
                }
        }
    }

    /**
     * Set the output message to the message context as a variable
     *
     * @param msgCtx        message context
     * @param outputType    output type
     * @param output        output message
     * @param targetVariableName
     */
    private void setOutputVariable(MessageContext msgCtx, String outputType, String output, String targetVariableName) {

        VariableMediator variableMediator = new VariableMediator();
        variableMediator.setName(targetVariableName);
        variableMediator.setValue(output, outputType);
        msgCtx.setVariable(targetVariableName, variableMediator.getValue());
    }

    /**
     * Does message conversion and gives the output message as the final result
     *
     * @param synCtx      the message synCtx
     */
    private void transformWithSchemaValidation(MessageContext synCtx) {
        try {
            String outputResult;
            if (usingXSLTMapping) {
                if (xsltMappingHandler == null) {
                    synchronized (xsltHandlerLock) {
                        if (xsltMappingHandler == null) {
                            xsltMappingHandler = new XSLTMappingHandler(this.xsltMappingResource);
                        }
                    }
                }
                outputResult = xsltMappingHandler.doMap(
                        getPropertiesMapForXSLT(xsltMappingResource.getRunTimeProperties(), synCtx),
                        getInputStream(synCtx, inputType, xsltMappingResource.getName()));
            } else {
                Map<String, Map<String, Object>> propertiesMap;

                String dmExecutorPoolSize = SynapsePropertiesLoader
                        .getPropertyValue(ORG_APACHE_SYNAPSE_DATAMAPPER_EXECUTOR_POOL_SIZE, null);
                if (!compiledFunctionList.contains(mappingResource.getFunction())) {
                    synchronized (this) {
                        // Only the first thread inside the sync block should pre-compile the script.
                        if (!compiledFunctionList.contains(mappingResource.getFunction())) {
                            try {
                                ScriptEngine scriptEngine = getScriptExecutor();
                                JSFunction jsFunction = mappingResource.getFunction();
                                String helperJSFunction = "var " + PROPERTIES_OBJECT_NAME + " = JSON.parse(" + ScriptExecutor.PROPERTIES_IDENTIFIER + ");\n" +
                                        "var " + getInputVariable(mappingResource.getInputSchema().getName()) + " = JSON.parse(" + INPUT_VARIABLE_IDENTIFIER + ");\n";
                                // Compile Data-mapper JS function body
                                if (jsFunction.getCompiledBody() == null) {
                                    if (scriptEngine instanceof Compilable) {
                                        jsFunction.setCompiledBody(((Compilable) scriptEngine).compile(jsFunction.getFunctionBody()));
                                    }
                                }
                                // Compile Data-mapper JS function name
                                if (jsFunction.getCompiledName() == null) {
                                    if (scriptEngine instanceof Compilable) {
                                        jsFunction.setCompiledName(((Compilable) scriptEngine).compile(jsFunction.getFunctionName()));
                                    }
                                }
                                if (jsFunction.getBindingHelperFunction() == null) {
                                    if (scriptEngine instanceof Compilable) {
                                        jsFunction.setBindingHelperFunction(((Compilable) scriptEngine).compile(helperJSFunction));
                                    }
                                }
                                // setting the pre-compiled function from the first thread
                                mappingResource.setFunction(jsFunction);
                                compiledFunctionList.add(jsFunction);
                            } catch (ScriptException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                MappingHandler mappingHandler = new MappingHandler(mappingResource, inputType, outputType,
                        dmExecutorPoolSize);

                propertiesMap = getPropertiesMap(mappingResource.getPropertiesList(), synCtx);

                /* execute mapping on the input stream */
                outputResult = mappingHandler.doMap(
                        getInputStream(synCtx, inputType, mappingResource.getInputSchema().getName()),
                        propertiesMap);
            }

            if (InputOutputDataType.CSV.toString().equals(outputType) &&
                    !InputOutputDataType.CSV.toString().equals(inputType)) {
                outputResult = cSVToXMLOpeningTag + StringEscapeUtils.escapeXml(outputResult) + cSVToXMLClosingTag;
            }

            if (InputOutputDataType.XML.toString().equals(outputType) ||
                    (InputOutputDataType.CSV.toString().equals(outputType) && !InputOutputDataType.CSV.toString()
                            .equals(inputType))) {

                OMElement outputMessage = AXIOMUtil.stringToOM(outputResult);
                if (outputMessage != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Output message received ");
                    }
                    // Use to create the SOAP message
                    QName resultQName = outputMessage.getQName();
                    if (resultQName.getLocalPart().equals("Envelope") && (
                            resultQName.getNamespaceURI().equals(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI)
                                    || resultQName.getNamespaceURI()
                                    .equals(SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI))) {
                        SOAPEnvelope soapEnvelope = AXIOMUtils.getSOAPEnvFromOM(outputMessage);
                        if (soapEnvelope != null) {
                            try {
                                if (log.isDebugEnabled()) {
                                    log.debug("Valid Envelope");
                                }
                                synCtx.setEnvelope(soapEnvelope);
                            } catch (AxisFault axisFault) {
                                handleException("Invalid Envelope", axisFault, synCtx);
                            }
                        }
                    } else {
                        synCtx.getEnvelope().getBody().getFirstElement().detach();
                        synCtx.getEnvelope().getBody().addChild(outputMessage);
                    }
                } else {
                    synCtx.getEnvelope().getBody().getFirstElement().detach();
                }

            } else if (InputOutputDataType.JSON.toString().equals(outputType)) {
                org.apache.axis2.context.MessageContext axis2MessageContext = ((Axis2MessageContext) synCtx)
                        .getAxis2MessageContext();
                JsonUtil.newJsonPayload(axis2MessageContext, outputResult, true, true);
            } else if (InputOutputDataType.CSV.toString().equals(outputType)) {
                synCtx.getEnvelope().getBody().getFirstElement().setText(outputResult.toString());

            }
        } catch (ReaderException | InterruptedException | XMLStreamException | SchemaException
                | IOException | JSException | WriterException | TransformerException e) {
            handleException("DataMapper mediator : mapping failed", e, synCtx);
        }
    }

    private InputStream getInputStream(MessageContext context, String inputType, String inputStartElement) {
        InputStream inputStream = null;
        try {
            switch (InputOutputDataType.fromString(inputType)) {
            case XML:
            case CSV:
                if ("soapenv:Envelope".equals(inputStartElement)) {
                    inputStream = new ByteArrayInputStream(
                            context.getEnvelope().toString().getBytes(StandardCharsets.UTF_8));
                } else {
                    inputStream = new ByteArrayInputStream(context.getEnvelope().getBody().getFirstElement().toString()
                            .getBytes(StandardCharsets.UTF_8));
                }
                break;
            case JSON:
                org.apache.axis2.context.MessageContext a2mc = ((Axis2MessageContext) context).getAxis2MessageContext();
                if (JsonUtil.hasAJsonPayload(a2mc)) {
                    inputStream = JsonUtil.getJsonPayload(a2mc);
                }
                break;
            default:
                inputStream = new ByteArrayInputStream(
                        context.getEnvelope().toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (OMException e) {
            handleException("Unable to read input message in Data Mapper mediator reason : " + e.getMessage(), e,
                    context);
        }
        return inputStream;
    }

    /**
     * State that DataMapperMediator interacts with the message context
     *
     * @return true if the DataMapperMediator is intending to interact with the
     * message context
     */
    @Override
    public boolean isContentAware() {
        return true;
    }

    @Override
    public boolean isContentAltering() {
        return true;
    }

    @Override
    public void init(SynapseEnvironment se) {

    }

    /**
     * destroy the generated unique ID for the DataMapperMediator instance
     */
    @Override
    public void destroy() {
    }

    /**
     * When Data mapper mediator has been invoked initially, this creates a new mapping resource
     * loader
     *
     * @param synCtx       message context
     * @param configKey    the location of the mapping configuration
     * @param inSchemaKey  the location of the input schema
     * @param outSchemaKey the location of the output schema
     * @return the MappingResourceLoader object
     * @throws IOException
     */
    private MappingResource getMappingResource(MessageContext synCtx, String configKey, String inSchemaKey,
            String outSchemaKey) throws IOException {

        InputStream configFileInputStream = getRegistryResource(synCtx, configKey);
        InputStream inputSchemaStream = getRegistryResource(synCtx, inSchemaKey);
        InputStream outputSchemaStream = getRegistryResource(synCtx, outSchemaKey);

        if (configFileInputStream == null) {
            handleException("DataMapper mediator : mapping configuration is null", synCtx);
        }

        if (inputSchemaStream == null) {
            handleException("DataMapper mediator : input schema is null", synCtx);
        }

        if (outputSchemaStream == null) {
            handleException("DataMapper mediator : output schema is null", synCtx);
        }

        try {
            // Creates a new mappingResourceLoader
            return new MappingResource(inputSchemaStream, outputSchemaStream, configFileInputStream, outputType);
        } catch (SchemaException | JSException e) {
            handleException(e.getMessage(), synCtx);
        }
        return null;
    }

    private String getScriptResource(MessageContext synCtx, String configKey) throws IOException {

        InputStream configFileInputStream = getRegistryResource(synCtx, configKey);
        if (configFileInputStream == null) {
            handleException("DataMapper mediator : mapping configuration is null", synCtx);
        }
        return IOUtils.toString(configFileInputStream, String.valueOf(StandardCharsets.UTF_8));
    }

    /**
     * When Data mapper mediator has been invoked initially, this creates a new xslt mapping
     * resource
     *
     * @param synCtx message context
     * @param xsltKey the location of the xslt stylesheet
     * @return
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    private XSLTMappingResource getXsltMappingResource(MessageContext synCtx, String xsltKey)
            throws SAXException, IOException,
            ParserConfigurationException {

        String content = synCtx.getEntry(xsltKey).toString();
        return new XSLTMappingResource(content);

    }

    /**
     * Retrieve property values and insert into a map
     *
     * @param propertiesNamesList Required properties
     * @param synCtx              Message context
     * @return Map filed with property name and the value
     */
    private Map<String, Map<String, Object>> getPropertiesMap(List<String> propertiesNamesList, MessageContext synCtx) {
        Map<String, Map<String, Object>> propertiesMap = new HashMap<>();
        String[] contextAndName;
        Object value;
        org.apache.axis2.context.MessageContext axis2MsgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        HashMap functionProperties = new HashMap();
        Stack<TemplateContext> templeteContextStack = ((Stack) synCtx
                .getProperty(SynapseConstants.SYNAPSE__FUNCTION__STACK));
        if (templeteContextStack != null && !templeteContextStack.isEmpty()) {
            TemplateContext templateContext = templeteContextStack.peek();
            functionProperties.putAll(templateContext.getMappedValues());
        }
        for (String propertyName : propertiesNamesList) {
            contextAndName = propertyName.split("\\['|'\\]");
            switch (contextAndName[INDEX_OF_CONTEXT].toUpperCase()) {
            case DEFAULT_CONTEXT:
            case SYNAPSE_CONTEXT:
                value = synCtx.getProperty(contextAndName[INDEX_OF_NAME]);
                break;
            case TRANSPORT_CONTEXT:
                value = ((Map) axis2MsgCtx.getProperty(TRANSPORT_HEADERS)).get(contextAndName[INDEX_OF_NAME]);
                break;
            case AXIS2_CONTEXT:
                value = axis2MsgCtx.getProperty(contextAndName[INDEX_OF_NAME]);
                break;
            case AXIS2_CLIENT_CONTEXT:
                value = axis2MsgCtx.getOptions().getProperty(contextAndName[INDEX_OF_NAME]);
                break;
            case OPERATIONS_CONTEXT:
                value = axis2MsgCtx.getOperationContext().getProperty(contextAndName[INDEX_OF_NAME]);
                break;
            case FUNCTION_CONTEXT:
                value = functionProperties.get(contextAndName[INDEX_OF_NAME]);
                break;
            default:
                log.warn(contextAndName[INDEX_OF_CONTEXT] + " scope is not found. Setting it to an empty value.");
                value = EMPTY_STRING;
            }
            if (value == null) {
                log.warn(propertyName + "not found. Setting it to an empty value.");
                value = EMPTY_STRING;
            }
            insertToMap(propertiesMap, contextAndName, value);
        }

        return propertiesMap;
    }

    /**
     * Retrive property values and return as a map
     *
     * @param properties Required properties
     * @param synCtx Message context
     * @return Map with values of each property
     */
    private Map<String, Object> getPropertiesMapForXSLT(Map<String, String> properties,
                                                        MessageContext
                                                                synCtx) {
        Map<String, Object> propertyValues = new HashMap<>();

        if (!properties.isEmpty()) {
            Object value;
            org.apache.axis2.context.MessageContext axis2MsgCtx = ((Axis2MessageContext) synCtx)
                    .getAxis2MessageContext();

            HashMap functionProperties = new HashMap();
            Stack<TemplateContext> templeteContextStack = ((Stack) synCtx
                    .getProperty(SynapseConstants.SYNAPSE__FUNCTION__STACK));
            if (templeteContextStack != null && !templeteContextStack.isEmpty()) {
                TemplateContext templateContext = templeteContextStack.peek();
                functionProperties.putAll(templateContext.getMappedValues());
            }
            for (Map.Entry<String, String> property : properties.entrySet()) {
                switch (property.getValue()) {
                    case DEFAULT_CONTEXT:
                    case SYNAPSE_CONTEXT:
                        value = synCtx.getProperty(property.getKey());
                        break;
                    case TRANSPORT_CONTEXT:
                        value = ((Map) axis2MsgCtx.getProperty(TRANSPORT_HEADERS)).get(property
                                .getKey());
                        break;
                    case AXIS2_CONTEXT:
                        value = axis2MsgCtx.getProperty(property.getKey());
                        break;
                    case AXIS2_CLIENT_CONTEXT:
                        value = axis2MsgCtx.getOptions().getProperty(property.getKey());
                        break;
                    case OPERATIONS_CONTEXT:
                        value = axis2MsgCtx.getOperationContext().getProperty(property.getKey());
                        break;
                    case FUNCTION_CONTEXT:
                        value = functionProperties.get(property.getKey());
                        break;
                    default:
                        log.warn(property.getValue() + " scope is not found. Setting it to an " +
                                 "empty value.");
                        value = EMPTY_STRING;
                }
                if (value == null) {
                    log.warn(property.getKey() + " not found. Setting it to an empty value.");
                    value = EMPTY_STRING;
                }
                propertyValues.put(property.getValue() + "_" + property.getKey(), value);
            }
        }

        return propertyValues;
    }

    /**
     * Insert a given value to the properties map
     *
     * @param propertiesMap  Reference to the properties map
     * @param contextAndName Context and the name of the property
     * @param value          Current value of the property
     */
    private void insertToMap(Map<String, Map<String, Object>> propertiesMap, String[] contextAndName, Object value) {
        Map<String, Object> insideMap = propertiesMap.get(contextAndName[INDEX_OF_CONTEXT]);
        if (insideMap == null) {
            insideMap = new HashMap();
            propertiesMap.put(contextAndName[INDEX_OF_CONTEXT], insideMap);
        }
        insideMap.put(contextAndName[INDEX_OF_NAME], value);
    }

    private String getInputVariable(String inputSchemaName) throws ScriptException {
        return "input" + inputSchemaName.replace(':', '_').replace('=', '_').replace(',', '_')
                .replace(HYPHEN, ENCODE_CHAR_HYPHEN);
    }

    private ScriptEngine getScriptExecutor() {

        String javaVersion = System.getProperty("java.version");
        if (javaVersion.startsWith("1.7") || javaVersion.startsWith("1.6")) {
            return new ScriptEngineManager().getEngineByName(DataMapperEngineConstants.DEFAULT_ENGINE_NAME);
        }
        return new ScriptEngineManager().getEngineByName(DataMapperEngineConstants.GRAALJS_ENGINE_NAME);
    }

    private void initializeInputOutputType() {
        if (inputType == null) {
            Schema inputSchema = mappingResource.getInputSchema();
            if (inputSchema != null && inputSchema.getSchemaMap() != null &&
                    inputSchema.getSchemaMap().containsKey(INPUT_TYPE)) {
                inputType = inputSchema.getSchemaMap().get(INPUT_TYPE).toString();
            } else {
                log.error("Input type is not defined in the input schema/synapse configuration");
                throw new SynapseException("Input type is not defined in the input schema/synapse configuration");
            }
        }
        if (outputType == null) {
            Schema outputSchema = mappingResource.getOutputSchema();
            if (outputSchema != null && outputSchema.getSchemaMap() != null &&
                    outputSchema.getSchemaMap().containsKey(OUTPUT_TYPE)) {
                outputType = outputSchema.getSchemaMap().get(OUTPUT_TYPE).toString();
            } else {
                log.error("Output type is not defined in the output schema/synapse configuration");
                throw new SynapseException("Output type is not defined in the output schema/synapse configuration");
            }
        }
    }
}
