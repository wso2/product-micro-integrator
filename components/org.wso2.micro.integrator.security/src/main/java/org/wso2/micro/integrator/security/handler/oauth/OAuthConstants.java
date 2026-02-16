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

public class OAuthConstants {

    public static final String JWT_OAUTH_CONFIG = "rest_api.jwt_security_handler.";
    public static final String CACHE_EXPIRY = JWT_OAUTH_CONFIG + "cache_expiry";
    public static final String REMOVE_OAUTH_HEADERS_FROM_OUT_MESSAGE = JWT_OAUTH_CONFIG
            + "remove_oauth_headers_from_out_message";
    public static final String TRUSTED_ISSUERS = JWT_OAUTH_CONFIG + "trusted_issuers";

    public static final String BEARER = "Bearer";
    public static final String DOT = ".";
    public static final String CONSUMER_KEY_SEGMENT_DELIMITER = " ";
    public static final String DESCRIPTION_SEPARATOR = ". ";
    public static final String SCOPE = "scope";
    public static final String SCOPE_DELIMITER = " ";
    public static final String OAUTH2_DEFAULT_SCOPE = "default";

    public static final String SCOPES = "Scopes";
    public static final String JWT_CLAIMS = "jwt_token_claims";

    public static final long DEFAULT_TIMESTAMP_SKEW_IN_SECONDS = 300L;

    public static final int API_AUTH_GENERAL_ERROR       = 900900;
    public static final String API_AUTH_GENERAL_ERROR_MESSAGE = "Unclassified Authentication Failure";

    public static final int API_AUTH_INVALID_CREDENTIALS = 900901;

    public static final String SIGNATURE_VERIFICATION_FAILURE_MESSAGE =
            "Invalid JWT token. Signature verification failed.";

    public static final String API_AUTH_INVALID_CREDENTIALS_MESSAGE = "Invalid Credentials";
    public static final String API_AUTH_INVALID_CREDENTIALS_DESCRIPTION =
            "Make sure you have provided the correct security credentials";

    public static final int API_AUTH_MISSING_CREDENTIALS = 900902;
    public static final String API_AUTH_MISSING_CREDENTIALS_MESSAGE = "Missing Credentials";
    public static final String API_AUTH_MISSING_CREDENTIALS_DESCRIPTION =
            "Make sure your API invocation call has a header: ";

    public static final int API_AUTH_ACCESS_TOKEN_EXPIRED = 900903;
    public static final String API_AUTH_ACCESS_TOKEN_EXPIRED_MESSAGE = "Access Token Expired";
    public static final String API_AUTH_ACCESS_TOKEN_EXPIRED_DESCRIPTION =
            "Renew the access token and try again";

    public static final int API_AUTH_ACCESS_TOKEN_INACTIVE = 900904;
    public static final String API_AUTH_ACCESS_TOKEN_INACTIVE_MESSAGE = "Access Token Inactive";
    public static final String API_AUTH_ACCESS_TOKEN_INACTIVE_DESCRIPTION =
            "Generate a new access token and try again";

    public static final int API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE = 900905;
    public static final String API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE_MESSAGE = "Incorrect Access Token Type is provided";

    public static final int API_AUTH_INCORRECT_API_RESOURCE = 900906;
    public static final String API_AUTH_INCORRECT_API_RESOURCE_MESSAGE = "No matching resource found in the API for the given request";
    public static final String API_AUTH_INCORRECT_API_RESOURCE_DESCRIPTION =
            "Check the API documentation and add a proper REST resource path to the invocation URL";

    public static final int API_BLOCKED = 900907;
    public static final String API_BLOCKED_MESSAGE = "The requested API is temporarily blocked";

    public static final int API_AUTH_FORBIDDEN = 900908;
    public static final String API_AUTH_FORBIDDEN_MESSAGE = "Resource forbidden ";

    public static final int SUBSCRIPTION_INACTIVE = 900909;
    public static final String SUBSCRIPTION_INACTIVE_MESSAGE = "The subscription to the API is inactive";

    public static final int INVALID_SCOPE = 900910;
    public static final String INVALID_SCOPE_MESSAGE = "The access token does not allow you to access the requested resource";

    public static final int API_AUTH_MISSING_OPEN_API_DEF = 900911;
    public static final String API_AUTH_MISSING_OPEN_API_DEF_ERROR_MESSAGE = "Internal Server Error";

    public static final int API_AUTH_ACCESS_TOKEN_CLAIMS_MISMATCH = 900912;
    public static final String API_AUTH_ACCESS_TOKEN_CLAIMS_MISMATCH_MESSAGE = "Claim Mismatch";
    public static final String API_AUTH_ACCESS_TOKEN_CLAIMS_MISMATCH_DESCRIPTION =
            "The claim configured in the system and the claim provided in the token do not align. Please ensure the claims match.";

    public static final int API_AUTH_ACCESS_TOKEN_CLAIMS_INVALID = 900913;
    public static final String API_AUTH_ACCESS_TOKEN_CLAIMS_INVALID_MESSAGE = "Claim Invalid";
    public static final String API_AUTH_ACCESS_TOKEN_CLAIMS_INVALID_DESCRIPTION =
            "The configured resource access validation claim is not present in the token.";

