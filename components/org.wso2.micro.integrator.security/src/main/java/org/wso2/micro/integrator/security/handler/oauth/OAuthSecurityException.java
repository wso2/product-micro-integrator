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
 * Represents an API security violation or a system error that may have occurred
 * while validating security requirements.
 */
public class OAuthSecurityException extends Exception {

    private String description;
    private int errorCode;

    public OAuthSecurityException(String message) {
        super(message);
    }

    public OAuthSecurityException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OAuthSecurityException(int errorCode, String message, String description) {
        super(message);
        this.description = description;
        this.errorCode = errorCode;
    }

    public OAuthSecurityException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public OAuthSecurityException(String message, Throwable cause) {
        super(message, cause);
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getDescription() {
        return description;
    }
}
