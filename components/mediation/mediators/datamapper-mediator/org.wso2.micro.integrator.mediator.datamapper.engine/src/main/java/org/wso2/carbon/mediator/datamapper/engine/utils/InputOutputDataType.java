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

package org.wso2.carbon.mediator.datamapper.engine.utils;

public enum InputOutputDataType {

    CSV("CSV"),
    XML("XML"),
    JSON("JSON");

    private String dataTypeValue;

    InputOutputDataType(String dataTypeValue) {
        this.dataTypeValue = dataTypeValue;
    }

    // Use to get the DataType from the relevant input and output data type
    public static InputOutputDataType fromString(String dataType) {
        if (dataType != null) {
            for (InputOutputDataType definedTypes : InputOutputDataType.values()) {
                if (dataType.equalsIgnoreCase(definedTypes.toString())) {
                    return definedTypes;
                }
            }
        }
        throw new IllegalArgumentException("Invalid input type found : " + dataType);
    }
}
