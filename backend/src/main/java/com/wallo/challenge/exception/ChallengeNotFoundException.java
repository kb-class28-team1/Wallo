package com.wallo.challenge.exception;

/** 요청한 ID에 해당하는 챌린지를 찾을 수 없을 때 발생하는 예외임. */
public class ChallengeNotFoundException extends RuntimeException {

    public ChallengeNotFoundException() {
        super("챌린지를 찾을 수 없습니다.");
    }
}
