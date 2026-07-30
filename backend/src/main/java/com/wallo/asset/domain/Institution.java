package com.wallo.asset.domain;

public class Institution {

    private String institutionId;
    private String name;
    private String institutionType;
    private String logoUrl;

    public Institution(String institutionId, String name, String institutionType, String logoUrl) {
        this.institutionId = institutionId;
        this.name = name;
        this.institutionType = institutionType;
        this.logoUrl = logoUrl;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public String getName() {
        return name;
    }

    public String getInstitutionType() {
        return institutionType;
    }

    public String getLogoUrl() {
        return logoUrl;
    }
}


