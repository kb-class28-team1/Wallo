package com.wallo.asset.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.common.response.CommonResponse;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.service.ConnectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final CurrentUserProvider currentUserProvider;

    public ConnectionController(
            ConnectionService connectionService,
            CurrentUserProvider currentUserProvider) {
        this.connectionService = connectionService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public CommonResponse<ConnectionDto.Response> connectAllAssets(
            @RequestBody ConnectionDto.Request request
    ) {
        return CommonResponse.success(
                connectionService.connectAllAssets(currentUserProvider.getCurrentUserId(), request));
    }
}
