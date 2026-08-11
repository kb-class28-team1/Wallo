package com.wallo.external;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Optional;
import java.util.OptionalInt;

/** Common parser for CODEF's compact date, time, and year values. */
public final class CodefDateTime {

    public static final DateTimeFormatter BASIC_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    public static final DateTimeFormatter BASIC_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter();

    private CodefDateTime() {
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("CODEF date is required.");
        }
        return date.format(BASIC_DATE_FORMATTER);
    }

    public static LocalDate parseDate(String value, String fieldName) {
        if (!isEightDigitValue(value)) {
            throw new IllegalArgumentException(fieldName + " must be a valid YYYYMMDD value.");
        }
        try {
            return LocalDate.parse(value, BASIC_DATE_FORMATTER);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid YYYYMMDD value.", exception);
        }
    }

    public static LocalDate parseIsoDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid ISO date.", exception);
        }
    }

    public static LocalTime parseTime(String value, String fieldName) {
        if (!isSixDigitValue(value)) {
            throw new IllegalArgumentException(fieldName + " must be a valid HHmmss value.");
        }
        try {
            return LocalTime.parse(value, BASIC_TIME_FORMATTER);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid HHmmss value.", exception);
        }
    }

    public static LocalTime parseIsoTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid ISO time.", exception);
        }
    }

    public static YearMonth parseYearMonth(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid YYYY-MM value.", exception);
        }
    }

    public static Optional<LocalDate> tryParseDate(String value) {
        if (!isEightDigitValue(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value, BASIC_DATE_FORMATTER));
        } catch (DateTimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<DateRange> parseDateRange(String startDate, String endDate) {
        Optional<LocalDate> parsedStartDate = tryParseDate(startDate);
        Optional<LocalDate> parsedEndDate = tryParseDate(endDate);
        if (parsedStartDate.isEmpty() || parsedEndDate.isEmpty()
                || parsedStartDate.get().isAfter(parsedEndDate.get())) {
            return Optional.empty();
        }
        return Optional.of(new DateRange(parsedStartDate.get(), parsedEndDate.get()));
    }

    public static OptionalInt parseYear(String value) {
        if (value == null || !value.matches("\\d{4}")) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private static boolean isEightDigitValue(String value) {
        return value != null && value.matches("\\d{8}");
    }

    private static boolean isSixDigitValue(String value) {
        return value != null && value.matches("\\d{6}");
    }

    public record DateRange(LocalDate startDate, LocalDate endDate) {

        public boolean contains(String basicIsoDate) {
            return tryParseDate(basicIsoDate)
                    .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
                    .isPresent();
        }
    }
}
