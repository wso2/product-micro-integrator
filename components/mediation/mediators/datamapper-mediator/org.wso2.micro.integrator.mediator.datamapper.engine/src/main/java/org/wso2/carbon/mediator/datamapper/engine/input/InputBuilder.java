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
package org.wso2.carbon.mediator.datamapper.engine.input;

import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.JSException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.ReaderException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.notifiers.InputVariableNotifier;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.Schema;
import org.wso2.carbon.mediator.datamapper.engine.input.readers.InputReader;
import org.wso2.carbon.mediator.datamapper.engine.input.readers.InputReaderFactory;
import org.wso2.carbon.mediator.datamapper.engine.utils.InputOutputDataType;

import java.io.IOException;
import java.io.InputStream;

/**
 * Class manage the XML to JSON parsing process
 */
public class InputBuilder {

    private InputReader inputReader;
    private Schema inputSchema;
    private InputVariableNotifier inputVariableNotifier;

    /**
     * Constructor
     *
     * @param inputSchema Input message JSON schema
     * @throws IOException
     */
    public InputBuilder(InputOutputDataType inputType, Schema inputSchema) throws IOException {
        this.inputReader = InputReaderFactory.getReader(inputType);
        this.inputSchema = inputSchema;
    }

    /**
     * @param inputStream           XML input message
     * @param inputVariableNotifier Reference to the MappingHandler instance
     * @throws ReaderException
     */
    public void buildInputModel(InputStream inputStream, InputVariableNotifier inputVariableNotifier)
            throws ReaderException {
        this.inputVariableNotifier = inputVariableNotifier;
        inputReader.read(inputStream, inputSchema, this);
    }

    /**
     * This method will be called by the XMLInputReader instance to notify with the output
     *
     * @param builtMessage Built JSON message
     * @throws JSException
     * @throws ReaderException
     * @throws SchemaException
     */
    public void notifyWithResult(String builtMessage) throws JSException, ReaderException, SchemaException {
        inputVariableNotifier.notifyInputVariable(builtMessage);
    }

}
