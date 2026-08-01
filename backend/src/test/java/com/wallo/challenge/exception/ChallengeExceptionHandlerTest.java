package com.wallo.challenge.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wallo.auth.UnauthenticatedException;
import com.wallo.common.exception.GlobalExceptionHandler;
import com.wallo.common.response.CommonResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ChallengeExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void returnsUnauthorizedWhenLoginSessionIsMissing() {
        ResponseEntity<CommonResponse<Void>> response =
                exceptionHandler.handleUnauthenticated(new UnauthenticatedException());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTH_001", response.getBody().getError().getCode());
        assertEquals("로그인이 필요합니다.", response.getBody().getError().getMessage());
    }
}
