package com.wallo.spending.mapper;

import com.wallo.spending.dto.SpendingCategoryAggregate;
import com.wallo.spending.dto.SpendingExpenseAggregate;
import com.wallo.spending.dto.SpendingTimeSlotAggregate;
import com.wallo.spending.dto.SpendingWeekdayAggregate;
import java.time.LocalDate;
import java.util.List;
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

    /**
     * 지정 기간(시작일·종료일 포함)의 카테고리별 유효 지출 금액과 거래 건수를 조회한다.
     *
     * <p>{@link #selectExpenseAggregate}와 마찬가지로 분석유형에 종속되지 않는 범용 기간
     * 조회이며, 현재 기간과 비교 기간을 각각 다른 {@code startDate}/{@code endDate}로 넘겨
     * 두 번 호출해 재사용한다. 거래가 없는 카테고리는 결과에 포함되지 않고, 유효 거래가 전혀
     * 없으면 빈 리스트를 반환한다(현재/이전 결과 병합, 비중·증감률 계산, 누락 카테고리를 0으로
     * 채우는 처리는 Service 계층 책임).</p>
     */
    List<SpendingCategoryAggregate> selectCategoryAggregates(
            @Param("userId") long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 지정 기간(시작일·종료일 포함)의 요일별 유효 지출 금액과 거래 건수를 조회한다.
     *
     * <p>{@code transaction_date} 기준으로 요일을 계산한다({@code transaction_time}은 쓰지
     * 않음). 다른 집계 메서드와 마찬가지로 분석유형에 종속되지 않는 범용 기간 조회다. 거래가
     * 없는 요일은 결과에 포함되지 않고, 유효 거래가 전혀 없으면 빈 리스트를 반환한다(누락 요일을
     * 0으로 채우는 처리, 주요 소비 요일 판정은 Service 계층 책임).</p>
     */
    List<SpendingWeekdayAggregate> selectWeekdayAggregates(
            @Param("userId") long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 지정 기간(시작일·종료일 포함)의 시간대별 유효 지출 금액과 거래 건수를 조회한다.
     *
     * <p>날짜 필터는 {@code transaction_date}, 시간대 분류는 {@code transaction_time}
     * 기준이다. 다른 집계 메서드와 마찬가지로 범용 기간 조회이며, 거래가 없는 시간대는 결과에
     * 포함되지 않고 유효 거래가 전혀 없으면 빈 리스트를 반환한다(누락 시간대를 0으로 채우는
     * 처리, 주요 소비 시간대 판정은 Service 계층 책임).</p>
     */
    List<SpendingTimeSlotAggregate> selectTimeSlotAggregates(
            @Param("userId") long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
