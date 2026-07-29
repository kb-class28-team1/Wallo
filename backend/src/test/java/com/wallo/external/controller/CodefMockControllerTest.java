package com.wallo.external.controller;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.external.dto.CodefDto;
import com.wallo.external.service.CodefMockResponseLoader;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class CodefMockControllerTest {

    private final CodefMockResponseLoader responseLoader = mock(CodefMockResponseLoader.class);
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CodefMockController(responseLoader)).build();
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
}
