package com.wallo.external.auth;

/**
 * 모든 사용자와 기관에 동일한 Mock CODEF 인증정보를 제공하는 구현체입니다.
 *
 * <p>인증값은 생성자로 주입받으므로 AppConfig에서 Mock 전용 설정과 연결할 수 있습니다.
 * 실제 구현체로 교체할 때는 이 클래스 대신 다른 {@link CodefCredentialProvider}
 * 구현체를 등록하면 됩니다.</p>
 */
public final class MockCodefCredentialProvider implements CodefCredentialProvider {

    private final CodefCredential credential;

    public MockCodefCredentialProvider(
            String loginType,
            String id,
            String password
    ) {
        this.credential = new CodefCredential(loginType, id, password);
    }

    @Override
    public CodefCredential getCredential(long userId, String organization) {
        return credential;
    }
}
