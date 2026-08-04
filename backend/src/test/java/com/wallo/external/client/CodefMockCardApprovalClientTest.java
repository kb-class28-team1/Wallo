package com.wallo.external.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class CodefMockCardApprovalClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final CodefAuthorizedRequestFactory requestFactory =
            new CodefAuthorizedRequestFactory(() -> "mock-codef-token");
    private final CodefMockCardApprovalClient client = new CodefMockCardApprovalClient(
            restTemplate,
            "http://localhost:8080",
            requestFactory
    );

    @Test
    void callsCardApprovalMockEndpoint() {
        CodefDto.CardApprovalRequest request = new CodefDto.CardApprovalRequest(
                "0311", "1", "mock_id", "mock_pw", "20260701", "20260731"
        );
        CodefDto.Response expected = CodefDto.Response.success("approvals");
        when(restTemplate.exchange(
                org.mockito.ArgumentMatchers.eq("http://localhost:8080/mock/v1/kr/card/p/approval-list"),
                org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
                org.mockito.ArgumentMatchers.any(HttpEntity.class),
                org.mockito.ArgumentMatchers.eq(CodefDto.Response.class)
        )).thenReturn(ResponseEntity.ok(expected));

        CodefDto.Response actual = client.getApprovals(request);

        assertEquals(expected, actual);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                org.mockito.ArgumentMatchers.eq("http://localhost:8080/mock/v1/kr/card/p/approval-list"),
                org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
                entityCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(CodefDto.Response.class)
        );
        assertEquals("Bearer mock-codef-token", entityCaptor.getValue().getHeaders().getFirst("Authorization"));
        assertEquals(request, entityCaptor.getValue().getBody());
    }
}
