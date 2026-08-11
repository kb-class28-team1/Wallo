package com.wallo.external.client;

import com.wallo.external.dto.CodefDto;

public interface IncomeProofClient {

    CodefDto.Response getIncomeProof(CodefDto.IncomeProofRequest request);
}
