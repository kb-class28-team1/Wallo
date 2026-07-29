package com.wallo.external.controller;

import com.wallo.external.dto.CodefDto;
import com.wallo.external.service.CodefMockResponseLoader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodefMockController {

    private final CodefMockResponseLoader codefMockResponseLoader;

    public CodefMockController(CodefMockResponseLoader codefMockResponseLoader) {
        this.codefMockResponseLoader = codefMockResponseLoader;
    }

    @PostMapping("/mock/v1/kr/bank/p/account/account-list")
    public CodefDto.Response getBankAccountList(@RequestBody CodefDto.Request request) {
        return loadResponse("bank-accounts.json");
    }

    @PostMapping("/mock/v1/kr/card/p/account/card-list")
    public CodefDto.Response getCardList(@RequestBody CodefDto.Request request) {
        return loadResponse("card-transactions.json");
    }

    @PostMapping("/mock/v1/kr/stock/p/account/account-list")
    public CodefDto.Response getStockAccountList(@RequestBody CodefDto.Request request) {
        return loadResponse("bank-transactions.json");
    }

    private CodefDto.Response loadResponse(String fileName) {
        return codefMockResponseLoader.load(fileName);
    }
}
