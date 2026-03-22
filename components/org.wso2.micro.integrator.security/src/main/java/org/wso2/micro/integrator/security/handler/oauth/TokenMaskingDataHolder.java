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

import org.wso2.config.mapper.ConfigParser;

public class TokenMaskingDataHolder {

    private static int tokenMaxLength = 36;
    private static int tokenMaxVisibleLength = 0;
    private static int tokenMinVisibleLengthRatio = 5;
    private static String tokenMaskChar = "X";

    private TokenMaskingDataHolder() {
    }

    private static volatile TokenMaskingDataHolder instance;

    /**
     * Returns the singleton instance of {@code TokenMaskingDataHolder}.
     * <p>
     * This method lazily initializes the singleton using double-checked locking to
     * ensure thread-safe creation. When the instance is first created it reads
     * token masking-related configuration values from {@link org.wso2.config.mapper.ConfigParser}
     * (via {@link OAuthConstants}) and populates internal defaults accordingly.
     * Subsequent calls return the already-initialized instance.
     * <p>
     *
     * @return the singleton {@code TokenMaskingDataHolder} instance
     */
    public static TokenMaskingDataHolder getInstance() {
        if (instance == null) {
            synchronized (TokenMaskingDataHolder.class) {
                if (instance == null) {
                    instance = new TokenMaskingDataHolder();

                    Object tokenMaxLen = ConfigParser.getParsedConfigs()
                            .get(OAuthConstants.TOKEN_MAX_LEN);
                    if (tokenMaxLen != null) {
                        tokenMaxLength = Integer.parseInt(tokenMaxLen.toString());
                    }

                    Object tokenMaxVisibleLen = ConfigParser.getParsedConfigs()
                            .get(OAuthConstants.TOKEN_MAX_VISIBLE_LEN);
                    if (tokenMaxVisibleLen != null) {
                        tokenMaxVisibleLength = Integer.parseInt(tokenMaxVisibleLen.toString());
                    }

                    Object tokenMinVisibleLenRatio = ConfigParser.getParsedConfigs()
                            .get(OAuthConstants.TOKEN_MIN_VISIBLE_LEN_RATIO);
                    if (tokenMinVisibleLenRatio != null) {
                        tokenMinVisibleLengthRatio = Integer.parseInt(tokenMinVisibleLenRatio.toString());
                    }

                    Object tokenMaskCharObj = ConfigParser.getParsedConfigs()
                            .get(OAuthConstants.TOKEN_MASK_CHAR);
                    if (tokenMaskCharObj != null) {
                        tokenMaskChar = tokenMaskCharObj.toString();
                    }
                }
            }
        }
        return instance;
    }


    public int getTokenMaxLength() { return tokenMaxLength; }

    public int getTokenMaxVisibleLength() { return tokenMaxVisibleLength; }

    public int getTokenMinVisibleLengthRatio() { return tokenMinVisibleLengthRatio; }

    public String getTokenMaskChar() { return tokenMaskChar; }

}
