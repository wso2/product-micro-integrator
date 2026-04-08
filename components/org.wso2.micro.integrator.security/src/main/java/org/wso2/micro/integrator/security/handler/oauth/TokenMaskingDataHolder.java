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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.config.mapper.ConfigParser;

public class TokenMaskingDataHolder {

    private static final Log log = LogFactory.getLog(TokenMaskingDataHolder.class);
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
                    TokenMaskingDataHolder initialized = new TokenMaskingDataHolder();

                    Object tokenMaxLen = ConfigParser.getParsedConfigs().get(OAuthConstants.TOKEN_MAX_LEN);
                    if (tokenMaxLen != null) {
                        if (tokenMaxLen instanceof Number) {
                            int configuredTokenMaxLength = ((Number) tokenMaxLen).intValue();
                            if (configuredTokenMaxLength < 0) {
                                log.warn("Invalid configuration for token_max_len. Expected a non-negative number "
                                        + "but found: " + configuredTokenMaxLength
                                        + ". Using default value: " + tokenMaxLength);
                            } else {
                                tokenMaxLength = configuredTokenMaxLength;
                            }
                        } else {
                            log.warn("Invalid configuration for token_max_len. Expected a number but found: "
                                    + tokenMaxLen.getClass().getName() + ". Using default value: " + tokenMaxLength);
                        }
                    }

                    Object tokenMaxVisibleLen = ConfigParser.getParsedConfigs().get(OAuthConstants.TOKEN_MAX_VISIBLE_LEN);
                    if (tokenMaxVisibleLen != null) {
                        if (tokenMaxVisibleLen instanceof Number) {
                            int configuredTokenMaxVisibleLength = ((Number) tokenMaxVisibleLen).intValue();
                            if (configuredTokenMaxVisibleLength < 0) {
                                log.warn("Invalid configuration for token_max_visible_len. Expected a non-negative number "
                                        + "but found: " + configuredTokenMaxVisibleLength
                                        + ". Using default value: " + tokenMaxVisibleLength);
                            } else {
                                tokenMaxVisibleLength = configuredTokenMaxVisibleLength;
                            }
                        } else {
                            log.warn("Invalid configuration for token_max_visible_len. Expected a number but found: "
                                    + tokenMaxVisibleLen.getClass().getName() + ". Using default value: "
                                    + tokenMaxVisibleLength);
                        }
                    }

                    Object tokenMinVisibleLenRatio = ConfigParser.getParsedConfigs()
                            .get(OAuthConstants.TOKEN_MIN_VISIBLE_LEN_RATIO);
                    if (tokenMinVisibleLenRatio != null) {
                        if (tokenMinVisibleLenRatio instanceof Number) {
                            int configuredTokenMinVisibleLenRatio = ((Number) tokenMinVisibleLenRatio).intValue();
                            if (configuredTokenMinVisibleLenRatio < 0) {
                                log.warn("Invalid configuration for token_min_visible_len_ratio. Expected a non-negative number "
                                        + "but found: " + configuredTokenMinVisibleLenRatio
                                        + ". Using default value: " + tokenMinVisibleLengthRatio);
                            } else {
                                tokenMinVisibleLengthRatio = configuredTokenMinVisibleLenRatio;
                            }
                        } else {
                            log.warn("Invalid configuration for token_min_visible_len_ratio. Expected a number "
                                    + "but found: " + tokenMinVisibleLenRatio.getClass().getName()
                                    + ". Using default value: " + tokenMinVisibleLengthRatio);
                        }
                    }

                    Object tokenMaskCharObj = ConfigParser.getParsedConfigs().get(OAuthConstants.TOKEN_MASK_CHAR);
                    if (tokenMaskCharObj != null) {
                        String tmpTokenMaskChar = tokenMaskCharObj.toString();
                        if (tmpTokenMaskChar.isEmpty()) {
                            log.warn("Invalid configuration for token_mask_char. Expected a single character but found: "
                                    + tmpTokenMaskChar + ". Using default value: " + tokenMaskChar);
                        } else {
                            tokenMaskChar = tmpTokenMaskChar;
                        }
                    }
                    instance = initialized;
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
