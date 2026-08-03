package com.wallo.external.controller;

import com.wallo.external.dto.CodefDto;
import com.wallo.external.service.CodefMockResponseLoader;
import com.wallo.external.service.CodefTransactionMockService;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodefMockController {

    private static final Map<String, String> FIXTURE_BY_PATH = Map.of(
            "/mock/v1/kr/bank/p/account/account-list", "bank-accounts.json",
            "/mock/v1/kr/card/p/account/card-list", "card-list.json",
            "/mock/v1/kr/stock/p/account/account-list", "stock-accounts.json"
    );

    private final CodefMockResponseLoader codefMockResponseLoader;
    private final CodefTransactionMockService codefTransactionMockService;

    public CodefMockController(
            CodefMockResponseLoader codefMockResponseLoader,
            CodefTransactionMockService codefTransactionMockService
    ) {
        this.codefMockResponseLoader = codefMockResponseLoader;
        this.codefTransactionMockService = codefTransactionMockService;
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

    @PostMapping("/mock/v1/kr/card/p/approval-list")
    public CodefDto.Response getCardApprovals(
            @RequestBody CodefDto.CardApprovalRequest request
    ) {
        return codefTransactionMockService.getCardApprovals(request);
    }

    @PostMapping({
            "/mock/v1/kr/bank/p/account/transaction-list",
            "/v1/kr/bank/p/account/transaction-list"
    })
    public CodefDto.Response getBankTransactions(
            @RequestBody CodefDto.BankTransactionRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return codefTransactionMockService.getBankTransactions(request, authorizationHeader);
    }

    private CodefDto.Response loadResponse(String fileName) {
        return codefMockResponseLoader.load(fileName);
    }
}
