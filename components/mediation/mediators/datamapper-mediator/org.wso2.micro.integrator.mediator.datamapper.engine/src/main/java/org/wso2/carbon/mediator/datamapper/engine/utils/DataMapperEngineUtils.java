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

import org.wso2.carbon.mediator.datamapper.engine.core.exceptions.JSException;
import org.wso2.carbon.mediator.datamapper.engine.output.formatters.MapOutputFormatter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class DataMapperEngineUtils {

    public static Map<String, Object> getMapFromNativeArray(Object value) throws JSException {
        try {
            final Class<?> cls = Class.forName(MapOutputFormatter.RHINO_NATIVE_ARRAY_FULL_QUALIFIED_CLASS_NAME);
            if (cls.isAssignableFrom(value.getClass())) {
                Map<String, Object> tempValue = new HashMap();
                final Method getIds = cls.getMethod("getIds");
                Method get = null;
                Method[] allMethods = cls.getDeclaredMethods();
                for (Method method : allMethods) {
                    String methodName = method.getName();
                    // find the method with name get
                    if ("get".equals(methodName)) {
                        Type[] pType = method.getGenericParameterTypes();
                        //find the get method with two parameters and first one a int
                        if ((pType.length == 2) && ((Class) pType[0]).getName().equals("int")) {
                            get = method;
                            break;
                        }
                    }
                }
                final Object[] result = (Object[]) getIds.invoke(value);
                for (Object id : result) {
                    Object childValue = get.invoke(value, id, value);
                    tempValue.put(id.toString(), childValue);
                }
                return tempValue;
            }
            throw new JSException(
                    "Un-assignable class found for sun.org.mozilla.javascript.internal.NativeArray as :" + cls
                            .toString());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new JSException("Error while parsing rhino native array values",e);
        }
    }
}
