package com.wallo.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wallo.chat.client.AiServerException;
import com.wallo.common.response.CommonResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void handlesIllegalArgumentExceptionWithCommonResponse() {
        ResponseEntity<CommonResponse<Void>> response =
                exceptionHandler.handleBadRequest(new IllegalArgumentException("입력 값 오류"));

        assertError(response, HttpStatus.BAD_REQUEST, "COMMON_003", "입력 값 오류");
    }

    @Test
    void handlesAiServerExceptionWithCommonResponse() {
        ResponseEntity<CommonResponse<Void>> response =
                exceptionHandler.handleAiServer(new AiServerException("AI 서버 연결 실패"));

        assertError(response, HttpStatus.BAD_GATEWAY, "COMMON_004", "AI 서버 연결 실패");
    }

    private void assertError(
            ResponseEntity<CommonResponse<Void>> response,
            HttpStatus status,
            String code,
            String message) {
        assertEquals(status, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getError());
        assertEquals(code, response.getBody().getError().getCode());
        assertEquals(message, response.getBody().getError().getMessage());
    }
}
