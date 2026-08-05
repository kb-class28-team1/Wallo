package com.wallo.asset.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.chat.client.AiServerException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiCategoryRuleTest {

    private final CategoryClassificationClient client = mock(CategoryClassificationClient.class);
    private AiCategoryRule rule;

    @BeforeEach
    void setUp() {
        rule = new AiCategoryRule(client);
    }

    @Test
    void classifiesWithAiWhenConfidenceIsHighEnough() {
        when(client.classify(any())).thenReturn(
                new CategoryClassificationDto.Response("living", new BigDecimal("0.8600"))
        );

        Optional<ExpenseCategoryClassifier.Result> result = rule.classify(
                new ExpenseCategoryClassifier.Context("알 수 없는 상점", "기타", 12_000L)
        );

        assertTrue(result.isPresent());
        assertEquals("LIVING", result.get().category());
        assertEquals("AI", result.get().source());
        assertEquals(new BigDecimal("0.8600"), result.get().confidence());
        assertEquals("ai-v1", result.get().classifierVersion());
    }

    @Test
    void skipsLowConfidenceResult() {
        when(client.classify(any())).thenReturn(
                new CategoryClassificationDto.Response("LIVING", new BigDecimal("0.6999"))
        );

        assertTrue(rule.classify(context()).isEmpty());
    }

    @Test
    void skipsUnsupportedCategory() {
        when(client.classify(any())).thenReturn(
                new CategoryClassificationDto.Response("INCOME", new BigDecimal("0.9900"))
        );

        assertTrue(rule.classify(context()).isEmpty());
    }

    @Test
    void fallsBackWhenAiServerFails() {
        when(client.classify(any())).thenThrow(new AiServerException("AI unavailable"));

        assertTrue(rule.classify(context()).isEmpty());
    }

    @Test
    void doesNotCallAiWithoutPositiveAmount() {
        assertTrue(rule.classify(
                new ExpenseCategoryClassifier.Context("알 수 없는 상점", "기타")
        ).isEmpty());

        verify(client, never()).classify(any());
    }

    private ExpenseCategoryClassifier.Context context() {
        return new ExpenseCategoryClassifier.Context("알 수 없는 상점", "기타", 12_000L);
    }
}
