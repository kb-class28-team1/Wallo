package com.wallo.auth;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** REST 요청의 Bearer access JWT를 미리 검증해 CurrentUserProvider가 재사용하도록 한다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                request.setAttribute(JwtCurrentUserProvider.USER_ID_ATTRIBUTE,
                        jwtTokenService.parseAccessToken(authorization.substring(7).trim()));
            } catch (RuntimeException ignored) {
                // 보호된 API에서 CurrentUserProvider가 공통 401 응답을 만들도록 둔다.
            }
        }
        filterChain.doFilter(request, response);
    }
}
