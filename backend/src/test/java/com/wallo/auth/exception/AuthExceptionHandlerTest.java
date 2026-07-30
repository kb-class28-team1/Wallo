package com.wallo.auth.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wallo.auth.dto.response.AuthErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void returnsConflictForDuplicateEmail() {
        ResponseEntity<AuthErrorResponse> response =
                handler.handleAuthException(
                        new AuthException(AuthErrorCode.EMAIL_ALREADY_EXISTS));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTH_EMAIL_ALREADY_EXISTS", response.getBody().getCode());
        assertEquals("이미 사용 중인 이메일입니다.", response.getBody().getMessage());
    }

    @Test
    void returnsUnauthorizedForMissingLoginSession() {
        ResponseEntity<AuthErrorResponse> response =
                handler.handleAuthException(new AuthException(AuthErrorCode.AUTH_REQUIRED));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTH_REQUIRED", response.getBody().getCode());
    }
}
