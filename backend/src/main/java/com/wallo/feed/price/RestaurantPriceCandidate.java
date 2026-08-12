package com.wallo.feed.price;

/** 음식점 메뉴 검색 결과에서 추출한 판매가 후보. */
public record RestaurantPriceCandidate(
        String title,
        int price,
        String source,
        String sourceUrl) {
}
