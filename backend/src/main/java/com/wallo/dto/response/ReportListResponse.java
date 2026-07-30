package com.wallo.dto.response;

import com.wallo.domain.News;

import java.time.LocalDateTime;

/** GET /api/reports 목록의 항목 하나. news 테이블 원본 데이터를 기반으로 한다. */
public class ReportListResponse {

    private final Long id;
    private final String title;
    private final String summary;
    private final String category;
    private final String source;
    private final String url;
    private final String thumbnailUrl;
    private final LocalDateTime publishedAt;

    private ReportListResponse(News news) {
        this.id = news.getNewsId();
        this.title = news.getTitle();
        // 현재 news 테이블에는 AI 요약을 저장하는 컬럼이 없다.
        // 향후 AI 요약 생성 및 저장 기능을 구현하면 해당 값을 반환할 예정이다.
        this.summary = null;
        this.category = news.getCategory();
        this.source = news.getSource();
        this.url = news.getUrl();
        // 현재 뉴스 크롤러는 썸네일 URL을 수집·저장하지 않고, news 테이블에도 썸네일 URL 컬럼이 없다.
        // 향후 크롤러와 DB 구조를 확장하면 저장된 썸네일 URL을 반환할 예정이다.
        this.thumbnailUrl = null;
        this.publishedAt = news.getPublishedAt();
    }

    public static ReportListResponse from(News news) {
        return new ReportListResponse(news);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getCategory() {
        return category;
    }

    public String getSource() {
        return source;
    }

    public String getUrl() {
        return url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}
