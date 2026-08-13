package com.wallo.feed.service;

import com.wallo.feed.domain.DishRecipeIngredientRow;
import com.wallo.feed.mapper.DishRecipeIngredientMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 표준 단위 재료 시세와 레시피 사용량을 합산해 음식 1단위 재료비를 계산한다. */
@Service
public class RecipeIngredientCostService {
    private static final int MAX_INGREDIENT_COST = 10_000_000;
    private static final Pattern REFERENCE_UNIT = Pattern.compile(
            "(?i)^(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|l|개|장|회)$");

    private final DishRecipeIngredientMapper recipeIngredientMapper;

    public RecipeIngredientCostService(DishRecipeIngredientMapper recipeIngredientMapper) {
        this.recipeIngredientMapper = recipeIngredientMapper;
    }

    public Optional<RecipeCost> calculate(
            String normalizedDishName, String normalizedDishUnit, String category) {
        List<DishRecipeIngredientRow> ingredients = recipeIngredientMapper.findPricedIngredients(
                normalizedDishName, normalizedDishUnit, category);
        if (ingredients == null || ingredients.isEmpty()) {
            return Optional.empty();
        }

        int totalCost = 0;
        List<String> basisParts = new ArrayList<>();
        for (DishRecipeIngredientRow ingredient : ingredients) {
            Optional<Integer> cost = calculateIngredientCost(ingredient);
            if (cost.isEmpty()) {
                // 일부 재료만 합산하면 원가를 과소평가하므로 전체 레시피 계산을 사용하지 않는다.
                return Optional.empty();
            }
            totalCost = Math.min(MAX_INGREDIENT_COST, totalCost + cost.get());
            basisParts.add(String.format(Locale.KOREA, "%s %s%s %,d원",
                    ingredient.getDisplayIngredientName(),
                    displayQuantity(ingredient.getIngredientQuantity()),
                    ingredient.getIngredientUnit(), cost.get()));
        }
        if (totalCost <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RecipeCost(totalCost, String.join(", ", basisParts)));
    }

    private Optional<Integer> calculateIngredientCost(DishRecipeIngredientRow ingredient) {
        if (ingredient == null || ingredient.getIngredientQuantity() == null
                || ingredient.getIngredientQuantity().signum() <= 0
                || ingredient.getReferencePrice() == null
                || ingredient.getReferencePrice() <= 0
                || ingredient.getIngredientUnit() == null
                || ingredient.getPriceReferenceUnit() == null) {
            return Optional.empty();
        }
        Matcher matcher = REFERENCE_UNIT.matcher(
                ingredient.getPriceReferenceUnit().trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String ingredientUnit = ingredient.getIngredientUnit().trim().toLowerCase(Locale.ROOT);
        String referenceUnit = matcher.group(2).toLowerCase(Locale.ROOT);
        if (!dimension(ingredientUnit).equals(dimension(referenceUnit))) {
            return Optional.empty();
        }

        BigDecimal requiredBaseQuantity = toBaseQuantity(
                ingredient.getIngredientQuantity(), ingredientUnit);
        BigDecimal referenceBaseQuantity = toBaseQuantity(
                new BigDecimal(matcher.group(1)), referenceUnit);
        if (requiredBaseQuantity == null || referenceBaseQuantity == null
                || referenceBaseQuantity.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal cost = BigDecimal.valueOf(ingredient.getReferencePrice())
                .multiply(requiredBaseQuantity)
                .divide(referenceBaseQuantity, 0, RoundingMode.CEILING);
        return Optional.of(Math.min(MAX_INGREDIENT_COST, cost.intValue()));
    }

    private BigDecimal toBaseQuantity(BigDecimal quantity, String unit) {
        return switch (unit) {
            case "kg", "l" -> quantity.multiply(BigDecimal.valueOf(1_000));
            case "g", "ml", "개", "장", "회" -> quantity;
            default -> null;
        };
    }

    private String dimension(String unit) {
        return switch (unit) {
            case "kg", "g" -> "MASS";
            case "l", "ml" -> "VOLUME";
            default -> unit;
        };
    }

    private String displayQuantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    public record RecipeCost(int ingredientCost, String ingredientBasis) {}
}
