package com.wallo.challenge.exception;

/** 여러 사용자를 비교할 수 없는 SOLO 챌린지에서 랭킹을 요청할 때 발생하는 예외임. */
public class SoloFeatureNotAllowedException extends RuntimeException {

    public SoloFeatureNotAllowedException() {
        super("주간 랭킹은 GROUP 챌린지에서만 이용할 수 있습니다.");
    }
}
