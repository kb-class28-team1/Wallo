package com.wallo.asset.dto;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
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
        private AnnualSalaryLookupStatus annualSalaryLookupStatus;

        public Response(List<Result> results) {
            this.results = results;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ConnectedAssetsResponse {

        private List<ConnectedAsset> connections;

        public ConnectedAssetsResponse(List<ConnectedAsset> connections) {
            this.connections = connections == null
                    ? new ArrayList<>()
                    : new ArrayList<>(connections);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectedAsset {

        private Long connectionId;
        private Long assetId;
        private Long institutionId;
        private String institutionName;
        private String institutionType;
        private String logoUrl;
        private String financialGroupCode;
        private String financialGroupName;
        private LocalDateTime lastSyncAt;
        private String assetKind;
        private String assetName;
        private String displayNumber;
        private String assetType;
        private Long amount;
        private String currency;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private Long institutionId;
        private String institutionName;
        private String financialGroupCode;
        private String financialGroupName;
        private String institutionType;
        private String logoUrl;
        private Status status;
        private String message;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstitutionResponse {
        private List<InstitutionItem> banks;
        private List<InstitutionItem> cards;
        private List<InstitutionItem> stocks;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstitutionItem {
        private Long institutionId;
        private String codefOrganizationCode;
        private String name;
        private String financialGroupCode;
        private String financialGroupName;
        private String logoUrl;
        private List<String> services;
    }

    public enum Status {
        SUCCESS,
        FAILED
    }

    public enum AnnualSalaryLookupStatus {
        AVAILABLE,
        UNAVAILABLE,
        ERROR
    }
}
