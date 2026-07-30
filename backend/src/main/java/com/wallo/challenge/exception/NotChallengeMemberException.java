package com.wallo.challenge.exception;

/** 로그인 사용자가 요청한 챌린지에 참여하고 있지 않을 때 발생하는 예외임. */
public class NotChallengeMemberException extends RuntimeException {

    public NotChallengeMemberException() {
        super("참여 중인 챌린지의 랭킹만 조회할 수 있습니다.");
    }
}
