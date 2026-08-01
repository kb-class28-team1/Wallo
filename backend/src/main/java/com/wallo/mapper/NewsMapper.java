package com.wallo.mapper;

import com.wallo.domain.News;
import com.wallo.domain.NewsReportListItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * news 테이블에 접근하는 MyBatis Mapper.
 * 크롤링한 뉴스 원본 데이터의 저장과 조회를 제공한다.
 */
public interface NewsMapper {

    /** 뉴스 1건을 저장하고, DB가 생성한 news.news_id를 News 객체에 채운다. */
    int insertNews(News news);

    /** URL 중복 여부를 확인한다. 일치하는 행 수를 반환하며, 0이면 미존재, 1 이상이면 존재로 판단한다. */
    int existsByUrl(@Param("url") String url);

    /** news_id로 뉴스 1건을 조회한다. 없으면 null을 반환한다. */
    News findById(@Param("newsId") Long newsId);

    /** 게시일시 기준 최신 뉴스 목록을 limit개 조회한다. */
    List<News> findLatest(@Param("limit") int limit);

    /**
     * news와 news_report를 LEFT JOIN해 게시일시 최신순으로 전체 목록을 조회한다(개수 제한 없음,
     * 목록 조회 API 전용). N+1 없이 단일 쿼리로 처리하며, 리포트가 없는 뉴스는 summary가 null이다.
     */
    List<NewsReportListItem> findAllWithReportSummary();
}
