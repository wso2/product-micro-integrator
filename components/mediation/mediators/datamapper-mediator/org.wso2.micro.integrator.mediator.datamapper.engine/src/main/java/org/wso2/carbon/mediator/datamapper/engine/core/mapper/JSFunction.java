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

import javax.script.CompiledScript;

/**
 * This class will hold the data mapper mapping configuration
 */
public class JSFunction {

    private String functionName;
    private String functionBody;
    private CompiledScript compiledBody;
    private CompiledScript compiledName;
    private CompiledScript bindingHelperFunction;

    public JSFunction(String name, String body) {
        this.setFunctionName(name);
        this.setFunctionBody(body);
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getFunctionBody() {
        return functionBody;
    }

    public void setFunctionBody(String functionBody) {
        this.functionBody = functionBody;
    }

    public CompiledScript getCompiledName() {
        return compiledName;
    }

    public void setCompiledName(CompiledScript compiledName) {
        this.compiledName = compiledName;
    }

    public CompiledScript getCompiledBody() {
        return compiledBody;
    }

    public void setCompiledBody(CompiledScript compiledBody) {
        this.compiledBody = compiledBody;
    }

    public CompiledScript getBindingHelperFunction() {
        return bindingHelperFunction;
    }

    public void setBindingHelperFunction(CompiledScript bindingHelperFunction) {
        this.bindingHelperFunction = bindingHelperFunction;
    }
}
