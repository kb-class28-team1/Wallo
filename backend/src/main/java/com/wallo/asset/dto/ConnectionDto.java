package com.wallo.asset.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class ConnectionDto {

    private ConnectionDto() {
    }

    public static final String CODEF_SUCCESS_CODE = "CF-00000";
    public static final String MOCK_LOGIN_TYPE = "1";
    public static final String MOCK_ID = "mock_id";
    public static final String MOCK_PASSWORD = "mock_pw";
    public static final String SUCCESS_MESSAGE = "연동 완료";
    public static final String FAILED_MESSAGE = "연동 실패";

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        private Boolean consentAgreed;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<Result> results;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private String institutionId;
        private String institutionName;
        private String institutionType;
        private String logoUrl;
        private Status status;
        private String message;
    }

    public enum Status {
        SUCCESS,
        FAILED
    }
}
