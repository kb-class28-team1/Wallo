package com.wallo.report.scheduler;

import com.wallo.report.domain.News;
import com.wallo.report.domain.NewsReportListItem;
import com.wallo.report.mapper.NewsMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewsRetentionSchedulerTest {

    @Test
    void calculatesCutoffIncludingTodayForThreeDays() {
        LocalDateTime cutoff = NewsRetentionScheduler.calculateCutoff(LocalDate.of(2026, 8, 4), 3);

        assertEquals(LocalDateTime.of(2026, 8, 2, 0, 0), cutoff);
    }

    @Test
    void rejectsInvalidRetentionDays() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();

        assertThrows(IllegalArgumentException.class, () -> new NewsRetentionScheduler(newsMapper, 0));
        assertThrows(IllegalArgumentException.class, () -> NewsRetentionScheduler.calculateCutoff(LocalDate.now(), 0));
    }

    @Test
    void purgesDependentRowsBeforeNewsRows() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        NewsRetentionScheduler scheduler = new NewsRetentionScheduler(newsMapper, 3);

        scheduler.purgeExpiredNews();

        assertEquals(List.of("news_term", "news_report", "news"), newsMapper.deleteOrder);
    }

    private static class FakeNewsMapper implements NewsMapper {

        private final List<String> deleteOrder = new ArrayList<>();

        @Override
        public int insertNews(News news) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int existsByUrl(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public News findById(Long newsId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<News> findLatest(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NewsReportListItem> findAllWithReportSummary() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> findNewsIdsWithoutReport(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteNewsTermsBeforePublishedAt(LocalDateTime cutoff) {
            deleteOrder.add("news_term");
            return 1;
        }

        @Override
        public int deleteNewsReportsBeforePublishedAt(LocalDateTime cutoff) {
            deleteOrder.add("news_report");
            return 1;
        }

        @Override
        public int deleteNewsBeforePublishedAt(LocalDateTime cutoff) {
            deleteOrder.add("news");
            return 1;
        }
    }
}
