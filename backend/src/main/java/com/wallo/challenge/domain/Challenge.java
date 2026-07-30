package com.wallo.challenge.domain;

import java.time.LocalDateTime;

/**
 * challenge 테이블 한 행을 표현하는 도메인 객체.
 * MyBatis 조회 결과와 챌린지 생성 처리에서 사용한다.
 */
public class Challenge {

    // challenge 테이블의 PK
    private Long id;

    // 챌린지를 만든 사용자 ID → users.id와 연결
    private Long ownerId;

    // 챌린지 이름
    private String name;

    // 챌린지 유형
    private String challengeType;

    // 다른 사용자를 초대할 때 쓰는 고유 코드
    private String inviteCode;

    // 챌린지 상태 예: ACTIVE, CLOSED
    private String status;

    // 생성 시각
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getChallengeType() {
        return challengeType;
    }

    public void setChallengeType(String challengeType) {
        this.challengeType = challengeType;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
