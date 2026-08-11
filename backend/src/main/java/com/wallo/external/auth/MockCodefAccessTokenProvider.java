package com.wallo.external.auth;

public class MockCodefAccessTokenProvider implements CodefAccessTokenProvider {

    private final String accessToken;

    public MockCodefAccessTokenProvider(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("CODEF Mock API access token is required.");
        }
        this.accessToken = accessToken.trim();
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }
}
