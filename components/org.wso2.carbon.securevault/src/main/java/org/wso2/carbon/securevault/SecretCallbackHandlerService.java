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
package org.wso2.carbon.securevault;

import org.wso2.securevault.secret.SecretCallbackHandler;

/**
 * Expose <code>SecretCallbackHandler</code> as a service
 */
public interface SecretCallbackHandlerService {

    /**
     * Returns the global secret call handler
     *
     * @return An instance of <code>SecretCallbackHandler</code>
     */
    SecretCallbackHandler getSecretCallbackHandler();

    /**
     * Register the global secret call handler
     *
     * @param secretCallbackHandler an instance of <code>SecretCallbackHandler</code>
     */
    void setSecretCallbackHandler(SecretCallbackHandler secretCallbackHandler);
}
