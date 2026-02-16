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

package org.wso2.micro.integrator.security.handler.oauth.jwt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds jwt validation related information.
 */
public class JWTValidationInfo implements Serializable {

    private String user;
    private String issuer;
    private long expiryTime;
    private long issuedTime;
    private String consumerKey;
    private boolean valid;
    private List<String> scopes = new ArrayList<>();
    private Map<String, Object> claims = new HashMap<>();
    private String jti;
    private int validationCode;
    private String rawPayload;
    private String keyManager;
    private Boolean isAppToken;
    private boolean isExpired = false;

    public JWTValidationInfo() {

    }

    public JWTValidationInfo(JWTValidationInfo jwtValidationInfo) {

        this.user = jwtValidationInfo.getUser();
        this.issuer = jwtValidationInfo.getIssuer();
        this.expiryTime = jwtValidationInfo.getExpiryTime();
        this.consumerKey = jwtValidationInfo.getConsumerKey();
        this.valid = jwtValidationInfo.isValid();
        this.scopes = jwtValidationInfo.getScopes();
        this.claims = jwtValidationInfo.getClaims();
        this.jti = jwtValidationInfo.getJti();
        this.validationCode = jwtValidationInfo.getValidationCode();
        this.rawPayload = jwtValidationInfo.getRawPayload();
        this.keyManager = jwtValidationInfo.getKeyManager();
        this.isAppToken = jwtValidationInfo.getAppToken();
    }

    public Boolean getAppToken() {

        return isAppToken;
    }

    public void setAppToken(Boolean appToken) {

        isAppToken = appToken;
    }

    public String getUser() {

        return user;
    }

    public void setUser(String user) {

        this.user = user;
    }

    public String getIssuer() {

        return issuer;
    }

    public void setIssuer(String issuer) {

        this.issuer = issuer;
    }

    public long getExpiryTime() {

        return expiryTime;
    }

    public void setExpiryTime(long expiryTime) {

        this.expiryTime = expiryTime;
    }

    public long getIssuedTime() {

        return issuedTime;
    }

    public void setIssuedTime(long issuedTime) {

        this.issuedTime = issuedTime;
    }

    public boolean isValid() {

        return valid;
    }

    public void setValid(boolean valid) {

        this.valid = valid;
    }

    public List<String> getScopes() {

        return scopes;
    }

    public void setScopes(List<String> scopes) {

        this.scopes = scopes;
    }

    public Map<String, Object> getClaims() {

        return claims;
    }

    public void setClaims(Map<String, Object> claims) {

        this.claims = claims;
    }

    public String getJti() {

        return jti;
    }

    public void setJti(String jti) {

        this.jti = jti;
    }

    public String getConsumerKey() {

        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {

        this.consumerKey = consumerKey;
    }

    public int getValidationCode() {

        return validationCode;
    }

    public void setValidationCode(int validationCode) {

        this.validationCode = validationCode;
    }

    public String getRawPayload() {

        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {

        this.rawPayload = rawPayload;
    }

    public String getKeyManager() {

        return keyManager;
    }

    public void setKeyManager(String keyManager) {

        this.keyManager = keyManager;
    }

    public boolean isExpired() {
        return isExpired;
    }

    public void setExpired(boolean expired) {
        isExpired = expired;
    }
}
