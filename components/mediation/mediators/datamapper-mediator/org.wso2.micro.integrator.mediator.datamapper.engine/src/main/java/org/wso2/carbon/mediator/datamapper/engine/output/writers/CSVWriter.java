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
package org.wso2.carbon.mediator.datamapper.engine.output.writers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.WriterException;
import org.wso2.carbon.mediator.datamapper.engine.core.schemas.Schema;

/**
 * This class implements {@link Writer} interface and CSV writer for data mapper engine
 */
public class CSVWriter implements Writer {

    private static final Log log = LogFactory.getLog(CSVWriter.class);
    private Schema outputSchema;
    private StringBuilder csvOutputMessage;
    private boolean isStartingObject;

    public CSVWriter(Schema outputSchema) throws SchemaException, WriterException {
        this.outputSchema = outputSchema;
        this.csvOutputMessage = new StringBuilder();
        this.isStartingObject = true;
    }

    @Override
    public void writeField(String name, Object value) throws WriterException {
        if (!isStartingObject) {
            csvOutputMessage.append(",");
        } else {
            isStartingObject = false;
        }
        csvOutputMessage.append(value);
    }

    @Override
    public void writeStartAnonymousObject() throws WriterException {
        csvOutputMessage.append(System.getProperty("line.separator"));
        isStartingObject = true;
    }

    @Override
    public String terminateMessageBuilding() throws WriterException {
        return csvOutputMessage.toString();
    }

    @Override
    public void writePrimitive(Object value) throws WriterException {
    }

    @Override
    public void writeStartObject(String name) throws WriterException {
    }

    @Override
    public void writeEndObject(String objectName) throws WriterException {
    }

    @Override
    public void writeStartArray() {
    }

    @Override
    public void writeEndArray() throws WriterException {
    }
}
