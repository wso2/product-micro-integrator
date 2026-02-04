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
package org.wso2.carbon.mediator.datamapper.engine.core.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.JSException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.ReaderException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.WriterException;
import org.wso2.carbon.mediator.datamapper.engine.core.executors.Executor;
import org.wso2.carbon.mediator.datamapper.engine.core.executors.ScriptExecutorFactory;
import org.wso2.carbon.mediator.datamapper.engine.core.models.Model;
import org.wso2.carbon.mediator.datamapper.engine.core.notifiers.InputVariableNotifier;
import org.wso2.carbon.mediator.datamapper.engine.core.notifiers.OutputVariableNotifier;
import org.wso2.carbon.mediator.datamapper.engine.input.InputBuilder;
import org.wso2.carbon.mediator.datamapper.engine.output.OutputMessageBuilder;
import org.wso2.carbon.mediator.datamapper.engine.utils.InputOutputDataType;
import org.wso2.carbon.mediator.datamapper.engine.utils.ModelType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class MappingHandler implements InputVariableNotifier, OutputVariableNotifier {

    private String dmExecutorPoolSize;
    private String inputVariable;
    private String outputVariable;
    private MappingResource mappingResource;
    private OutputMessageBuilder outputMessageBuilder;
    private Executor scriptExecutor;
    private InputBuilder inputBuilder;
    private String propertiesInJSON;

    public MappingHandler(MappingResource mappingResource, String inputType, String outputType,
            String dmExecutorPoolSize) throws IOException, SchemaException, WriterException {

        this.inputBuilder = new InputBuilder(InputOutputDataType.fromString(inputType),
                mappingResource.getInputSchema());

        this.outputMessageBuilder = new OutputMessageBuilder(InputOutputDataType.fromString(outputType),
                ModelType.JAVA_MAP, mappingResource.getOutputSchema());

        this.dmExecutorPoolSize = dmExecutorPoolSize;
        this.mappingResource = mappingResource;
    }

    /**
     * This method performs the mapping from input message to the specified output schema.
     * <p>
     *  Input message should be passed as an InputStream and any runtime properties can be passed as a Map.
     *  At the top level of the Properties map, it contains scope name (String) and an individual map for each scope.
     *  In the next level, a map in a single scope should contain name, value pairs for each property name and its
     *  value.
     * </p>
     * <p>
     *  Map of maps will be converted to a JSON object to be injected to the JavaScript processing engine.
     * </p>
     *
     * @param inputMsg  Input message as an InputStream
     * @param propertiesMap Map of maps, single map for each scope
     * @return  Output message created according to the provided OutputSchema using the runtime arguments
     * @throws ReaderException
     * @throws InterruptedException
     * @throws IOException
     * @throws SchemaException
     * @throws JSException
     */
	public String doMap(InputStream inputMsg, Map<String, Map<String, Object>> propertiesMap)
			throws ReaderException, InterruptedException, IOException, SchemaException, JSException {
		ReaderException readerException = null;
		try {
			this.scriptExecutor = ScriptExecutorFactory.getScriptExecutor(dmExecutorPoolSize);
			this.propertiesInJSON = propertiesMapToJSON(propertiesMap);
			inputBuilder.buildInputModel(inputMsg, this);
		} catch (ReaderException re) {
			readerException = re;
			throw re;
		} finally {
			// Fix for https://github.com/wso2/product-ei/issues/650
			if (scriptExecutor != null) {
				try {
					releaseExecutor();
				} catch (InterruptedException ie) {
					if (readerException != null) {
						ie.initCause(readerException);
					}
					throw ie;
				}
			}
		}
		return outputVariable;
	}

    @Override
    public void notifyInputVariable(Object variable) throws SchemaException, JSException, ReaderException {
        this.inputVariable = (String) variable;
        Model outputModel = scriptExecutor.execute(mappingResource, inputVariable, propertiesInJSON);
        try {
            releaseExecutor();
            if (outputModel.getModel() instanceof Map) {
                outputMessageBuilder.buildOutputMessage(outputModel, this);
            } else {
                notifyOutputVariable(outputModel.getModel());
            }

        } catch (InterruptedException | WriterException e) {
            throw new ReaderException(e.getMessage());
        }
    }

    private void releaseExecutor() throws InterruptedException {
        ScriptExecutorFactory.releaseScriptExecutor(scriptExecutor);
        this.scriptExecutor = null;
    }

    @Override
    public void notifyOutputVariable(Object variable) {
        outputVariable = (String) variable;
    }

    /**
     * Convert the properties map to a JSON String
     * @param propertiesMap
     * @return JSON String
     * @throws ReaderException
     */
    private String propertiesMapToJSON(Map<String, Map<String, Object>> propertiesMap) throws ReaderException {
        ObjectMapper mapperObj = new ObjectMapper();
        String propertiesInJSON = null;

        try {
            propertiesInJSON = mapperObj.writeValueAsString(propertiesMap);
        } catch (JsonProcessingException e) {
            throw new ReaderException("Error while parsing the input properties. " + e.getMessage());
        }
        return propertiesInJSON;
    }

}
