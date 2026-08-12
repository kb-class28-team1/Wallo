package com.wallo.spending.service;

import com.wallo.spending.domain.SpendingTimeSlot;
import com.wallo.spending.domain.SpendingTimeSlotMetrics;
import com.wallo.spending.domain.SpendingWeekday;
import com.wallo.spending.domain.SpendingWeekdayMetrics;
import com.wallo.spending.dto.SpendingTimeSlotAggregate;
import com.wallo.spending.dto.SpendingWeekdayAggregate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Mapper가 반환한 요일·시간대 집계(거래가 있는 항목만 포함)를 고정된 전체 항목 집합으로
 * 정규화하는 순수 계산 객체.
 *
 * <p>DB를 직접 조회하지 않는다. Mapper 결과에 없는 요일/시간대는 0원·0건으로 채우고, 입력
 * 순서와 무관하게 항상 {@link SpendingWeekday}/{@link SpendingTimeSlot} 선언 순서로 반환한다.
 * 주요 요일·시간대 판정은 이 클래스의 책임이 아니다.</p>
 */
@Component
public class SpendingPatternNormalizer {

    public List<SpendingWeekdayMetrics> normalizeWeekdays(List<SpendingWeekdayAggregate> aggregates) {
        if (aggregates == null) {
            throw new IllegalArgumentException("요일 집계 목록이 필요합니다.");
        }
        Map<String, SpendingWeekdayAggregate> byWeekday = indexAndValidateWeekdays(aggregates);

        List<SpendingWeekdayMetrics> result = new ArrayList<>(SpendingWeekday.values().length);
        for (SpendingWeekday weekday : SpendingWeekday.values()) {
            SpendingWeekdayAggregate aggregate = byWeekday.get(weekday.name());
            result.add(new SpendingWeekdayMetrics(
                    weekday.name(),
                    aggregate == null ? 0L : aggregate.getAmount(),
                    aggregate == null ? 0L : aggregate.getTransactionCount()
            ));
        }
        return List.copyOf(result);
    }

    public List<SpendingTimeSlotMetrics> normalizeTimeSlots(List<SpendingTimeSlotAggregate> aggregates) {
        if (aggregates == null) {
            throw new IllegalArgumentException("시간대 집계 목록이 필요합니다.");
        }
        Map<String, SpendingTimeSlotAggregate> byTimeSlot = indexAndValidateTimeSlots(aggregates);

        List<SpendingTimeSlotMetrics> result = new ArrayList<>(SpendingTimeSlot.values().length);
        for (SpendingTimeSlot timeSlot : SpendingTimeSlot.values()) {
            SpendingTimeSlotAggregate aggregate = byTimeSlot.get(timeSlot.name());
            result.add(new SpendingTimeSlotMetrics(
                    timeSlot.name(),
                    aggregate == null ? 0L : aggregate.getAmount(),
                    aggregate == null ? 0L : aggregate.getTransactionCount()
            ));
        }
        return List.copyOf(result);
    }

    private Map<String, SpendingWeekdayAggregate> indexAndValidateWeekdays(
            List<SpendingWeekdayAggregate> aggregates
    ) {
        Map<String, SpendingWeekdayAggregate> byWeekday = new LinkedHashMap<>();
        for (SpendingWeekdayAggregate aggregate : aggregates) {
            if (aggregate == null) {
                throw new IllegalArgumentException("요일 집계 목록에 null 항목이 있습니다.");
            }
            String weekday = validateWeekday(aggregate.getWeekday());
            validateNonNegative(weekday, aggregate.getAmount(), aggregate.getTransactionCount());
            if (byWeekday.put(weekday, aggregate) != null) {
                throw new IllegalArgumentException("요일 집계 목록에 중복된 요일이 있습니다: " + weekday);
            }
        }
        return byWeekday;
    }

    private Map<String, SpendingTimeSlotAggregate> indexAndValidateTimeSlots(
            List<SpendingTimeSlotAggregate> aggregates
    ) {
        Map<String, SpendingTimeSlotAggregate> byTimeSlot = new LinkedHashMap<>();
        for (SpendingTimeSlotAggregate aggregate : aggregates) {
            if (aggregate == null) {
                throw new IllegalArgumentException("시간대 집계 목록에 null 항목이 있습니다.");
            }
            String timeSlot = validateTimeSlot(aggregate.getTimeSlot());
            validateNonNegative(timeSlot, aggregate.getAmount(), aggregate.getTransactionCount());
            if (byTimeSlot.put(timeSlot, aggregate) != null) {
                throw new IllegalArgumentException("시간대 집계 목록에 중복된 시간대가 있습니다: " + timeSlot);
            }
        }
        return byTimeSlot;
    }

    private String validateWeekday(String weekday) {
        if (weekday == null || weekday.isBlank()) {
            throw new IllegalArgumentException("요일 값이 필요합니다.");
        }
        try {
            return SpendingWeekday.valueOf(weekday).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("허용되지 않은 요일입니다: " + weekday, exception);
        }
    }

    private String validateTimeSlot(String timeSlot) {
        if (timeSlot == null || timeSlot.isBlank()) {
            throw new IllegalArgumentException("시간대 값이 필요합니다.");
        }
        try {
            return SpendingTimeSlot.valueOf(timeSlot).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("허용되지 않은 시간대입니다: " + timeSlot, exception);
        }
    }

    private void validateNonNegative(String code, long amount, long transactionCount) {
        if (amount < 0) {
            throw new IllegalArgumentException(code + " 금액은 음수가 될 수 없습니다: " + amount);
        }
        if (transactionCount < 0) {
            throw new IllegalArgumentException(code + " 거래 건수는 음수가 될 수 없습니다: " + transactionCount);
        }
    }
}
