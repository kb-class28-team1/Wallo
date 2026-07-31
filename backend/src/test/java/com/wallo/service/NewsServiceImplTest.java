package com.wallo.service;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.domain.News;
import com.wallo.domain.NewsReport;
import com.wallo.dto.response.ReportDetailResponse;
import com.wallo.mapper.NewsMapper;
import com.wallo.mapper.NewsReportMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        NewsService service = new NewsServiceImpl(mapper, new FakeNewsReportMapper());

        News result = service.getNewsByIdOrThrow(1L);

        assertEquals(1L, result.getNewsId());
        assertEquals("제목", result.getTitle());
    }

    @Test
    void getNewsByIdOrThrowThrowsCustomExceptionWhenNotFound() {
        FakeNewsMapper mapper = new FakeNewsMapper();
        NewsService service = new NewsServiceImpl(mapper, new FakeNewsReportMapper());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getNewsByIdOrThrow(999L));

        assertEquals(ErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getReportDetailFillsAiFieldsWhenNewsReportExists() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        newsMapper.newsById.put(1L, news(1L));
        newsReportMapper.reportByNewsId.put(1L, NewsReport.builder()
                .reportId(1L)
                .newsId(1L)
                .summary("요약")
                .cause("원인")
                .socialImpact("사회적 영향")
                .userImpact("사용자 영향")
                .responseStrategy("대응 방안")
                .build());
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper);

        ReportDetailResponse response = service.getReportDetail(1L);

        assertEquals(1L, response.getNewsId());
        assertEquals("요약", response.getSummary());
        assertEquals("원인", response.getCause());
        assertEquals("사회적 영향", response.getSocialImpact());
        assertEquals("사용자 영향", response.getUserImpact());
        assertEquals("대응 방안", response.getActionPlan());
        assertTrue(response.getTerms().isEmpty());
    }

    @Test
    void getReportDetailReturnsNullAiFieldsWhenNewsReportDoesNotExist() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        newsMapper.newsById.put(1L, news(1L));
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper);

        ReportDetailResponse response = service.getReportDetail(1L);

        assertEquals(1L, response.getNewsId());
        assertNull(response.getSummary());
        assertNull(response.getCause());
        assertNull(response.getSocialImpact());
        assertNull(response.getUserImpact());
        assertNull(response.getActionPlan());
        assertTrue(response.getTerms().isEmpty());
    }

    @Test
    void getReportDetailThrowsCustomExceptionWhenNewsDoesNotExist() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getReportDetail(999L));

        assertEquals(ErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
    }

    private News news(Long newsId) {
        return News.builder()
                .newsId(newsId)
                .title("제목")
                .content("본문")
                .source("매일경제")
                .url("https://example.com/" + newsId)
                .category("경제")
                .publishedAt(LocalDateTime.of(2026, 7, 30, 12, 0))
                .build();
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

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Mapper다. */
    private static class FakeNewsReportMapper implements NewsReportMapper {

        private final Map<Long, NewsReport> reportByNewsId = new HashMap<>();

        @Override
        public NewsReport findByNewsId(Long newsId) {
            return reportByNewsId.get(newsId);
        }

        @Override
        public int insert(NewsReport newsReport) {
            throw new UnsupportedOperationException();
        }
    }
}
