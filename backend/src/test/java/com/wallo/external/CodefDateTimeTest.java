package com.wallo.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CodefDateTimeTest {

    @Test
    void parsesAndFormatsCompactDate() {
        LocalDate date = CodefDateTime.parseDate("20260731", "startDate");

        assertEquals(LocalDate.of(2026, 7, 31), date);
        assertEquals("20260731", CodefDateTime.formatDate(date));
    }

    @Test
    void parsesCompactTime() {
        assertEquals(
                LocalTime.of(19, 30),
                CodefDateTime.parseTime("193000", "resUsedTime")
        );
    }

    @Test
    void rejectsInvalidCompactValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CodefDateTime.parseDate("20260230", "startDate")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CodefDateTime.parseTime("246000", "resUsedTime")
        );
    }

    @Test
    void validatesDateRangeAndYear() {
        assertEquals(
                LocalDate.of(2026, 7, 1),
                CodefDateTime.parseDateRange("20260701", "20260731")
                        .orElseThrow()
                        .startDate()
        );
        assertFalse(CodefDateTime.parseDateRange("20260731", "20260701").isPresent());
        assertEquals(2025, CodefDateTime.parseYear("2025").orElseThrow());
        assertFalse(CodefDateTime.parseYear("25").isPresent());
        assertEquals(YearMonth.of(2026, 7), CodefDateTime.parseYearMonth("2026-07", "snapshotMonth"));
    }
}
