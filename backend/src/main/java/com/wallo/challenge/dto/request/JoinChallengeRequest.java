package com.wallo.challenge.dto.request;

/** POST /api/challenges/join 요청 본문. */
public class JoinChallengeRequest {

    private String inviteCode;

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
