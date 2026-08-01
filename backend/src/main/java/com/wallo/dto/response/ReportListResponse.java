package com.wallo.dto.response;

import com.wallo.domain.NewsReportListItem;

import java.time.LocalDateTime;

/**
 * GET /api/reports 목록의 항목 하나.
 * news와 news_report를 LEFT JOIN한 결과(NewsReportListItem)를 기반으로 한다.
 * 리포트가 생성된 뉴스는 summary가 채워지고 analyzed가 true, 아직 없으면 summary는 null이고 analyzed는 false다.
 */
public class ReportListResponse {

    private final Long id;
    private final String title;
    private final String summary;
    private final String category;
    private final String source;
    private final String url;
    private final String thumbnailUrl;
    private final LocalDateTime publishedAt;
    private final boolean analyzed;

    private ReportListResponse(NewsReportListItem item) {
        this.id = item.getNewsId();
        this.title = item.getTitle();
        this.summary = item.getSummary();
        this.category = item.getCategory();
        this.source = item.getSource();
        this.url = item.getUrl();
        // 현재 뉴스 크롤러는 썸네일 URL을 수집·저장하지 않고, news 테이블에도 썸네일 URL 컬럼이 없다.
        // 향후 크롤러와 DB 구조를 확장하면 저장된 썸네일 URL을 반환할 예정이다.
        this.thumbnailUrl = null;
        this.publishedAt = item.getPublishedAt();
        this.analyzed = Boolean.TRUE.equals(item.getAnalyzed());
    }

    public static ReportListResponse from(NewsReportListItem item) {
        return new ReportListResponse(item);
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

    public boolean isAnalyzed() {
        return analyzed;
    }
}
