package com.wallo.external.mapper;

import com.wallo.external.dto.CodefDto;

/**
 * CODEF 자산 응답을 내부 자산 처리용 데이터로 변환한다.
 */
public interface CodefAssetResponseMapper {

    CodefDto.AssetData toAssetData(CodefDto.Response response);
}
