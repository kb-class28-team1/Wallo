package com.wallo.report.dto.response;

import com.wallo.report.domain.News;
import com.wallo.report.domain.NewsReport;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GET /api/reports/{newsId} 상세 응답.
 * news 테이블 원본 데이터에 news_report(AI 가공 결과)를 함께 채운다.
 * news_report가 아직 생성되지 않은 뉴스는 AI 관련 필드가 null(summaryPoints/terms는 빈 배열)로 내려간다.
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

    private final List<String> summaryPoints;
    private final String eventDescription;
    private final String cause;
    private final String socialImpact;
    private final String userImpact;
    private final String actionPlan;
    private final List<MatchedTermResponse> terms;

    private ReportDetailResponse(News news, NewsReport newsReport, List<MatchedTermResponse> terms) {
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

        // news_report가 아직 생성되지 않은 뉴스(AI 가공 전)는 newsReport가 null로 들어와 아래 필드가 전부 null(summaryPoints는 빈 배열)로 내려간다.
        if (newsReport != null) {
            this.summaryPoints = parseSummaryPoints(newsReport.getSummary());
            this.eventDescription = newsReport.getEventDescription();
            this.cause = newsReport.getCause();
            this.socialImpact = newsReport.getSocialImpact();
            this.userImpact = newsReport.getUserImpact();
            this.actionPlan = newsReport.getResponseStrategy();
        } else {
            this.summaryPoints = Collections.emptyList();
            this.eventDescription = null;
            this.cause = null;
            this.socialImpact = null;
            this.userImpact = null;
            this.actionPlan = null;
        }
        // terms가 null로 들어오면(매칭 결과가 없는 경우 포함) 빈 배열로 내려간다.
        this.terms = terms != null ? terms : Collections.emptyList();
    }

    /**
     * news_report.summary는 TEXT 컬럼 하나에 bullet 문장을 줄바꿈으로 이어붙여 저장한다
     * (NewsReportGenerationServiceImpl 참고). 여기서 다시 줄바꿈 기준으로 분리해 목록으로 돌려준다.
     * 줄바꿈이 없는 값(마이그레이션 이전의 기존 리포트는 summary가 문단 하나였다)은 항목 1개짜리
     * 목록으로 안전하게 표시된다.
     */
    private static List<String> parseSummaryPoints(String rawSummary) {
        if (rawSummary == null || rawSummary.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawSummary.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    public static ReportDetailResponse from(News news, NewsReport newsReport, List<MatchedTermResponse> terms) {
        return new ReportDetailResponse(news, newsReport, terms);
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

    public List<String> getSummaryPoints() {
        return summaryPoints;
    }

    public String getEventDescription() {
        return eventDescription;
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

    public List<MatchedTermResponse> getTerms() {
        return terms;
    }
}
