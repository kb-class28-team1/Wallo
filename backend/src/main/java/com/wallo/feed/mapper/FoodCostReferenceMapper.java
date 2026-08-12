package com.wallo.feed.mapper;

import com.wallo.feed.domain.FoodCostReferenceRow;
import org.apache.ibatis.annotations.Param;

public interface FoodCostReferenceMapper {
    FoodCostReferenceRow findBestMatch(
            @Param("normalizedDishName") String normalizedDishName,
            @Param("normalizedUnit") String normalizedUnit,
            @Param("category") String category);

    int upsert(FoodCostReferenceRow row);
}
