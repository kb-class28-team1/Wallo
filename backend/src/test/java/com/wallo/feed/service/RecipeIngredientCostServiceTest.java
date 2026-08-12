package com.wallo.feed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.wallo.feed.domain.DishRecipeIngredientRow;
import com.wallo.feed.mapper.DishRecipeIngredientMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipeIngredientCostServiceTest {
    private final DishRecipeIngredientMapper mapper =
            org.mockito.Mockito.mock(DishRecipeIngredientMapper.class);
    private RecipeIngredientCostService service;

    @BeforeEach
    void setUp() {
        service = new RecipeIngredientCostService(mapper);
    }

    @Test
    void calculatesPastaCostFromIndividualIngredientPrices() {
        when(mapper.findPricedIngredients("파스타", "1인분", "FOOD"))
                .thenReturn(List.of(
                        ingredient("파스타면", "100", "g", "100g", 700),
                        ingredient("토마토소스", "150", "g", "100g", 1_000),
                        ingredient("버섯", "50", "g", "100g", 1_400),
                        ingredient("마늘", "10", "g", "100g", 1_500),
                        ingredient("치즈", "20", "g", "100g", 2_500),
                        ingredient("기본양념", "1", "회", "1회", 200)));

        RecipeIngredientCostService.RecipeCost result =
                service.calculate("파스타", "1인분", "FOOD").orElseThrow();

        assertEquals(3_750, result.ingredientCost());
        assertTrue(result.ingredientBasis().contains("토마토소스 150g 1,500원"));
        assertTrue(result.ingredientBasis().contains("버섯 50g 700원"));
    }

    @Test
    void convertsKilogramsToGramReferenceUnit() {
        when(mapper.findPricedIngredients("파스타", "1인분", "FOOD"))
                .thenReturn(List.of(ingredient("파스타면", "0.1", "kg", "100g", 700)));

        RecipeIngredientCostService.RecipeCost result =
                service.calculate("파스타", "1인분", "FOOD").orElseThrow();

        assertEquals(700, result.ingredientCost());
    }

    @Test
    void rejectsIncompleteRecipeInsteadOfUnderestimatingCost() {
        DishRecipeIngredientRow missingPrice =
                ingredient("버섯", "50", "g", "100g", null);
        when(mapper.findPricedIngredients("파스타", "1인분", "FOOD"))
                .thenReturn(List.of(
                        ingredient("파스타면", "100", "g", "100g", 700),
                        missingPrice));

        Optional<RecipeIngredientCostService.RecipeCost> result =
                service.calculate("파스타", "1인분", "FOOD");

        assertTrue(result.isEmpty());
    }

    private DishRecipeIngredientRow ingredient(
            String name, String quantity, String unit, String priceUnit, Integer price) {
        DishRecipeIngredientRow row = new DishRecipeIngredientRow();
        row.setDisplayIngredientName(name);
        row.setIngredientQuantity(new BigDecimal(quantity));
        row.setIngredientUnit(unit);
        row.setPriceReferenceUnit(priceUnit);
        row.setReferencePrice(price);
        return row;
    }
}
