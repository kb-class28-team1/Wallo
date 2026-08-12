package com.wallo.feed.price;

/** 쇼핑 검색 API에서 받은 가격 후보. 금액 계산 전 서버 검증을 거친다. */
public record ShoppingPriceCandidate(
        String title,
        int price,
        String source,
        String sourceUrl,
        String delivery) {
}
