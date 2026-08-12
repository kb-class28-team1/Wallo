package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.external.CodefConstants;
import com.wallo.external.CodefRetryExecutor;
import com.wallo.external.auth.CodefCredential;
import com.wallo.external.auth.CodefCredentialProvider;
import com.wallo.external.client.CodefClient;
import com.wallo.external.dto.CodefDto;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetSyncWorkerTest {

    private final CodefClient codefClient = mock(CodefClient.class);
    private final CodefCredentialProvider credentialProvider = mock(CodefCredentialProvider.class);
    private final AssetSyncService assetSyncService = mock(AssetSyncService.class);
    private final CodefRetryExecutor retryExecutor = new CodefRetryExecutor(
            2,
            300,
            500,
            6500
    );
    private final AssetSyncWorker worker = new AssetSyncWorker(
            codefClient,
            credentialProvider,
            retryExecutor,
            assetSyncService
    );

    private final AssetSyncDto.SyncTarget target = new AssetSyncDto.SyncTarget(
            7L,
            11L,
            101L,
            "0311",
            "Mock Card",
            "CARD",
            "Mock Financial Group",
            "CARD",
            null
    );
    private final LocalDate startDate = LocalDate.of(2026, 8, 1);
    private final LocalDate endDate = LocalDate.of(2026, 8, 11);

    @BeforeEach
    void setUpCredential() {
        when(credentialProvider.getCredential(7L, "0311"))
                .thenReturn(new CodefCredential("1", "mock_id", "mock_pw"));
    }

    @Test
    void retriesTransientAssetConnectionBeforePersisting() {
        CodefDto.Response temporaryFailure = CodefDto.Response.failure(
                CodefConstants.CLIENT_FAILURE_CODE,
                "temporary failure",
                "timeout"
        );
        CodefDto.Response success = CodefDto.Response.success("assets");
        AssetSyncDto.SyncStats expectedStats = new AssetSyncDto.SyncStats(2, 1);
        when(codefClient.connectInstitution(any(CodefDto.Request.class)))
                .thenReturn(temporaryFailure, success);
        when(assetSyncService.sync(
                eq(7L),
                eq(11L),
                any(),
                same(success),
                eq(startDate),
                eq(endDate)
        )).thenReturn(expectedStats);

        AssetSyncDto.SyncStats actual = worker.sync(7L, target, startDate, endDate);

        assertEquals(expectedStats, actual);
        verify(codefClient, org.mockito.Mockito.times(2))
                .connectInstitution(any(CodefDto.Request.class));
        verify(assetSyncService).sync(
                eq(7L),
                eq(11L),
                any(),
                same(success),
                eq(startDate),
                eq(endDate)
        );
    }

    @Test
    void doesNotPersistWhenAssetConnectionHasPermanentFailure() {
        CodefDto.Response failure = CodefDto.Response.failure(
                CodefConstants.AUTHENTICATION_FAILURE_CODE,
                "authentication failed",
                ""
        );
        when(codefClient.connectInstitution(any(CodefDto.Request.class))).thenReturn(failure);

        assertThrows(
                AssetSyncWorker.CodefSyncException.class,
                () -> worker.sync(7L, target, startDate, endDate)
        );

        verify(codefClient).connectInstitution(any(CodefDto.Request.class));
        verify(assetSyncService, never()).sync(
                any(Long.class),
                any(Long.class),
                any(),
                any(CodefDto.Response.class),
                any(LocalDate.class),
                any(LocalDate.class)
        );
    }
}
