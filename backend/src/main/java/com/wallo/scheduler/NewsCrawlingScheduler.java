package com.wallo.scheduler;

import com.wallo.crawler.NewsCrawler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 4회(06/12/15/18시, Asia/Seoul) {@link NewsCrawler#crawlAndSave()}를 자동 실행한다.
 * 증시 일정이 아니라, 사용자가 하루 동안 접하는 경제·생활금융 뉴스를 최신 상태로 유지하는 것이 목적이다.
 */
@Component
public class NewsCrawlingScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewsCrawlingScheduler.class);

    private final NewsCrawler newsCrawler;

    public NewsCrawlingScheduler(NewsCrawler newsCrawler) {
        this.newsCrawler = newsCrawler;
    }

    @Scheduled(cron = "0 0 6,12,15,18 * * *", zone = "Asia/Seoul")
    public void scheduledNewsCrawling() {
        log.info("===== 뉴스 크롤링 시작 =====");

        try {
            newsCrawler.crawlAndSave();
        } catch (Exception e) {
            log.error("===== 뉴스 크롤링 실패 =====", e);
        } finally {
            log.info("===== 뉴스 크롤링 종료 =====");
        }
    }
}
