package com.wallo.challenge.dto.request;

/**
 * POST /api/challenges 요청 본문.
 * 생성자 ID는 요청으로 받지 않고, 이후 인증 정보에서 가져온다.
 */
public class CreateChallengeRequest {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
