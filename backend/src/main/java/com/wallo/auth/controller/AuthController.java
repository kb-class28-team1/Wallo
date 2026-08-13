package com.wallo.auth.controller;

import com.wallo.auth.JwtTokenService;
import com.wallo.auth.dto.request.LoginRequest;
import com.wallo.auth.dto.request.SignupRequest;
import com.wallo.auth.dto.response.AuthMessageResponse;
import com.wallo.auth.dto.response.AuthTokenResponse;
import com.wallo.auth.dto.response.AuthUserResponse;
import com.wallo.auth.exception.AuthErrorCode;
import com.wallo.auth.exception.AuthException;
import com.wallo.auth.service.AuthService;
import java.time.Duration;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;

    /** 테스트와 기존 직접 호출부를 위한 기본 설정 생성자. */
    public AuthController(AuthService authService) {
        this(authService, new JwtTokenService(
                "change-this-wall-o-jwt-secret-at-least-32-bytes", 1800, 1209600), false, "Lax");
    }

    @Autowired
    public AuthController(
            AuthService authService,
            JwtTokenService jwtTokenService,
            @Value("${jwt.refresh-cookie-secure:false}") boolean refreshCookieSecure,
            @Value("${jwt.refresh-cookie-same-site:Lax}") String refreshCookieSameSite) {
        this.authService = authService;
        this.jwtTokenService = jwtTokenService;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthUserResponse> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        AuthUserResponse user = authService.login(request);
        return tokenResponse(user);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> getCurrentUser(HttpServletRequest request) {
        return ResponseEntity.ok(authService.getCurrentUser(parseAccessToken(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(HttpServletRequest request) {
        Cookie refreshCookie = findCookie(request, REFRESH_TOKEN_COOKIE);
        if (refreshCookie == null) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED);
        }
        Long userId = jwtTokenService.parseRefreshToken(refreshCookie.getValue());
        return tokenResponse(authService.getCurrentUser(userId));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthMessageResponse> logout() {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .sameSite(refreshCookieSameSite)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthMessageResponse("로그아웃되었습니다."));
    }

    private static final String REFRESH_TOKEN_COOKIE = "WALLO_REFRESH_TOKEN";

    private ResponseEntity<AuthTokenResponse> tokenResponse(AuthUserResponse user) {
        String accessToken = jwtTokenService.createAccessToken(user.getId());
        String refreshToken = jwtTokenService.createRefreshToken(user.getId());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(jwtTokenService.getRefreshTokenTtlSeconds()))
                .sameSite(refreshCookieSameSite)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthTokenResponse(accessToken, user));
    }

    private Long parseAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED);
        }
        return jwtTokenService.parseAccessToken(authorization.substring(7).trim());
    }

    private Cookie findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie;
        }
        return null;
    }
}
