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

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;

/**
 * JWT internal Representation
 */
public class SignedJWTInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private transient SignedJWT signedJWT;
    private JWTClaimsSet jwtClaimsSet;
    private ValidationStatus validationStatus = ValidationStatus.NOT_VALIDATED;

    public enum ValidationStatus {
        NOT_VALIDATED, INVALID, VALID
    }

    public SignedJWTInfo(String token, SignedJWT signedJWT, JWTClaimsSet jwtClaimsSet) {

        this.token = token;
        this.signedJWT = signedJWT;
        this.jwtClaimsSet = jwtClaimsSet;
    }

    public SignedJWTInfo() {

    }

    public SignedJWT getSignedJWT() {

        return signedJWT;
    }

    public void setSignedJWT(SignedJWT signedJWT) {

        this.signedJWT = signedJWT;
    }

    public JWTClaimsSet getJwtClaimsSet() {

        return jwtClaimsSet;
    }

    public void setJwtClaimsSet(JWTClaimsSet jwtClaimsSet) {

        this.jwtClaimsSet = jwtClaimsSet;
    }

    public String getToken() {

        return token;
    }

    public void setToken(String token) {

        this.token = token;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

}
