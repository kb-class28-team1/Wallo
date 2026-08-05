package com.wallo.spending.mapper;

import com.wallo.spending.dto.SpendingExpenseAggregate;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Param;

public interface SpendingAnalysisMapper {

    /**
     * 지정 기간(시작일·종료일 포함)의 유효 지출 총액과 거래 건수를 조회한다.
     *
     * <p>분석유형에 종속되지 않는 범용 기간 집계 메서드다. 같은 메서드에 실제 분석 기간과
     * 비교 기간을 각각 다른 {@code startDate}/{@code endDate}로 넘겨 두 번 호출해 재사용한다.</p>
     */
    SpendingExpenseAggregate selectExpenseAggregate(
            @Param("userId") long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
