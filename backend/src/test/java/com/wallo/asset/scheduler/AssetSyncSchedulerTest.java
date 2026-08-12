package com.wallo.asset.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.ConnectionMapper;
import com.wallo.asset.service.AssetSyncOrchestrator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class AssetSyncSchedulerTest {

    private final ConnectionMapper connectionMapper = mock(ConnectionMapper.class);
    private final AssetSyncOrchestrator assetSyncOrchestrator = mock(AssetSyncOrchestrator.class);

    @Test
    void syncsEveryUserWithAnActiveConnection() {
        when(connectionMapper.findUserIdsWithActiveConnections()).thenReturn(List.of(7L, 8L));
        when(assetSyncOrchestrator.syncNow(7L)).thenReturn(successfulResult());
        when(assetSyncOrchestrator.syncNow(8L)).thenReturn(successfulResult());

        new AssetSyncScheduler(connectionMapper, assetSyncOrchestrator, true).syncAssets();

        verify(assetSyncOrchestrator).syncNow(7L);
        verify(assetSyncOrchestrator).syncNow(8L);
    }

    @Test
    void doesNothingWhenSchedulerIsDisabled() {
        new AssetSyncScheduler(connectionMapper, assetSyncOrchestrator, false).syncAssets();

        verifyNoInteractions(connectionMapper, assetSyncOrchestrator);
    }

    @Test
    void continuesWithTheNextUserWhenOneSynchronizationFails() {
        when(connectionMapper.findUserIdsWithActiveConnections()).thenReturn(List.of(7L, 8L));
        doThrow(new RuntimeException("CODEF unavailable"))
                .when(assetSyncOrchestrator)
                .syncNow(7L);
        when(assetSyncOrchestrator.syncNow(8L)).thenReturn(successfulResult());

        assertDoesNotThrow(
                () -> new AssetSyncScheduler(connectionMapper, assetSyncOrchestrator, true).syncAssets()
        );

        verify(assetSyncOrchestrator).syncNow(8L);
    }

    @Test
    void usesTheConfiguredTwelveHourKoreanTimeZoneSchedule() throws NoSuchMethodException {
        Scheduled scheduled = AssetSyncScheduler.class
                .getDeclaredMethod("syncAssets")
                .getAnnotation(Scheduled.class);

        assertEquals("${asset-sync.scheduler.cron:0 0 */12 * * *}", scheduled.cron());
        assertEquals("Asia/Seoul", scheduled.zone());
    }

    private AssetSyncDto.SyncResponse successfulResult() {
        return new AssetSyncDto.SyncResponse(null, 3, 42, 0);
    }
}
