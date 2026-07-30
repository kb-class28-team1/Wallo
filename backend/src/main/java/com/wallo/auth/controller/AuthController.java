package com.wallo.auth.controller;

import com.wallo.auth.SessionCurrentUserProvider;
import com.wallo.auth.dto.request.LoginRequest;
import com.wallo.auth.dto.request.SignupRequest;
import com.wallo.auth.dto.response.AuthMessageResponse;
import com.wallo.auth.dto.response.AuthUserResponse;
import com.wallo.auth.exception.AuthErrorCode;
import com.wallo.auth.exception.AuthException;
import com.wallo.auth.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthUserResponse> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthUserResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthUserResponse user = authService.login(request);

        HttpSession previousSession = httpRequest.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SessionCurrentUserProvider.LOGIN_USER_ID, user.getId());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED);
        }

        Object sessionUserId = session.getAttribute(SessionCurrentUserProvider.LOGIN_USER_ID);
        if (!(sessionUserId instanceof Number)) {
            session.invalidate();
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED);
        }

        try {
            return ResponseEntity.ok(
                    authService.getCurrentUser(((Number) sessionUserId).longValue()));
        } catch (AuthException exception) {
            session.invalidate();
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthMessageResponse> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(new AuthMessageResponse("로그아웃되었습니다."));
    }
}
