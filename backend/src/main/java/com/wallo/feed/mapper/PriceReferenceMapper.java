package com.wallo.feed.mapper;

import com.wallo.feed.domain.PriceReferenceRow;
import org.apache.ibatis.annotations.Param;

public interface PriceReferenceMapper {
    PriceReferenceRow findByKey(@Param("normalizedItemName") String normalizedItemName,
                                @Param("brand") String brand,
                                @Param("unit") String unit);

    int upsert(PriceReferenceRow row);
}
