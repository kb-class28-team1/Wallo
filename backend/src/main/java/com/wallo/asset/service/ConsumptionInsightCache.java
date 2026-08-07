package com.wallo.asset.service;

import com.wallo.asset.dto.AssetReportDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Caches completed consumption reports separately from reports currently being generated.
 */
@Component
public class ConsumptionInsightCache {

    private static final Duration AI_CACHE_TTL = Duration.ofHours(24);
    private static final Duration FAILURE_RETRY_COOLDOWN = Duration.ofMinutes(5);

    private final ConcurrentMap<Key, CachedInsight> completedCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Key, FailureState> failureCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Key, InFlightInsight> inFlightCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Key, Long> cacheVersions = new ConcurrentHashMap<>();
    private final Clock clock;

    public ConsumptionInsightCache() {
        this(Clock.systemUTC());
    }

    ConsumptionInsightCache(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public AssetReportDto.Insight get(Key key) {
        Instant now = clock.instant();
        CachedInsight cachedInsight = completedCache.get(key);
        if (cachedInsight != null) {
            if (now.isBefore(cachedInsight.expiresAt())) {
                return copyInsight(cachedInsight.insight());
            }
            completedCache.remove(key, cachedInsight);
        }

        FailureState failureState = failureCache.get(key);
        if (failureState == null) {
            return null;
        }

        long currentVersion = cacheVersions.getOrDefault(key, 0L);
        if (failureState.version() != currentVersion
                || !now.isBefore(failureState.retryAfter())) {
            failureCache.remove(key, failureState);
            return null;
        }

        return copyInsight(failureState.insight());
    }

    public AssetReportDto.Insight getOrGenerate(
            Key key,
            Supplier<AssetReportDto.Insight> generator
    ) {
        AssetReportDto.Insight cachedInsight = get(key);
        if (cachedInsight != null) {
            return cachedInsight;
        }

        while (true) {
            long version = cacheVersions.getOrDefault(key, 0L);
            InFlightInsight newRequest = new InFlightInsight(version, new CompletableFuture<>());
            InFlightInsight existingRequest = inFlightCache.putIfAbsent(key, newRequest);

            if (existingRequest == null) {
                return generateAsOwner(key, newRequest, generator);
            }

            if (existingRequest.version() == version) {
                return await(existingRequest.future());
            }

            if (inFlightCache.replace(key, existingRequest, newRequest)) {
                return generateAsOwner(key, newRequest, generator);
            }
        }
    }

    public void invalidate(long userId, YearMonth currentMonth) {
        Key key = new Key(userId, currentMonth);
        cacheVersions.merge(key, 1L, Long::sum);
        completedCache.remove(key);
        failureCache.remove(key);
    }

    public void invalidateAfterCommit(long userId, YearMonth currentMonth) {
        Runnable invalidation = () -> invalidate(userId, currentMonth);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidation.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidation.run();
            }
        });
    }

    private AssetReportDto.Insight generateAsOwner(
            Key key,
            InFlightInsight request,
            Supplier<AssetReportDto.Insight> generator
    ) {
        try {
            AssetReportDto.Insight generatedInsight = generator.get();
            if (cacheVersions.getOrDefault(key, 0L) == request.version()) {
                if (isAiResult(generatedInsight)) {
                    completedCache.put(
                            key,
                            new CachedInsight(
                                    copyInsight(generatedInsight),
                                    clock.instant().plus(AI_CACHE_TTL)
                            )
                    );
                    failureCache.remove(key);
                } else if (isFallbackResult(generatedInsight)) {
                    failureCache.put(
                            key,
                            new FailureState(
                                    request.version(),
                                    copyInsight(generatedInsight),
                                    clock.instant().plus(FAILURE_RETRY_COOLDOWN)
                            )
                    );
                }
            }
            request.future().complete(copyInsight(generatedInsight));
            return copyInsight(generatedInsight);
        } catch (RuntimeException exception) {
            request.future().completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightCache.remove(key, request);
        }
    }

    private AssetReportDto.Insight await(CompletableFuture<AssetReportDto.Insight> future) {
        try {
            return copyInsight(future.join());
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private boolean isAiResult(AssetReportDto.Insight insight) {
        return insight != null && insight.getGenerationMode() == AssetReportDto.GenerationMode.AI;
    }

    private boolean isFallbackResult(AssetReportDto.Insight insight) {
        return insight != null
                && insight.getGenerationMode() == AssetReportDto.GenerationMode.FALLBACK;
    }

    private AssetReportDto.Insight copyInsight(AssetReportDto.Insight insight) {
        if (insight == null) {
            return null;
        }
        return new AssetReportDto.Insight(
                insight.getReportTitle(),
                insight.getReportContent(),
                insight.getGenerationMode(),
                insight.getCategory()
        );
    }

    public record Key(long userId, YearMonth currentMonth) {
    }

    private record CachedInsight(
            AssetReportDto.Insight insight,
            Instant expiresAt
    ) {
    }

    private record FailureState(
            long version,
            AssetReportDto.Insight insight,
            Instant retryAfter
    ) {
    }

    private record InFlightInsight(
            long version,
            CompletableFuture<AssetReportDto.Insight> future
    ) {
    }
}
