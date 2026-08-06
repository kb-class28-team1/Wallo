package com.wallo.user.exception;

import org.springframework.http.HttpStatus;

public enum UserErrorCode {

    INVALID_NICKNAME(
            HttpStatus.BAD_REQUEST,
            "USER_INVALID_NICKNAME",
            "닉네임은 1자 이상 50자 이하로 입력해주세요."),
    NICKNAME_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "USER_NICKNAME_ALREADY_EXISTS",
            "이미 사용 중인 닉네임입니다."),
    INVALID_PROFILE_IMAGE(
            HttpStatus.BAD_REQUEST,
            "USER_INVALID_PROFILE_IMAGE",
            "JPG 또는 PNG 이미지 파일만 업로드할 수 있습니다."),
    PROFILE_IMAGE_TOO_LARGE(
            HttpStatus.BAD_REQUEST,
            "USER_PROFILE_IMAGE_TOO_LARGE",
            "프로필 이미지는 5MB 이하만 업로드할 수 있습니다."),
    PROFILE_IMAGE_STORAGE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "USER_PROFILE_IMAGE_STORAGE_FAILED",
            "프로필 이미지를 저장하지 못했습니다."),
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "USER_NOT_FOUND",
            "사용자 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus status, String code, String message) {
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
