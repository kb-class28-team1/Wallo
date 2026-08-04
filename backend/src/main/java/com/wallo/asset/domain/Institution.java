package com.wallo.asset.domain;

public class Institution {

    private Long institutionId;
    private String codefOrganizationCode;
    private String name;
    private String institutionType;
    private String logoUrl;
    private String services;

    public Institution() {
    }

    public Institution(
            Long institutionId,
            String codefOrganizationCode,
            String name,
            String institutionType,
            String logoUrl
    ) {
        this.institutionId = institutionId;
        this.codefOrganizationCode = codefOrganizationCode;
        this.name = name;
        this.institutionType = institutionType;
        this.logoUrl = logoUrl;
    }

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }

    public String getCodefOrganizationCode() {
        return codefOrganizationCode;
    }

    public void setCodefOrganizationCode(String codefOrganizationCode) {
        this.codefOrganizationCode = codefOrganizationCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstitutionType() {
        return institutionType;
    }

    public void setInstitutionType(String institutionType) {
        this.institutionType = institutionType;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getServices() {
        return services;
    }

    public void setServices(String services) {
        this.services = services;
    }
}


