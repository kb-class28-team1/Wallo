package com.wallo.external.client;

import com.wallo.external.dto.CodefDto;

public interface CardApprovalClient {

    CodefDto.Response getApprovals(CodefDto.CardApprovalRequest request);
}
