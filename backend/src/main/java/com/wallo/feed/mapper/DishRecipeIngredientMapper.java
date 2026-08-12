package com.wallo.feed.mapper;

import com.wallo.feed.domain.DishRecipeIngredientRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DishRecipeIngredientMapper {
    List<DishRecipeIngredientRow> findPricedIngredients(
            @Param("normalizedDishName") String normalizedDishName,
            @Param("normalizedDishUnit") String normalizedDishUnit,
            @Param("category") String category);
}
