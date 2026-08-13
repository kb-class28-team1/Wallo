package com.wallo.chat.mapper;

import com.wallo.chat.dto.AssetAnalysisResultDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AssetAnalysisResultMapper {
    int insert(AssetAnalysisResultDto.SaveCommand command);

    List<AssetAnalysisResultDto.StoredResult> findByAssistantMessageIds(
            @Param("assistantMessageIds") List<Long> assistantMessageIds
    );
}
