package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.mapper.AnnualSalaryMapper;
import com.wallo.external.client.IncomeProofClient;
import com.wallo.external.dto.CodefDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnnualSalarySyncServiceTest {

    private IncomeProofClient incomeProofClient;
    private AnnualSalaryMapper annualSalaryMapper;
    private AnnualSalarySyncService service;

    @BeforeEach
    void setUp() {
        incomeProofClient = mock(IncomeProofClient.class);
        annualSalaryMapper = mock(AnnualSalaryMapper.class);
        service = new AnnualSalarySyncService(
                incomeProofClient,
                annualSalaryMapper,
                new ObjectMapper(),
                Clock.fixed(
                        Instant.parse("2026-08-10T00:00:00Z"),
                        ZoneId.of("Asia/Seoul")
                )
        );
    }

    @Test
    void savesPreviousYearsEarnedIncomeOnly() {
        CodefDto.PaymentDetails earnedIncome = payment("2025", "근로소득", "50,000,000");
        CodefDto.PaymentDetails secondEmployer = payment("2025", "근로소득", "10,000,000");
        CodefDto.PaymentDetails businessIncome = payment("2025", "사업소득", "12,000,000");
        CodefDto.PaymentDetails previousYear = payment("2024", "근로소득", "45,000,000");
        CodefDto.IncomeProofData data = new CodefDto.IncomeProofData();
        data.setResPaymentDetailsStatusList(List.of(
                earnedIncome,
                secondEmployer,
                businessIncome,
                previousYear
        ));
        when(incomeProofClient.getIncomeProof(any())).thenReturn(CodefDto.Response.success(data));
        when(annualSalaryMapper.updateAnnualSalary(7L, 60_000_000L)).thenReturn(1);

        ConnectionDto.AnnualSalaryLookupStatus status = service.syncAnnualSalary(7L);

        assertEquals(ConnectionDto.AnnualSalaryLookupStatus.AVAILABLE, status);
        ArgumentCaptor<CodefDto.IncomeProofRequest> requestCaptor =
                ArgumentCaptor.forClass(CodefDto.IncomeProofRequest.class);
        verify(incomeProofClient).getIncomeProof(requestCaptor.capture());
        assertEquals("0001", requestCaptor.getValue().getOrganization());
        assertEquals("2025", requestCaptor.getValue().getSearchStartYear());
        assertEquals("2025", requestCaptor.getValue().getSearchEndYear());
        verify(annualSalaryMapper).updateAnnualSalary(7L, 60_000_000L);
    }

    @Test
    void doesNotOverwriteSalaryWhenIncomeProofFails() {
        when(incomeProofClient.getIncomeProof(any()))
                .thenReturn(CodefDto.Response.failure("CF-99999", "failed", ""));

        ConnectionDto.AnnualSalaryLookupStatus status = service.syncAnnualSalary(7L);

        assertEquals(ConnectionDto.AnnualSalaryLookupStatus.UNAVAILABLE, status);
        verify(annualSalaryMapper, never()).updateAnnualSalary(anyLong(), anyLong());
    }

    @Test
    void reportsErrorWhenIncomeProofClientFails() {
        when(incomeProofClient.getIncomeProof(any()))
                .thenThrow(new IllegalStateException("CODEF unavailable"));

        ConnectionDto.AnnualSalaryLookupStatus status = service.syncAnnualSalary(7L);

        assertEquals(ConnectionDto.AnnualSalaryLookupStatus.ERROR, status);
        verify(annualSalaryMapper, never()).updateAnnualSalary(anyLong(), anyLong());
    }

    private CodefDto.PaymentDetails payment(String year, String type, String amount) {
        CodefDto.PaymentDetails payment = new CodefDto.PaymentDetails();
        payment.setResAttrYear(year);
        payment.setResType(type);
        payment.setResPaidTotalAmt(amount);
        return payment;
    }
}
