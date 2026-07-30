package com.wallo.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.auth.SessionCurrentUserProvider;
import com.wallo.auth.dto.request.LoginRequest;
import com.wallo.auth.dto.request.SignupRequest;
import com.wallo.auth.dto.response.AuthUserResponse;
import com.wallo.auth.exception.AuthErrorCode;
import com.wallo.auth.exception.AuthException;
import com.wallo.auth.service.AuthService;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class AuthControllerTest {

    private final AuthController controller = new AuthController(new StubAuthService());

    @Test
    void storesLoginUserIdInNewSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@wallo.com");
        loginRequest.setPassword("password123!");

        controller.login(loginRequest, request);

        HttpSession session = request.getSession(false);
        assertNotNull(session);
        assertEquals(7L, session.getAttribute(SessionCurrentUserProvider.LOGIN_USER_ID));
    }

    @Test
    void returnsCurrentUserFromSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true)
                .setAttribute(SessionCurrentUserProvider.LOGIN_USER_ID, 7L);

        AuthUserResponse response = controller.getCurrentUser(request).getBody();

        assertNotNull(response);
        assertEquals(7L, response.getId());
    }

    @Test
    void rejectsCurrentUserRequestWithoutSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        AuthException exception =
                assertThrows(AuthException.class, () -> controller.getCurrentUser(request));

        assertEquals(AuthErrorCode.AUTH_REQUIRED, exception.getErrorCode());
    }

    @Test
    void invalidatesSessionOnLogout() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        session.setAttribute(SessionCurrentUserProvider.LOGIN_USER_ID, 7L);

        assertEquals(HttpStatus.OK, controller.logout(request).getStatusCode());
        assertTrue(session.isInvalid());
    }

    private static class StubAuthService implements AuthService {

        @Override
        public AuthUserResponse signup(SignupRequest request) {
            return response();
        }

        @Override
        public AuthUserResponse login(LoginRequest request) {
            return response();
        }

        @Override
        public AuthUserResponse getCurrentUser(Long userId) {
            if (!Long.valueOf(7L).equals(userId)) {
                throw new AuthException(AuthErrorCode.AUTH_REQUIRED);
            }
            return response();
        }

        private AuthUserResponse response() {
            return new AuthUserResponse(
                    7L,
                    "홍길동",
                    "wallo",
                    "test@wallo.com",
                    "/images/profiles/default-profile.svg",
                    0);
        }
    }
}
