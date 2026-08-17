package com.wallo.asset.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.chat.client.AiServerException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiCategoryRuleTest {

    private final CategoryClassificationClient client = mock(CategoryClassificationClient.class);
    private AiCategoryRule rule;

    @BeforeEach
    void setUp() {
        rule = new AiCategoryRule(client, new AiCategoryBatchExecutor(Runnable::run));
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
        verify(client).classify(any());
    }

    @Test
    void skipsLowConfidenceResult() {
        when(client.classify(any())).thenReturn(
                new CategoryClassificationDto.Response("LIVING", new BigDecimal("0.6999"))
        );

        ExpenseCategoryClassifier.Result result = rule.classify(context()).orElseThrow();

        assertEquals("ETC", result.category());
        assertEquals("FALLBACK", result.source());
        assertEquals(CategoryFailureReason.LOW_CONFIDENCE, result.failureReason());
    }

    @Test
    void skipsUnsupportedCategory() {
        when(client.classify(any())).thenReturn(
                new CategoryClassificationDto.Response("INCOME", new BigDecimal("0.9900"))
        );

        ExpenseCategoryClassifier.Result result = rule.classify(context()).orElseThrow();

        assertEquals("ETC", result.category());
        assertEquals("FALLBACK", result.source());
        assertEquals(CategoryFailureReason.AI_INVALID_RESPONSE, result.failureReason());
    }

    @Test
    void fallsBackWhenAiServerFails() {
        when(client.classify(any())).thenThrow(new AiServerException("AI unavailable"));

        Optional<ExpenseCategoryClassifier.Result> result = rule.classify(context());

        assertTrue(result.isPresent());
        assertEquals("ETC", result.get().category());
        assertEquals("FALLBACK", result.get().source());
        assertEquals(CategoryFailureReason.AI_UNAVAILABLE, result.get().failureReason());
        verify(client).classify(any());
    }

    @Test
    void doesNotCallAiWithoutPositiveAmount() {
        ExpenseCategoryClassifier.Result result = rule.classify(
                new ExpenseCategoryClassifier.Context("알 수 없는 상점", "기타")
        ).orElseThrow();

        assertEquals("FALLBACK", result.source());
        assertEquals(CategoryFailureReason.MISSING_INPUT, result.failureReason());
        verify(client, never()).classify(any());
    }

    @Test
    void splitsLargeBatchIntoRequestsOfAtMostFifty() {
        List<ExpenseCategoryClassifier.Context> contexts = contexts(123);
        when(client.classifyBatch(any())).thenAnswer(invocation -> responses(invocation.getArgument(0)));

        List<Optional<ExpenseCategoryClassifier.Result>> results = rule.classifyBatch(contexts);

        ArgumentCaptor<List<CategoryClassificationDto.Request>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).classifyBatch(captor.capture());
        assertEquals(List.of(50, 50, 23), captor.getAllValues().stream().map(List::size).toList());
        assertEquals(123, results.size());
        assertTrue(results.stream().allMatch(Optional::isPresent));
        assertEquals("LIVING", results.get(122).orElseThrow().category());
    }

    @Test
    void keepsBatchFailureScopedToTheFailedBatch() {
        List<ExpenseCategoryClassifier.Context> contexts = contexts(123);
        AtomicInteger calls = new AtomicInteger();
        when(client.classifyBatch(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                throw new AiServerException(
                        "AI timeout",
                        AiServerException.FailureReason.AI_TIMEOUT,
                        true,
                        504
                );
            }
            return responses(invocation.getArgument(0));
        });

        List<Optional<ExpenseCategoryClassifier.Result>> results = rule.classifyBatch(contexts);

        assertEquals("LIVING", results.get(0).orElseThrow().category());
        assertEquals("FALLBACK", results.get(50).orElseThrow().source());
        assertEquals(CategoryFailureReason.AI_TIMEOUT, results.get(50).orElseThrow().failureReason());
        assertEquals("LIVING", results.get(100).orElseThrow().category());
    }

    @Test
    void limitsConcurrentBatchRequestsToTwo() throws Exception {
        CategoryClassificationClient concurrentClient = mock(CategoryClassificationClient.class);
        AiCategoryRule concurrentRule = new AiCategoryRule(concurrentClient);
        List<ExpenseCategoryClassifier.Context> contexts = contexts(123);
        CountDownLatch firstTwoStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        when(concurrentClient.classifyBatch(any())).thenAnswer(invocation -> {
            int current = active.incrementAndGet();
            maxActive.updateAndGet(previous -> Math.max(previous, current));
            firstTwoStarted.countDown();
            try {
                if (!firstTwoStarted.await(2, TimeUnit.SECONDS)
                        || !release.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("batch executor did not release concurrent requests");
                }
                return responses(invocation.getArgument(0));
            } finally {
                active.decrementAndGet();
            }
        });

        CompletableFuture<List<Optional<ExpenseCategoryClassifier.Result>>> future =
                CompletableFuture.supplyAsync(() -> concurrentRule.classifyBatch(contexts));
        try {
            assertTrue(firstTwoStarted.await(2, TimeUnit.SECONDS));
            assertEquals(2, maxActive.get());
        } finally {
            release.countDown();
        }
        assertEquals(123, future.get(5, TimeUnit.SECONDS).size());
        verify(concurrentClient, times(3)).classifyBatch(any());
    }

    private List<ExpenseCategoryClassifier.Context> contexts(int count) {
        List<ExpenseCategoryClassifier.Context> contexts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            contexts.add(new ExpenseCategoryClassifier.Context(
                    "알 수 없는 상점 " + index,
                    "기타",
                    1_000L + index
            ));
        }
        return contexts;
    }

    private List<CategoryClassificationDto.Response> responses(
            List<CategoryClassificationDto.Request> requests
    ) {
        return requests.stream()
                .map(request -> new CategoryClassificationDto.Response(
                        "LIVING",
                        new BigDecimal("0.8600")
                ))
                .toList();
    }

    private ExpenseCategoryClassifier.Context context() {
        return new ExpenseCategoryClassifier.Context("알 수 없는 상점", "기타", 12_000L);
    }
}
