package com.wallo.external.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.external.dto.CodefDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class CodefMockBankTransactionClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final CodefMockBankTransactionClient client = new CodefMockBankTransactionClient(
            restTemplate,
            "http://localhost:8080",
            "mock-codef-token"
    );

    @Test
    void callsBankTransactionMockEndpointWithBearerToken() {
        CodefDto.BankTransactionRequest request = new CodefDto.BankTransactionRequest(
                "0004",
                "1",
                "mock_id",
                "mock_pw",
                "123456-01-789012",
                "20260701",
                "20260731"
        );
        CodefDto.Response expected = CodefDto.Response.success("transactions");
        when(restTemplate.exchange(
                eq("http://localhost:8080/mock/v1/kr/bank/p/account/transaction-list"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(CodefDto.Response.class)
        )).thenReturn(ResponseEntity.ok(expected));

        CodefDto.Response actual = client.getTransactions(request);

        assertEquals(expected, actual);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8080/mock/v1/kr/bank/p/account/transaction-list"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(CodefDto.Response.class)
        );
        assertEquals("Bearer mock-codef-token", entityCaptor.getValue().getHeaders().getFirst("Authorization"));
        assertEquals(request, entityCaptor.getValue().getBody());
    }
}
