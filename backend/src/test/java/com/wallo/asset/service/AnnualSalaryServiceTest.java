package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.AnnualSalaryDto;
import com.wallo.asset.mapper.AnnualSalaryMapper;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnnualSalaryServiceTest {

    private AnnualSalaryMapper annualSalaryMapper;
    private AnnualSalaryService annualSalaryService;

    @BeforeEach
    void setUp() {
        annualSalaryMapper = mock(AnnualSalaryMapper.class);
        annualSalaryService = new AnnualSalaryService(annualSalaryMapper);
    }

    @Test
    void updatesAnnualSalaryForCurrentUser() {
        when(annualSalaryMapper.updateAnnualSalary(7L, 50_000_000L)).thenReturn(1);

        AnnualSalaryDto.Response response = annualSalaryService.updateAnnualSalary(
                7L,
                new AnnualSalaryDto.UpdateRequest(50_000_000L)
        );

        assertEquals(50_000_000L, response.getAnnualSalary());
        verify(annualSalaryMapper).updateAnnualSalary(7L, 50_000_000L);
    }

    @Test
    void rejectsMissingOrNonPositiveAnnualSalary() {
        assertInvalidSalary(null);
        assertInvalidSalary(new AnnualSalaryDto.UpdateRequest(null));
        assertInvalidSalary(new AnnualSalaryDto.UpdateRequest(0L));
        assertInvalidSalary(new AnnualSalaryDto.UpdateRequest(-1L));

        verify(annualSalaryMapper, never()).updateAnnualSalary(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void rejectsUpdateWhenUserDoesNotExist() {
        when(annualSalaryMapper.updateAnnualSalary(99L, 50_000_000L)).thenReturn(0);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> annualSalaryService.updateAnnualSalary(
                        99L,
                        new AnnualSalaryDto.UpdateRequest(50_000_000L)
                )
        );

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    private void assertInvalidSalary(AnnualSalaryDto.UpdateRequest request) {
        CustomException exception = assertThrows(
                CustomException.class,
                () -> annualSalaryService.updateAnnualSalary(7L, request)
        );

        assertEquals(ErrorCode.INVALID_ANNUAL_SALARY, exception.getErrorCode());
    }
}
