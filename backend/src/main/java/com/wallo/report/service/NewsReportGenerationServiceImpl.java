package com.wallo.report.service;

import com.wallo.report.client.NewsReportAiClient;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.report.domain.News;
import com.wallo.report.domain.NewsReport;
import com.wallo.report.domain.NewsReportPersonalization;
import com.wallo.report.domain.ReportUserProfile;
import com.wallo.report.dto.ai.NewsReportAiRequest;
import com.wallo.report.dto.ai.NewsReportAiResponse;
import com.wallo.report.mapper.NewsReportMapper;
import com.wallo.report.mapper.NewsReportPersonalizationMapper;
import com.wallo.report.term.service.FinancialTermMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service
public class NewsReportGenerationServiceImpl implements NewsReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(NewsReportGenerationServiceImpl.class);

    private final NewsService newsService;
    private final NewsReportMapper newsReportMapper;
    private final NewsReportAiClient newsReportAiClient;
    private final FinancialTermMatchingService financialTermMatchingService;
    private final NewsReportPersonalizationMapper personalizationMapper;

    @Autowired
    public NewsReportGenerationServiceImpl(
            NewsService newsService,
            NewsReportMapper newsReportMapper,
            NewsReportAiClient newsReportAiClient,
            FinancialTermMatchingService financialTermMatchingService,
            NewsReportPersonalizationMapper personalizationMapper
    ) {
        this.newsService = newsService;
        this.newsReportMapper = newsReportMapper;
        this.newsReportAiClient = newsReportAiClient;
        this.financialTermMatchingService = financialTermMatchingService;
        this.personalizationMapper = personalizationMapper;
    }

    public NewsReportGenerationServiceImpl(NewsService newsService, NewsReportMapper newsReportMapper,
            NewsReportAiClient newsReportAiClient, FinancialTermMatchingService financialTermMatchingService) {
        this(newsService, newsReportMapper, newsReportAiClient, financialTermMatchingService, null);
    }

    /**
     * AI 호출은 초 단위로 오래 걸릴 수 있어(최대 90초) 트랜잭션으로 감싸지 않는다.
     * 실제 DB 쓰기는 {@link #insertOrReuseExisting}의 단일 INSERT 문 하나뿐이라
     * MyBatis/JDBC 수준에서 이미 원자적이라 별도 트랜잭션 경계가 없어도 정합성에 문제가 없다.
     */
    @Override
    public NewsReport generateIfAbsent(Long newsId) {
        News news = newsService.getNewsByIdOrThrow(newsId);

        NewsReport existing = newsReportMapper.findByNewsId(newsId);
        if (existing != null) {
            matchFinancialTerms(news);
            return existing;
        }

        if (news.getContent() == null || news.getContent().isBlank()) {
            log.warn("기사 본문이 비어 있어 리포트를 생성할 수 없습니다 - newsId: {}", newsId);
            throw new CustomException(ErrorCode.REPORT_CONTENT_EMPTY);
        }

        NewsReportAiResponse aiResponse = newsReportAiClient.generateReport(toAiRequest(news));
        validateComplete(newsId, aiResponse);

        NewsReport newsReport = NewsReport.builder()
                .newsId(newsId)
                // news_report.summary는 TEXT 컬럼 하나라, 화면 상단 bullet 목록(2~3개의 짧은 문장)을
                // 줄바꿈으로 이어붙여 저장한다. 읽을 때(ReportDetailResponse)는 다시 줄바꿈 기준으로 분리한다.
                .summary(String.join("\n", aiResponse.summary()))
                .eventDescription(aiResponse.eventDescription())
                .cause(aiResponse.cause())
                .socialImpact(aiResponse.socialImpact())
                .build();

        NewsReport saved = insertOrReuseExisting(newsId, newsReport);
        matchFinancialTerms(news);
        return saved;
    }

    @Override
    public NewsReportPersonalization generatePersonalizationIfAbsent(Long newsId, Long userId) {
        News news = newsService.getNewsByIdOrThrow(newsId);
        NewsReportPersonalization existing = personalizationMapper.findByNewsIdAndUserId(newsId, userId);
        if (existing != null) {
            return existing;
        }
        ReportUserProfile profile = personalizationMapper.findUserProfile(userId);
        if (profile == null || profile.getNickname() == null || profile.getNickname().isBlank()) {
            throw new CustomException(ErrorCode.REPORT_NOT_FOUND);
        }
        NewsReportAiResponse response = newsReportAiClient.generateReport(new NewsReportAiRequest(
                news.getNewsId(), news.getTitle(), news.getContent(), news.getCategory(), news.getSource(),
                news.getPublishedAt(), "PERSONALIZED", profile.toPromptMap()));
        if (isBlank(response.userImpact()) || isBlank(response.responseStrategy())) {
            throw new CustomException(ErrorCode.AI_REPORT_INVALID_RESPONSE);
        }
        NewsReportPersonalization personalization = NewsReportPersonalization.builder()
                .newsId(newsId).userId(userId).userImpact(response.userImpact())
                .responseStrategy(response.responseStrategy()).build();
        try {
            if (personalizationMapper.insert(personalization) != 1) {
                throw new CustomException(ErrorCode.REPORT_SAVE_FAILED);
            }
            return personalization;
        } catch (DuplicateKeyException exception) {
            NewsReportPersonalization winner = personalizationMapper.findByNewsIdAndUserId(newsId, userId);
            if (winner != null) return winner;
            throw new CustomException(ErrorCode.REPORT_SAVE_FAILED, exception);
        }
    }

    /**
     * news_report 저장이 끝난 뒤 뉴스 제목/본문에서 금융용어를 매칭해 news_term에 저장한다.
     * news_term은 리포트의 부가 정보이므로, 매칭 과정에서 예외가 나더라도 리포트 조회/생성 자체는
     * 실패시키지 않고 로그만 남긴다.
     */
    private void matchFinancialTerms(News news) {
        try {
            financialTermMatchingService.matchAndSaveTerms(news.getNewsId(), news.getTitle(), news.getContent());
        } catch (RuntimeException exception) {
            log.error("금융용어 매칭에 실패했습니다 - newsId: {}", news.getNewsId(), exception);
        }
    }

    /**
     * AI 응답 6개 핵심 필드가 모두 채워졌는지 확인한다. 하나라도 비어 있으면 저장하지 않고
     * AI_REPORT_INVALID_RESPONSE로 실패시켜 다음 스케줄에서 재시도되게 한다 — 일부만 채워진
     * "반쪽짜리" 리포트가 news_report에 저장되는 것을 막기 위함이다.
     */
    private void validateComplete(Long newsId, NewsReportAiResponse aiResponse) {
        if (isBlankSummary(aiResponse.summary())
                || isBlank(aiResponse.eventDescription())
                || isBlank(aiResponse.cause())
                || isBlank(aiResponse.socialImpact())) {
            log.warn("AI 응답에 빈 필드가 있어 저장하지 않습니다 - newsId: {}", newsId);
            throw new CustomException(ErrorCode.AI_REPORT_INVALID_RESPONSE);
        }
    }

    /** summary는 bullet 목록이라 목록 자체가 비었거나, 안의 항목 중 하나라도 빈 문자열이면 무효다. */
    private boolean isBlankSummary(List<String> summary) {
        return summary == null || summary.isEmpty() || summary.stream().anyMatch(this::isBlank);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private NewsReportAiRequest toAiRequest(News news) {
        return new NewsReportAiRequest(
                news.getNewsId(),
                news.getTitle(),
                news.getContent(),
                news.getCategory(),
                news.getSource(),
                news.getPublishedAt());
    }

    /**
     * 존재 확인과 INSERT 사이에 동시 요청이 끼어들 수 있어, news_report.news_id의 UNIQUE 제약조건
     * 위반(DuplicateKeyException)을 최종 방어선으로 삼는다. 위반이 나면 방금 만든 값은 버리고
     * 먼저 저장된 기존 값을 재조회해 반환한다. 광범위한 synchronized 없이 DB 제약조건만으로 방어한다.
     */
    private NewsReport insertOrReuseExisting(Long newsId, NewsReport newsReport) {
        try {
            int insertedRows = newsReportMapper.insert(newsReport);
            if (insertedRows != 1) {
                log.error("금융 리포트 저장에 실패했습니다 - newsId: {}", newsId);
                throw new CustomException(ErrorCode.REPORT_SAVE_FAILED);
            }
            return newsReport;
        } catch (DuplicateKeyException exception) {
            log.warn("동시 요청으로 이미 리포트가 저장되어 기존 값을 재조회합니다 - newsId: {}", newsId);
            NewsReport winner = newsReportMapper.findByNewsId(newsId);
            if (winner != null) {
                return winner;
            }
            throw new CustomException(ErrorCode.REPORT_SAVE_FAILED, exception);
        }
    }
}
