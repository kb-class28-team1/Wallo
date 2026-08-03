package com.wallo.external.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockCodefAccessTokenProvider implements CodefAccessTokenProvider {

    private final String accessToken;

    public MockCodefAccessTokenProvider(
            @Value("${codef.mock-api.access-token}") String accessToken
    ) {
        this.accessToken = accessToken;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }
}
