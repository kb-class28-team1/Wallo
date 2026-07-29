package com.wallo.challenge.exception;

/** 이미 현재 참여 챌린지가 있는 사용자가 새 챌린지를 생성하려 할 때 발생한다. */
public class AlreadyJoinedChallengeException extends RuntimeException {

    public AlreadyJoinedChallengeException() {
        super("이미 참여 중인 챌린지가 있어 새 챌린지를 생성할 수 없습니다.");
    }
}
