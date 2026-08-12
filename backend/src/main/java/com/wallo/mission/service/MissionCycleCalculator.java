package com.wallo.mission.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

@Component
public class MissionCycleCalculator {
    private static final LocalDate ANCHOR_MONDAY = LocalDate.of(2026, 1, 5);

    public LocalDate cycleStart(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long weeks = ChronoUnit.WEEKS.between(ANCHOR_MONDAY, monday);
        return Math.floorMod(weeks, 2) == 0 ? monday : monday.minusWeeks(1);
    }

    public boolean isCycleStart(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.MONDAY && cycleStart(date).equals(date);
    }
}

