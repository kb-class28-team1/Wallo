package com.wallo.mapper;

import com.wallo.domain.NewsReport;
import org.apache.ibatis.annotations.Param;

/**
 * news_report 테이블(AI가 생성한 금융 리포트 가공 결과)에 접근하는 MyBatis Mapper.
 * news와 news_id 기준 1:1 관계이며, 아직 생성되지 않은 뉴스도 있을 수 있다.
 */
public interface NewsReportMapper {

    /** news_id로 AI 가공 결과 1건을 조회한다. 아직 생성되지 않았으면 null을 반환한다. */
    NewsReport findByNewsId(@Param("newsId") Long newsId);

    /** AI 가공 결과 1건을 저장하고, DB가 생성한 news_report.report_id를 NewsReport 객체에 채운다. */
    int insert(NewsReport newsReport);
}
