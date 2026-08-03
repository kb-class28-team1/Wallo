package com.wallo.external.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.external.dto.CodefDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class CodefMockCardApprovalClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final CodefMockCardApprovalClient client = new CodefMockCardApprovalClient(
            restTemplate,
            "http://localhost:8080"
    );

    @Test
    void callsCardApprovalMockEndpoint() {
        CodefDto.CardApprovalRequest request = new CodefDto.CardApprovalRequest(
                "0311", "1", "mock_id", "mock_pw", "20260701", "20260731"
        );
        CodefDto.Response expected = CodefDto.Response.success("approvals");
        when(restTemplate.postForObject(
                "http://localhost:8080/mock/v1/kr/card/p/approval-list",
                request,
                CodefDto.Response.class
        )).thenReturn(expected);

        CodefDto.Response actual = client.getApprovals(request);

        assertEquals(expected, actual);
        verify(restTemplate).postForObject(
                "http://localhost:8080/mock/v1/kr/card/p/approval-list",
                request,
                CodefDto.Response.class
        );
    }
}
