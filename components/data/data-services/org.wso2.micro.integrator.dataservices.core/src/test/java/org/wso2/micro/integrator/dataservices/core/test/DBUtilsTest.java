/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
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

package org.wso2.micro.integrator.dataservices.core.test;

import junit.framework.TestCase;
import org.wso2.micro.integrator.dataservices.core.DBUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

public class DBUtilsTest extends TestCase {

    public void testUnwrapWithInvocationTargetException() {
        Exception cause = new Exception("Root cause");
        InvocationTargetException ite = new InvocationTargetException(cause);
        Throwable result = DBUtils.unwrap(ite);
        assertEquals(cause, result);
    }

    public void testUnwrapWithUndeclaredThrowableException() {
        Exception cause = new Exception("Root cause");
        UndeclaredThrowableException ute = new UndeclaredThrowableException(cause);
        Throwable result = DBUtils.unwrap(ute);
        assertEquals(cause, result);
    }

    public void testUnwrapWithNormalException() {
        Exception ex = new Exception("Normal");
        Throwable result = DBUtils.unwrap(ex);
        assertEquals(ex, result);
    }

    public void testUnwrapWithNestedExceptions() {
        Exception cause = new Exception("Root cause");
        InvocationTargetException ite = new InvocationTargetException(cause);
        UndeclaredThrowableException ute = new UndeclaredThrowableException(ite);
        Throwable result = DBUtils.unwrap(ute);
        assertEquals(cause, result);
    }
}
