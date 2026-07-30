package com.wallo.service;

import com.wallo.domain.News;

import java.util.List;

/** 뉴스 원본 데이터의 저장과 조회에 대한 비즈니스 규칙을 처리한다. */
public interface NewsService {

    /**
     * 뉴스를 저장한다. URL이 이미 존재하면 저장하지 않는다.
     *
     * @return 신규 저장되었으면 true, 이미 존재해 저장하지 않았으면 false
     */
    boolean saveNews(News news);

    /** URL로 뉴스 존재 여부를 확인한다. */
    boolean existsByUrl(String url);

    /** news_id로 뉴스 1건을 조회한다. 없으면 null을 반환한다. */
    News getNewsById(Long newsId);

    /** 게시일시 기준 최신 뉴스 목록을 limit개 조회한다. */
    List<News> getLatestNews(int limit);

    /** 게시일시 최신순으로 전체 뉴스 목록을 조회한다. (개수 제한 없음, 목록 조회 API용) */
    List<News> getAllNews();
}