    public static final int API_OAUTH_INVALID_AUDIENCES = 900914;
    public static final String API_OAUTH_INVALID_AUDIENCES_MESSAGE = "Access Denied";
    public static final String API_OAUTH_INVALID_AUDIENCES_DESCRIPTION = "The access token does not allow you to access the requested resource";

    /**
     * returns a String that corresponds to errorCode passed in
     *
     * @param errorCode
     * @return String
     */
    public static final String getAuthenticationFailureMessage(int errorCode) {
        String errorMessage;
        switch (errorCode) {
            case API_AUTH_ACCESS_TOKEN_EXPIRED:
                errorMessage = API_AUTH_ACCESS_TOKEN_EXPIRED_MESSAGE;
                break;
            case API_AUTH_ACCESS_TOKEN_INACTIVE:
                errorMessage = API_AUTH_ACCESS_TOKEN_INACTIVE_MESSAGE;
                break;
            case API_AUTH_GENERAL_ERROR:
                errorMessage = API_AUTH_GENERAL_ERROR_MESSAGE;
                break;
            case API_AUTH_MISSING_OPEN_API_DEF:
                errorMessage = API_AUTH_MISSING_OPEN_API_DEF_ERROR_MESSAGE;
                break;
            case API_AUTH_INVALID_CREDENTIALS:
                errorMessage = API_AUTH_INVALID_CREDENTIALS_MESSAGE;
                break;
            case API_AUTH_MISSING_CREDENTIALS:
                errorMessage = API_AUTH_MISSING_CREDENTIALS_MESSAGE;
                break;
            case API_AUTH_INCORRECT_API_RESOURCE:
                errorMessage = API_AUTH_INCORRECT_API_RESOURCE_MESSAGE;
                break;
            case API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE:
                errorMessage = API_AUTH_INCORRECT_ACCESS_TOKEN_TYPE_MESSAGE;
                break;
            case API_OAUTH_INVALID_AUDIENCES:
                errorMessage = API_OAUTH_INVALID_AUDIENCES_MESSAGE;
                break;
            case API_BLOCKED:
                errorMessage = API_BLOCKED_MESSAGE;
                break;
            case API_AUTH_FORBIDDEN:
                errorMessage = API_AUTH_FORBIDDEN_MESSAGE;
                break;
            case SUBSCRIPTION_INACTIVE:
                errorMessage = SUBSCRIPTION_INACTIVE_MESSAGE;
                break;
            case INVALID_SCOPE:
                errorMessage = INVALID_SCOPE_MESSAGE;
                break;
            case API_AUTH_ACCESS_TOKEN_CLAIMS_MISMATCH:
                errorMessage = API_AUTH_ACCESS_TOKEN_CLAIMS_MISMATCH_MESSAGE;
                break;
            case API_AUTH_ACCESS_TOKEN_CLAIMS_INVALID:
                errorMessage = API_AUTH_ACCESS_TOKEN_CLAIMS_INVALID_MESSAGE;
                break;
            default:
                errorMessage = API_AUTH_GENERAL_ERROR_MESSAGE;
                break;
        }
        return errorMessage;
    }

    /**
     * This method is used to get an additional description for error message details.
     *
     * @param errorCode    The error code that is embedded in the exception
     * @param errorMessage The default error message of the exception
     * @return The error description including the original error message and some additional information
     */
    public static String getFailureMessageDetailDescription(int errorCode, String errorMessage){
        String errorDescription = errorMessage;
        switch (errorCode){
            case API_AUTH_INCORRECT_API_RESOURCE:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_INCORRECT_API_RESOURCE_DESCRIPTION;
                break;
            case API_AUTH_ACCESS_TOKEN_INACTIVE:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_ACCESS_TOKEN_INACTIVE_DESCRIPTION;
                break;
            case API_AUTH_MISSING_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_MISSING_CREDENTIALS_DESCRIPTION;
                break;
            case API_AUTH_ACCESS_TOKEN_EXPIRED:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_ACCESS_TOKEN_EXPIRED_DESCRIPTION;
                break;
            case API_AUTH_INVALID_CREDENTIALS:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_INVALID_CREDENTIALS_DESCRIPTION;
                break;
            case API_AUTH_ACCESS_TOKEN_CLAIMS_MISMATCH:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_ACCESS_TOKEN_CLAIMS_MISMATCH_DESCRIPTION;
                break;
            case API_AUTH_ACCESS_TOKEN_CLAIMS_INVALID:
                errorDescription += DESCRIPTION_SEPARATOR + API_AUTH_ACCESS_TOKEN_CLAIMS_INVALID_DESCRIPTION;
                break;
            case API_OAUTH_INVALID_AUDIENCES:
                errorDescription += DESCRIPTION_SEPARATOR + API_OAUTH_INVALID_AUDIENCES_DESCRIPTION;
                break;
            default:
                // Do nothing since we are anyhow returning the original error description.
        }
        return errorDescription;
    }

}
