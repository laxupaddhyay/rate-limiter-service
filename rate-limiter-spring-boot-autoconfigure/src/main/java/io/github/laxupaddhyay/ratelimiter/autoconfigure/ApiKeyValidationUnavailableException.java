package io.github.laxupaddhyay.ratelimiter.autoconfigure;

/** Signals that the validator's credential store is temporarily unavailable. */
public class ApiKeyValidationUnavailableException extends RuntimeException {
    public ApiKeyValidationUnavailableException(String message) { super(message); }
    public ApiKeyValidationUnavailableException(String message, Throwable cause) { super(message, cause); }
}
