package com.wallo.service;

import com.wallo.domain.NewsReport;

/** AI 금융 리포트 생성과 news_report 저장을 담당한다. */
public interface NewsReportGenerationService {

    /**
     * news_id에 해당하는 news_report가 이미 있으면 AI를 다시 호출하지 않고 기존 값을 그대로 반환한다.
     * 없으면 Python AI 서버를 호출해 생성한 뒤 news_report에 저장하고 그 값을 반환한다.
     *
     * @throws com.wallo.common.exception.CustomException 아래 경우에 발생한다.
     *         뉴스가 없음({@link com.wallo.common.exception.ErrorCode#REPORT_NOT_FOUND}),
     *         본문이 비어 있음({@link com.wallo.common.exception.ErrorCode#REPORT_CONTENT_EMPTY}),
     *         AI 호출 실패({@link com.wallo.common.exception.ErrorCode#AI_REPORT_GENERATION_FAILED}),
     *         AI 응답이 올바르지 않음({@link com.wallo.common.exception.ErrorCode#AI_REPORT_INVALID_RESPONSE}),
     *         저장 실패({@link com.wallo.common.exception.ErrorCode#REPORT_SAVE_FAILED})
     */
    NewsReport generateIfAbsent(Long newsId);
}
