package com.wallo.challenge.dto.response;

/** 챌린지 API 요청 실패 시 클라이언트에 전달하는 공통 오류 형식이다. */
public class ChallengeErrorResponse {

    private final int status;
    private final String message;

    public ChallengeErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
