package com.wallo.chat.mapper;

import com.wallo.chat.dto.ConsumptionAnalysisResultDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ConsumptionAnalysisResultMapper {
    int insert(ConsumptionAnalysisResultDto.SaveCommand command);
    int countByUserId(@Param("userId") long userId);

    List<ConsumptionAnalysisResultDto.StoredResult> findByAssistantMessageIds(
            @Param("assistantMessageIds") List<Long> assistantMessageIds
    );
}
