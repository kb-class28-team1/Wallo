package com.wallo.challenge.dto.response;

import java.time.LocalDateTime;
import com.wallo.challenge.domain.Challenge;

/**
 * POST /api/challenges 성공 응답.
 */
public class CreateChallengeResponse {

    private final Long id;
    private final String name;
    private final String challengeType;
    private final String inviteCode;
    private final String status;
    private final LocalDateTime createdAt;

    private CreateChallengeResponse(Challenge challenge) {
        this.id = challenge.getId();
        this.name = challenge.getName();
        this.challengeType = challenge.getChallengeType();
        this.inviteCode = challenge.getInviteCode();
        this.status = challenge.getStatus();
        this.createdAt = challenge.getCreatedAt();
    }

    public static CreateChallengeResponse from(Challenge challenge) {
        return new CreateChallengeResponse(challenge);
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
