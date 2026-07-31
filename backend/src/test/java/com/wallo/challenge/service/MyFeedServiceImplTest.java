package com.wallo.challenge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wallo.challenge.domain.MyFeed;
import com.wallo.challenge.dto.response.MyFeedListResponse;
import com.wallo.challenge.mapper.MyFeedMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyFeedServiceImplTest {

    @Test
    void usesDefaultConditionsWhenOptionalValuesAreMissing() {
        FakeMyFeedMapper mapper = new FakeMyFeedMapper();
        mapper.totalElements = 1;
        mapper.feeds.add(feed(1L));
        MyFeedService service = new MyFeedServiceImpl(mapper);

        MyFeedListResponse response = service.getMyFeeds(10L, null, null, null, null);

        assertEquals("LIKE_DESC", mapper.requestedSort);
        assertEquals("ALL", mapper.requestedCategory);
        assertEquals(0, mapper.requestedOffset);
        assertEquals(10, mapper.requestedSize);
        assertEquals(1, response.getContent().size());
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void normalizesConditionsAndCalculatesPageOffset() {
        FakeMyFeedMapper mapper = new FakeMyFeedMapper();
        mapper.totalElements = 11;
        mapper.feeds.add(feed(11L));
        MyFeedService service = new MyFeedServiceImpl(mapper);

        MyFeedListResponse response = service.getMyFeeds(10L, " latest ", " cafe ", 2, 5);

        assertEquals("LATEST", mapper.requestedSort);
        assertEquals("CAFE", mapper.requestedCategory);
        assertEquals(10, mapper.requestedOffset);
        assertEquals(5, mapper.requestedSize);
        assertEquals(3, response.getTotalPages());
        assertEquals(false, response.isHasNext());
    }

    @Test
    void skipsListQueryWhenNoFeedExists() {
        FakeMyFeedMapper mapper = new FakeMyFeedMapper();
        MyFeedService service = new MyFeedServiceImpl(mapper);

        MyFeedListResponse response = service.getMyFeeds(10L, "LIKE_DESC", "ALL", 0, 10);

        assertEquals(0, mapper.listQueryCount);
        assertEquals(0, response.getContent().size());
        assertEquals(0, response.getTotalPages());
    }

    @Test
    void rejectsUnsupportedSortCondition() {
        MyFeedService service = new MyFeedServiceImpl(new FakeMyFeedMapper());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getMyFeeds(10L, "UNKNOWN", "ALL", 0, 10));
    }

    @Test
    void rejectsInvalidPagingCondition() {
        MyFeedService service = new MyFeedServiceImpl(new FakeMyFeedMapper());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getMyFeeds(10L, "LIKE_DESC", "ALL", -1, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getMyFeeds(10L, "LIKE_DESC", "ALL", 0, 0));
    }

    private MyFeed feed(Long feedId) {
        MyFeed feed = new MyFeed();
        feed.setFeedId(feedId);
        feed.setChallengeId(1L);
        feed.setCaption("절약 인증 게시물");
        return feed;
    }

    /** 실제 DB 없이 서비스가 Mapper에 전달하는 조회 조건을 확인하기 위한 테스트용 Mapper임. */
    private static class FakeMyFeedMapper implements MyFeedMapper {

        private final List<MyFeed> feeds = new ArrayList<>();
        private long totalElements;
        private String requestedSort;
        private String requestedCategory;
        private int requestedOffset;
        private int requestedSize;
        private int listQueryCount;

        @Override
        public List<MyFeed> findMyFeeds(
                Long userId,
                String sort,
                String category,
                int offset,
                int size) {
            requestedSort = sort;
            requestedCategory = category;
            requestedOffset = offset;
            requestedSize = size;
            listQueryCount++;
            return feeds;
        }

        @Override
        public long countMyFeeds(Long userId, String category) {
            requestedCategory = category;
            return totalElements;
        }
    }
}
