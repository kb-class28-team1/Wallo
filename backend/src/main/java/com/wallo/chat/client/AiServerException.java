package com.wallo.chat.client;

public class AiServerException extends RuntimeException {
    public enum FailureReason {
        AI_NOT_CONFIGURED,
        AI_UNAVAILABLE,
        AI_TIMEOUT,
        AI_UPSTREAM_ERROR,
        AI_INVALID_REQUEST,
        AI_INVALID_RESPONSE,
        UNKNOWN
    }

    private final FailureReason failureReason;
    private final boolean retryable;
    private final Integer statusCode;

    public AiServerException(String message) {
        this(message, FailureReason.UNKNOWN, false, null, null);
    }

    public AiServerException(String message, Throwable cause) {
        this(message, FailureReason.UNKNOWN, false, null, cause);
    }

    public AiServerException(
            String message,
            FailureReason failureReason,
            boolean retryable,
            Integer statusCode
    ) {
        this(message, failureReason, retryable, statusCode, null);
    }

    public AiServerException(
            String message,
            FailureReason failureReason,
            boolean retryable,
            Integer statusCode,
            Throwable cause
    ) {
        super(message, cause);
        this.failureReason = failureReason == null ? FailureReason.UNKNOWN : failureReason;
        this.retryable = retryable;
        this.statusCode = statusCode;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
