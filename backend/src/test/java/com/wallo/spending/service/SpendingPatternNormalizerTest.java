package com.wallo.spending.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wallo.spending.domain.SpendingTimeSlotMetrics;
import com.wallo.spending.domain.SpendingWeekdayMetrics;
import com.wallo.spending.dto.SpendingTimeSlotAggregate;
import com.wallo.spending.dto.SpendingWeekdayAggregate;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpendingPatternNormalizerTest {

    private final SpendingPatternNormalizer normalizer = new SpendingPatternNormalizer();

    private SpendingWeekdayAggregate weekday(String weekday, long amount, long transactionCount) {
        return new SpendingWeekdayAggregate(weekday, amount, transactionCount);
    }

    private SpendingTimeSlotAggregate timeSlot(String timeSlot, long amount, long transactionCount) {
        return new SpendingTimeSlotAggregate(timeSlot, amount, transactionCount);
    }

    private BigInteger sumAmounts(List<Long> amounts) {
        return amounts.stream().map(BigInteger::valueOf).reduce(BigInteger.ZERO, BigInteger::add);
    }

    // ==================== 요일 ====================

    @Test
    void weekdayEmptyInputYieldsSevenZeroEntries() {
        List<SpendingWeekdayMetrics> result = normalizer.normalizeWeekdays(List.of());

        assertEquals(7, result.size());
        assertEquals(
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                result.stream().map(SpendingWeekdayMetrics::weekday).toList()
        );
        assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L),
                result.stream().map(SpendingWeekdayMetrics::amount).toList());
        assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L),
                result.stream().map(SpendingWeekdayMetrics::transactionCount).toList());
    }

    @Test
    void weekdayPartialInputFillsRestWithZero() {
        List<SpendingWeekdayMetrics> result = normalizer.normalizeWeekdays(List.of(
                weekday("MONDAY", 10_000L, 2L),
                weekday("FRIDAY", 30_000L, 3L)
        ));

        assertEquals(7, result.size());
        SpendingWeekdayMetrics wednesday = result.stream()
                .filter(m -> m.weekday().equals("WEDNESDAY")).findFirst().orElseThrow();
        assertEquals(0L, wednesday.amount());
        assertEquals(0L, wednesday.transactionCount());
    }

    @Test
    void weekdayResultOrderIsAlwaysMondayToSundayRegardlessOfInputOrder() {
        List<SpendingWeekdayMetrics> result = normalizer.normalizeWeekdays(List.of(
                weekday("FRIDAY", 30_000L, 1L),
                weekday("MONDAY", 10_000L, 1L),
                weekday("SUNDAY", 5_000L, 1L)
        ));

        assertEquals(
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                result.stream().map(SpendingWeekdayMetrics::weekday).toList()
        );
    }

    @Test
    void weekdayPreservesInputAmountAndTransactionCount() {
        List<SpendingWeekdayMetrics> result = normalizer.normalizeWeekdays(List.of(
                weekday("MONDAY", 10_000L, 2L),
                weekday("FRIDAY", 30_000L, 3L)
        ));

        SpendingWeekdayMetrics monday = result.stream()
                .filter(m -> m.weekday().equals("MONDAY")).findFirst().orElseThrow();
        assertEquals(10_000L, monday.amount());
        assertEquals(2L, monday.transactionCount());

        SpendingWeekdayMetrics friday = result.stream()
                .filter(m -> m.weekday().equals("FRIDAY")).findFirst().orElseThrow();
        assertEquals(30_000L, friday.amount());
        assertEquals(3L, friday.transactionCount());
    }

    @Test
    void weekdayPreservesTotalSum() {
        List<SpendingWeekdayAggregate> input = List.of(
                weekday("MONDAY", 10_000L, 2L),
                weekday("WEDNESDAY", 7_000L, 1L),
                weekday("FRIDAY", 30_000L, 3L)
        );
        BigInteger inputAmountSum = sumAmounts(input.stream().map(SpendingWeekdayAggregate::getAmount).toList());
        BigInteger inputCountSum = sumAmounts(input.stream().map(SpendingWeekdayAggregate::getTransactionCount).toList());

        List<SpendingWeekdayMetrics> result = normalizer.normalizeWeekdays(input);

        BigInteger outputAmountSum = sumAmounts(result.stream().map(SpendingWeekdayMetrics::amount).toList());
        BigInteger outputCountSum = sumAmounts(result.stream().map(SpendingWeekdayMetrics::transactionCount).toList());

        assertEquals(inputAmountSum, outputAmountSum);
        assertEquals(inputCountSum, outputCountSum);
    }

    @Test
    void weekdayResultListIsImmutable() {
        List<SpendingWeekdayMetrics> result = normalizer.normalizeWeekdays(List.of());

        assertThrows(UnsupportedOperationException.class, () -> result.add(result.get(0)));
    }

    @Test
    void weekdayRejectsNullList() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalizeWeekdays(null));
    }

    @Test
    void weekdayRejectsNullItemInList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeWeekdays(Arrays.asList(weekday("MONDAY", 1_000L, 1L), null))
        );
    }

    @Test
    void weekdayRejectsNullWeekday() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeWeekdays(List.of(weekday(null, 1_000L, 1L)))
        );
    }

    @Test
    void weekdayRejectsBlankWeekday() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeWeekdays(List.of(weekday("   ", 1_000L, 1L)))
        );
    }

    @Test
    void weekdayRejectsDisallowedWeekday() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeWeekdays(List.of(weekday("FUNDAY", 1_000L, 1L)))
        );
    }

    @Test
    void weekdayRejectsDuplicateWeekday() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeWeekdays(List.of(
                        weekday("MONDAY", 1_000L, 1L),
                        weekday("MONDAY", 2_000L, 1L)
                ))
        );
    }

    @Test
    void weekdayRejectsNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeWeekdays(List.of(weekday("MONDAY", -1L, 1L)))
        );
    }

    @Test
    void weekdayRejectsNegativeTransactionCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeWeekdays(List.of(weekday("MONDAY", 1_000L, -1L)))
        );
    }

    // ==================== 시간대 ====================

    @Test
    void timeSlotEmptyInputYieldsFourZeroEntries() {
        List<SpendingTimeSlotMetrics> result = normalizer.normalizeTimeSlots(List.of());

        assertEquals(4, result.size());
        assertEquals(
                List.of("DAWN", "MORNING", "AFTERNOON", "EVENING"),
                result.stream().map(SpendingTimeSlotMetrics::timeSlot).toList()
        );
        assertEquals(List.of(0L, 0L, 0L, 0L), result.stream().map(SpendingTimeSlotMetrics::amount).toList());
        assertEquals(List.of(0L, 0L, 0L, 0L),
                result.stream().map(SpendingTimeSlotMetrics::transactionCount).toList());
    }

    @Test
    void timeSlotPartialInputFillsRestWithZero() {
        List<SpendingTimeSlotMetrics> result = normalizer.normalizeTimeSlots(List.of(
                timeSlot("MORNING", 20_000L, 2L)
        ));

        assertEquals(4, result.size());
        SpendingTimeSlotMetrics dawn = result.stream()
                .filter(m -> m.timeSlot().equals("DAWN")).findFirst().orElseThrow();
        assertEquals(0L, dawn.amount());
        assertEquals(0L, dawn.transactionCount());
    }

    @Test
    void timeSlotResultOrderIsAlwaysDawnToEveningRegardlessOfInputOrder() {
        List<SpendingTimeSlotMetrics> result = normalizer.normalizeTimeSlots(List.of(
                timeSlot("EVENING", 40_000L, 1L),
                timeSlot("DAWN", 1_000L, 1L),
                timeSlot("AFTERNOON", 20_000L, 1L)
        ));

        assertEquals(
                List.of("DAWN", "MORNING", "AFTERNOON", "EVENING"),
                result.stream().map(SpendingTimeSlotMetrics::timeSlot).toList()
        );
    }

    @Test
    void timeSlotPreservesInputAmountAndTransactionCount() {
        List<SpendingTimeSlotMetrics> result = normalizer.normalizeTimeSlots(List.of(
                timeSlot("MORNING", 20_000L, 2L),
                timeSlot("EVENING", 40_000L, 4L)
        ));

        SpendingTimeSlotMetrics morning = result.stream()
                .filter(m -> m.timeSlot().equals("MORNING")).findFirst().orElseThrow();
        assertEquals(20_000L, morning.amount());
        assertEquals(2L, morning.transactionCount());

        SpendingTimeSlotMetrics evening = result.stream()
                .filter(m -> m.timeSlot().equals("EVENING")).findFirst().orElseThrow();
        assertEquals(40_000L, evening.amount());
        assertEquals(4L, evening.transactionCount());
    }

    @Test
    void timeSlotPreservesTotalSum() {
        List<SpendingTimeSlotAggregate> input = List.of(
                timeSlot("DAWN", 500L, 1L),
                timeSlot("MORNING", 20_000L, 2L),
                timeSlot("EVENING", 40_000L, 4L)
        );
        BigInteger inputAmountSum = sumAmounts(input.stream().map(SpendingTimeSlotAggregate::getAmount).toList());
        BigInteger inputCountSum = sumAmounts(input.stream().map(SpendingTimeSlotAggregate::getTransactionCount).toList());

        List<SpendingTimeSlotMetrics> result = normalizer.normalizeTimeSlots(input);

        BigInteger outputAmountSum = sumAmounts(result.stream().map(SpendingTimeSlotMetrics::amount).toList());
        BigInteger outputCountSum = sumAmounts(result.stream().map(SpendingTimeSlotMetrics::transactionCount).toList());

        assertEquals(inputAmountSum, outputAmountSum);
        assertEquals(inputCountSum, outputCountSum);
    }

    @Test
    void timeSlotResultListIsImmutable() {
        List<SpendingTimeSlotMetrics> result = normalizer.normalizeTimeSlots(List.of());

        assertThrows(UnsupportedOperationException.class, () -> result.add(result.get(0)));
    }

    @Test
    void timeSlotRejectsNullList() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalizeTimeSlots(null));
    }

    @Test
    void timeSlotRejectsNullItemInList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeTimeSlots(Arrays.asList(timeSlot("MORNING", 1_000L, 1L), null))
        );
    }

    @Test
    void timeSlotRejectsNullTimeSlot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeTimeSlots(List.of(timeSlot(null, 1_000L, 1L)))
        );
    }

    @Test
    void timeSlotRejectsBlankTimeSlot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeTimeSlots(List.of(timeSlot("   ", 1_000L, 1L)))
        );
    }

    @Test
    void timeSlotRejectsDisallowedTimeSlot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeTimeSlots(List.of(timeSlot("MIDNIGHT", 1_000L, 1L)))
        );
    }

    @Test
    void timeSlotRejectsDuplicateTimeSlot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeTimeSlots(List.of(
                        timeSlot("MORNING", 1_000L, 1L),
                        timeSlot("MORNING", 2_000L, 1L)
                ))
        );
    }

    @Test
    void timeSlotRejectsNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeTimeSlots(List.of(timeSlot("MORNING", -1L, 1L)))
        );
    }

    @Test
    void timeSlotRejectsNegativeTransactionCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeTimeSlots(List.of(timeSlot("MORNING", 1_000L, -1L)))
        );
    }
}
