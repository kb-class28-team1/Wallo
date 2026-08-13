package com.wallo.auth;

import javax.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 현재 요청의 HttpSession에서 로그인 사용자 ID를 읽는 임시 인증 구현이다.
 *
 * 로그인 기능이 완성되면 로그인 성공 시 세션의 LOGIN_USER_ID에 사용자 ID를 넣는다.
 * JWT를 사용하게 되면 CurrentUserProvider 구현체만 JWT 방식으로 교체한다.
 */
/**
 * 이전 세션 인증 호환용 구현이다. 현재 애플리케이션 인증에는 사용하지 않는다.
 */
public class SessionCurrentUserProvider implements CurrentUserProvider {

    public static final String LOGIN_USER_ID = "LOGIN_USER_ID";

    @Override
    public Long getCurrentUserId() {
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null) {
            throw new UnauthenticatedException();
        }

        HttpSession session = requestAttributes.getRequest().getSession(false);
        if (session == null) {
            throw new UnauthenticatedException();
        }

        Object userId = session.getAttribute(LOGIN_USER_ID);
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }

        throw new UnauthenticatedException();
    }
}
