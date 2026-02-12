package org.wso2.carbon.connector.core.util;

import org.apache.commons.lang.StringUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.mediators.template.TemplateContext;
import org.wso2.carbon.connector.core.ConnectException;
import org.wso2.carbon.connector.core.pool.Configuration;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * Utility methods for connectors.
 *
 * @deprecated This class is deprecated. Use the new connector core module available at
 * <a href="https://github.com/wso2-extensions/mi-connector-core">https://github.com/wso2-extensions/mi-connector-core</a>
 */
@Deprecated
public class ConnectorUtils {

    public static Object lookupTemplateParamater(MessageContext ctxt, String paramName) {
        Stack<TemplateContext> funcStack = (Stack) ctxt.getProperty(SynapseConstants.SYNAPSE__FUNCTION__STACK);
        TemplateContext currentFuncHolder = funcStack.peek();
        Object paramValue =  currentFuncHolder.getParameterValue(paramName);
        return paramValue;
    }

    public static Object lookupTemplateParamater(MessageContext ctxt, int index) {
        Stack<TemplateContext> funcStack = (Stack) ctxt.getProperty(SynapseConstants.SYNAPSE__FUNCTION__STACK);
        TemplateContext currentFuncHolder = funcStack.peek();
        Collection paramList = currentFuncHolder.getParameters();
        Iterator it = paramList.iterator();
        int i = 0;
        while (it.hasNext()) {
            String param = (String) it.next();
            if (i == index) {
                return param;
            }
            i++;
        }
        return null;
    }

    /**
     * Get pool configuration from template parameters
     *
     * @param messageContext Message Context
     * @return Pool Configuration
     */
    public static Configuration getPoolConfiguration(MessageContext messageContext) {

        Configuration configuration = new Configuration();
        String maxActiveConnections = (String) lookupTemplateParamater(messageContext,
                Constants.MAX_ACTIVE_CONNECTIONS);
        String maxIdleConnections = (String) lookupTemplateParamater(messageContext,
                Constants.MAX_IDLE_CONNECTIONS);
        String maxWaitTime = (String) lookupTemplateParamater(messageContext,
                Constants.MAX_WAIT_TIME);
        String minEvictionTime = (String) lookupTemplateParamater(messageContext,
                Constants.MAX_EVICTION_TIME);
        String evictionCheckInterval = (String) lookupTemplateParamater(messageContext,
                Constants.EVICTION_CHECK_INTERVAL);
        String exhaustedAction = (String) lookupTemplateParamater(messageContext,
                Constants.EXHAUSTED_ACTION);

        if (!StringUtils.isEmpty(maxActiveConnections)) {
            configuration.setMaxActiveConnections(Integer.parseInt(maxActiveConnections));
        }
        if (!StringUtils.isEmpty(maxWaitTime)) {
            configuration.setMaxWaitTime(Long.parseLong(maxWaitTime));
        }
        if (!StringUtils.isEmpty(maxIdleConnections)) {
            configuration.setMaxIdleConnections(Integer.parseInt(maxIdleConnections));
        }
        if (!StringUtils.isEmpty(minEvictionTime)) {
            configuration.setMinEvictionTime(Long.parseLong(minEvictionTime));
        }
        if (!StringUtils.isEmpty(evictionCheckInterval)) {
            configuration.setEvictionCheckInterval(Long.parseLong(evictionCheckInterval));
        }
        if (!StringUtils.isEmpty(exhaustedAction)) {
            configuration.setExhaustedAction(exhaustedAction);
        }
        return configuration;
    }

    public static String sendPost(String postData, Charset charset, MessageContext messageContext) throws IOException,
            ConnectException {

        String PROPERTY_PREFIX = "uri.var.";
        byte[] byteData = postData.getBytes(charset);
        String tokenEndpoint = messageContext.getProperty(PROPERTY_PREFIX + "tokenEndpointUrl").toString();
        URL tokenEndpointUrl = new URL(tokenEndpoint);
        HttpURLConnection connection = (HttpURLConnection) tokenEndpointUrl.openConnection();
        try {
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(byteData);
            }
            if (Pattern.matches("4[0-9][0-9]", String.valueOf(connection.getResponseCode()))) {
                throw new ConnectException("Access token generation call returned HTTP Status code " +
                        connection.getResponseCode() + ". " +
                        connection.getResponseMessage());
            }
            if (connection.getResponseMessage() == null) {
                throw new ConnectException("Empty response received for access token generation call");
            } else {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder content;
                    String line;
                    content = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        content.append(line);
                        content.append(System.lineSeparator());
                    }
                    return content.toString();
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
