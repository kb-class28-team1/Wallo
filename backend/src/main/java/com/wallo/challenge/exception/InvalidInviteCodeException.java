package com.wallo.challenge.exception;

/** 입력한 초대 코드와 일치하는 챌린지가 없을 때 발생한다. */
public class InvalidInviteCodeException extends RuntimeException {

    public InvalidInviteCodeException() {
        super("유효하지 않은 초대 코드입니다.");
    }
}
