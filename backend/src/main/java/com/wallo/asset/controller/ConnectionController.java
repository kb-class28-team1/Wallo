package com.wallo.asset.controller;

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

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/ping")
    public CommonResponse<String> ping() {
        return CommonResponse.success("connections-api-ok");
    }

    @PostMapping
    public CommonResponse<ConnectionDto.Response> connectAllAssets(
            @RequestBody ConnectionDto.Request request
    ) {
        return CommonResponse.success(connectionService.connectAllAssets(request));
    }
}



