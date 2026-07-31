package com.wallo.auth.dto.response;

import com.wallo.auth.domain.User;

public class AuthUserResponse {

    private final Long id;
    private final String name;
    private final String nickname;
    private final String email;
    private final String profileImageUrl;
    private final Integer point;

    public AuthUserResponse(
            Long id,
            String name,
            String nickname,
            String email,
            String profileImageUrl,
            Integer point) {
        this.id = id;
        this.name = name;
        this.nickname = nickname;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.point = point;
    }

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getName(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getPoint());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public Integer getPoint() {
        return point;
    }
}
