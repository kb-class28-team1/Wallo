package com.wallo.feed.analysis;

import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 외부 영상/이미지 AI API 연결 지점.
 * 실제 API 도입 시 이 인터페이스의 구현체만 교체하면 된다.
 */
public interface FeedAnalysisClient {
    AnalysisResponse analyze(MultipartFile media, String spendingType, String category);
}
