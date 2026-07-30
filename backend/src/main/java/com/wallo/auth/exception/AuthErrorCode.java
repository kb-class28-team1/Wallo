package com.wallo.auth.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_REQUEST", "필수 입력값을 확인해주세요."),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "AUTH_INVALID_EMAIL", "올바른 이메일 형식을 입력해주세요."),
    INVALID_PASSWORD(
            HttpStatus.BAD_REQUEST,
            "AUTH_INVALID_PASSWORD",
            "비밀번호는 8자 이상 72자 이하로 입력해주세요."),
    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "AUTH_EMAIL_ALREADY_EXISTS",
            "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "AUTH_NICKNAME_ALREADY_EXISTS",
            "이미 사용 중인 닉네임입니다."),
    LOGIN_FAILED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_LOGIN_FAILED",
            "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
