package com.wallo.external.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.dto.CodefDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class CodefMockClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final CodefAuthorizedRequestFactory requestFactory =
            new CodefAuthorizedRequestFactory(() -> "mock-codef-token");
    private final CodefMockClient client = new CodefMockClient(
            restTemplate,
            new CodefMockApiUrlProvider("http://localhost:8080"),
            requestFactory
    );

    @Test
    void callsInstitutionMockEndpointWithBearerToken() {
        CodefDto.Request request = new CodefDto.Request();
        request.setInstitutionType("CARD");
        CodefDto.Response expected = CodefDto.Response.success("cards");
        when(restTemplate.exchange(
                eq("http://localhost:8080/mock/v1/kr/card/p/account/card-list"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(CodefDto.Response.class)
        )).thenReturn(ResponseEntity.ok(expected));

        CodefDto.Response actual = client.connectInstitution(request);

        assertEquals(expected, actual);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8080/mock/v1/kr/card/p/account/card-list"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(CodefDto.Response.class)
        );
        assertEquals(
                "Bearer mock-codef-token",
                entityCaptor.getValue().getHeaders().getFirst("Authorization")
        );
        CodefDto.Request sentRequest = (CodefDto.Request) entityCaptor.getValue().getBody();
        assertEquals(request.getInstitutionType(), sentRequest.getInstitutionType());
        assertEquals(request.getPassword(), sentRequest.getPassword());
    }

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
        assertEquals(
                "Bearer mock-codef-token",
                entityCaptor.getValue().getHeaders().getFirst("Authorization")
        );
        CodefDto.BankTransactionRequest sentRequest =
                (CodefDto.BankTransactionRequest) entityCaptor.getValue().getBody();
        assertEquals(request.getAccount(), sentRequest.getAccount());
        assertEquals(request.getPassword(), sentRequest.getPassword());
    }

    @Test
    void callsCardApprovalMockEndpointWithBearerToken() {
        CodefDto.CardApprovalRequest request = new CodefDto.CardApprovalRequest(
                "0311", "1", "mock_id", "mock_pw", "20260701", "20260731"
        );
        CodefDto.Response expected = CodefDto.Response.success("approvals");
        when(restTemplate.exchange(
                eq("http://localhost:8080/mock/v1/kr/card/p/approval-list"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(CodefDto.Response.class)
        )).thenReturn(ResponseEntity.ok(expected));

        CodefDto.Response actual = client.getApprovals(request);

        assertEquals(expected, actual);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8080/mock/v1/kr/card/p/approval-list"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(CodefDto.Response.class)
        );
        assertEquals(
                "Bearer mock-codef-token",
                entityCaptor.getValue().getHeaders().getFirst("Authorization")
        );
        CodefDto.CardApprovalRequest sentRequest =
                (CodefDto.CardApprovalRequest) entityCaptor.getValue().getBody();
        assertEquals(request.getOrganization(), sentRequest.getOrganization());
        assertEquals(request.getPassword(), sentRequest.getPassword());
    }

    @Test
    void usesConfiguredSandboxBaseUrlPathAndPasswordEncryptor() {
        CodefMockClient configuredClient = new CodefMockClient(
                restTemplate,
                new CodefMockApiUrlProvider("https://sandbox.codef.example/"),
                requestFactory,
                password -> "encrypted(" + password + ")",
                "/v1"
        );
        CodefDto.Request request = new CodefDto.Request(
                "0004", "BANK", "1", "real_id", "real_pw"
        );
        CodefDto.Response expected = CodefDto.Response.success("accounts");
        when(restTemplate.exchange(
                eq("https://sandbox.codef.example/v1/kr/bank/p/account/account-list"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(CodefDto.Response.class)
        )).thenReturn(ResponseEntity.ok(expected));

        CodefDto.Response actual = configuredClient.connectInstitution(request);

        assertEquals(expected, actual);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://sandbox.codef.example/v1/kr/bank/p/account/account-list"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(CodefDto.Response.class)
        );
        CodefDto.Request sentRequest = (CodefDto.Request) entityCaptor.getValue().getBody();
        assertEquals("encrypted(real_pw)", sentRequest.getPassword());
    }
}
