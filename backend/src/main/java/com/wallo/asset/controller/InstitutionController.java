package com.wallo.asset.controller;

import com.wallo.common.response.CommonResponse;
import com.wallo.asset.dto.InstitutionDto;
import com.wallo.asset.service.InstitutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/institutions")
public class InstitutionController {

    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @GetMapping
    public CommonResponse<InstitutionDto.Response> getInstitutions() {
        return CommonResponse.success(institutionService.getInstitutions());
    }
}



