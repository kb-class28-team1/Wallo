package com.wallo.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MissionCycleCalculatorTest {
    private final MissionCycleCalculator calculator = new MissionCycleCalculator();

    @Test
    void keepsFourteenDaysInSameCycle() {
        assertEquals(LocalDate.of(2026, 8, 17),
                calculator.cycleStart(LocalDate.of(2026, 8, 17)));
        assertEquals(LocalDate.of(2026, 8, 17),
                calculator.cycleStart(LocalDate.of(2026, 8, 30)));
        assertEquals(LocalDate.of(2026, 8, 31),
                calculator.cycleStart(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void identifiesOnlyBiweeklyMondayAsCycleStart() {
        assertTrue(calculator.isCycleStart(LocalDate.of(2026, 8, 17)));
        assertFalse(calculator.isCycleStart(LocalDate.of(2026, 8, 24)));
        assertTrue(calculator.isCycleStart(LocalDate.of(2026, 8, 31)));
    }
}
