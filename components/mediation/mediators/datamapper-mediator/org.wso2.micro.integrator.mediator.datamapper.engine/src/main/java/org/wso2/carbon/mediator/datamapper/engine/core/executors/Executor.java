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

import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.JSException;
import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.SchemaException;
import org.wso2.carbon.mediator.datamapper.engine.core.mapper.MappingResource;
import org.wso2.carbon.mediator.datamapper.engine.core.models.Model;

/**
 * This interface should be implemented by script executors of Data Mapper Engine
 */
public interface Executor {

    /**
     * Method to execute the mapping config in the {@link MappingResource} on
     * input variable and returns the output model
     *
     * @param mappingResource mapping resource model
     * @param inputVariable   input variable
     * @return model output model
     * @throws JSException if mapping throws an exception
     */
    public Model execute(MappingResource mappingResource, String inputVariable, String properties)
            throws JSException, SchemaException;
}
