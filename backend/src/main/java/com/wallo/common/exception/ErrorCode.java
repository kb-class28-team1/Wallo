package com.wallo.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    AUTH_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_001",
            "로그인이 필요합니다."
    ),
    CONNECTION_CONSENT_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "CONNECTION_001",
            "개인신용정보 수집·이용 동의가 필요합니다."
    ),
    EXTERNAL_CONNECTION_API_FAILED(
            HttpStatus.BAD_GATEWAY,
            "CONNECTION_002",
            "외부 자산 연동 API 호출에 실패했습니다."
    ),
    CONNECTION_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CONNECTION_003",
            "연동 결과 저장에 실패했습니다. CONNECTIONS 테이블 스키마를 확인해주세요."
    ),
    INVALID_DASHBOARD_REQUEST(
            HttpStatus.BAD_REQUEST,
            "DASHBOARD_001",
            "대시보드 요청 값이 올바르지 않습니다."
    ),
    INVALID_ANNUAL_SALARY(
            HttpStatus.BAD_REQUEST,
            "PROFILE_001",
            "연봉은 0원보다 큰 금액으로 입력해주세요."
    ),
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PROFILE_002",
            "사용자 정보를 찾을 수 없습니다."
    ),
    INVALID_REPORT_YEAR(
            HttpStatus.BAD_REQUEST,
            "REPORT_001",
            "조회 연도가 올바르지 않습니다."
    ),
    ANNUAL_SALARY_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "REPORT_002",
            "연말정산 계산을 위해 연봉을 먼저 입력해주세요."
    ),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "COMMON_002",
            "지원하지 않는 HTTP 메서드입니다."
    ),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON_001",
            "서버 오류가 발생했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
