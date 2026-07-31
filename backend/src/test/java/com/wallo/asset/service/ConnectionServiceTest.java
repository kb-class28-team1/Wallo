package com.wallo.asset.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.exception.ConnectionConsentRequiredException;
import com.wallo.asset.mapper.ConnectionMapper;
import com.wallo.common.exception.ErrorCode;
import com.wallo.external.client.CodefClient;
import com.wallo.external.dto.CodefDto;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ConnectionServiceTest {

    private final CodefClient codefClient = mock(CodefClient.class);
    private final InstitutionService institutionService = mock(InstitutionService.class);
    private final ConnectionMapper connectionMapper = mock(ConnectionMapper.class);
    private final AssetSyncService assetSyncService = mock(AssetSyncService.class);
    private final ConnectionService connectionService = new ConnectionService(
            codefClient, institutionService, connectionMapper, assetSyncService);

    @Test
    public void connectAllAssetsThrowsConsentExceptionWhenConsentIsMissing() {
        assertConsentRequired(null);

        ConnectionDto.Request request = new ConnectionDto.Request();
        request.setConsentAgreed(false);
        assertConsentRequired(request);
    }

    @Test
    public void connectAllAssetsReturnsSuccessForEachSuccessfulInstitution() {
        givenConnectionTargets();
        when(codefClient.connectInstitution(any(CodefDto.Request.class)))
                .thenReturn(CodefDto.Response.success(Arrays.asList()));

        ConnectionDto.Request request = new ConnectionDto.Request();
        request.setConsentAgreed(true);

        when(connectionMapper.insertConnections(any(), org.mockito.ArgumentMatchers.eq(7L), any(), any(), any()))
                .thenReturn(3);
        when(connectionMapper.findActiveConnectionId(org.mockito.ArgumentMatchers.eq(7L), any()))
                .thenReturn(1L);
        ConnectionDto.Response response = connectionService.connectAllAssets(7L, request);

        assertEquals(3, response.getResults().size());
        assertEquals(ConnectionDto.Status.SUCCESS, response.getResults().get(0).getStatus());
        assertEquals(ConnectionDto.Status.SUCCESS, response.getResults().get(1).getStatus());
        assertEquals(ConnectionDto.Status.SUCCESS, response.getResults().get(2).getStatus());
        verify(codefClient, times(3)).connectInstitution(any(CodefDto.Request.class));
        verify(connectionMapper).insertConnections(any(), org.mockito.ArgumentMatchers.eq(7L), any(), any(), any());
    }

    @Test
    public void connectAllAssetsIncludesFailureWithoutStoppingOtherInstitutions() {
        givenConnectionTargets();
        when(codefClient.connectInstitution(any(CodefDto.Request.class)))
                .thenReturn(CodefDto.Response.success(Arrays.asList()))
                .thenReturn(CodefDto.Response.failure("CF-99999", "External service failed", ""))
                .thenReturn(CodefDto.Response.success(Arrays.asList()));

        ConnectionDto.Request request = new ConnectionDto.Request();
        request.setConsentAgreed(true);

        when(connectionMapper.insertConnections(any(), org.mockito.ArgumentMatchers.eq(7L), any(), any(), any()))
                .thenReturn(3);
        when(connectionMapper.findActiveConnectionId(org.mockito.ArgumentMatchers.eq(7L), any()))
                .thenReturn(1L);
        ConnectionDto.Response response = connectionService.connectAllAssets(7L, request);

        assertEquals(3, response.getResults().size());
        assertEquals(ConnectionDto.Status.SUCCESS, response.getResults().get(0).getStatus());
        assertEquals(ConnectionDto.Status.FAILED, response.getResults().get(1).getStatus());
        assertEquals("External service failed", response.getResults().get(1).getMessage());
        assertEquals(ConnectionDto.Status.SUCCESS, response.getResults().get(2).getStatus());
    }

    private void givenConnectionTargets() {
        List<Institution> institutions = Arrays.asList(
                new Institution("0004", "Bank", "BANK", "bank-logo"),
                new Institution("0311", "Card", "CARD", "card-logo"),
                new Institution("0264", "Stock", "STOCK", "stock-logo")
        );
        when(institutionService.getConnectionTargetInstitutions()).thenReturn(institutions);
    }

    private void assertConsentRequired(ConnectionDto.Request request) {
        try {
            connectionService.connectAllAssets(7L, request);
            fail("Consent is required before connecting assets.");
        } catch (ConnectionConsentRequiredException exception) {
            assertEquals(ErrorCode.CONNECTION_CONSENT_REQUIRED, exception.getErrorCode());
        }
    }
}
