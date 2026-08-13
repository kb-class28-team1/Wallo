package com.wallo.auth;

import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Authorization Bearer access JWT에서 현재 사용자 ID를 읽는다. */
@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {
    public static final String USER_ID_ATTRIBUTE = JwtCurrentUserProvider.class.getName() + ".userId";

    private final JwtTokenService jwtTokenService;
    private final HttpServletRequest request;

    public JwtCurrentUserProvider(JwtTokenService jwtTokenService, HttpServletRequest request) {
        this.jwtTokenService = jwtTokenService;
        this.request = request;
    }

    @Override
    public Long getCurrentUserId() {
        Object userId = request.getAttribute(USER_ID_ATTRIBUTE);
        if (userId instanceof Long value) return value;

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthenticatedException();
        }
        Long parsedUserId = jwtTokenService.parseAccessToken(authorization.substring(7).trim());
        request.setAttribute(USER_ID_ATTRIBUTE, parsedUserId);
        return parsedUserId;
    }
}
