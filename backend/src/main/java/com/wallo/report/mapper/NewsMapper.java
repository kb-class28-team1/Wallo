package com.wallo.report.mapper;

import com.wallo.report.domain.News;
import com.wallo.report.domain.NewsReportListItem;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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
     * news와 news_report를 INNER JOIN해, news_report가 이미 생성된 뉴스만 게시일시 최신순으로
     * 전체 조회한다(개수 제한 없음, 목록 조회 API 전용). 목록에 나온 기사는 클릭 즉시 AI 리포트가
     * 보여야 하므로 리포트가 없는 뉴스는 여기서부터 제외되고, summary는 항상 채워지며 analyzed는
     * 항상 true다. N+1 없이 단일 쿼리로 처리한다.
     */
    List<NewsReportListItem> findAllWithReportSummary();

    /**
     * news_report가 아직 없는 뉴스의 news_id를 게시일시 최신순으로 최대 limit개 조회한다.
     * 본문이 NULL이거나 공백뿐인 뉴스는 애초에 대상에서 제외한다(항상 REPORT_CONTENT_EMPTY로
     * 스킵될 뿐이라 매 스케줄마다 반복 선택되지 않게 하기 위함).
     * 금융 리포트 자동 생성 스케줄러(FinancialReportGenerationScheduler)가 처리 대상을 고를 때 사용한다.
     */
    List<Long> findNewsIdsWithoutReport(@Param("limit") int limit);

    int deleteNewsTermsBeforePublishedAt(@Param("cutoff") LocalDateTime cutoff);

    int deleteNewsReportsBeforePublishedAt(@Param("cutoff") LocalDateTime cutoff);

    int deleteNewsBeforePublishedAt(@Param("cutoff") LocalDateTime cutoff);
}
