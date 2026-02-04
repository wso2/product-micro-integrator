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

import org.wso2.securevault.secret.AbstractSecretCallbackHandler;
import org.wso2.securevault.secret.SecretManager;
import org.wso2.securevault.secret.SingleSecretCallback;

/**
 * SecretManager based secret provider , this can be used by other application
 * to get secret form  SecretManager
 */
public class SecretManagerSecretCallbackHandler extends AbstractSecretCallbackHandler {

    private final SecretManager secretManager;

    public SecretManagerSecretCallbackHandler(SecretManager secretManager) {
        this.secretManager = secretManager;
    }

    protected void handleSingleSecretCallback(SingleSecretCallback singleSecretCallback) {

        if (!secretManager.isInitialized()) {
            if (log.isDebugEnabled()) {
                log.debug("SecretManager has not been initialized.Cannot collect secrets");
            }
            return;
        }

        String id = singleSecretCallback.getId();
        if (id != null && !"".equals(id)) {
            singleSecretCallback.setSecret(secretManager.getSecret(id));
        }
    }
}