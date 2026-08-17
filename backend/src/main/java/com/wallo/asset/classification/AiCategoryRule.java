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
    private final AiCategoryBatchExecutor batchExecutor;

    public AiCategoryRule(CategoryClassificationClient categoryClassificationClient) {
        this(categoryClassificationClient, new AiCategoryBatchExecutor());
    }

    AiCategoryRule(
            CategoryClassificationClient categoryClassificationClient,
            AiCategoryBatchExecutor batchExecutor
    ) {
        this.categoryClassificationClient = categoryClassificationClient;
        this.batchExecutor = batchExecutor;
    }

    @Override
    public Optional<ExpenseCategoryClassifier.Result> classify(
            ExpenseCategoryClassifier.Context context
    ) {
        if (!canRequestClassification(context)) {
            return fallback(CategoryFailureReason.MISSING_INPUT);
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
            return fallback(toFailureReason(exception));
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
            } else {
                results.set(index, fallback(CategoryFailureReason.MISSING_INPUT));
            }
        }
        if (requests.isEmpty()) {
            return results;
        }

        List<AiCategoryBatchExecutor.BatchResult<List<CategoryClassificationDto.Response>>> batches =
                batchExecutor.execute(requests, categoryClassificationClient::classifyBatch);
        int requestOffset = 0;
        for (AiCategoryBatchExecutor.BatchResult<List<CategoryClassificationDto.Response>> batch : batches) {
            int batchSize = Math.min(
                    AiCategoryBatchExecutor.MAX_BATCH_SIZE,
                    requests.size() - requestOffset
            );
            if (!batch.succeeded()) {
                CategoryFailureReason reason = toFailureReason(batch.failure());
                setFallbacks(results, requestIndexes, requestOffset, batchSize, reason);
                requestOffset += batchSize;
                continue;
            }

            List<CategoryClassificationDto.Response> responses = batch.value();
            if (responses == null || responses.size() != batchSize) {
                setFallbacks(
                        results,
                        requestIndexes,
                        requestOffset,
                        batchSize,
                        CategoryFailureReason.AI_INVALID_RESPONSE
                );
                requestOffset += batchSize;
                continue;
            }
            for (int index = 0; index < responses.size(); index++) {
                results.set(requestIndexes.get(requestOffset + index), toResult(responses.get(index)));
            }
            requestOffset += batchSize;
        }
        if (requestOffset < requests.size()) {
            setFallbacks(
                    results,
                    requestIndexes,
                    requestOffset,
                    requests.size() - requestOffset,
                    CategoryFailureReason.BATCH_PARTIAL_FAILURE
            );
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
            return fallback(CategoryFailureReason.AI_INVALID_RESPONSE);
        }

        String category = response.category().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CATEGORIES.contains(category)) {
            return fallback(CategoryFailureReason.AI_INVALID_RESPONSE);
        }
        if (response.confidence().compareTo(MIN_CONFIDENCE) < 0) {
            return fallback(CategoryFailureReason.LOW_CONFIDENCE);
        }

        return Optional.of(new ExpenseCategoryClassifier.Result(
                category,
                CATEGORY_SOURCE,
                response.confidence(),
                CLASSIFIER_VERSION,
                null
        ));
    }

    private Optional<ExpenseCategoryClassifier.Result> fallback(CategoryFailureReason reason) {
        return Optional.of(new ExpenseCategoryClassifier.Result(
                "ETC",
                "FALLBACK",
                BigDecimal.ZERO,
                "fallback-v1",
                reason
        ));
    }

    private void setFallbacks(
            List<Optional<ExpenseCategoryClassifier.Result>> results,
            List<Integer> requestIndexes,
            int offset,
            int count,
            CategoryFailureReason reason
    ) {
        for (int index = 0; index < count; index++) {
            results.set(requestIndexes.get(offset + index), fallback(reason));
        }
    }

    private CategoryFailureReason toFailureReason(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AiServerException exception) {
                return switch (exception.getFailureReason()) {
                    case AI_NOT_CONFIGURED -> CategoryFailureReason.AI_NOT_CONFIGURED;
                    case AI_TIMEOUT -> CategoryFailureReason.AI_TIMEOUT;
                    case AI_INVALID_REQUEST -> CategoryFailureReason.AI_INVALID_REQUEST;
                    case AI_INVALID_RESPONSE -> CategoryFailureReason.AI_INVALID_RESPONSE;
                    case AI_UPSTREAM_ERROR -> CategoryFailureReason.AI_UPSTREAM_ERROR;
                    case AI_UNAVAILABLE, UNKNOWN -> CategoryFailureReason.AI_UNAVAILABLE;
                };
            }
            current = current.getCause();
        }
        return CategoryFailureReason.AI_UNAVAILABLE;
    }

    private boolean canRequestClassification(ExpenseCategoryClassifier.Context context) {
        return context != null
                && context.amount() > 0
                && context.merchantName() != null
                && !context.merchantName().isBlank();
    }
}
