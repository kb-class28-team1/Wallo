package com.wallo.common;

import com.wallo.chat.client.AiServerException;
import com.wallo.chat.client.AiRateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(AiRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleAiRateLimit(
            AiRateLimitException exception
    ) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(AiServerException.class)
    public ResponseEntity<ErrorResponse> handleAiServer(
            AiServerException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(exception.getMessage()));
    }
}
