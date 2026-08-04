package com.wallo.external.controller;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.external.auth.CodefAccessTokenProvider;
import com.wallo.external.dto.CodefDto;
import com.wallo.external.service.CodefMockService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class CodefMockControllerTest {

    private final CodefMockService codefMockService = mock(CodefMockService.class);
    private final CodefAccessTokenProvider accessTokenProvider = mock(CodefAccessTokenProvider.class);
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        when(accessTokenProvider.getAccessToken()).thenReturn("mock-codef-token");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CodefMockController(codefMockService, accessTokenProvider)
        ).build();
    }

    @Test
    public void bankAccountEndpointLoadsBankAccountsFixture() throws Exception {
        assertFixtureIsReturned("/mock/v1/kr/bank/p/account/account-list");
    }

    @Test
    public void cardListEndpointLoadsCardListFixture() throws Exception {
        assertFixtureIsReturned("/mock/v1/kr/card/p/account/card-list");
    }

    @Test
    public void stockAccountEndpointLoadsStockAccountsFixture() throws Exception {
        assertFixtureIsReturned("/mock/v1/kr/stock/p/account/account-list");
    }

    @Test
    public void cardApprovalEndpointDelegatesToCodefMockService() throws Exception {
        when(codefMockService.getCardApprovals(any()))
                .thenReturn(CodefDto.Response.success("approvals"));

        String responseBody = mockMvc.perform(post("/mock/v1/kr/card/p/approval-list")
                        .header("Authorization", "Bearer mock-codef-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardApprovalRequestJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("CF-00000"));
        verify(codefMockService).getCardApprovals(any());
    }

    @Test
    public void bankTransactionEndpointAcceptsDocumentedPathAndBearerToken() throws Exception {
        when(codefMockService.getBankTransactions(any()))
                .thenReturn(CodefDto.Response.success("transactions"));

        String responseBody = mockMvc.perform(post("/v1/kr/bank/p/account/transaction-list")
                        .header("Authorization", "Bearer mock-codef-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bankTransactionRequestJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("CF-00000"));
        verify(codefMockService).getBankTransactions(any());
    }

    @Test
    public void allMockEndpointsRejectMissingBearerToken() throws Exception {
        String[] paths = {
                "/mock/v1/kr/bank/p/account/account-list",
                "/mock/v1/kr/card/p/account/card-list",
                "/mock/v1/kr/stock/p/account/account-list",
                "/mock/v1/kr/card/p/approval-list",
                "/mock/v1/kr/bank/p/account/transaction-list",
                "/v1/kr/bank/p/account/transaction-list"
        };

        for (String path : paths) {
            String responseBody = mockMvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJsonFor(path)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertTrue(responseBody.contains("CF-40100"));
        }
    }

    @Test
    public void mockEndpointRejectsIncorrectBearerToken() throws Exception {
        String responseBody = mockMvc.perform(post("/mock/v1/kr/card/p/approval-list")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardApprovalRequestJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("CF-40100"));
    }

    private void assertFixtureIsReturned(String path) throws Exception {
        when(codefMockService.getAssetResponse(eq(path), eq("0004")))
                .thenReturn(CodefDto.Response.success("fixture"));

        String responseBody = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer mock-codef-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assetRequestJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("CF-00000"));
        verify(codefMockService).getAssetResponse(path, "0004");
    }

    private String requestJsonFor(String path) {
        if (path.contains("approval-list")) {
            return cardApprovalRequestJson();
        }
        if (path.contains("transaction-list")) {
            return bankTransactionRequestJson();
        }
        return "{}";
    }

    private String cardApprovalRequestJson() {
        return "{"
                + "\"organization\":\"0311\","
                + "\"loginType\":\"1\","
                + "\"id\":\"mock_id\","
                + "\"password\":\"mock_password\","
                + "\"startDate\":\"20260701\","
                + "\"endDate\":\"20260731\""
                + "}";
    }

    private String bankTransactionRequestJson() {
        return "{"
                + "\"organization\":\"0004\","
                + "\"loginType\":\"1\","
                + "\"id\":\"mock_id\","
                + "\"password\":\"mock_password\","
                + "\"account\":\"123456-01-789012\","
                + "\"startDate\":\"20260701\","
                + "\"endDate\":\"20260731\""
                + "}";
    }

    private String assetRequestJson() {
        return "{"
                + "\"organization\":\"0004\","
                + "\"institutionType\":\"BANK\","
                + "\"loginType\":\"1\","
                + "\"id\":\"mock_id\","
                + "\"password\":\"mock_password\""
                + "}";
    }
}
