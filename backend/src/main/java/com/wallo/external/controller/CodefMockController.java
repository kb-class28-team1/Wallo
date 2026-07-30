package com.wallo.external.controller;

import com.wallo.external.dto.CodefDto;
import com.wallo.external.service.CodefMockResponseLoader;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodefMockController {

    private static final Map<String, String> FIXTURE_BY_PATH = Map.of(
            "/mock/v1/kr/bank/p/account/account-list", "bank-accounts.json",
            "/mock/v1/kr/card/p/account/card-list", "card-list.json",
            "/mock/v1/kr/stock/p/account/account-list", "stock-accounts.json"
    );

    private final CodefMockResponseLoader codefMockResponseLoader;

    public CodefMockController(CodefMockResponseLoader codefMockResponseLoader) {
        this.codefMockResponseLoader = codefMockResponseLoader;
    }

    @PostMapping({
            "/mock/v1/kr/bank/p/account/account-list",
            "/mock/v1/kr/card/p/account/card-list",
            "/mock/v1/kr/stock/p/account/account-list"
    })
    public CodefDto.Response getMockResponse(
            @RequestBody CodefDto.Request ignoredRequest,
            HttpServletRequest request
    ) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return loadResponse(FIXTURE_BY_PATH.get(requestPath));
    }

    private CodefDto.Response loadResponse(String fileName) {
        return codefMockResponseLoader.load(fileName);
    }
}
