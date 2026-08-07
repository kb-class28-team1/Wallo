package com.wallo.common.exception;

import com.wallo.auth.UnauthenticatedException;
import com.wallo.chat.client.AiServerException;
import com.wallo.common.response.CommonResponse;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = Logger.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<CommonResponse<Void>> handleUnauthenticated(
            UnauthenticatedException exception) {
        ErrorCode errorCode = ErrorCode.AUTH_REQUIRED;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Void>> handleBadRequest(
            IllegalArgumentException exception) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.failure(
                        errorCode.getCode(),
                        messageOrDefault(exception.getMessage(), errorCode)));
    }

    @ExceptionHandler(AiServerException.class)
    public ResponseEntity<CommonResponse<Void>> handleAiServer(
            AiServerException exception) {
        ErrorCode errorCode = ErrorCode.AI_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.failure(
                        errorCode.getCode(),
                        messageOrDefault(exception.getMessage(), errorCode)));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CommonResponse<Void>> handleCustomException(CustomException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception
    ) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(CommonResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleException(Exception exception) {
        log.log(Level.SEVERE, "처리되지 않은 서버 예외가 발생했습니다.", exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    private String messageOrDefault(String message, ErrorCode errorCode) {
        return message == null || message.isBlank() ? errorCode.getMessage() : message;
    }
}

