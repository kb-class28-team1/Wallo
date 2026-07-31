package com.wallo.dto.response;

import com.wallo.domain.News;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * GET /api/reports/{newsId} 상세 응답.
 * 현재는 news 테이블 원본 데이터만 채우고, news_report(AI 가공 결과) 연동 전이라
 * AI 관련 필드는 항상 null 또는 빈 배열로 내려간다.
 */
public class ReportDetailResponse {

    private final Long newsId;
    private final String title;
    private final String content;
    private final String source;
    private final String url;
    private final String thumbnailUrl;
    private final String category;
    private final LocalDateTime publishedAt;

    private final String summary;
    private final String cause;
    private final String socialImpact;
    private final String userImpact;
    private final String actionPlan;
    private final List<String> terms;

    private ReportDetailResponse(News news) {
        this.newsId = news.getNewsId();
        this.title = news.getTitle();
        this.content = news.getContent();
        this.source = news.getSource();
        this.url = news.getUrl();
        // 현재 뉴스 크롤러는 썸네일 URL을 수집·저장하지 않고, news 테이블에도 썸네일 URL 컬럼이 없다.
        // 향후 크롤러와 DB 구조를 확장하면 저장된 썸네일 URL을 반환할 예정이다.
        this.thumbnailUrl = null;
        this.category = news.getCategory();
        this.publishedAt = news.getPublishedAt();

        // 아래 필드들은 news_report(AI 가공 결과) 테이블과 아직 연결하지 않았다.
        // 향후 AI 요약·분석 생성 및 저장 기능을 구현하면 해당 값을 반환할 예정이다.
        this.summary = null;
        this.cause = null;
        this.socialImpact = null;
        this.userImpact = null;
        this.actionPlan = null;
        this.terms = Collections.emptyList();
    }

    public static ReportDetailResponse from(News news) {
        return new ReportDetailResponse(news);
    }

    public Long getNewsId() {
        return newsId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
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

    public String getCategory() {
        return category;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getSummary() {
        return summary;
    }

    public String getCause() {
        return cause;
    }

    public String getSocialImpact() {
        return socialImpact;
    }

    public String getUserImpact() {
        return userImpact;
    }

    public String getActionPlan() {
        return actionPlan;
    }

    public List<String> getTerms() {
        return terms;
    }
}
