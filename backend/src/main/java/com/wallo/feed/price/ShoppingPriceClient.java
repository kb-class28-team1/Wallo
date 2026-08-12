package com.wallo.feed.price;

import java.util.List;

/** 외부 쇼핑 검색 제공자를 교체할 수 있도록 분리한 가격 검색 인터페이스. */
public interface ShoppingPriceClient {
    List<ShoppingPriceCandidate> search(String itemName, String brand, String unit);
}
