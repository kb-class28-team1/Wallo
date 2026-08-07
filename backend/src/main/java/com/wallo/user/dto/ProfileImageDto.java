package com.wallo.user.dto;

public final class ProfileImageDto {

    private ProfileImageDto() {
    }

    public static class Response {

        private final String profileImageUrl;

        public Response(String profileImageUrl) {
            this.profileImageUrl = profileImageUrl;
        }

        public String getProfileImageUrl() {
            return profileImageUrl;
        }
    }
}
