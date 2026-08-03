package com.wallo.external.controller;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.external.dto.CodefDto;
import com.wallo.external.service.CodefMockResponseLoader;
import com.wallo.external.service.CodefTransactionMockService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class CodefMockControllerTest {

    private final CodefMockResponseLoader responseLoader = mock(CodefMockResponseLoader.class);
    private final CodefTransactionMockService transactionMockService = mock(CodefTransactionMockService.class);
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CodefMockController(responseLoader, transactionMockService)
        ).build();
    }

    @Test
    public void bankAccountEndpointLoadsBankAccountsFixture() throws Exception {
        assertFixtureIsReturned("/mock/v1/kr/bank/p/account/account-list", "bank-accounts.json");
    }

    @Test
    public void cardListEndpointLoadsCardListFixture() throws Exception {
        assertFixtureIsReturned("/mock/v1/kr/card/p/account/card-list", "card-list.json");
    }

    @Test
    public void stockAccountEndpointLoadsStockAccountsFixture() throws Exception {
        assertFixtureIsReturned("/mock/v1/kr/stock/p/account/account-list", "stock-accounts.json");
    }

    @Test
    public void cardApprovalEndpointDelegatesToTransactionMockService() throws Exception {
        when(transactionMockService.getCardApprovals(any()))
                .thenReturn(CodefDto.Response.success("approvals"));

        String responseBody = mockMvc.perform(post("/mock/v1/kr/card/p/approval-list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardApprovalRequestJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("CF-00000"));
        verify(transactionMockService).getCardApprovals(any());
    }

    @Test
    public void bankTransactionEndpointAcceptsDocumentedPathAndBearerToken() throws Exception {
        when(transactionMockService.getBankTransactions(any(), eq("Bearer mock-token")))
                .thenReturn(CodefDto.Response.success("transactions"));

        String responseBody = mockMvc.perform(post("/v1/kr/bank/p/account/transaction-list")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bankTransactionRequestJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("CF-00000"));
        verify(transactionMockService).getBankTransactions(any(), eq("Bearer mock-token"));
    }

    private void assertFixtureIsReturned(String path, String fixtureName) throws Exception {
        when(responseLoader.load(fixtureName)).thenReturn(CodefDto.Response.success("fixture"));

        String responseBody = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("CF-00000"));
        verify(responseLoader).load(fixtureName);
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
}
