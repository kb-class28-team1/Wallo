package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.asset.dto.AssetReportDto;
import java.time.YearMonth;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConsumptionInsightCacheTest {

    private final ConsumptionInsightCache cache = new ConsumptionInsightCache();
    private final ConsumptionInsightCache.Key key =
            new ConsumptionInsightCache.Key(7L, YearMonth.of(2026, 8));

    @Test
    void sharesOneAiGenerationAmongConcurrentRequests() throws Exception {
        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        AtomicInteger generationCount = new AtomicInteger();
        AssetReportDto.Insight generatedInsight = aiInsight();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AssetReportDto.Insight> first = executor.submit(() -> cache.getOrGenerate(
                    key,
                    () -> {
                        generationCount.incrementAndGet();
                        generationStarted.countDown();
                        await(releaseGeneration);
                        return generatedInsight;
                    }
            ));
            assertTrue(generationStarted.await(1, TimeUnit.SECONDS));

            Future<AssetReportDto.Insight> second = executor.submit(() -> cache.getOrGenerate(
                    key,
                    () -> {
                        generationCount.incrementAndGet();
                        return generatedInsight;
                    }
            ));
            releaseGeneration.countDown();

            assertEquals(generatedInsight.getReportTitle(), first.get().getReportTitle());
            assertEquals(generatedInsight.getReportTitle(), second.get().getReportTitle());
            assertEquals(1, generationCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidationRemovesCompletedAiResult() {
        AtomicInteger generationCount = new AtomicInteger();

        cache.getOrGenerate(key, () -> {
            generationCount.incrementAndGet();
            return aiInsight();
        });
        assertTrue(cache.get(key) != null);

        cache.invalidate(key.userId(), key.currentMonth());

        assertNull(cache.get(key));
        cache.getOrGenerate(key, () -> {
            generationCount.incrementAndGet();
            return aiInsight();
        });
        assertEquals(2, generationCount.get());
    }

    private AssetReportDto.Insight aiInsight() {
        return new AssetReportDto.Insight(
                "제목",
                "본문",
                AssetReportDto.GenerationMode.AI,
                "CAFE"
        );
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
