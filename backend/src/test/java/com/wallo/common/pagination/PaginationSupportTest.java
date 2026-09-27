package com.wallo.common.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PaginationSupportTest {

    @Test
    void usesDefaultsForMissingPageAndSize() {
        assertEquals(0, PaginationSupport.normalizePage(null, 0));
        assertEquals(20, PaginationSupport.normalizeSize(null, 20, 100));
    }

    @Test
    void acceptsValidPageAndSize() {
        assertEquals(3, PaginationSupport.normalizePage(3, 0));
        assertEquals(100, PaginationSupport.normalizeSize(100, 20, 100));
        assertEquals(300, PaginationSupport.calculateOffset(3, 100));
    }

    @Test
    void rejectsNegativePage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PaginationSupport.normalizePage(-1, 0));

        assertEquals("페이지 번호는 0 이상이어야 합니다.", exception.getMessage());
    }

    @Test
    void rejectsSizeOutsideAllowedRange() {
        IllegalArgumentException zeroSizeException = assertThrows(
                IllegalArgumentException.class,
                () -> PaginationSupport.normalizeSize(0, 20, 100));
        IllegalArgumentException oversizedException = assertThrows(
                IllegalArgumentException.class,
                () -> PaginationSupport.normalizeSize(101, 20, 100));

        assertEquals("페이지 크기는 1 이상 100 이하여야 합니다.", zeroSizeException.getMessage());
        assertEquals("페이지 크기는 1 이상 100 이하여야 합니다.", oversizedException.getMessage());
    }

    @Test
    void rejectsOffsetBeyondIntegerRange() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PaginationSupport.calculateOffset(Integer.MAX_VALUE, 2));

        assertEquals("요청한 페이지 범위가 너무 큽니다.", exception.getMessage());
    }
}
