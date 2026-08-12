package com.wallo.spending.exception;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;

/**
 * 소비분석 요청 검증 실패.
 *
 * <p>HTTP 응답은 {@code GlobalExceptionHandler}가 {@code ErrorCode.getMessage()}의 고정
 * 메시지만 사용하는 기존 구조를 그대로 따라 항상 SPENDING_001의 고정 메시지를 반환한다.
 * 구체적인 실패 원인은 {@link #getReason()}으로만 전달하며, 로깅 여부와 시점은 이 예외를
 * 처리하는 호출 계층({@code GlobalExceptionHandler} 등)의 책임이다.</p>
 */
public class InvalidSpendingAnalysisRequestException extends CustomException {

    private final String reason;

    public InvalidSpendingAnalysisRequestException(String reason) {
        super(ErrorCode.INVALID_SPENDING_ANALYSIS_REQUEST);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
