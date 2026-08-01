package com.wallo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * GET /api/reports 목록 조회 전용 Projection.
 * news와 news_report를 LEFT JOIN한 결과 1행이다. news_report가 없는 뉴스는 summary가 null이고
 * analyzed가 false다.
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
