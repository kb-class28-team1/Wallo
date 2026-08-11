package com.wallo.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.service.AssetSyncService;
import com.wallo.external.client.CodefClient;
import com.wallo.external.dto.CodefDto;
import com.wallo.goal.dto.GoalAccountDto;
import com.wallo.goal.mapper.GoalAccountMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoalAccountSyncServiceTest {

    private final GoalAccountMapper goalAccountMapper = mock(GoalAccountMapper.class);
    private final CodefClient codefClient = mock(CodefClient.class);
    private final AssetSyncService assetSyncService = mock(AssetSyncService.class);
    private final GoalAccountSyncService goalAccountSyncService = new GoalAccountSyncService(
            goalAccountMapper,
            codefClient,
            assetSyncService
    );

    @Test
    void syncsSelectedAccountWithStoredConnectionCredentials() {
        GoalAccountDto.SyncTarget target = new GoalAccountDto.SyncTarget(
                11L,
                1L,
                "0004",
                "Wallo Bank",
                "BANK",
                "Wallo Financial Group",
                "BANK",
                "bank-logo",
                "ID",
                "mock-user",
                "mock-password"
        );
        CodefDto.Response response = CodefDto.Response.success(java.util.Map.of());
        when(goalAccountMapper.findSelectedAccountSyncTargets(7L)).thenReturn(List.of(target));
        when(codefClient.connectInstitution(any(CodefDto.Request.class))).thenReturn(response);

        goalAccountSyncService.syncSelectedAccounts(7L);

        ArgumentCaptor<CodefDto.Request> requestCaptor =
                ArgumentCaptor.forClass(CodefDto.Request.class);
        verify(codefClient).connectInstitution(requestCaptor.capture());
        assertEquals("0004", requestCaptor.getValue().getOrganization());
        assertEquals("BANK", requestCaptor.getValue().getInstitutionType());
        assertEquals("ID", requestCaptor.getValue().getLoginType());
        assertEquals("mock-user", requestCaptor.getValue().getId());
        assertEquals("mock-password", requestCaptor.getValue().getPassword());

        ArgumentCaptor<Institution> institutionCaptor =
                ArgumentCaptor.forClass(Institution.class);
        verify(assetSyncService).syncAccountBalances(
                eq(11L),
                institutionCaptor.capture(),
                same(response)
        );
        assertEquals(1L, institutionCaptor.getValue().getInstitutionId());
        assertEquals("Wallo Bank", institutionCaptor.getValue().getName());
        assertEquals("BANK", institutionCaptor.getValue().getInstitutionType());
    }
}
