package com.wallo.external.auth;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class CodefAuthorizedRequestFactory {

    private final CodefAccessTokenProvider accessTokenProvider;

    public CodefAuthorizedRequestFactory(CodefAccessTokenProvider accessTokenProvider) {
        this.accessTokenProvider = accessTokenProvider;
    }

    public <T> HttpEntity<T> create(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenProvider.getAccessToken());
        return new HttpEntity<>(body, headers);
    }
}
