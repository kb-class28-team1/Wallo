package com.wallo.external;

/** Common exception raised after a CODEF request cannot be completed. */
public class CodefSyncException extends RuntimeException {

    private final String operation;
    private final String code;
    private final int attempts;

    public CodefSyncException(
            String operation,
            String code,
            int attempts,
            String message
    ) {
        this(operation, code, attempts, message, null);
    }

    public CodefSyncException(
            String operation,
            String code,
            int attempts,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.operation = operation;
        this.code = code;
        this.attempts = attempts;
    }

    public String getOperation() {
        return operation;
    }

    public String getCode() {
        return code;
    }

    public int getAttempts() {
        return attempts;
    }
}
