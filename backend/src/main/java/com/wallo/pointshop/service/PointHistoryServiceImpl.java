package com.wallo.pointshop.service;

import com.wallo.pointshop.dto.response.PointHistoryItemResponse;
import com.wallo.pointshop.dto.response.PointHistoryResponse;
import com.wallo.pointshop.dto.response.PointHistorySummaryResponse;
import com.wallo.pointshop.mapper.PointHistoryMapper;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 포인트 내역 조회 파라미터를 검증하고 DB 조회 결과를 응답 DTO로 조합함. */
@Service
public class PointHistoryServiceImpl implements PointHistoryService {

    private static final String DEFAULT_TYPE = "ALL";
    private static final String DEFAULT_PERIOD = "ALL";
    private static final String DEFAULT_SORT = "LATEST";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private static final Set<String> ALLOWED_TYPES = Set.of("ALL", "EARN", "USE");
    private static final Set<String> ALLOWED_PERIODS = Set.of("ALL", "THIS_MONTH", "LAST_3_MONTHS");
    private static final Set<String> ALLOWED_SORTS = Set.of("LATEST", "OLDEST");

    private final PointHistoryMapper pointHistoryMapper;

    public PointHistoryServiceImpl(PointHistoryMapper pointHistoryMapper) {
        this.pointHistoryMapper = pointHistoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PointHistoryResponse getHistory(
            Long userId,
            String type,
            String period,
            String sort,
            String keyword,
            Integer page,
            Integer size) {
        validateUserId(userId);

        String normalizedType = normalize(type, DEFAULT_TYPE);
        String normalizedPeriod = normalize(period, DEFAULT_PERIOD);
        String normalizedSort = normalize(sort, DEFAULT_SORT);
        validate(normalizedType, ALLOWED_TYPES, "포인트 내역 유형");
        validate(normalizedPeriod, ALLOWED_PERIODS, "포인트 내역 기간");
        validate(normalizedSort, ALLOWED_SORTS, "포인트 내역 정렬");

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = calculateOffset(normalizedPage, normalizedSize);
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
                ? null
                : keyword.trim();
        String startDate = resolveStartDate(normalizedPeriod);

        PointHistorySummaryResponse summary = pointHistoryMapper.findSummary(userId);
        if (summary == null) {
            summary = emptySummary();
        }

        long totalElements = pointHistoryMapper.countItems(
                userId,
                normalizedType,
                startDate,
                normalizedKeyword);
        List<PointHistoryItemResponse> items = totalElements == 0
                ? Collections.emptyList()
                : pointHistoryMapper.findItems(
                        userId,
                        normalizedType,
                        startDate,
                        normalizedSort,
                        normalizedKeyword,
                        offset,
                        normalizedSize);

        return PointHistoryResponse.of(
                summary,
                items,
                normalizedPage,
                normalizedSize,
                totalElements);
    }

    private PointHistorySummaryResponse emptySummary() {
        PointHistorySummaryResponse summary = new PointHistorySummaryResponse();
        summary.setTotalEarned(0);
        summary.setTotalUsed(0);
        summary.setBalance(0);
        summary.setMonthlyChange(0);
        return summary;
    }

    private String resolveStartDate(String period) {
        LocalDate today = LocalDate.now();
        if ("THIS_MONTH".equals(period)) {
            return today.withDayOfMonth(1).toString();
        }
        if ("LAST_3_MONTHS".equals(period)) {
            return today.withDayOfMonth(1).minusMonths(2).toString();
        }
        return null;
    }

    private String normalize(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void validate(String value, Set<String> allowedValues, String fieldName) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException("지원하지 않는 " + fieldName + "입니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID가 올바르지 않습니다.");
        }
    }

    private int normalizePage(Integer page) {
        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        if (normalizedPage < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }
        return normalizedPage;
    }

    private int normalizeSize(Integer size) {
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        if (normalizedSize <= 0 || normalizedSize > MAX_SIZE) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        return normalizedSize;
    }

    private int calculateOffset(int page, int size) {
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("요청한 페이지 범위가 너무 큽니다.");
        }
        return (int) offset;
    }
}
