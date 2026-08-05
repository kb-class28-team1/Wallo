package com.wallo.asset.classification;

import com.wallo.chat.client.AiServerException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
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
    public List<Optional<ExpenseCategoryClassifier.Result>> classifyBatch(
            List<ExpenseCategoryClassifier.Context> contexts
    ) {
        List<Optional<ExpenseCategoryClassifier.Result>> results = new ArrayList<>(
                Collections.nCopies(contexts.size(), Optional.empty())
        );
        List<CategoryClassificationDto.Request> requests = new ArrayList<>();
        List<Integer> requestIndexes = new ArrayList<>();
        for (int index = 0; index < contexts.size(); index++) {
            ExpenseCategoryClassifier.Context context = contexts.get(index);
            if (canRequestClassification(context)) {
                requests.add(new CategoryClassificationDto.Request(
                        context.merchantName(),
                        context.merchantSector(),
                        context.amount()
                ));
                requestIndexes.add(index);
            }
        }
        if (requests.isEmpty()) {
            return results;
        }

        try {
            List<CategoryClassificationDto.Response> responses =
                    categoryClassificationClient.classifyBatch(requests);
            if (responses == null || responses.size() != requests.size()) {
                return results;
            }
            for (int index = 0; index < responses.size(); index++) {
                results.set(requestIndexes.get(index), toResult(responses.get(index)));
            }
        } catch (AiServerException exception) {
            return results;
        }
        return results;
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
