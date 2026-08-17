package com.wallo.asset.service;

import com.wallo.asset.classification.ExpenseCategoryClassifier;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.mapper.ExpenseMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reclassifies persisted fallback transactions without collecting the source account again. */
@Service
public class FallbackClassificationService {

    private final ExpenseMapper expenseMapper;
    private final ExpenseCategoryClassifier categoryClassifier;

    public FallbackClassificationService(
            ExpenseMapper expenseMapper,
            ExpenseCategoryClassifier categoryClassifier
    ) {
        this.expenseMapper = expenseMapper;
        this.categoryClassifier = categoryClassifier;
    }

    @Transactional
    public int reclassify(long userId) {
        List<ExpenseDto.Transaction> transactions = safeList(
                expenseMapper.selectFallbackTransactions(userId)
        );
        if (transactions.isEmpty()) {
            return 0;
        }

        List<ExpenseCategoryClassifier.Context> contexts = transactions.stream()
                .map(transaction -> new ExpenseCategoryClassifier.Context(
                        transaction.getMerchantName(),
                        null,
                        transaction.getAmount()
                ))
                .toList();
        List<ExpenseCategoryClassifier.Result> classifications = categoryClassifier.classifyBatch(contexts);
        int reclassifiedCount = 0;
        for (int index = 0; index < transactions.size(); index++) {
            ExpenseDto.Transaction transaction = transactions.get(index);
            ExpenseCategoryClassifier.Result classification = classifications != null
                    && index < classifications.size()
                    ? classifications.get(index)
                    : null;
            if (transaction == null
                    || transaction.getTransactionId() <= 0
                    || classification == null
                    || AssetTransactionConstants.FALLBACK_CATEGORY_SOURCE.equals(classification.source())) {
                continue;
            }

            reclassifiedCount += expenseMapper.updateReclassifiedCategory(
                    userId,
                    transaction.getTransactionId(),
                    classification.category(),
                    classification.source(),
                    classification.confidence(),
                    classification.classifierVersion()
            );
        }
        return reclassifiedCount;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
