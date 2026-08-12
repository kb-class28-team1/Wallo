package com.wallo.feed.mapper;

import com.wallo.feed.domain.PriceReferenceRow;
import org.apache.ibatis.annotations.Param;

public interface PriceReferenceMapper {
    PriceReferenceRow findBestMatch(
            @Param("normalizedItemName") String normalizedItemName,
            @Param("normalizedBrand") String normalizedBrand,
            @Param("normalizedUnit") String normalizedUnit,
            @Param("category") String category);

    int upsert(PriceReferenceRow row);
}
