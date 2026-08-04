package com.wallo.pointshop.dto.response;

import java.util.Collections;
import java.util.List;

/** 포인트 내역 요약, 목록, 페이지 정보를 한 번에 반환하는 DTO임. */
public class PointHistoryResponse {

    private final PointHistorySummaryResponse summary;
    private final List<PointHistoryItemResponse> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;

    private PointHistoryResponse(
            PointHistorySummaryResponse summary,
            List<PointHistoryItemResponse> items,
            int page,
            int size,
            long totalElements) {
        this.summary = summary;
        this.items = items == null ? Collections.emptyList() : items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        this.hasNext = page + 1 < totalPages;
    }

    public static PointHistoryResponse of(
            PointHistorySummaryResponse summary,
            List<PointHistoryItemResponse> items,
            int page,
            int size,
            long totalElements) {
        return new PointHistoryResponse(summary, items, page, size, totalElements);
    }

    public PointHistorySummaryResponse getSummary() {
        return summary;
    }

    public List<PointHistoryItemResponse> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isHasNext() {
        return hasNext;
    }
}
