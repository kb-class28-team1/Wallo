package com.wallo.user.dto;

public final class NicknameDto {

    private NicknameDto() {
    }

    public static class UpdateRequest {

        private String nickname;

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
    }

    public static class Response {

        private final String nickname;

        public Response(String nickname) {
            this.nickname = nickname;
        }

        public String getNickname() {
            return nickname;
        }
    }
}
