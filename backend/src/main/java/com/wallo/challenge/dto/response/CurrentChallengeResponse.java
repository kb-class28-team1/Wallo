package com.wallo.challenge.dto.response;

import com.wallo.challenge.domain.Challenge;

/** GET /api/challenges/current 응답. 미참여 상태도 정상 응답으로 표현한다. */
public class CurrentChallengeResponse {

    private final boolean joined;
    private final Long id;
    private final String name;
    private final String challengeType;
    private final String inviteCode;
    private final String status;

    private CurrentChallengeResponse(boolean joined, Challenge challenge) {
        this.joined = joined;
        this.id = challenge == null ? null : challenge.getId();
        this.name = challenge == null ? null : challenge.getName();
        this.challengeType = challenge == null ? null : challenge.getChallengeType();
        this.inviteCode = challenge == null ? null : challenge.getInviteCode();
        this.status = challenge == null ? null : challenge.getStatus();
    }

    public static CurrentChallengeResponse joined(Challenge challenge) {
        return new CurrentChallengeResponse(true, challenge);
    }

    public static CurrentChallengeResponse notJoined() {
        return new CurrentChallengeResponse(false, null);
    }

    public boolean isJoined() {
        return joined;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getChallengeType() {
        return challengeType;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public String getStatus() {
        return status;
    }
}
