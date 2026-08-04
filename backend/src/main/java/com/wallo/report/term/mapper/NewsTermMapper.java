package com.wallo.report.term.mapper;

import com.wallo.report.term.domain.FinancialTerm;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * news_term(뉴스-금융용어 다대다 연결) 테이블에 접근하는 MyBatis Mapper.
 * news_term은 (news_id, term_id) 복합 PK라 동일 조합은 DB 레벨에서도 중복 저장될 수 없다.
 */
public interface NewsTermMapper {

    /** 특정 뉴스에 연결된 기존 매칭 결과를 모두 삭제한다. 재매칭 시 먼저 호출해 최신 상태로 되돌린다. */
    int deleteByNewsId(@Param("newsId") Long newsId);

    /** 매칭된 term_id 목록을 한 번의 batch INSERT로 저장한다. termIds가 비어 있으면 호출하지 않는다. */
    int batchInsert(@Param("newsId") Long newsId, @Param("termIds") List<Long> termIds);

    /**
     * 특정 뉴스에 매칭된 금융용어를 news_term과 financial_term을 조인해 조회한다.
     * 매칭된 용어가 없으면 빈 리스트를 반환한다(null 반환 없음).
     */
    List<FinancialTerm> findTermsByNewsId(@Param("newsId") Long newsId);
}
