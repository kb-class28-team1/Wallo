package com.wallo.external.auth;

public class ConfiguredCodefAccessTokenProvider implements CodefAccessTokenProvider {

    private final String accessToken;

    public ConfiguredCodefAccessTokenProvider(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("CODEF access token is required.");
        }
        this.accessToken = accessToken.trim();
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }
}
