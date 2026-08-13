package com.wallo.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.auth.JwtTokenService;
import com.wallo.auth.dto.request.LoginRequest;
import com.wallo.auth.dto.request.SignupRequest;
import com.wallo.auth.dto.response.AuthTokenResponse;
import com.wallo.auth.dto.response.AuthUserResponse;
import com.wallo.auth.exception.AuthErrorCode;
import com.wallo.auth.exception.AuthException;
import com.wallo.auth.service.AuthService;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthControllerTest {

    private final AuthController controller = new AuthController(new StubAuthService());

    @Test
    void returnsAccessTokenAndRefreshCookieOnLogin() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@wallo.com");
        loginRequest.setPassword("password123!");

        AuthTokenResponse response = controller.login(loginRequest).getBody();

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertEquals(7L, response.getUser().getId());
        assertNotNull(controller.login(loginRequest).getHeaders().getFirst("Set-Cookie"));
    }

    @Test
    void returnsCurrentUserFromBearerAccessToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String token = new JwtTokenService(
                "change-this-wall-o-jwt-secret-at-least-32-bytes", 1800, 1209600)
                .createAccessToken(7L);
        request.addHeader("Authorization", "Bearer " + token);

        AuthUserResponse response = controller.getCurrentUser(request).getBody();

        assertNotNull(response);
        assertEquals(7L, response.getId());
    }

    @Test
    void rejectsCurrentUserRequestWithoutAccessToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        AuthException exception =
                assertThrows(AuthException.class, () -> controller.getCurrentUser(request));

        assertEquals(AuthErrorCode.AUTH_REQUIRED, exception.getErrorCode());
    }

    @Test
    void clearsRefreshCookieOnLogout() {
        assertEquals(HttpStatus.OK, controller.logout().getStatusCode());
        String cookie = controller.logout().getHeaders().getFirst("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("WALLO_REFRESH_TOKEN="));
        assertTrue(cookie.contains("Max-Age=0"));
    }

    @Test
    void refreshesAccessTokenFromHttpOnlyCookie() {
        JwtTokenService tokenService = new JwtTokenService(
                "change-this-wall-o-jwt-secret-at-least-32-bytes", 1800, 1209600);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("WALLO_REFRESH_TOKEN", tokenService.createRefreshToken(7L)));

        AuthTokenResponse response = controller.refresh(request).getBody();

        assertNotNull(response);
        assertEquals(7L, response.getUser().getId());
        assertNotNull(response.getAccessToken());
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
