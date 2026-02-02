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
package org.wso2.carbon.mediator.datamapper.engine.input.readers.events;

public class ReaderEvent {

    private ReaderEventType eventType;
    private String name;
    private Object value;
    private String fieldType;

    public ReaderEvent(ReaderEventType eventType, String name, Object value, String fieldType) {
        this.eventType = eventType;
        this.name = name;
        this.value = value;
        this.fieldType = fieldType;
    }

    public ReaderEvent(ReaderEventType eventType, String name, Object value) {
        this.eventType = eventType;
        this.name = name;
        this.value = value;
    }

    public ReaderEvent(ReaderEventType eventType, String name) {
        this.eventType = eventType;
        this.name = name;
    }

    public ReaderEvent(ReaderEventType eventType) {
        this.eventType = eventType;
    }

    public void setEventType(ReaderEventType eventType) {
        this.eventType = eventType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public ReaderEventType getEventType() {
        return eventType;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }
}
