package com.wallo.service;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.domain.News;
import com.wallo.mapper.NewsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NewsServiceImpl implements NewsService {

    private static final int MAX_LATEST_NEWS_LIMIT = 100;

    private final NewsMapper newsMapper;

    public NewsServiceImpl(NewsMapper newsMapper) {
        this.newsMapper = newsMapper;
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
