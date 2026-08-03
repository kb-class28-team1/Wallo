package com.wallo.asset.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class InstitutionDto {

    private InstitutionDto() {
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<Item> banks;
        private List<Item> cards;
        private List<Item> stocks;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long institutionId;
        private String codefOrganizationCode;
        private String name;
        private String logoUrl;
        private List<String> services;
    }
}
