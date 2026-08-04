package com.wallo.asset.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.service.ConnectionService;
import com.wallo.asset.service.InstitutionService;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final InstitutionService institutionService;
    private final CurrentUserProvider currentUserProvider;

    public ConnectionController(
            ConnectionService connectionService,
            InstitutionService institutionService,
            CurrentUserProvider currentUserProvider
    ) {
        this.connectionService = connectionService;
        this.institutionService = institutionService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/institutions")
    public CommonResponse<ConnectionDto.InstitutionResponse> getInstitutions() {
        return CommonResponse.success(institutionService.getInstitutions());
    }

    @PostMapping("/connections")
    public CommonResponse<ConnectionDto.Response> connectAllAssets(
            @RequestBody ConnectionDto.Request request
    ) {
        return CommonResponse.success(
                connectionService.connectAllAssets(currentUserProvider.getCurrentUserId(), request));
    }
}
