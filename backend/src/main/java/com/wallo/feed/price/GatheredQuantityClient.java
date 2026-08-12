package com.wallo.feed.price;

import java.util.OptionalInt;

/** 채집물의 무게·포장 단위에 포함된 평균 개체 수를 외부 검색으로 확인한다. */
public interface GatheredQuantityClient {
    OptionalInt findAveragePackageQuantity(String itemName, String packageUnit);
}
