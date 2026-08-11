package com.wallo.external.auth;

/**
 * 금융기관 CODEF 요청에 사용하는 로그인 인증정보를 표현하는 값 객체입니다.
 *
 * <p>현재는 Mock 인증정보를 담지만, 이후 실제 기관 인증정보나 Connected ID 기반
 * 인증정보를 제공하는 구현체에서도 동일한 요청 경계로 사용할 수 있습니다.</p>
 */
public record CodefCredential(
        String loginType,
        String id,
        String password
) {

    public CodefCredential {
        if (loginType == null || loginType.isBlank()) {
            throw new IllegalArgumentException("CODEF login type is required.");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CODEF login id is required.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("CODEF password is required.");
        }
    }
}
