package com.wallo.common;

import com.wallo.chat.client.AiServerException;
import com.wallo.chat.controller.ChatController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// AI 채팅 컨트롤러의 예외만 처리하여 다른 도메인의 예외 응답과 충돌하지 않게 함.
@RestControllerAdvice(assignableTypes = ChatController.class)
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest()
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
