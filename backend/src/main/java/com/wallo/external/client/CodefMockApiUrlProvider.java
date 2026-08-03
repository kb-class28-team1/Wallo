package com.wallo.external.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CodefMockApiUrlProvider {

    private final String baseUrl;

    public CodefMockApiUrlProvider(
            @Value("${codef.mock-api.base-url}") String baseUrl
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("CODEF Mock API base URL이 필요합니다.");
        }
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
    }

    public String resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("CODEF Mock API 경로가 필요합니다.");
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }
}
