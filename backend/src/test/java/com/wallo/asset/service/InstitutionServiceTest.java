package com.wallo.asset.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.mapper.InstitutionMapper;
import java.util.Arrays;
import org.junit.Test;

public class InstitutionServiceTest {

    private final InstitutionMapper institutionMapper = mock(InstitutionMapper.class);
    private final InstitutionService institutionService =
            new InstitutionService(institutionMapper, new ObjectMapper());

    @Test
    public void getInstitutionsReturnsDatabaseInstitutionsGroupedByType() {
        Institution bank = institution(1L, "0004", "Bank", "BANK", "[\"Account\"]");
        bank.setFinancialGroupCode("KB");
        bank.setFinancialGroupName("KB Financial");
        Institution card = institution(2L, "0311", "Card", "CARD", "[\"Credit card\"]");
        Institution stock = institution(3L, "0264", "Stock", "STOCK", "[\"Stock\"]");
        when(institutionMapper.findActiveInstitutions()).thenReturn(Arrays.asList(bank, card, stock));

        ConnectionDto.InstitutionResponse response = institutionService.getInstitutions();

        assertEquals(1, response.getBanks().size());
        assertEquals(1L, response.getBanks().get(0).getInstitutionId().longValue());
        assertEquals("0004", response.getBanks().get(0).getCodefOrganizationCode());
        assertEquals("KB", response.getBanks().get(0).getFinancialGroupCode());
        assertEquals("KB Financial", response.getBanks().get(0).getFinancialGroupName());
        assertEquals("Account", response.getBanks().get(0).getServices().get(0));
        assertEquals(1, response.getCards().size());
        assertEquals(1, response.getStocks().size());
    }

    private Institution institution(Long id, String code, String name, String type, String services) {
        Institution institution = new Institution(id, code, name, type, "logo");
        institution.setServices(services);
        return institution;
    }
}
