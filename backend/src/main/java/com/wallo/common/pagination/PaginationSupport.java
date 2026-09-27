package com.wallo.common.pagination;

/** 페이지 요청 값을 검증하고 MyBatis 조회에 사용할 offset을 계산하는 공통 유틸리티임. */
public final class PaginationSupport {

    private PaginationSupport() {
    }

    public static int normalizePage(Integer page, int defaultPage) {
        int normalizedPage = page == null ? defaultPage : page;
        if (normalizedPage < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }
        return normalizedPage;
    }

    public static int normalizeSize(Integer size, int defaultSize, int maxSize) {
        int normalizedSize = size == null ? defaultSize : size;
        if (normalizedSize <= 0 || normalizedSize > maxSize) {
            throw new IllegalArgumentException(
                    "페이지 크기는 1 이상 " + maxSize + " 이하여야 합니다.");
        }
        return normalizedSize;
    }

    public static int calculateOffset(int page, int size) {
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("요청한 페이지 범위가 너무 큽니다.");
        }
        return (int) offset;
    }
}
