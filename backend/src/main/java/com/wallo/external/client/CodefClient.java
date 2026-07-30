package com.wallo.external.client;

import com.wallo.external.dto.CodefDto;

public interface CodefClient {

    CodefDto.Response connectInstitution(CodefDto.Request request);
}



