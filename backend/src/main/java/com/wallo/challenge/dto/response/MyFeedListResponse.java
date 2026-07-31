package com.wallo.challenge.dto.response;

import com.wallo.challenge.domain.MyFeed;
import java.util.List;
import java.util.stream.Collectors;

/** 내 게시물 목록과 페이지 정보를 함께 반환하는 응답임. */
public class MyFeedListResponse {

    private final List<MyFeedItemResponse> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;

    private MyFeedListResponse(
            List<MyFeed> feeds,
            int page,
            int size,
            long totalElements) {
        this.content = feeds.stream()
                .map(MyFeedItemResponse::from)
                .collect(Collectors.toList());
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = calculateTotalPages(totalElements, size);
        this.hasNext = page + 1 < totalPages;
    }

    /** 조회 목록과 전체 개수를 이용해 페이지 응답을 생성함. */
    public static MyFeedListResponse of(
            List<MyFeed> feeds,
            int page,
            int size,
            long totalElements) {
        return new MyFeedListResponse(feeds, page, size, totalElements);
    }

    private static int calculateTotalPages(long totalElements, int size) {
        if (totalElements == 0 || size <= 0) {
            return 0;
        }

        return (int) Math.ceil((double) totalElements / size);
    }

    public List<MyFeedItemResponse> getContent() {
        return content;
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
