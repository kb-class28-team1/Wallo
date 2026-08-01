package com.wallo.term.mapper;

import com.wallo.term.domain.FinancialTerm;

import java.util.List;

/**
 * financial_term 테이블에 접근하는 MyBatis Mapper.
 * financial_term에는 normalized_term 컬럼이 없다(term_id, term_name, description, source, created_at만 존재).
 * 공백/대소문자 정규화 및 매칭용 정규식 생성은 전부 Service 계층(FinancialTermMatchingServiceImpl)에서 수행한다.
 */
public interface FinancialTermMapper {

    /** 뉴스-용어 매칭에 사용할 금융용어 전체를 조회한다. */
    List<FinancialTerm> findAll();
}
