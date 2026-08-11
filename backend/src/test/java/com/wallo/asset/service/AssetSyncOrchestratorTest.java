package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.ConnectionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetSyncOrchestratorTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 7, 30);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 6);

    private final ConnectionMapper connectionMapper = mock(ConnectionMapper.class);
    private final AssetSyncWorker assetSyncWorker = mock(AssetSyncWorker.class);
    private final CardWithdrawalReconciliationService reconciliationService =
            mock(CardWithdrawalReconciliationService.class);

    @Test
    void syncsEveryActiveTargetAndReconcilesAfterSuccessfulSync() {
        when(connectionMapper.findActiveSyncTargets(7L)).thenReturn(List.of(
                target(11L, "0311", "CARD"),
                target(12L, "0004", "BANK")
        ));
        when(assetSyncWorker.sync(
                eq(7L),
                any(AssetSyncDto.SyncTarget.class),
                eq(START_DATE),
                eq(END_DATE)
        )).thenReturn(
                new AssetSyncDto.SyncStats(2, 3),
                new AssetSyncDto.SyncStats(1, 4)
        );

        AssetSyncDto.SyncResponse response = orchestrator().syncNow(7L);

        assertEquals(3, response.getInserted());
        assertEquals(7, response.getUpdated());
        assertEquals(0, response.getFailedConnections());
        verify(assetSyncWorker, times(2)).sync(
                eq(7L),
                any(AssetSyncDto.SyncTarget.class),
                eq(START_DATE),
                eq(END_DATE)
        );
        verify(reconciliationService).reconcile(7L);
    }

    @Test
    void isolatesConnectionFailureAndContinuesWithTheNextTarget() {
        when(connectionMapper.findActiveSyncTargets(7L)).thenReturn(List.of(
                target(11L, "0311", "CARD"),
                target(12L, "0004", "BANK")
        ));
        when(assetSyncWorker.sync(
                eq(7L),
                any(AssetSyncDto.SyncTarget.class),
                eq(START_DATE),
                eq(END_DATE)
        )).thenThrow(new RuntimeException("transaction persistence failed"))
                .thenReturn(new AssetSyncDto.SyncStats(1, 2));

        AssetSyncDto.SyncResponse response = orchestrator().syncNow(7L);

        assertEquals(1, response.getInserted());
        assertEquals(2, response.getUpdated());
        assertEquals(1, response.getFailedConnections());
        verify(assetSyncWorker, times(2)).sync(
                eq(7L),
                any(AssetSyncDto.SyncTarget.class),
                eq(START_DATE),
                eq(END_DATE)
        );
        verify(reconciliationService).reconcile(7L);
    }

    @Test
    void retriesCodefFailureBeforeMarkingConnectionAsFailed() {
        when(connectionMapper.findActiveSyncTargets(7L)).thenReturn(List.of(
                target(11L, "0311", "CARD")
        ));
        when(assetSyncWorker.sync(
                eq(7L),
                any(AssetSyncDto.SyncTarget.class),
                eq(START_DATE),
                eq(END_DATE)
        )).thenThrow(new AssetSyncWorker.CodefSyncException("0311", "temporary failure"))
                .thenReturn(new AssetSyncDto.SyncStats(3, 1));

        AssetSyncDto.SyncResponse response = orchestrator().syncNow(7L);

        assertEquals(3, response.getInserted());
        assertEquals(1, response.getUpdated());
        assertEquals(0, response.getFailedConnections());
        verify(assetSyncWorker, times(2)).sync(
                eq(7L),
                any(AssetSyncDto.SyncTarget.class),
                eq(START_DATE),
                eq(END_DATE)
        );
        verify(reconciliationService).reconcile(7L);
    }

    private AssetSyncOrchestrator orchestrator() {
        return new AssetSyncOrchestrator(
                connectionMapper,
                assetSyncWorker,
                reconciliationService,
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneId.of("Asia/Seoul")),
                7
        );
    }

    private AssetSyncDto.SyncTarget target(
            long connectionId,
            String organizationCode,
            String institutionType
    ) {
        return new AssetSyncDto.SyncTarget(
                7L,
                connectionId,
                institutionType.equals("CARD") ? 2L : 1L,
                organizationCode,
                institutionType + " institution",
                institutionType,
                institutionType + " group",
                institutionType,
                institutionType + "-logo"
        );
    }
}
