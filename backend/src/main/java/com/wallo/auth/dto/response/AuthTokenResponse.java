package com.wallo.auth.dto.response;

/** 로그인 또는 토큰 갱신 결과. access token은 브라우저 메모리에만 보관한다. */
public class AuthTokenResponse {
    private final String accessToken;
    private final AuthUserResponse user;

    public AuthTokenResponse(String accessToken, AuthUserResponse user) {
        this.accessToken = accessToken;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public AuthUserResponse getUser() {
        return user;
    }
}
