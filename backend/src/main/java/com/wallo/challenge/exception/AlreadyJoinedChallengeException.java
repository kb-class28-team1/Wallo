package com.wallo.challenge.exception;

/** 이미 현재 참여 챌린지가 있는 사용자가 생성 또는 참여를 요청할 때 발생한다. */
public class AlreadyJoinedChallengeException extends RuntimeException {

    public AlreadyJoinedChallengeException() {
        super("이미 참여 중인 챌린지가 있습니다.");
    }
}
