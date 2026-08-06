package com.wallo.user.dto;

public final class UserProfileDto {

    private UserProfileDto() {
    }

    public static class Response {

        private Long id;
        private String name;
        private String nickname;
        private String email;
        private String profileImageUrl;

        public Response() {
        }

        public Response(
                Long id,
                String name,
                String nickname,
                String email,
                String profileImageUrl
        ) {
            this.id = id;
            this.name = name;
            this.nickname = nickname;
            this.email = email;
            this.profileImageUrl = profileImageUrl;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getProfileImageUrl() {
            return profileImageUrl;
        }

        public void setProfileImageUrl(String profileImageUrl) {
            this.profileImageUrl = profileImageUrl;
        }
    }
}
