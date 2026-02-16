package org.wso2.micro.integrator.security.handler.oauth;

public class OAuthUtil {

    public static String getMaskedToken(String token) {

        if (token.length() >= 10) {
            return "XXXXX" + token.substring(token.length() - 10);
        } else {
            return "XXXXX" + token.substring(token.length() / 2);
        }
    }

}
