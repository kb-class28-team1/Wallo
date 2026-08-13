package com.wallo.external.auth;

/**
 * 금융기관 CODEF 요청에 사용할 인증정보를 제공하는 추상화입니다.
 *
 * <p>구현체는 Mock 인증정보, 사용자별 실제 인증정보 또는 Connected ID 기반
 * 인증정보를 제공할 수 있으며, 호출자는 인증정보의 출처를 알 필요가 없습니다.</p>
 */
public interface CodefCredentialProvider {

    /**
     * 사용자와 기관에 맞는 CODEF 요청 인증정보를 반환합니다.
     *
     * @param userId 인증정보 소유 사용자 ID
     * @param organization CODEF 기관 코드
     * @return CODEF 요청에 사용할 인증정보
     * @throws RuntimeException 인증정보를 조회할 수 없거나 유효하지 않은 경우
     */
    CodefCredential getCredential(long userId, String organization);
}
