package org.wso2.micro.integrator.security.handler.oauth;

/**
 * Exception thrown when an error occurs during the token revocation check process.
 * This is used to distinguish between a "successfully checked but revoked" status
 * and a "failed to perform the check" system error.
 */
public class RevocationCheckException extends Exception {

    /**
     * Constructs a new exception with a specified detail message.
     * @param message the detail message.
     */
    public RevocationCheckException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with a specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause of the exception (e.g., a SQLException or CacheException).
     */
    public RevocationCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
