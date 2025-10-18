/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.inbound.endpoint.protocol;

import org.apache.axis2.util.GracefulShutdownTimer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;

public class Utils {

    private static final Log log = LogFactory.getLog(Utils.class);

    /**
     * Waits for the completion of all in-flight messages during a graceful shutdown.
     * The method blocks until either all in-flight messages are processed or the graceful
     * shutdown timer expires, whichever comes first. This ensures that message processing
     * is completed as much as possible before shutting down the consumer. To ensure the
     * waiting loop doesn't run indefinitely due to unexpected conditions, a fallback
     * check is also introduced.
     *
     * @param gracefulShutdownTimer the {@link GracefulShutdownTimer} instance controlling the shutdown timeout
     */
    public static void waitForGracefulTaskCompletion(GracefulShutdownTimer gracefulShutdownTimer,
                                                     AtomicInteger inFlightMessages, String inboundEndpointName,
                                                     long unDeploymentWaitTimeout) {

        log.info("Waiting for in-flight messages in inbound endpoint: " + inboundEndpointName
                + " to finish before shutdown.");

        long startTimeMillis = System.currentTimeMillis();
        long timeoutMillis = gracefulShutdownTimer.getShutdownTimeoutMillis();

        // If the server is shutting down, we wait until either all in-flight messages are done
        // or the graceful shutdown timer expires (whichever comes first)
        while (inFlightMessages.get() > 0 && !gracefulShutdownTimer.isExpired()) {
            try {
                Thread.sleep(unDeploymentWaitTimeout); // wait until all in-flight messages are done
            } catch (InterruptedException e) {}

            // Safety check: Ensure the loop doesn't run indefinitely due to unexpected conditions.
            // This fallback check ensures that if the timer somehow fails to expire as expected,
            // the loop can still exit gracefully once the configured timeout period has elapsed.
            if ((System.currentTimeMillis() - startTimeMillis) >= timeoutMillis) {
                log.warn("Graceful shutdown timer elapsed. Exiting waiting loop to prevent "
                        + "indefinite blocking.");
                break;
            }
        }
    }

    public static boolean checkMethodImplementation(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        if (log.isDebugEnabled()) {
            log.debug("Checking method implementation for: " + methodName + " in class: " + clazz.getName());
        }
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            boolean isImplemented = !Modifier.isAbstract(method.getModifiers());
            if (log.isDebugEnabled()) {
                log.debug("Method " + methodName + " implementation status: " + isImplemented);
            }
            return isImplemented;
        } catch (NoSuchMethodException e) {
            if (log.isDebugEnabled()) {
                log.debug("Method " + methodName + " not found in class " + clazz.getName(), e);
            }
            return false;
        }
    }
}
