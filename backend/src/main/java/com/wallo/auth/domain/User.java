package com.wallo.auth.domain;

/** 인증과 로그인 사용자 조회에 필요한 users 테이블 정보다. */
public class User {

    private Long id;
    private String email;
    private String passwordHash;
    private String nickname;
    private String name;
    private Long annualSalary;
    private String profileImageUrl;
    private String role;
    private Integer point;
    private Long currentChallengeId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAnnualSalary() {
        return annualSalary;
    }

    public void setAnnualSalary(Long annualSalary) {
        this.annualSalary = annualSalary;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getPoint() {
        return point;
    }

    public void setPoint(Integer point) {
        this.point = point;
    }

    public Long getCurrentChallengeId() {
        return currentChallengeId;
    }

    public void setCurrentChallengeId(Long currentChallengeId) {
        this.currentChallengeId = currentChallengeId;
    }
}
