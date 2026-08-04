package com.wallo.external.client;

import com.wallo.external.dto.CodefDto;

public interface BankTransactionClient {

    CodefDto.Response getTransactions(CodefDto.BankTransactionRequest request);
}
