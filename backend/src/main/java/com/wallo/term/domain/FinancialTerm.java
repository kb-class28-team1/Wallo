package com.wallo.term.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** financial_term 테이블 1행. 실제 컬럼은 term_id/term_name/description/short_definition/source/created_at뿐이며
 *  normalized_term 컬럼은 존재하지 않는다(공백/대소문자 정규화는 매칭 시 Service에서 계산한다). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FinancialTerm {

    private Long termId;
    private String termName;
    private String description;
    /** 금융 초보자용으로 가공한 2문장 짧은 설명. 아직 생성되지 않은 용어는 null이다. */
    private String shortDefinition;
    private String source;
    private LocalDateTime createdAt;
}
