package com.wallo.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    AUTH_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_001",
            "로그인이 필요합니다."
    ),
    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "COMMON_003",
            "요청 값이 올바르지 않습니다."
    ),
    AI_SERVER_ERROR(
            HttpStatus.BAD_GATEWAY,
            "COMMON_004",
            "AI 서버 요청 처리에 실패했습니다."
    ),
    CONNECTION_CONSENT_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "CONNECTION_001",
            "개인신용정보 수집·이용 동의가 필요합니다."
    ),
    CONNECTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CONNECTION_004",
            "연결된 자산을 찾을 수 없습니다."
    ),
    INVALID_DASHBOARD_REQUEST(
            HttpStatus.BAD_REQUEST,
            "DASHBOARD_001",
            "대시보드 요청 값이 올바르지 않습니다."
    ),
    REPORT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REPORT_001",
            "요청하신 금융 리포트를 찾을 수 없습니다."
    ),
    REPORT_CONTENT_EMPTY(
            HttpStatus.BAD_REQUEST,
            "REPORT_002",
            "기사 본문이 비어 있어 금융 리포트를 생성할 수 없습니다."
    ),
    AI_REPORT_GENERATION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "REPORT_003",
            "AI 서버 호출에 실패했습니다."
    ),
    AI_REPORT_INVALID_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "REPORT_004",
            "AI 서버 응답이 올바르지 않습니다."
    ),
    REPORT_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "REPORT_005",
            "금융 리포트 저장에 실패했습니다."
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
    // 연말정산 리포트(자산) 기능의 코드였던 REPORT_001/REPORT_002가 금융 리포트(뉴스) 기능의
    // 코드와 우연히 겹쳐 있었다(develop 병합 중 발견) — 같은 "REPORT" 접두어를 서로 다른 두 기능이
    // 독립적으로 사용해서 생긴 충돌이다. 이미 있는 PROFILE_00X 네임스페이스로 옮겨 재번호했다.
    INVALID_REPORT_YEAR(
            HttpStatus.BAD_REQUEST,
            "PROFILE_003",
            "조회 연도가 올바르지 않습니다."
    ),
    ANNUAL_SALARY_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "PROFILE_004",
            "연말정산 계산을 위해 세전 연봉 자동 조회 결과가 없습니다."
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
