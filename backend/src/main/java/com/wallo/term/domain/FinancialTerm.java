package com.wallo.term.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** financial_term 테이블 1행. 실제 컬럼은 term_id/term_name/description/source/created_at뿐이며
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
    private String source;
    private LocalDateTime createdAt;
}
