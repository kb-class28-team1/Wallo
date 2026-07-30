package com.wallo.auth;

/**
 * 현재 로그인한 사용자의 ID를 제공한다.
 *
 * 인증 방식이 세션에서 JWT로 변경되어도 Controller와 Service가 인증 구현에
 * 직접 의존하지 않도록 분리한다.
 */
public interface CurrentUserProvider {

    Long getCurrentUserId();
}
