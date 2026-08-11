package com.wallo.chat.mapper;

import com.wallo.chat.dto.ConsumptionAnalysisResultDto;

public interface ConsumptionAnalysisResultMapper {
    int insert(ConsumptionAnalysisResultDto.SaveCommand command);
}
