package com.wallo.external.controller;

import com.wallo.external.auth.CodefAccessTokenProvider;
import com.wallo.external.dto.CodefDto;
import com.wallo.external.service.CodefMockService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodefMockController {

    private final CodefMockService codefMockService;
    private final CodefAccessTokenProvider accessTokenProvider;

    public CodefMockController(
            CodefMockService codefMockService,
            CodefAccessTokenProvider accessTokenProvider
    ) {
        this.codefMockService = codefMockService;
        this.accessTokenProvider = accessTokenProvider;
    }

    @PostMapping({
            "/mock/v1/kr/bank/p/account/account-list",
            "/mock/v1/kr/card/p/account/card-list",
            "/mock/v1/kr/stock/p/account/account-list"
    })
    public CodefDto.Response getMockResponse(
            @RequestBody CodefDto.Request ignoredRequest,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletRequest request
    ) {
        CodefDto.Response authenticationFailure = authenticate(authorizationHeader);
        if (authenticationFailure != null) {
            return authenticationFailure;
        }
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return codefMockService.getAssetResponse(requestPath);
    }

    @PostMapping("/mock/v1/kr/card/p/approval-list")
    public CodefDto.Response getCardApprovals(
            @RequestBody CodefDto.CardApprovalRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        CodefDto.Response authenticationFailure = authenticate(authorizationHeader);
        if (authenticationFailure != null) {
            return authenticationFailure;
        }
        return codefMockService.getCardApprovals(request);
    }

    @PostMapping({
            "/mock/v1/kr/bank/p/account/transaction-list",
            "/v1/kr/bank/p/account/transaction-list"
    })
    public CodefDto.Response getBankTransactions(
            @RequestBody CodefDto.BankTransactionRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        CodefDto.Response authenticationFailure = authenticate(authorizationHeader);
        if (authenticationFailure != null) {
            return authenticationFailure;
        }
        return codefMockService.getBankTransactions(request);
    }

    private CodefDto.Response authenticate(String authorizationHeader) {
        String expectedAuthorization = "Bearer " + accessTokenProvider.getAccessToken();
        if (expectedAuthorization.equals(authorizationHeader)) {
            return null;
        }
        return CodefDto.Response.failure(
                "CF-40100",
                "인증 정보가 올바르지 않습니다.",
                "유효한 Authorization Bearer 토큰이 필요합니다."
        );
    }
}
