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
package org.wso2.carbon.mediator.datamapper.engine.core.executors;

import com.sun.phobos.script.javascript.RhinoScriptEngineFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.JSException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.mapper.JSFunction;
import org.wso2.carbon.mediator.datamapper.engine.core.mapper.MappingResource;
import org.wso2.carbon.mediator.datamapper.engine.core.models.MapModel;
import org.wso2.carbon.mediator.datamapper.engine.core.models.Model;
import org.wso2.carbon.mediator.datamapper.engine.core.models.StringModel;
import org.wso2.carbon.mediator.datamapper.engine.output.formatters.MapOutputFormatter;
import org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants;
import org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineUtils;
import org.wso2.carbon.mediator.datamapper.engine.utils.OpenJDKNashornFactoryWrapper;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import java.util.AbstractList;
import java.util.HashMap;
import java.util.Map;

import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.DEFAULT_ENGINE_NAME;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.ENCODE_CHAR_HYPHEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.HYPHEN;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.INPUT_VARIABLE_IDENTIFIER;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.NASHORN_ENGINE_NAME;
import static org.wso2.carbon.mediator.datamapper.engine.utils.DataMapperEngineConstants.PROPERTIES_OBJECT_NAME;

/**
 * This class implements script executor for data mapper using java script executor (Rhino or
 * Nashorn)
 */
public class ScriptExecutor implements Executor {

    public static final String PROPERTIES_IDENTIFIER = "properties";
    public static final String OPENJDK_NASHORN_CLASSNAME = "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory";
    private static final Log log = LogFactory.getLog(ScriptExecutor.class);
    private ScriptEngine scriptEngine;
    Bindings bindings;

    /**
     * Create a script executor of the provided script executor type
     *
     * @param scriptExecutorType
     */
    public ScriptExecutor(ScriptExecutorType scriptExecutorType) {
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        switch (scriptExecutorType) {
            case NASHORN:
                scriptEngine = scriptEngineManager.getEngineByName(NASHORN_ENGINE_NAME);
                if (scriptEngine == null) {
                    try {
                        // Check whether the openjdk classes are available in the runtime, otherwise throw class not found exception
                        Class.forName(OPENJDK_NASHORN_CLASSNAME);
                        // Try getting openjdk nashorn engine from open-jdk-nashorn jar in the lib folder
                        scriptEngine = OpenJDKNashornFactoryWrapper.getOpenJDKNashornFactory().getScriptEngine();
                        bindings = scriptEngine.createBindings();
                        log.debug("Setting OpenJDK Nashorn as Script Engine");
                        break;
                    } catch (Exception e) {
                        log.warn("Could not find Nashorn jar in the lib, switching to rhino js. This may cause performance issue ");
                        log.debug(e);
                        scriptEngineManager.registerEngineName(DEFAULT_ENGINE_NAME, new RhinoScriptEngineFactory());
                        scriptEngine = scriptEngineManager.getEngineByName(DEFAULT_ENGINE_NAME);
                        bindings = scriptEngine.createBindings();
                        log.debug("Setting Rhino as Script Engine");
                    }
                } else {
                    scriptEngine = scriptEngineManager.getEngineByName(NASHORN_ENGINE_NAME);
                    bindings = scriptEngine.createBindings();
                    log.debug("Setting Nashorn as Script Engine");
                    break;
                }
            case GRAALJS:
                try {
                    // Set the system property to disable the warning messages
                    System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
                    scriptEngine = new ScriptEngineManager().getEngineByName(DataMapperEngineConstants.GRAALJS_ENGINE_NAME);
                    bindings = scriptEngine.createBindings();
                    log.debug("Setting Graal.js as Script Engine");
                } catch (Exception e) {
                    log.warn("Could not find Graal.js jar in the lib, switching to rhino js. This may cause performance issue ");
                    log.debug(e);
                    scriptEngineManager.registerEngineName(DEFAULT_ENGINE_NAME, new RhinoScriptEngineFactory());
                    scriptEngine = scriptEngineManager.getEngineByName(DEFAULT_ENGINE_NAME);
                    bindings = scriptEngine.createBindings();
                    log.debug("Setting Rhino as Script Engine");
                }
                break;
            case RHINO:
                scriptEngineManager.registerEngineName(DEFAULT_ENGINE_NAME, new RhinoScriptEngineFactory());
                scriptEngine = scriptEngineManager.getEngineByName(DEFAULT_ENGINE_NAME);
                bindings = scriptEngine.createBindings();
                log.debug("Setting Rhino as Script Engine");
                break;
            default:
                scriptEngineManager.registerEngineName(DEFAULT_ENGINE_NAME, new RhinoScriptEngineFactory());
                scriptEngine = scriptEngineManager.getEngineByName(DEFAULT_ENGINE_NAME);
                bindings = scriptEngine.createBindings();
                log.debug("Setting default Rhino as Script Engine");
                break;
        }
    }

    @Override
    public Model execute(MappingResource mappingResource, String inputVariable, String properties)
            throws JSException, SchemaException {
        try {
            // Compile Data-mapper JS function binding helper
            String helperJSFunction = "var " + PROPERTIES_OBJECT_NAME + " = JSON.parse(" + PROPERTIES_IDENTIFIER + ");\n" +
                    "var " + getInputVariable(mappingResource.getInputSchema().getName()) + " = JSON.parse(" + INPUT_VARIABLE_IDENTIFIER + ");\n";

            bindings.put(PROPERTIES_IDENTIFIER, properties);
            bindings.put(INPUT_VARIABLE_IDENTIFIER, inputVariable);

            // Get the updated function
            JSFunction jsFunction = mappingResource.getFunction();

            if (jsFunction.getBindingHelperFunction() != null) {
                jsFunction.getBindingHelperFunction().eval(bindings);
            } else {
                scriptEngine.eval(helperJSFunction, bindings);
            }
            if (jsFunction.getCompiledBody() != null) {
                jsFunction.getCompiledBody().eval(bindings);
            } else {
                scriptEngine.eval(jsFunction.getFunctionBody(), bindings);
            }
            Object result;
            if (jsFunction.getCompiledName() != null) {
                result = jsFunction.getCompiledName().eval(bindings);
            } else {
                result = scriptEngine.eval(jsFunction.getFunctionName(), bindings);
            }
            if (result instanceof AbstractList) {
                result = convertListToMap((AbstractList) result);
            }
            if (result instanceof Map) {
                return new MapModel((Map<String, Object>) result);
            } else if (result instanceof String) {
                // Fix: https://github.com/wso2/product-ei/issues/5290
                result = ((String) result).replace(ENCODE_CHAR_HYPHEN, HYPHEN);
                return new StringModel((String) result);
            } else if (result != null && result.getClass().toString()
                    .contains(MapOutputFormatter.RHINO_NATIVE_ARRAY_FULL_QUALIFIED_CLASS_NAME)) {
                return new MapModel(DataMapperEngineUtils.getMapFromNativeArray(result));
            }
        } catch (ScriptException e) {
            throw new JSException("Script engine unable to execute the script " + e);
        }
        throw new JSException("Failed to execute mapping function");
    }

    private String getInputVariable(String inputSchemaName) throws ScriptException {
        return "input" + inputSchemaName.replace(':', '_').replace('=', '_').replace(',', '_').replace(HYPHEN, ENCODE_CHAR_HYPHEN);
    }

    private Map convertListToMap(AbstractList polyglotList) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < polyglotList.size(); i++) {
            map.put(i, polyglotList.get(i));
        }
        return map;
    }
}
