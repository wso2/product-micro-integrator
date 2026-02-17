/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.security.handler.oauth;

/**
 * Exception thrown when an error occurs during the token revocation check process.
 * This is used to distinguish between a "successfully checked but revoked" status
 * and a "failed to perform the check" system error.
 */
public class RevocationCheckException extends Exception {

    /**
     * Constructs a new exception with a specified detail message.
     * @param message the detail message.
     */
    public RevocationCheckException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with a specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause of the exception (e.g., a SQLException or CacheException).
     */
    public RevocationCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
