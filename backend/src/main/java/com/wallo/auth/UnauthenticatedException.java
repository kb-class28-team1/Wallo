package com.wallo.auth;

/** 로그인 정보 없이 인증이 필요한 API를 요청했을 때 발생한다. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("로그인이 필요합니다.");
    }
}
