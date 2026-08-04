package com.wallo.external.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.external.dto.CodefDto;
import org.junit.Test;

public class CodefMockResponseLoaderTest {

    private final CodefMockResponseLoader loader = new CodefMockResponseLoader(new ObjectMapper());

    @Test
    public void loadReturnsBankMockResponse() {
        CodefDto.Response response = loader.load("bank-accounts.json");

        assertSuccessResponse(response);
        CodefDto.AssetData data = new ObjectMapper().convertValue(response.getData(), CodefDto.AssetData.class);
        assertEquals(2, data.getAccounts().size());
        assertEquals(1, data.getLoans().size());
        assertEquals(1, data.getTransactions().size());
        assertEquals(5, data.getAssetSnapshots().size());
        assertEquals("2026-08", data.getAssetSnapshots().get(4).getSnapshotMonth());
        assertEquals("40100000", data.getAssetSnapshots().get(4).getTotalAssets());
    }

    @Test
    public void loadReturnsCardMockResponse() {
        CodefDto.Response response = loader.load("card-list.json");

        assertSuccessResponse(response);
        CodefDto.AssetData data = new ObjectMapper().convertValue(response.getData(), CodefDto.AssetData.class);
        assertEquals(2, data.getCards().size());
        assertNull(data.getTransactions());
    }

    @Test
    public void loadReturnsStockMockResponse() {
        CodefDto.Response response = loader.load("stock-accounts.json");

        assertSuccessResponse(response);
    }

    @Test
    public void loadReturnsNotFoundResponseWhenFixtureDoesNotExist() {
        CodefDto.Response response = loader.load("missing.json");

        assertNotNull(response);
        assertNotNull(response.getResult());
        assertEquals("CF-40400", response.getResult().getCode());
        assertEquals(null, response.getData());
    }

    private void assertSuccessResponse(CodefDto.Response response) {
        assertNotNull(response);
        assertNotNull(response.getResult());
        assertEquals("CF-00000", response.getResult().getCode());
        assertNotNull(response.getData());
    }
}
