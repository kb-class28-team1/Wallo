package com.wallo.service;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.domain.News;
import com.wallo.domain.NewsReport;
import com.wallo.dto.response.MatchedTermResponse;
import com.wallo.dto.response.ReportDetailResponse;
import com.wallo.mapper.NewsMapper;
import com.wallo.mapper.NewsReportMapper;
import com.wallo.term.mapper.NewsTermMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsServiceImpl implements NewsService {

    private static final int MAX_LATEST_NEWS_LIMIT = 100;

    private final NewsMapper newsMapper;
    private final NewsReportMapper newsReportMapper;
    private final NewsTermMapper newsTermMapper;

    public NewsServiceImpl(NewsMapper newsMapper, NewsReportMapper newsReportMapper, NewsTermMapper newsTermMapper) {
        this.newsMapper = newsMapper;
        this.newsReportMapper = newsReportMapper;
        this.newsTermMapper = newsTermMapper;
    }

    /**
     * URL 중복 확인과 저장을 하나의 트랜잭션으로 묶는다.
     * 중복 확인과 INSERT 사이의 동시성 문제는 news.url의 UNIQUE 제약조건이 최종 방어선이다.
     */
    @Override
    @Transactional
    public boolean saveNews(News news) {
        if (news == null) {
            return false;
        }

        String url = news.getUrl();
        if (url == null || url.isBlank()) {
            return false;
        }

        String trimmedUrl = url.trim();
        news.setUrl(trimmedUrl);

        if (existsByUrl(trimmedUrl)) {
            return false;
        }

        int insertedRows = newsMapper.insertNews(news);
        if (insertedRows != 1) {
            throw new IllegalStateException("뉴스 저장에 실패했습니다.");
        }

        return true;
    }

    @Override
    public boolean existsByUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        return newsMapper.existsByUrl(url.trim()) > 0;
    }

    @Override
    public News getNewsById(Long newsId) {
        if (newsId == null) {
            return null;
        }

        return newsMapper.findById(newsId);
    }

    @Override
    public News getNewsByIdOrThrow(Long newsId) {
        News news = newsMapper.findById(newsId);
        if (news == null) {
            throw new CustomException(ErrorCode.REPORT_NOT_FOUND);
        }

        return news;
    }

    /**
     * news를 먼저 조회(없으면 예외)한 뒤, news_report를 news_id로 조회해 있으면 매핑하고
     * 없으면 AI 관련 필드를 null로 둔 채 응답을 구성한다. news_report 부재는 오류로 취급하지 않는다.
     * 매칭된 금융용어(news_term + financial_term)도 함께 조회해 terms에 채운다. 매칭 결과가
     * 없으면 빈 리스트가 된다(ReportDetailResponse 생성자에서도 null-safe하게 한 번 더 보장한다).
     */
    @Override
    public ReportDetailResponse getReportDetail(Long newsId) {
        News news = getNewsByIdOrThrow(newsId);
        NewsReport newsReport = newsReportMapper.findByNewsId(newsId);
        List<MatchedTermResponse> terms = newsTermMapper.findTermsByNewsId(newsId).stream()
                .map(MatchedTermResponse::from)
                .collect(Collectors.toList());
        return ReportDetailResponse.from(news, newsReport, terms);
    }

    @Override
    public List<News> getLatestNews(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }

        int boundedLimit = Math.min(limit, MAX_LATEST_NEWS_LIMIT);
        return newsMapper.findLatest(boundedLimit);
    }

    @Override
    public List<News> getAllNews() {
        return newsMapper.findAll();
    }
}
