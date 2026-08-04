package com.wallo.report.scheduler;

import com.wallo.report.mapper.NewsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 금융 리포트 목록에 노출되는 뉴스 데이터를 최근 N일치만 보관한다.
 *
 * <p>news_report/news_term이 news를 참조하므로, 삭제는 news_term → news_report → news 순서로 수행한다.
 */
@Component
public class NewsRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewsRetentionScheduler.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final NewsMapper newsMapper;
    private final int retentionDays;

    public NewsRetentionScheduler(
            NewsMapper newsMapper,
            @Value("${news.retention-days:3}") int retentionDays
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("news.retention-days는 1 이상이어야 합니다. 입력값: " + retentionDays);
        }

        this.newsMapper = newsMapper;
        this.retentionDays = retentionDays;
    }

    /**
     * 매일 00:10에 최근 retentionDays일 범위를 벗어난 뉴스와 관련 리포트/용어 매칭을 삭제한다.
     */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void purgeExpiredNews() {
        LocalDateTime cutoff = calculateCutoff(LocalDate.now(KOREA_ZONE), retentionDays);

        int deletedTerms = newsMapper.deleteNewsTermsBeforePublishedAt(cutoff);
        int deletedReports = newsMapper.deleteNewsReportsBeforePublishedAt(cutoff);
        int deletedNews = newsMapper.deleteNewsBeforePublishedAt(cutoff);

        log.info(
                "뉴스 보관 기간 정리 완료 - cutoff: {}, news_term {}건, news_report {}건, news {}건 삭제",
                cutoff, deletedTerms, deletedReports, deletedNews
        );
    }

    static LocalDateTime calculateCutoff(LocalDate today, int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays는 1 이상이어야 합니다. 입력값: " + retentionDays);
        }

        return today.minusDays(retentionDays - 1L).atStartOfDay();
    }
}
