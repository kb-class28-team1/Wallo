package com.wallo.asset.service;

import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.ConnectionMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Coordinates a complete user synchronization across all active institutions. */
@Service
public class AssetSyncOrchestrator {

    private static final int MAX_CODEF_ATTEMPTS = 2;
    private static final Logger LOGGER = Logger.getLogger(AssetSyncOrchestrator.class.getName());

    private final ConnectionMapper connectionMapper;
    private final AssetSyncWorker assetSyncWorker;
    private final CardWithdrawalReconciliationService cardWithdrawalReconciliationService;
    private final Clock clock;
    private final int lookbackDays;
    private final ConcurrentMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public AssetSyncOrchestrator(
            ConnectionMapper connectionMapper,
            AssetSyncWorker assetSyncWorker,
            CardWithdrawalReconciliationService cardWithdrawalReconciliationService,
            Clock clock,
            @Value("${asset-sync.lookback-days:7}") int lookbackDays
    ) {
        if (lookbackDays < 0) {
            throw new IllegalArgumentException("Asset synchronization lookback days must not be negative.");
        }
        this.connectionMapper = connectionMapper;
        this.assetSyncWorker = assetSyncWorker;
        this.cardWithdrawalReconciliationService = cardWithdrawalReconciliationService;
        this.clock = clock;
        this.lookbackDays = lookbackDays;
    }

    public AssetSyncDto.SyncResponse syncNow(long userId) {
        ReentrantLock userLock = userLocks.computeIfAbsent(userId, ignored -> new ReentrantLock());
        userLock.lock();
        try {
            return syncLocked(userId);
        } finally {
            userLock.unlock();
        }
    }

    private AssetSyncDto.SyncResponse syncLocked(long userId) {
        LocalDate endDate = LocalDate.now(clock);
        LocalDate startDate = endDate.minusDays(lookbackDays);
        AssetSyncDto.SyncStats totalStats = AssetSyncDto.SyncStats.empty();
        int failedConnections = 0;
        int successfulConnections = 0;

        for (AssetSyncDto.SyncTarget target : activeTargets(userId)) {
            try {
                AssetSyncDto.SyncStats targetStats = syncWithRetry(
                        userId,
                        target,
                        startDate,
                        endDate
                );
                totalStats = totalStats.plus(targetStats);
                successfulConnections++;
            } catch (RuntimeException exception) {
                failedConnections++;
                LOGGER.log(
                        Level.WARNING,
                        String.format(
                                "asset-sync-orchestrator failed userId=%d connectionId=%d organization=%s",
                                userId,
                                target.getConnectionId(),
                                target.getCodefOrganizationCode()
                        ),
                        exception
                );
            }
        }

        if (successfulConnections > 0) {
            cardWithdrawalReconciliationService.reconcile(userId);
        }

        return new AssetSyncDto.SyncResponse(
                LocalDateTime.now(clock),
                totalStats.getInserted(),
                totalStats.getUpdated(),
                failedConnections
        );
    }

    private AssetSyncDto.SyncStats syncWithRetry(
            long userId,
            AssetSyncDto.SyncTarget target,
            LocalDate startDate,
            LocalDate endDate
    ) {
        for (int attempt = 1; attempt <= MAX_CODEF_ATTEMPTS; attempt++) {
            try {
                return assetSyncWorker.sync(userId, target, startDate, endDate);
            } catch (AssetSyncWorker.CodefSyncException exception) {
                if (attempt == MAX_CODEF_ATTEMPTS) {
                    throw exception;
                }
                LOGGER.log(
                        Level.WARNING,
                        String.format(
                                "asset-sync-orchestrator retry userId=%d connectionId=%d attempt=%d",
                                userId,
                                target.getConnectionId(),
                                attempt + 1
                        ),
                        exception
                );
            }
        }
        throw new IllegalStateException("Asset synchronization did not produce a result.");
    }

    private List<AssetSyncDto.SyncTarget> activeTargets(long userId) {
        List<AssetSyncDto.SyncTarget> targets = connectionMapper.findActiveSyncTargets(userId);
        return targets == null ? Collections.emptyList() : targets;
    }
}
