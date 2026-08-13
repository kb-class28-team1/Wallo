package com.wallo.chat.mapper;

import com.wallo.chat.dto.ConsumptionAnalysisResultDto;
import java.util.List;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface ConsumptionAnalysisResultMapper {
    int insert(ConsumptionAnalysisResultDto.SaveCommand command);

    List<ConsumptionAnalysisResultDto.StoredResult> findByAssistantMessageIds(
            @Param("assistantMessageIds") List<Long> assistantMessageIds
    );

    ConsumptionAnalysisResultDto.RecentResult findLatestSince(
            @Param("userId") long userId,
            @Param("cutoff") LocalDateTime cutoff
    );
}
