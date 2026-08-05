package com.wallo.spending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class SpendingAnalysisDto {

    private SpendingAnalysisDto() {
    }

    /**
     * 소비분석 생성 요청.
     *
     * <p>날짜·월·분석유형 필드를 {@code String}으로 받는 이유: 이 값들을 {@code LocalDate}/
     * {@code YearMonth}/enum 타입으로 직접 받으면 형식이 잘못된 요청이 Controller에 도달하기도
     * 전에 Jackson 역직렬화 단계에서 예외를 던진다. 이 예외는 {@code GlobalExceptionHandler}가
     * {@code CustomException}에만 대응하는 구조상 500(INTERNAL_SERVER_ERROR)으로 새어 나가
     * 프로젝트가 일관되게 쓰는 {@code CommonResponse.failure(...)} 400 응답 형식을 우회하게 된다.
     * 기존 {@code ExpenseDto.SearchCondition}(startDate/endDate)과 {@code BudgetService}
     * (targetMonth)도 동일한 이유로 문자열을 받아 Service/검증 계층에서 직접 파싱한다.</p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String analysisType;
        private String targetMonth;
        private String startDate;
        private String endDate;
        private Boolean forceReanalyze;
    }
}
