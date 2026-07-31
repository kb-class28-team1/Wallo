package com.wallo.service;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.domain.News;
import com.wallo.mapper.NewsMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewsServiceImplTest {

    @Test
    void getNewsByIdOrThrowReturnsNewsWhenFound() {
        FakeNewsMapper mapper = new FakeNewsMapper();
        News news = News.builder()
                .newsId(1L)
                .title("제목")
                .content("본문")
                .source("매일경제")
                .url("https://example.com/1")
                .category("경제")
                .publishedAt(LocalDateTime.of(2026, 7, 30, 12, 0))
                .build();
        mapper.newsById.put(1L, news);
        NewsService service = new NewsServiceImpl(mapper);

        News result = service.getNewsByIdOrThrow(1L);

        assertEquals(1L, result.getNewsId());
        assertEquals("제목", result.getTitle());
    }

    @Test
    void getNewsByIdOrThrowThrowsCustomExceptionWhenNotFound() {
        FakeNewsMapper mapper = new FakeNewsMapper();
        NewsService service = new NewsServiceImpl(mapper);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getNewsByIdOrThrow(999L));

        assertEquals(ErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Mapper다. */
    private static class FakeNewsMapper implements NewsMapper {

        private final Map<Long, News> newsById = new HashMap<>();

        @Override
        public int insertNews(News news) {
            return 0;
        }

        @Override
        public int existsByUrl(String url) {
            return 0;
        }

        @Override
        public News findById(Long newsId) {
            return newsById.get(newsId);
        }

        @Override
        public List<News> findLatest(int limit) {
            return new ArrayList<>();
        }

        @Override
        public List<News> findAll() {
            return new ArrayList<>();
        }
    }
}
