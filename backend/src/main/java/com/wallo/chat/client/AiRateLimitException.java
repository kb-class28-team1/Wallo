package com.wallo.chat.client;

public class AiRateLimitException extends AiServerException {
    public AiRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
