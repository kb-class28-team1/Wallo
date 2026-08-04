package com.wallo.report.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * GET /api/reports 목록 조회 전용 Projection.
 * news와 news_report를 INNER JOIN한 결과 1행이다. news_report가 있는 뉴스만 조회 대상이라
 * summary는 항상 채워지고 analyzed는 항상 true다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NewsReportListItem {

    private Long newsId;
    private String title;
    private String category;
    private String source;
    private String url;
    private LocalDateTime publishedAt;
    private String summary;
    private Boolean analyzed;
}
