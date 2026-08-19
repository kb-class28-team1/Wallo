package com.wallo.report.dto.ai;

import java.time.LocalDateTime;
import java.util.Map;

/** Python AI 서버에 금융 리포트 생성을 요청할 때 보내는 값. */
public record NewsReportAiRequest(
        Long newsId,
        String title,
        String content,
        String category,
        String source,
        LocalDateTime publishedAt,
        String generationType,
        Map<String, Object> userProfile
) {
    public NewsReportAiRequest(Long newsId, String title, String content, String category, String source, LocalDateTime publishedAt) {
        this(newsId, title, content, category, source, publishedAt, "COMMON", null);
    }
}
