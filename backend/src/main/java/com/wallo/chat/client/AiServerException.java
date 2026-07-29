package com.wallo.chat.client;

public class AiServerException extends RuntimeException {
    public AiServerException(String message) {
        super(message);
    }

    public AiServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
