package com.wallo.external.client;

public class CodefMockApiUrlProvider {

    private final String baseUrl;

    public CodefMockApiUrlProvider(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("CODEF API base URL is required.");
        }
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
    }

    public String resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("CODEF API path is required.");
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }
}
