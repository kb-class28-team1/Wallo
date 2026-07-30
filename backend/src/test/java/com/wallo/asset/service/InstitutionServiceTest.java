package com.wallo.asset.service;

import static org.junit.Assert.assertEquals;

import com.wallo.asset.dto.InstitutionDto;
import org.junit.Test;

public class InstitutionServiceTest {

    private final InstitutionService institutionService = new InstitutionService();

    @Test
    public void getInstitutionsReturnsBankCardAndStockGroups() {
        InstitutionDto.Response response = institutionService.getInstitutions();

        assertEquals(2, response.getBanks().size());
        assertEquals(2, response.getCards().size());
        assertEquals(2, response.getStocks().size());
        assertEquals("0004", response.getBanks().get(0).getInstitutionId());
        assertEquals("0311", response.getCards().get(0).getInstitutionId());
        assertEquals("0264", response.getStocks().get(0).getInstitutionId());
    }
}
