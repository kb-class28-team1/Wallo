package com.wallo.service;

import com.wallo.client.NewsReportAiClient;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.domain.News;
import com.wallo.domain.NewsReport;
import com.wallo.dto.ai.NewsReportAiRequest;
import com.wallo.dto.ai.NewsReportAiResponse;
import com.wallo.mapper.NewsReportMapper;
import com.wallo.term.service.FinancialTermMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class NewsReportGenerationServiceImpl implements NewsReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(NewsReportGenerationServiceImpl.class);

    private final NewsService newsService;
    private final NewsReportMapper newsReportMapper;
    private final NewsReportAiClient newsReportAiClient;
    private final FinancialTermMatchingService financialTermMatchingService;

    public NewsReportGenerationServiceImpl(
            NewsService newsService,
            NewsReportMapper newsReportMapper,
            NewsReportAiClient newsReportAiClient,
            FinancialTermMatchingService financialTermMatchingService
    ) {
        this.newsService = newsService;
        this.newsReportMapper = newsReportMapper;
        this.newsReportAiClient = newsReportAiClient;
        this.financialTermMatchingService = financialTermMatchingService;
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

        if (aiResponse.summary() == null || aiResponse.summary().isBlank()) {
            log.warn("AI 응답의 summary가 비어 있어 저장하지 않습니다 - newsId: {}", newsId);
            throw new CustomException(ErrorCode.AI_REPORT_INVALID_RESPONSE);
        }

        NewsReport newsReport = NewsReport.builder()
                .newsId(newsId)
                .summary(aiResponse.summary())
                .cause(aiResponse.cause())
                .socialImpact(aiResponse.socialImpact())
                .userImpact(aiResponse.userImpact())
                .responseStrategy(aiResponse.responseStrategy())
                .build();

        NewsReport saved = insertOrReuseExisting(newsId, newsReport);
        matchFinancialTerms(news);
        return saved;
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
