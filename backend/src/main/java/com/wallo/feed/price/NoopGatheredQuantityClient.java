package com.wallo.feed.price;

import java.util.OptionalInt;

/** 외부 검색을 사용할 수 없을 때 채집물 평균 개수 추정을 하지 않는다. */
public class NoopGatheredQuantityClient implements GatheredQuantityClient {
    @Override
    public OptionalInt findAveragePackageQuantity(String itemName, String packageUnit) {
        return OptionalInt.empty();
    }
}
