package com.wallo.external.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.external.dto.CodefDto;
import org.springframework.stereotype.Component;

/**
 * 현재 Mock CODEF 응답을 Jackson으로 변환하는 기본 구현체다.
 * 실제 CODEF 응답 형식이 확정되면 이 구현체만 교체할 수 있다.
 */
@Component
public class ObjectMapperCodefAssetResponseMapper implements CodefAssetResponseMapper {

    private final ObjectMapper objectMapper;

    public ObjectMapperCodefAssetResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CodefDto.AssetData toAssetData(CodefDto.Response response) {
        return objectMapper.convertValue(response.getData(), CodefDto.AssetData.class);
    }
}
