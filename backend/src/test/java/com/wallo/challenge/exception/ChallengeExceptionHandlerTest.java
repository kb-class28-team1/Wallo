package com.wallo.challenge.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wallo.auth.UnauthenticatedException;
import com.wallo.challenge.dto.response.ChallengeErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ChallengeExceptionHandlerTest {

    private final ChallengeExceptionHandler exceptionHandler =
            new ChallengeExceptionHandler();

    @Test
    void returnsUnauthorizedWhenLoginSessionIsMissing() {
        ResponseEntity<ChallengeErrorResponse> response =
                exceptionHandler.handleUnauthenticated(new UnauthenticatedException());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getBody().getStatus());
        assertEquals("로그인이 필요합니다.", response.getBody().getMessage());
    }
}
