package com.wallo.asset.classification;

import com.wallo.chat.client.AiServerException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiCategoryRule implements ExpenseCategoryRule {

    private static final String CLASSIFIER_VERSION = "ai-v1";
    private static final String CATEGORY_SOURCE = "AI";
    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.7000");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "FOOD",
            "CAFE",
            "TRANSPORT",
            "SHOPPING",
            "DELIVERY",
            "HOUSING",
            "LIVING",
            "CULTURE",
            "HEALTH",
            "EDUCATION",
            "ETC"
    );

    private final CategoryClassificationClient categoryClassificationClient;

    public AiCategoryRule(CategoryClassificationClient categoryClassificationClient) {
        this.categoryClassificationClient = categoryClassificationClient;
    }

    @Override
    public Optional<ExpenseCategoryClassifier.Result> classify(
            ExpenseCategoryClassifier.Context context
    ) {
        if (!canRequestClassification(context)) {
            return Optional.empty();
        }

        try {
            CategoryClassificationDto.Response response = categoryClassificationClient.classify(
                    new CategoryClassificationDto.Request(
                            context.merchantName(),
                            context.merchantSector(),
                            context.amount()
                    )
            );
            return toResult(response);
        } catch (AiServerException exception) {
            return Optional.empty();
        }
    }

    @Override
    public int getOrder() {
        return 300;
    }

    private Optional<ExpenseCategoryClassifier.Result> toResult(
            CategoryClassificationDto.Response response
    ) {
        if (response == null || response.category() == null || response.confidence() == null) {
            return Optional.empty();
        }

        String category = response.category().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CATEGORIES.contains(category)
                || response.confidence().compareTo(MIN_CONFIDENCE) < 0) {
            return Optional.empty();
        }

        return Optional.of(new ExpenseCategoryClassifier.Result(
                category,
                CATEGORY_SOURCE,
                response.confidence(),
                CLASSIFIER_VERSION
        ));
    }

    private boolean canRequestClassification(ExpenseCategoryClassifier.Context context) {
        return context != null
                && context.amount() > 0
                && context.merchantName() != null
                && !context.merchantName().isBlank();
    }
}
