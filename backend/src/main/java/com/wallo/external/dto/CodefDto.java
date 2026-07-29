package com.wallo.external.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class CodefDto {

    private CodefDto() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String organization;
        private String institutionType;
        private String loginType;
        private String id;
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Result result;
        private Object data;

        public static Response success(Object data) {
            return new Response(new Result("CF-00000", "성공", ""), data);
        }

        public static Response failure(String code, String message, String extraMessage) {
            return new Response(new Result(code, message, extraMessage), null);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private String code;
        private String message;
        private String extraMessage;
    }
}
