package com.wallo.challenge.service;

import com.wallo.challenge.domain.MyFeed;
import com.wallo.challenge.dto.response.MyFeedListResponse;
import com.wallo.challenge.mapper.MyFeedMapper;
import com.wallo.common.pagination.PaginationSupport;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 게시물 조회 조건을 검증하고 MyBatis 조회 결과를 응답 DTO로 변환하는 서비스임. */
@Service
public class MyFeedServiceImpl implements MyFeedService {

    private static final String DEFAULT_SORT = "LIKE_DESC";
    private static final String DEFAULT_CATEGORY = "ALL";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    // SQL에서 허용하는 정렬 조건만 Mapper에 전달하도록 제한함.
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "LATEST",
            "LIKE_DESC",
            "SAVING_DESC");

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "ALL", "FOOD", "CAFE", "TRANSPORT", "SHOPPING", "DELIVERY",
            "HOUSING", "LIVING", "CULTURE", "HEALTH", "ETC");

    private final MyFeedMapper myFeedMapper;

    public MyFeedServiceImpl(MyFeedMapper myFeedMapper) {
        this.myFeedMapper = myFeedMapper;
    }

    /**
     * 요청 조건을 표준 형식으로 변환한 뒤 전체 개수와 현재 페이지의 게시물을 조회함.
     * 조회 결과가 없으면 목록 쿼리를 생략하고 빈 페이지 응답을 반환함.
     */
    @Override
    @Transactional(readOnly = true)
    public MyFeedListResponse getMyFeeds(
            Long userId,
            String sort,
            String category,
            Integer page,
            Integer size) {
        validateUserId(userId);

        String normalizedSort = normalizeSort(sort);
        String normalizedCategory = normalizeCategory(category);
        int normalizedPage = PaginationSupport.normalizePage(page, DEFAULT_PAGE);
        int normalizedSize = PaginationSupport.normalizeSize(size, DEFAULT_SIZE, MAX_SIZE);
        int offset = PaginationSupport.calculateOffset(normalizedPage, normalizedSize);

        long totalElements = myFeedMapper.countMyFeeds(userId, normalizedCategory);
        List<MyFeed> feeds = totalElements == 0
                ? Collections.emptyList()
                : myFeedMapper.findMyFeeds(
                        userId,
                        normalizedSort,
                        normalizedCategory,
                        offset,
                        normalizedSize);

        return MyFeedListResponse.of(
                feeds,
                normalizedPage,
                normalizedSize,
                totalElements);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID가 올바르지 않습니다.");
        }
    }

    private String normalizeSort(String sort) {
        String normalizedSort = normalizeOrDefault(sort, DEFAULT_SORT);
        if (!ALLOWED_SORTS.contains(normalizedSort)) {
            throw new IllegalArgumentException("지원하지 않는 게시물 정렬 조건입니다.");
        }
        return normalizedSort;
    }

    private String normalizeCategory(String category) {
        String normalizedCategory = normalizeOrDefault(category, DEFAULT_CATEGORY);
        if (!ALLOWED_CATEGORIES.contains(normalizedCategory)) {
            throw new IllegalArgumentException("지원하지 않는 게시물 카테고리입니다.");
        }
        return normalizedCategory;
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

}
