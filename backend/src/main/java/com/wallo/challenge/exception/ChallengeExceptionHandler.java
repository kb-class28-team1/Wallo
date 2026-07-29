package com.wallo.challenge.exception;

import com.wallo.auth.UnauthenticatedException;
import com.wallo.challenge.dto.response.ChallengeErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** 챌린지 API에서 발생한 예외를 일정한 HTTP 오류 응답으로 변환한다. */
@ControllerAdvice
public class ChallengeExceptionHandler {

    @ExceptionHandler(AlreadyJoinedChallengeException.class)
    public ResponseEntity<ChallengeErrorResponse> handleAlreadyJoined(
            AlreadyJoinedChallengeException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidInviteCodeException.class)
    public ResponseEntity<ChallengeErrorResponse> handleInvalidInviteCode(
            InvalidInviteCodeException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ChallengeErrorResponse> handleUnauthenticated(
            UnauthenticatedException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    private ResponseEntity<ChallengeErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ChallengeErrorResponse(status.value(), message));
    }
}
