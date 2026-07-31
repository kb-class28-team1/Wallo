package com.wallo.auth.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.controller.AuthController;
import com.wallo.auth.dto.request.LoginRequest;
import com.wallo.auth.dto.request.SignupRequest;
import com.wallo.auth.dto.response.AuthUserResponse;
import com.wallo.auth.service.AuthService;
import com.wallo.common.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthExceptionRoutingTest {

    @Test
    void routesDuplicateNicknameToAuthExceptionHandlerInsteadOfGlobalHandler()
            throws Exception {
        AuthService authService = new DuplicateNicknameAuthService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new AuthExceptionHandler(), new GlobalExceptionHandler())
                .build();

        String responseBody = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "중복 검사",
                                  "nickname": "동길홍",
                                  "email": "new-user@wallo.test",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(responseBody.contains("\"code\":\"AUTH_NICKNAME_ALREADY_EXISTS\""));
        assertTrue(responseBody.contains("\"message\":\"이미 사용 중인 닉네임입니다.\""));
    }

    private static class DuplicateNicknameAuthService implements AuthService {

        @Override
        public AuthUserResponse signup(SignupRequest request) {
            throw new AuthException(AuthErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        @Override
        public AuthUserResponse login(LoginRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthUserResponse getCurrentUser(Long userId) {
            throw new UnsupportedOperationException();
        }
    }
}
